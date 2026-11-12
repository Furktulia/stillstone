package com.example.ballsortgame.engine.tools

import com.example.ballsortgame.engine.*
import java.util.Random

/**
 * Does hiding colours make puzzles harder for simple play? Compares the success of the same
 * four strategies with and without sight of the buried stones, on the shipped levels and on
 * shuffled starts of every recipe. Bot success is not a human rating.
 */
internal fun runHiddenStudy(options: Map<String, String>) {
    val count = options.number("count", 150)
    val seed = options.longNumber("seed", 91_000)
    val trials = options.number("policy-trials", 40)
    val maxStates = options.number("analysis-states", 60_000)
    println("id,tier_open,open_percent,hidden_percent,tier_hidden,moves,losing_states,states")
    for (level in BundledLevelPacks.playtest().levels) {
        val a = (PuzzleAnalyzer.analyze(level.board, maxStates, trials) as? AnalysisOutcome.Analyzed)?.analysis
        if (a == null) { println("${level.id},TOO_LARGE"); continue }
        val blind = requireNotNull(HiddenColors.bestBlindSuccesses(level.board, trials)) * 100 / trials
        println(listOf(level.id, a.tier ?: "NONE", a.bestBotPercent, blind, a.tierFor(blind) ?: "NONE", a.solution.size, a.losingStates, a.states).joinToString(","))
    }
    println()
    println("colors,capacity,empty,open_tier,samples,mean_open_percent,mean_hidden_percent,hidden_relaxed,hidden_balanced,hidden_challenge,hidden_none")
    for (empty in 1..2) for (capacity in 3..5) for (colors in 3..5) {
        val goal = PuzzleGenerator.equalCapacityGoal(colors, capacity, empty)
        class Cell { var n = 0; var open = 0L; var hidden = 0L; val tiers = HashMap<PuzzleTier?, Int>() }
        val cells = HashMap<PuzzleTier?, Cell>()
        repeat(count) { index ->
            val board = ShuffleConstruction.candidate(goal, Random(seed + index))
            if (board.isSolved) return@repeat
            val a = (PuzzleAnalyzer.analyze(board, maxStates, trials) as? AnalysisOutcome.Analyzed)?.analysis ?: return@repeat
            val blind = requireNotNull(HiddenColors.bestBlindSuccesses(board, trials)) * 100 / trials
            val cell = cells.getOrPut(a.tier) { Cell() }
            cell.n++; cell.open += a.bestBotPercent; cell.hidden += blind
            val after = a.tierFor(blind)
            cell.tiers[after] = (cell.tiers[after] ?: 0) + 1
        }
        for ((tier, cell) in cells.entries.sortedBy { it.key?.ordinal ?: 9 }) println(listOf(colors, capacity, empty, tier ?: "NONE", cell.n,
            cell.open / cell.n, cell.hidden / cell.n, cell.tiers[PuzzleTier.RELAXED] ?: 0, cell.tiers[PuzzleTier.BALANCED] ?: 0,
            (cell.tiers[PuzzleTier.CHALLENGE] ?: 0) + (cell.tiers[PuzzleTier.EXPERT] ?: 0), cell.tiers[null] ?: 0).joinToString(","))
    }
}
