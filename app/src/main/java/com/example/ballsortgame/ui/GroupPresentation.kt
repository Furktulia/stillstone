package com.example.ballsortgame.ui

import com.example.ballsortgame.engine.Board
import com.example.ballsortgame.game.MovingGroup

internal enum class FlightPhase { WAITING, FLYING, LANDED }
internal data class GroupFrame(val group: MovingGroup, val phase: FlightPhase)

/**
 * A move starts as soon as its own stones are standing in its source tube.
 *
 * - An earlier move that has not left yet blocks any move sharing one of its tubes, so
 *   stones leave each tube in the order the moves were made.
 * - An earlier move still in the air blocks only a move that would lift the very stones it
 *   is delivering. Taking the next group from the same source, pouring into the tube it
 *   left, or following it into the same destination can all begin at once;
 *   [mustLandAfter] keeps arrivals in one tube in order.
 */
internal fun canStartFlight(group: MovingGroup, earlier: List<GroupFrame>): Boolean =
    earlier.none { frame ->
        val move = frame.group.move
        when (frame.phase) {
            FlightPhase.LANDED -> false
            FlightPhase.FLYING -> move.destination == group.move.source
            FlightPhase.WAITING -> move.source == group.move.source || move.destination == group.move.source ||
                move.source == group.move.destination || move.destination == group.move.destination
        }
    }

/** Earlier groups heading for the same tube must touch down first: this group rests on top of them. */
internal fun mustLandAfter(group: MovingGroup, earlier: List<MovingGroup>): List<MovingGroup> =
    earlier.filter { it.move.destination == group.move.destination }

/** Reconstruct stationary pieces without duplicating stones already in flight. */
internal fun stationaryPieces(frames: List<GroupFrame>, board: Board): List<List<Int>> {
    val tubes = (frames.firstOrNull()?.group?.before ?: board).tubes.map { it.balls.toMutableList() }
    for ((group, phase) in frames) {
        if (phase == FlightPhase.WAITING) continue
        repeat(group.count) { tubes[group.move.source].removeAt(tubes[group.move.source].lastIndex) }
        if (phase == FlightPhase.LANDED) repeat(group.count) {
            tubes[group.move.destination].add(requireNotNull(group.before.tubes[group.move.source].top))
        }
    }
    return tubes
}
