package com.example.ballsortgame.engine.tools

import com.example.ballsortgame.engine.*
import java.io.File
import java.util.Random

/**
 * Hand-sized sets of campaign levels for the non-classic modes: mixed tube sizes, sealed
 * tubes (one or two, with stones inside), connected boards and restoring a pattern.
 *
 * For each mode and tube size many random candidates are solved exactly under the level's
 * own rules, measured, and a short ladder is picked from them: straightforward first, then
 * easy and harder ones in turns, the hardest last. The measurements describe how the rules
 * behave for a plain player; they are not human difficulty ratings.
 */
internal class ModeMeasure(
    val board: Board, val rules: LevelRules, val solution: List<Move>, val states: Int,
    /** Share of reachable positions from which the level can no longer be finished. */
    val losingShare: Double,
    /** How often a plain player finishes: complete a colour, else join a match, never repeat, no Undo. */
    val plainSuccess: Double,
    val variant: String,
) {
    val hardness: Double get() = if (rules.target != null) solution.size / 40.0
        else 0.6 * (1 - plainSuccess) + 0.25 * minOf(1.0, losingShare * 4) + 0.15 * minOf(1.0, solution.size / 25.0)
}

/** Complete labelled state graph under [rules]; null when larger than [maxStates]. */
internal class RuledGraph private constructor(val positions: List<RuledPosition>, val edges: List<IntArray>, val distance: IntArray) {
    companion object {
        fun explore(rules: LevelRules, board: Board, maxStates: Int): RuledGraph? {
            val start = rules.start(board)
            val index = hashMapOf(start to 0)
            val positions = arrayListOf(start)
            val edges = ArrayList<IntArray>()
            var cursor = 0
            while (cursor < positions.size) {
                val position = positions[cursor++]
                if (rules.solved(position.board)) { edges.add(IntArray(0)); continue }
                val next = rules.legalMoves(position).map { move ->
                    val after = requireNotNull(rules.transfer(position, move)).first
                    index.getOrPut(after) { positions.add(after); positions.size - 1 }
                }
                if (positions.size > maxStates) return null
                edges.add(next.distinct().toIntArray())
            }
            val distance = IntArray(positions.size) { -1 }
            val reverse = Array(positions.size) { ArrayList<Int>() }
            edges.forEachIndexed { from, list -> list.forEach { reverse[it].add(from) } }
            val queue = ArrayDeque<Int>()
            positions.forEachIndexed { i, p -> if (rules.solved(p.board)) { distance[i] = 0; queue.add(i) } }
            while (queue.isNotEmpty()) {
                val at = queue.removeFirst()
                for (previous in reverse[at]) if (distance[previous] < 0) { distance[previous] = distance[at] + 1; queue.add(previous) }
            }
            return RuledGraph(positions, edges, distance)
        }
    }
}

/** Colours whose every stone sits in a full one-colour tube. Counts a colour once however many tubes hold it. */
private fun gathered(board: Board): Int {
    val loose = HashSet<Int>()
    val seen = HashSet<Int>()
    for (tube in board.tubes) for (color in tube.balls) { seen.add(color); if (!tube.isComplete) loose.add(color) }
    return seen.size - loose.size
}

internal fun plainSuccess(graph: RuledGraph, random: Random, trials: Int): Double {
    var wins = 0
    repeat(trials) {
        var at = 0
        val visited = hashSetOf(0)
        var steps = 0
        while (true) {
            if (graph.distance[at] == 0) { wins++; break }
            if (steps++ >= 300) break
            val board = graph.positions[at].board
            var best = -1
            val choices = ArrayList<Int>()
            for (next in graph.edges[at]) {
                if (next in visited) continue
                val after = graph.positions[next].board
                val destination = after.tubes.indices.first { after.tubes[it].balls.size > board.tubes[it].balls.size }
                val score = (gathered(after) - gathered(board)) * 4 + if (board.tubes[destination].balls.isNotEmpty()) 2 else 0
                if (score > best) { best = score; choices.clear() }
                if (score == best) choices.add(next)
            }
            if (choices.isEmpty()) break
            at = choices[random.nextInt(choices.size)]
            visited.add(at)
        }
    }
    return wins.toDouble() / trials
}

private fun deal(stones: List<Int>, fill: List<Int>, capacities: List<Int>, random: Random): Board {
    val shuffled = stones.toMutableList()
    PuzzleAnalyzer.shuffle(shuffled, random)
    var offset = 0
    return Board(capacities.mapIndexed { tube, capacity ->
        Tube(capacity, shuffled.subList(offset, offset + fill[tube]).toList()).also { offset += fill[tube] }
    })
}

private fun stones(colors: Int, each: Int) = (0 until colors).flatMap { color -> List(each) { color } }

/** One random candidate of a pool, or null when this draw does not fit the tube size. */
internal fun candidate(pool: String, capacity: Int, random: Random): Triple<Board, LevelRules, String>? {
    val drawn = draw(pool, capacity, random) ?: return null
    // A sealed tube must hold a real mix. One that is already a single colour (or a lone stone)
    // adds nothing: opening it changes nothing about how the level is played.
    if (drawn.second.seals.any { drawn.first.tubes[it.tube].balls.distinct().size < 2 }) return null
    return drawn
}

private fun draw(pool: String, capacity: Int, random: Random): Triple<Board, LevelRules, String>? {
    fun pick(vararg values: Int) = values[random.nextInt(values.size)]
    when (pool) {
        "mixed" -> {
            val colors = when (capacity) { 3 -> pick(4, 5); else -> pick(3, 4) }
            val shapes = when (capacity) {
                3 -> listOf(listOf(2, 1), listOf(2, 2), listOf(1, 1, 1), listOf(2, 1, 1))
                4 -> listOf(listOf(3, 1), listOf(2, 2), listOf(2, 1, 1), listOf(3, 2))
                else -> listOf(listOf(4, 1), listOf(3, 2), listOf(2, 2, 1), listOf(3, 1, 1), listOf(4, 2))
            }
            val spares = shapes[random.nextInt(shapes.size)]
            val capacities = List(colors) { capacity } + spares
            val board = deal(stones(colors, capacity), List(colors) { capacity } + spares.map { 0 }, capacities, random)
            return Triple(board, LevelRules(gather = true), "spares " + spares.joinToString("+"))
        }
        "seal-one" -> {
            val colors = when (capacity) { 3 -> pick(4, 5); 4 -> 4; else -> pick(3, 4) }
            return when (random.nextInt(4)) {
                0, 1 -> { // A full sealed tube and two open spares: gentle.
                    val capacities = List(colors + 2) { capacity }
                    val fill = List(colors - 1) { capacity } + listOf(0, 0, capacity)
                    Triple(deal(stones(colors, capacity), fill, capacities, random), LevelRules(listOf(Seal(colors + 1, 1))), "full seal, two spares")
                }
                2 -> { // Half the sealed tube filled, one spare.
                    val inside = capacity / 2
                    val capacities = List(colors + 2) { capacity }
                    val fill = List(colors - 1) { capacity } + listOf(capacity - inside, 0, inside)
                    Triple(deal(stones(colors, capacity), fill, capacities, random), LevelRules(listOf(Seal(colors + 1, 1))), "half seal, one spare")
                }
                else -> { // A full sealed tube and a single spare: tight, most draws are impossible.
                    val capacities = List(colors + 1) { capacity }
                    val fill = List(colors - 1) { capacity } + listOf(0, capacity)
                    Triple(deal(stones(colors, capacity), fill, capacities, random), LevelRules(listOf(Seal(colors, 1))), "full seal, one spare")
                }
            }
        }
        "seal-two" -> {
            val colors = when (capacity) { 3 -> 5; 4 -> pick(4, 5); else -> 4 }
            val half = random.nextBoolean()
            val inside = if (half) capacity / 2 else capacity
            val open = if (half) colors - 1 else colors - 2
            val rest = colors * capacity - open * capacity - 2 * inside
            val capacities = List(open + (if (rest > 0) 1 else 0) + 2 + 2) { capacity }
            val fill = List(open) { capacity } + (if (rest > 0) listOf(rest) else emptyList()) + listOf(0, 0, inside, inside)
            val first = capacities.size - 2
            return Triple(deal(stones(colors, capacity), fill, capacities, random),
                LevelRules(listOf(Seal(first, 1), Seal(first + 1, 2))), if (half) "two half seals" else "two full seals")
        }
        "connected" -> {
            // Little or no room of their own: the middle tube is the place to work AND the way
            // across, and it cannot be both at once. That is what makes these levels their own.
            val (left, right) = when (capacity) {
                3 -> listOf(2 to 2, 2 to 3, 3 to 2, 3 to 3)
                4 -> listOf(2 to 2, 2 to 3, 3 to 2)
                else -> listOf(2 to 2, 1 to 2, 2 to 1, 2 to 3)
            }.let { it[random.nextInt(it.size)] }
            val (leftSpare, rightSpare) = listOf(0 to 0, 0 to 0, 1 to 0, 0 to 1)[random.nextInt(4)]
            val colors = left + right
            val capacities = List(left + leftSpare + 1 + right + rightSpare) { capacity }
            val fill = List(left) { capacity } + List(leftSpare) { 0 } + listOf(0) + List(right) { capacity } + List(rightSpare) { 0 }
            val shared = left + leftSpare
            return Triple(deal(stones(colors, capacity), fill, capacities, random),
                LevelRules(bridge = Bridge((0 until shared).toSet(), shared)),
                "$left+$right, spares $leftSpare/$rightSpare")
        }
        "pattern" -> {
            val (tubes, colors, spares) = when (capacity) {
                3 -> listOf(Triple(2, 2, 1), Triple(3, 3, 1), Triple(3, 3, 1), Triple(3, 2, 1), Triple(3, 3, 2))
                4 -> listOf(Triple(2, 2, 1), Triple(2, 4, 1), Triple(3, 2, 1), Triple(3, 3, 1), Triple(3, 4, 1))
                else -> listOf(Triple(2, 2, 1), Triple(2, 5, 1), Triple(2, 3, 1), Triple(3, 5, 1))
            }.let { it[random.nextInt(it.size)] }
            val shift = random.nextInt(2)
            val goal = Board(List(tubes) { tube -> Tube(capacity, List(capacity) { slot -> (slot + tube * shift) % colors }) } +
                List(spares) { Tube(capacity) })
            val capacities = goal.tubes.map { it.capacity }
            val board = deal(goal.tubes.flatMap { it.balls }, goal.tubes.map { it.balls.size }, capacities, random)
            if (board == goal) return null
            return Triple(board, LevelRules(target = goal, singleStone = true), if (shift == 0) "stripes" else "diagonal")
        }
        else -> error("Unknown pool $pool")
    }
}

internal fun measure(board: Board, rules: LevelRules, variant: String, random: Random, maxStates: Int): ModeMeasure? {
    if (rules.solved(board)) return null
    if (rules.target != null) {
        // The arrangement graph is far too large to enumerate; nothing in it is ever lost, so only the distance matters.
        // Kept well inside what a phone can search for a hint from any position the player reaches.
        val solved = RuledSolver.solve(rules, rules.start(board), 40_000) as? RuledSolveResult.Solved ?: return null
        return ModeMeasure(board, rules, solved.moves, 0, 0.0, 1.0, variant)
    }
    val graph = RuledGraph.explore(rules, board, maxStates) ?: return null
    if (graph.distance[0] < 0) return null
    val solved = RuledSolver.solve(rules, rules.start(board), maxStates * 2) as? RuledSolveResult.Solved ?: return null
    check(solved.moves.size == graph.distance[0])
    return ModeMeasure(board, rules, solved.moves, graph.positions.size,
        graph.distance.count { it < 0 }.toDouble() / graph.positions.size, plainSuccess(graph, random, 60), variant)
}

internal fun runModeCampaign(options: Map<String, String>) {
    val output = File(options["output"] ?: "build/puzzle-lab/mode-campaign")
    require(!output.exists()) { "Refusing to overwrite ${output.absolutePath}; choose a fresh output directory" }
    val attempts = options.number("attempts", 500)
    val seed = options.longNumber("seed", 140_000)
    val maxStates = options.number("analysis-states", 40_000)
    val worldOf = mapOf(3 to "three", 4 to "four", 5 to "five")
    // (pool, how far up the pool's hardness order): easy, easy, a step up, easy again, harder, a breather, hardest.
    val ladders = mapOf(
        LevelMode.MIXED to listOf("mixed" to .05, "mixed" to .15, "mixed" to .45, "mixed" to .25, "mixed" to .68, "mixed" to .4, "mixed" to .93),
        LevelMode.SEALED to listOf("seal-one" to .05, "seal-one" to .2, "seal-one" to .5, "seal-two" to .2, "seal-one" to .75, "seal-two" to .6, "seal-one" to .95),
        LevelMode.CONNECTED to listOf("connected" to .05, "connected" to .15, "connected" to .45, "connected" to .25, "connected" to .68, "connected" to .4, "connected" to .93),
        LevelMode.PATTERN to listOf("pattern" to .05, "pattern" to .15, "pattern" to .45, "pattern" to .25, "pattern" to .68, "pattern" to .4, "pattern" to .93),
    )
    val levels = ArrayList<PackLevel>()
    val rows = mutableListOf("id,world,mode,step,variant,role,moves,states,dead_end_share,plain_success,hardness,tubes")
    val seen = HashSet<String>()
    for (capacity in 3..5) for ((mode, ladder) in ladders) {
        val pools = HashMap<String, List<ModeMeasure>>()
        for (pool in ladder.map { it.first }.distinct()) {
            val random = Random(seed + capacity * 1000L + pool.hashCode())
            val found = ArrayList<ModeMeasure>()
            repeat(attempts) {
                val (board, rules, variant) = candidate(pool, capacity, random) ?: return@repeat
                val measured = measure(board, rules, variant, random, maxStates) ?: return@repeat
                if (measured.solution.size < 4) return@repeat
                if (seen.add(board.canonicalKey() + "/" + rules.encode())) found.add(measured)
            }
            pools[pool] = found.sortedBy { it.hardness }
            println("capacity $capacity ${mode.slug} pool $pool: ${found.size} usable of $attempts")
        }
        // The largest tubes get one step fewer: their boards are the busiest on a phone.
        val steps = if (capacity == 5) ladder.filterIndexed { index, _ -> index != 5 } else ladder
        val taken = HashSet<ModeMeasure>()
        steps.forEachIndexed { step, (pool, level) ->
            val sorted = pools.getValue(pool).filter { it !in taken }
            if (sorted.isEmpty()) { println("  no candidate left for step ${step + 1}"); return@forEachIndexed }
            val chosen = sorted[(level * (sorted.size - 1)).toInt()]
            taken.add(chosen)
            val digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest((chosen.board.toString() + chosen.rules.encode()).toByteArray(Charsets.UTF_8))
                .take(5).joinToString("") { "%02x".format(it.toInt() and 255) }
            val id = "${worldOf.getValue(capacity)}-${mode.slug}-$digest"
            val role = when { level < .3 -> LevelRole.RELAXED; level < .7 -> LevelRole.REGULAR; else -> LevelRole.CHALLENGE }
            levels += PackLevel(id, worldOf.getValue(capacity), role, null, true, chosen.board, chosen.solution, chosen.rules)
            rows += listOf(id, worldOf.getValue(capacity), mode.slug, step + 1, chosen.variant, role, chosen.solution.size, chosen.states,
                "%.3f".format(chosen.losingShare), "%.2f".format(chosen.plainSuccess), "%.2f".format(chosen.hardness),
                chosen.board.tubes.size).joinToString(",")
        }
    }
    require(levels.isNotEmpty())
    require(output.mkdirs())
    val shipped = BundledLevelPacks.playtestV3()
    File(output, "levels.txt").writeText(LevelPackCodec.encode(LevelPack(shipped.worlds, levels)))
    File(output, "chosen.csv").writeText(rows.joinToString("\n", postfix = "\n"))
    File(output, "parameters.txt").writeText("seed=$seed\nattempts_per_pool=$attempts\nanalysis_states=$maxStates\n")
    println("${levels.size} levels in ${output.absolutePath}")
}
