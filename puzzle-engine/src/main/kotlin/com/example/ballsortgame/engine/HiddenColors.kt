package com.example.ballsortgame.engine

import com.example.ballsortgame.engine.tools.ChallengeStrategy
import com.example.ballsortgame.engine.tools.challengeRank
import com.example.ballsortgame.engine.tools.compareRanks
import com.example.ballsortgame.engine.tools.effectiveChoices
import java.util.Random

/**
 * The hidden-colours way of playing: at the start only the top stone of each tube shows its
 * colour. A stone is revealed the first time it is on top, and stays revealed afterwards.
 * The rules of moving are unchanged; only what the player can see differs.
 *
 * A stone that has never been on top has never moved, and neither has anything under it. So
 * the hidden stones of a tube are always its bottom ones, and their number is one less than
 * the smallest size that tube has ever had. That is all the state this rule needs.
 */
object HiddenColors {
    /** Smallest size each tube has had over [initial] and every position reached by [moves]. */
    fun smallestSizes(initial: Board, moves: List<Move>): IntArray {
        val sizes = IntArray(initial.tubes.size) { initial.tubes[it].balls.size }
        var board = initial
        for (move in moves) {
            board = requireNotNull(PuzzleRules.transfer(board, move)) { "Illegal move: $move" }.board
            observe(sizes, board)
        }
        return sizes
    }

    /** Fold one more visited position into [sizes]. */
    fun observe(sizes: IntArray, board: Board) {
        for (tube in sizes.indices) sizes[tube] = minOf(sizes[tube], board.tubes[tube].balls.size)
    }

    /** How many bottom stones of each tube are still hidden. */
    fun hiddenCounts(smallestSizes: IntArray): List<Int> = smallestSizes.map { (it - 1).coerceAtLeast(0) }

    /** What the player sees: every hidden stone becomes a colour of its own that matches nothing. */
    internal fun visible(board: Board, smallestSizes: IntArray): Board = Board(board.tubes.mapIndexed { tube, contents ->
        val hidden = (smallestSizes[tube] - 1).coerceAtLeast(0)
        if (hidden == 0) contents
        else Tube(contents.capacity, contents.balls.mapIndexed { index, color -> if (index < hidden) UNKNOWN + tube * 64 + index else color })
    })

    private const val UNKNOWN = 1000

    /**
     * How often the best of the four simple strategies solves [board] when it can only use
     * what is visible. Same rules as the open-board measure in [PuzzleAnalyzer]: its favourite
     * unvisited move, ties at random, no Undo, at most 200 moves. Played on real boards, because
     * the symmetry-reduced graph forgets which tube is which and with it what has been seen.
     */
    fun bestBlindSuccesses(board: Board, trials: Int, cancelled: () -> Boolean = { false }): Int? {
        require(trials in 1..1000)
        val random = Random(52_000L)
        var best = 0
        for (strategy in ChallengeStrategy.entries) {
            var solved = 0
            repeat(trials) {
                if (cancelled()) return null
                var current = board
                val sizes = IntArray(board.tubes.size) { board.tubes[it].balls.size }
                val seen = hashSetOf(current.canonicalKey())
                var steps = 0
                while (true) {
                    if (current.isSolved) { solved++; break }
                    if (steps++ >= 200) break
                    val view = visible(current, sizes)
                    var chosen: Board? = null
                    var chosenRank: List<Int>? = null
                    var ties = 0
                    for ((move, next) in effectiveChoices(current)) {
                        if (next.canonicalKey() in seen) continue
                        val rank = challengeRank(view, move, strategy)
                        val order = chosenRank?.let { compareRanks(rank, it) } ?: 1
                        if (order > 0) { chosen = next; chosenRank = rank; ties = 1 }
                        else if (order == 0 && random.nextInt(++ties) == 0) { chosen = next; chosenRank = rank }
                    }
                    current = chosen ?: break
                    seen.add(current.canonicalKey())
                    observe(sizes, current)
                }
            }
            best = maxOf(best, solved)
        }
        return best
    }
}
