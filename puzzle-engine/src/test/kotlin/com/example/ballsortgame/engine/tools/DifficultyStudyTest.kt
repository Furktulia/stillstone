package com.example.ballsortgame.engine.tools

import com.example.ballsortgame.engine.*
import org.junit.Assert.*
import org.junit.Test

class DifficultyStudyTest {
    private fun level(prefix: String) = BundledLevelPacks.difficultyStudy().levels.first { it.id.startsWith(prefix) }
    private val exact = SearchOptions(SearchLimits(100_000, 100_000, 100_000, 5_000))

    @Test fun fullBackwardGraphAgreesWithExactSearchAtEveryReachableState() {
        for (prefix in listOf("three-regular-21261-", "three-challenge-", "four-challenge-", "five-challenge-30261-")) {
            val graph = requireNotNull(DecisionGraph.explore(level(prefix).board))
            graph.boards.forEachIndexed { index, board ->
                val solved = PuzzleSolver().solve(board, exact)
                val distance = graph.distance(board)
                if (distance == null) assertTrue(solved is SolveResult.Unsolvable) else {
                    assertTrue(solved is SolveResult.Solved)
                    assertEquals(distance, (solved as SolveResult.Solved).moves.size)
                }
                if (board.isSolved) assertTrue(graph.edges[index].isEmpty())
            }
        }
    }

    @Test fun truncatedGraphsAreUnknownRatherThanProofsOfLosingPositions() {
        val board = level("three-challenge-").board
        assertNull(DecisionGraph.explore(board, maxStates = 1))
        assertNull(DecisionGraph.explore(board, maxMillis = 0))
        assertThrows(IllegalArgumentException::class.java) { DecisionGraph.explore(board, maxStates = 0) }
    }

    @Test fun doublingPreservesEveryPhysicalMoveAndShortestLength() {
        val original = level("three-challenge-")
        val graph = requireNotNull(DecisionGraph.explore(original.board))
        for (board in graph.boards) {
            val doubled = scalePuzzle(board, 2)
            assertEquals(PuzzleRules.legalMoves(board), PuzzleRules.legalMoves(doubled))
            for (move in PuzzleRules.legalMoves(board)) {
                val before = requireNotNull(PuzzleRules.transfer(board, move))
                val after = requireNotNull(PuzzleRules.transfer(doubled, move))
                assertEquals(before.count * 2, after.count)
                assertEquals(scalePuzzle(before.board, 2), after.board)
            }
        }
        val solved = PuzzleSolver().solve(scalePuzzle(original.board, 2), exact) as SolveResult.Solved
        assertTrue(solved.optimal)
        assertEquals(original.solution.size, solved.moves.size)
        assertTrue(PuzzleRules.verifiesSolution(scalePuzzle(original.board, 2), original.solution))
    }

    @Test fun scalingAlsoPreservesMixedCapacitiesAndPartialTransfers() {
        val board = Board(listOf(Tube(2, listOf(0, 0)), Tube(3, listOf(0, 0)), Tube(1, listOf(1))))
        val next = requireNotNull(PuzzleRules.transfer(board, Move(0, 1)))
        assertEquals(1, next.count)
        val scaled = requireNotNull(PuzzleRules.transfer(scalePuzzle(board, 3), Move(0, 1)))
        assertEquals(3, scaled.count)
        assertEquals(scalePuzzle(next.board, 3), scaled.board)
        assertThrows(IllegalArgumentException::class.java) { scalePuzzle(board, 0) }
    }

    @Test fun duskNineHasNoReachableLosingStatesButDuskTenDoes() {
        val easy = requireNotNull(DecisionGraph.explore(level("three-regular-21261-").board))
        assertTrue(easy.distance.all { it >= 0 })
        val hard = requireNotNull(DecisionGraph.explore(level("three-challenge-").board))
        assertTrue(hard.distance.any { it < 0 })
    }

    @Test fun purpleOpeningDependsOnWhichSourceTubeWasChosen() {
        val board = level("three-challenge-").board
        val graph = requireNotNull(DecisionGraph.explore(board))
        val wrong = requireNotNull(PuzzleRules.transfer(board, Move(4, 5))).board
        val recoverable = requireNotNull(PuzzleRules.transfer(board, Move(1, 5))).board
        assertTrue(graph.contains(wrong))
        assertNull(graph.distance(wrong))
        assertNotNull(graph.distance(recoverable))
        assertTrue(PuzzleRules.legalMoves(wrong).isNotEmpty())
        val bothPurples = requireNotNull(PuzzleRules.transfer(recoverable, Move(4, 5))).board
        assertTrue(graph.contains(bothPurples))
        assertNull(graph.distance(bothPurples))
        assertTrue(PuzzleSolver().solve(wrong, exact) is SolveResult.Unsolvable)
    }

    @Test fun moonlightTenLargestOpeningIsUniqueAndSafeDespiteOtherTraps() {
        val board = level("four-challenge-").board
        val graph = requireNotNull(DecisionGraph.explore(board))
        val best = bestSurfaceMoves(board, SurfacePolicy.LARGEST_GROUP)
        assertEquals(listOf(Move(2, 5)), best)
        val choices = graph.choices(board)
        assertEquals(4, choices.count { it.remaining == null })
        assertNotNull(graph.distance(requireNotNull(PuzzleRules.transfer(board, best.single())).board))
        assertEquals("SOLVED", followSurfacePolicy(board, SurfacePolicy.LARGEST_GROUP).status)
    }
}
