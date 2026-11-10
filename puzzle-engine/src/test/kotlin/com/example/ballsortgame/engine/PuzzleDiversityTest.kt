package com.example.ballsortgame.engine

import java.util.Random
import org.junit.Assert.*
import org.junit.Test

class PuzzleDiversityTest {
    @Test fun recognizesTubeAndGlobalColorPermutations() {
        val first = Board(listOf(Tube(3, listOf(0, 1, 0)), Tube(2, listOf(1, 2)), Tube(3)))
        val second = Board(listOf(Tube(3), Tube(3, listOf(70, 9, 70)), Tube(2, listOf(9, 42))))
        assertEquals(StructuralMatch.EQUIVALENT, PuzzleStructure.compare(first, second).match)
        val index = PuzzleDiversityIndex()
        assertEquals(DiversityAdmission.ADDED, index.admit(first))
        assertEquals(DiversityAdmission.DUPLICATE, index.admit(second))
        assertEquals(1, index.size)
    }

    @Test fun equalLocalPatternsDoNotProveGlobalEquivalence() {
        fun board(vararg pieces: List<Int>) = Board(pieces.map { Tube(2, it) })
        val twoCycles = board(listOf(0, 1), listOf(1, 0), listOf(2, 3), listOf(3, 2))
        val fourCycle = board(listOf(0, 1), listOf(1, 2), listOf(2, 3), listOf(3, 0))
        assertEquals(PuzzleStructure.fingerprint(twoCycles), PuzzleStructure.fingerprint(fourCycle))
        assertEquals(StructuralMatch.DISTINCT, PuzzleStructure.compare(twoCycles, fourCycle).match)
    }

    @Test fun preservesTubeCapacityAndBottomToTopOrder() {
        val first = Board(listOf(Tube(3, listOf(0, 0, 1)), Tube(2, listOf(0, 1))))
        val reversed = Board(listOf(Tube(3, listOf(1, 0, 0)), Tube(2, listOf(0, 1))))
        val differentCapacity = Board(listOf(Tube(4, listOf(0, 0, 1)), Tube(2, listOf(0, 1))))
        assertEquals(StructuralMatch.DISTINCT, PuzzleStructure.compare(first, reversed).match)
        assertEquals(StructuralMatch.DISTINCT, PuzzleStructure.compare(first, differentCapacity).match)
    }

    @Test fun uncertaintyDoesNotPretendToProveDiversity() {
        val board = Board(listOf(Tube(3, listOf(0, 1)), Tube(3)))
        assertEquals(StructuralMatch.LIMIT_REACHED, PuzzleStructure.compare(board, board, 0).match)
        val index = PuzzleDiversityIndex(0)
        assertEquals(DiversityAdmission.ADDED, index.admit(board))
        assertEquals(DiversityAdmission.INCONCLUSIVE, index.admit(board))
        assertEquals(1, index.size)
        assertThrows(IllegalArgumentException::class.java) { PuzzleDiversityIndex(-1) }
    }

    @Test fun matchingAgreesWithIndependentBruteForceCanonicalization() {
        val random = Random(8234)
        fun randomBoard(): Board {
            val tubes = List(4) { Tube(2, List(2) { random.nextInt(3) }) }
            return Board(tubes)
        }
        repeat(160) {
            val first = randomBoard()
            val second = if (it % 2 == 0) {
                val names = listOf(70, 20, 900).shuffled(kotlin.random.Random(it))
                Board(first.tubes.reversed().map { tube -> Tube(tube.capacity, tube.balls.map { names[it] }) })
            } else randomBoard()
            val expected = bruteKey(first) == bruteKey(second)
            val result = PuzzleStructure.compare(first, second)
            assertNotEquals(StructuralMatch.LIMIT_REACHED, result.match)
            assertEquals("$first versus $second", expected, result.match == StructuralMatch.EQUIVALENT)
        }
    }

    /** Enumerates all tube permutations, independently assigning colors by first appearance. */
    private fun bruteKey(board: Board): String {
        fun permutations(values: List<Tube>): List<List<Tube>> =
            if (values.isEmpty()) listOf(emptyList()) else values.indices.flatMap { index ->
                permutations(values.filterIndexed { i, _ -> i != index }).map { listOf(values[index]) + it }
            }
        return permutations(board.tubes).minOf { tubes ->
            val colors = LinkedHashMap<Int, Int>()
            tubes.joinToString("|") { tube ->
                "${tube.capacity}:" + tube.balls.joinToString(",") {
                    colors.getOrPut(it) { colors.size }.toString()
                }
            }
        }
    }
}
