package com.example.ballsortgame

import com.example.ballsortgame.ui.SettlingColumns
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class SettlingColumnsTest {
    private val slot = 100f
    private val frame = 1f / 60

    /** Runs to rest, checking every frame that stones never overlap, sink or teleport. */
    private fun run(columns: SettlingColumns, maxSeconds: Float = 4f, limit: Float = Float.POSITIVE_INFINITY,
        eachFrame: (Int) -> Unit = {}): Int {
        var frames = 0
        var previous = List(columns.size(0)) { columns.offset(0, it) }
        while (columns.awake && frames * frame < maxSeconds) {
            val fastest = (0 until columns.size(0)).maxOf { abs(columns.velocity(0, it)) }
            columns.step(frame)
            frames++
            val now = List(columns.size(0)) { columns.offset(0, it) }
            now.forEachIndexed { i, offset ->
                assertTrue("stone $i below its slot", offset >= 0f)
                assertTrue("stone $i above the ceiling", offset <= limit + 0.001f)
                if (i > 0) assertTrue("stones $i and ${i - 1} overlap", offset >= now[i - 1] - 0.001f)
                // Continuous motion: no stone may jump further than momentum plus gravity allows.
                val reach = (fastest * 2.2f + SettlingColumns.GRAVITY_SLOTS * slot * frame) * frame + 1f
                assertTrue("stone $i jumped ${offset - previous[i]}", abs(offset - previous[i]) <= reach)
            }
            previous = now
            eachFrame(frames)
        }
        assertFalse("did not settle", columns.awake)
        repeat(columns.size(0)) { assertEquals(0f, columns.offset(0, it), 0f) }
        return frames
    }

    @Test fun singleStoneReboundsToTheIntendedHeightAndComesToRest() {
        val columns = SettlingColumns(1, slot)
        val height = slot * 1.2f
        columns.land(0, 1, 0f, SettlingColumns.impactSpeed(height, slot))
        var peak = 0f
        var firstFrame = 0f
        val frames = run(columns) { n ->
            if (n == 1) firstFrame = columns.offset(0, 0)
            peak = maxOf(peak, columns.offset(0, 0))
        }
        assertTrue("rebound must start immediately", firstFrame > height * 0.05f)
        assertEquals(height, peak, height * 0.08f)
        assertTrue(frames * frame in 0.3f..2f)
    }

    @Test fun groupOnARestingStackReboundsWhileTheStackStaysPut() {
        val columns = SettlingColumns(1, slot)
        columns.sync(0, 2)
        val height = slot
        columns.land(0, 3, 0f, SettlingColumns.impactSpeed(height, slot))
        var peak = 0f
        var disturbed = 0f
        run(columns) {
            peak = maxOf(peak, columns.offset(0, 4))
            disturbed = maxOf(disturbed, columns.offset(0, 1))
        }
        assertTrue("landed group should rebound, peak $peak", peak > height * 0.5f)
        assertTrue("resting stones should barely move, moved $disturbed", disturbed < slot * 0.2f)
    }

    @Test fun overheadTubeIsStruckInsteadOfPassedThrough() {
        val limit = slot * 0.4f
        val columns = SettlingColumns(1, slot) { _, _ -> limit }
        columns.land(0, 1, 0f, SettlingColumns.impactSpeed(slot * 2f, slot))
        var struck = false
        var wasRising = false
        run(columns, limit = limit) {
            val rising = columns.velocity(0, 0) > 0f
            // Thrown back down from the ceiling while it still had upward speed.
            if (wasRising && !rising && columns.velocity(0, 0) < -slot) struck = true
            wasRising = rising
        }
        assertTrue("stone should hit the tube above and be thrown back", struck)
    }

    @Test fun secondGroupStrikesTheFirstInTheAirWithoutCancellingIt() {
        val columns = SettlingColumns(1, slot)
        columns.land(0, 2, 0f, SettlingColumns.impactSpeed(slot * 1.5f, slot))
        repeat(9) { columns.step(frame) }
        val inAir = columns.offset(0, 1)
        assertTrue("first group should be airborne", inAir > slot * 0.5f)
        // The next group meets the first where it actually is.
        columns.land(0, 1, columns.topOffset(0), SettlingColumns.impactSpeed(slot, slot))
        assertEquals(inAir, columns.offset(0, 1), 0f)
        assertEquals(inAir, columns.offset(0, 2), 0f)
        columns.step(frame)
        assertTrue("first group must still be moving", columns.offset(0, 1) > slot * 0.2f)
        run(columns)
    }

    @Test fun departingStonesLeaveTheRestMovingAndUnexplainedStonesStartAtRest() {
        val columns = SettlingColumns(2, slot)
        columns.land(0, 3, 0f, SettlingColumns.impactSpeed(slot, slot))
        repeat(5) { columns.step(frame) }
        val lower = columns.offset(0, 0)
        columns.sync(0, 1)
        assertEquals(1, columns.size(0))
        assertEquals(lower, columns.offset(0, 0), 0f)
        assertTrue(columns.awake(0))
        columns.sync(0, 4) // Undo or restart: no physical history.
        assertFalse(columns.awake(0))
        repeat(4) { assertEquals(0f, columns.offset(0, it), 0f) }
        assertFalse(columns.awake(1))
    }

    @Test fun manyRapidArrivalsStillSettle() {
        val columns = SettlingColumns(1, slot) { _, index -> slot * (6 - index) }
        repeat(5) { n ->
            columns.land(0, 1, columns.topOffset(0), SettlingColumns.impactSpeed(slot * (1 + n % 3), slot))
            repeat(6) { columns.step(frame) }
        }
        run(columns, maxSeconds = 6f, limit = slot * 6)
    }
}
