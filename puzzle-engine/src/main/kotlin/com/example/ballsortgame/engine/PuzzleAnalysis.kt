package com.example.ballsortgame.engine

import com.example.ballsortgame.engine.tools.ChallengeStrategy
import com.example.ballsortgame.engine.tools.DecisionGraph
import com.example.ballsortgame.engine.tools.challengeRank
import com.example.ballsortgame.engine.tools.compareRanks
import com.example.ballsortgame.engine.tools.decisionStructure
import com.example.ballsortgame.engine.tools.effectiveChoices
import java.util.Random

/**
 * Provisional machine tiers anchored to the creator's rated puzzles, not a human difficulty scale.
 *
 * - RELAXED: a simple rule of thumb almost always finishes it (Dusk 9, Moonlight 10).
 * - BALANCED: rules of thumb sometimes fail, and some choices matter (Midnight 10 is near its hard edge).
 * - CHALLENGE: rules of thumb nearly always fail and every shortest route passes misleading choices
 *   (Dusk 10, Moonlight 11).
 * - EXPERT: as CHALLENGE with many more required decisions and traps (Midnight 11).
 */
enum class PuzzleTier { RELAXED, BALANCED, CHALLENGE, EXPERT }

/**
 * Exact description of one puzzle from its complete reachable state graph.
 * Counts are over tube-symmetry-reduced states. [solution] is a shortest solution.
 */
data class PuzzleAnalysis(
    val solution: List<Move>,
    val states: Int,
    val losingStates: Int,
    val openings: Int,
    val losingOpenings: Int,
    /** Minimum over every shortest route of positions with a choice that is losing or slower. */
    val minDecisions: Int,
    /** Minimum over every shortest route of positions where a rule of thumb's favourite is losing. */
    val minTemptations: Int,
    /** Minimum over every shortest route of positions where no rule of thumb's favourite is optimal. */
    val minCounterGreedy: Int,
    val botTrials: Int,
    /** Solves by the most successful of four simple strategies, each run [botTrials] times. */
    val bestBotSuccesses: Int,
) {
    val bestBotPercent: Int get() = bestBotSuccesses * 100 / botTrials

    /** Null when the measurements fit none of the anchored tiers; such puzzles are not offered. */
    val tier: PuzzleTier? get() {
        val misleading = minTemptations + minCounterGreedy
        return when {
            bestBotPercent >= 90 -> PuzzleTier.RELAXED
            bestBotPercent <= 5 && minDecisions >= 12 && minTemptations >= 6 -> PuzzleTier.EXPERT
            bestBotPercent < 20 && minDecisions >= 5 && misleading >= 2 -> PuzzleTier.CHALLENGE
            bestBotPercent in 20..89 && minDecisions >= 3 -> PuzzleTier.BALANCED
            else -> null
        }
    }
}

sealed interface AnalysisOutcome {
    data class Analyzed(val analysis: PuzzleAnalysis) : AnalysisOutcome
    /** Proven: the complete graph contains no solved state. */
    data object Unsolvable : AnalysisOutcome
    /** The state limit was reached. Nothing is known about solvability. */
    data object TooLarge : AnalysisOutcome
    data object Cancelled : AnalysisOutcome
}

/**
 * Shared by desktop authoring and on-device Infinite generation. Work is bounded by a state
 * count, never by wall-clock time, so the same board gives the same answer on every device.
 */
object PuzzleAnalyzer {
    const val DEFAULT_MAX_STATES = 20_000
    const val DEFAULT_BOT_TRIALS = 40

    fun analyze(
        board: Board,
        maxStates: Int = DEFAULT_MAX_STATES,
        botTrials: Int = DEFAULT_BOT_TRIALS,
        cancelled: () -> Boolean = { false },
    ): AnalysisOutcome {
        require(maxStates > 0 && botTrials in 1..1000)
        require(!board.isSolved) { "Already solved" }
        val graph = DecisionGraph.explore(board, maxStates, Long.MAX_VALUE, cancelled)
            ?: return if (cancelled() || Thread.currentThread().isInterrupted) AnalysisOutcome.Cancelled else AnalysisOutcome.TooLarge
        val shortest = graph.distance(board) ?: return AnalysisOutcome.Unsolvable
        val structure = decisionStructure(graph)

        val solution = ArrayList<Move>(shortest)
        var cursor = board
        while (!cursor.isSolved) {
            val remaining = requireNotNull(graph.distance(cursor))
            val step = graph.choices(cursor).first { it.remaining == remaining - 1 }
            solution.add(step.move)
            cursor = step.next
        }
        check(solution.size == shortest && PuzzleRules.verifiesSolution(board, solution))

        val best = bestBotSuccesses(graph, botTrials, cancelled) ?: return AnalysisOutcome.Cancelled
        val openings = graph.choices(board)
        return AnalysisOutcome.Analyzed(PuzzleAnalysis(
            solution, graph.boards.size, graph.distance.count { it < 0 },
            openings.size, openings.count { it.remaining == null },
            structure.minDecisions, structure.minTemptations, structure.minCounterGreedy, botTrials, best,
        ))
    }

    /**
     * Each simple strategy plays [trials] games on the finished graph: always its top-ranked
     * unvisited move, ties broken at random, no Undo, at most 200 moves. Playing on the graph
     * avoids rebuilding boards and keys for every move, which dominated on-device cost.
     * (The desktop challenge review still shuffles tube order for ties; results are close but not identical.)
     */
    private fun bestBotSuccesses(graph: DecisionGraph, trials: Int, cancelled: () -> Boolean): Int? {
        val strategies = ChallengeStrategy.entries
        val ranks = arrayOfNulls<List<List<List<Int>>>>(graph.boards.size)
        fun ranksOf(state: Int): List<List<List<Int>>> = ranks[state] ?: run {
            val board = graph.boards[state]
            val moves = effectiveChoices(board)
            strategies.map { strategy -> moves.map { challengeRank(board, it.first, strategy) } }.also { ranks[state] = it }
        }
        val visited = IntArray(graph.boards.size)
        var stamp = 0
        val random = Random(51_000L)
        var best = 0
        for (strategy in strategies.indices) {
            var solved = 0
            for (trial in 0 until trials) {
                if (cancelled()) return null
                stamp++
                var state = 0
                visited[0] = stamp
                var steps = 0
                while (true) {
                    if (graph.distance[state] == 0) { solved++; break }
                    if (steps++ >= 200) break
                    val successors = graph.edges[state]
                    val rank = ranksOf(state)[strategy]
                    var chosen = -1
                    var ties = 0
                    for (choice in successors.indices) {
                        if (visited[successors[choice]] == stamp) continue
                        val order = if (chosen < 0) 1 else compareRanks(rank[choice], rank[chosen])
                        if (order > 0) { chosen = choice; ties = 1 }
                        else if (order == 0 && random.nextInt(++ties) == 0) chosen = choice
                    }
                    if (chosen < 0) break
                    state = successors[chosen]
                    visited[state] = stamp
                }
            }
            best = maxOf(best, solved)
        }
        return best
    }

    /** Explicit Fisher–Yates so seeded results never depend on a platform's library shuffle. */
    internal fun <T> shuffle(items: MutableList<T>, random: Random) {
        for (i in items.size downTo 2) {
            val j = random.nextInt(i)
            val swap = items[i - 1]; items[i - 1] = items[j]; items[j] = swap
        }
    }
}
