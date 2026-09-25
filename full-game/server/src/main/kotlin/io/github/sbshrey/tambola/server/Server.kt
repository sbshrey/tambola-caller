package io.github.sbshrey.tambola.server

import io.github.sbshrey.tambola.protocol.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.io.readByteArray
import kotlinx.serialization.encodeToString
import java.sql.SQLException
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.seconds

private const val MAX_BODY_BYTES = 32_768

fun main() {
    fun required(name: String) = System.getenv(name)?.takeIf { it.isNotBlank() } ?: error("$name is required")
    val host = System.getenv("TAMBOLA_BIND_HOST") ?: "127.0.0.1"
    require(host == "127.0.0.1" || host == "::1" || System.getenv("TAMBOLA_TLS_PROXY") == "true") {
        "Public binding requires TAMBOLA_TLS_PROXY=true and a TLS reverse proxy."
    }
    val port = (System.getenv("PORT") ?: "8080").toInt().also { require(it in 1..65535) }
    Database(required("TAMBOLA_DATABASE_URL"), required("TAMBOLA_DATABASE_USER"), required("TAMBOLA_DATABASE_PASSWORD")).use { database ->
        database.migrate()
        embeddedServer(Netty, host = host, port = port) { roomsModule(database) }.start(wait = true)
    }
}

fun Application.roomsModule(database: Database, service: RoomService = RoomService(database), runWorker: Boolean = true) {
    install(ContentNegotiation) { json(WireJson) }
    install(WebSockets) { pingPeriod = 15.seconds; timeout = 30.seconds; maxFrameSize = 1_024; masking = false }
    install(StatusPages) {
        exception<ApiFailure> { call, error ->
            if (error.status == 429) call.response.headers.append(HttpHeaders.RetryAfter, "60")
            call.respond(HttpStatusCode.fromValue(error.status), ApiError(error.code, error.message))
        }
        exception<IllegalArgumentException> { call, _ -> call.respond(HttpStatusCode.BadRequest, ApiError("invalid_request", "The request is not valid for this protocol.")) }
        exception<SQLException> { call, _ -> call.respond(HttpStatusCode.ServiceUnavailable, ApiError("database_unavailable", "The room service is temporarily unavailable. Retry the same command ID.")) }
        exception<Throwable> { call, error ->
            if (error is CancellationException) throw error
            // Do not log exception text, request bodies or credentials.
            this@roomsModule.log.error("Room request failed ({})", error.javaClass.simpleName)
            call.respond(HttpStatusCode.InternalServerError, ApiError("internal_error", "The request could not be completed. Retry the same command ID."))
        }
    }
    intercept(ApplicationCallPipeline.Plugins) {
        call.response.headers.append(HttpHeaders.CacheControl, "no-store")
        call.response.headers.append("X-Content-Type-Options", "nosniff")
    }
    routing {
        get("/health/live") { call.respond(Health("ok")) }
        get("/health/ready") { withContext(Dispatchers.IO) { database.healthy() }; call.respond(Health("ready")) }
        route("/v1") {
            post("/guests") {
                val body = call.body<GuestRequest>()
                val result = withContext(Dispatchers.IO) { service.register(body, call.request.local.remoteHost) }
                call.respond(HttpStatusCode.Created, result)
            }
            post("/guests/me/logout") { withContext(Dispatchers.IO) { service.revoke(call.bearer()) }; call.respond(HttpStatusCode.NoContent) }
            post("/rooms") {
                val body = call.body<CreateRoomRequest>()
                call.respond(HttpStatusCode.Created, withContext(Dispatchers.IO) { service.create(call.bearer(), body) })
            }
            post("/rooms/{code}/join") { call.respond(withContext(Dispatchers.IO) { service.join(call.bearer(), call.code()) }) }
            get("/rooms/{code}") { call.respond(withContext(Dispatchers.IO) { service.read(call.bearer(), call.code(), call.cursor()) }) }
            post("/rooms/{code}/commands") {
                val body = call.body<CommandRequest>()
                call.respond(withContext(Dispatchers.IO) { service.command(call.bearer(), call.code(), body) })
            }
            webSocket("/rooms/{code}/events") {
                try {
                    val token = call.bearer()
                    val code = call.code()
                    var cursor = call.cursor()
                    val lastSent = AtomicLong(-1)
                    val receiver = launch {
                        for (frame in incoming) {
                            if (frame !is Frame.Text) { close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Only event acknowledgements are accepted.")); break }
                            val ack = runCatching { WireJson.decodeFromString<EventAck>(frame.readText()) }.getOrNull()
                            if (ack == null || ack.revision < 0 || ack.revision > lastSent.get()) {
                                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid event acknowledgement.")); break
                            }
                        }
                    }
                    try {
                        while (isActive && receiver.isActive) {
                            val update = withContext(Dispatchers.IO) { service.read(token, code, cursor) }
                            if (update.snapshot.revision != lastSent.get()) {
                                // Backpressure cannot create an unbounded application queue.
                                lastSent.set(update.snapshot.revision)
                                withTimeout(10_000) { send(Frame.Text(WireJson.encodeToString(update))) }
                                cursor = lastSent.get()
                            }
                            delay(1_000)
                        }
                    } finally { receiver.cancel() }
                } catch (error: ApiFailure) {
                    close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, error.code))
                } catch (error: SQLException) {
                    close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "Service temporarily unavailable."))
                }
            }
        }
    }
    if (runWorker) {
        val job = launch(Dispatchers.IO) {
            var ticks = 0
            while (isActive) {
                try {
                    service.tick()
                    if (++ticks % 60 == 0) service.cleanup()
                } catch (error: CancellationException) { throw error }
                catch (error: Exception) { log.error("Room worker failed ({})", error.javaClass.simpleName) }
                delay(1_000)
            }
        }
        monitor.subscribe(ApplicationStopped) { job.cancel() }
    }
}

private fun ApplicationCall.bearer(): String {
    val authorization = request.headers[HttpHeaders.Authorization].orEmpty()
    demand(authorization.startsWith("Bearer "), 401, "unauthorized", "A bearer session is required.")
    return authorization.removePrefix("Bearer ")
}
private fun ApplicationCall.code(): String = parameters["code"].orEmpty().uppercase()
private fun ApplicationCall.cursor(): Long? = request.queryParameters["after"]?.let {
    it.toLongOrNull()?.takeIf { value -> value >= 0 } ?: fail(400, "invalid_cursor", "Event cursor must be a non-negative integer.")
}
private suspend inline fun <reified T> ApplicationCall.body(): T {
    demand(request.contentType().match(ContentType.Application.Json), 415, "content_type", "Send application/json.")
    val length = request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
    demand(length == null || length in 0..MAX_BODY_BYTES, 413, "body_too_large", "Request exceeds 32 KiB.")
    val bytes = withTimeout(10_000) { receiveChannel().readRemaining((MAX_BODY_BYTES + 1).toLong()).readByteArray() }
    demand(bytes.size <= MAX_BODY_BYTES, 413, "body_too_large", "Request exceeds 32 KiB.")
    return WireJson.decodeFromString(bytes.decodeToString(throwOnInvalidSequence = true))
}
