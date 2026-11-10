package com.example.ballsortgame

import com.example.ballsortgame.engine.*
import com.example.ballsortgame.game.MovingGroup
import com.example.ballsortgame.ui.*
import org.junit.Assert.*
import org.junit.Test

class GroupPresentationTest {
    private val initial = Board(listOf(Tube(4, listOf(0, 0)), Tube(4, listOf(0)),
        Tube(4, listOf(1, 1)), Tube(4, listOf(1)), Tube(4)))
    private val groups = buildList {
        var board = initial
        for ((i, move) in listOf(Move(0, 1), Move(2, 3), Move(1, 4)).withIndex()) {
            val result = requireNotNull(PuzzleRules.transfer(board, move))
            add(MovingGroup(i.toLong(), board, move, result.count)); board = result.board
        }
    }
    @Test fun independentGroupsOverlapButSharedTubesWaitOnlyUntilLanding() {
        val first = GroupFrame(groups[0], FlightPhase.FLYING)
        assertTrue(canStartFlight(groups[1], listOf(first)))
        assertFalse(canStartFlight(groups[2], listOf(first, GroupFrame(groups[1], FlightPhase.FLYING))))
        assertTrue(canStartFlight(groups[2], listOf(first.copy(phase = FlightPhase.LANDED),
            GroupFrame(groups[1], FlightPhase.FLYING))))
    }
    @Test fun stationaryAndFlyingPiecesConserveColorsAcrossOverlappingPhases() {
        val frames = groups.map { GroupFrame(it, FlightPhase.WAITING) }.toMutableList()
        val original = initial.tubes.flatMap { it.balls }.sorted()
        fun checkConservation() {
            val stationary = stationaryPieces(frames, initial).flatten()
            val moving = frames.filter { it.phase == FlightPhase.FLYING }.flatMap { frame ->
                List(frame.group.count) { frame.group.before.tubes[frame.group.move.source].top!! }
            }
            assertEquals(original, (stationary + moving).sorted())
        }
        checkConservation()
        frames[0] = frames[0].copy(phase = FlightPhase.FLYING); checkConservation()
        frames[1] = frames[1].copy(phase = FlightPhase.FLYING); checkConservation()
        frames[1] = frames[1].copy(phase = FlightPhase.LANDED); checkConservation() // Out of order.
        frames[0] = frames[0].copy(phase = FlightPhase.LANDED); checkConservation()
        frames[2] = frames[2].copy(phase = FlightPhase.FLYING); checkConservation()
        frames[2] = frames[2].copy(phase = FlightPhase.LANDED); checkConservation()
        val final = PuzzleRules.replay(initial, groups.map { it.move })
        assertEquals(final.tubes.map { it.balls }, stationaryPieces(frames, initial))
        // Once a completed prefix retires, reconstruction must remain identical.
        assertEquals(final.tubes.map { it.balls }, stationaryPieces(frames.drop(2), final))
    }
}
