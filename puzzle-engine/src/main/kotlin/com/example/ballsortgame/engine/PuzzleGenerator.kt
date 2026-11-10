package com.example.ballsortgame.engine

import java.util.Collections
import java.util.Random

data class ReverseStep(val predecessor: Board, val forwardMove: Move)

/** Enumerates predecessors, never assuming an arbitrary reversed transfer is legal. */
object ReverseConstruction {
    fun predecessors(board: Board): List<ReverseStep> = buildList {
        val seen = HashSet<String>()
        for (source in board.tubes.indices) {
            val sourceAfter = board.tubes[source]
            if (sourceAfter.freeSpace == 0) continue
            for (destination in board.tubes.indices) {
                if (source == destination) continue
                val destinationAfter = board.tubes[destination]
                val color = destinationAfter.top ?: continue
                for (count in 1..minOf(sourceAfter.freeSpace, destinationAfter.topRun)) {
                    val tubes = board.tubes.toMutableList()
                    tubes[source] = Tube(sourceAfter.capacity, sourceAfter.balls + List(count) { color })
                    tubes[destination] = Tube(destinationAfter.capacity, destinationAfter.balls.dropLast(count))
                    val predecessor = Board(tubes)
                    val move = Move(source, destination)
                    // This check enforces both matching colors AND maximal-group transfer.
                    if (PuzzleRules.transfer(predecessor, move)?.board == board &&
                        seen.add(predecessor.canonicalKey())
                    ) add(ReverseStep(predecessor, move))
                }
            }
        }
    }
}

enum class GenerationStrategy { CERTIFIED_REVERSE, SHUFFLE_AND_SOLVE }

/** Preserves the goal template's occupancy profile and color quantities, not its solvability. */
object ShuffleConstruction {
    fun candidate(goal: Board, random: Random): Board {
        val pieces = goal.tubes.flatMap { it.balls }.toMutableList()
        Collections.shuffle(pieces, random)
        var offset = 0
        return Board(goal.tubes.map {
            val contents = pieces.subList(offset, offset + it.balls.size).toList()
            offset += it.balls.size
            Tube(it.capacity, contents)
        })
    }
}

data class GenerationRequest(
    val solvedBoard: Board,
    val seed: Long,
    val reverseSteps: Int = 40,
    val maxAttempts: Int = 30,
    val minBoundaries: Int = 2,
    val minEmptyTubes: Int = 1,
    /** Bounds on the shortest solution, NOT on the length of a construction walk. */
    val minSolutionMoves: Int = 0,
    val maxSolutionMoves: Int? = null,
    val requireOptimalSolution: Boolean = false,
    val search: SearchOptions = SearchOptions(),
    val strategy: GenerationStrategy = GenerationStrategy.CERTIFIED_REVERSE,
) {
    init {
        require(solvedBoard.isSolved) { "Construction must start at a valid goal" }
        require(reverseSteps > 0 && maxAttempts > 0)
        require(minBoundaries >= 0 && minSolutionMoves >= 0)
        require(minEmptyTubes in 0..solvedBoard.tubes.size)
        require(maxSolutionMoves == null || maxSolutionMoves >= minSolutionMoves)
    }
}

data class PuzzleMetrics(
    val colorBoundaries: Int,
    val colorRuns: Int,
    val emptyTubes: Int,
    val legalMoves: Int,
    val distinctSuccessors: Int,
    val solutionMoves: Int,
    val shortestSolutionLowerBound: Int,
    val shortestSolutionProven: Boolean,
    /** Moves on this certificate that do not remove a color boundary; not a human difficulty score. */
    val rearrangementMoves: Int,
)

data class GeneratedPuzzle(
    val board: Board,
    val solution: List<Move>,
    val seed: Long,
    val attempt: Int,
    val constructionMoves: Int,
    val metrics: PuzzleMetrics,
    val evaluation: SolveResult,
    val strategy: GenerationStrategy,
    val diagnostics: GenerationDiagnostics,
)

sealed interface GenerationResult {
    val diagnostics: GenerationDiagnostics
    data class Generated(val puzzle: GeneratedPuzzle) : GenerationResult {
        override val diagnostics: GenerationDiagnostics get() = puzzle.diagnostics
    }
    data class Exhausted(
        val attemptedCandidates: Int, val reason: String,
        override val diagnostics: GenerationDiagnostics,
    ) : GenerationResult
    data class Cancelled(
        val attemptedCandidates: Int, override val diagnostics: GenerationDiagnostics,
    ) : GenerationResult
}

class PuzzleGenerator(private val solver: PuzzleSolver = PuzzleSolver()) {
    fun generate(
        request: GenerationRequest,
        diversity: PuzzleDiversityIndex? = null,
        cancelled: () -> Boolean = { false },
    ): GenerationResult {
        val random = Random(request.seed)
        val seenCandidates = HashSet<String>()
        val rejections = java.util.EnumMap<RejectionReason, Int>(RejectionReason::class.java)
        var attempts = 0
        var evaluated = 0
        var expanded = 0L
        var searchMillis = 0L
        fun reject(reason: RejectionReason) { rejections[reason] = (rejections[reason] ?: 0) + 1 }
        fun diagnostics(accepted: Int = 0) = GenerationDiagnostics(
            attempts, evaluated, accepted, Collections.unmodifiableMap(java.util.EnumMap(rejections)),
            expanded, searchMillis,
        )
        fun isCancelled() = cancelled() || Thread.currentThread().isInterrupted
        fun cancellation(inProgress: Boolean): GenerationResult.Cancelled {
            if (inProgress) reject(RejectionReason.CANCELLED)
            return GenerationResult.Cancelled(attempts, diagnostics())
        }
        for (attempt in 1..request.maxAttempts) {
            if (isCancelled()) return cancellation(false)
            attempts++
            var board = request.solvedBoard
            var certificate: List<Move>? = emptyList()
            val visited = hashSetOf(board.canonicalKey())
            if (request.strategy == GenerationStrategy.SHUFFLE_AND_SOLVE) {
                board = ShuffleConstruction.candidate(request.solvedBoard, random)
                certificate = null
            }
            for (step in 0 until if (certificate == null) 0 else request.reverseSteps) {
                if (isCancelled()) return cancellation(true)
                val candidates = ReverseConstruction.predecessors(board).filter {
                    it.predecessor.canonicalKey() !in visited &&
                        it.predecessor.tubes.count { tube -> tube.balls.isEmpty() } >= request.minEmptyTubes
                }
                if (candidates.isEmpty()) break
                // Favor mixing without making every walk follow the same steepest path.
                val pool = if (random.nextDouble() < 0.65) {
                    val mostBoundaries = candidates.maxOf { PuzzleRules.boundaryCount(it.predecessor) }
                    candidates.filter { PuzzleRules.boundaryCount(it.predecessor) == mostBoundaries }
                } else candidates
                val chosen = pool[random.nextInt(pool.size)]
                board = chosen.predecessor
                certificate = listOf(chosen.forwardMove) + requireNotNull(certificate)
                visited.add(board.canonicalKey())
            }
            val structuralRejection = when {
                board.isSolved -> RejectionReason.ALREADY_SOLVED
                PuzzleRules.boundaryCount(board) < request.minBoundaries -> RejectionReason.TOO_FEW_BOUNDARIES
                board.tubes.count { it.balls.isEmpty() } < request.minEmptyTubes -> RejectionReason.TOO_FEW_EMPTY_TUBES
                !seenCandidates.add(board.canonicalKey()) -> RejectionReason.REPEATED_CANDIDATE
                else -> null
            }
            if (structuralRejection != null) {
                reject(structuralRejection)
                continue
            }
            val constructionMoves = certificate?.size ?: 0
            if (certificate != null) check(PuzzleRules.verifiesSolution(board, certificate)) {
                "Invalid reverse-construction certificate"
            }
            val evaluation = solver.solve(board, request.search, cancelled)
            evaluated++
            expanded += evaluation.statistics.expandedStates
            searchMillis += evaluation.statistics.elapsedMillis
            if (evaluation is SolveResult.Cancelled) return cancellation(true)
            if (certificate != null) check(evaluation !is SolveResult.Unsolvable) {
                "Solver rejected a replay-verified solvable board"
            }
            val solved = evaluation as? SolveResult.Solved
            // Shuffled candidates are never accepted on a timeout or an unsolvability result.
            if (certificate == null && solved == null) {
                reject(when (evaluation) {
                    is SolveResult.Unsolvable -> RejectionReason.PROVEN_UNSOLVABLE
                    is SolveResult.LimitReached -> when (evaluation.reason) {
                        LimitReason.TIME -> RejectionReason.SEARCH_TIME
                        LimitReason.EXPANSIONS -> RejectionReason.SEARCH_EXPANSIONS
                        LimitReason.STORED_STATES -> RejectionReason.SEARCH_STORED_STATES
                        LimitReason.FRONTIER -> RejectionReason.SEARCH_FRONTIER
                    }
                    else -> error("Unexpected evaluation outcome")
                })
                continue
            }
            val optimal = solved?.optimal == true
            if (request.requireOptimalSolution && !optimal) {
                reject(RejectionReason.OPTIMALITY_UNPROVEN)
                continue
            }
            val solution = if (solved != null && (certificate == null || solved.moves.size <= certificate.size)) {
                solved.moves
            } else requireNotNull(certificate)
            check(PuzzleRules.verifiesSolution(board, solution))
            val lowerBound = if (optimal) requireNotNull(solved).moves.size else PuzzleRules.lowerBound(board)
            if (lowerBound < request.minSolutionMoves) {
                reject(if (optimal) RejectionReason.BELOW_MINIMUM_MOVES else RejectionReason.MINIMUM_UNPROVEN)
                continue
            }
            if (request.maxSolutionMoves != null && solution.size > request.maxSolutionMoves) {
                reject(if (optimal) RejectionReason.ABOVE_MAXIMUM_MOVES else RejectionReason.MAXIMUM_UNPROVEN)
                continue
            }
            when (diversity?.admit(board)) {
                DiversityAdmission.DUPLICATE -> {
                    reject(RejectionReason.STRUCTURAL_DUPLICATE)
                    continue
                }
                DiversityAdmission.INCONCLUSIVE -> {
                    reject(RejectionReason.DIVERSITY_COMPARISON_LIMIT)
                    continue
                }
                else -> Unit
            }
            var cursor = board
            var rearrangements = 0
            for (move in solution) {
                val next = requireNotNull(PuzzleRules.transfer(cursor, move)).board
                if (PuzzleRules.boundaryCount(next) == PuzzleRules.boundaryCount(cursor)) rearrangements++
                cursor = next
            }
            val legal = PuzzleRules.legalMoves(board)
            return GenerationResult.Generated(
                GeneratedPuzzle(
                    board, solution.toList(), request.seed, attempt, constructionMoves,
                    PuzzleMetrics(
                        PuzzleRules.boundaryCount(board), board.tubes.sumOf { it.runs },
                        board.tubes.count { it.balls.isEmpty() }, legal.size,
                        legal.map { requireNotNull(PuzzleRules.transfer(board, it)).board.canonicalKey() }.toSet().size,
                        solution.size, lowerBound, optimal, rearrangements,
                    ),
                    evaluation, request.strategy, diagnostics(accepted = 1),
                ),
            )
        }
        return GenerationResult.Exhausted(
            request.maxAttempts, "No candidate met the requested constraints within the attempt budget", diagnostics(),
        )
    }

    companion object {
        fun equalCapacityGoal(colors: Int, capacity: Int, emptyTubes: Int): Board {
            require(colors > 0 && capacity > 0 && emptyTubes >= 0)
            return Board(
                List(colors) { color -> Tube(capacity, List(capacity) { color }) } +
                    List(emptyTubes) { Tube(capacity) },
            )
        }
    }
}
