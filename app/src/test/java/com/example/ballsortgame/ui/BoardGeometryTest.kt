package com.example.ballsortgame.ui

import org.junit.Assert.*
import org.junit.Test

class BoardGeometryTest {
    @Test fun phoneLandscapeAndTabletLayoutsKeepTubesAndTargetsWithinBoard() {
        for ((width, height) in listOf(320f to 400f, 400f to 500f, 560f to 280f, 900f to 700f)) {
            for (capacities in listOf(listOf(3, 3), List(5) { 3 }, List(6) { 4 }, listOf(2, 3, 5, 2, 3))) {
                val layout = boardGeometry(capacities, width, height, 1f)
                assertEquals(capacities.size, layout.size)
                layout.forEachIndexed { index, tube ->
                    assertTrue(tube.hitLeft >= 0 && tube.hitLeft + tube.hitWidth <= width)
                    assertTrue(tube.hitTop >= 0 && tube.hitTop + tube.hitHeight <= height)
                    assertTrue(tube.top >= 0 && tube.bottom <= height)
                    assertTrue(tube.slot > 0)
                    for (piece in 0 until capacities[index]) {
                        assertTrue(tube.pieceY(piece, 1f) > tube.top)
                        assertTrue(tube.pieceY(piece, 1f) < tube.bottom)
                    }
                }
            }
        }
    }

    @Test fun incompleteLastRowIsCentered() {
        val layout = boardGeometry(List(5) { 3 }, 400f, 500f, 1f)
        assertEquals(400f, layout[3].centerX + layout[4].centerX, 0.01f)
    }
}
