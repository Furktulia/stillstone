package com.example.ballsortgame.engine
import org.junit.Assert.*
import org.junit.Test
private const val E2_REFERENCE_BOARD = "5:0,0,2,2,4 | 5:3,1,0,0,4 | 5:4,4,3,3,2 | 5:1,1,3,4,3 | 5:2,2,0,1,1 | 5:"
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
        for (code in listOf("E3-B-1", "E1-X-1", "E1-R-1", "E2-X-1", "E1-B--1", "E1-B-ZZZZZZZZZZZZ", "E2-B-ZZZZZZZZZZZZ", "E2-B-!")) {
            assertThrows(IllegalArgumentException::class.java) { SeededPuzzle.create(code) }
        }
    }
    @Test fun e2PuzzlesAreReproducibleShortestSolvedAndMatchTheRequestedTier() {
        val wanted = mapOf('R' to setOf(PuzzleTier.RELAXED), 'B' to setOf(PuzzleTier.BALANCED),
            'C' to setOf(PuzzleTier.CHALLENGE, PuzzleTier.EXPERT))
        for ((recipe, tiers) in wanted) {
            var made = 0
            for (seed in 1L..12L) {
                val code = SeededPuzzle.code(seed, recipe)
                val level = try { SeededPuzzle.create(code) } catch (_: IllegalArgumentException) { continue }
                made++
                assertEquals(level, SeededPuzzle.create(code.lowercase()))
                assertTrue(level.shortestProven)
                val analysis = (PuzzleAnalyzer.analyze(level.board) as AnalysisOutcome.Analyzed).analysis
                assertTrue("$code is ${analysis.tier}", analysis.tier in tiers)
                assertEquals(analysis.solution.size, level.solution.size)
                val exact = PuzzleSolver().solve(level.board) as SolveResult.Solved
                assertTrue(exact.optimal)
                assertEquals(exact.moves.size, level.solution.size)
                assertTrue(level.board.tubes.flatMap { it.balls }.distinct().size <= 5)
            }
            // A seed may legitimately have no construction, but most must.
            assertTrue("recipe $recipe produced only $made of 12", made >= 9)
        }
    }
    /** Recipe or analyzer changes must introduce E3 rather than silently change E2. */
    @Test fun publishedE2CodeKeepsItsLiteralBoard() {
        assertEquals(E2_REFERENCE_BOARD, SeededPuzzle.create("E2-C-2").board.toString())
    }
    @Test fun e2CancellationDoesNotReturnAPuzzle() {
        assertThrows(IllegalArgumentException::class.java) { SeededPuzzle.create("E2-B-1") { true } }
    }
    @Test fun cancellationDoesNotReturnAnUnverifiedPuzzle() {
        assertThrows(IllegalArgumentException::class.java) { SeededPuzzle.create("E1-B-1") { true } }
    }
}
