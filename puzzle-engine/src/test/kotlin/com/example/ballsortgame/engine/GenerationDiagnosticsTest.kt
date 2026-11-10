package com.example.ballsortgame.engine

import org.junit.Assert.*
import org.junit.Test

class GenerationDiagnosticsTest {
    private val generator = PuzzleGenerator()
    private fun request() = GenerationRequest(
        PuzzleGenerator.equalCapacityGoal(3, 3, 2), 7,
        maxAttempts = 1, reverseSteps = 20,
        search = SearchOptions(SearchLimits(50_000, 80_000, 100_000, 5_000)),
    )
    private fun assertAccounting(result: GenerationResult) {
        val d = result.diagnostics
        assertEquals(d.attempts, d.acceptedCandidates + d.rejections.values.sum())
        assertTrue(d.evaluatedCandidates <= d.attempts)
    }

    @Test fun acceptedAndRejectedAttemptsHaveExactAccounting() {
        val success = generator.generate(request())
        assertTrue(success is GenerationResult.Generated)
        assertEquals(1, success.diagnostics.acceptedCandidates)
        assertAccounting(success)
        val rejected = generator.generate(request().copy(minBoundaries = 1000, maxAttempts = 4))
        assertTrue(rejected is GenerationResult.Exhausted)
        assertEquals(4, rejected.diagnostics.rejections[RejectionReason.TOO_FEW_BOUNDARIES])
        assertEquals(0, rejected.diagnostics.evaluatedCandidates)
        assertAccounting(rejected)
    }

    @Test fun separatesSearchLimitsFromUnsolvability() {
        val result = generator.generate(request().copy(
            strategy = GenerationStrategy.SHUFFLE_AND_SOLVE,
            search = SearchOptions(SearchLimits(maxExpandedStates = 0)),
        ))
        assertTrue(result is GenerationResult.Exhausted)
        assertEquals(1, result.diagnostics.rejections[RejectionReason.SEARCH_EXPANSIONS])
        assertFalse(result.diagnostics.rejections.containsKey(RejectionReason.PROVEN_UNSOLVABLE))
        assertAccounting(result)
    }

    @Test fun boundFailuresDistinguishProofFromInsufficientEvidence() {
        fun reason(input: GenerationRequest): RejectionReason {
            val result = generator.generate(input)
            assertAccounting(result)
            return result.diagnostics.rejections.keys.single()
        }
        val zeroBudget = request().copy(search = SearchOptions(SearchLimits(maxExpandedStates = 0)))
        assertEquals(RejectionReason.OPTIMALITY_UNPROVEN, reason(zeroBudget.copy(requireOptimalSolution = true)))
        assertEquals(RejectionReason.MINIMUM_UNPROVEN, reason(zeroBudget.copy(minSolutionMoves = 1000)))
        assertEquals(RejectionReason.MAXIMUM_UNPROVEN, reason(zeroBudget.copy(maxSolutionMoves = 1)))
        assertEquals(RejectionReason.BELOW_MINIMUM_MOVES, reason(request().copy(minSolutionMoves = 1000)))
        assertEquals(RejectionReason.ABOVE_MAXIMUM_MOVES, reason(request().copy(maxSolutionMoves = 1)))
    }

    @Test fun batchDuplicatesAreRejectedAndDoNotGrowIndex() {
        val diversity = PuzzleDiversityIndex()
        val first = generator.generate(request(), diversity)
        assertTrue(first is GenerationResult.Generated)
        val repeated = generator.generate(request(), diversity)
        assertTrue(repeated is GenerationResult.Exhausted)
        assertEquals(1, repeated.diagnostics.rejections[RejectionReason.STRUCTURAL_DUPLICATE])
        assertEquals(1, diversity.size)
        assertAccounting(repeated)
    }

    @Test fun tinyBatchExhaustsDistinctStructuresInsteadOfRecoloringDuplicates() {
        val diversity = PuzzleDiversityIndex()
        val outcomes = (0L..5L).map { seed ->
            generator.generate(request().copy(
                solvedBoard = PuzzleGenerator.equalCapacityGoal(2, 2, 2),
                seed = seed, strategy = GenerationStrategy.SHUFFLE_AND_SOLVE,
                maxAttempts = 30, requireOptimalSolution = true,
            ), diversity).also(::assertAccounting)
        }
        // With two full two-slot tubes, there are only two unsolved structures:
        // both tubes have the same ordering, or opposite ordering.
        assertEquals(2, outcomes.count { it is GenerationResult.Generated })
        assertEquals(2, diversity.size)
        assertTrue(outcomes.sumOf { it.diagnostics.rejections[RejectionReason.STRUCTURAL_DUPLICATE] ?: 0 } > 0)
    }

    @Test fun boundedDiversityFailureIsReportedWithoutAdmittingCandidate() {
        val diversity = PuzzleDiversityIndex(0)
        assertTrue(generator.generate(request(), diversity) is GenerationResult.Generated)
        val result = generator.generate(request(), diversity)
        assertEquals(1, result.diagnostics.rejections[RejectionReason.DIVERSITY_COMPARISON_LIMIT])
        assertEquals(1, diversity.size)
        assertAccounting(result)
    }

    @Test fun cancellationBeforeAndDuringAttemptIsAccountedFor() {
        val before = generator.generate(request()) { true }
        assertEquals(0, before.diagnostics.attempts)
        assertAccounting(before)
        var calls = 0
        val during = generator.generate(request()) { ++calls >= 3 }
        assertTrue(during is GenerationResult.Cancelled)
        assertEquals(1, during.diagnostics.rejections[RejectionReason.CANCELLED])
        assertAccounting(during)
    }

    @Test fun rejectionMapIsAnImmutableSnapshot() {
        val result = generator.generate(request().copy(minBoundaries = 1000))
        assertThrows(UnsupportedOperationException::class.java) {
            (result.diagnostics.rejections as MutableMap<RejectionReason, Int>).clear()
        }
    }
}
