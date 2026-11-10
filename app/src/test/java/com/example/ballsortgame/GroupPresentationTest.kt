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
    @Test fun movesSharingATubeStartAtOnceUnlessTheirStonesAreStillArriving() {
        val board = Board(listOf(Tube(4, listOf(0, 1, 0)), Tube(4, listOf(1, 0)), Tube(4, listOf(1)), Tube(4)))
        fun group(id: Long, before: Board, move: Move) =
            MovingGroup(id, before, move, requireNotNull(PuzzleRules.transfer(before, move)).count)
        val first = group(0, board, Move(0, 3))                    // coral from tube 0 into the empty tube
        val after = PuzzleRules.replay(board, listOf(first.move))
        val flying = listOf(GroupFrame(first, FlightPhase.FLYING))
        val waiting = listOf(GroupFrame(first, FlightPhase.WAITING))
        val sameDestination = group(1, after, Move(1, 3))          // another coral into that tube
        val sameSource = group(1, after, Move(0, 2))               // the next group out of tube 0
        val intoVacated = group(1, after, Move(2, 0))              // mint poured into the tube it left
        val liftsArriving = group(1, after, Move(3, 1))            // the arriving coral itself
        assertTrue(canStartFlight(sameDestination, flying))
        assertTrue(canStartFlight(sameSource, flying))
        assertTrue(canStartFlight(intoVacated, flying))
        assertFalse(canStartFlight(liftsArriving, flying))
        assertTrue(canStartFlight(liftsArriving, listOf(GroupFrame(first, FlightPhase.LANDED))))
        // Nothing overtakes a move that has not left yet.
        for (later in listOf(sameDestination, sameSource, intoVacated, liftsArriving)) assertFalse(canStartFlight(later, waiting))
        assertEquals(listOf(first), mustLandAfter(sameDestination, listOf(first)))
        assertTrue(mustLandAfter(sameSource, listOf(first)).isEmpty())
        // Reconstruction stays consistent while both are in the air and in either landing order.
        val frames = mutableListOf(GroupFrame(first, FlightPhase.FLYING), GroupFrame(sameSource, FlightPhase.FLYING))
        assertEquals(listOf(listOf(0), listOf(1, 0), listOf(1), emptyList()), stationaryPieces(frames, after))
        frames[1] = frames[1].copy(phase = FlightPhase.LANDED)
        assertEquals(listOf(listOf(0), listOf(1, 0), listOf(1, 1), emptyList()), stationaryPieces(frames, after))
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
