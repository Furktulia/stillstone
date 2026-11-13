package com.example.ballsortgame.ui

import org.junit.Assert.*
import org.junit.Test

class VariantGeometryTest {
    @Test fun connectedTubesHaveReachableRoutesAndDistinctTouchAreas() {
        for ((width, height) in listOf(360f to 300f, 393f to 550f)) {
            val layout = connectedGeometry(List(5) { 2 }, setOf(0, 1), 2, width, height, 1f)
            layout.forEach { tube ->
                assertTrue(tube.hitLeft >= 0)
                assertTrue(tube.hitLeft + tube.hitWidth <= width)
                assertTrue(tube.hitTop >= 0)
                assertTrue(tube.hitTop + tube.hitHeight <= height)
            }
            for (source in layout.indices) for (destination in layout.indices) {
                if (source == destination) continue
                if (source != 2 && destination != 2 && (source < 2) != (destination < 2)) continue
                assertNotNull("No route $source -> $destination at $width x $height",
                    stoneRoute(layout, source, destination, 0, 0, width, height, 1f))
            }
        }
    }
}
