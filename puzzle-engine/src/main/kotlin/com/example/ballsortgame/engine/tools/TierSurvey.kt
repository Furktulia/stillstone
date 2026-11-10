package com.example.ballsortgame.engine.tools

import com.example.ballsortgame.engine.*
import java.io.File
import java.util.Random

/**
 * What does each shuffled recipe actually produce? Samples uniformly shuffled starts and
 * classifies each with the shared analyzer. PC timings are not phone timings.
 */
internal fun runTierSurvey(options: Map<String, String>) {
    val count = options.number("count", 200)
    require(count in 1..5000)
    val seed = options.longNumber("seed", 70_000)
    val maxStates = options.number("analysis-states", PuzzleAnalyzer.DEFAULT_MAX_STATES)
    val trials = options.number("policy-trials", PuzzleAnalyzer.DEFAULT_BOT_TRIALS)
    val output = options["output"]?.let(::File)
    require(output == null || !output.exists()) { "Refusing to overwrite ${output?.absolutePath}" }
    val rows = mutableListOf(
        "colors,capacity,empty,samples,unsolvable,too_large,relaxed,balanced,challenge,expert,unclassified," +
            "p50_states,p90_states,p50_moves_balanced,p50_moves_challenge,total_ms",
    )
    val detail = mutableListOf("colors,capacity,empty,seed,outcome,tier,moves,states,losing_states,openings,losing_openings,min_decisions,min_temptations,min_countergreedy,best_bot_percent")
    for (empty in 1..2) for (capacity in 3..5) for (colors in 3..5) {
        val goal = PuzzleGenerator.equalCapacityGoal(colors, capacity, empty)
        val started = System.nanoTime()
        var unsolvable = 0
        var tooLarge = 0
        val tiers = HashMap<PuzzleTier?, Int>()
        val states = ArrayList<Long>()
        val moves = HashMap<PuzzleTier?, ArrayList<Long>>()
        repeat(count) { index ->
            val currentSeed = seed + index
            val board = ShuffleConstruction.candidate(goal, Random(currentSeed))
            if (board.isSolved) return@repeat
            when (val outcome = PuzzleAnalyzer.analyze(board, maxStates, trials)) {
                is AnalysisOutcome.Analyzed -> {
                    val a = outcome.analysis
                    tiers[a.tier] = (tiers[a.tier] ?: 0) + 1
                    states.add(a.states.toLong())
                    moves.getOrPut(a.tier) { ArrayList() }.add(a.solution.size.toLong())
                    detail += listOf(colors, capacity, empty, currentSeed, "ANALYZED", a.tier ?: "NONE", a.solution.size, a.states,
                        a.losingStates, a.openings, a.losingOpenings, a.minDecisions, a.minTemptations, a.minCounterGreedy, a.bestBotPercent).joinToString(",")
                }
                AnalysisOutcome.Unsolvable -> { unsolvable++; detail += "$colors,$capacity,$empty,$currentSeed,UNSOLVABLE,,,,,,,,,," }
                AnalysisOutcome.TooLarge -> { tooLarge++; detail += "$colors,$capacity,$empty,$currentSeed,TOO_LARGE,,,,,,,,,," }
                AnalysisOutcome.Cancelled -> error("Unexpected cancellation")
            }
        }
        rows += listOf(
            colors, capacity, empty, count, unsolvable, tooLarge,
            tiers[PuzzleTier.RELAXED] ?: 0, tiers[PuzzleTier.BALANCED] ?: 0, tiers[PuzzleTier.CHALLENGE] ?: 0,
            tiers[PuzzleTier.EXPERT] ?: 0, tiers[null] ?: 0,
            percentile(states, 50) ?: "", percentile(states, 90) ?: "",
            percentile(moves[PuzzleTier.BALANCED] ?: emptyList(), 50) ?: "",
            percentile(moves[PuzzleTier.CHALLENGE] ?: emptyList(), 50) ?: "",
            (System.nanoTime() - started) / 1_000_000,
        ).joinToString(",")
        println(rows.last())
    }
    if (output != null) {
        require(output.mkdirs())
        File(output, "summary.csv").writeText(rows.joinToString("\n", postfix = "\n"))
        File(output, "detail.csv").writeText(detail.joinToString("\n", postfix = "\n"))
        File(output, "parameters.txt").writeText("seed=$seed\ncount=$count\nanalysis_states=$maxStates\nbot_trials=$trials\nconstruction=uniform shuffle of a full goal\n")
    }
    println(rows.first())
}

/**
 * Authoring pool: structurally distinct, exactly analyzed candidates for every tier and
 * capacity, with their measurements, for a person to play and choose from.
 * Nothing is imported into the app automatically.
 */
internal fun runTierPool(options: Map<String, String>) {
    val output = File(options["output"] ?: "build/puzzle-lab/tier-pool")
    require(!output.exists()) { "Refusing to overwrite ${output.absolutePath}; choose a fresh output directory" }
    val perCell = options.number("count", 6)
    require(perCell in 1..100)
    val seed = options.longNumber("seed", 80_000)
    val maxStates = options.number("analysis-states", PuzzleAnalyzer.DEFAULT_MAX_STATES)
    val attempts = options.number("attempts", 4000)
    val shipped = BundledLevelPacks.playtest()
    val diversity = PuzzleDiversityIndex()
    shipped.levels.forEach { diversity.admit(it.board) }
    val levels = ArrayList<PackLevel>()
    val rows = mutableListOf("id,world,tier,colors,capacity,spare,moves,states,losing_states,openings,losing_openings,min_decisions,min_temptations,min_countergreedy,best_bot_percent,playtest_feeling,playtest_notes")
    val worldOf = mapOf(3 to "three", 4 to "four", 5 to "five")
    for (capacity in 3..5) {
        val found = HashMap<PuzzleTier, Int>()
        val random = Random(seed + capacity)
        var tried = 0
        while (tried < attempts && PuzzleTier.entries.any { (found[it] ?: 0) < perCell }) {
            tried++
            val colors = 3 + random.nextInt(3)
            // Two spare tubes only ever help the relaxed pool; stop sampling them once it is full.
            val spare = if ((found[PuzzleTier.RELAXED] ?: 0) < perCell && random.nextInt(3) == 0) 2 else 1
            val board = ShuffleConstruction.candidate(PuzzleGenerator.equalCapacityGoal(colors, capacity, spare), random)
            if (board.isSolved) continue
            val analysis = (PuzzleAnalyzer.analyze(board, maxStates, 100) as? AnalysisOutcome.Analyzed)?.analysis ?: continue
            val tier = analysis.tier ?: continue
            if ((found[tier] ?: 0) >= perCell || analysis.solution.size < 5) continue
            if (diversity.admit(board) != DiversityAdmission.ADDED) continue
            found[tier] = (found[tier] ?: 0) + 1
            val digest = java.security.MessageDigest.getInstance("SHA-256").digest(board.toString().toByteArray(Charsets.UTF_8))
                .take(6).joinToString("") { "%02x".format(it.toInt() and 255) }
            val id = "${worldOf.getValue(capacity)}-${tier.name.lowercase()}-$digest"
            val role = when (tier) {
                PuzzleTier.RELAXED -> LevelRole.RELAXED
                PuzzleTier.BALANCED -> LevelRole.REGULAR
                else -> LevelRole.CHALLENGE
            }
            levels += PackLevel(id, worldOf.getValue(capacity), role, null, true, board, analysis.solution)
            rows += listOf(id, worldOf.getValue(capacity), tier, colors, capacity, spare, analysis.solution.size, analysis.states,
                analysis.losingStates, analysis.openings, analysis.losingOpenings, analysis.minDecisions,
                analysis.minTemptations, analysis.minCounterGreedy, analysis.bestBotPercent, "", "").joinToString(",")
        }
        println("capacity $capacity: " + PuzzleTier.entries.joinToString { "${it.name.lowercase()} ${found[it] ?: 0}/$perCell" } + " after $tried shuffles")
    }
    require(levels.isNotEmpty()) { "No candidates found" }
    require(output.mkdirs())
    File(output, "pool.txt").writeText(LevelPackCodec.encode(LevelPack(shipped.worlds, levels)))
    File(output, "candidates.csv").writeText(rows.joinToString("\n", postfix = "\n"))
    File(output, "parameters.txt").writeText("seed=$seed\nper_tier_and_capacity=$perCell\nanalysis_states=$maxStates\nbot_trials=100\nattempts_per_capacity=$attempts\nexcluded=structural duplicates of each other and of playtest-v2\n")
    println("${levels.size} candidates in ${output.absolutePath}")
}

/** PC timing and yield of the on-device Infinite recipe. Not a phone measurement. */
internal fun runSeedBenchmark(options: Map<String, String>) {
    val count = options.number("count", 100)
    val seed = options.longNumber("seed", 1)
    repeat(20) { runCatching { SeededPuzzle.create(SeededPuzzle.code(900_000L + it, 'B')) } } // Warm up the JIT.
    for (recipe in "RBC") {
        val millis = ArrayList<Long>()
        val moves = ArrayList<Long>()
        val shapes = HashMap<String, Int>()
        var failed = 0
        repeat(count) { index ->
            val started = System.nanoTime()
            val level = try { SeededPuzzle.create(SeededPuzzle.code(seed + index, recipe)) } catch (_: IllegalArgumentException) { null }
            millis.add((System.nanoTime() - started) / 1_000_000)
            if (level == null) failed++ else {
                moves.add(level.solution.size.toLong())
                val shape = "${level.board.tubes.flatMap { it.balls }.distinct().size} colors x ${level.board.tubes.first().capacity}, ${level.board.tubes.count { it.balls.isEmpty() }} spare"
                shapes[shape] = (shapes[shape] ?: 0) + 1
            }
        }
        println("E2-$recipe: $failed/$count seeds without a puzzle; ms p50 ${percentile(millis, 50)} p90 ${percentile(millis, 90)} max ${millis.max()}; " +
            "moves p50 ${percentile(moves, 50)} min ${moves.minOrNull()} max ${moves.maxOrNull()}")
        println("  " + shapes.entries.sortedByDescending { it.value }.joinToString { "${it.key}: ${it.value}" })
    }
}
