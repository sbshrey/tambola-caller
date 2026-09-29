package io.github.sbshrey.tambola.game.updates

import kotlinx.serialization.json.*
import java.net.URI

/** Dedicated beta assets opt in to updates; ordinary release attachments are never installed. */
data class UpdateRelease(val version: Int, val url: String, val bytes: Long, val sha256: String)
object UpdateFeed {
    const val URL = "https://api.github.com/repos/sbshrey/tambola-caller/releases?per_page=20"
    const val MAX_APK_BYTES = 150L * 1024 * 1024
    private val assetName = Regex("tambola-beta-v([1-9][0-9]{0,8})\\.apk")
    fun latest(text: String, installed: Int): UpdateRelease? {
        val releases = Json.parseToJsonElement(text).jsonArray
        return releases.filter { it.jsonObject["draft"]?.jsonPrimitive?.booleanOrNull == false }
            .flatMap { it.jsonObject["assets"]?.jsonArray.orEmpty() }.mapNotNull { asset ->
                val a = asset.jsonObject
                val name = a["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val version = assetName.matchEntire(name)?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
                if (version <= installed) return@mapNotNull null
                val url = a["browser_download_url"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val uri = URI(url)
                require(uri.scheme == "https" && uri.host == "github.com" && uri.port == -1 && uri.userInfo == null && uri.query == null && uri.fragment == null)
                require(uri.rawPath.startsWith("/sbshrey/tambola-caller/releases/download/") && uri.rawPath.endsWith("/$name"))
                val bytes = requireNotNull(a["size"]?.jsonPrimitive?.longOrNull)
                require(bytes in 1..MAX_APK_BYTES)
                val digest = a["digest"]?.jsonPrimitive?.content.orEmpty().removePrefix("sha256:")
                require(Regex("[a-f0-9]{64}").matches(digest))
                UpdateRelease(version, url, bytes, digest)
            }.maxByOrNull { it.version }
    }
}

internal fun validateUpdateIdentity(actualPackage: String, actualVersion: Long, minimumSdk: Int, actualSigners: Set<String>,
    expectedPackage: String, installedVersion: Int, deviceSdk: Int, trustedSigners: Set<String>, targetVersion: Int) {
    require(actualPackage == expectedPackage)
    require(actualVersion == targetVersion.toLong() && actualVersion > installedVersion)
    require(minimumSdk <= deviceSdk)
    require(trustedSigners.isNotEmpty() && actualSigners == trustedSigners)
}
