package com.example.ballsortgame.engine

/** One code identifies one construction, independent of evaluation time and previous puzzles. */
object SeededPuzzle {
    fun create(code: String, cancelled: () -> Boolean = { false }): PackLevel {
        val parts = code.trim().uppercase().split("-")
        require(parts.size == 3 && parts[0] == "E1" && parts[1] in listOf("B", "C")) { "Use E1-B-seed or E1-C-seed" }
        val seed = parts[2].toLong(36)
        require(seed in 0..281474976710655L)
        val challenge = parts[1] == "C"
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
        return PackLevel("endless-${parts[1].lowercase()}-${seed.toString(36)}", "endless",
            if (challenge) LevelRole.CHALLENGE else LevelRole.REGULAR, seed, false, p.board, p.solution)
    }
    fun code(seed: Long, challenge: Boolean) = "E1-${if (challenge) "C" else "B"}-${seed.toString(36).uppercase()}"
}
