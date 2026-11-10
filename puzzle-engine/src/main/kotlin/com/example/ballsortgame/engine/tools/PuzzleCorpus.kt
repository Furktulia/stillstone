package com.example.ballsortgame.engine.tools

import com.example.ballsortgame.engine.*

/** Versioned literal fixtures. Prefix cases are correlated hint positions, not independent puzzles. */
data class CorpusCase(val id: String, val family: String, val board: Board, val shortestMoves: Int?) {
    val solvable: Boolean get() = shortestMoves != null
}

object PuzzleCorpus {
    const val VERSION = 1

    private fun equal(capacity: Int, vararg contents: List<Int>) =
        Board(contents.map { Tube(capacity, it) })

    val trappedPartials = equal(3, listOf(1, 0, 2), listOf(1, 0, 2), listOf(0, 2), listOf(1))

    private val medium = equal(
        4, listOf(1, 2, 0, 0), listOf(2, 3, 0, 3), listOf(0, 1, 3, 1),
        listOf(3, 1, 2, 2), emptyList(), emptyList(),
    )
    private val mediumSolution = listOf(
        Move(0, 4), Move(3, 0), Move(2, 3), Move(1, 2), Move(1, 4), Move(2, 1),
        Move(3, 2), Move(1, 3), Move(0, 1), Move(2, 0), Move(2, 4),
    )
    private val large = equal(
        4, listOf(5, 0, 6, 4), listOf(0, 7, 0, 1), listOf(2, 1, 1, 4),
        listOf(3, 6, 5, 7), listOf(5, 2, 7, 6), listOf(2, 0, 3, 1),
        listOf(7, 3, 4, 6), listOf(3, 4, 2, 5), emptyList(), emptyList(),
    )
    private val largeSolution = listOf(
        Move(1, 8), Move(4, 9), Move(3, 4), Move(5, 8), Move(6, 9), Move(0, 6),
        Move(0, 9), Move(0, 1), Move(3, 0), Move(3, 9), Move(5, 3), Move(1, 5),
        Move(4, 1), Move(7, 0), Move(7, 4), Move(6, 7), Move(6, 3), Move(1, 6),
        Move(5, 1), Move(5, 4), Move(2, 5), Move(2, 8), Move(4, 2), Move(0, 4),
        Move(7, 5), Move(3, 7),
    )

    val cases: List<CorpusCase> = buildList {
        add(CorpusCase("already-solved", "goal", equal(3, listOf(0, 0, 0), emptyList()), 0))
        add(CorpusCase("blocked", "dead-end", equal(2, listOf(0, 1), listOf(1, 0)), null))
        // Counts fit a valid goal (three balls of each color); both initial choices lose.
        add(CorpusCase("trapped-partials", "dead-end-with-legal-moves", trappedPartials, null))
        add(CorpusCase("infeasible-quantities", "infeasible-goal", equal(3, listOf(0, 1), emptyList()), null))
        add(CorpusCase("matching-run", "group-transfer", equal(3, listOf(0, 0), listOf(0)), 1))
        add(CorpusCase("two-color", "small", equal(2, listOf(0, 1), listOf(1, 0), emptyList()), 3))
        add(CorpusCase("three-color-cycle", "small", equal(
            3, listOf(0, 0, 1), listOf(1, 1, 2), listOf(2, 2, 0), emptyList(), emptyList(),
        ), 4))
        add(CorpusCase("six-slots-easy", "capacity-variety", equal(
            6, listOf(0, 0, 0, 1, 1, 1), listOf(1, 1, 1, 0, 0, 0), emptyList(),
        ), 3))
        add(CorpusCase("mixed-consolidation", "mixed-capacity",
            Board(listOf(Tube(2, listOf(0)), Tube(3, listOf(0, 0)))), 1))
        add(CorpusCase("mixed-must-move-completed", "mixed-capacity",
            Board(listOf(Tube(1, listOf(0)), Tube(2, listOf(0, 1)), Tube(2))), 3))
        add(CorpusCase("four-color-root", "four-color", medium, 11))
        add(CorpusCase("eight-color-root", "eight-color", large, 26))
        check(PuzzleRules.verifiesSolution(medium, mediumSolution))
        check(PuzzleRules.verifiesSolution(large, largeSolution))
        for (prefix in listOf(1, 3, 6, 10)) {
            add(CorpusCase("four-color-prefix-$prefix", "four-color-hints",
                PuzzleRules.replay(medium, mediumSolution.take(prefix)), mediumSolution.size - prefix))
        }
        for (prefix in listOf(1, 4, 8, 12, 20, 25)) {
            add(CorpusCase("eight-color-prefix-$prefix", "eight-color-hints",
                PuzzleRules.replay(large, largeSolution.take(prefix)), largeSolution.size - prefix))
        }
    }
}
