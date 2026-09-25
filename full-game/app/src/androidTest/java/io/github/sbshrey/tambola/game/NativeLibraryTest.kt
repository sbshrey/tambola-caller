package io.github.sbshrey.tambola.game

import android.os.Process
import android.system.Os
import android.system.OsConstants
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicit loader check; ordinary game tests may not need these libraries on newer Android. */
class NativeLibraryTest {
    @Test fun bundledLibrariesLoadInSixteenKbProcess() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("tambolaNative16kb") == "true")
        check(BuildConfig.DEBUG && isAndroidEmulator())
        assertTrue(Process.is64Bit())
        assertEquals(16_384L, Os.sysconf(OsConstants._SC_PAGESIZE))
        System.loadLibrary("androidx.graphics.path")
        System.loadLibrary("datastore_shared_counter")
    }
}
