package io.github.sbshrey.tambola.game

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry

/** Official Google and AOSP images use different model casing; hardware must still be emulated. */
internal fun isAndroidEmulator(): Boolean = android.os.Build.HARDWARE in setOf("ranchu", "goldfish") &&
    (android.os.Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
        android.os.Build.FINGERPRINT.contains("sdk", ignoreCase = true) || android.os.Build.MODEL.contains("sdk", ignoreCase = true))

internal fun ComposeTestRule.hasTextNow(text: String) = onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
internal fun ComposeTestRule.tapText(text: String) = tapNode(onNodeWithText(text))
internal fun ComposeTestRule.tapTag(tag: String) = tapNode(onNodeWithTag(tag))
internal fun ComposeTestRule.scrollTextToEnd(text: String) {
    val node = onNodeWithText(text)
    node.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
    // ScrollBy launches an animation; a real-time screenshot delay does not advance
    // Compose's test clock. Settle frames and prove the final scroll position first.
    waitForIdle()
    val range = node.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
    // At normal text size the Hindi copy already fits; zero is a valid end offset.
    check(range.maxValue() >= 0f && kotlin.math.abs(range.value() - range.maxValue()) < 1f) {
        "Long confirmation did not scroll to its end"
    }
}
private fun ComposeTestRule.tapNode(node: SemanticsNodeInteraction) {
    var ancestor = node.fetchSemanticsNode().parent
    while (ancestor != null && !ancestor.config.contains(SemanticsActions.ScrollBy)) ancestor = ancestor.parent
    if (ancestor != null) node.performScrollTo()
    node.performClick()
    waitForIdle()
}

internal fun captureTestScreen(name: String) {
    require(name.matches(Regex("[a-z0-9-]+")))
    android.os.SystemClock.sleep(500) // Let platform dialog/ripple transitions settle before visual QA.
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
    java.io.File(instrumentation.targetContext.filesDir, "$name.png").outputStream().use {
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
    }
}
