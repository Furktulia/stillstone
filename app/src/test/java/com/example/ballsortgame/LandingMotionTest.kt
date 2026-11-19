package com.example.ballsortgame

import com.example.ballsortgame.ui.*
import org.junit.Assert.*
import org.junit.Test

class LandingMotionTest {
    @Test fun fallAcceleratesThroughContactInsteadOfEasingToAStop() {
        val release = 0.7f
        val early = LandingMotion.distance(0.60f, release) - LandingMotion.distance(0.59f, release)
        val late = LandingMotion.distance(0.81f, release) - LandingMotion.distance(0.80f, release)
        assertTrue(late > early * 3)
        assertEquals(1f, LandingMotion.distance(LandingMotion.contact, release), 0.00001f)
        assertTrue(LandingMotion.distance(0.819f, release) < 1f)
    }
    @Test fun aNearlyFullTubeLeavesLessDistanceToAccelerate() {
        val layout = boardGeometry(List(6) { 4 }, 360f, 530f, 1f)
        fun drop(occupied: Int): Float {
            val spine = requireNotNull(stoneRoute(layout, 0, 1, 3, occupied, 360f, 530f, 1f))
            return GroupRoute(spine, 1, layout[0].slot).fallHeight
        }
        assertTrue(drop(0) > drop(3) + layout[0].slot * 2.5f)
    }

    @Test fun beadContactDeformsBothSurfacesAndSettlesAtRest() {
        val beads = SettlingColumns(1, 40f)
        beads.waterBeads = true
        beads.sync(0, 1)
        beads.land(0, 1, 5f, 400f)
        var lower = 0f
        var upper = 0f
        repeat(480) {
            beads.step(1f / 240f)
            lower = maxOf(lower, beads.squash(0, 0))
            upper = maxOf(upper, beads.squash(0, 1))
        }
        assertTrue(lower > upper && upper > 0.02f)
        assertFalse(beads.awake)
        val diameter = 40f * SettlingColumns.BEAD_DIAMETER
        val lowerRadius = diameter / 2f * (1f - beads.squash(0, 0))
        val upperRadius = diameter / 2f * (1f - beads.squash(0, 1))
        assertEquals(lowerRadius + upperRadius, 40f + beads.visibleOffset(0, 1) - beads.visibleOffset(0, 0), 0.01f)
        assertTrue(beads.squash(0, 0) < 0.02f && beads.squash(0, 1) < 0.02f)
    }

    @Test fun dusk25PurplePairStaysBelowTheTubeAboveItsDestination() {
        val layout = connectedGeometry(List(7) { 3 }, setOf(0, 1), 2, 372f, 560f, 1f)
        val spine = requireNotNull(stoneRoute(layout, 3, 6, 1, 0, 372f, 560f, 1f))
        val slot = layout[6].slot
        val drop = GroupRoute(spine, 2, slot).fallHeight
        val column = SettlingColumns(1, slot)
        column.land(0, 2, 0f, LandingMotion.fallSpeed(drop, slot))
        var peak = 0f
        repeat(240) {
            column.step(1f / 240f)
            peak = maxOf(peak, column.topOffset(0))
        }
        assertTrue(peak > 0f)
        assertTrue(peak < minOf(drop, reboundClearance(layout, 6, 1, 1f)))
    }

    @Test fun twoStonesBounceFromTheirFinalDropAfterASlowTurn() {
        val slot = 40f
        val route = GroupRoute(StoneRoute(listOf(FlightPoint(0f, 0f), FlightPoint(0f, 300f),
            FlightPoint(100f, 300f), FlightPoint(100f, 380f)), 5f), 2, slot)
        assertTrue(route.fallHeight in 74f..81f)
        val column = SettlingColumns(1, slot)
        val remaining = route.fallHeight * 0.15f
        column.land(0, 2, remaining, LandingMotion.fallSpeed(route.fallHeight - remaining, slot))
        var peak = 0f
        repeat(240) {
            column.step(1f / 240f)
            peak = maxOf(peak, column.topOffset(0))
        }
        assertTrue(peak > remaining && peak < route.fallHeight)
    }

    @Test fun earlyAlignmentCountsTheWholeVerticalDescent() {
        val spine = StoneRoute(listOf(FlightPoint(0f, 0f), FlightPoint(120f, 0f),
            FlightPoint(120f, 300f), FlightPoint(120f, 400f)), 0f)
        assertEquals(400f, GroupRoute(spine, 2, 40f).fallHeight, 0.01f)
        val straight = StoneRoute(listOf(FlightPoint(0f, 50f), FlightPoint(0f, 0f),
            FlightPoint(0f, 400f)), 0f)
        assertEquals(400f, GroupRoute(straight, 1, 40f).fallHeight, 0.01f)
    }
}
