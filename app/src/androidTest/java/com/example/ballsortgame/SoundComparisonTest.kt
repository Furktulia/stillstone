package com.example.ballsortgame

import android.content.ContextWrapper
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.ballsortgame.ui.*
import com.example.ballsortgame.ui.theme.BallSortGameTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SoundComparisonTest {
    @get:Rule val compose = createComposeRule()
    @Test fun allStylesPrepareOnDeviceAndComparisonSavesOnlyTheChosenStyle() {
        val player = StoneAudio()
        try {
            for (style in ImpactStyle.entries) {
                player.prepare(style, deep = true, balls = 3, preview = true)
                assertTrue("Native sample preparation failed: $style", player.ready)
                player.impact(0, style, 0f) // Exercise playback without making the test audible.
                player.stop()
                player.prepare(style) // Gameplay bank, not just the comparison clip.
                assertTrue("Gameplay bank failed: $style", player.ready)
                for (count in 1..6) { player.impact(0, style, 0f, count); player.stop() }
                for (kind in ContactKind.entries) repeat(5) { player.contact(kind, style, 0f, 1.03f); player.stop() }
            }
        } finally { player.release() }
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "sound-test-${System.nanoTime()}-"
        val context = object : ContextWrapper(base) {
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences(prefix + name, mode)
        }
        var visible by mutableStateOf(true)
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                val feedback = rememberGameFeedback()
                BallSortGameTheme {
                    if (visible) SoundComparison(feedback) { visible = false }
                    else Text("Selected: ${feedback.style.title}")
                }
            }
        }
        for (style in ImpactStyle.entries) {
            compose.onNodeWithContentDescription(style.title).performScrollTo().performClick()
            compose.onNodeWithText("3 balls").performScrollTo().performClick()
            compose.onNodeWithText("Deep").performScrollTo().performClick()
            compose.onNodeWithText("Play").performScrollTo().performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("Preparing…").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithText("Audio unavailable on this device.").assertDoesNotExist()
            compose.onNodeWithText("Stop").performClick()
            assertNull(context.getSharedPreferences("feedback", 0).getString("style", null))
        }
        compose.onNodeWithContentDescription("Ceramic").performScrollTo().performClick()
        compose.onNodeWithText("Use Ceramic").performClick()
        compose.onNodeWithText("Selected: Ceramic").assertIsDisplayed()
        assertEquals("CERAMIC", context.getSharedPreferences("feedback", 0).getString("style", null))
        // Leave nothing behind in the playtest app. The pause lets the asynchronous save finish
        // first; otherwise it recreates the file just after it is deleted.
        Thread.sleep(600)
        base.deleteSharedPreferences(prefix + "feedback")
    }
}
