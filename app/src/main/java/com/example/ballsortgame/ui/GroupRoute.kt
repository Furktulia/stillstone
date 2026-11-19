package com.example.ballsortgame.ui

import kotlin.math.*

/** One shared spine, with chord-distance spacing around bends rather than independent clocks. */
internal class GroupRoute(val spine: StoneRoute, val count: Int, val slot: Float) {
    private val leadingStart = (count - 1) * slot
    val length = (spine.length - leadingStart).coerceAtLeast(0f)
    val releaseFraction = if (length > 0)
        ((spine.releaseFraction * spine.length - leadingStart) / length).coerceIn(0f, 1f) else 0f
    val fallHeight = length * (1f - releaseFraction)

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

/**
 * The space this group still needs: from its last stone to [ahead] beyond its first, along
 * its route. Pass infinity to claim the whole remaining route down into the destination.
 */
internal fun GroupRoute.claimed(fraction: Float, ahead: Float): List<FlightPoint> {
    val tail = length * fraction.coerceIn(0f, 1f)
    val end = minOf(spine.length, tail + (count - 1) * slot + ahead)
    val step = slot * 0.4f
    val points = ArrayList<FlightPoint>()
    var distance = tail
    while (distance < end) { points.add(spine.at(distance / spine.length)); distance += step }
    points.add(spine.at(end / spine.length))
    return points
}

/**
 * Whether moving from [current] to [next] would take a later group into space an earlier
 * group has [claimed]. The later group then holds its place until the earlier one has gone by.
 *
 * Only ever asked of a later move about an earlier one, so two groups cannot wait for each
 * other. A group that is already inside the claimed space is not held (it would otherwise
 * be stuck in the way); the stones then overlap as they did before this rule existed.
 */
internal fun entersClaimedSpace(current: List<FlightPoint>, next: List<FlightPoint>,
    claimed: List<FlightPoint>, slot: Float): Boolean {
    val before = clearance(current, claimed)
    val after = clearance(next, claimed)
    // "Already inside" means well inside. A group held right at the edge must stay held
    // while the earlier group moves along: treating the edge as inside let it through.
    return after < slot * 0.95f && before >= slot * 0.7f && after <= before
}

/** Smallest distance from any stone to the claimed line, measured to the line itself, not its samples. */
private fun clearance(stones: List<FlightPoint>, line: List<FlightPoint>): Float {
    var best = Float.POSITIVE_INFINITY
    for (stone in stones) {
        if (line.size == 1) best = min(best, line[0].distance(stone))
        for (i in 0 until line.size - 1) {
            val a = line[i]; val b = line[i + 1]
            val dx = b.x - a.x; val dy = b.y - a.y
            val length = dx * dx + dy * dy
            val t = if (length == 0f) 0f else (((stone.x - a.x) * dx + (stone.y - a.y) * dy) / length).coerceIn(0f, 1f)
            best = min(best, hypot(stone.x - (a.x + dx * t), stone.y - (a.y + dy * t)))
        }
    }
    return best
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
