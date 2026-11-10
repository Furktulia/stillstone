package com.example.ballsortgame.engine.tools

import com.example.ballsortgame.engine.*
import java.io.File
import java.util.ArrayDeque
import java.util.Random

/** Offline research graph. A partial exploration MUST NOT classify unmarked states as losing. */
internal class DecisionGraph private constructor(
    val boards: List<Board>,
    val edges: List<List<Int>>,
    val distance: IntArray,
    private val indices: Map<String, Int>,
) {
    fun distance(board: Board): Int? = indices[board.canonicalKey()]?.let { distance[it].takeIf { d -> d >= 0 } }
    fun contains(board: Board): Boolean = board.canonicalKey() in indices
    fun choices(board: Board): List<DecisionChoice> = effectiveChoices(board).map { (move, next) ->
        check(contains(next))
        DecisionChoice(move, next, distance(next))
    }

    companion object {
        fun explore(initial: Board, maxStates: Int = 100_000, maxMillis: Long = 5_000,
            cancelled: () -> Boolean = { false }): DecisionGraph? {
            require(maxStates > 0 && maxMillis >= 0)
            val started = System.nanoTime()
            val boards = arrayListOf(initial)
            val indices = hashMapOf(initial.canonicalKey() to 0)
            val edges = ArrayList<List<Int>>()
            var cursor = 0
            while (cursor < boards.size) {
                if ((System.nanoTime() - started) / 1_000_000 >= maxMillis || Thread.currentThread().isInterrupted || cancelled()) return null
                val board = boards[cursor++]
                val nextIndices = ArrayList<Int>()
                if (!board.isSolved) for ((_, next) in effectiveChoices(board)) {
                    val key = next.canonicalKey()
                    val index = indices[key] ?: run {
                        if (boards.size >= maxStates) return null
                        val newIndex = boards.size
                        indices[key] = newIndex
                        boards.add(next)
                        newIndex
                    }
                    nextIndices.add(index)
                }
                edges.add(nextIndices)
            }
            val reverse = List(boards.size) { ArrayList<Int>() }
            edges.forEachIndexed { source, targets -> targets.forEach { reverse[it].add(source) } }
            val distance = IntArray(boards.size) { -1 }
            val queue = ArrayDeque<Int>()
            boards.forEachIndexed { index, board ->
                if (board.isSolved) { distance[index] = 0; queue.add(index) }
            }
            while (queue.isNotEmpty()) {
                val target = queue.removeFirst()
                for (source in reverse[target]) if (distance[source] < 0) {
                    distance[source] = distance[target] + 1
                    queue.add(source)
                }
            }
            return DecisionGraph(boards.toList(), edges.toList(), distance, indices.toMap())
        }
    }
}

internal data class DecisionChoice(val move: Move, val next: Board, val remaining: Int?)

/** Removes only capacity-preserving tube-permutation duplicates and self-transitions. */
internal fun effectiveChoices(board: Board): List<Pair<Move, Board>> {
    if (board.isSolved) return emptyList()
    val key = board.canonicalKey()
    return PuzzleRules.legalMoves(board).map { it to requireNotNull(PuzzleRules.transfer(board, it)).board }
        .filter { it.second.canonicalKey() != key }.distinctBy { it.second.canonicalKey() }
}

internal enum class SurfacePolicy { LARGEST_GROUP, LEAST_BURIED_COLOR }

/** Two deliberately simple hypotheses, NOT a validated psychological player model. */
internal fun surfaceRank(board: Board, move: Move, policy: SurfacePolicy): List<Int> {
    val source = board.tubes[move.source]
    val color = requireNotNull(source.top)
    val transfer = requireNotNull(PuzzleRules.transfer(board, move))
    val matching = if (board.tubes[move.destination].top == color) 1 else 0
    return when (policy) {
        SurfacePolicy.LARGEST_GROUP -> listOf(transfer.count, matching)
        SurfacePolicy.LEAST_BURIED_COLOR -> {
            val blockers = board.tubes.sumOf { tube ->
                val lowest = tube.balls.indexOf(color)
                if (lowest < 0) 0 else tube.balls.drop(lowest).count { it != color }
            }
            val exposed = board.tubes.filter { it.top == color }.sumOf { it.topRun }
            listOf(-blockers, exposed, transfer.count, matching)
        }
    }
}

internal fun compareRanks(first: List<Int>, second: List<Int>): Int {
    for (i in first.indices) if (first[i] != second[i]) return first[i].compareTo(second[i])
    return 0
}

internal fun bestSurfaceMoves(board: Board, policy: SurfacePolicy): List<Move> {
    val moves = effectiveChoices(board).map { it.first }
    val best = moves.maxWithOrNull { a, b -> compareRanks(surfaceRank(board, a, policy), surfaceRank(board, b, policy)) }
        ?: return emptyList()
    val rank = surfaceRank(board, best, policy)
    return moves.filter { surfaceRank(board, it, policy) == rank }
}

internal data class PolicyResult(val status: String, val moves: Int)

/** No backtracking/Undo; repeat states are avoided. Ties use tube-index order. */
internal fun followSurfacePolicy(initial: Board, policy: SurfacePolicy, maxMoves: Int = 200): PolicyResult {
    require(maxMoves >= 0)
    var board = initial
    val seen = hashSetOf(board.canonicalKey())
    repeat(maxMoves) { step ->
        if (board.isSolved) return PolicyResult("SOLVED", step)
        val candidates = effectiveChoices(board).filter { it.second.canonicalKey() !in seen }
        if (candidates.isEmpty()) {
            return PolicyResult(if (PuzzleRules.legalMoves(board).isEmpty()) "NO_LEGAL_MOVES" else "NO_UNVISITED_SUCCESSOR", step)
        }
        val chosen = candidates.maxWith { a, b -> compareRanks(surfaceRank(board, a.first, policy), surfaceRank(board, b.first, policy)) }
        board = chosen.second
        seen.add(board.canonicalKey())
    }
    return PolicyResult(if (board.isSolved) "SOLVED" else "STEP_LIMIT", maxMoves)
}

/** Same logical board, many tube orders: controls arbitrary index-based tie breaking. */
internal fun permutedPolicyResults(board: Board, policy: SurfacePolicy, count: Int, seed: Long): List<PolicyResult> {
    require(count in 1..10_000)
    val random = Random(seed)
    return List(count) {
        val tubes = board.tubes.toMutableList()
        java.util.Collections.shuffle(tubes, random)
        followSurfacePolicy(Board(tubes), policy)
    }
}

internal fun scalePuzzle(board: Board, factor: Int): Board {
    require(factor > 0)
    return Board(board.tubes.map { tube ->
        Tube(Math.multiplyExact(tube.capacity, factor), tube.balls.flatMap { color -> List(factor) { color } })
    })
}

internal fun runDifficultyStudy(options: Map<String, String>) {
    val output = File(options["output"] ?: "build/puzzle-lab/difficulty-study")
    require(!output.exists()) { "Choose a fresh output directory: ${output.absolutePath}" }
    val count = options.number("count", 100)
    require(count in 1..1000)
    val seed = options.longNumber("seed", 90_000)
    require(seed in 0..Long.MAX_VALUE - count)
    val search = SearchOptions(limits(options))
    val graphStates = options.number("graph-states", 100_000)
    val graphMillis = options.longNumber("graph-ms", 5_000)
    require(graphStates in 1..1_000_000 && graphMillis >= 0)
    require(output.mkdirs())
    val pack = BundledLevelPacks.difficultyStudy()
    val cases = listOf("three" to 9, "three" to 10, "four" to 10, "five" to 10)
    val roots = mutableListOf(
        "world,level,id,capacity,colors,balls,certificate_moves,graph_status,reachable_states,winning_states,losing_states,largest_policy,largest_moves,least_buried_policy,least_buried_moves",
    )
    val openings = mutableListOf(
        "world,level,source_1based,destination_1based,color,group_size,status,remaining_shortest,largest_group_best,least_buried_best",
    )
    val decisions = mutableListOf(
        "world,level,certificate_step,remaining_shortest,effective_choices,optimal_choices,solvable_detours,losing_choices,largest_best_count,largest_best_losing,largest_best_optimal,least_buried_best_count,least_buried_best_losing,least_buried_best_optimal",
    )
    val scaling = mutableListOf(
        "world,level,factor,capacity,shortest_moves,reachable_states,transitions_checked,exact_correspondence",
    )
    val policySummary = mutableListOf(
        "world,level,policy,permutation_trials,successes,p50_moves_solved,no_legal_moves,no_unvisited_successor,step_limit",
    )
    for ((index, case) in cases.withIndex()) {
        val (worldId, number) = case
        val level = pack.levels[index]
        check(level.worldId == worldId)
        val graph = DecisionGraph.explore(level.board, graphStates, graphMillis)
        val largest = followSurfacePolicy(level.board, SurfacePolicy.LARGEST_GROUP)
        val accessible = followSurfacePolicy(level.board, SurfacePolicy.LEAST_BURIED_COLOR)
        roots += listOf(
            worldId, number, level.id, level.board.tubes.first().capacity,
            level.board.tubes.flatMap { it.balls }.distinct().size, level.board.tubes.sumOf { it.balls.size },
            level.solution.size, if (graph == null) "LIMIT" else "COMPLETE",
            graph?.boards?.size ?: "", graph?.distance?.count { it >= 0 } ?: "", graph?.distance?.count { it < 0 } ?: "",
            largest.status, largest.moves, accessible.status, accessible.moves,
        ).joinToString(",")
        println("$worldId $number: graph ${graph?.boards?.size ?: "LIMIT"}; policies $largest / $accessible")
        for (policy in SurfacePolicy.entries) {
            val trials = permutedPolicyResults(level.board, policy, 100, 7300L + index)
            val wins = trials.filter { it.status == "SOLVED" }
            policySummary += listOf(
                worldId, number, policy, trials.size, wins.size,
                percentile(wins.map { it.moves.toLong() }, 50) ?: "",
                trials.count { it.status == "NO_LEGAL_MOVES" },
                trials.count { it.status == "NO_UNVISITED_SUCCESSOR" },
                trials.count { it.status == "STEP_LIMIT" },
            ).joinToString(",")
        }
        val names = listOf("coral", "mint", "gold", "blue", "purple")
        val topBest = bestSurfaceMoves(level.board, SurfacePolicy.LARGEST_GROUP)
        val accessibleBest = bestSurfaceMoves(level.board, SurfacePolicy.LEAST_BURIED_COLOR)
        // Keep individual physical tube moves in this report, including equivalent successors.
        for (move in PuzzleRules.legalMoves(level.board)) {
            val transfer = requireNotNull(PuzzleRules.transfer(level.board, move))
            val result = if (graph == null) PuzzleSolver().solve(transfer.board, search) else null
            val remaining = graph?.distance(transfer.board) ?: (result as? SolveResult.Solved)?.moves?.size
            val status = when {
                graph != null -> if (remaining == null) "PROVEN_LOSING" else "SOLVABLE"
                result is SolveResult.Solved -> "SOLVABLE"
                result is SolveResult.Unsolvable -> "PROVEN_LOSING"
                else -> "UNKNOWN"
            }
            openings += listOf(
                worldId, number, move.source + 1, move.destination + 1,
                names[requireNotNull(level.board.tubes[move.source].top)], transfer.count,
                status, remaining ?: "",
                topBest.any { surfaceRank(level.board, it, SurfacePolicy.LARGEST_GROUP) == surfaceRank(level.board, move, SurfacePolicy.LARGEST_GROUP) },
                accessibleBest.any { surfaceRank(level.board, it, SurfacePolicy.LEAST_BURIED_COLOR) == surfaceRank(level.board, move, SurfacePolicy.LEAST_BURIED_COLOR) },
            ).joinToString(",")
        }
        if (graph != null) {
            check(graph.distance(level.board) == level.solution.size)
            var board = level.board
            level.solution.forEachIndexed { step, move ->
                val choices = graph.choices(board)
                val remaining = requireNotNull(graph.distance(board))
                fun features(policy: SurfacePolicy): List<Int> {
                    val best = bestSurfaceMoves(board, policy).toSet()
                    val chosen = choices.filter { it.move in best }
                    return listOf(chosen.size, chosen.count { it.remaining == null }, chosen.count { it.remaining == remaining - 1 })
                }
                decisions += (listOf(
                    worldId, number, step + 1, remaining, choices.size,
                    choices.count { it.remaining == remaining - 1 },
                    choices.count { it.remaining != null && it.remaining >= remaining },
                    choices.count { it.remaining == null },
                ) + features(SurfacePolicy.LARGEST_GROUP) + features(SurfacePolicy.LEAST_BURIED_COLOR)).joinToString(",")
                board = requireNotNull(PuzzleRules.transfer(board, move)).board
            }
            for (factor in listOf(1, 2)) {
                var transitions = 0
                for (original in graph.boards) {
                    val scaled = scalePuzzle(original, factor)
                    check(scaled.isSolved == original.isSolved)
                    val legal = PuzzleRules.legalMoves(original)
                    check(PuzzleRules.legalMoves(scaled) == legal)
                    for (move in legal) {
                        val a = requireNotNull(PuzzleRules.transfer(original, move))
                        val b = requireNotNull(PuzzleRules.transfer(scaled, move))
                        check(b.board == scalePuzzle(a.board, factor) && b.count == a.count * factor)
                        transitions++
                    }
                }
                check(PuzzleRules.verifiesSolution(scalePuzzle(level.board, factor), level.solution))
                scaling += listOf(
                    worldId, number, factor, level.board.tubes.first().capacity * factor,
                    level.solution.size, graph.boards.size, transitions, true,
                ).joinToString(",")
            }
        }
    }
    fun write(name: String, rows: List<String>) = File(output, name).writeText(rows.joinToString("\n", postfix = "\n"))
    write("levels.csv", roots)
    write("openings.csv", openings)
    write("decisions.csv", decisions)
    write("scaling.csv", scaling)
    write("policy-summary.csv", policySummary)

    // Single-attempt uniformly shuffled labeled starts, no solvability/difficulty filtering.
    // Identical seeds at different capacities are strata, not structurally paired puzzles.
    val raw = mutableListOf(
        "capacity,colors,empty_tubes,seed,status,shortest_moves,boundaries,runs,expanded,search_ms,largest_policy,largest_moves,least_buried_policy,least_buried_moves",
    )
    val summary = mutableListOf(
        "capacity,colors,empty_tubes,samples,solved,proven_unsolvable,limited,p50_shortest_solved,p90_shortest_solved,p50_boundaries_solved,largest_success_among_solved,least_buried_success_among_solved",
    )
    // Warm up before collecting timings. Timings are still PC observations, not human difficulty.
    repeat(5) { PuzzleSolver().solve(pack.levels[1].board, search) }
    for (empty in 1..2) for (capacity in 3..6) {
        val goal = PuzzleGenerator.equalCapacityGoal(5, capacity, empty)
        val solvedLengths = ArrayList<Long>()
        val solvedBoundaries = ArrayList<Long>()
        var unsolvable = 0
        var limited = 0
        var topSuccess = 0
        var accessSuccess = 0
        repeat(count) { index ->
            val currentSeed = Math.addExact(seed, index.toLong())
            val board = ShuffleConstruction.candidate(goal, Random(currentSeed))
            val result = PuzzleSolver().solve(board, search)
            val solved = result as? SolveResult.Solved
            val top = followSurfacePolicy(board, SurfacePolicy.LARGEST_GROUP)
            val access = followSurfacePolicy(board, SurfacePolicy.LEAST_BURIED_COLOR)
            if (solved != null) {
                check(solved.optimal && PuzzleRules.verifiesSolution(board, solved.moves))
                solvedLengths.add(solved.moves.size.toLong())
                solvedBoundaries.add(PuzzleRules.boundaryCount(board).toLong())
                if (top.status == "SOLVED") topSuccess++
                if (access.status == "SOLVED") accessSuccess++
            } else if (result is SolveResult.Unsolvable) unsolvable++ else limited++
            raw += listOf(
                capacity, 5, empty, currentSeed, resultStatus(result), solved?.moves?.size ?: "",
                PuzzleRules.boundaryCount(board), board.tubes.sumOf { it.runs },
                result.statistics.expandedStates, result.statistics.elapsedMillis,
                top.status, top.moves, access.status, access.moves,
            ).joinToString(",")
        }
        summary += listOf(
            capacity, 5, empty, count, solvedLengths.size, unsolvable, limited,
            percentile(solvedLengths, 50) ?: "", percentile(solvedLengths, 90) ?: "",
            percentile(solvedBoundaries, 50) ?: "", topSuccess, accessSuccess,
        ).joinToString(",")
        println(summary.last())
        write("capacity-raw.csv", raw)
        write("capacity-summary.csv", summary)
    }
    File(output, "parameters.txt").writeText(
        "protocol=1\nfixtures=difficulty-study-v1.txt\nrules=1\ncolors=5\ncapacities=3,4,5,6\nempty_tubes=1,2\nseed=$seed\ncount_per_profile=$count\n" +
            "search=$search\ngraph_max_states=$graphStates\ngraph_max_ms=$graphMillis\n" +
            "policies=largest transfer then matching; least blockers then exposed count then largest transfer then matching\n" +
            "policy_limits=200 moves; no Undo/backtracking; avoid repeated quotient states; ties by tube indices\n" +
            "policy_tie_control=100 independently shuffled tube orders per case/policy; seed=7300+case_index\n" +
            "partial_graphs=unknown; survey search limits=unknown, not unsolvable\n",
    )
    println("Study saved to ${output.absolutePath}. These are machine proxies, not human skill-ceiling measurements.")
}
