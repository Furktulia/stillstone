package com.example.ballsortgame

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.example.ballsortgame.engine.Move
import com.example.ballsortgame.game.*
import com.example.ballsortgame.ui.GameScreen
import com.example.ballsortgame.ui.theme.BallSortGameTheme
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Uses an in-memory save store; never modifies the player's real save. */
class PlaytestUiTest {
    @get:Rule val compose = createComposeRule()
    private val owner = ViewModelStore()
    private lateinit var model: GameViewModel

    @Before fun start() {
        compose.runOnUiThread {
            model = ViewModelProvider(owner, object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    GameViewModel(LevelCatalog.levels, MemoryStore()) as T
            })[GameViewModel::class.java]
        }
        compose.setContent {
            val state by model.state.collectAsState()
            BallSortGameTheme { GameScreen(state, model) }
        }
        compose.waitUntil(5_000) { !model.state.value.loading }
    }

    @After fun stop() { compose.runOnUiThread { owner.clear() } }

    private fun choose(index: Int) {
        compose.onNodeWithTag("puzzles").performClick()
        val worldId = LevelCatalog.levels[index].worldId
        compose.onNodeWithTag("world_list").performScrollToNode(hasTestTag("world_$worldId"))
        compose.onNodeWithTag("world_$worldId").performClick()
        compose.onNodeWithTag("level_list").performScrollToNode(hasTestTag("level_$index"))
        compose.onNodeWithTag("level_$index").performClick()
        compose.waitForIdle()
    }

    @Test fun rapidTubeTapsCommitMovesWithoutWaitingForAnimationAndUndoCancelsPlayback() {
        choose(9)
        val moves = model.levels[9].solution.take(3)
        assertEquals(3, moves.size)
        compose.mainClock.autoAdvance = false
        try {
            for (move in moves) {
                compose.onNodeWithTag("tube_${move.source}").performClick()
                compose.mainClock.advanceTimeByFrame() // Publish selection without completing the flight.
                compose.onNodeWithTag("tube_${move.source}").assertIsSelected()
                compose.onNodeWithTag("tube_${move.destination}").performClick()
                compose.mainClock.advanceTimeByFrame()
            }
            compose.runOnIdle {
                assertEquals(moves, model.snapshotProgress().histories[model.levels[9].id])
                assertNotNull(model.state.value.transfer)
                assertEquals(2, model.state.value.queuedTransfers)
                val obsolete = model.state.value.transfer!!.id
                model.undo()
                assertNull(model.state.value.transfer)
                assertEquals(0, model.state.value.queuedTransfers)
                model.finishTransfer(obsolete)
                assertEquals(moves.dropLast(1), model.snapshotProgress().histories[model.levels[9].id])
            }
        } finally { compose.mainClock.autoAdvance = true }
    }

    @Test fun leavingDuringFlightKeepsCommittedBoardAndUndoHistory() {
        choose(9)
        compose.mainClock.autoAdvance = false
        try {
            val move = model.levels[9].solution.first()
            compose.runOnIdle {
                model.tapTube(move.source)
                model.tapTube(move.destination)
                val expected = model.state.value.board
                val obsolete = requireNotNull(model.state.value.transfer).id
                model.settlePresentation()
                assertNull(model.state.value.transfer)
                assertEquals(expected, model.state.value.board)
                assertEquals(listOf(move), model.snapshotProgress().histories[model.levels[9].id])
                model.finishTransfer(obsolete)
                assertEquals(expected, model.state.value.board)
                model.undo()
                assertEquals(model.levels[9].initial, model.state.value.board)
            }
        } finally { compose.mainClock.autoAdvance = true }
    }

    @Test fun solveAndUndoThroughActualTouchTargets() {
        compose.onNodeWithTag("tube_0").performClick().assertIsSelected()
        compose.onNodeWithTag("tube_1").performClick()
        compose.onNodeWithTag("next").assertIsDisplayed()
        assertTrue(model.state.value.board.isSolved)
        compose.onNodeWithTag("undo").performClick()
        compose.onNodeWithTag("next").assertDoesNotExist()
        assertEquals(LevelCatalog.levels[0].initial, model.state.value.board)
    }

    @Test fun tappingHintedSourceKeepsItSelectedAndAllowsTheMove() {
        repeat(2) {
            compose.onNodeWithTag("hint").performClick()
            compose.waitForIdle()
            assertEquals(Move(0, 1), model.state.value.hint)
        }
        compose.onNodeWithTag("tube_0").assertIsSelected().performClick().assertIsSelected()
        assertNull(model.state.value.hint)
        compose.onNodeWithTag("tube_1").performClick()
        compose.waitForIdle()
        assertTrue(model.state.value.board.isSolved)
    }

    @Test fun hintedDestinationCanBeTappedDirectly() {
        compose.onNodeWithTag("hint").performClick()
        compose.onNodeWithTag("tube_1").performClick()
        compose.waitForIdle()
        assertTrue(model.state.value.board.isSolved)
    }

    @Test fun tappingAnOrdinarilySelectedTubeStillDeselectsIt() {
        compose.onNodeWithTag("tube_0").performClick().assertIsSelected()
        compose.onNodeWithTag("tube_0").performClick().assertIsNotSelected()
    }

    @Test fun restartRequiresConfirmationAndRestoresInitialBoard() {
        choose(1)
        compose.onNodeWithTag("tube_0").performClick()
        compose.onNodeWithTag("tube_2").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("restart").performClick()
        compose.onNodeWithText("Restart puzzle").performClick()
        compose.waitForIdle()
        assertEquals(LevelCatalog.levels[1].initial, model.state.value.board)
        compose.onNodeWithTag("undo").assertIsNotEnabled()
    }

    @Test fun unfinishedBoardsSurviveSwitchingLevels() {
        choose(1)
        compose.onNodeWithTag("tube_0").performClick()
        compose.onNodeWithTag("tube_2").performClick()
        compose.waitForIdle()
        val expected = model.state.value.board
        choose(10)
        choose(1)
        assertEquals(expected, model.state.value.board)
        compose.onNodeWithTag("undo").assertIsEnabled()
    }

    @Test fun everyWorldCanBeEnteredImmediatelyAndCompletionIsShown() {
        val last = LevelCatalog.levels.lastIndex
        choose(last)
        assertEquals(last, model.state.value.levelIndex)
        choose(0)
        compose.onNodeWithTag("tube_0").performClick()
        compose.onNodeWithTag("tube_1").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("puzzles").performClick()
        compose.onNodeWithTag("world_three").performClick()
        // Completion is now a visual check plus an accessible state, without a repeated caption.
        compose.onNodeWithTag("level_0")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Sorted"))
        compose.onNodeWithTag("world_back").performClick()
        compose.onNodeWithTag("world_list").performScrollToNode(hasTestTag("world_five"))
        compose.onNodeWithTag("world_five").assertIsEnabled()
        compose.onNodeWithTag("world_list").performScrollToNode(hasTestTag("world_close"))
        compose.onNodeWithTag("world_close").performClick()
        assertTrue(model.state.value.board.isSolved)
    }

    @Test fun worldBrowserBackNavigationKeepsTheCurrentPuzzle() {
        val initial = model.state.value.board
        compose.onNodeWithTag("puzzles").performClick()
        compose.onNodeWithTag("world_list").performScrollToNode(hasTestTag("world_four"))
        compose.onNodeWithTag("world_four").performClick()
        compose.onNodeWithTag("world_back").performClick()
        compose.onNodeWithTag("world_list").performScrollToNode(hasTestTag("world_three"))
        compose.onNodeWithTag("world_three").assertIsDisplayed()
        compose.onNodeWithTag("world_list").performScrollToNode(hasTestTag("world_close"))
        compose.onNodeWithTag("world_close").performClick()
        assertEquals(initial, model.state.value.board)
    }

    @Test fun newFinalesAreReachableAndPlayableWithHintsAndUndo() {
        for (world in listOf("four", "five")) {
            val index = LevelCatalog.levels.indexOfLast { it.worldId == world }
            val level = LevelCatalog.levels[index]
            choose(index)
            repeat(level.solution.size) {
                compose.onNodeWithTag("hint").performClick()
                compose.waitForIdle()
                val move = requireNotNull(model.state.value.hint)
                compose.onNodeWithTag("tube_${move.source}").performClick().assertIsSelected()
                compose.onNodeWithTag("tube_${move.destination}").performClick()
                compose.waitForIdle()
            }
            assertTrue(level.id, model.state.value.board.isSolved)
            compose.onNodeWithTag("undo").performClick()
            assertFalse(model.state.value.board.isSolved)
            assertTrue(level.id in model.state.value.completed)
        }
    }

    @Test fun accessibilitySettingsUpdateWithoutResettingBoard() {
        val initial = model.state.value.board
        compose.onNodeWithTag("settings").performClick()
        compose.onNodeWithContentDescription("Color labels").performClick()
        compose.onNodeWithContentDescription("Reduced motion").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.waitForIdle()
        assertTrue(model.state.value.colorLabels)
        assertTrue(model.state.value.reducedMotion)
        assertEquals(initial, model.state.value.board)
    }

    private class MemoryStore : GameSaveStore {
        @Volatile private var progress: GameProgress? = null
        override fun load() = StoredProgress(progress)
        override fun save(progress: GameProgress) { this.progress = progress }
    }
}
