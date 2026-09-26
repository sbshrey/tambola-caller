package io.github.sbshrey.tambola.client

import io.github.sbshrey.tambola.protocol.*
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.io.readByteArray
import kotlinx.serialization.encodeToString
import java.net.URI
import java.util.concurrent.TimeUnit

const val MAX_RESPONSE_BYTES = 524_288

class RoomApiFailure(val status: Int, val code: String, val userMessage: String) : Exception(code)
class InvalidRoomResponse : Exception("Invalid room response")
/** A room stream should remain open until cancelled; retain only the numeric transport close code. */
class RoomStreamClosed(val closeCode: Short?) : java.io.IOException("Room event stream closed")

/** Endpoints are build configuration, never user-supplied links or token-bearing URLs. */
fun checkedEndpoint(value: String, allowLocalHttp: Boolean = false): String {
    val uri = URI(value)
    require(uri.userInfo == null && uri.query == null && uri.fragment == null && uri.host != null)
    require(uri.rawPath.orEmpty() in listOf("", "/") && (uri.port == -1 || uri.port in 1..65535))
    require(uri.scheme == "https" || (allowLocalHttp && uri.scheme == "http" && uri.host in setOf("127.0.0.1", "10.0.2.2", "localhost")))
    return value.trimEnd('/')
}

interface RoomApi : AutoCloseable {
    suspend fun guest(request: GuestRequest): GuestCredentials
    suspend fun wallet(token: String): WalletView
    suspend fun refill(token: String, request: RefillRequest): WalletView
    suspend fun match(token: String, request: MatchRequest): RoomUpdate
    suspend fun create(token: String, request: CreateRoomRequest): RoomUpdate
    suspend fun join(token: String, code: String): RoomUpdate
    suspend fun read(token: String, code: String, after: Long? = null): RoomUpdate
    suspend fun command(token: String, code: String, request: CommandRequest): RoomUpdate
    suspend fun logout(token: String)
    suspend fun deleteProfile(token: String, request: DeleteProfileRequest): DeleteProfileReceipt
    fun events(token: String, code: String, after: Long?): Flow<RoomUpdate>
}

class HttpRoomApi(endpoint: String, allowLocalHttp: Boolean = false,
    private val client: HttpClient = roomHttpClient()) : RoomApi {
    private val base = checkedEndpoint(endpoint, allowLocalHttp)
    private fun roomPath(code: String): String {
        require(Regex("[A-HJ-NP-Z2-9]{8}").matches(code))
        return "/v1/rooms/$code"
    }
    private suspend fun text(path: String, token: String? = null, body: String? = null, post: Boolean = false): String =
        client.prepareRequest(base + path) {
            method = if (post) HttpMethod.Post else HttpMethod.Get
            token?.let { bearerAuth(it) }
            if (body != null) { contentType(ContentType.Application.Json); setBody(body) }
        }.execute { response ->
            if ((response.contentLength() ?: 0) > MAX_RESPONSE_BYTES) throw InvalidRoomResponse()
            val bytes = response.bodyAsChannel().readRemaining((MAX_RESPONSE_BYTES + 1).toLong()).readByteArray()
            if (bytes.size > MAX_RESPONSE_BYTES) throw InvalidRoomResponse()
            val payload = bytes.decodeToString(throwOnInvalidSequence = true)
            if (!response.status.isSuccess()) {
                val error = runCatching { WireJson.decodeFromString<ApiError>(payload) }.getOrNull()
                // Server errors are bounded structured messages; never display arbitrary proxy HTML.
                throw RoomApiFailure(response.status.value, error?.code ?: "service_error",
                    error?.message?.take(240) ?: "The room service could not complete this request.")
            }
            payload
        }
    override suspend fun guest(request: GuestRequest): GuestCredentials = WireJson.decodeFromString(text("/v1/guests", body = WireJson.encodeToString(request), post = true))
    override suspend fun wallet(token: String): WalletView = WireJson.decodeFromString(text("/v1/wallet", token))
    override suspend fun refill(token: String, request: RefillRequest): WalletView = WireJson.decodeFromString(text("/v1/wallet/refill", token, WireJson.encodeToString(request), true))
    override suspend fun match(token: String, request: MatchRequest): RoomUpdate = WireJson.decodeFromString(text("/v1/matches", token, WireJson.encodeToString(request), true))
    override suspend fun create(token: String, request: CreateRoomRequest): RoomUpdate = WireJson.decodeFromString(text("/v1/rooms", token, WireJson.encodeToString(request), true))
    override suspend fun join(token: String, code: String): RoomUpdate = WireJson.decodeFromString(text(roomPath(code) + "/join", token, post = true))
    override suspend fun read(token: String, code: String, after: Long?): RoomUpdate {
        require(after == null || after >= 0)
        return WireJson.decodeFromString(text(roomPath(code) + (after?.let { "?after=$it" } ?: ""), token))
    }
    override suspend fun command(token: String, code: String, request: CommandRequest): RoomUpdate = WireJson.decodeFromString(text(roomPath(code) + "/commands", token, WireJson.encodeToString(request), true))
    override suspend fun logout(token: String) { text("/v1/guests/me/logout", token, post = true) }
    override suspend fun deleteProfile(token: String, request: DeleteProfileRequest): DeleteProfileReceipt {
        val receipt = WireJson.decodeFromString<DeleteProfileReceipt>(text("/v1/guests/me/delete", token, WireJson.encodeToString(request), true))
        if (receipt.id != request.id || receipt.deletedAt < 0 || receipt.confirmUntil <= receipt.deletedAt) throw InvalidRoomResponse()
        return receipt
    }
    override fun events(token: String, code: String, after: Long?): Flow<RoomUpdate> = channelFlow {
        require(after == null || after >= 0)
        val url = base.replaceFirst("http", "ws") + roomPath(code) + "/events" + (after?.let { "?after=$it" } ?: "")
        client.webSocket(urlString = url, request = { bearerAuth(token) }) {
            for (frame in incoming) {
                if (frame !is Frame.Text || frame.data.size > MAX_RESPONSE_BYTES) throw InvalidRoomResponse()
                val update = WireJson.decodeFromString<RoomUpdate>(frame.readText())
                this@channelFlow.send(update)
                send(Frame.Text(WireJson.encodeToString(EventAck(update.snapshot.revision))))
            }
            val reason = closeReason.await()
            if (reason?.code == CloseReason.Codes.VIOLATED_POLICY.code) {
                val code = reason.message
                throw RoomApiFailure(if (code == "unauthorized") 401 else 403, code, "Your room session needs to reconnect.")
            }
            throw RoomStreamClosed(reason?.code)
        }
    }.buffer(Channel.CONFLATED)
    override fun close() { client.close() }
}

private fun roomHttpClient(): HttpClient = HttpClient(OkHttp) {
    followRedirects = false
    install(HttpTimeout) { requestTimeoutMillis = 20_000; connectTimeoutMillis = 10_000; socketTimeoutMillis = 30_000 }
    // OkHttp rejects WebSockets.maxFrameSize. Check received frame size before decoding above.
    install(WebSockets)
    engine { config { followRedirects(false); followSslRedirects(false); pingInterval(20, TimeUnit.SECONDS) } }
}
