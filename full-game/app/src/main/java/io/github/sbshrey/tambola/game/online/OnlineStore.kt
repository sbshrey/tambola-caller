package io.github.sbshrey.tambola.game.online

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import io.github.sbshrey.tambola.client.OnlineSaved
import io.github.sbshrey.tambola.protocol.WireJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.io.File
import java.io.ByteArrayOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Credentials and cached rooms are encrypted together; backup and device transfer are disabled. */
class OnlineStore(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "private-rooms.enc"))
    private val alias = "tambola.private-rooms.v1"
    private val aad = "io.github.sbshrey.tambola.game/private-rooms/v1".toByteArray(Charsets.UTF_8)
    private fun key(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        check(create) { "Stored session key is unavailable" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
            generateKey()
        }
    }
    suspend fun read(): OnlineSaved? = withContext(Dispatchers.IO) {
        if (!file.baseFile.exists()) return@withContext null
        val bytes = file.openRead().use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                check(output.size() + count <= MAX_BYTES)
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        check(bytes.size in 30..MAX_BYTES && bytes[0] == 1.toByte())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(false), GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
        cipher.updateAAD(aad)
        WireJson.decodeFromString<OnlineSaved>(cipher.doFinal(bytes.copyOfRange(13, bytes.size)).decodeToString(throwOnInvalidSequence = true))
    }
    suspend fun write(saved: OnlineSaved?) = withContext(Dispatchers.IO) {
        if (saved == null) {
            file.delete()
            check(listOf(file.baseFile, File(file.baseFile.path + ".bak"), File(file.baseFile.path + ".new")).none { it.exists() }) { "Online data could not be removed" }
            return@withContext
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(true)); cipher.updateAAD(aad)
        val bytes = byteArrayOf(1) + cipher.iv + cipher.doFinal(WireJson.encodeToString(saved).toByteArray(Charsets.UTF_8))
        check(bytes.size <= MAX_BYTES)
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream); throw error }
    }
    companion object { private const val MAX_BYTES = 8 * 1024 * 1024 }
}
