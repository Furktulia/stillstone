package com.example.ballsortgame.engine

import org.junit.Assert.*
import org.junit.Test

class PuzzleRulesTest {
    @Test fun movesEntireMatchingTopGroup() {
        val board = Board(listOf(Tube(5, listOf(2, 0, 0, 0)), Tube(5, listOf(0))))
        val result = requireNotNull(PuzzleRules.transfer(board, Move(0, 1)))
        assertEquals(3, result.count)
        assertEquals(listOf(2), result.board.tubes[0].balls)
        assertEquals(listOf(0, 0, 0, 0), result.board.tubes[1].balls)
        assertEquals(listOf(2, 0, 0, 0), board.tubes[0].balls)
    }

    @Test fun destinationCapacityLimitsTransfer() {
        val board = Board(listOf(Tube(6, listOf(1, 0, 0, 0, 0)), Tube(3, listOf(0, 0))))
        val result = requireNotNull(PuzzleRules.transfer(board, Move(0, 1)))
        assertEquals(1, result.count)
        assertEquals(listOf(1, 0, 0, 0), result.board.tubes[0].balls)
        assertTrue(result.board.tubes[1].isComplete)
    }

    @Test fun cannotCrossColorBoundaryInSource() {
        val board = Board(listOf(Tube(5, listOf(0, 1, 0)), Tube(5)))
        assertEquals(1, requireNotNull(PuzzleRules.transfer(board, Move(0, 1))).count)
    }

    @Test fun rejectsIllegalMovesWithoutChangingBoard() {
        val board = Board(listOf(Tube(3, listOf(0)), Tube(3, listOf(1)), Tube(1, listOf(0)), Tube(3)))
        for (move in listOf(Move(0, 0), Move(-1, 1), Move(0, 4), Move(3, 0), Move(0, 1), Move(0, 2))) {
            assertNull("$move", PuzzleRules.transfer(board, move))
        }
    }

    @Test fun completedTubesAreMovableIncludingIntoSmallerTubes() {
        val board = Board(listOf(Tube(3, listOf(0, 0, 0)), Tube(1)))
        val result = requireNotNull(PuzzleRules.transfer(board, Move(0, 1)))
        assertEquals(1, result.count)
        assertEquals(listOf(0, 0), result.board.tubes[0].balls)
    }

    @Test fun completionRequiresFullMonochromeTubes() {
        assertTrue(Board(listOf(Tube(3, listOf(0, 0, 0)), Tube(6))).isSolved)
        assertFalse(Board(listOf(Tube(3, listOf(0, 0)), Tube(3))).isSolved)
        assertFalse(Board(listOf(Tube(3, listOf(0, 1, 0)), Tube(3))).isSolved)
        // Mixed-capacity semantics: full monochrome tubes, without assigning colors to slots.
        assertTrue(Board(listOf(Tube(1, listOf(0)), Tube(2, listOf(1, 1)), Tube(5))).isSolved)
    }

    @Test fun inputCollectionsAreDefensivelyCopiedAndCannotBeMutated() {
        val pieces = mutableListOf(0, 0)
        val tube = Tube(3, pieces)
        pieces.clear()
        val tubes = mutableListOf(tube)
        val board = Board(tubes)
        tubes.clear()
        assertEquals(listOf(0, 0), board.tubes.single().balls)
        assertThrows(UnsupportedOperationException::class.java) {
            (tube.balls as MutableList<Int>).add(1)
        }
        assertThrows(UnsupportedOperationException::class.java) {
            (board.tubes as MutableList<Tube>).clear()
        }
    }

    @Test fun validatesMalformedBoards() {
        assertThrows(IllegalArgumentException::class.java) { Tube(0) }
        assertThrows(IllegalArgumentException::class.java) { Tube(1, listOf(0, 0)) }
        assertThrows(IllegalArgumentException::class.java) { Tube(3, listOf(-1)) }
        assertThrows(IllegalArgumentException::class.java) { Board(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { Board(listOf(Tube(3))) }
    }

    @Test fun certificatesRejectIllegalAndIncompletePaths() {
        val board = Board(listOf(Tube(2, listOf(0)), Tube(2, listOf(0))))
        assertTrue(PuzzleRules.verifiesSolution(board, listOf(Move(0, 1))))
        assertFalse(PuzzleRules.verifiesSolution(board, emptyList()))
        assertFalse(PuzzleRules.verifiesSolution(board, listOf(Move(0, 8))))
    }

    @Test fun symmetryRespectsCapacityAndMultiDigitColors() {
        val a = Board(listOf(Tube(2, listOf(1)), Tube(3, listOf(12))))
        val reordered = Board(a.tubes.reversed())
        val different = Board(listOf(Tube(2, listOf(12)), Tube(3, listOf(1))))
        assertEquals(a.canonicalKey(), reordered.canonicalKey())
        assertNotEquals(a.canonicalKey(), different.canonicalKey())
        assertNotEquals(Tube(3, listOf(1, 23)), Tube(3, listOf(12, 3)))
    }
}
