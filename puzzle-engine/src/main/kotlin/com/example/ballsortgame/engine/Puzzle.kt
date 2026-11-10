package com.example.ballsortgame.engine

import java.util.Collections

/** Contents are bottom-to-top. Colors are nonnegative identifiers, independent of rendering. */
class Tube(val capacity: Int, balls: List<Int> = emptyList()) {
    val balls: List<Int> = Collections.unmodifiableList(ArrayList(balls))

    init {
        require(capacity > 0) { "Capacity must be positive" }
        require(this.balls.size <= capacity) { "Tube exceeds capacity" }
        require(this.balls.all { it >= 0 }) { "Colors must be nonnegative" }
    }

    val freeSpace: Int get() = capacity - balls.size
    val top: Int? get() = balls.lastOrNull()
    val topRun: Int get() = balls.asReversed().takeWhile { it == top }.size
    val runs: Int get() = balls.indices.count { it == 0 || balls[it] != balls[it - 1] }
    val isComplete: Boolean get() = balls.size == capacity && runs == 1

    override fun equals(other: Any?): Boolean =
        other is Tube && capacity == other.capacity && balls == other.balls
    override fun hashCode(): Int = 31 * capacity + balls.hashCode()
    override fun toString(): String = "${capacity}:${balls.joinToString(",")}"
}

class Board(tubes: List<Tube>) {
    val tubes: List<Tube> = Collections.unmodifiableList(ArrayList(tubes))

    init {
        require(this.tubes.isNotEmpty()) { "A board must contain tubes" }
        require(this.tubes.sumOf { it.balls.size.toLong() } > 0) { "A board must contain pieces" }
    }

    val isSolved: Boolean get() = tubes.all { it.balls.isEmpty() || it.isComplete }

    /** Only tubes of the same capacity are interchangeable. Color names remain unchanged. */
    fun canonicalKey(): String = tubes.map { it.toString() }.sorted().joinToString("|")

    override fun equals(other: Any?): Boolean = other is Board && tubes == other.tubes
    override fun hashCode(): Int = tubes.hashCode()
    override fun toString(): String = tubes.joinToString(" | ")
}

data class Move(val source: Int, val destination: Int)
data class Transfer(val board: Board, val count: Int)

object PuzzleRules {
    fun transfer(board: Board, move: Move): Transfer? {
        if (move.source == move.destination ||
            move.source !in board.tubes.indices || move.destination !in board.tubes.indices
        ) return null
        val source = board.tubes[move.source]
        val destination = board.tubes[move.destination]
        val color = source.top ?: return null
        if (destination.freeSpace == 0 || (destination.top != null && destination.top != color)) return null
        val count = minOf(source.topRun, destination.freeSpace)
        val tubes = board.tubes.toMutableList()
        tubes[move.source] = Tube(source.capacity, source.balls.dropLast(count))
        tubes[move.destination] = Tube(destination.capacity, destination.balls + List(count) { color })
        return Transfer(Board(tubes), count)
    }

    /** Includes moves out of completed tubes and rearrangements between unequal capacities. */
    fun legalMoves(board: Board): List<Move> = buildList {
        for (source in board.tubes.indices) {
            val tube = board.tubes[source]
            val color = tube.top ?: continue
            for (destination in board.tubes.indices) {
                val target = board.tubes[destination]
                if (source != destination && target.freeSpace > 0 &&
                    (target.top == null || target.top == color)
                ) add(Move(source, destination))
            }
        }
    }

    fun replay(board: Board, moves: List<Move>): Board =
        moves.fold(board) { state, move ->
            requireNotNull(transfer(state, move)) { "Illegal certificate move: $move in $state" }.board
        }

    fun verifiesSolution(board: Board, moves: List<Move>): Boolean =
        try {
            replay(board, moves).isSolved
        } catch (_: IllegalArgumentException) {
            false
        }

    /** Number of adjacent color boundaries. A legal move can remove at most one. */
    fun boundaryCount(board: Board): Int = board.tubes.sumOf { maxOf(0, it.runs - 1) }

    /**
     * Admissible for equal OR mixed capacities and for colors occupying multiple full tubes.
     * A move removes at most one color boundary, and resolves at most two partial tubes.
     */
    fun lowerBound(board: Board): Int {
        val partial = board.tubes.count { it.balls.isNotEmpty() && it.freeSpace > 0 }
        return maxOf(boundaryCount(board), (partial + 1) / 2)
    }
}
