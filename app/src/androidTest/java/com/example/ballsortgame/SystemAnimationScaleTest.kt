package com.example.ballsortgame

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.ballsortgame.engine.*
import com.example.ballsortgame.game.*
import com.example.ballsortgame.ui.LandingImpact
import com.example.ballsortgame.ui.PuzzleBoard
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Simulates a phone with system animations removed (animator duration scale 0). */
class SystemAnimationScaleTest {
    private object NoSystemAnimations : MotionDurationScale { override val scaleFactor: Float get() = 0f }
    @get:Rule val compose = createComposeRule(NoSystemAnimations)

    @Test fun stonesStillTravelWhenSystemAnimationsAreRemoved() {
        // A one-stone hop into a nearly full neighbour: the final fall is brief, so the
        // stone can only still be moving half a second later if the approach was animated.
        val before = Board(listOf(Tube(4, listOf(1, 0)), Tube(4, listOf(0, 0, 0)), Tube(4)))
        val move = Move(0, 1)
        val result = requireNotNull(PuzzleRules.transfer(before, move))
        var state by mutableStateOf(GameUiState(loading = false, board = result.board,
            transfers = listOf(MovingGroup(0, before, move, result.count))))
        val impacts = mutableListOf<LandingImpact>()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PuzzleBoard(state, {}, { state = state.copy(transfers = emptyList()) }, Modifier.fillMaxSize(), { impacts.add(it) })
        }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("board_motion")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "1 moving groups"))
        compose.runOnIdle { assertTrue(impacts.isEmpty()) }
        compose.mainClock.advanceTimeBy(4000)
        compose.runOnIdle { assertEquals(1, impacts.size); assertTrue(state.transfers.isEmpty()) }
        compose.mainClock.autoAdvance = true
    }
}
