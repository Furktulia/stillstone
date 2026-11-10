package com.example.ballsortgame

import com.example.ballsortgame.ui.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class TransferMotionTest {
    @Test fun allTubePairsHaveRoutesThatClearInterveningTubesIncludingLowerRows() {
        for ((w, h) in listOf(360f to 530f, 360f to 300f, 760f to 280f, 600f to 600f)) {
            for (capacities in listOf(List(6) { 5 }, List(5) { 3 }, listOf(3, 5, 4, 6, 3, 5))) {
                val layout = boardGeometry(capacities, w, h, 1f)
                for (a in layout.indices) for (b in layout.indices) {
                    if (a == b) continue
                    val route = stoneRoute(layout, a, b, capacities[a] - 1, 0, w, h, 1f)
                    assertNotNull("No route: ${w}x$h $capacities $a>$b", route)
                    val flight = requireNotNull(route)
                    val start = flight.at(0f); val end = flight.at(1f)
                    assertEquals(layout[a].centerX, start.x, 0.01f)
                    assertEquals(layout[a].pieceY(capacities[a] - 1, 1f), start.y, 0.01f)
                    assertEquals(layout[b].pieceY(0, 1f), end.y, 0.01f)
                    for (sample in 0..500) {
                        val point = flight.at(sample / 500f)
                        val radius = layout[a].slot * 0.43f
                        assertTrue(point.x >= radius - 0.1f && point.x <= w - radius + 0.1f)
                        assertTrue(point.y >= radius - 0.1f && point.y <= h - radius + 0.1f)
                        layout.forEachIndexed { index, tube ->
                            // Only the central entrance/exit shaft of the participating tubes is open.
                            if ((index == a || index == b) && abs(point.x - tube.centerX) < 0.01f) return@forEachIndexed
                            val dx = max(0f, abs(point.x - tube.centerX) - tube.width / 2)
                            val dy = maxOf(tube.top - point.y, point.y - tube.bottom, 0f)
                            assertTrue("Stone intersects tube $index on $a>$b at $point",
                                hypot(dx, dy) >= radius - 0.1f)
                        }
                    }
                }
            }
        }
    }

    @Test fun blockedCorridorHasNoUnsafeStraightLineFallback() {
        assertNull(flightCorridor(FlightPoint(10f, 10f), FlightPoint(90f, 90f),
            listOf(FlightObstacle(0f, 40f, 100f, 60f)), 100f, 100f, 5f))
    }
}
