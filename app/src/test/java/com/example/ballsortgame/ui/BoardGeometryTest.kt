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

    @Test fun fullSizeBoardsAreUnchangedAndShortBoardsGetLargerStones() {
        // Six five-slot tubes on a normal phone board: same stone size as the original fixed spacing.
        val normal = boardGeometry(List(6) { 5 }, 371f, 560f, 1f)
        assertEquals((560f - 52f * 2 - 40f) / 2 + 40f, normal[3].top - normal[0].top, 0.5f)
        assertEquals(((560f - 52f * 2 - 40f) / 2 - 28f) / 5, normal[0].slot, 0.05f)
        // The same puzzle with large text on a small phone leaves a much shorter board.
        val compact = boardGeometry(List(6) { 5 }, 320f, 330f, 1f)
        assertTrue("slot ${compact[0].slot}", compact[0].slot >= 18.5f)
        assertTrue(compact[0].top - compact[0].slot >= 0f) // Room for a stone to travel above the top row.
        assertTrue(compact[3].top - compact[0].bottom >= compact[0].slot * 1.14f)
        assertTrue(compact[5].bottom + 21f <= 330f) // Tube number still fits below the last row.
    }

    @Test fun largeScreensUseMoreColumnsForBiggerStonesAndStayInside() {
        // Board area of an unfolded foldable held sideways (beside the control rail), in dp at density 1.
        val capacities = listOf(4, 4, 4, 4, 4, 4, 4)
        val phone = boardGeometry(capacities, 526f, 640f, 1f)
        val large = boardGeometry(capacities, 526f, 640f, 1f, large = true)
        assertTrue(large[0].slot > phone[0].slot)
        assertTrue(large.map { it.bottom }.distinct().size < phone.map { it.bottom }.distinct().size)
        for (tube in large) {
            assertTrue(tube.hitLeft >= 0 && tube.hitLeft + tube.hitWidth <= 526f)
            assertTrue(tube.top >= 0 && tube.bottom <= 640f)
            assertTrue(tube.slot <= 52f)
        }
    }

    @Test fun incompleteLastRowIsCentered() {
        val layout = boardGeometry(List(5) { 3 }, 400f, 500f, 1f)
        assertEquals(400f, layout[3].centerX + layout[4].centerX, 0.01f)
    }
}
