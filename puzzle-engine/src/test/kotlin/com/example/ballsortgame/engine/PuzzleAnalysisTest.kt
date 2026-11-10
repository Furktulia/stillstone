package com.example.ballsortgame.engine

import org.junit.Assert.*
import org.junit.Test

class PuzzleAnalysisTest {
    private val pack = BundledLevelPacks.playtest()
    private fun analysis(id: String, trials: Int): PuzzleAnalysis {
        val level = pack.levels.first { it.id.startsWith(id) }
        val outcome = PuzzleAnalyzer.analyze(level.board, botTrials = trials)
        return (outcome as AnalysisOutcome.Analyzed).analysis.also {
            assertTrue(PuzzleRules.verifiesSolution(level.board, it.solution))
            if (level.shortestProven) assertEquals(level.solution.size, it.solution.size)
        }
    }

    /** The creator's own ratings of shipped puzzles are the only human calibration available. */
    @Test fun tiersAgreeWithTheHumanRatedReferencePuzzles() {
        for (trials in listOf(PuzzleAnalyzer.DEFAULT_BOT_TRIALS, 100)) {
            assertEquals("Dusk 9, easy", PuzzleTier.RELAXED, analysis("three-regular-21261", trials).tier)
            assertEquals("Moonlight 10, straightforward", PuzzleTier.RELAXED, analysis("four-challenge-26260", trials).tier)
            assertEquals("Dusk 10, enjoyable and hard", PuzzleTier.CHALLENGE, analysis("three-challenge-22261", trials).tier)
            assertEquals("Moonlight 11, harder finale", PuzzleTier.CHALLENGE, analysis("four-challenge-40015", trials).tier)
            assertEquals("Midnight 11, very hard", PuzzleTier.EXPERT, analysis("five-challenge-50028", trials).tier)
            // Midnight 10 was rated above Moonlight 10 and below Dusk 10. It measures close to the
            // BALANCED/CHALLENGE line, so only require that it is not treated as relaxed or expert.
            assertTrue(analysis("five-challenge-30261", trials).tier in setOf(PuzzleTier.BALANCED, PuzzleTier.CHALLENGE))
        }
    }

    @Test fun measurementsMatchTheDocumentedReferenceValues() {
        val dusk10 = analysis("three-challenge-22261", 100)
        assertEquals(46, dusk10.states)
        assertEquals(16, dusk10.losingStates)
        assertEquals(12, dusk10.solution.size)
        assertEquals(6, dusk10.minDecisions)
        assertEquals(4, dusk10.minTemptations)
        assertTrue("simple strategies rarely solve Dusk 10: ${dusk10.bestBotSuccesses}", dusk10.bestBotSuccesses in 1..15)
        assertEquals(5, dusk10.openings)
        assertEquals(1, dusk10.losingOpenings)
        val dusk9 = analysis("three-regular-21261", 100)
        assertEquals(0, dusk9.losingStates)
        assertEquals(100, dusk9.bestBotSuccesses)
    }

    @Test fun unsolvableOversizedAndCancelledBoardsAreNeverReportedAsAnalyzed() {
        // Two colors locked under each other with no spare tube.
        val stuck = Board(listOf(Tube(2, listOf(0, 1)), Tube(2, listOf(1, 0))))
        assertEquals(AnalysisOutcome.Unsolvable, PuzzleAnalyzer.analyze(stuck))
        val level = pack.levels.first { it.id.startsWith("five-regular-29263") }
        assertEquals(AnalysisOutcome.TooLarge, PuzzleAnalyzer.analyze(level.board, maxStates = 50))
        assertEquals(AnalysisOutcome.Cancelled, PuzzleAnalyzer.analyze(level.board) { true })
    }
}
