package com.example.ballsortgame.ui

import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test

class ArtworkLayoutTest {
    private val phones = listOf(360 to 640, 393 to 852, 411 to 914, 480 to 1040)

    @Test fun windowPerimeterHasNoGapsAtAnyPortraitHeight() {
        for ((width, height) in phones) {
            val slices = portraitWindowSlices(922, 1706, width, height)
            assertEquals(8, slices.size)
            fun count(x: Int, y: Int) = slices.count {
                val r = it.destination
                x >= r.x && x < r.x + r.width && y >= r.y && y < r.y + r.height
            }
            for (y in 0 until height) {
                assertEquals("left wall at $width x $height, y=$y", 1, count(0, y))
                assertEquals("right wall at $width x $height, y=$y", 1, count(width - 1, y))
            }
            for (x in 0 until width) {
                assertEquals(1, count(x, 0))
                assertEquals(1, count(x, height - 1))
            }
            assertEquals("opening must remain unpainted", 0, count(width / 2, height / 2))
            val corner = slices.first()
            assertEquals(corner.source.width.toFloat() / corner.source.height,
                corner.destination.width.toFloat() / corner.destination.height, .015f)
        }
    }

    @Test fun badgeAspectRatioSurvivesDifferentButtonShapes() {
        for ((width, height) in listOf(170 to 218, 246 to 250, 300 to 160, 120 to 180)) {
            for ((sourceWidth, sourceHeight) in listOf(1280 to 1280, 1100 to 1000, 800 to 1000)) {
                val r = fittedArtwork(sourceWidth, sourceHeight, width, height)
                assertTrue(r.x >= 0 && r.y >= 0 && r.x + r.width <= width && r.y + r.height <= height)
                assertTrue("same scale on both axes",
                    abs(r.width.toFloat() / sourceWidth - r.height.toFloat() / sourceHeight) < .002f)
            }
        }
    }
}
