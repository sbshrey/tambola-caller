package io.github.sbshrey.tambola.game

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry

internal fun ComposeTestRule.hasTextNow(text: String) = onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
internal fun ComposeTestRule.tapText(text: String) = tapNode(onNodeWithText(text))
internal fun ComposeTestRule.tapTag(tag: String) = tapNode(onNodeWithTag(tag))
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
