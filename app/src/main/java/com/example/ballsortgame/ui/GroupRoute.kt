package com.example.ballsortgame.ui

import kotlin.math.*

/** One shared spine, with chord-distance spacing around bends rather than independent clocks. */
internal class GroupRoute(val spine: StoneRoute, val count: Int, val slot: Float) {
    private val leadingStart = (count - 1) * slot
    val length = (spine.length - leadingStart).coerceAtLeast(0f)
    val fallHeight = spine.fallHeight
    val releaseFraction = if (length > 0)
        ((spine.releaseFraction * spine.length - leadingStart) / length).coerceIn(0f, 1f) else 0f

    fun at(fraction: Float): List<FlightPoint> {
        val positions = mutableListOf<FlightPoint>()
        var previous = leadingStart + length * fraction.coerceIn(0f, 1f)
        fun point(distance: Float) = spine.at(distance / spine.length)
        repeat(count) { index ->
            var distance = if (index == 0) previous else (previous - slot).coerceAtLeast(0f)
            fun clear(candidate: Float) = positions.all { it.distance(point(candidate)) >= slot * 0.90f - 0.001f }
            if (!clear(distance)) {
                var blocked = distance
                while (distance > 0f && !clear(distance)) {
                    blocked = distance
                    distance = (distance - slot * 0.05f).coerceAtLeast(0f)
                }
                // Find the closest non-overlapping position continuously around a turn.
                if (clear(distance)) repeat(12) {
                    val mid = (distance + blocked) / 2
                    if (clear(mid)) distance = mid else blocked = mid
                }
            }
            positions.add(point(distance))
            previous = distance
        }
        return positions
    }
}

/** Upward clearance for the whole landed stack; the destination's open rim is not a ceiling. */
internal fun reboundClearance(layout: List<TubeGeometry>, destination: Int, topSlot: Int, density: Float): Float {
    val tube = layout[destination]
    val radius = tube.slot * 0.43f
    val y = tube.pieceY(topSlot, density)
    var free = y - radius - 2 * density
    layout.forEachIndexed { index, other ->
        if (index != destination && abs(other.centerX - tube.centerX) < other.width / 2 + radius &&
            other.bottom < y - radius) free = min(free, y - other.bottom - radius - 2 * density)
    }
    return free.coerceAtLeast(0f)
}
