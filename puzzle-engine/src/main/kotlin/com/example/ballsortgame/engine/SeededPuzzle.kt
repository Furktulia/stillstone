package com.example.ballsortgame.engine

import java.util.Random

/**
 * One code identifies one construction, independent of evaluation time and previous puzzles.
 *
 * E1 (frozen): reverse construction with no quality check. B = 3 colors / 2 spare tubes,
 * C = 5 colors / 1 spare. Kept so published codes and saved examples still replay.
 *
 * E2: shuffled starts, each analyzed exactly and accepted only when its measured tier
 * matches the request. R = relaxed, B = balanced, C = challenge (see [PuzzleTier]).
 * The stored solution is a shortest one, so hints follow a shortest route.
 */
object SeededPuzzle {
    private const val MAX_SEED = 281474976710655L

    /** Colors, capacity and spare tubes of a full goal, plus the least interesting length worth offering. */
    private class Recipe(val colors: Int, val capacity: Int, val spare: Int, val minMoves: Int)

    // Chosen from tier-survey measurements (docs/puzzle-quality.md). With two spare tubes almost
    // every shuffled start is RELAXED; with one, BALANCED and CHALLENGE each appear in roughly
    // one start in ten. Changing these lists changes what E2 codes mean: add E3 instead.
    private val relaxed = listOf(
        Recipe(3, 3, 2, 5), Recipe(4, 3, 2, 6), Recipe(3, 4, 2, 6), Recipe(4, 4, 2, 8), Recipe(3, 5, 2, 7),
        Recipe(3, 4, 1, 6), Recipe(3, 5, 1, 7), Recipe(4, 4, 1, 8),
    )
    private val balanced = listOf(
        Recipe(4, 3, 1, 7), Recipe(5, 3, 1, 8), Recipe(3, 4, 1, 7), Recipe(4, 4, 1, 8),
        Recipe(5, 4, 1, 9), Recipe(3, 5, 1, 8), Recipe(4, 5, 1, 9),
    )
    private val challenge = listOf(
        Recipe(5, 3, 1, 9), Recipe(4, 4, 1, 9), Recipe(5, 4, 1, 10), Recipe(4, 5, 1, 10), Recipe(5, 5, 1, 12),
    )
    private const val E2_CANDIDATES = 48
    private const val E2_MAX_STATES = 3_000

    fun create(code: String, cancelled: () -> Boolean = { false }): PackLevel {
        val parts = code.trim().uppercase().split("-")
        require(parts.size == 3 && parts[0] in listOf("E1", "E2", "H1")) { "Use a code such as E2-B-seed" }
        val seed = requireNotNull(parts[2].toLongOrNull(36)) { "Use a code such as E2-B-seed" }
        require(seed in 0..MAX_SEED)
        return when (parts[0]) {
            "E1" -> createE1(parts[1], seed, cancelled)
            "E2" -> createE2(parts[1], seed, cancelled, hidden = false)
            else -> createE2(parts[1], seed, cancelled, hidden = true)
        }
    }

    /** Fewer games than the open measure: these are played on real boards, which costs more per move. */
    private const val H1_BLIND_TRIALS = 20

    private fun createE1(recipe: String, seed: Long, cancelled: () -> Boolean): PackLevel {
        require(recipe in listOf("B", "C")) { "Use E1-B-seed or E1-C-seed" }
        val challenge = recipe == "C"
        val capacity = 3 + (seed % 3).toInt()
        val colors = if (challenge) 5 else 3
        val goal = PuzzleGenerator.equalCapacityGoal(colors, capacity, if (challenge) 1 else 2)
        // Fixed work limits; construction certificates survive skipped solver evaluation.
        val result = PuzzleGenerator().generate(GenerationRequest(
            goal, seed, reverseSteps = if (challenge) 100 else 50, maxAttempts = 16,
            minBoundaries = 2, minEmptyTubes = if (challenge) 0 else 1, requireOptimalSolution = false,
            search = SearchOptions(SearchLimits(0, 1, 1, 0)),
            strategy = GenerationStrategy.CERTIFIED_REVERSE,
        ), cancelled = cancelled)
        require(result is GenerationResult.Generated) { "This code has no suitable construction; try another code" }
        val p = result.puzzle
        return PackLevel("endless-${recipe.lowercase()}-${seed.toString(36)}", "endless",
            if (challenge) LevelRole.CHALLENGE else LevelRole.REGULAR, seed, false, p.board, p.solution)
    }

    /**
     * [hidden] is the H1 recipe, for play with hidden colours: the same starts, but the tier is
     * taken from how simple strategies fare when they cannot see buried stones. With one
     * spare tube about half of the starts that are relaxed in the open stop being relaxed
     * once colours are hidden, so E2 codes are not reused for that way of playing.
     */
    private fun createE2(recipe: String, seed: Long, cancelled: () -> Boolean, hidden: Boolean): PackLevel {
        val (recipes, wanted, role) = when (recipe) {
            "R" -> Triple(relaxed, setOf(PuzzleTier.RELAXED), LevelRole.RELAXED)
            "B" -> Triple(balanced, setOf(PuzzleTier.BALANCED), LevelRole.REGULAR)
            // Expert boards are rare (well under one start in a hundred) and welcome here.
            "C" -> Triple(challenge, setOf(PuzzleTier.CHALLENGE, PuzzleTier.EXPERT), LevelRole.CHALLENGE)
            else -> throw IllegalArgumentException("Use E2-R-seed, E2-B-seed or E2-C-seed")
        }
        val version = if (hidden) "h1" else "e2"
        // java.util.Random's sequence is fixed by its specification; the shuffle is our own.
        // Neighbouring seeds give Random nearly identical first values, so scramble first (SplitMix64).
        var mixed = seed + 0x9E3779B97F4A7C15uL.toLong()
        mixed = (mixed xor (mixed ushr 30)) * 0xBF58476D1CE4E5B9uL.toLong()
        mixed = (mixed xor (mixed ushr 27)) * 0x94D049BB133111EBuL.toLong()
        val random = Random(mixed xor (mixed ushr 31))
        repeat(E2_CANDIDATES) {
            val chosen = recipes[random.nextInt(recipes.size)]
            val goal = PuzzleGenerator.equalCapacityGoal(chosen.colors, chosen.capacity, chosen.spare)
            val pieces = goal.tubes.flatMap { it.balls }.toMutableList()
            PuzzleAnalyzer.shuffle(pieces, random)
            var offset = 0
            val board = Board(goal.tubes.map { tube ->
                Tube(tube.capacity, pieces.subList(offset, offset + tube.balls.size).toList()).also { offset += tube.balls.size }
            })
            if (board.isSolved) return@repeat
            when (val outcome = PuzzleAnalyzer.analyze(board, E2_MAX_STATES, PuzzleAnalyzer.DEFAULT_BOT_TRIALS, cancelled)) {
                is AnalysisOutcome.Analyzed -> {
                    val analysis = outcome.analysis
                    if (analysis.solution.size >= chosen.minMoves) {
                        val tier = if (!hidden) analysis.tier
                            // Hiding colours never makes simple play more successful in any way that
                            // matters here, so a start that is not relaxed in the open is not tried.
                            else if (recipe == "R" && analysis.tier != PuzzleTier.RELAXED) null
                            else {
                                val solved = HiddenColors.bestBlindSuccesses(board, H1_BLIND_TRIALS, cancelled)
                                    ?: throw IllegalArgumentException("Puzzle preparation was cancelled")
                                analysis.tierFor(solved * 100 / H1_BLIND_TRIALS)
                            }
                        if (tier in wanted) return PackLevel("endless-$version${recipe.lowercase()}-${seed.toString(36)}",
                            "endless", role, seed, true, board, analysis.solution)
                    }
                }
                AnalysisOutcome.Cancelled -> throw IllegalArgumentException("Puzzle preparation was cancelled")
                AnalysisOutcome.Unsolvable, AnalysisOutcome.TooLarge -> Unit
            }
        }
        throw IllegalArgumentException("This code has no suitable construction; try another code")
    }

    /** E1 code, kept for existing callers and tests. */
    fun code(seed: Long, challenge: Boolean) = "E1-${if (challenge) "C" else "B"}-${seed.toString(36).uppercase()}"

    /** Current-version code. [recipe] is R, B or C. */
    fun code(seed: Long, recipe: Char, hidden: Boolean = false): String {
        require(recipe in "RBC")
        return "${if (hidden) "H1" else "E2"}-$recipe-${seed.toString(36).uppercase()}"
    }
}
