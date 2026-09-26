package io.github.sbshrey.tambola.protocol

import java.net.URI
import java.util.Locale

/** A link selects a room on this build's service; it never supplies a session or another API endpoint. */
object RoomInvites {
    const val PATH = "/invite/"
    private val codePattern = Regex("[A-HJ-NP-Z2-9]{8}")

    fun code(value: String): String? = value.uppercase(Locale.ROOT).takeIf { codePattern.matches(it) && value.all { c -> c.code < 128 } }

    fun origin(value: String, allowLocalHttp: Boolean = false): String? = runCatching {
        if (value.length !in 1..256 || value.any { it.code !in 33..126 }) return null
        val uri = URI(value)
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        if (uri.isOpaque || uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null || uri.rawPath.orEmpty() !in listOf("", "/")) return null
        if (uri.port != -1 && uri.port !in 1..65535) return null
        if (uri.scheme != "https" && !(allowLocalHttp && uri.scheme == "http" && host == "127.0.0.1")) return null
        val port = uri.port.takeUnless { it == defaultPort(uri.scheme) } ?: -1
        URI(uri.scheme, null, host, port, null, null, null).toASCIIString()
    }.getOrNull()

    fun link(serviceOrigin: String, roomCode: String, allowLocalHttp: Boolean = false): String =
        requireNotNull(origin(serviceOrigin, allowLocalHttp)) + PATH + requireNotNull(code(roomCode))

    fun parse(value: String?, serviceOrigin: String, allowLocalHttp: Boolean = false): String? = runCatching {
        val expected = URI(origin(serviceOrigin, allowLocalHttp) ?: return null)
        if (value == null || value.length !in 1..512 || value.any { it.code !in 33..126 }) return null
        val uri = URI(value)
        if (uri.isOpaque || uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null) return null
        if (!uri.host.orEmpty().equals(expected.host, ignoreCase = true)) return null
        val sameOrigin = uri.scheme == expected.scheme && effectivePort(uri) == effectivePort(expected)
        // Android also routes http App Links. The app still uses its fixed HTTPS API.
        val upgradesToHttps = expected.scheme == "https" && effectivePort(expected) == 443 && uri.scheme == "http" && effectivePort(uri) == 80
        if (!sameOrigin && !upgradesToHttps) return null
        val path = uri.rawPath ?: return null
        if (!path.startsWith(PATH)) return null
        code(path.removePrefix(PATH))
    }.getOrNull()

    private fun defaultPort(scheme: String?) = if (scheme == "https") 443 else 80
    private fun effectivePort(uri: URI) = if (uri.port == -1) defaultPort(uri.scheme) else uri.port
}
