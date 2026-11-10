package com.example.ballsortgame.engine

import com.example.ballsortgame.engine.tools.PuzzleCorpus
import com.example.ballsortgame.engine.tools.percentile
import org.junit.Assert.*
import org.junit.Test

class PuzzleCorpusTest {
    @Test fun fixedCorpusMatchesDeclaredSolvabilityAndShortestLengths() {
        val options = SearchOptions(SearchLimits(150_000, 200_000, 250_000, 10_000))
        assertEquals(PuzzleCorpus.cases.size, PuzzleCorpus.cases.map { it.id }.toSet().size)
        for (fixture in PuzzleCorpus.cases) {
            val result = PuzzleSolver().solve(fixture.board, options)
            if (fixture.solvable) {
                assertTrue("${fixture.id}: $result", result is SolveResult.Solved)
                val solution = result as SolveResult.Solved
                assertEquals(fixture.id, fixture.shortestMoves, solution.moves.size)
                assertTrue(PuzzleRules.verifiesSolution(fixture.board, solution.moves))
            } else {
                assertTrue("${fixture.id}: $result", result is SolveResult.Unsolvable)
            }
        }
        println("Fixed corpus: ${PuzzleCorpus.cases.size} root, dead-end and intermediate-position cases")
    }

    @Test fun legalMoveDeadEndHasFeasibleQuantitiesButEveryChoiceImmediatelyBlocks() {
        val board = PuzzleCorpus.trappedPartials
        assertEquals(mapOf(0 to 3, 1 to 3, 2 to 3), board.tubes.flatMap { it.balls }.groupingBy { it }.eachCount())
        val moves = PuzzleRules.legalMoves(board)
        assertEquals(setOf(Move(0, 2), Move(1, 2)), moves.toSet())
        for (move in moves) {
            val next = requireNotNull(PuzzleRules.transfer(board, move)).board
            assertFalse(next.isSolved)
            assertTrue(PuzzleRules.legalMoves(next).isEmpty())
        }
    }

    @Test fun percentilesUseNearestRankAndLeaveMissingSamplesEmpty() {
        assertNull(percentile(emptyList(), 95))
        assertEquals(2L, percentile(listOf(4L, 1L, 3L, 2L), 50))
        assertEquals(4L, percentile(listOf(4L, 1L, 3L, 2L), 95))
        assertEquals(99L, percentile(listOf(99L), 95))
        assertThrows(IllegalArgumentException::class.java) { percentile(listOf(1L), 0) }
    }
}
