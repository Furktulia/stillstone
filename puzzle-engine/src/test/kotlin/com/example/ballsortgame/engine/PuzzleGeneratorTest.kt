package com.example.ballsortgame.engine

import org.junit.Assert.*
import org.junit.Test

class PuzzleGeneratorTest {
    private val generator = PuzzleGenerator()
    private val search = SearchOptions(SearchLimits(50_000, 80_000, 100_000, 5_000))

    private fun request(seed: Long = 7, capacity: Int = 3) = GenerationRequest(
        PuzzleGenerator.equalCapacityGoal(3, capacity, 2), seed,
        reverseSteps = 24, maxAttempts = 12, search = search,
    )

    @Test fun reversePredecessorsAlwaysReplayExactMaximalTransfers() {
        val states = listOf(
            PuzzleGenerator.equalCapacityGoal(3, 3, 2),
            Board(listOf(Tube(3, listOf(0)), Tube(3, listOf(0, 0)))),
            Board(listOf(Tube(2, listOf(0, 1)), Tube(3, listOf(1, 1)), Tube(1))),
        )
        for (state in states) for (step in ReverseConstruction.predecessors(state)) {
            assertEquals(state, requireNotNull(PuzzleRules.transfer(step.predecessor, step.forwardMove)).board)
            assertEquals(
                state.tubes.flatMap { it.balls }.sorted(),
                step.predecessor.tubes.flatMap { it.balls }.sorted(),
            )
        }
        val state = states[1]
        val invalid = Board(listOf(Tube(3, listOf(0, 0)), Tube(3, listOf(0))))
        assertFalse(ReverseConstruction.predecessors(state).any {
            it.predecessor == invalid && it.forwardMove == Move(0, 1)
        })
    }

    @Test fun generatedCertificatesWorkAcrossSeedsAndCapacities() {
        var generated = 0
        for (capacity in 3..6) for (seed in 0L..3L) {
            val puzzle = (generator.generate(request(seed, capacity)) as GenerationResult.Generated).puzzle
            assertFalse(puzzle.board.isSolved)
            assertTrue(puzzle.metrics.colorBoundaries >= 2)
            assertTrue(puzzle.metrics.emptyTubes >= 1)
            assertTrue(PuzzleRules.verifiesSolution(puzzle.board, puzzle.solution))
            assertEquals(capacity * 3, puzzle.board.tubes.sumOf { it.balls.size })
            assertTrue(puzzle.board.tubes.all { it.capacity == capacity })
            assertTrue(puzzle.metrics.shortestSolutionLowerBound <= puzzle.solution.size)
            generated++
        }
        println("Reverse generation: $generated replay-verified puzzles, capacities 3 through 6")
    }

    @Test fun supportsMixedCapacityGoalsWithoutSpecialMovePruning() {
        val goal = Board(listOf(
            Tube(2, listOf(0, 0)), Tube(3, listOf(1, 1, 1)), Tube(5, List(5) { 2 }),
            Tube(2), Tube(3),
        ))
        for (seed in 0L..3L) {
            val result = generator.generate(request(seed).copy(solvedBoard = goal))
            val puzzle = (result as GenerationResult.Generated).puzzle
            assertEquals(goal.tubes.map { it.capacity }, puzzle.board.tubes.map { it.capacity })
            assertTrue(PuzzleRules.verifiesSolution(puzzle.board, puzzle.solution))
        }
    }

    @Test fun seedReproducesBoardAndSolutionWhenSearchCompletes() {
        val first = (generator.generate(request()) as GenerationResult.Generated).puzzle
        val second = (generator.generate(request()) as GenerationResult.Generated).puzzle
        assertEquals(first.board, second.board)
        assertEquals(first.solution, second.solution)
        assertEquals(first.metrics, second.metrics)
    }

    @Test fun solverTimeoutKeepsCertifiedPuzzleWithoutClaimingShortestSolution() {
        val result = generator.generate(request().copy(
            search = SearchOptions(SearchLimits(maxExpandedStates = 0)),
        ))
        val puzzle = (result as GenerationResult.Generated).puzzle
        assertTrue(puzzle.evaluation is SolveResult.LimitReached)
        assertFalse(puzzle.metrics.shortestSolutionProven)
        assertTrue(PuzzleRules.verifiesSolution(puzzle.board, puzzle.solution))
        val strict = generator.generate(request().copy(
            maxAttempts = 2, requireOptimalSolution = true,
            search = SearchOptions(SearchLimits(maxExpandedStates = 0)),
        ))
        assertTrue(strict is GenerationResult.Exhausted)
    }

    @Test fun constructionDepthIsNotUsedAsMinimumSolutionLength() {
        val result = generator.generate(request().copy(
            maxAttempts = 3, minSolutionMoves = 1000, reverseSteps = 12,
        ))
        assertTrue(result is GenerationResult.Exhausted)
    }

    @Test fun canEvaluateHintsFromIntermediatePositions() {
        val puzzle = (generator.generate(request()) as GenerationResult.Generated).puzzle
        var board = puzzle.board
        for (move in puzzle.solution.take(4)) {
            board = requireNotNull(PuzzleRules.transfer(board, move)).board
            val hint = PuzzleSolver().solve(board, search) as SolveResult.Solved
            assertTrue(PuzzleRules.verifiesSolution(board, hint.moves))
        }
    }

    @Test fun shuffledCandidatesRequireSolverProofAndPreserveOccupancy() {
        for (seed in 0L..7L) {
            val input = request(seed).copy(
                strategy = GenerationStrategy.SHUFFLE_AND_SOLVE,
                requireOptimalSolution = true,
            )
            val puzzle = (generator.generate(input) as GenerationResult.Generated).puzzle
            assertEquals(input.solvedBoard.tubes.map { it.balls.size }, puzzle.board.tubes.map { it.balls.size })
            assertTrue(PuzzleRules.verifiesSolution(puzzle.board, puzzle.solution))
            assertTrue(puzzle.metrics.shortestSolutionProven)
            assertEquals(0, puzzle.constructionMoves)
        }
        val noProof = generator.generate(request().copy(
            strategy = GenerationStrategy.SHUFFLE_AND_SOLVE, maxAttempts = 3,
            search = SearchOptions(SearchLimits(maxExpandedStates = 0)),
        ))
        assertTrue(noProof is GenerationResult.Exhausted)
    }

    @Test fun exactSolutionBandsAreEnforcedOnAcceptedPuzzles() {
        val input = request().copy(
            strategy = GenerationStrategy.SHUFFLE_AND_SOLVE,
            requireOptimalSolution = true, minSolutionMoves = 5, maxSolutionMoves = 15,
            maxAttempts = 30,
        )
        val puzzle = (generator.generate(input) as GenerationResult.Generated).puzzle
        assertTrue(puzzle.solution.size in 5..15)
        assertEquals(puzzle.solution.size, puzzle.metrics.shortestSolutionLowerBound)
    }

    @Test fun shuffledMixedCapacityCertificatesAreVerified() {
        val goal = Board(listOf(
            Tube(2, listOf(0, 0)), Tube(3, listOf(1, 1, 1)), Tube(2), Tube(3),
        ))
        val puzzle = (generator.generate(request().copy(
            solvedBoard = goal, strategy = GenerationStrategy.SHUFFLE_AND_SOLVE,
        )) as GenerationResult.Generated).puzzle
        assertTrue(PuzzleRules.verifiesSolution(puzzle.board, puzzle.solution))
        assertEquals(goal.tubes.map { it.balls.size }, puzzle.board.tubes.map { it.balls.size })
    }

    @Test fun generationCanBeCancelled() {
        assertTrue(generator.generate(request()) { true } is GenerationResult.Cancelled)
    }

    @Test fun validatesGenerationRequests() {
        assertThrows(IllegalArgumentException::class.java) {
            request().copy(solvedBoard = Board(listOf(Tube(2, listOf(0)))))
        }
        assertThrows(IllegalArgumentException::class.java) { request().copy(reverseSteps = 0) }
        assertThrows(IllegalArgumentException::class.java) { request().copy(minSolutionMoves = 9, maxSolutionMoves = 2) }
    }
}
