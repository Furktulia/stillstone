package com.example.ballsortgame

import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.example.ballsortgame.game.*
import com.example.ballsortgame.ui.GameScreen
import com.example.ballsortgame.ui.theme.BallSortGameTheme
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * What a first-time player meets, with an in-memory save and fresh preferences: never the real
 * player's. Taps on the board are real touches on the screen, because the point is whether a
 * note on top lets them through.
 */
class NewPlayerUiTest {
    @get:Rule val compose = createComposeRule()
    private val owner = ViewModelStore()
    private lateinit var model: GameViewModel
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val used = mutableSetOf<String>()
    private val isolated = object : android.content.ContextWrapper(base) {
        override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences {
            used += "new-player-$name"
            return base.getSharedPreferences("new-player-$name", mode)
        }
        override fun getApplicationContext() = this
    }

    @Before fun start() {
        // Start as someone who has never played: no notes seen, no control names retired.
        listOf("onboarding", "mode", "feedback").forEach { base.deleteSharedPreferences("new-player-$it") }
        compose.runOnUiThread {
            model = ViewModelProvider(owner, object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = GameViewModel(LevelCatalog.levels, MemoryStore()) as T
            })[GameViewModel::class.java]
        }
        compose.setContent {
            val state by model.state.collectAsState()
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalContext provides isolated) {
                BallSortGameTheme { GameScreen(state, model) }
            }
        }
        compose.waitUntil(5_000) { !model.state.value.loading }
    }

    @After fun stop() {
        compose.runOnUiThread { owner.clear() }
        used.forEach { base.deleteSharedPreferences(it) }
    }

    /** A real finger tap in the middle of [tag], delivered by the system to whatever window is there. */
    private fun touch(tag: String) {
        val node = compose.onNodeWithTag(tag).fetchSemanticsNode()
        val x = node.positionOnScreen.x + node.size.width / 2f
        val y = node.positionOnScreen.y + node.size.height / 2f
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val down = SystemClock.uptimeMillis()
        instrumentation.sendPointerSync(MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x, y, 0))
        instrumentation.sendPointerSync(MotionEvent.obtain(down, down + 40, MotionEvent.ACTION_UP, x, y, 0))
        compose.waitForIdle()
    }

    private fun status() = compose.onNodeWithTag("game_status")

    @Test fun firstPuzzleSaysHowToMoveAndTheSecondWhatFor() {
        status().assertTextEquals("Tap a tube to pick up its top stones.")
        compose.onNodeWithTag("tube_0").performClick()
        status().assertTextEquals("Now tap the tube where they should go.")
        compose.onNodeWithTag("tube_1").performClick()
        compose.onNodeWithTag("next").assertIsDisplayed()
        compose.runOnUiThread { model.chooseLevel(1) }
        compose.waitForIdle()
        status().assertTextEquals("Give every color a tube of its own.")
    }

    @Test fun firstNoteLetsTheBoardBeTappedAndClosesAtTheFirstMove() {
        val index = LevelCatalog.levels.indexOfFirst { it.rules.target != null }
        compose.runOnUiThread { model.chooseLevel(index) }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("trait_note").fetchSemanticsNodes().isNotEmpty() }
        val board = model.state.value.board
        val source = board.tubes.indices.first { board.tubes[it].balls.isNotEmpty() }
        val destination = board.tubes.indices.first { it != source && board.tubes[it].freeSpace > 0 }
        // The first tap reaches the board instead of only closing the note.
        touch("tube_$source")
        assertEquals(source, model.state.value.selected)
        compose.onNodeWithTag("trait_note").assertIsDisplayed()
        touch("tube_$destination")
        assertTrue(model.state.value.canUndo)
        compose.onNodeWithTag("trait_note").assertDoesNotExist()
    }

    @Test fun tappingThePatternPictureSaysWhatItIs() {
        val index = LevelCatalog.levels.indexOfFirst { it.rules.target != null }
        compose.runOnUiThread { model.chooseLevel(index) }
        compose.waitForIdle()
        compose.onAllNodesWithTag("trait_note").fetchSemanticsNodes().forEach { _ -> compose.onNodeWithTag("trait_note").performClick() }
        compose.onNodeWithContentDescription("Target tube 1", substring = true).performClick()
        compose.onNodeWithTag("pattern_note").assertIsDisplayed()
        compose.onNodeWithText("The pattern to make").assertIsDisplayed()
        compose.onNodeWithTag("pattern_note").performClick()
        compose.onNodeWithTag("pattern_note").assertDoesNotExist()
        assertFalse(model.state.value.canUndo) // Looking at the picture is not a move.
    }

    private class MemoryStore : GameSaveStore {
        @Volatile private var progress: GameProgress? = null
        override fun load() = StoredProgress(progress)
        override fun save(progress: GameProgress) { this.progress = progress }
    }
}
