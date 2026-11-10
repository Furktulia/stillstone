package com.example.ballsortgame.ui

import com.example.ballsortgame.engine.Board
import com.example.ballsortgame.game.MovingGroup

internal enum class FlightPhase { WAITING, FLYING, LANDED }
internal data class GroupFrame(val group: MovingGroup, val phase: FlightPhase)

/** Independent tubes can animate concurrently; shared tubes preserve logical move order. */
internal fun canStartFlight(group: MovingGroup, earlier: List<GroupFrame>): Boolean =
    earlier.none { frame ->
        frame.phase != FlightPhase.LANDED && (
            frame.group.move.source == group.move.source || frame.group.move.destination == group.move.source ||
            frame.group.move.source == group.move.destination || frame.group.move.destination == group.move.destination)
    }

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
