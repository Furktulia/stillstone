package com.example.ballsortgame.engine.tools

import com.example.ballsortgame.engine.*
import java.util.Random

/**
 * What a campaign level must be, by class (agreed with the creator, 2026-11-15):
 *
 * - INTRODUCTION: the first levels of every world, and in the first world the level that
 *   introduces each mode. Nothing can be lost: every reachable position can still be finished.
 * - RELAXED: there may be dead ends, but each is shallow: from any losing position a few Undos
 *   reach a position that can still be finished, and simple play still usually wins.
 * - REGULAR and CHALLENGE: measured as before (how often simple play fails).
 *
 * And for every level outside the tutorials:
 * - No tube is already finished when the level opens, under the level's own goal.
 * - At most one tube can be finished with a single move from the opening position.
 * - At least [MIN_CHOICE_SHARE] of the moves on even the easiest shortest route are real
 *   choices: positions where some legal move is slower or loses. A long level made of forced
 *   moves is busywork, not a puzzle.
 *
 * The measurements describe the rules and simple strategies. They are not human ratings.
 */
internal object LevelQuality {
    const val MIN_CHOICE_SHARE = 0.30
    /** The most Undos ever needed to leave a dead end in a relaxed level. */
    const val MAX_TRAP_DEPTH = 3
    const val MIN_RELAXED_SUCCESS = 0.85
    /** At least this many authored tutorial levels open the first world and are exempt from the choice rule. */
    val TUTORIALS = setOf("three-01", "three-02", "three-03")
    /**
     * Levels the creator chose to keep in a class their measurements do not give them. The
     * Midnight trial board: simple play wins 97%, but its worst dead end needs 4 Undos to leave.
     */
    val KEPT_BY_CREATOR = mapOf("five-peak-394154cb6402" to LevelRole.CHALLENGE)

    /** Stones of each colour on the board. */
    private fun totals(board: Board): Map<Int, Int> = board.tubes.flatMap { it.balls }.groupingBy { it }.eachCount()

    /** Tubes already finished under the level's own goal when it opens. */
    fun finishedTubes(board: Board, rules: LevelRules): Int {
        rules.target?.let { goal -> return board.tubes.indices.count { board.tubes[it].balls.isNotEmpty() && board.tubes[it] == goal.tubes[it] } }
        val totals = totals(board)
        return board.tubes.count { tube ->
            tube.isComplete && (!rules.gather || tube.balls.size == totals.getValue(tube.balls.first()))
        }
    }

    /** Tubes holding one colour that a single move from the opening position would finish. */
    fun freeFinishes(board: Board, rules: LevelRules): Int {
        if (rules.target != null) return 0
        val totals = totals(board)
        return board.tubes.indices.count { index ->
            val tube = board.tubes[index]
            if (tube.balls.isEmpty() || tube.runs != 1 || tube.isComplete) return@count false
            val color = tube.balls.first()
            val need = tube.capacity - tube.balls.size
            if (rules.gather && totals.getValue(color) != tube.capacity) return@count false
            board.tubes.indices.any { other ->
                other != index && board.tubes[other].top == color && board.tubes[other].topRun >= need &&
                    rules.transfer(rules.start(board), Move(other, index))?.first?.board?.tubes?.get(index)?.isComplete == true
            }
        }
    }

    fun structuralFlaws(board: Board, rules: LevelRules): List<String> = buildList {
        finishedTubes(board, rules).let { if (it > 0) add("$it finished tube(s) at the start") }
        freeFinishes(board, rules).let { if (it > 1) add("$it tubes finish in one move") }
    }
}

/** How a level behaves under its own rules, from its complete position graph. */
internal class LevelMeasure(
    val moves: Int,
    val states: Int,
    val losingStates: Int,
    /** Fewest Undos that leave the worst reachable dead end; 0 when nothing can be lost. */
    val trapDepth: Int,
    /** Minimum over every shortest route of positions where a legal move is slower or losing. */
    val decisions: Int,
    /** How often simple play finishes, 0..1. */
    val success: Double,
) {
    val choiceShare: Double get() = decisions.toDouble() / moves
    val cannotLose: Boolean get() = losingStates == 0
}

/** Shortest-route decision count and dead-end depth over any position graph whose root is 0. */
internal fun graphShape(edges: List<IntArray>, distance: IntArray): Pair<Int, Int> {
    val n = distance.size
    val decisions = IntArray(n)
    for (index in (0 until n).filter { distance[it] > 0 }.sortedBy { distance[it] }) {
        val d = distance[index]
        val next = edges[index]
        val decision = if (next.size > 1 && next.any { distance[it] != d - 1 }) 1 else 0
        decisions[index] = decision + next.filter { distance[it] == d - 1 }.minOf { decisions[it] }
    }
    // Dead ends: breadth-first from the moves that leave the winnable part of the graph.
    val depth = IntArray(n)
    val queue = ArrayDeque<Int>()
    for (from in 0 until n) if (distance[from] >= 0) for (to in edges[from]) {
        if (distance[to] < 0 && depth[to] == 0) { depth[to] = 1; queue.add(to) }
    }
    var deepest = 0
    while (queue.isNotEmpty()) {
        val at = queue.removeFirst()
        deepest = maxOf(deepest, depth[at])
        for (to in edges[at]) if (distance[to] < 0 && depth[to] == 0) { depth[to] = depth[at] + 1; queue.add(to) }
    }
    return decisions[0] to deepest
}

/**
 * Null for a level too large to measure, or a pattern level (nothing is lost there and the
 * arrangement graph is too large to enumerate).
 */
internal fun measureLevel(board: Board, rules: LevelRules, maxStates: Int = 200_000, trials: Int = 100): LevelMeasure? {
    if (rules.target != null) return null
    val plain = rules.copy(hidden = false).isClassic
    val edges: List<IntArray>
    val distance: IntArray
    var ruled: RuledGraph? = null
    if (plain) {
        // Tube order does not matter in plain sorting: positions that differ only by it are one.
        val graph = DecisionGraph.explore(board, maxStates, Long.MAX_VALUE) ?: return null
        edges = graph.edges.map { it.toIntArray() }; distance = graph.distance
    } else {
        val graph = RuledGraph.explore(rules, board, maxStates) ?: return null
        ruled = graph
        // Without seals or a crossing, pouring a whole tube into an empty one changes nothing.
        val neutral = rules.seals.isEmpty() && rules.bridge == null
        edges = graph.edges.mapIndexed { from, next ->
            if (!neutral) next else {
                val key = graph.positions[from].board.canonicalKey()
                next.filter { graph.positions[it].board.canonicalKey() != key }.toIntArray()
            }
        }
        distance = graph.distance
    }
    val moves = distance[0]
    if (moves <= 0) return null
    val (decisions, trapDepth) = graphShape(edges, distance)
    val success = when {
        rules.hidden && plain -> requireNotNull(HiddenColors.bestBlindSuccesses(board, trials)).toDouble() / trials
        plain -> (PuzzleAnalyzer.analyze(board, maxStates, trials) as? AnalysisOutcome.Analyzed)?.analysis?.bestBotPercent?.div(100.0) ?: return null
        else -> plainSuccess(requireNotNull(ruled), Random(51_000L), trials * 2)
    }
    return LevelMeasure(moves, distance.size, distance.count { it < 0 }, trapDepth, decisions, success)
}

/** The class a measured level fits, or null when it fits none and does not belong in the campaign. */
internal fun classify(measure: LevelMeasure, hidden: Boolean): LevelRole? {
    if (measure.choiceShare < LevelQuality.MIN_CHOICE_SHARE) return null
    val failure = 1 - measure.success
    return when {
        failure >= .80 -> LevelRole.CHALLENGE
        failure >= .15 -> LevelRole.REGULAR
        measure.cannotLose -> LevelRole.INTRODUCTION
        // Buried stones are part of the fun only where a wrong guess costs nothing.
        hidden -> null
        measure.trapDepth <= LevelQuality.MAX_TRAP_DEPTH && measure.success >= LevelQuality.MIN_RELAXED_SUCCESS -> LevelRole.RELAXED
        else -> null
    }
}

/** One line per level: its role, measurements, the class it fits and what is wrong with it. */
internal fun auditLevel(level: PackLevel, position: Int): List<String> {
    val tutorial = level.id in LevelQuality.TUTORIALS
    val flaws = LevelQuality.structuralFlaws(level.board, level.rules).toMutableList()
    val measure = measureLevel(level.board, level.rules)
    val fits = measure?.let { classify(it, level.rules.hidden) }
    if (measure != null && !tutorial && measure.choiceShare < LevelQuality.MIN_CHOICE_SHARE) flaws += "choices ${measure.decisions}/${measure.moves}"
    return listOf(level.worldId, "$position", level.id, level.mode.slug + if (level.rules.hidden) "+hidden" else "", "${level.role}",
        "${measure?.moves ?: level.solution.size}", "${measure?.states ?: ""}", "${measure?.losingStates ?: ""}", "${measure?.trapDepth ?: ""}",
        "${measure?.decisions ?: ""}", measure?.let { "%.2f".format(it.success) } ?: "", "${fits ?: if (measure == null) "unmeasured" else "none"}",
        flaws.joinToString("; "))
}

internal fun runCampaignAudit(options: Map<String, String>) {
    val pack = options["input"]?.let { LevelPackCodec.decode(java.io.File(it).readText()) } ?: BundledLevelPacks.playtest()
    val rows = mutableListOf("world,position,id,mode,role,moves,states,losing,trap_depth,decisions,success,fits,flaws")
    val levels = pack.worlds.flatMap { world -> pack.levels.filter { it.worldId == world.id }.mapIndexed { i, level -> level to i + 1 } }
    val lines = levels.parallelStream().map { (level, position) -> auditLevel(level, position).joinToString(",") }.toList()
    rows += lines
    val text = rows.joinToString("\n", postfix = "\n")
    options["output"]?.let { java.io.File(it).writeText(text) } ?: print(text)
}

internal class PlainCandidate(val board: Board, val colors: Int, val spare: Int, val measure: LevelMeasure, val fits: LevelRole?)

/** Shuffled plain boards that pass the structural rules, measured; attempt i is seeded by [seed] + i alone. */
internal fun plainCandidates(capacity: Int, colorRange: IntRange, attempts: Int, seed: Long, hidden: Boolean = false): List<PlainCandidate> =
    (0 until attempts).toList().parallelStream().map { attempt ->
        val random = Random(seed + attempt)
        val colors = colorRange.first + random.nextInt(colorRange.last - colorRange.first + 1)
        val spare = if (random.nextInt(3) == 0) 1 else 2
        val board = ShuffleConstruction.candidate(PuzzleGenerator.equalCapacityGoal(colors, capacity, spare), random)
        val rules = if (hidden) LevelRules.CLASSIC.copy(hidden = true) else LevelRules.CLASSIC
        if (board.isSolved || LevelQuality.structuralFlaws(board, rules).isNotEmpty()) return@map null
        val measure = measureLevel(board, rules, 60_000) ?: return@map null
        if (measure.moves < colors * 2) return@map null
        PlainCandidate(board, colors, spare, measure, classify(measure, hidden))
    }.toList().filterNotNull()

internal fun runClassSurvey(options: Map<String, String>) {
    val attempts = options.number("attempts", 600)
    for ((capacity, colors) in listOf(3 to 4..6, 4 to 4..6, 5 to 4..5)) {
        val found = plainCandidates(capacity, colors, attempts, options.longNumber("seed", 300_000) + capacity * 1_000_000L)
        val relaxed = found.filter { it.fits == LevelRole.RELAXED }
        println("capacity $capacity: ${found.size} measured of $attempts; " + found.groupingBy { it.fits }.eachCount())
        println("  relaxed moves ${relaxed.map { it.measure.moves }.sorted()}")
        println("  relaxed decisions ${relaxed.map { it.measure.decisions }.sorted()}")
        // Why the rest with dead ends were refused.
        val traps = found.filter { it.fits == null && !it.measure.cannotLose && it.measure.choiceShare >= LevelQuality.MIN_CHOICE_SHARE }
        println("  refused with dead ends: deep=${traps.count { it.measure.trapDepth > LevelQuality.MAX_TRAP_DEPTH }} " +
            "depths=${traps.groupingBy { it.measure.trapDepth }.eachCount().toSortedMap()} success=${traps.map { (it.measure.success * 100).toInt() }.sorted()}")
    }
}
