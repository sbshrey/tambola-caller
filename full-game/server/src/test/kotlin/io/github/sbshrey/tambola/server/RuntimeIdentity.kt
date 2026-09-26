package io.github.sbshrey.tambola.server

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.MessageDigest

/** Matches tools/service-runtime.mjs; includes dependencies even when server.jar is unchanged. */
internal fun serviceRuntimeIdentity(root: Path, folder: Path = root.resolve("server/build/install/server/lib")): Map<String, String> {
    fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    val entries = Files.list(folder).use { paths -> paths.filter { it.fileName.toString().endsWith(".jar") }.toList() }
        .sortedBy { it.fileName.toString() }.map { path ->
            check(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
            val name = path.fileName.toString(); check(name.matches(Regex("[A-Za-z0-9_.+-]+\\.jar")))
            name to sha(Files.readAllBytes(path))
        }
    val jar = entries.single { it.first == "server.jar" }.second
    val manifest = entries.joinToString("") { (name, hash) -> "$hash  $name\n" }
    return mapOf("serviceJarSha256" to jar, "serviceRuntimeSha256" to sha(manifest.toByteArray(Charsets.UTF_8)), "serviceRuntimeManifest" to manifest)
}
