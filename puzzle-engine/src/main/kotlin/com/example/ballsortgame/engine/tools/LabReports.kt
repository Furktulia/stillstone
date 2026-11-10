package com.example.ballsortgame.engine.tools

import com.example.ballsortgame.engine.*
import java.io.File
import kotlin.math.ceil

internal fun labRequest(options: Map<String, String>, seed: Long, attemptsDefault: Int = 30): GenerationRequest {
    val empty = options.number("empty", 2)
    val strategy = when (options["strategy"] ?: "shuffle") {
        "shuffle" -> GenerationStrategy.SHUFFLE_AND_SOLVE
        "reverse" -> GenerationStrategy.CERTIFIED_REVERSE
        else -> throw IllegalArgumentException("strategy must be shuffle or reverse")
    }
    return GenerationRequest(
        PuzzleGenerator.equalCapacityGoal(options.number("colors", 4), options.number("capacity", 4), empty),
        seed, reverseSteps = options.number("steps", 50), maxAttempts = options.number("attempts", attemptsDefault),
        minEmptyTubes = minOf(1, empty), minSolutionMoves = options.number("min-moves", 0),
        maxSolutionMoves = options["max-moves"]?.toInt(),
        requireOptimalSolution = options["require-optimal"]?.toBooleanStrict() ?: false,
        search = SearchOptions(limits(options), options.number("weight", 1)), strategy = strategy,
    )
}

internal fun diagnosticsJson(d: GenerationDiagnostics): String {
    val rejections = d.rejections.entries.joinToString(",") { "\"${it.key.name}\":${it.value}" }
    return """{"attempts":${d.attempts},"evaluatedCandidates":${d.evaluatedCandidates},"acceptedCandidates":${d.acceptedCandidates},"rejections":{$rejections},"expandedStates":${d.expandedStates},"searchMillis":${d.searchMillis}}"""
}

internal fun resultStatus(result: SolveResult): String = when (result) {
    is SolveResult.Solved -> "SOLVED"
    is SolveResult.Unsolvable -> "UNSOLVABLE"
    is SolveResult.LimitReached -> "LIMIT_${result.reason}"
    is SolveResult.Cancelled -> "CANCELLED"
}

/** Nearest-rank percentile; null for an empty sample, never substitutes zero for missing data. */
internal fun percentile(values: List<Long>, percent: Int): Long? {
    require(percent in 1..100)
    if (values.isEmpty()) return null
    return values.sorted()[ceil(values.size * percent / 100.0).toInt() - 1]
}

private class ReportOutput(options: Map<String, String>) {
    private val raw = options["output"]?.let(::File)
    private val summary = raw?.let { File(it.parentFile, it.nameWithoutExtension + ".summary.csv") }

    init {
        for (file in listOfNotNull(raw, summary)) {
            require(!file.exists()) { "Refusing to overwrite ${file.absolutePath}" }
        }
    }

    fun write(rows: List<String>, summaries: List<String>) {
        if (raw == null) {
            rows.forEach(::println)
        } else {
            raw.parentFile?.let { require(it.isDirectory || it.mkdirs()) }
            raw.writeText(rows.joinToString("\n", postfix = "\n"))
            requireNotNull(summary).writeText(summaries.joinToString("\n", postfix = "\n"))
            println(raw.absolutePath)
            println(summary.absolutePath)
        }
        summaries.forEach(System.err::println)
    }
}

private data class Observation(val fixture: CorpusCase, val algorithm: String, val result: SolveResult)

internal fun runCorpus(options: Map<String, String>) {
    val repeats = options.number("repeats", 3)
    val warmup = options.number("warmup", 1)
    require(repeats in 1..100 && warmup in 0..10)
    val output = ReportOutput(options)
    val limits = limits(options)
    val algorithms = linkedMapOf(
        "astar" to SearchOptions(limits),
        "weighted4" to SearchOptions(limits, heuristicWeight = 4),
        "astar-no-symmetry" to SearchOptions(limits, useTubeSymmetry = false),
    )
    fun evaluate(fixture: CorpusCase, search: SearchOptions): SolveResult {
        val result = PuzzleSolver().solve(fixture.board, search)
        when (result) {
            is SolveResult.Solved -> {
                check(fixture.solvable) { "False solution for ${fixture.id}" }
                check(PuzzleRules.verifiesSolution(fixture.board, result.moves))
                check(result.moves.size >= requireNotNull(fixture.shortestMoves))
                if (result.optimal) check(result.moves.size == fixture.shortestMoves) {
                    "Shortest-path regression in ${fixture.id}"
                }
            }
            is SolveResult.Unsolvable -> check(!fixture.solvable) { "False unsolvability for ${fixture.id}" }
            else -> Unit // A budget limit is an observation, not an incorrect classification.
        }
        return result
    }
    repeat(warmup) { for (fixture in PuzzleCorpus.cases) for (search in algorithms.values) evaluate(fixture, search) }
    val observations = ArrayList<Observation>()
    val rows = arrayListOf(
        "corpus_version,case,family,repeat,algorithm,expected,status,moves,optimal,expanded,discovered,peak_frontier,search_ms",
    )
    repeat(repeats) { iteration ->
        for (fixture in PuzzleCorpus.cases) for ((algorithm, search) in algorithms) {
            val result = evaluate(fixture, search)
            observations.add(Observation(fixture, algorithm, result))
            val solved = result as? SolveResult.Solved
            val stats = result.statistics
            rows.add(listOf(
                PuzzleCorpus.VERSION, fixture.id, fixture.family, iteration, algorithm,
                if (fixture.solvable) "SOLVABLE" else "UNSOLVABLE", resultStatus(result),
                solved?.moves?.size ?: "", solved?.optimal ?: false,
                stats.expandedStates, stats.discoveredStates, stats.peakFrontierEntries, stats.elapsedMillis,
            ).joinToString(","))
        }
    }
    val summaries = arrayListOf(
        "family,algorithm,runs,solved,proven_unsolvable,limited,cancelled,p50_ms_all,p95_ms_all,p50_ms_solved,p95_ms_solved",
    )
    for ((key, group) in observations.groupBy { it.fixture.family to it.algorithm }) {
        val solved = group.filter { it.result is SolveResult.Solved }
        summaries.add(listOf(
            key.first, key.second, group.size, solved.size,
            group.count { it.result is SolveResult.Unsolvable },
            group.count { it.result is SolveResult.LimitReached },
            group.count { it.result is SolveResult.Cancelled },
            percentile(group.map { it.result.statistics.elapsedMillis }, 50) ?: "",
            percentile(group.map { it.result.statistics.elapsedMillis }, 95) ?: "",
            percentile(solved.map { it.result.statistics.elapsedMillis }, 50) ?: "",
            percentile(solved.map { it.result.statistics.elapsedMillis }, 95) ?: "",
        ).joinToString(","))
    }
    output.write(rows, summaries)
}

internal fun runSurvey(options: Map<String, String>) {
    val count = options.number("count", 100)
    require(count in 1..10000)
    val seed = options.longNumber("seed", 42)
    val output = ReportOutput(options)
    val diversity = PuzzleDiversityIndex()
    val generator = PuzzleGenerator()
    val reasons = RejectionReason.entries
    val rows = arrayListOf(
        "seed,strategy,outcome,attempts,evaluated,accepted,expanded,search_ms,empty_tubes,solution_moves,shortest_proven," +
            reasons.joinToString(",") { it.name },
    )
    val totals = java.util.EnumMap<RejectionReason, Long>(RejectionReason::class.java)
    var attempts = 0L
    var evaluated = 0L
    var accepted = 0L
    var expanded = 0L
    val started = System.nanoTime()
    repeat(count) { index ->
        val currentSeed = Math.addExact(seed, index.toLong())
        val request = labRequest(options, currentSeed, attemptsDefault = 1)
        val result = generator.generate(request, diversity)
        val d = result.diagnostics
        val puzzle = (result as? GenerationResult.Generated)?.puzzle
        attempts += d.attempts
        evaluated += d.evaluatedCandidates
        accepted += d.acceptedCandidates
        expanded += d.expandedStates
        for ((reason, value) in d.rejections) totals[reason] = (totals[reason] ?: 0L) + value
        val outcome = when (result) {
            is GenerationResult.Generated -> "ACCEPTED"
            is GenerationResult.Exhausted -> "EXHAUSTED"
            is GenerationResult.Cancelled -> "CANCELLED"
        }
        rows.add((listOf(
            currentSeed, request.strategy, outcome, d.attempts, d.evaluatedCandidates,
            d.acceptedCandidates, d.expandedStates, d.searchMillis,
            puzzle?.metrics?.emptyTubes ?: "", puzzle?.solution?.size ?: "",
            puzzle?.metrics?.shortestSolutionProven ?: "",
        ) + reasons.map { d.rejections[it] ?: 0 }).joinToString(","))
    }
    check(attempts == accepted + totals.values.sum())
    val summaries = arrayListOf(
        "metric,value", "seeds,$count", "attempts,$attempts", "evaluated,$evaluated", "accepted,$accepted",
        "expanded,$expanded", "wall_ms,${(System.nanoTime() - started) / 1_000_000}",
    )
    summaries.addAll(reasons.map { "rejected_${it.name},${totals[it] ?: 0}" })
    output.write(rows, summaries)
}
