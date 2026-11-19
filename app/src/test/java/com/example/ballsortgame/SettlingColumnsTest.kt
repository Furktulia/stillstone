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

    @Test fun contactsNameWhatWasHitGetQuieterAndStopAtRest() {
        val speed = SettlingColumns.impactSpeed(slot * 0.6f, slot)
        val columns = SettlingColumns(1, slot)
        columns.land(0, 1, slot, speed)
        val alone = mutableListOf<com.example.ballsortgame.ui.StoneContact>()
        run(columns) { alone += columns.drainContacts() }
        assertTrue(alone.isNotEmpty() && alone.all { it.kind == com.example.ballsortgame.ui.ContactKind.FLOOR })
        // Arrival speed plus what gravity adds over the last slot of the fall.
        val expectedSpeed = kotlin.math.sqrt(speed * speed + 2f * SettlingColumns.GRAVITY_SLOTS * slot * slot)
        assertEquals(expectedSpeed / slot, alone.first().speedSlots, 0.15f)
        assertTrue(alone.zipWithNext().all { (earlier, later) -> later.speedSlots < earlier.speedSlots })

        columns.land(0, 2, slot, speed)
        val onto = mutableListOf<com.example.ballsortgame.ui.StoneContact>()
        run(columns) { onto += columns.drainContacts() }
        assertEquals(com.example.ballsortgame.ui.ContactKind.STONE, onto.first().kind)
        assertTrue(onto.all { it.speedSlots >= SettlingColumns.MIN_CONTACT_SLOTS })
        repeat(30) { columns.step(frame) }
        assertTrue("resting stones are silent", columns.drainContacts().isEmpty())

        val low = SettlingColumns(1, slot) { _, _ -> slot * 0.2f }
        low.land(0, 1, 0f, SettlingColumns.impactSpeed(slot * 2f, slot))
        val under = mutableListOf<com.example.ballsortgame.ui.StoneContact>()
        run(low, limit = slot * 0.2f) { under += low.drainContacts() }
        assertTrue(under.any { it.kind == com.example.ballsortgame.ui.ContactKind.OVERHEAD })
    }

    /** A stone waiting to drop in must stop one rebounding out of the tube, not be passed through. */
    @Test fun aStoneHoveringOverTheTubeStopsTheReboundBelowIt() {
        val speed = SettlingColumns.impactSpeed(slot * 4f, slot)
        val free = SettlingColumns(1, slot)
        free.land(0, 1, 0f, speed)
        var highest = 0f
        run(free, maxSeconds = 8f) { highest = maxOf(highest, free.offset(0, 0)) }
        assertTrue("fixture must rebound well past the lid", highest > slot * 3f)

        val covered = SettlingColumns(1, slot)
        covered.lid(0, slot * 1.5f)
        covered.land(0, 1, 0f, speed)
        val contacts = mutableListOf<com.example.ballsortgame.ui.StoneContact>()
        run(covered, maxSeconds = 8f, limit = slot * 1.5f) { contacts += covered.drainContacts() }
        assertTrue(contacts.any { it.kind == com.example.ballsortgame.ui.ContactKind.STONE })
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

    @Test fun threeBeadsShareTheLoadAndKeepTheirCompressedSurfacesInContact() {
        val columns = SettlingColumns(1, slot).apply { waterBeads = true }
        val radius = slot * SettlingColumns.BEAD_DIAMETER / 2f
        columns.land(0, 3, 0f, slot * 21f)
        val peak = FloatArray(3)
        val contacts = mutableListOf<com.example.ballsortgame.ui.StoneContact>()
        repeat(1500) { step ->
            columns.step(0.002f)
            contacts += columns.drainContacts()
            val heights = FloatArray(3) { radius * (1f - columns.squash(0, it)) }
            for (i in 0..2) {
                val squash = columns.squash(0, i)
                assertTrue("visible deformation stays within renderer bounds", squash in 0f..0.30f)
                if (step < 40) peak[i] = maxOf(peak[i], squash)
                val center = columns.visibleOffset(0, i) + i * slot
                val lowerSurface = if (i == 0) -radius else
                    columns.visibleOffset(0, i - 1) + (i - 1) * slot + heights[i - 1]
                assertTrue("bead $i overlaps its support", center - heights[i] >= lowerSurface - 0.001f)
                val overlap = if (i == 0) -columns.offset(0, i) else
                    columns.offset(0, i - 1) - columns.offset(0, i) + 2 * radius - slot
                if (overlap > 0.001f) assertEquals(lowerSurface, center - heights[i], 0.001f)
            }
        }
        assertTrue("deformation should diminish up the stack: ${peak.toList()}",
            peak[0] > peak[1] * 1.2f && peak[1] > peak[2] * 1.5f && peak[2] > 0.02f)
        assertEquals(listOf("FLOOR", "STONE", "STONE"), contacts.take(3).map { it.kind.name })
        assertFalse(columns.awake)
    }

    @Test fun beadsSettleAfterSuccessiveGroupsLandOnAnExistingStack() {
        val columns = SettlingColumns(1, slot) { _, _ -> slot * 0.3f }.apply { waterBeads = true }
        columns.sync(0, 2)
        repeat(2) {
            columns.land(0, 2, columns.topOffset(0), slot * 17f)
            repeat(6) { columns.step(frame) }
        }
        repeat(240) { columns.step(frame) }
        assertEquals(6, columns.size(0))
        assertFalse(columns.awake)
        columns.drainContacts()
        repeat(30) { columns.step(frame) }
        assertTrue(columns.drainContacts().isEmpty())
    }

    /** The landing's pulse runs down through beads that already touch; only the new meeting is heard. */
    @Test fun aBeadLandingOnAStackIsOneTapNotAnEcho() {
        fun firstContacts(columns: SettlingColumns): List<com.example.ballsortgame.ui.StoneContact> {
            columns.land(0, 1, columns.topOffset(0), slot * 20f)
            val heard = mutableListOf<com.example.ballsortgame.ui.StoneContact>()
            // The first 0.15 s: the landing itself, before the bead can rebound and come down again.
            repeat(75) { columns.step(0.002f); heard += columns.drainContacts() }
            return heard
        }
        // A stack placed at rest (level start, undo), and one that came to rest under its own weight.
        val placed = SettlingColumns(1, slot).apply { waterBeads = true; sync(0, 2) }
        val settled = SettlingColumns(1, slot).apply { waterBeads = true }
        settled.land(0, 2, 0f, slot * 15f)
        repeat(600) { settled.step(frame) }
        assertFalse(settled.awake)
        settled.drainContacts()
        for (columns in listOf(placed, settled)) {
            val heard = firstContacts(columns)
            assertEquals(heard.toString(), 1, heard.size)
            assertEquals(com.example.ballsortgame.ui.ContactKind.STONE, heard.single().kind)
        }
    }

    @Test fun aDepartingBeadGroupStartsWhereItWasDrawn() {
        val columns = SettlingColumns(1, slot).apply { waterBeads = true; sync(0, 3) }
        val lifts = columns.departing(0, 3, 2)
        // Top stone first, each from its own packed position.
        assertEquals(columns.visibleOffset(0, 2), lifts[0], 0f)
        assertEquals(columns.visibleOffset(0, 1), lifts[1], 0f)
        assertTrue(lifts[0] < lifts[1] - slot * 0.1f)
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
