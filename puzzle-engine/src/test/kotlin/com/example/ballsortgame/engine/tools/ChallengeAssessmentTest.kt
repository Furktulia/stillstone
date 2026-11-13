package com.example.ballsortgame.engine.tools

import com.example.ballsortgame.engine.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ChallengeAssessmentTest {
    @get:Rule val temporary = TemporaryFolder()
    private val fixtures = BundledLevelPacks.difficultyStudy().levels
    private val reference by lazy { assessChallenge(referenceChallenge()) }
    private val requirements by lazy { referenceChallengeRequirements(reference) }

    @Test fun humanReferencePassesAndTheStraightforwardFinaleFails() {
        assertTrue(requirements.rejectionReasons(reference).isEmpty())
        val easy = assessChallenge(fixtures[0])
        val straightforward = assessChallenge(fixtures[2])
        assertTrue("SIMPLE_STRATEGY_TOO_SUCCESSFUL" in requirements.rejectionReasons(easy))
        assertTrue("SIMPLE_STRATEGY_TOO_SUCCESSFUL" in requirements.rejectionReasons(straightforward))
        assertEquals(0, straightforward.openingTemptingLosses)
        assertEquals(100, straightforward.easiestBotSuccesses)
    }

    @Test fun graphLimitsRemainUnknownAndCannotPassScreening() {
        val limited = assessChallenge(referenceChallenge(), ChallengeBudget(graphStates = 1, trials = 8))
        assertFalse(limited.complete)
        assertNull(limited.minTemptations)
        assertNull(limited.shortest)
        assertTrue("GRAPH_UNRESOLVED" in requirements.rejectionReasons(limited))
    }

    @Test fun oneSuccessfulSimpleStrategyIsEnoughToRejectRatherThanAverageItAway() {
        val otherBotsFail = ChallengeStrategy.entries.associateWith { 0 }
        val a = reference.copy(successes = otherBotsFail + (ChallengeStrategy.FREE_TUBE to reference.trials))
        assertTrue("SIMPLE_STRATEGY_TOO_SUCCESSFUL" in requirements.rejectionReasons(a))
    }

    @Test fun anEasyAlternateShortestRouteCannotBeHiddenByAHardCertificate() {
        val a = reference.copy(minTemptations = 0, maxTemptations = 99, minCounterGreedy = 0)
        assertTrue("NO_REQUIRED_MISLEADING_CHECKPOINT" in requirements.rejectionReasons(a))
    }

    @Test fun shortestRouteBoundsAgreeWithIndependentPathEnumeration() {
        val level = referenceChallenge()
        val graph = requireNotNull(DecisionGraph.explore(level.board))
        fun traces(board: Board): List<List<Int>> {
            val remaining = requireNotNull(graph.distance(board))
            if (remaining == 0) return listOf(listOf(0, 0, 0))
            val choices = graph.choices(board)
            val optimal = choices.filter { it.remaining == remaining - 1 }
            val preferred = ChallengeStrategy.entries.flatMap { strategy ->
                val best = choices.maxWith { a, b ->
                    compareRanks(challengeRank(board, a.move, strategy), challengeRank(board, b.move, strategy))
                }
                choices.filter { challengeRank(board, it.move, strategy) == challengeRank(board, best.move, strategy) }.map { it.move }
            }.toSet()
            val local = listOf(
                if (choices.size > 1 && choices.any { it.remaining != remaining - 1 }) 1 else 0,
                if (choices.any { it.move in preferred && it.remaining == null }) 1 else 0,
                if (optimal.none { it.move in preferred }) 1 else 0,
            )
            return optimal.flatMap { choice -> traces(choice.next).map { tail -> local.indices.map { local[it] + tail[it] } } }
        }
        val paths = traces(level.board)
        assertTrue(paths.size > 1)
        assertEquals(paths.minOf { it[0] }, reference.minDecisions)
        assertEquals(paths.minOf { it[1] }, reference.minTemptations)
        assertEquals(paths.maxOf { it[1] }, reference.maxTemptations)
        assertEquals(paths.minOf { it[2] }, reference.minCounterGreedy)
        assertTrue(requireNotNull(reference.minTemptations) < requireNotNull(reference.maxTemptations))
    }

    @Test fun doublingCapacityAndBallsDoesNotManufactureMoreDecisionDifficulty() {
        val original = referenceChallenge()
        val doubled = original.copy(id = "scaled-reference", board = scalePuzzle(original.board, 2))
        assertEquals(reference, assessChallenge(doubled))
    }

    @Test fun selectedNewFinalesMeetTheReferenceScreenAndHaveEarlyTemptingChoices() {
        val pack = BundledLevelPacks.playtestV3()
        for (world in listOf("four", "five")) {
            val last = pack.levels.last { it.worldId == world }
            val result = assessChallenge(last)
            assertTrue(last.id, requirements.rejectionReasons(result).isEmpty())
            assertTrue(requireNotNull(result.openingTemptingLosses) >= 1)
        }
    }

    @Test fun refinementFailureKeepsDiagnosticsAndNeverExportsAPartialCampaign() {
        val output = java.io.File(temporary.root, "failed-refinement")
        assertThrows(IllegalArgumentException::class.java) {
            runFinaleRefinement(mapOf(
                "output" to output.path, "count" to "1", "attempts" to "1", "max-ms" to "0",
                "policy-trials" to "8",
            ))
        }
        assertTrue(java.io.File(output, "generation.csv").isFile)
        assertTrue(java.io.File(output, "candidates.csv").isFile)
        assertTrue(java.io.File(output, "screening.csv").isFile)
        val pool = LevelPackCodec.decode(java.io.File(output, "pool.txt").readText())
        assertEquals(BundledLevelPacks.playtestV3(), pool)
        assertFalse(java.io.File(output, "playtest.txt").exists())
    }
}
