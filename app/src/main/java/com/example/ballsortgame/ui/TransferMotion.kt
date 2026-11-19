package com.example.ballsortgame.ui

import kotlin.math.*

internal data class FlightPoint(val x: Float, val y: Float) {
    fun distance(other: FlightPoint) = hypot(x - other.x, y - other.y)
    fun mix(other: FlightPoint, t: Float) = FlightPoint(x + (other.x - x) * t, y + (other.y - y) * t)
}
internal data class FlightObstacle(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun contains(p: FlightPoint) = p.x > left + 0.01f && p.x < right - 0.01f && p.y > top + 0.01f && p.y < bottom - 0.01f
    fun blocks(a: FlightPoint, b: FlightPoint): Boolean =
        if (a.x == b.x) a.x > left + 0.01f && a.x < right - 0.01f && max(a.y, b.y) > top + 0.01f && min(a.y, b.y) < bottom - 0.01f
        else a.y > top + 0.01f && a.y < bottom - 0.01f && max(a.x, b.x) > left + 0.01f && min(a.x, b.x) < right - 0.01f
}

/** Shortest rectilinear visibility path around ALL tubes, inflated by stone radius. */
internal fun flightCorridor(start: FlightPoint, end: FlightPoint, obstacles: List<FlightObstacle>,
    width: Float, height: Float, radius: Float): List<FlightPoint>? {
    // Prefer alignment before descent when both legs are clear. This has Manhattan
    // minimum length too, but avoids a late sideways turn among equally short routes.
    val aligned = FlightPoint(end.x, start.y)
    val direct = listOf(start, aligned, end).distinct()
    if (direct.all { it.x in radius..(width - radius) && it.y in radius..(height - radius) } &&
        direct.none { point -> obstacles.any { it.contains(point) } } &&
        direct.zipWithNext().all { (a, b) -> obstacles.none { it.blocks(a, b) } }) return direct
    val xs = (obstacles.flatMap { listOf(it.left, it.right) } + listOf(start.x, end.x, radius, width - radius))
        .filter { it >= radius && it <= width - radius }.distinct().sorted()
    val ys = (obstacles.flatMap { listOf(it.top, it.bottom) } + listOf(start.y, end.y, radius, height - radius))
        .filter { it >= radius && it <= height - radius }.distinct().sorted()
    val points = ys.flatMap { y -> xs.map { x -> FlightPoint(x, y) } }
    val from = points.indexOf(start); val to = points.indexOf(end)
    if (from < 0 || to < 0) return null
    val distances = FloatArray(points.size) { Float.POSITIVE_INFINITY }
    val previous = IntArray(points.size) { -1 }
    val queue = java.util.PriorityQueue<Pair<Int, Float>>(compareBy { it.second })
    distances[from] = 0f; queue.add(from to 0f)
    while (queue.isNotEmpty()) {
        val (index, distance) = queue.remove()
        if (distance != distances[index]) continue
        if (index == to) break
        val col = index % xs.size
        val neighbors = listOfNotNull(
            (index - 1).takeIf { col > 0 }, (index + 1).takeIf { col < xs.lastIndex },
            (index - xs.size).takeIf { it >= 0 }, (index + xs.size).takeIf { it < points.size })
        for (next in neighbors) {
            if (obstacles.any { it.contains(points[next]) || it.blocks(points[index], points[next]) }) continue
            val cost = distance + points[index].distance(points[next])
            if (cost < distances[next]) { distances[next] = cost; previous[next] = index; queue.add(next to cost) }
        }
    }
    if (!distances[to].isFinite()) return null
    val result = mutableListOf<FlightPoint>()
    var cursor = to
    while (cursor != -1) { result.add(points[cursor]); cursor = previous[cursor] }
    val ordered = result.reversed()
    return ordered.filterIndexed { i, p ->
        i == 0 || i == ordered.lastIndex ||
            !((ordered[i - 1].x == p.x && p.x == ordered[i + 1].x) ||
                (ordered[i - 1].y == p.y && p.y == ordered[i + 1].y))
    }
}

internal class StoneRoute(points: List<FlightPoint>, rounding: Float) {
    // Round corners only within the extra clearance reserved by the planner.
    val samples = buildList {
        add(points.first())
        for (i in 1 until points.lastIndex) {
            val a = points[i - 1]; val b = points[i]; val c = points[i + 1]
            val r = minOf(rounding, a.distance(b) / 3, b.distance(c) / 3)
            val enter = b.mix(a, r / b.distance(a)); val exit = b.mix(c, r / b.distance(c))
            add(enter)
            for (step in 1..8) {
                val t = step / 8f
                add(enter.mix(b, t).mix(b.mix(exit, t), t))
            }
        }
        add(points.last())
    }
    private val lengths = samples.zipWithNext { a, b -> a.distance(b) }
    val length = lengths.sum()
    // Finish aligning with the tube before releasing the group into free fall.
    private val alignedIndex = samples.indices.lastOrNull { i ->
        i > 0 && (abs(samples[i - 1].x - samples.last().x) > 0.01f || samples[i].y < samples[i - 1].y)
    } ?: 0
    val releaseFraction = if (length > 0) lengths.take(alignedIndex).sum() / length else 0f
    fun at(fraction: Float): FlightPoint {
        var remaining = fraction.coerceIn(0f, 1f) * length
        lengths.forEachIndexed { index, length ->
            if (remaining <= length && length > 0) return samples[index].mix(samples[index + 1], remaining / length)
            remaining -= length
        }
        return samples.last()
    }
}

internal fun stoneRoute(layout: List<TubeGeometry>, source: Int, destination: Int,
    sourceSlot: Int, destinationSlot: Int, width: Float, height: Float, density: Float): StoneRoute? {
    val size = layout[source].slot
    val radius = size * 0.43f
    val clearance = radius + size * 0.14f
    val obstacles = layout.map { FlightObstacle(it.centerX - it.width / 2 - clearance,
        it.top - clearance, it.centerX + it.width / 2 + clearance, it.bottom + clearance) }
    val a = layout[source]; val b = layout[destination]
    val exit = FlightPoint(a.centerX, a.top - clearance)
    val entry = FlightPoint(b.centerX, b.top - clearance)
    val corridor = flightCorridor(exit, entry, obstacles, width, height, radius) ?: return null
    return StoneRoute(listOf(FlightPoint(a.centerX, a.pieceY(sourceSlot, density))) + corridor +
        FlightPoint(b.centerX, b.pieceY(destinationSlot, density)), size * 0.1f)
}
