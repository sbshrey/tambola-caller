package io.github.sbshrey.tambola.game

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.sbshrey.tambola.domain.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Explicitly selected only after a real alpha07 install/save and an in-place alpha08 update. */
class UpgradeAvatarTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val model get() = ViewModelProvider(compose.activity)[GameViewModel::class.java]
    @Test fun realVersionTwoRoundKeepsTicketsCallsAndMarksAndSavesAsVersionThree() {
        check(BuildConfig.DEBUG && BuildConfig.VERSION_NAME == "0.8.0-alpha08")
        check(isAndroidEmulator())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixture = Json.parseToJsonElement(File(context.filesDir, "alpha08-upgrade.json").readText()).jsonObject
        assertEquals("0.7.0-alpha07", fixture.getValue("appVersion").jsonPrimitive.content)
        val payload = fixture.getValue("round").jsonObject
        assertEquals(2, payload.getValue("version").jsonPrimitive.int)
        val before = RoundCodec.decode(payload.toString())
        assertTrue(before.called.isNotEmpty()); assertTrue(before.marks.values.any { it.isNotEmpty() })
        compose.waitUntil(15_000) { !model.state.value.loading && model.state.value.round != null }
        val restored = model.state.value.round!!
        assertEquals(before.id, restored.id); assertEquals(before.tickets, restored.tickets)
        assertEquals(before.called, restored.called); assertEquals(before.marks, restored.marks)
        assertEquals(before.settings, restored.settings); assertEquals(before.players, restored.players)
        assertTrue(restored.players.all { it.avatar == 0 }); assertEquals(3, restored.version)
        assertNull(model.state.value.winMoment)
        compose.tapText("Resume round"); compose.tapText("Resume calling")
        compose.waitUntil(10_000) { model.state.value.round!!.status == RoundStatus.PLAYING && !model.state.value.saving }
        compose.tapText("Call next number")
        compose.waitUntil(10_000) { model.state.value.round!!.called.size == before.called.size + 1 && !model.state.value.saving }
        val after = model.state.value.round!!
        assertEquals(before.called, after.called.dropLast(1)); assertEquals(before.tickets, after.tickets)
        assertEquals(before.marks, after.marks); assertEquals(before.players, after.players)
        captureTestScreen("avatar-upgrade-table")
        compose.tapText("‹ Home")
        compose.waitUntil(10_000) { model.state.value.round!!.status == RoundStatus.PAUSED && !model.state.value.saving }
    }
}
