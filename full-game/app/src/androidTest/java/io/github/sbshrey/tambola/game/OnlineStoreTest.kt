package io.github.sbshrey.tambola.game

import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import io.github.sbshrey.tambola.client.*
import io.github.sbshrey.tambola.game.online.OnlineStore
import io.github.sbshrey.tambola.protocol.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class OnlineStoreTest {
    @Test fun keystoreRoundTripUsesFreshCiphertextAndRejectsTamperingWithoutDeletingData() = runBlocking<Unit> {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val context = object : ContextWrapper(target) {
            override fun getNoBackupFilesDir(): File = File(target.noBackupFilesDir, "store-test-fixture").apply { mkdirs() }
        }
        val store = OnlineStore(context)
        val file = File(context.noBackupFilesDir, "private-rooms.enc")
        val value = OnlineSaved("https://fixture.example", GuestCredentials("fixture-player", "fixture-secret", 99_999), "Fixture Player")
        try {
            store.write(value)
            val first = file.readBytes()
            assertFalse(first.toString(Charsets.ISO_8859_1).contains("fixture-secret"))
            assertTrue(store.read()!!.credentials.token == value.credentials.token)
            store.write(value)
            assertFalse(first.contentEquals(file.readBytes()))
            val corrupted = file.readBytes().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
            file.writeBytes(corrupted)
            var rejected = false
            try { store.read() } catch (_: java.security.GeneralSecurityException) { rejected = true }
            assertTrue("Tampered ciphertext must fail authentication", rejected)
            assertTrue(file.exists()); assertTrue(corrupted.contentEquals(file.readBytes()))
        } finally { store.write(null) }
        assertNull(store.read())
    }
}
