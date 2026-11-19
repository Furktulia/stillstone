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
enum class ContactKind { FLOOR, STONE, OVERHEAD }

/** One audible meeting of a stone with something. [speedSlots] is the closing speed in slots per second. */
data class StoneContact(val kind: ContactKind, val tube: Int, val speedSlots: Float, val delaySeconds: Float = 0f)

internal class SettlingColumns(
    tubes: Int,
    private val slot: Float,
    /** Furthest a stone in this slot may rise before touching a tube above or the screen edge. */
    private val ceiling: (tube: Int, slot: Int) -> Float = { _, _ -> Float.POSITIVE_INFINITY },
) {
    var waterBeads = false
        set(value) {
            if (field == value) return
            field = value
            for (column in columns) {
                column.beadQuietTime = 0f
                for (i in column.offset.indices) {
                    val packing = i * slot * (1f - BEAD_DIAMETER)
                    column.offset[i] = if (!column.awake) {
                        if (value) -packing else 0f
                    } else column.offset[i] + if (value) -packing else packing
                }
            }
        }
    private class Column {
        var beadQuietTime = 0f
        val offset = ArrayList<Float>()
        val velocity = ArrayList<Float>()
        val resting = ArrayList<Boolean>()
        var awake = false
    }

    private val columns = List(tubes) { Column() }
    private val gravity = GRAVITY_SLOTS * slot
    private val restSpeed = 0.7f * slot
    private val restGap = 0.01f * slot
    private val contacts = ArrayList<StoneContact>()
    private val lids = FloatArray(tubes) { Float.POSITIVE_INFINITY }

    /**
     * A stone hovering over [tube], still in flight: the tube's top stone may rise at most
     * [limit] above its resting slot before touching it. Infinity when nothing is overhead.
     * Without this a stone rebounding out of its tube passed through one waiting to drop in.
     */
    fun lid(tube: Int, limit: Float) { lids[tube] = limit.coerceAtLeast(0f) }

    /** Contacts since the last call, in the order they happened. For sound and haptics only. */
    fun drainContacts(): List<StoneContact> {
        if (contacts.isEmpty()) return emptyList()
        val first = contacts.minOf { it.delaySeconds }
        return contacts.map { it.copy(delaySeconds = it.delaySeconds - first) }.also { contacts.clear() }
    }

    private fun report(kind: ContactKind, tube: Int, speed: Float, time: Float = 0f) {
        // Stones leaning on each other touch every substep; only real knocks are worth hearing.
        if (speed >= MIN_CONTACT_SLOTS * slot && contacts.size < 64) contacts.add(StoneContact(kind, tube, speed / slot, time))
    }

    val awake get() = columns.any { it.awake }
    val awakeCount get() = columns.count { it.awake }
    fun awake(tube: Int) = columns[tube].awake
    fun size(tube: Int) = columns[tube].offset.size
    fun offset(tube: Int, slot: Int) = columns[tube].offset.getOrElse(slot) { 0f }
    fun velocity(tube: Int, slot: Int) = columns[tube].velocity.getOrElse(slot) { 0f }
    private val beadRadius = slot * BEAD_DIAMETER * 0.5f

    // A floor contact deforms the bead alone. At a bead-to-bead contact the
    // two beads share the compression, so their visible surfaces meet.
    private fun overlap(column: Column, i: Int): Float = when {
        i == 0 -> (-column.offset[0]).coerceAtLeast(0f)
        i < column.offset.size -> (column.offset[i - 1] - column.offset[i] + 2 * beadRadius - slot)
            .coerceAtLeast(0f)
        else -> 0f
    }
    private fun bottomTrim(column: Column, i: Int) = overlap(column, i) * if (i == 0) 1f else 0.5f
    private fun topTrim(column: Column, i: Int) = overlap(column, i + 1) * 0.5f

    fun squash(tube: Int, index: Int): Float {
        if (!waterBeads) return 0f
        val column = columns[tube]
        return (bottomTrim(column, index) + topTrim(column, index)) / (2 * beadRadius)
    }

    fun visibleOffset(tube: Int, index: Int): Float {
        val column = columns[tube]
        val offset = offset(tube, index)
        return if (waterBeads) offset + (bottomTrim(column, index) - topTrim(column, index)) / 2 else offset
    }

    private fun support(tube: Int, i: Int): Float = if (i == 0) 0f else columns[tube].offset[i - 1]

    fun topOffset(tube: Int): Float {
        val top = size(tube) - 1
        if (top < 0) return 0f
        return offset(tube, top) + if (waterBeads) 2 * beadRadius - slot else 0f
    }

    /**
     * Match the number of standing stones. Departing stones leave from the top and the
     * rest keep moving. Stones that appear without [land] (undo, restart, instant
     * motion) have no physical history, so that tube is shown at rest.
     */
    fun sync(tube: Int, count: Int) {
        val column = columns[tube]
        if (count == column.offset.size) return
        if (count < column.offset.size) {
            column.beadQuietTime = 0f
            while (column.offset.size > count) {
                column.offset.removeAt(column.offset.lastIndex)
                column.velocity.removeAt(column.velocity.lastIndex)
                column.resting.removeAt(column.resting.lastIndex)
            }
        } else {
            column.beadQuietTime = 0f
            column.offset.clear(); column.velocity.clear(); column.resting.clear()
            repeat(count) { i -> column.offset.add(if (waterBeads) i * slot * (BEAD_DIAMETER - 1f) else 0f); column.velocity.add(0f); column.resting.add(true) }
            column.awake = false
        }
    }

    /** A group arrives moving downward at [speed], [height] above its resting slots. */
    fun land(tube: Int, count: Int, height: Float, speed: Float) {
        val column = columns[tube]
        val start = maxOf(height, topOffset(tube), if (waterBeads) Float.NEGATIVE_INFINITY else 0f)
        column.beadQuietTime = 0f
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
                if (waterBeads) advanceBeads(index, column, dt, total - remaining) else advance(index, column, dt)
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
                    val floor = support(tube, 0)
                    if (o[0] < floor) {
                        o[0] = floor
                        if (v[0] < 0f) {
                            report(ContactKind.FLOOR, tube, -v[0])
                            v[0] *= -FLOOR_RESTITUTION
                        }
                    }
                } else if (o[i] < support(tube, i)) {
                    o[i] = support(tube, i)
                    collide(tube, v, i - 1, i)
                }
            }
            // Overhead contact, then push that limit back down the stack.
            for (i in n - 1 downTo 0) {
                val fixed = ceiling(tube, i).coerceAtLeast(0f)
                val stone = if (i == n - 1) lids[tube] else Float.POSITIVE_INFINITY
                val limit = minOf(fixed, stone)
                if (o[i] > limit) {
                    o[i] = limit
                    if (v[i] > 0f) {
                        report(if (stone < fixed) ContactKind.STONE else ContactKind.OVERHEAD, tube, v[i])
                        v[i] = -(if (stone < fixed) STONE_RESTITUTION * 0.5f else CEILING_RESTITUTION) * v[i]
                    }
                }
                if (i < n - 1 && support(tube, i + 1) > o[i + 1]) {
                    o[i] -= support(tube, i + 1) - o[i + 1]
                    collide(tube, v, i, i + 1)
                }
            }
        }
        var allResting = true
        for (i in 0 until n) {
            val support = support(tube, i)
            val supported = i == 0 || resting[i - 1]
            resting[i] = supported && o[i] - support <= restGap && abs(v[i]) <= restSpeed
            if (resting[i]) { o[i] = support; v[i] = 0f } else allResting = false
        }
        if (allResting) column.awake = false
    }

    private fun advanceBeads(tube: Int, column: Column, dt: Float, time: Float) {
        val o = column.offset
        val v = column.velocity
        val n = o.size
        if (n == 0) {
            column.awake = false
            return
        }

        // Integrate the whole stack before applying any contact forces. Pairs
        // exchange equal and opposite impulses, and the glass supports the load.
        for (i in 0 until n) {
            v[i] -= gravity * dt
            o[i] += v[i] * dt
        }
        var lowerImpulse = 0f
        for (i in 0 until n) {
            val closing = if (i == 0) -v[i] else v[i - 1] - v[i]
            val depth = overlap(column, i)
            val newContact = depth > 0f && depth <= closing * dt
            if (newContact) report(if (i == 0) ContactKind.FLOOR else ContactKind.STONE, tube, closing, time)
            val upperImpulse = if (depth > 0f) {
                val stiffness = if (i == 0) 16000f else 11000f
                val damping = if (i == 0) 64f else 104f
                // Progressively stiffen a deeply compressed bead to keep its shape legible.
                val force = depth * stiffness * (1f + 6f * depth / slot) + damping * closing
                force.coerceAtLeast(0f) * dt
            } else 0f
            if (i > 0) v[i - 1] += lowerImpulse - upperImpulse
            lowerImpulse = upperImpulse
        }
        v[n - 1] += lowerImpulse

        var quiet = true
        for (i in n - 1 downTo 0) {
            val fixed = ceiling(tube, i).coerceAtLeast(0f) + topTrim(column, i)
            val lid = if (i == n - 1) lids[tube] else Float.POSITIVE_INFINITY
            val limit = minOf(fixed, lid)
            if (o[i] > limit) {
                o[i] = limit
                if (v[i] > 0f) {
                    report(if (lid < fixed) ContactKind.STONE else ContactKind.OVERHEAD, tube, v[i], time)
                    v[i] *= -0.32f
                }
            }
            quiet = quiet && abs(v[i]) < restSpeed && overlap(column, i) > 0f
        }
        column.beadQuietTime = if (quiet) column.beadQuietTime + dt else 0f
        if (column.beadQuietTime >= 0.045f) {
            // Retain the tiny static compression under the stack's weight.
            for (i in 0 until n) {
                v[i] = 0f
                column.resting[i] = true
            }
            column.awake = false
        }
    }

    /** Equal stones meeting along one line: they trade velocity, losing a little. */
    private fun collide(tube: Int, v: MutableList<Float>, lower: Int, upper: Int) {
        val closing = v[lower] - v[upper]
        if (closing <= 0f) return
        report(ContactKind.STONE, tube, closing)
        val mean = (v[lower] + v[upper]) / 2
        v[lower] = mean - STONE_RESTITUTION * closing / 2
        v[upper] = mean + STONE_RESTITUTION * closing / 2
    }

    companion object {
        /** Tuned so the first hop keeps the pace of the earlier authored rebound. */
        const val BEAD_DIAMETER = 0.86f
        const val GRAVITY_SLOTS = 52f
        const val FLOOR_RESTITUTION = 0.70f
        const val CEILING_RESTITUTION = 0.45f
        const val STONE_RESTITUTION = 0.92f
        /** Slowest closing speed, in slots per second, that counts as a contact worth reporting. */
        const val MIN_CONTACT_SLOTS = 2.5f
        private const val SUBSTEP = 0.002f
        private const val MAX_FRAME = 0.05f

        /** Downward arrival speed whose first floor bounce rises [reboundHeight]. */
        fun impactSpeed(reboundHeight: Float, slot: Float): Float =
            sqrt(2 * GRAVITY_SLOTS * slot * reboundHeight.coerceAtLeast(0f)) / FLOOR_RESTITUTION
    }
}
