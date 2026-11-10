package com.example.ballsortgame.engine.tools

import com.example.ballsortgame.engine.*
import java.io.File

internal fun referenceChallengeRequirements(reference: ChallengeAssessment): ChallengeRequirements {
    require(reference.complete && reference.shortest != null) { "Reference analysis unresolved; cannot calibrate selection" }
    return ChallengeRequirements(
        maxBotSuccessPercent = (reference.easiestBotSuccesses * 100 + reference.trials - 1) / reference.trials,
        minDecisions = maxOf(3, requireNotNull(reference.minDecisions)),
        minMisleadingCheckpoints = maxOf(1, requireNotNull(reference.minTemptations), requireNotNull(reference.minCounterGreedy)),
    )
}

internal fun referenceChallenge(): PackLevel =
    BundledLevelPacks.difficultyStudy().levels.first { it.id.startsWith("three-challenge-") }

internal fun generatedLevel(world: String, puzzle: GeneratedPuzzle): PackLevel {
    val hash = java.security.MessageDigest.getInstance("SHA-256")
        .digest(puzzle.board.toString().toByteArray(Charsets.UTF_8))
        .take(6).joinToString("") { "%02x".format(it.toInt() and 255) }
    return PackLevel("$world-challenge-${puzzle.seed}-$hash", world, LevelRole.CHALLENGE, puzzle.seed, true, puzzle.board, puzzle.solution)
}

/** Preserves every old board/ID and appends one newly screened finale to each later world. */
internal fun runFinaleRefinement(options: Map<String, String>) {
    val output = File(options["output"] ?: "build/puzzle-lab/refined-finales")
    require(!output.exists()) { "Choose a fresh output directory" }
    val count = options.number("count", 48)
    require(count in 1..200)
    val seed = options.longNumber("seed", 40_000)
    require(seed in 0..Long.MAX_VALUE - 10_200)
    val base = options["input"]?.let { LevelPackCodec.decode(File(it).readText()) } ?: BundledLevelPacks.playtest()
    require(base.worlds.any { it.id == "four" } && base.worlds.any { it.id == "five" })
    val budget = challengeBudget(options)
    val reference = referenceChallenge()
    val referenceAssessment = assessChallenge(reference, budget)
    val requirements = referenceChallengeRequirements(referenceAssessment)
    require(output.mkdirs())
    File(output, "parameters.txt").writeText(
        "selection_protocol=1\nreference=${reference.id}\nrequirements=$requirements\n" +
            "early_temptation_required=true\ncount_per_world=$count\nseed=$seed\nsearch=${SearchOptions(limits(options))}\n" +
            "analysis=$budget\npolicies=${ChallengeStrategy.entries.joinToString()}\ntrial_seed=51000+trial\n" +
            "order=lowest best-bot success; most required decisions; most required temptations; shortest certificate; ID\n" +
            "interpretation=machine screening; human difficulty/fun not proven\n",
    )
    val benchmark = BundledLevelPacks.difficultyStudy()
    val referenceResults = benchmark.levels.associate { it.id to assessChallenge(it, budget) }
    File(output, "reference.csv").writeText(challengeCsv(benchmark.levels, referenceResults, requirements))
    val all = base.levels.toMutableList()
    val assessments = linkedMapOf<String, ChallengeAssessment>()
    val diversity = PuzzleDiversityIndex()
    all.forEach { diversity.admit(it.board) }
    val generationRows = mutableListOf("world,seed,outcome,attempts,rejections")
    val extraRejections = linkedMapOf<String, String>()
    val selected = mutableListOf<PackLevel>()
    for ((worldIndex, world) in listOf("four", "five").withIndex()) {
        val capacity = worldIndex + 4
        val candidates = mutableListOf<PackLevel>()
        repeat(count) { index ->
            val currentSeed = seed + worldIndex * 10_000L + index
            val result = PuzzleGenerator().generate(GenerationRequest(
                PuzzleGenerator.equalCapacityGoal(5, capacity, 1), currentSeed,
                maxAttempts = options.number("attempts", 40), minSolutionMoves = if (capacity == 4) 12 else 14,
                maxSolutionMoves = if (capacity == 4) 26 else 32, requireOptimalSolution = true,
                search = SearchOptions(limits(options)), strategy = GenerationStrategy.SHUFFLE_AND_SOLVE,
            ), diversity)
            generationRows += listOf(
                world, currentSeed, if (result is GenerationResult.Generated) "GENERATED" else "EXHAUSTED",
                result.diagnostics.attempts, result.diagnostics.rejections.entries.joinToString(";") { "${it.key}=${it.value}" },
            ).joinToString(",")
            if (result is GenerationResult.Generated) {
                val level = generatedLevel(world, result.puzzle)
                val assessment = assessChallenge(level, budget)
                all += level
                assessments[level.id] = assessment
                val rejection = requirements.rejectionReasons(assessment).toMutableList()
                if (assessment.openingTemptingLosses == null || assessment.openingTemptingLosses < 1) rejection += "NO_EARLY_TEMPTING_LOSS"
                extraRejections[level.id] = rejection.joinToString(";")
                if (rejection.isEmpty()) candidates += level
            }
            if ((index + 1) % 8 == 0) println("$world: ${index + 1}/$count examined; ${candidates.size} eligible")
            File(output, "generation.csv").writeText(generationRows.joinToString("\n", postfix = "\n"))
            File(output, "candidates.csv").writeText(challengeCsv(all, assessments, requirements))
            File(output, "pool.txt").writeText(LevelPackCodec.encode(LevelPack(base.worlds, all)))
        }
        val winner = candidates.map { it to assessments.getValue(it.id) }.sortedWith(challengeOrder).firstOrNull()?.first
        if (winner != null) {
            selected += winner
            println("$world: selected ${winner.id}")
        }
    }
    File(output, "screening.csv").writeText(
        "id,finale_eligible,rejection_reasons\n" + extraRejections.entries.joinToString("\n", postfix = "\n") {
            "${it.key},${it.value.isEmpty()},${it.value}"
        },
    )
    require(selected.size == 2) { "No complete two-finale selection. Candidate pool and reasons retained; increase count/budget or change seed." }
    val chosen = base.worlds.flatMap { world ->
        base.levels.filter { it.worldId == world.id } + selected.filter { it.worldId == world.id }
    }
    File(output, "playtest.txt").writeText(LevelPackCodec.encode(LevelPack(base.worlds, chosen)))
    File(output, "selection.txt").writeText(chosen.joinToString("\n", postfix = "\n") { it.id })
    File(output, "selected-assessment.csv").writeText(challengeCsv(selected, assessments, requirements))
    println("Exported ${chosen.size} levels. Every previous level is retained; new finales are appended.")
}
