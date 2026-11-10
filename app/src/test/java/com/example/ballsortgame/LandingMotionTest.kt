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
    @Test fun deepDropHasVisibleImmediateReboundAndEndsExactlyAtRest() {
        val slot = 44f
        val shallow = LandingMotion.reboundHeight(slot, slot, 1)
        val deep = LandingMotion.reboundHeight(slot * 4, slot, 1)
        assertTrue(deep > shallow * 2)
        assertTrue(deep >= slot * 0.5f)
        assertEquals(0f, LandingMotion.rebound(LandingMotion.contact, deep), 0.00001f)
        val afterOneFrame = LandingMotion.contact + (1 - LandingMotion.contact) * 16f / LandingMotion.settlingMillis(deep, slot)
        assertTrue(LandingMotion.rebound(afterOneFrame, deep) > deep * 0.07f)
        assertEquals(deep, LandingMotion.rebound(LandingMotion.contact + (1 - LandingMotion.contact) / 1.4f / 2, deep), 0.0001f)
        assertEquals(0f, LandingMotion.rebound(1f, deep), 0.00001f)
        assertTrue(LandingMotion.reboundHeight(slot * 4, slot, 4) < deep)
    }
    @Test fun destinationOccupancyChangesTheMeasuredFallAndRebound() {
        val layout = boardGeometry(List(6) { 4 }, 360f, 530f, 1f)
        val empty = requireNotNull(stoneRoute(layout, 0, 1, 3, 0, 360f, 530f, 1f))
        val nearlyFull = requireNotNull(stoneRoute(layout, 0, 1, 3, 3, 360f, 530f, 1f))
        assertTrue(empty.fallHeight > nearlyFull.fallHeight + layout[0].slot * 2.5f)
        assertTrue(LandingMotion.reboundHeight(empty.fallHeight, layout[0].slot, 1) >
            LandingMotion.reboundHeight(nearlyFull.fallHeight, layout[0].slot, 1))
    }
}
