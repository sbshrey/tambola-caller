package io.github.sbshrey.tambola.game

import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.platform.app.InstrumentationRegistry
import io.github.sbshrey.tambola.game.online.OnlineStore
import io.github.sbshrey.tambola.game.online.RoomInviteViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RoomInviteColdTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun browsableLaunchSelectsInvitationWithoutJoiningAndSavedStateRestoresOnlyCode() = runBlocking<Unit> {
        check(isAndroidEmulator() && BuildConfig.DEBUG && BuildConfig.ROOM_API_URL == "http://127.0.0.1:8080")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val before = OnlineStore(context).read()
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://127.0.0.1:8080/invite/ABCDEFGH"), context, MainActivity::class.java)
            .addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("untrusted-extra", "must-not-be-retained")
            .putExtra("room_invite", 123)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        fun activity(): MainActivity {
            var found: MainActivity? = null
            val read = Runnable {
                found = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().singleOrNull()
            }
            if (Looper.myLooper() == Looper.getMainLooper()) read.run() else instrumentation.runOnMainSync(read)
            return checkNotNull(found)
        }
        // A consumed VIEW Intent deliberately changes filter identity. Observe the actual lifecycle rather than
        // ActivityScenario's original-intent matcher, which cannot follow a consumed deep link.
        context.startActivity(intent)
        try {
            compose.waitUntil(15_000) { runCatching { compose.onAllNodesWithTag("invite-code").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
            compose.onNodeWithTag("invite-code").assertTextEquals("ABCDEFGH").assertIsDisplayed()
            instrumentation.runOnMainSync {
                val current = activity()
                assertEquals(Intent.ACTION_MAIN, current.intent.action)
                assertFalse(current.intent.hasExtra("untrusted-extra"))
                assertNull(current.intent.data)
                assertEquals(Screen.ONLINE, ViewModelProvider(current)[GameViewModel::class.java].state.value.screen)
            }
            val initial = activity()
            instrumentation.runOnMainSync { initial.recreate() }
            compose.waitUntil(15_000) { runCatching { activity() !== initial }.getOrDefault(false) }
            compose.onNodeWithTag("invite-code").assertTextEquals("ABCDEFGH")
            assertEquals(before?.credentials, OnlineStore(context).read()?.credentials)
            assertEquals(before?.room?.code, OnlineStore(context).read()?.room?.code)
            assertEquals(before?.pending, OnlineStore(context).read()?.pending)
            instrumentation.runOnMainSync { ViewModelProvider(activity())[RoomInviteViewModel::class.java].dismiss() }
        } finally { instrumentation.runOnMainSync { activity().finishAndRemoveTask() } }
    }
}
