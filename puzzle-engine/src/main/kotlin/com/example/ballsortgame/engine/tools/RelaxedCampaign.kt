package com.example.ballsortgame.engine.tools

import com.example.ballsortgame.engine.*
import java.io.File
import java.util.Random

/**
 * Candidates for the relaxed bulk of a campaign world: longer than the first hand-picked
 * levels, but with no way to get stuck. Every candidate
 * - is RELAXED for the simple strategies, with and without sight of buried stones,
 * - has NO losing position anywhere in its complete state graph (no soft lock is possible),
 * - is structurally different from the others and from the shipped pack.
 * Nothing is imported into the app automatically; levels are chosen from the output.
 */
internal fun runRelaxedCampaign(options: Map<String, String>) {
    val output = File(options["output"] ?: "build/puzzle-lab/relaxed-campaign")
    require(!output.exists()) { "Refusing to overwrite ${output.absolutePath}; choose a fresh output directory" }
    val attempts = options.number("attempts", 600)
    val seed = options.longNumber("seed", 120_000)
    val maxStates = options.number("analysis-states", 150_000)
    val trials = options.number("policy-trials", 100)
    val shipped = BundledLevelPacks.playtest()
    val diversity = PuzzleDiversityIndex()
    shipped.levels.forEach { diversity.admit(it.board) }
    val worldOf = mapOf(3 to "three", 4 to "four", 5 to "five")
    val levels = ArrayList<PackLevel>()
    val rows = mutableListOf("id,world,colors,capacity,spare,moves,states,min_decisions,open_percent,hidden_percent")
    for (capacity in 3..5) {
        val random = Random(seed + capacity)
        var kept = 0; var tooLarge = 0; var losing = 0; var notRelaxed = 0
        repeat(attempts) {
            val colors = 3 + random.nextInt(3)
            val spare = if (colors >= 4 && random.nextInt(4) == 0) 1 else 2
            val board = ShuffleConstruction.candidate(PuzzleGenerator.equalCapacityGoal(colors, capacity, spare), random)
            if (board.isSolved) return@repeat
            val analysis = (PuzzleAnalyzer.analyze(board, maxStates, trials) as? AnalysisOutcome.Analyzed)?.analysis
            if (analysis == null) { tooLarge++; return@repeat }
            if (analysis.losingStates > 0) { losing++; return@repeat }
            val hidden = requireNotNull(HiddenColors.bestBlindSuccesses(board, trials)) * 100 / trials
            if (analysis.bestBotPercent < 95 || hidden < 90) { notRelaxed++; return@repeat }
            if (diversity.admit(board) != DiversityAdmission.ADDED) return@repeat
            val digest = java.security.MessageDigest.getInstance("SHA-256").digest(board.toString().toByteArray(Charsets.UTF_8))
                .take(6).joinToString("") { "%02x".format(it.toInt() and 255) }
            val id = "${worldOf.getValue(capacity)}-calm-$digest"
            levels += PackLevel(id, worldOf.getValue(capacity), LevelRole.RELAXED, null, true, board, analysis.solution)
            rows += listOf(id, worldOf.getValue(capacity), colors, capacity, spare, analysis.solution.size, analysis.states,
                analysis.minDecisions, analysis.bestBotPercent, hidden).joinToString(",")
            kept++
        }
        println("capacity $capacity: kept $kept of $attempts (graph too large $tooLarge, has a losing position $losing, not relaxed enough $notRelaxed)")
    }
    require(levels.isNotEmpty()) { "No candidates found" }
    require(output.mkdirs())
    File(output, "pool.txt").writeText(LevelPackCodec.encode(LevelPack(shipped.worlds, levels.sortedBy { it.solution.size })))
    File(output, "candidates.csv").writeText(rows.joinToString("\n", postfix = "\n"))
    File(output, "parameters.txt").writeText("seed=$seed\nattempts_per_capacity=$attempts\nanalysis_states=$maxStates\nbot_trials=$trials\n" +
        "kept=RELAXED open>=95 and hidden>=90, zero losing states, not a structural duplicate\n")
    println("${levels.size} candidates in ${output.absolutePath}")
}
