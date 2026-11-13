package com.example.ballsortgame.engine

import java.util.PriorityQueue

/** How a level differs from classic sorting, for grouping and naming. Derived from its rules and tubes. */
enum class LevelMode(val title: String, val slug: String) {
    CLASSIC("Classic", "classic"),
    MIXED("Mixed sizes", "mixed"),
    SEALED("Sealed tubes", "sealed"),
    CONNECTED("Connected boards", "connected"),
    PATTERN("Restore a pattern", "pattern"),
}

/** [tube] cannot give or take stones until [need] other tubes are full and one colour. It then stays open. */
data class Seal(val tube: Int, val need: Int) {
    init { require(tube >= 0 && need > 0) }
}

/** Two shores joined by one tube. Tubes in [left] and the remaining ones can only exchange stones through [shared]. */
data class Bridge(val left: Set<Int>, val shared: Int) {
    init { require(left.isNotEmpty() && shared !in left) }
}

/** A board together with which seals have been opened so far (bit i = seal i of the rules). */
data class RuledPosition(val board: Board, val open: Int = 0)

/**
 * The rules one level is played by. The default is classic sorting, and every classic level
 * keeps exactly its earlier behaviour. Rules never change a tube's contents by themselves:
 * they only restrict or widen which transfers are allowed, and say when the level is done.
 */
data class LevelRules(
    val seals: List<Seal> = emptyList(),
    val bridge: Bridge? = null,
    /** When set, the level is finished only when the board equals this arrangement exactly. */
    val target: Board? = null,
    /** One stone per move, onto any colour. Used for arranging rather than sorting. */
    val singleStone: Boolean = false,
    /**
     * A hidden-colours level: only each tube's top stone shows its colour until the ones
     * beneath are uncovered. Display only; which moves are allowed does not change.
     */
    val hidden: Boolean = false,
) {
    init {
        require(seals.size <= 8 && seals.map { it.tube }.distinct().size == seals.size)
    }

    /** Ordinary sorting moves and goal. A hidden-colours level still plays by classic rules. */
    val isClassic: Boolean get() = seals.isEmpty() && bridge == null && target == null && !singleStone

    fun mode(board: Board): LevelMode = when {
        target != null -> LevelMode.PATTERN
        bridge != null -> LevelMode.CONNECTED
        seals.isNotEmpty() -> LevelMode.SEALED
        board.tubes.map { it.capacity }.distinct().size > 1 -> LevelMode.MIXED
        else -> LevelMode.CLASSIC
    }

    /** Fails for rules that do not fit [board]. */
    fun check(board: Board) {
        val tubes = board.tubes.indices
        require(seals.all { it.tube in tubes }) { "Seal on a missing tube" }
        bridge?.let { require(it.shared in tubes && it.left.all { tube -> tube in tubes }) { "Bridge names a missing tube" } }
        target?.let { goal ->
            require(goal.tubes.map { it.capacity } == board.tubes.map { it.capacity }) { "Target has different tubes" }
            require(goal.tubes.flatMap { it.balls }.sorted() == board.tubes.flatMap { it.balls }.sorted()) { "Target has different stones" }
        }
    }

    fun start(board: Board): RuledPosition = RuledPosition(board, opened(board, 0))

    fun sealed(tube: Int, open: Int): Boolean {
        val index = seals.indexOfFirst { it.tube == tube }
        return index >= 0 && open and (1 shl index) == 0
    }

    /** Whether stones may pass directly between these two tubes, seals aside. */
    fun connected(source: Int, destination: Int): Boolean {
        val link = bridge ?: return true
        return source == link.shared || destination == link.shared || (source in link.left) == (destination in link.left)
    }

    private fun opened(board: Board, open: Int): Int {
        if (seals.isEmpty()) return open
        val complete = board.tubes.indices.count { !sealed(it, open) && board.tubes[it].isComplete }
        var result = open
        seals.forEachIndexed { index, seal -> if (complete >= seal.need) result = result or (1 shl index) }
        return result
    }

    /** The position after [move] and how many stones it carried, or null when the rules forbid it. */
    fun transfer(position: RuledPosition, move: Move): Pair<RuledPosition, Int>? {
        val board = position.board
        if (move.source == move.destination || move.source !in board.tubes.indices || move.destination !in board.tubes.indices) return null
        if (sealed(move.source, position.open) || sealed(move.destination, position.open)) return null
        if (!connected(move.source, move.destination)) return null
        val result = if (!singleStone) PuzzleRules.transfer(board, move) ?: return null else {
            val source = board.tubes[move.source]
            val destination = board.tubes[move.destination]
            val color = source.top ?: return null
            if (destination.freeSpace == 0) return null
            val tubes = board.tubes.toMutableList()
            tubes[move.source] = Tube(source.capacity, source.balls.dropLast(1))
            tubes[move.destination] = Tube(destination.capacity, destination.balls + color)
            Transfer(Board(tubes), 1)
        }
        return RuledPosition(result.board, opened(result.board, position.open)) to result.count
    }

    fun legalMoves(position: RuledPosition): List<Move> = buildList {
        val tubes = position.board.tubes
        for (source in tubes.indices) {
            if (tubes[source].balls.isEmpty()) continue
            for (destination in tubes.indices) {
                val move = Move(source, destination)
                if (transfer(position, move) != null) add(move)
            }
        }
    }

    fun solved(board: Board): Boolean = target?.let { board == it } ?: board.isSolved

    fun replay(board: Board, moves: List<Move>): RuledPosition =
        moves.fold(start(board)) { position, move ->
            requireNotNull(transfer(position, move)) { "Illegal move under this level's rules: $move in ${position.board}" }.first
        }

    fun verifiesSolution(board: Board, moves: List<Move>): Boolean =
        try { check(board); solved(replay(board, moves).board) } catch (_: IllegalArgumentException) { false }

    /** Compact text for the level pack; empty for classic rules. */
    fun encode(): String = buildList {
        if (seals.isNotEmpty()) add("seal=" + seals.joinToString(",") { "${it.tube}@${it.need}" })
        bridge?.let { add("bridge=" + it.left.sorted().joinToString(",") + "/" + it.shared) }
        target?.let { goal -> add("target=" + goal.tubes.joinToString("|") { "${it.capacity}:${it.balls.joinToString(",")}" }) }
        if (singleStone) add("single")
        if (hidden) add("hidden")
    }.joinToString(";")

    companion object {
        val CLASSIC = LevelRules()

        fun decode(text: String): LevelRules {
            if (text.isBlank() || text == "-") return CLASSIC
            var seals = emptyList<Seal>()
            var bridge: Bridge? = null
            var target: Board? = null
            var single = false
            var hidden = false
            for (token in text.split(';')) {
                val name = token.substringBefore('=')
                val value = token.substringAfter('=', "")
                when (name) {
                    "seal" -> seals = value.split(',').map { Seal(it.substringBefore('@').toInt(), it.substringAfter('@').toInt()) }
                    "bridge" -> bridge = Bridge(value.substringBefore('/').split(',').map(String::toInt).toSet(), value.substringAfter('/').toInt())
                    "target" -> target = Board(value.split('|').map { raw ->
                        val capacity = raw.substringBefore(':').toInt()
                        val balls = raw.substringAfter(':')
                        Tube(capacity, if (balls.isEmpty()) emptyList() else balls.split(',').map(String::toInt))
                    })
                    "single" -> single = true
                    "hidden" -> hidden = true
                    else -> throw IllegalArgumentException("Unknown level rule: $name")
                }
            }
            return LevelRules(seals, bridge, target, single, hidden)
        }
    }
}

sealed interface RuledSolveResult {
    /** [moves] is a shortest solution under the level's rules. */
    data class Solved(val moves: List<Move>) : RuledSolveResult
    /** Every reachable position was examined and none is solved. */
    data object Unsolvable : RuledSolveResult
    /** The state limit was reached. Nothing is known about solvability. */
    data object TooLarge : RuledSolveResult
    data object Cancelled : RuledSolveResult
}

/**
 * Shortest solutions for levels with their own rules. Bounded by a state count, never by time.
 * Sorting goals use breadth-first search. An exact target uses A* with the number of stones
 * that are not part of a correct bottom run of their tube: each of those must move at least
 * once, so the estimate never overshoots and the first solution found is a shortest one.
 */
object RuledSolver {
    const val DEFAULT_MAX_STATES = 200_000

    fun solve(rules: LevelRules, start: RuledPosition, maxStates: Int = DEFAULT_MAX_STATES,
        cancelled: () -> Boolean = { false }): RuledSolveResult {
        require(maxStates > 0)
        if (rules.solved(start.board)) return RuledSolveResult.Solved(emptyList())
        class Node(val position: RuledPosition, val parent: Node?, val move: Move?, val cost: Int, val estimate: Int)
        fun estimate(board: Board): Int {
            val goal = rules.target ?: return 0
            var misplaced = 0
            for (tube in board.tubes.indices) {
                val have = board.tubes[tube].balls
                val want = goal.tubes[tube].balls
                var correct = 0
                while (correct < have.size && correct < want.size && have[correct] == want[correct]) correct++
                misplaced += have.size - correct
            }
            return misplaced
        }
        val best = HashMap<RuledPosition, Int>()
        val queue = PriorityQueue<Node>(compareBy<Node> { it.cost + it.estimate }.thenByDescending { it.cost })
        queue.add(Node(start, null, null, 0, estimate(start.board)))
        best[start] = 0
        var examined = 0
        while (queue.isNotEmpty()) {
            val node = queue.remove()
            if (best[node.position] != node.cost) continue
            if (rules.solved(node.position.board)) {
                val moves = ArrayList<Move>()
                var cursor: Node? = node
                while (cursor?.move != null) { moves.add(cursor.move!!); cursor = cursor.parent }
                return RuledSolveResult.Solved(moves.reversed())
            }
            if (++examined and 255 == 0 && (cancelled() || Thread.currentThread().isInterrupted)) return RuledSolveResult.Cancelled
            for (move in rules.legalMoves(node.position)) {
                val next = requireNotNull(rules.transfer(node.position, move)).first
                val cost = node.cost + 1
                val known = best[next]
                if (known != null && known <= cost) continue
                if (known == null && best.size >= maxStates) return RuledSolveResult.TooLarge
                best[next] = cost
                queue.add(Node(next, node, move, cost, estimate(next.board)))
            }
        }
        return RuledSolveResult.Unsolvable
    }
}
