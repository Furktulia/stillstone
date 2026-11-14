package com.example.ballsortgame.engine.tools

import com.example.ballsortgame.engine.*
import java.io.File
import java.util.Random
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** A level with how often a simple player fails it (0 always finishes, 1 never) and what it asks of the player. */
private class Rated(val level: PackLevel, val score: Double) {
    val traits: Set<String> = buildSet {
        if (level.mode != LevelMode.CLASSIC) add(level.mode.name)
        if (level.rules.hidden) add("HIDDEN")
    }
}

private fun digest(text: String, bytes: Int) = java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
    .take(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }

/**
 * Rebuilds the campaign after the creator played it (2026-11-14). What was wrong, and what this does:
 *
 * - Roles were set by where a level stood in its generator's list, so some "challenges" were
 *   trivial. Every level is now MEASURED (how often simple play finishes it) and its role follows:
 *   relaxed, regular (some choices matter) or challenge.
 * - Each world's run is re-ordered: gentle at first, regular levels more and more common, and
 *   challenges spread from about a fifth of the way in, the hardest last.
 * - A mode is introduced alone by its gentlest level, one mode at a time; a level that combines
 *   two things (for example mixed sizes with hidden colours) never comes before both were met.
 * - Mixed sizes: every colour must end in one tube (rule `gather`), re-solved for that goal.
 * - Sealed tubes that held a single colour are replaced by ones holding a mix.
 * - Connected boards are all new, under the crossing rule and with little room on the shores.
 *   Each is proven impossible without parking stones in the middle tube.
 * - More regular plain levels, and two more challenges per world; the eight shortest of the
 *   recently added calm levels make room.
 *
 * Measurements describe simple strategies. They are not human difficulty ratings.
 */
internal fun runCampaignRebuild(options: Map<String, String>) {
    val output = File(options["output"] ?: "build/puzzle-lab/campaign-rebuild")
    require(!output.exists()) { "Refusing to overwrite ${output.absolutePath}; choose a fresh output directory" }
    val seed = options.longNumber("seed", 210_000)
    val modeAttempts = options.number("attempts", 1200)
    val classicAttempts = options.number("classic-attempts", 1500)
    val maxStates = options.number("analysis-states", 40_000)
    val steadyCount = options.number("steady", 10)
    val peakCount = options.number("peaks", 2)
    val base = BundledLevelPacks.playtestV6()
    val before = BundledLevelPacks.playtestV5().levels.map { it.id }.toSet()
    val capacityOf = mapOf("three" to 3, "four" to 4, "five" to 5)
    val rows = mutableListOf("world,position,id,mode,role,score,moves,traits")
    val result = ArrayList<PackLevel>()
    for (world in base.worlds) {
        val capacity = capacityOf.getValue(world.id)
        val random = Random(seed + capacity)
        val old = base.levels.filter { it.worldId == world.id }
        val keep = ArrayList<PackLevel>()

        // Plain sorting: all stay except the eight shortest of the recently added calm ones.
        val classic = old.filter { it.mode == LevelMode.CLASSIC }
        val shortest = classic.filter { "-calm-" in it.id && it.id !in before }.sortedBy { it.solution.size }.take(8).map { it.id }.toSet()
        keep += classic.filter { it.id !in shortest }
        keep += old.filter { it.mode == LevelMode.PATTERN }

        // Mixed sizes: the same boards, now finished only when each colour is in one tube.
        for (level in old.filter { it.mode == LevelMode.MIXED }) {
            val rules = level.rules.copy(gather = true)
            val solved = RuledSolver.solve(rules, rules.start(level.board), 300_000) as? RuledSolveResult.Solved
            if (solved == null || solved.moves.isEmpty()) { println("${level.id}: cannot be gathered, dropped"); continue }
            keep += PackLevel(level.id, level.worldId, level.role, null, true, level.board, solved.moves, rules)
        }

        fun pool(name: String, attempts: Int): List<ModeMeasure> {
            val seen = HashSet<String>()
            val found = ArrayList<ModeMeasure>()
            val poolRandom = Random(seed + capacity * 1000L + name.hashCode())
            repeat(attempts) {
                val (board, rules, variant) = candidate(name, capacity, poolRandom) ?: return@repeat
                // A connected level must NEED its middle tube. While nothing is ever parked there the
                // shores are simply one board, so: take the middle tube away, and if the rest can
                // still be sorted the level is plain sorting in disguise and is thrown out.
                rules.bridge?.let { link ->
                    val without = Board(board.tubes.filterIndexed { index, _ -> index != link.shared })
                    val plain = PuzzleSolver().solve(without, SearchOptions(SearchLimits(150_000, 150_000, 150_000, 5_000)))
                    if (plain !is SolveResult.Unsolvable) return@repeat
                }
                val measured = measure(board, rules, variant, poolRandom, maxStates) ?: return@repeat
                if (measured.solution.size >= 6 && seen.add(board.canonicalKey())) found.add(measured)
            }
            println("${world.id} $name: ${found.size} usable of $attempts")
            return found.sortedBy { it.hardness }
        }
        fun pack(measure: ModeMeasure, slug: String) = PackLevel("${world.id}-$slug-${digest(measure.board.toString() + measure.rules.encode(), 5)}",
            world.id, LevelRole.REGULAR, null, true, measure.board, measure.solution, measure.rules)
        fun spread(sorted: List<ModeMeasure>, fractions: List<Double>) = fractions.map { sorted[(it * (sorted.size - 1)).roundToInt()] }.distinct()

        // Sealed: keep the ones whose sealed tubes hold a mix; replace the rest.
        val sealed = old.filter { it.mode == LevelMode.SEALED }
        val mixedSeals = sealed.filter { level -> level.rules.seals.all { level.board.tubes[it.tube].balls.distinct().size >= 2 } }
        keep += mixedSeals
        val missing = sealed.size - mixedSeals.size
        if (missing > 0) {
            val candidates = (pool("seal-one", modeAttempts) + pool("seal-two", modeAttempts / 2)).sortedBy { it.hardness }
            keep += spread(candidates, List(missing) { (it + .5) / missing }).map { pack(it, "sealed") }
        }

        // Connected boards: all new.
        val wanted = old.count { it.mode == LevelMode.CONNECTED }
        keep += spread(pool("connected", modeAttempts * 2), // From the gentler part of what was found: the simple player used for measuring is poor at
            // parking stones, so it rates these harder than they play.
            List(wanted) { .02 + .5 * it / (wanted - 1) }).map { pack(it, "connected") }

        // More plain levels where choices matter, and two more challenges.
        val diversity = PuzzleDiversityIndex()
        keep.filter { it.mode == LevelMode.CLASSIC }.forEach { diversity.admit(it.board) }
        val steady = ArrayList<PuzzleAnalysis>(); val peaks = ArrayList<PuzzleAnalysis>()
        val boards = HashMap<PuzzleAnalysis, Board>()
        repeat(classicAttempts) {
            val colors = when (capacity) { 3 -> 4 + random.nextInt(3); 4 -> 4 + random.nextInt(3); else -> 4 + random.nextInt(2) }
            val spare = if (random.nextInt(3) == 0) 1 else 2
            val board = ShuffleConstruction.candidate(PuzzleGenerator.equalCapacityGoal(colors, capacity, spare), random)
            if (board.isSolved) return@repeat
            val analysis = (PuzzleAnalyzer.analyze(board, 60_000, 100) as? AnalysisOutcome.Analyzed)?.analysis ?: return@repeat
            if (analysis.solution.size < colors * 2 + 2) return@repeat
            boards[analysis] = board
            when (analysis.tier) {
                PuzzleTier.BALANCED -> steady += analysis
                PuzzleTier.CHALLENGE -> peaks += analysis
                else -> Unit
            }
        }
        println("${world.id} plain: ${steady.size} regular and ${peaks.size} challenge candidates of $classicAttempts")
        fun take(sorted: List<PuzzleAnalysis>, count: Int, slug: String) {
            var taken = 0
            for (step in 0 until count) {
                var at = if (count == 1) sorted.size / 2 else (step.toDouble() / (count - 1) * (sorted.size - 1)).roundToInt()
                while (at < sorted.size) {
                    val analysis = sorted[at++]
                    val board = boards.remove(analysis) ?: continue
                    if (diversity.admit(board) != DiversityAdmission.ADDED) continue
                    keep += PackLevel("${world.id}-$slug-${digest(board.toString(), 6)}", world.id, LevelRole.REGULAR, null, true, board, analysis.solution)
                    taken++
                    break
                }
            }
            if (taken < count) println("${world.id}: only $taken of $count $slug levels found")
        }
        take(steady.sortedByDescending { it.bestBotPercent }, steadyCount, "steady")
        take(peaks.sortedByDescending { it.solution.size }, peakCount, "peak")

        // Measure every level the same way: how often does simple play fail it?
        val rater = Random(seed + capacity + 77)
        val rated = keep.map { level ->
            val score = when {
                level.rules.target != null -> ((level.solution.size - 6) / 40.0).coerceIn(0.0, 1.0) // Nothing is ever lost; only length counts.
                level.rules.isClassic -> (PuzzleAnalyzer.analyze(level.board, 150_000, 100) as? AnalysisOutcome.Analyzed)
                    ?.analysis?.let { 1 - it.bestBotPercent / 100.0 } ?: .5
                else -> RuledGraph.explore(level.rules, level.board, 80_000)?.let { 1 - plainSuccess(it, rater, 200) } ?: .5
            }
            Rated(level, score)
        }
        fun roleOf(item: Rated) = when {
            item.level.role == LevelRole.INTRODUCTION -> LevelRole.INTRODUCTION
            item.score >= .80 -> LevelRole.CHALLENGE
            item.score >= .15 -> LevelRole.REGULAR
            else -> LevelRole.RELAXED
        }

        // Where each kind of level goes.
        val n = rated.size
        val placed = arrayOfNulls<Rated>(n)
        val roles = arrayOfNulls<LevelRole>(n)
        val introductions = rated.filter { it.level.role == LevelRole.INTRODUCTION }
        introductions.forEachIndexed { index, item -> placed[index] = item }
        // A mode is met alone, through its gentlest level, and the modes arrive one at a time.
        val meets = HashMap<String, Int>()
        val firsts = HashSet<Rated>()
        listOf("MIXED" to .07, "HIDDEN" to .13, "SEALED" to .19, "CONNECTED" to .26, "PATTERN" to .33).forEach { (trait, fraction) ->
            val first = rated.filter { it.traits == setOf(trait) && it !in introductions }.minWithOrNull(compareBy({ it.score }, { it.level.solution.size }))
                ?: return@forEach
            var at = maxOf(introductions.size + 1, (fraction * n).roundToInt())
            while (placed[at] != null) at++
            placed[at] = first; firsts += first; meets[trait] = at
        }
        val rest = rated.filter { it !in introductions && it !in firsts }
        val pools = rest.groupBy { roleOf(it) }.mapValues { (_, list) -> list.sortedWith(compareBy({ it.score }, { it.level.solution.size })).toMutableList() }
        fun free(from: Int): Int {
            val start = from.coerceIn(0, n - 1)
            for (at in start downTo 0) if (placed[at] == null && roles[at] == null) return at
            for (at in start + 1 until n) if (placed[at] == null && roles[at] == null) return at
            error("No free place left in ${world.id}")
        }
        val challenges = pools[LevelRole.CHALLENGE]?.size ?: 0
        for (k in 0 until challenges) roles[free((n * (.18 + .82 * (k + 1) / challenges)).roundToInt() - 1)] = LevelRole.CHALLENGE
        val regulars = pools[LevelRole.REGULAR]?.size ?: 0
        // Regular levels grow more common along the run.
        for (j in 0 until regulars) roles[free((n * sqrt((j + .5) / regulars)).roundToInt().coerceAtMost(n - 1))] = LevelRole.REGULAR
        var previous: Rated? = null
        var lastChallenge: LevelMode? = null
        for (at in 0 until n) {
            val fixed = placed[at]
            if (fixed != null) { previous = fixed; continue }
            fun ready(item: Rated) = item.traits.all { (meets[it] ?: -1) in 0 until at }
            val order = listOf(roles[at] ?: LevelRole.RELAXED, LevelRole.RELAXED, LevelRole.REGULAR, LevelRole.CHALLENGE)
            val choice = order.firstNotNullOfOrNull { role ->
                val ready = pools[role].orEmpty().filter(::ready).take(5)
                // Vary what the player is asked to do from one level to the next.
                // ...and never let one mode supply the hard levels several times running.
                if (role == LevelRole.CHALLENGE) pools[role].orEmpty().filter(::ready).let { all ->
                    all.firstOrNull { it.level.mode != lastChallenge } ?: all.firstOrNull()
                } else ready.firstOrNull { it.level.mode != previous?.level?.mode || it.level.mode == LevelMode.CLASSIC } ?: ready.firstOrNull()
            } ?: pools.values.flatten().first()
            pools.values.forEach { it.remove(choice) }
            placed[at] = choice
            previous = choice
            if (roleOf(choice) == LevelRole.CHALLENGE) lastChallenge = choice.level.mode
        }
        placed.forEachIndexed { at, item ->
            val chosen = requireNotNull(item)
            val role = if (chosen in firsts) LevelRole.RELAXED else roleOf(chosen)
            val level = chosen.level
            result += PackLevel(level.id, level.worldId, role, null, true, level.board, level.solution, level.rules)
            rows += listOf(world.id, at + 1, level.id, level.mode.slug, role, "%.2f".format(chosen.score), level.solution.size, chosen.traits.joinToString("+")).joinToString(",")
        }
    }
    require(output.mkdirs())
    val pack = LevelPack(base.worlds, result)
    File(output, "playtest-v7.txt").writeText(LevelPackCodec.encode(pack))
    File(output, "levels.csv").writeText(rows.joinToString("\n", postfix = "\n"))
    File(output, "parameters.txt").writeText("seed=$seed\nmode_attempts=$modeAttempts\nclassic_attempts=$classicAttempts\nanalysis_states=$maxStates\nsteady=$steadyCount peaks=$peakCount\n")
    println("${pack.levels.size} levels in ${output.absolutePath}")
}
