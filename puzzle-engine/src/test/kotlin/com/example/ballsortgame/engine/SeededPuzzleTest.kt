package com.example.ballsortgame.engine
import org.junit.Assert.*
import org.junit.Test
class SeededPuzzleTest {
    /** A generator change must preserve E1 or introduce a new seed-code version. */
    @Test fun publishedCodeKeepsItsLiteralBoardAndWinningPath() {
        val level = SeededPuzzle.create("E1-C-2")
        assertEquals(Board(listOf(
            Tube(5, listOf(0, 1, 1, 3)), Tube(5, listOf(1, 1, 1, 3)),
            Tube(5, listOf(2, 2, 0)), Tube(5, listOf(3, 3, 4, 4, 0)),
            Tube(5, listOf(4, 4, 4, 0)), Tube(5, listOf(0, 2, 2, 2, 3)),
        )), level.board)
        assertEquals(listOf(
            Move(3, 2), Move(4, 2), Move(3, 4), Move(1, 0), Move(0, 3),
            Move(0, 1), Move(2, 0), Move(5, 3), Move(5, 2), Move(5, 0),
        ), level.solution)
    }
    @Test fun codesReproduceBoardsAndCertificatesAcrossRepeatedConstruction() {
        for (challenge in listOf(false, true)) for (seed in 1L..15L) {
            val code = SeededPuzzle.code(seed, challenge)
            val a = SeededPuzzle.create(code)
            assertEquals(a, SeededPuzzle.create(code.lowercase()))
            assertFalse(a.board.isSolved)
            assertTrue(PuzzleRules.verifiesSolution(a.board, a.solution))
            assertEquals(3 + (seed % 3).toInt(), a.board.tubes.first().capacity)
        }
    }
    @Test fun malformedVersionsRecipesAndSeedBoundsAreRejected() {
        for (code in listOf("E2-B-1", "E1-X-1", "E1-B--1", "E1-B-ZZZZZZZZZZZZ")) {
            assertThrows(IllegalArgumentException::class.java) { SeededPuzzle.create(code) }
        }
    }
    @Test fun cancellationDoesNotReturnAnUnverifiedPuzzle() {
        assertThrows(IllegalArgumentException::class.java) { SeededPuzzle.create("E1-B-1") { true } }
    }
}
