package com.example.ballsortgame.engine

import java.util.PriorityQueue

data class SearchLimits(
    val maxExpandedStates: Int = 250_000,
    val maxStoredStates: Int = 400_000,
    val maxFrontierEntries: Int = 500_000,
    val maxMillis: Long = 5_000,
) {
    init {
        require(maxExpandedStates >= 0)
        require(maxStoredStates >= 1 && maxFrontierEntries >= 1)
        require(maxMillis >= 0)
    }
}

data class SearchOptions(
    val limits: SearchLimits = SearchLimits(),
    /** 1 = optimal A*. Larger weights favor speed and do not certify a shortest path. */
    val heuristicWeight: Int = 1,
    val useTubeSymmetry: Boolean = true,
) {
    init { require(heuristicWeight in 1..1000) }
}

data class SearchStatistics(
    val expandedStates: Int,
    val discoveredStates: Int,
    val peakFrontierEntries: Int,
    val elapsedMillis: Long,
)

enum class LimitReason { TIME, EXPANSIONS, STORED_STATES, FRONTIER }

sealed interface SolveResult {
    val statistics: SearchStatistics

    data class Solved(
        val moves: List<Move>,
        val optimal: Boolean,
        override val statistics: SearchStatistics,
    ) : SolveResult

    data class Unsolvable(override val statistics: SearchStatistics) : SolveResult
    data class LimitReached(
        val reason: LimitReason,
        override val statistics: SearchStatistics,
    ) : SolveResult
    data class Cancelled(override val statistics: SearchStatistics) : SolveResult
}

/**
 * A-star / weighted A-star with reopening and capacity-aware tube symmetry.
 * No puzzle-specific move pruning: in particular completed tubes are never locked.
 * Exhaustion proves unsolvability; budgets and cancellation never do.
 * Thread-safe: all search state belongs to one invocation.
 */
class PuzzleSolver {
    private class Node(
        val board: Board,
        val key: String,
        val depth: Int,
        val estimate: Int,
        val parent: Node?,
        val move: Move?,
        val sequence: Long,
    )

    fun solve(
        initial: Board,
        options: SearchOptions = SearchOptions(),
        cancelled: () -> Boolean = { false },
    ): SolveResult {
        val started = System.nanoTime()
        var expanded = 0
        var peak = 1
        val bestDepth = HashMap<String, Int>()
        fun statistics() = SearchStatistics(
            expanded, bestDepth.size, peak, (System.nanoTime() - started) / 1_000_000,
        )
        fun key(board: Board) = if (options.useTubeSymmetry) board.canonicalKey() else board.toString()
        val queue = PriorityQueue<Node>(
            compareBy<Node> { it.depth.toLong() + options.heuristicWeight.toLong() * it.estimate }
                .thenBy { it.estimate }
                .thenBy { it.sequence },
        )
        val rootKey = key(initial)
        var sequence = 0L
        bestDepth[rootKey] = 0
        queue.add(Node(initial, rootKey, 0, PuzzleRules.lowerBound(initial), null, null, sequence++))
        val limits = options.limits
        while (queue.isNotEmpty()) {
            if (cancelled() || Thread.currentThread().isInterrupted) return SolveResult.Cancelled(statistics())
            val node = queue.remove()
            if (bestDepth[node.key] != node.depth) continue
            if (node.board.isSolved) {
                val reversed = ArrayList<Move>()
                var cursor: Node? = node
                while (cursor?.parent != null) {
                    reversed.add(requireNotNull(cursor.move))
                    cursor = cursor.parent
                }
                return SolveResult.Solved(reversed.asReversed().toList(), options.heuristicWeight == 1, statistics())
            }
            if ((System.nanoTime() - started) / 1_000_000 >= limits.maxMillis) {
                return SolveResult.LimitReached(LimitReason.TIME, statistics())
            }
            if (expanded >= limits.maxExpandedStates) {
                return SolveResult.LimitReached(LimitReason.EXPANSIONS, statistics())
            }
            expanded++
            for (move in PuzzleRules.legalMoves(node.board)) {
                val next = requireNotNull(PuzzleRules.transfer(node.board, move)).board
                val nextKey = key(next)
                val nextDepth = node.depth + 1
                val previous = bestDepth[nextKey]
                if (previous != null && previous <= nextDepth) continue
                if (previous == null && bestDepth.size >= limits.maxStoredStates) {
                    return SolveResult.LimitReached(LimitReason.STORED_STATES, statistics())
                }
                if (queue.size >= limits.maxFrontierEntries) {
                    return SolveResult.LimitReached(LimitReason.FRONTIER, statistics())
                }
                bestDepth[nextKey] = nextDepth
                queue.add(Node(next, nextKey, nextDepth, PuzzleRules.lowerBound(next), node, move, sequence++))
                peak = maxOf(peak, queue.size)
            }
        }
        return SolveResult.Unsolvable(statistics())
    }
}
