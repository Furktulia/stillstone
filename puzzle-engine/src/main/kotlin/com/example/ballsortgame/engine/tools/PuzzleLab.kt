package com.example.ballsortgame.engine.tools

import com.example.ballsortgame.engine.*
import java.io.File

/**
 * Host-JVM lab. No Android SDK/device is used by the engine itself.
 * Generate actual level boards and certificates, not seeds that depend on future code.
 */
fun main(args: Array<String>) {
    try {
        val command = args.firstOrNull() ?: "help"
        if (command == "help" || command == "--help") {
            println("""
                Puzzle Lab
                replay-seed --code E1-B-1 --output build/puzzle-lab/replayed.txt
                generate --strategy shuffle --colors 4 --capacity 4 --empty 2 --count 5 --seed 42 --steps 60
                         --max-ms 5000 --max-states 400000 --weight 1 --attempts 30
                         --min-moves 0 --max-moves 1000 --require-optimal false --output levels
                benchmark --count 2 --seed 42 --max-ms 2000 --max-states 150000
                corpus --warmup 1 --repeats 3 --max-ms 1500 --output build/puzzle-lab/corpus.csv
                survey --strategy shuffle --count 100 --colors 4 --capacity 4 --empty 1 --attempts 1
                       --max-ms 250 --max-states 20000 --output build/puzzle-lab/survey.csv
                workshop --seed 20260 --per-profile 4 --attempts 30 --max-ms 1500 --max-states 150000
                         --output build/puzzle-lab/workshop
                pack --input build/puzzle-lab/workshop/pool.txt --selection build/puzzle-lab/workshop/selection.txt
                     --output build/puzzle-lab/selected.txt
                verify-pack --input build/puzzle-lab/selected.txt --max-ms 5000 --max-states 400000
                difficulty-study --count 100 --seed 90000 --max-ms 100 --max-states 30000
                                 --output build/puzzle-lab/difficulty-study
                refine-finales --count 48 --seed 40000 --output build/puzzle-lab/refined-finales
                review-challenges --output build/puzzle-lab/challenge-review.csv --policy-trials 100
                Options are name/value pairs. Generation emits JSON; --output writes one file per puzzle.
                Strategies: shuffle (solve before accepting) or reverse (construction certificate).
                Benchmark compares both candidate methods with exact and weighted search.
                Search limits are per evaluation. Generation also has finite attempt and walk limits.
            """.trimIndent())
            return
        }
        require(command in setOf("generate", "benchmark", "corpus", "survey", "workshop", "pack", "verify-pack", "difficulty-study", "review-challenges", "refine-finales", "replay-seed")) { "Unknown command: $command" }
        val options = parseOptions(args.drop(1))
        when (command) {
            "replay-seed" -> {
                val level = SeededPuzzle.create(requireNotNull(options["code"]) { "--code is required" })
                val pack = LevelPackCodec.encode(LevelPack(listOf(PackWorld("endless", "Endless", "Seed replay")), listOf(level)))
                val output = options["output"]?.let(::File)
                if (output == null) println(pack) else {
                    require(!output.exists()) { "Refusing to overwrite ${output.absolutePath}" }
                    output.parentFile?.let { require(it.isDirectory || it.mkdirs()) }
                    output.writeText(pack)
                    println(output.absolutePath)
                }
            }
            "generate" -> generate(options)
            "benchmark" -> benchmark(options)
            "corpus" -> runCorpus(options)
            "survey" -> runSurvey(options)
            "workshop" -> runWorkshop(options)
            "pack" -> packSelection(options)
            "verify-pack" -> verifyPack(options)
            "difficulty-study" -> runDifficultyStudy(options)
            "review-challenges" -> runChallengeReview(options)
            "refine-finales" -> runFinaleRefinement(options)
        }
    } catch (error: IllegalArgumentException) {
        System.err.println("Puzzle Lab: ${error.message}")
        kotlin.system.exitProcess(2)
    }
}

private val allowedOptions = setOf(
    "colors", "capacity", "empty", "count", "seed", "steps", "max-ms", "max-states",
    "weight", "attempts", "min-moves", "max-moves", "require-optimal", "output", "strategy", "warmup", "repeats",
    "code", "input", "selection", "per-profile", "graph-states", "graph-ms", "analysis-states", "analysis-ms", "policy-trials", "challenge-count",
)

private fun parseOptions(args: List<String>): Map<String, String> {
    require(args.size % 2 == 0) { "Every option requires a value" }
    val values = LinkedHashMap<String, String>()
    for (pair in args.chunked(2)) {
        require(pair[0].startsWith("--")) { "Expected --option, got ${pair[0]}" }
        val name = pair[0].removePrefix("--")
        require(name in allowedOptions) { "Unknown option: $name" }
        require(values.put(name, pair[1]) == null) { "Duplicate option: $name" }
    }
    return values
}

internal fun Map<String, String>.number(name: String, default: Int) = get(name)?.toInt() ?: default
internal fun Map<String, String>.longNumber(name: String, default: Long) = get(name)?.toLong() ?: default

internal fun limits(options: Map<String, String>): SearchLimits {
    val states = options.number("max-states", 400_000)
    require(states in 1..10_000_000) { "max-states must be between 1 and 10000000" }
    return SearchLimits(
        maxExpandedStates = states, maxStoredStates = states,
        maxFrontierEntries = states, maxMillis = options.longNumber("max-ms", 5_000),
    )
}

private fun generate(options: Map<String, String>) {
    val count = options.number("count", 1)
    require(count in 1..10000)
    val seed = options.longNumber("seed", 42)
    val output = options["output"]?.let { File(it) }
    if (output != null) require(output.isDirectory || output.mkdirs()) { "Cannot create output directory" }
    val diversity = PuzzleDiversityIndex()
    var failures = 0
    for (index in 0 until count) {
        val request = labRequest(options, Math.addExact(seed, index.toLong()))
        when (val result = PuzzleGenerator().generate(request, diversity)) {
            is GenerationResult.Generated -> {
                val puzzle = result.puzzle
                System.err.println("Seed ${request.seed}: ${result.diagnostics}")
                val json = PuzzleJson.encode(puzzle)
                if (output == null) println(json) else {
                    val file = File(output, "puzzle-${request.seed}.json")
                    require(!file.exists()) { "Refusing to overwrite ${file.absolutePath}" }
                    file.writeText(json + "\n")
                    println(file.absolutePath)
                }
            }
            is GenerationResult.Exhausted -> {
                failures++
                System.err.println("Seed ${request.seed}: ${result.reason}; ${result.diagnostics}")
            }
            is GenerationResult.Cancelled -> error("Generation cancelled")
        }
    }
    if (failures > 0) {
        System.err.println("$failures requested puzzles were not exported; no unverified substitutes were used")
        kotlin.system.exitProcess(1)
    }
}

private fun benchmark(options: Map<String, String>) {
    val count = options.number("count", 2)
    require(count in 1..100)
    val seed = options.longNumber("seed", 42)
    val limits = limits(options)
    val profiles = linkedMapOf(
        "3-colors-cap3" to PuzzleGenerator.equalCapacityGoal(3, 3, 2),
        "4-colors-cap4" to PuzzleGenerator.equalCapacityGoal(4, 4, 2),
        "6-colors-cap4" to PuzzleGenerator.equalCapacityGoal(6, 4, 2),
        "8-colors-cap4" to PuzzleGenerator.equalCapacityGoal(8, 4, 2),
        "5-colors-cap5" to PuzzleGenerator.equalCapacityGoal(5, 5, 2),
        "4-colors-cap6" to PuzzleGenerator.equalCapacityGoal(4, 6, 2),
        "mixed-2-3-5" to Board(listOf(
            Tube(2, List(2) { 0 }), Tube(3, List(3) { 1 }), Tube(5, List(5) { 2 }), Tube(2), Tube(3),
        )),
    )
    val report = StringBuilder()
    fun row(value: String) {
        println(value)
        report.append(value).append('\n')
    }
    row("profile,candidate,seed,algorithm,generation_ms,construction_moves,boundaries,empty_tubes,status,solution_moves,optimal,expanded,discovered,peak_frontier,search_ms")
    for ((name, goal) in profiles) for (index in 0 until count) {
        val currentSeed = Math.addExact(seed, index.toLong())
        val started = System.nanoTime()
        val generation = PuzzleGenerator().generate(GenerationRequest(
            goal, currentSeed, reverseSteps = 60, maxAttempts = 20,
            search = SearchOptions(limits.copy(maxExpandedStates = 0)),
        ))
        val generationMillis = (System.nanoTime() - started) / 1_000_000
        if (generation !is GenerationResult.Generated) {
            System.err.println("$name seed $currentSeed: no benchmark candidate: $generation")
            continue
        }
        val puzzle = generation.puzzle
        for ((kind, board, certified) in listOf(
            Triple("reverse", puzzle.board, true),
            Triple("shuffle", ShuffleConstruction.candidate(goal, java.util.Random(currentSeed)), false),
        )) {
        for ((algorithm, search) in listOf(
            "astar" to SearchOptions(limits),
            "weighted4" to SearchOptions(limits, heuristicWeight = 4),
            "astar-no-symmetry" to SearchOptions(limits, useTubeSymmetry = false),
        )) {
            val result = PuzzleSolver().solve(board, search)
            val solved = result as? SolveResult.Solved
            if (solved != null) check(PuzzleRules.verifiesSolution(board, solved.moves))
            if (certified) check(result !is SolveResult.Unsolvable) { "A certified candidate was declared unsolvable" }
            val status = when (result) {
                is SolveResult.Solved -> "SOLVED"
                is SolveResult.Unsolvable -> "UNSOLVABLE"
                is SolveResult.LimitReached -> "LIMIT_${result.reason}"
                is SolveResult.Cancelled -> "CANCELLED"
            }
            val stats = result.statistics
            row(listOf(
                name, kind, currentSeed, algorithm, if (certified) generationMillis else "",
                if (certified) puzzle.constructionMoves else "",
                PuzzleRules.boundaryCount(board), board.tubes.count { it.balls.isEmpty() },
                status, solved?.moves?.size ?: "", solved?.optimal ?: false,
                stats.expandedStates, stats.discoveredStates, stats.peakFrontierEntries, stats.elapsedMillis,
            ).joinToString(","))
        }
        }
    }
    options["output"]?.let {
        val file = File(it)
        require(!file.exists()) { "Refusing to overwrite ${file.absolutePath}" }
        file.parentFile?.let { parent -> require(parent.isDirectory || parent.mkdirs()) }
        file.writeText(report.toString())
    }
}

/** Export-only numeric schema. Import/version migration belongs to the later persistence milestone. */
object PuzzleJson {
    fun encode(puzzle: GeneratedPuzzle): String {
        require(PuzzleRules.verifiesSolution(puzzle.board, puzzle.solution)) { "Cannot export an invalid solution" }
        val tubes = puzzle.board.tubes.joinToString(",") {
            """{"capacity":${it.capacity},"balls":[${it.balls.joinToString(",")}]}"""
        }
        val moves = puzzle.solution.joinToString(",") {
            """{"source":${it.source},"destination":${it.destination}}"""
        }
        val m = puzzle.metrics
        val stats = puzzle.evaluation.statistics
        val diagnostics = diagnosticsJson(puzzle.diagnostics)
        val status = when (val evaluation = puzzle.evaluation) {
            is SolveResult.Solved -> "SOLVED"
            is SolveResult.LimitReached -> "LIMIT_${evaluation.reason}"
            is SolveResult.Unsolvable -> "UNSOLVABLE"
            is SolveResult.Cancelled -> "CANCELLED"
        }
        return """{"formatVersion":1,"rulesVersion":1,"generatorVersion":2,"strategy":"${puzzle.strategy}","seed":${puzzle.seed},"attempt":${puzzle.attempt},"tubes":[$tubes],"solution":[$moves],"constructionMoves":${puzzle.constructionMoves},"metrics":{"colorBoundaries":${m.colorBoundaries},"colorRuns":${m.colorRuns},"emptyTubes":${m.emptyTubes},"legalMoves":${m.legalMoves},"distinctSuccessors":${m.distinctSuccessors},"solutionMoves":${m.solutionMoves},"shortestSolutionLowerBound":${m.shortestSolutionLowerBound},"shortestSolutionProven":${m.shortestSolutionProven},"rearrangementMoves":${m.rearrangementMoves}},"diagnostics":$diagnostics,"evaluation":"$status","search":{"expandedStates":${stats.expandedStates},"discoveredStates":${stats.discoveredStates},"peakFrontierEntries":${stats.peakFrontierEntries},"elapsedMillis":${stats.elapsedMillis}}}"""
    }
}
