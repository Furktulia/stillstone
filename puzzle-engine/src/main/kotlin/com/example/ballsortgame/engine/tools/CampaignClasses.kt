package com.example.ballsortgame.engine.tools

import com.example.ballsortgame.engine.*
import java.io.File
import java.util.Random
import kotlin.math.roundToInt
import kotlin.math.sqrt

private class Entry(val level: PackLevel, val fits: LevelRole?, val measure: LevelMeasure?, val score: Double) {
    val traits: Set<String> = buildSet {
        if (level.mode != LevelMode.CLASSIC) add(level.mode.name)
        if (level.rules.hidden) add("HIDDEN")
    }
    val plain: Boolean get() = traits.isEmpty()
    val decisions: Int get() = measure?.decisions ?: level.solution.size / 3
}

private fun digest(text: String, bytes: Int) = java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
    .take(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }

/** Pattern levels cannot be lost and their graphs are too large to enumerate: only their length is measured. */
private fun patternScore(moves: Int) = ((moves - 6) / 40.0).coerceIn(0.0, 1.0)

private fun entryOf(level: PackLevel): Entry {
    if (level.rules.target != null) {
        val score = patternScore(level.solution.size)
        return Entry(level, if (score >= .15) LevelRole.REGULAR else LevelRole.INTRODUCTION, null, score)
    }
    val measure = measureLevel(level.board, level.rules)
    // A level the creator kept stays in its class; the trial board stays Midnight's last level.
    LevelQuality.KEPT_BY_CREATOR[level.id]?.let { return Entry(level, it, measure, 2.0) }
    val fits = measure?.let { classify(it, level.rules.hidden) }
    return Entry(level, fits, measure, measure?.let { 1 - it.success } ?: .5)
}

/**
 * Rebuilds the campaign into the classes agreed with the creator (2026-11-15), see [LevelQuality]:
 * introductory levels open every world and, in the first world, introduce each mode; relaxed
 * levels may hold shallow dead ends; no level opens with a finished tube; and at least 30% of
 * every level's moves are real choices.
 *
 * Every level of playtest-v7 is measured. It stays, under its ID, when it passes and fits a
 * class; plain levels that can never be lost stay only as introductions. The gaps are filled
 * with new levels measured the same way, so each world keeps its length.
 */
internal fun runCampaignClasses(options: Map<String, String>) {
    val output = File(options["output"] ?: "build/puzzle-lab/campaign-classes")
    require(!output.exists()) { "Refusing to overwrite ${output.absolutePath}; choose a fresh output directory" }
    val seed = options.longNumber("seed", 400_000)
    val attempts = options.number("attempts", 3000)
    val modeAttempts = options.number("classic-attempts", 1500)
    val base = BundledLevelPacks.playtestV7()
    val usedIds = (listOf(BundledLevelPacks.playtestV5(), BundledLevelPacks.playtestV6(), base).flatMap { p -> p.levels.map { it.id } }).toMutableSet()
    val capacityOf = mapOf("three" to 3, "four" to 4, "five" to 5)
    val rows = mutableListOf("world,position,id,mode,role,new,moves,decisions,losing,trap_depth,success")
    val result = ArrayList<PackLevel>()
    for ((worldIndex, world) in base.worlds.withIndex()) {
        val first = worldIndex == 0
        val capacity = capacityOf.getValue(world.id)
        val old = base.levels.filter { it.worldId == world.id }
        val n = old.size
        val created = HashSet<String>()
        fun newId(slug: String, board: Board, rules: LevelRules): String {
            val id = "${world.id}-$slug-${digest(board.toString() + rules.encode(), 6)}"
            require(usedIds.add(id)) { "ID collision: $id" }
            created += id
            return id
        }

        val measured = old.parallelStream().map(::entryOf).toList()
        val tutorials = measured.filter { it.level.role == LevelRole.INTRODUCTION }
        val sound = measured.filter { it !in tutorials && it.fits != null && LevelQuality.structuralFlaws(it.level.board, it.level.rules).isEmpty() }
        val refused = measured.filter { it !in tutorials && it !in sound }
        println("${world.id}: ${measured.size} measured, ${refused.size} refused: ${refused.joinToString { it.level.id }}")

        val diversity = PuzzleDiversityIndex()
        sound.filter { it.plain }.forEach { diversity.admit(it.level.board) }
        val colorRange = if (capacity == 5) 4..5 else 4..6
        val pool = plainCandidates(capacity, colorRange, attempts, seed + capacity * 1_000_000L)
        println("${world.id}: plain candidates ${pool.groupingBy { it.fits }.eachCount()}")
        fun plainEntries(fits: LevelRole, count: Int, slug: String, order: Comparator<PlainCandidate>): List<Entry> {
            val sorted = pool.filter { it.fits == fits }.sortedWith(order)
            val taken = ArrayList<Entry>()
            // Spread picks over the whole sorted list so the new levels are not all alike.
            for (step in 0 until count) {
                var at = if (count == 1) sorted.size / 2 else (step.toDouble() / (count - 1) * (sorted.size - 1)).roundToInt()
                while (at < sorted.size) {
                    val c = sorted[at++]
                    if (taken.any { it.level.board == c.board } || diversity.admit(c.board) != DiversityAdmission.ADDED) continue
                    val shortest = requireNotNull(PuzzleAnalyzer.analyze(c.board, 200_000, 1) as? AnalysisOutcome.Analyzed).analysis.solution
                    check(shortest.size == c.measure.moves) { "Analysis and graph disagree" }
                    val level = PackLevel(newId(slug, c.board, LevelRules.CLASSIC), world.id, fits, null, true, c.board, shortest)
                    taken += Entry(level, fits, c.measure, 1 - c.measure.success)
                    break
                }
            }
            require(taken.size == count) { "${world.id}: only ${taken.size} of $count new $slug levels; raise --attempts" }
            return taken
        }

        // Mode levels that were refused are replaced by one of the same kind.
        fun modeEntries(kind: String, count: Int, accept: (Entry) -> Boolean): List<Entry> {
            if (count == 0) return emptyList()
            val found = (0 until modeAttempts).toList().parallelStream().map { attempt ->
                val random = Random(seed + capacity * 7_000_000L + kind.hashCode() * 31L + attempt)
                val (board, rules, _) = when (kind) {
                    "hidden" -> {
                        val colors = colorRange.first + random.nextInt(colorRange.last - colorRange.first + 1)
                        Triple(ShuffleConstruction.candidate(PuzzleGenerator.equalCapacityGoal(colors, capacity, 2), random), LevelRules.CLASSIC.copy(hidden = true), "")
                    }
                    "sealed" -> candidate(if (random.nextInt(3) == 0) "seal-two" else "seal-one", capacity, random)
                    else -> candidate(kind, capacity, random)
                } ?: return@map null
                if (rules.solved(board) || LevelQuality.structuralFlaws(board, rules).isNotEmpty()) return@map null
                rules.bridge?.let { link -> // A connected level must NEED its middle tube.
                    val without = Board(board.tubes.filterIndexed { index, _ -> index != link.shared })
                    if (PuzzleSolver().solve(without, SearchOptions(SearchLimits(150_000, 150_000, 150_000, 5_000))) !is SolveResult.Unsolvable) return@map null
                }
                val moves = (RuledSolver.solve(rules, rules.start(board), 300_000) as? RuledSolveResult.Solved)?.moves ?: return@map null
                if (moves.size < 6) return@map null
                val level = PackLevel("candidate", world.id, LevelRole.RELAXED, null, true, board, moves, rules)
                entryOf(level).takeIf { it.fits != null && (it.measure == null || it.measure.moves == moves.size) && accept(it) }
            }.toList().filterNotNull().distinctBy { it.level.board }.sortedBy { it.score }
            require(found.size >= count) { "${world.id}: only ${found.size} of $count new $kind levels" }
            return List(count) { found[((it + .5) / count * found.size).toInt()] }.map { e ->
                val slug = if (kind == "hidden") "hidden" else e.level.mode.slug
                val level = e.level.copy(id = newId(slug, e.level.board, e.level.rules), role = requireNotNull(e.fits))
                Entry(level, e.fits, e.measure, e.score)
            }
        }
        val replacements = ArrayList<Entry>()
        refused.filter { !it.plain }.groupBy { if (it.traits == setOf("HIDDEN")) "hidden" else it.level.mode.slug }.forEach { (kind, list) ->
            val gentle = list.count { it.level.role == LevelRole.RELAXED }
            replacements += modeEntries(kind, gentle) { it.fits == LevelRole.INTRODUCTION || it.fits == LevelRole.RELAXED }
            replacements += modeEntries(kind, list.size - gentle) { it.fits == LevelRole.REGULAR || it.fits == LevelRole.CHALLENGE }
        }

        // The opening: tutorials, then plain levels that cannot be lost, gentlest first.
        val headCount = if (first) 3 else 4
        val gentlePlain = sound.filter { it.plain && it.fits == LevelRole.INTRODUCTION }.sortedBy { it.level.solution.size }
        val head = tutorials + (if (gentlePlain.size >= headCount) gentlePlain.take(headCount)
            else gentlePlain + plainEntries(LevelRole.INTRODUCTION, headCount - gentlePlain.size, "intro", compareBy { it.measure.moves }))

        // In the first world each mode is introduced by a level of its own that cannot be lost.
        val modes = (sound.filter { !it.plain } + replacements).toMutableList()
        val introductions = LinkedHashMap<String, Entry>()
        if (first) for (trait in listOf("MIXED", "HIDDEN", "SEALED", "CONNECTED", "PATTERN")) {
            val gentle = modes.filter { it.traits == setOf(trait) && it.fits == LevelRole.INTRODUCTION }.minByOrNull { it.level.solution.size }
            introductions[trait] = gentle ?: run {
                val kind = if (trait == "HIDDEN") "hidden" else LevelMode.valueOf(trait).slug
                modeEntries(kind, 1) { it.fits == LevelRole.INTRODUCTION && it.level.solution.size <= 12 }.single()
            }
            modes.remove(introductions[trait])
        }
        // Elsewhere each mode is still first met through a gentle level. Where none of a mode's levels
        // measures gentle, its gentlest is swapped for a new one that does (v7 only labelled it so).
        if (!first) for (trait in listOf("MIXED", "HIDDEN", "SEALED", "CONNECTED", "PATTERN")) {
            val own = modes.filter { it.traits == setOf(trait) }
            if (own.isEmpty() || own.any { it.fits == LevelRole.INTRODUCTION || it.fits == LevelRole.RELAXED }) continue
            val kind = if (trait == "HIDDEN") "hidden" else LevelMode.valueOf(trait).slug
            val gentle = modeEntries(kind, 1) { it.fits == LevelRole.INTRODUCTION || it.fits == LevelRole.RELAXED }.single()
            val dropped = own.minBy { it.score }
            println("${world.id}: ${dropped.level.id} swapped for a gentle ${gentle.level.id}")
            modes.remove(dropped); modes += gentle
        }

        // Plain levels that stay outside the opening; those that can never be lost no longer have a place there.
        val plain = sound.filter { it.plain && it.fits != LevelRole.INTRODUCTION }.toMutableList()
        // Keep each world's share of regular and challenge levels; the rest are relaxed.
        fun count(entries: List<Entry>, role: LevelRole) = entries.count { it.fits == role }
        val wantChallenges = old.count { it.role == LevelRole.CHALLENGE }
        val wantRegulars = old.count { it.role == LevelRole.REGULAR }
        val pieces = modes + plain
        plain += plainEntries(LevelRole.CHALLENGE, (wantChallenges - count(pieces, LevelRole.CHALLENGE)).coerceAtLeast(0), "peak",
            compareByDescending { it.measure.decisions })
        plain += plainEntries(LevelRole.REGULAR, (wantRegulars - count(pieces, LevelRole.REGULAR)).coerceAtLeast(0), "steady",
            compareByDescending<PlainCandidate> { it.measure.success }.thenByDescending { it.measure.decisions })
        val relaxedNeeded = n - head.size - introductions.size - modes.size - plain.size
        require(relaxedNeeded >= 0) { "${world.id}: too many levels kept" }
        // Relaxed: the ones with the most real choices for their length first.
        plain += plainEntries(LevelRole.RELAXED, relaxedNeeded, "relaxed", compareByDescending { it.measure.choiceShare })

        // Placement, as in the v7 rebuild: challenges spread from a fifth of the way in, the hardest
        // last; regular levels more and more common; relaxed ones between, rising in real choices.
        val placed = arrayOfNulls<Entry>(n)
        head.forEachIndexed { i, e -> placed[i] = e }
        val meets = HashMap<String, Int>()
        val firsts = HashSet<Entry>()
        val fractions = if (first) listOf("MIXED" to .08, "HIDDEN" to .13, "SEALED" to .18, "CONNECTED" to .23, "PATTERN" to .28)
            else listOf("MIXED" to .07, "HIDDEN" to .13, "SEALED" to .19, "CONNECTED" to .26, "PATTERN" to .33)
        fractions.forEach { (trait, fraction) ->
            val meet = introductions[trait] ?: modes.filter { it.traits == setOf(trait) }.minWithOrNull(compareBy({ it.score }, { it.level.solution.size }))
                ?: return@forEach
            var at = maxOf(head.size, (fraction * n).roundToInt())
            while (placed[at] != null) at++
            placed[at] = meet; firsts += meet; meets[trait] = at
        }
        val rest = (modes + plain).filter { it !in firsts }
        fun roleOf(e: Entry): LevelRole = when (e.fits) {
            LevelRole.CHALLENGE -> LevelRole.CHALLENGE
            LevelRole.REGULAR -> LevelRole.REGULAR
            else -> LevelRole.RELAXED
        }
        val pools = rest.groupBy(::roleOf).mapValues { (role, list) ->
            (if (role == LevelRole.RELAXED) list.sortedWith(compareBy({ it.decisions }, { it.level.solution.size }))
                else list.sortedWith(compareBy({ it.score }, { it.level.solution.size }))).toMutableList()
        }
        val roles = arrayOfNulls<LevelRole>(n)
        fun free(from: Int): Int {
            val start = from.coerceIn(0, n - 1)
            for (at in start downTo 0) if (placed[at] == null && roles[at] == null) return at
            for (at in start + 1 until n) if (placed[at] == null && roles[at] == null) return at
            error("No free place left in ${world.id}")
        }
        val challenges = pools[LevelRole.CHALLENGE]?.size ?: 0
        for (k in 0 until challenges) roles[free((n * (.18 + .82 * (k + 1) / challenges)).roundToInt() - 1)] = LevelRole.CHALLENGE
        val regulars = pools[LevelRole.REGULAR]?.size ?: 0
        for (j in 0 until regulars) roles[free((n * sqrt((j + .5) / regulars)).roundToInt().coerceAtMost(n - 1))] = LevelRole.REGULAR
        var previous: Entry? = null
        var lastChallenge: LevelMode? = null
        for (at in 0 until n) {
            placed[at]?.let { previous = it; continue }
            fun ready(e: Entry) = e.traits.all { (meets[it] ?: -1) in 0 until at }
            val order = listOf(roles[at] ?: LevelRole.RELAXED, LevelRole.RELAXED, LevelRole.REGULAR, LevelRole.CHALLENGE)
            val choice = order.firstNotNullOfOrNull { role ->
                val ready = pools[role].orEmpty().filter(::ready)
                if (role == LevelRole.CHALLENGE) ready.firstOrNull { it.level.mode != lastChallenge } ?: ready.firstOrNull()
                else ready.take(5).let { few -> few.firstOrNull { it.level.mode != previous?.level?.mode || it.level.mode == LevelMode.CLASSIC } ?: few.firstOrNull() }
            } ?: pools.values.flatten().first()
            pools.values.forEach { it.remove(choice) }
            placed[at] = choice
            previous = choice
            if (roleOf(choice) == LevelRole.CHALLENGE) lastChallenge = choice.level.mode
        }
        val run = placed.mapIndexed { at, item ->
            val e = requireNotNull(item)
            val role = when {
                e in tutorials || e in head || e in introductions.values -> LevelRole.INTRODUCTION
                else -> roleOf(e)
            }
            val level = e.level.copy(role = role)
            rows += listOf(world.id, at + 1, level.id, level.mode.slug + if (level.rules.hidden) "+hidden" else "", role, level.id in created,
                level.solution.size, e.measure?.decisions ?: "", e.measure?.losingStates ?: "", e.measure?.trapDepth ?: "",
                e.measure?.let { "%.2f".format(it.success) } ?: "").joinToString(",")
            level
        }
        require(run.take(8).none { it.role == LevelRole.CHALLENGE })
        require(run.map { it.id }.distinct().size == n)
        result += run
        println("${world.id}: ${created.size} new; " + run.groupingBy { it.role }.eachCount())
    }
    require(output.mkdirs())
    val pack = LevelPack(base.worlds, result)
    File(output, "playtest-v8.txt").writeText(LevelPackCodec.encode(pack))
    File(output, "levels.csv").writeText(rows.joinToString("\n", postfix = "\n"))
    File(output, "parameters.txt").writeText("seed=$seed\nattempts=$attempts\nmode_attempts=$modeAttempts\n" +
        "min_choice_share=${LevelQuality.MIN_CHOICE_SHARE}\nmax_trap_depth=${LevelQuality.MAX_TRAP_DEPTH}\nmin_relaxed_success=${LevelQuality.MIN_RELAXED_SUCCESS}\n")
    println("${pack.levels.size} levels in ${output.absolutePath}")
}
