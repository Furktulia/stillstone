package com.example.ballsortgame.engine

data class OpeningChoices(val solvable: Int, val provenDead: Int, val unresolved: Int) {
    val total: Int get() = solvable + provenDead + unresolved
}

/** Offline probe of distinct opening states, not a difficulty score or a complete trap map. */
object OpeningAnalysis {
    fun analyze(board: Board, options: SearchOptions = SearchOptions()): OpeningChoices {
        val successors = PuzzleRules.legalMoves(board).map { requireNotNull(PuzzleRules.transfer(board, it)).board }
            .distinctBy { it.canonicalKey() }
        var solvable = 0
        var dead = 0
        var unresolved = 0
        for (next in successors) {
            when (val result = PuzzleSolver().solve(next, options)) {
                is SolveResult.Solved -> {
                    check(PuzzleRules.verifiesSolution(next, result.moves))
                    solvable++
                }
                is SolveResult.Unsolvable -> dead++
                is SolveResult.LimitReached, is SolveResult.Cancelled -> unresolved++
            }
        }
        return OpeningChoices(solvable, dead, unresolved)
    }
}
