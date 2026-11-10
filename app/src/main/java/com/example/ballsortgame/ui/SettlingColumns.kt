package com.example.ballsortgame.ui

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Persistent visual settling for the stones standing in each tube.
 *
 * Every stone has an upward displacement from its resting slot and a velocity.
 * Stones bounce off the tube floor, each other and whatever is overhead, so a
 * group that lands while an earlier group is still in the air strikes it rather
 * than replacing its animation. Presentation only: the logical board never reads this.
 *
 * Units are pixels and seconds; "up" is positive. A stone is identified by its tube
 * and slot index, which is stable for as long as it stays in that tube.
 */
internal class SettlingColumns(
    tubes: Int,
    private val slot: Float,
    /** Furthest a stone in this slot may rise before touching a tube above or the screen edge. */
    private val ceiling: (tube: Int, slot: Int) -> Float = { _, _ -> Float.POSITIVE_INFINITY },
) {
    private class Column {
        val offset = ArrayList<Float>()
        val velocity = ArrayList<Float>()
        val resting = ArrayList<Boolean>()
        var awake = false
    }

    private val columns = List(tubes) { Column() }
    private val gravity = GRAVITY_SLOTS * slot
    private val restSpeed = 0.7f * slot
    private val restGap = 0.01f * slot

    val awake get() = columns.any { it.awake }
    val awakeCount get() = columns.count { it.awake }
    fun awake(tube: Int) = columns[tube].awake
    fun size(tube: Int) = columns[tube].offset.size
    fun offset(tube: Int, slot: Int) = columns[tube].offset.getOrElse(slot) { 0f }
    fun velocity(tube: Int, slot: Int) = columns[tube].velocity.getOrElse(slot) { 0f }
    fun topOffset(tube: Int) = columns[tube].offset.lastOrNull() ?: 0f

    /**
     * Match the number of standing stones. Departing stones leave from the top and the
     * rest keep moving. Stones that appear without [land] (undo, restart, instant
     * motion) have no physical history, so that tube is shown at rest.
     */
    fun sync(tube: Int, count: Int) {
        val column = columns[tube]
        if (count == column.offset.size) return
        if (count < column.offset.size) {
            while (column.offset.size > count) {
                column.offset.removeAt(column.offset.lastIndex)
                column.velocity.removeAt(column.velocity.lastIndex)
                column.resting.removeAt(column.resting.lastIndex)
            }
        } else {
            column.offset.clear(); column.velocity.clear(); column.resting.clear()
            repeat(count) { column.offset.add(0f); column.velocity.add(0f); column.resting.add(true) }
            column.awake = false
        }
    }

    /** A group arrives moving downward at [speed], [height] above its resting slots. */
    fun land(tube: Int, count: Int, height: Float, speed: Float) {
        val column = columns[tube]
        val start = maxOf(height, topOffset(tube), 0f)
        repeat(count) {
            column.offset.add(start); column.velocity.add(-abs(speed)); column.resting.add(false)
        }
        column.awake = true
    }

    /** Advance all moving tubes. Returns whether anything is still moving. */
    fun step(seconds: Float): Boolean {
        val total = seconds.coerceIn(0f, MAX_FRAME)
        columns.forEachIndexed { index, column ->
            if (!column.awake) return@forEachIndexed
            var remaining = total
            while (remaining > 0f && column.awake) {
                val dt = minOf(remaining, SUBSTEP)
                remaining -= dt
                advance(index, column, dt)
            }
        }
        return awake
    }

    private fun advance(tube: Int, column: Column, dt: Float) {
        val o = column.offset; val v = column.velocity; val resting = column.resting
        val n = o.size
        for (i in 0 until n) {
            if (!resting[i]) v[i] = v[i] - gravity * dt
            o[i] = o[i] + v[i] * dt
        }
        repeat(2) {
            // Floor and stone-on-stone contacts, bottom to top.
            for (i in 0 until n) {
                if (i == 0) {
                    if (o[0] < 0f) {
                        o[0] = 0f
                        if (v[0] < 0f) v[0] = -FLOOR_RESTITUTION * v[0]
                    }
                } else if (o[i] < o[i - 1]) {
                    o[i] = o[i - 1]
                    collide(v, i - 1, i)
                }
            }
            // Overhead contact, then push that limit back down the stack.
            for (i in n - 1 downTo 0) {
                val limit = ceiling(tube, i).coerceAtLeast(0f)
                if (o[i] > limit) {
                    o[i] = limit
                    if (v[i] > 0f) v[i] = -CEILING_RESTITUTION * v[i]
                }
                if (i < n - 1 && o[i] > o[i + 1]) {
                    o[i] = o[i + 1]
                    collide(v, i, i + 1)
                }
            }
        }
        var allResting = true
        for (i in 0 until n) {
            val support = if (i == 0) 0f else o[i - 1]
            val supported = i == 0 || resting[i - 1]
            resting[i] = supported && o[i] - support <= restGap && abs(v[i]) <= restSpeed
            if (resting[i]) { o[i] = support; v[i] = 0f } else allResting = false
        }
        if (allResting) column.awake = false
    }

    /** Equal stones meeting along one line: they trade velocity, losing a little. */
    private fun collide(v: MutableList<Float>, lower: Int, upper: Int) {
        val closing = v[lower] - v[upper]
        if (closing <= 0f) return
        val mean = (v[lower] + v[upper]) / 2
        v[lower] = mean - STONE_RESTITUTION * closing / 2
        v[upper] = mean + STONE_RESTITUTION * closing / 2
    }

    companion object {
        /** Tuned so the first hop keeps the pace of the earlier authored rebound. */
        const val GRAVITY_SLOTS = 52f
        const val FLOOR_RESTITUTION = 0.4f
        const val CEILING_RESTITUTION = 0.45f
        const val STONE_RESTITUTION = 0.92f
        private const val SUBSTEP = 0.002f
        private const val MAX_FRAME = 0.05f

        /** Downward arrival speed whose first floor bounce rises [reboundHeight]. */
        fun impactSpeed(reboundHeight: Float, slot: Float): Float =
            sqrt(2 * GRAVITY_SLOTS * slot * reboundHeight.coerceAtLeast(0f)) / FLOOR_RESTITUTION
    }
}
