package com.example.ballsortgame.engine

import java.util.ArrayDeque
import org.junit.Assert.*
import org.junit.Test

class PuzzleSolverTest {
    private val solver = PuzzleSolver()
    private val generous = SearchLimits(100_000, 150_000, 150_000, 60_000)
    private fun options(limits: SearchLimits = generous) = SearchOptions(limits)

    @Test fun findsOptimalSolutionAndValidCertificate() {
        val board = Board(listOf(Tube(2, listOf(0, 1)), Tube(2, listOf(1, 0)), Tube(2)))
        val result = solver.solve(board, options()) as SolveResult.Solved
        assertEquals(3, result.moves.size)
        assertTrue(result.optimal)
        assertTrue(PuzzleRules.verifiesSolution(board, result.moves))
    }

    @Test fun recognizesSolvedAndBlockedBoards() {
        val solved = Board(listOf(Tube(2, listOf(0, 0)), Tube(2)))
        assertEquals(0, (solver.solve(solved) as SolveResult.Solved).moves.size)
        val blocked = Board(listOf(Tube(2, listOf(0, 1)), Tube(2, listOf(1, 0))))
        assertTrue(solver.solve(blocked, options()) is SolveResult.Unsolvable)
    }

    @Test fun legalMovesDoNotImplySolvability() {
        val board = Board(listOf(Tube(3, listOf(0, 1)), Tube(3)))
        assertTrue(PuzzleRules.legalMoves(board).isNotEmpty())
        assertTrue(solver.solve(board, options()) is SolveResult.Unsolvable)
    }

    @Test fun budgetsAndCancellationNeverClaimUnsolvability() {
        val board = Board(listOf(Tube(2, listOf(0, 1)), Tube(2, listOf(1, 0)), Tube(2)))
        val cases = listOf(
            generous.copy(maxExpandedStates = 0) to LimitReason.EXPANSIONS,
            generous.copy(maxStoredStates = 1) to LimitReason.STORED_STATES,
            generous.copy(maxFrontierEntries = 1) to LimitReason.FRONTIER,
            generous.copy(maxMillis = 0) to LimitReason.TIME,
        )
        for ((limits, reason) in cases) {
            assertEquals(reason, (solver.solve(board, options(limits)) as SolveResult.LimitReached).reason)
        }
        assertTrue(solver.solve(board, options()) { true } is SolveResult.Cancelled)
    }

    @Test fun weightedSearchDoesNotClaimOptimality() {
        val board = Board(listOf(Tube(2, listOf(0, 1)), Tube(2, listOf(1, 0)), Tube(2)))
        val result = solver.solve(board, SearchOptions(generous, heuristicWeight = 4)) as SolveResult.Solved
        assertFalse(result.optimal)
        assertTrue(PuzzleRules.verifiesSolution(board, result.moves))
    }

    @Test fun exhaustiveSmallBoardsAgreeWithIndependentBreadthFirstOracle() {
        var checked = 0
        for ((capacities, quantities) in listOf(
            listOf(2, 2, 2) to listOf(2, 2),
            listOf(2, 2, 2, 2) to listOf(2, 2, 2),
            listOf(1, 2, 3) to listOf(1, 2),
            listOf(2, 3, 2) to listOf(2, 3),
        )) {
            for (board in enumerateBoards(capacities, quantities)) {
                val distance = oracleDistance(board)
                for (symmetry in listOf(false, true)) {
                    val result = solver.solve(board, SearchOptions(generous, useTubeSymmetry = symmetry))
                    if (distance == null) {
                        assertTrue("Expected unsolvable: $board, got $result", result is SolveResult.Unsolvable)
                    } else {
                        assertTrue("Expected solution: $board, got $result", result is SolveResult.Solved)
                        val solved = result as SolveResult.Solved
                        assertEquals("$board", distance, solved.moves.size)
                        assertTrue(PuzzleRules.verifiesSolution(board, solved.moves))
                        assertTrue(PuzzleRules.lowerBound(board) <= distance)
                    }
                }
                val oracleNext = oracleSuccessors(board).toSet()
                val productionNext = PuzzleRules.legalMoves(board).map {
                    requireNotNull(PuzzleRules.transfer(board, it)).board
                }.toSet()
                assertEquals("Legal moves differ for $board", oracleNext, productionNext)
                for (next in productionNext) {
                    assertTrue(PuzzleRules.lowerBound(board) <= 1 + PuzzleRules.lowerBound(next))
                    assertEquals(
                        board.tubes.flatMap { it.balls }.sorted(),
                        next.tubes.flatMap { it.balls }.sorted(),
                    )
                }
                checked++
            }
        }
        println("Exhaustive differential verification: $checked boards; symmetry on/off; independent BFS oracle")
        assertTrue(checked > 100)
    }

    /**
     * Deliberately uses neither production moves, goal test, heuristic nor canonical keys.
     * Full ordered-state BFS is practical only for these tiny fixtures.
     */
    private fun oracleDistance(initial: Board): Int? {
        val queue = ArrayDeque<Pair<Board, Int>>()
        val seen = hashSetOf(initial)
        queue.add(initial to 0)
        while (queue.isNotEmpty()) {
            val (board, distance) = queue.removeFirst()
            if (board.tubes.all { tube ->
                tube.balls.isEmpty() || (tube.balls.size == tube.capacity && tube.balls.distinct().size == 1)
            }) return distance
            for (next in oracleSuccessors(board)) if (seen.add(next)) queue.add(next to distance + 1)
        }
        return null
    }

    private fun oracleSuccessors(board: Board): List<Board> = buildList {
        for (i in board.tubes.indices) for (j in board.tubes.indices) {
            if (i == j) continue
            val source = board.tubes[i]
            val target = board.tubes[j]
            if (source.balls.isEmpty() || target.balls.size == target.capacity) continue
            val color = source.balls.last()
            if (target.balls.isNotEmpty() && target.balls.last() != color) continue
            var run = 0
            var index = source.balls.lastIndex
            while (index >= 0 && source.balls[index] == color) { run++; index-- }
            val moved = minOf(run, target.capacity - target.balls.size)
            val tubes = board.tubes.toMutableList()
            tubes[i] = Tube(source.capacity, source.balls.subList(0, source.balls.size - moved))
            tubes[j] = Tube(target.capacity, target.balls + List(moved) { color })
            add(Board(tubes))
        }
    }

    private fun enumerateBoards(capacities: List<Int>, quantities: List<Int>): List<Board> {
        val boards = ArrayList<Board>()
        fun fillTube(index: Int, remaining: List<Int>, built: List<Tube>) {
            if (index == capacities.size) {
                if (remaining.all { it == 0 }) boards.add(Board(built))
                return
            }
            fun fillPieces(pieces: List<Int>, left: List<Int>) {
                fillTube(index + 1, left, built + Tube(capacities[index], pieces))
                if (pieces.size == capacities[index]) return
                for (color in left.indices) if (left[color] > 0) {
                    val rest = left.toMutableList()
                    rest[color]--
                    fillPieces(pieces + color, rest)
                }
            }
            fillPieces(emptyList(), remaining)
        }
        fillTube(0, quantities, emptyList())
        return boards
    }
}
