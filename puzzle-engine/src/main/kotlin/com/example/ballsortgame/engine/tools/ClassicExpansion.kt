package com.example.ballsortgame.engine.tools

import com.example.ballsortgame.engine.*
import java.io.File
import java.util.Random
import kotlin.math.abs
import kotlin.math.roundToInt

private class ClassicCandidate(val board: Board, val analysis: PuzzleAnalysis, val colors: Int, val spare: Int, val hiddenPercent: Int)

/**
 * Grows the campaign with plain sorting levels, so that they clearly outnumber the mode
 * levels, and writes the complete new pack.
 *
 * Per world it adds, by default, 24 calm levels (no position anywhere from which the level
 * is lost, simple play nearly always finishes, also without sight of buried stones), six
 * steadier ones (some choices matter) and two optional challenges. Four of the calm ones are
 * played with hidden colours. Every level of the previous pack is kept unchanged under its
 * ID; the new ones are woven into each world's run: calm ones by rising length through the
 * whole run, the steadier ones from the first third on, the challenges late.
 *
 * The measurements describe how the rules behave for simple strategies. They are not human
 * difficulty ratings.
 */
internal fun runClassicExpansion(options: Map<String, String>) {
    val output = File(options["output"] ?: "build/puzzle-lab/classic-expansion")
    require(!output.exists()) { "Refusing to overwrite ${output.absolutePath}; choose a fresh output directory" }
    val attempts = options.number("attempts", 1200)
    val seed = options.longNumber("seed", 160_000)
    val maxStates = options.number("analysis-states", 60_000)
    val trials = options.number("policy-trials", 100)
    val calmCount = options.number("calm", 24)
    val steadyCount = options.number("steady", 6)
    val peakCount = options.number("peaks", 2)
    val hiddenCount = options.number("hidden", 4)
    val base = BundledLevelPacks.playtestV5()
    val diversity = PuzzleDiversityIndex()
    base.levels.filter { it.mode == LevelMode.CLASSIC }.forEach { diversity.admit(it.board) }
    val capacityOf = mapOf("three" to 3, "four" to 4, "five" to 5)
    val rows = mutableListOf("id,world,position,role,hidden,colors,spare,moves,states,losing_states,min_decisions,open_percent,hidden_percent")
    val merged = ArrayList<PackLevel>()
    for (world in base.worlds) {
        val capacity = capacityOf.getValue(world.id)
        val random = Random(seed + capacity)
        val calm = ArrayList<ClassicCandidate>(); val steady = ArrayList<ClassicCandidate>(); val peaks = ArrayList<ClassicCandidate>()
        repeat(attempts) {
            // More colours than the first campaign used, so later levels have more to sort.
            val colors = when (capacity) { 3 -> 4 + random.nextInt(3); 4 -> 3 + random.nextInt(4); else -> 3 + random.nextInt(3) }
            val spare = if (colors >= 4 && random.nextInt(4) == 0) 1 else 2
            val board = ShuffleConstruction.candidate(PuzzleGenerator.equalCapacityGoal(colors, capacity, spare), random)
            if (board.isSolved) return@repeat
            val analysis = (PuzzleAnalyzer.analyze(board, maxStates, trials) as? AnalysisOutcome.Analyzed)?.analysis ?: return@repeat
            if (analysis.solution.size < colors * 2) return@repeat // Too little to do to be worth a place.
            when {
                analysis.losingStates == 0 && analysis.bestBotPercent >= 95 -> {
                    val hidden = requireNotNull(HiddenColors.bestBlindSuccesses(board, trials)) * 100 / trials
                    if (hidden >= 90) calm += ClassicCandidate(board, analysis, colors, spare, hidden)
                }
                analysis.tier == PuzzleTier.BALANCED && analysis.bestBotPercent >= 50 -> steady += ClassicCandidate(board, analysis, colors, spare, -1)
                analysis.tier == PuzzleTier.CHALLENGE -> peaks += ClassicCandidate(board, analysis, colors, spare, -1)
            }
        }
        println("${world.id}: ${calm.size} calm, ${steady.size} steady, ${peaks.size} challenge candidates of $attempts")

        /** [count] structurally distinct picks spread evenly over [sorted], walking on when one is too like another. */
        fun spread(sorted: List<ClassicCandidate>, count: Int): List<ClassicCandidate> {
            val taken = LinkedHashSet<ClassicCandidate>()
            for (step in 0 until count) {
                var at = if (count == 1) 0 else (step.toDouble() / (count - 1) * (sorted.size - 1)).roundToInt()
                while (at < sorted.size) {
                    val candidate = sorted[at++]
                    if (candidate !in taken && diversity.admit(candidate.board) == DiversityAdmission.ADDED) { taken += candidate; break }
                }
            }
            require(taken.size == count) { "${world.id}: only ${taken.size} of $count distinct levels found; raise --attempts" }
            return taken.toList()
        }
        // Calm levels from the longer three quarters of what was found; steadier ones from the gentler half of theirs.
        val calmSorted = calm.sortedBy { it.analysis.solution.size }
        val calmPicks = spread(calmSorted.drop(calmSorted.size / 4), calmCount).sortedBy { it.analysis.solution.size }
        val steadySorted = steady.sortedByDescending { it.analysis.bestBotPercent }
        val steadyPicks = spread(steadySorted.take((steadySorted.size * 6 / 10).coerceAtLeast(steadyCount)), steadyCount)
            .sortedByDescending { it.analysis.bestBotPercent }
        val peakPicks = spread(peaks.sortedByDescending { it.analysis.bestBotPercent }, peakCount)

        val total = calmCount + steadyCount + peakCount
        val slots = arrayOfNulls<Pair<ClassicCandidate, LevelRole>>(total)
        fun nearestFree(fraction: Double) = slots.indices.filter { slots[it] == null }
            .minBy { abs((it + 1.0) / (total + 1) - fraction) }
        peakPicks.forEachIndexed { index, pick -> slots[nearestFree(.62 + .26 * index / (peakCount - 1).coerceAtLeast(1))] = pick to LevelRole.CHALLENGE }
        steadyPicks.forEachIndexed { index, pick -> slots[nearestFree(.30 + .63 * index / (steadyCount - 1).coerceAtLeast(1))] = pick to LevelRole.REGULAR }
        val calmSlots = slots.indices.filter { slots[it] == null }
        calmPicks.forEachIndexed { index, pick -> slots[calmSlots[index]] = pick to LevelRole.RELAXED }
        // Hidden colours now and then: never among the first few, spread through the calm ones.
        val hiddenAt = (0 until hiddenCount).map { calmSlots[((it + 1.0) / (hiddenCount + 1) * calmCount).roundToInt().coerceIn(3, calmCount - 1)] }.toSet()

        val run = base.levels.filter { it.worldId == world.id }
        val after = run.indexOfLast { it.role == LevelRole.INTRODUCTION } + 1
        val before = Array(run.size) { ArrayList<PackLevel>() }
        slots.forEachIndexed { slot, entry ->
            val (pick, role) = requireNotNull(entry)
            val position = ((slot + 1.0) / (total + 1) * run.size).roundToInt().coerceIn(after, run.size - 1)
            val digest = java.security.MessageDigest.getInstance("SHA-256").digest(pick.board.toString().toByteArray(Charsets.UTF_8))
                .take(6).joinToString("") { "%02x".format(it.toInt() and 255) }
            val kind = when (role) { LevelRole.RELAXED -> "calm"; LevelRole.REGULAR -> "steady"; else -> "peak" }
            val id = "${world.id}-$kind-$digest"
            val hidden = slot in hiddenAt
            before[position] += PackLevel(id, world.id, role, null, true, pick.board, pick.analysis.solution,
                if (hidden) LevelRules.CLASSIC.copy(hidden = true) else LevelRules.CLASSIC)
            rows += listOf(id, world.id, position, role, hidden, pick.colors, pick.spare, pick.analysis.solution.size, pick.analysis.states,
                pick.analysis.losingStates, pick.analysis.minDecisions, pick.analysis.bestBotPercent, pick.hiddenPercent).joinToString(",")
        }
        val woven = ArrayList<PackLevel>()
        run.forEachIndexed { index, level -> woven += before[index]; woven += level }
        // A challenge is a breather apart from the next: move a new one earlier until no three stand together.
        var guard = 0
        while (true) {
            val clash = woven.windowed(3).indexOfFirst { w -> w.all { it.role == LevelRole.CHALLENGE } }
            if (clash < 0) break
            require(guard++ < 50) { "${world.id}: could not separate the challenges" }
            val moving = (clash..clash + 2).first { woven[it].id.contains("-peak-") }
            val level = woven.removeAt(moving)
            woven.add((moving - 3).coerceAtLeast(9), level)
        }
        require(woven.first().role == LevelRole.INTRODUCTION && woven.last().role == LevelRole.CHALLENGE)
        require(woven.take(8).none { it.role == LevelRole.CHALLENGE })
        merged += woven
    }
    require(output.mkdirs())
    val pack = LevelPack(base.worlds, merged)
    File(output, "playtest-v6.txt").writeText(LevelPackCodec.encode(pack))
    File(output, "added.csv").writeText(rows.joinToString("\n", postfix = "\n"))
    File(output, "parameters.txt").writeText("seed=$seed\nattempts_per_world=$attempts\nanalysis_states=$maxStates\nbot_trials=$trials\n" +
        "calm=$calmCount steady=$steadyCount peaks=$peakCount hidden=$hiddenCount per world\n")
    val classic = pack.levels.count { it.mode == LevelMode.CLASSIC }
    println("${pack.levels.size} levels (${classic} classic, ${pack.levels.size - classic} mode) in ${output.absolutePath}")
}
