package io.github.sbshrey.tambola.client

import io.github.sbshrey.tambola.protocol.WireJson
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.readByteArray
import kotlinx.serialization.Serializable
import java.net.URI

/** Only the publisher-controlled directory can change the temporary Internet beta origin.
 * No player credential is sent to the directory. Mutating game requests are never replayed here. */
class PublicEndpointDirectory(
    private val url: String,
    private val client: HttpClient,
    private val now: () -> Long = System::currentTimeMillis,
) {
    init {
        require(url == DIRECTORY_URL) { "Unrecognized game directory" }
    }
    private val mutex = Mutex()
    private var cached: Entry? = null
    private var checkedAt = 0L

    suspend fun origin(): String = mutex.withLock {
        val time = now()
        cached?.takeIf { time >= checkedAt && time - checkedAt < 60_000 && time < it.expiresAt }
            ?.let { return@withLock it.origin }
        val entry = client.prepareGet(url) {
            parameter("minute", time / 60_000)
            header(HttpHeaders.CacheControl, "no-cache")
        }.execute { response ->
            if (response.status != HttpStatusCode.OK || (response.contentLength() ?: 0) > 4096) throw InvalidRoomResponse()
            val bytes = response.bodyAsChannel().readRemaining(4097).readByteArray()
            if (bytes.size > 4096) throw InvalidRoomResponse()
            val value = WireJson.decodeFromString<Entry>(bytes.decodeToString(throwOnInvalidSequence = true))
            require(value.version == 1 && value.service == SERVICE)
            require(value.expiresAt > time && value.expiresAt - time <= 48 * 60 * 60 * 1000L)
            require(value.origin == checkedEndpoint(value.origin))
            val uri = URI(value.origin)
            require(uri.port == -1 && uri.host.matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*\\.trycloudflare\\.com")))
            value
        }
        cached = entry
        checkedAt = time
        entry.origin
    }

    @Serializable private data class Entry(val version: Int, val service: String, val origin: String, val expiresAt: Long)
    companion object {
        const val SERVICE = "tambola-together-public-beta-v1"
        const val DIRECTORY_URL = "https://raw.githubusercontent.com/sbshrey/tambola-caller/codex/public-beta-channel/server.json"
    }
}
