package com.example.ballsortgame.engine.tools

import com.example.ballsortgame.engine.*
import java.io.File
import java.util.Collections
import java.util.Random

/** Version 1 authoring probes. Success rates describe these bots, never human players. */
internal enum class ChallengeStrategy { LARGEST_GROUP, LEAST_BURIED_COLOR, MERGE_FIRST, FREE_TUBE }

internal data class ChallengeBudget(
    val graphStates: Int = 50_000, val graphMillis: Long = 1_000,
    val trials: Int = 100, val policyMoves: Int = 200,
) {
    init {
        require(graphStates > 0 && graphMillis >= 0 && trials in 1..1000 && policyMoves > 0)
    }
}

internal data class ChallengeAssessment(
    val complete: Boolean,
    val states: Int,
    val shortest: Int?,
    val minDecisions: Int?,
    val minTemptations: Int?,
    val maxTemptations: Int?,
    val minCounterGreedy: Int?,
    val openingTemptingLosses: Int?,
    val trials: Int,
    val successes: Map<ChallengeStrategy, Int>,
) {
    init {
        require(trials > 0 && successes.keys == ChallengeStrategy.entries.toSet())
        require(successes.values.all { it in 0..trials })
    }
    val easiestBotSuccesses: Int get() = successes.values.maxOrNull() ?: trials
}

/** Explicit, provisional eligibility rules rather than an invented aggregate difficulty score. */
internal data class ChallengeRequirements(
    val maxBotSuccessPercent: Int = 35,
    val minDecisions: Int = 3,
    val minMisleadingCheckpoints: Int = 1,
) {
    init { require(maxBotSuccessPercent in 0..100 && minDecisions >= 0 && minMisleadingCheckpoints >= 0) }
    fun rejectionReasons(a: ChallengeAssessment): List<String> = buildList {
        if (!a.complete) add("GRAPH_UNRESOLVED")
        if (a.shortest == null) add("NO_PROVEN_SOLUTION")
        if (a.easiestBotSuccesses.toLong() * 100 > maxBotSuccessPercent.toLong() * a.trials) add("SIMPLE_STRATEGY_TOO_SUCCESSFUL")
        if (a.minDecisions == null || a.minDecisions < minDecisions) add("TOO_FEW_REQUIRED_DECISION_CHECKPOINTS")
        if (a.minTemptations == null || a.minCounterGreedy == null ||
            maxOf(a.minTemptations, a.minCounterGreedy) < minMisleadingCheckpoints
        ) add("NO_REQUIRED_MISLEADING_CHECKPOINT")
    }
}

internal fun challengeRank(board: Board, move: Move, strategy: ChallengeStrategy): List<Int> {
    if (strategy == ChallengeStrategy.LARGEST_GROUP) return surfaceRank(board, move, SurfacePolicy.LARGEST_GROUP)
    if (strategy == ChallengeStrategy.LEAST_BURIED_COLOR) return surfaceRank(board, move, SurfacePolicy.LEAST_BURIED_COLOR)
    val transfer = requireNotNull(PuzzleRules.transfer(board, move))
    val matching = if (board.tubes[move.destination].top == board.tubes[move.source].top) 1 else 0
    val freed = if (transfer.board.tubes[move.source].balls.isEmpty()) 1 else 0
    val completed = transfer.board.tubes.count { it.isComplete } - board.tubes.count { it.isComplete }
    val boundaryGain = PuzzleRules.boundaryCount(board) - PuzzleRules.boundaryCount(transfer.board)
    return when (strategy) {
        ChallengeStrategy.MERGE_FIRST -> listOf(matching, completed, transfer.count, boundaryGain)
        ChallengeStrategy.FREE_TUBE -> listOf(freed, completed, boundaryGain, matching, transfer.count)
        else -> error("Handled above")
    }
}

internal fun followChallengeStrategy(initial: Board, strategy: ChallengeStrategy, maxMoves: Int = 200): PolicyResult {
    require(maxMoves > 0)
    var board = initial
    val seen = hashSetOf(board.canonicalKey())
    repeat(maxMoves) { step ->
        if (board.isSolved) return PolicyResult("SOLVED", step)
        val choices = effectiveChoices(board).filter { it.second.canonicalKey() !in seen }
        if (choices.isEmpty()) return PolicyResult("NO_UNVISITED_SUCCESSOR", step)
        val chosen = choices.maxWith { a, b -> compareRanks(challengeRank(board, a.first, strategy), challengeRank(board, b.first, strategy)) }
        board = chosen.second
        seen.add(board.canonicalKey())
    }
    return PolicyResult(if (board.isSolved) "SOLVED" else "STEP_LIMIT", maxMoves)
}

internal fun assessChallenge(level: PackLevel, budget: ChallengeBudget = ChallengeBudget()): ChallengeAssessment {
    // Use the same trial seed for every candidate, independent of its generation seed.
    val permutations = List(budget.trials) { trial ->
        level.board.tubes.toMutableList().also { Collections.shuffle(it, Random(51_000L + trial)) }
    }
    val successes = ChallengeStrategy.entries.associateWith { strategy ->
        permutations.count { followChallengeStrategy(Board(it), strategy, budget.policyMoves).status == "SOLVED" }
    }
    val graph = DecisionGraph.explore(level.board, budget.graphStates, budget.graphMillis)
        ?: return ChallengeAssessment(false, 0, null, null, null, null, null, null, budget.trials, successes)
    val shortest = graph.distance(level.board)
    check(shortest != null) { "Complete graph rejected a replay-certified level: ${level.id}" }
    if (level.shortestProven) check(shortest == level.solution.size) { "Shortest-path claim failed for ${level.id}" }

    val structure = decisionStructure(graph)
    return ChallengeAssessment(
        true, graph.boards.size, shortest, structure.minDecisions, structure.minTemptations, structure.maxTemptations,
        structure.minCounterGreedy, structure.openingTemptingLosses, budget.trials, successes,
    )
}

internal class DecisionStructure(
    val minDecisions: Int, val minTemptations: Int, val maxTemptations: Int,
    val minCounterGreedy: Int, val openingTemptingLosses: Int,
)

/** Requires a solvable root at index 0. Minima are taken over EVERY shortest route. */
internal fun decisionStructure(graph: DecisionGraph): DecisionStructure {
    // Shortest-path edges strictly decrease distance. Dynamic programming takes MIN over
    // all shortest routes; a convenient single certificate cannot hide an easier route.
    val minDecisions = IntArray(graph.boards.size)
    val minTemptations = IntArray(graph.boards.size)
    val maxTemptations = IntArray(graph.boards.size)
    val minCounter = IntArray(graph.boards.size)
    var rootTemptingLosses = 0
    for (index in graph.boards.indices.filter { graph.distance[it] > 0 }.sortedBy { graph.distance[it] }) {
        val board = graph.boards[index]
        val distance = graph.distance[index]
        val choices = graph.choices(board)
        val optimal = choices.filter { it.remaining == distance - 1 }
        check(optimal.isNotEmpty())
        val preferred = ChallengeStrategy.entries.flatMap { strategy ->
            val ranked = choices.map { it to challengeRank(board, it.move, strategy) }
            val best = ranked.maxWith { a, b -> compareRanks(a.second, b.second) }.second
            ranked.filter { it.second == best }.map { it.first.move }
        }.toSet()
        val temptingLosses = choices.count { it.move in preferred && it.remaining == null }
        val decision = if (choices.size > 1 && choices.any { it.remaining != distance - 1 }) 1 else 0
        val temptation = if (temptingLosses > 0) 1 else 0
        val counter = if (optimal.none { it.move in preferred }) 1 else 0
        val children = graph.edges[index].filter { graph.distance[it] == distance - 1 }
        minDecisions[index] = decision + children.minOf { minDecisions[it] }
        minTemptations[index] = temptation + children.minOf { minTemptations[it] }
        maxTemptations[index] = temptation + children.maxOf { maxTemptations[it] }
        minCounter[index] = counter + children.minOf { minCounter[it] }
        if (index == 0) rootTemptingLosses = temptingLosses
    }
    return DecisionStructure(minDecisions[0], minTemptations[0], maxTemptations[0], minCounter[0], rootTemptingLosses)
}

internal val challengeOrder: Comparator<Pair<PackLevel, ChallengeAssessment>> =
    compareBy<Pair<PackLevel, ChallengeAssessment>> { it.second.easiestBotSuccesses.toDouble() / it.second.trials }
        .thenByDescending { it.second.minDecisions }
        .thenByDescending { it.second.minTemptations }
        .thenBy { it.first.solution.size }
        .thenBy { it.first.id }

internal fun challengeCsv(
    levels: List<PackLevel>, assessments: Map<String, ChallengeAssessment>,
    requirements: ChallengeRequirements = ChallengeRequirements(),
): String {
    val rows = mutableListOf(
        "id,world,capacity,moves,graph_status,states,min_decisions_all_shortest,min_temptations_all_shortest,max_temptations_on_shortest,min_countergreedy_all_shortest,opening_tempting_losses,trials," +
            ChallengeStrategy.entries.joinToString(",") { "success_${it.name.lowercase()}" } +
            ",eligible,rejection_reasons",
    )
    for (level in levels) {
        val a = assessments[level.id] ?: continue
        val reasons = requirements.rejectionReasons(a)
        rows += (listOf(
            level.id, level.worldId, level.board.tubes.map { it.capacity }.distinct().joinToString("/"),
            level.solution.size, if (a.complete) "COMPLETE" else "UNKNOWN", a.states,
            a.minDecisions ?: "", a.minTemptations ?: "", a.maxTemptations ?: "", a.minCounterGreedy ?: "",
            a.openingTemptingLosses ?: "", a.trials,
        ) + ChallengeStrategy.entries.map { a.successes.getValue(it) } +
            listOf(reasons.isEmpty(), reasons.joinToString(";"))).joinToString(",")
    }
    return rows.joinToString("\n", postfix = "\n")
}

internal fun challengeBudget(options: Map<String, String>) = ChallengeBudget(
    options.number("analysis-states", 50_000), options.longNumber("analysis-ms", 1_000),
    options.number("policy-trials", 100),
)

internal fun runChallengeReview(options: Map<String, String>) {
    val output = File(options["output"] ?: "build/puzzle-lab/challenge-review.csv")
    require(!output.exists()) { "Refusing to overwrite ${output.absolutePath}" }
    val pack = options["input"]?.let { LevelPackCodec.decode(File(it).readText()) } ?: BundledLevelPacks.difficultyStudy()
    val results = pack.levels.associate { it.id to assessChallenge(it, challengeBudget(options)) }
    output.parentFile?.let { require(it.isDirectory || it.mkdirs()) }
    output.writeText(challengeCsv(pack.levels, results))
    println(output.readText())
}
