package com.example.ballsortgame

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.ballsortgame.engine.*
import com.example.ballsortgame.game.*
import com.example.ballsortgame.ui.PuzzleBoard
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class GroupMotionUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun independentGroupsFlyTogetherAndDependentGroupFollowsWithoutLostPieces() {
        var board = Board(listOf(Tube(4, listOf(0, 0)), Tube(4, listOf(0)),
            Tube(4, listOf(1, 1)), Tube(4, listOf(1)), Tube(4)))
        val groups = listOf(Move(0, 1), Move(2, 3), Move(1, 4)).mapIndexed { index, move ->
            val result = requireNotNull(PuzzleRules.transfer(board, move))
            MovingGroup(index.toLong(), board, move, result.count).also { board = result.board }
        }
        var state by mutableStateOf(GameUiState(loading = false, board = board,
            transfer = groups.first(), transfers = groups))
        val completed = mutableListOf<Long>()
        val impacts = mutableListOf<com.example.ballsortgame.ui.LandingImpact>()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PuzzleBoard(state, {}, { id ->
                completed.add(id)
                val remaining = state.transfers.drop(1)
                state = state.copy(transfer = remaining.firstOrNull(), transfers = remaining)
            }, Modifier.fillMaxSize(), { impacts.add(it) })
        }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag("board_motion")
            .assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "2 moving groups"))
        compose.runOnIdle { assertTrue(completed.isEmpty()); assertTrue(impacts.isEmpty()) }
        // The short 0→1 flight lands first. Its dependent 1→4 flight should
        // start during settling, while the long independent 2→3 flight is still moving.
        repeat(70) {
            if (impacts.isEmpty()) compose.mainClock.advanceTimeByFrame()
        }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { assertTrue(impacts.isNotEmpty()); assertTrue(completed.isEmpty()) }
        compose.onNodeWithTag("board_motion")
            .assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "2 moving groups"))
        compose.mainClock.advanceTimeBy(4000)
        compose.runOnIdle {
            assertEquals(listOf(0L, 1L, 2L), completed)
            assertEquals(listOf(2, 2, 3), impacts.map { it.balls })
            assertTrue(impacts.all { it.dropSlots > 0 }) // Real landing geometry reaches the feedback layer.
            assertTrue(state.transfers.isEmpty())
            assertEquals(board, state.board)
        }
        compose.mainClock.autoAdvance = true
    }
}
