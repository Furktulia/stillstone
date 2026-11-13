package com.example.ballsortgame.engine

import org.junit.Assert.*
import org.junit.Test

class LevelRulesTest {
    private fun board(vararg tubes: Pair<Int, List<Int>>) = Board(tubes.map { Tube(it.first, it.second) })

    @Test fun classicRulesAreExactlyTheOrdinaryRules() {
        val start = board(3 to listOf(0, 1, 1), 3 to listOf(1, 0, 0), 3 to emptyList())
        val rules = LevelRules.CLASSIC
        assertTrue(rules.isClassic)
        assertEquals(LevelMode.CLASSIC, rules.mode(start))
        assertEquals(PuzzleRules.legalMoves(start), rules.legalMoves(rules.start(start)))
        for (move in PuzzleRules.legalMoves(start)) {
            val ordinary = requireNotNull(PuzzleRules.transfer(start, move))
            assertEquals(ordinary.board to ordinary.count, rules.transfer(rules.start(start), move)!!.let { it.first.board to it.second })
        }
        assertEquals("", rules.encode())
        assertEquals(rules, LevelRules.decode(""))
    }

    @Test fun aSealedTubeIsShutUntilEnoughTubesAreCompleteAndThenStaysOpen() {
        // Tube 3 is sealed with a stone inside; completing blue (0) opens it.
        val start = board(2 to listOf(1, 0), 2 to listOf(0), 2 to emptyList(), 2 to listOf(1))
        val rules = LevelRules(seals = listOf(Seal(3, 1)))
        assertEquals(LevelMode.SEALED, rules.mode(start))
        var position = rules.start(start)
        assertTrue(rules.sealed(3, position.open))
        assertNull("nothing leaves a sealed tube", rules.transfer(position, Move(3, 2)))
        assertNull("nothing enters a sealed tube", rules.transfer(position, Move(0, 3)))
        position = rules.transfer(position, Move(0, 1))!!.first // blue joins blue: tube 2 is complete
        assertFalse(rules.sealed(3, position.open))
        position = rules.transfer(position, Move(1, 2))!!.first // break the completed tube again
        assertFalse("an opened seal stays open", rules.sealed(3, position.open))
        assertEquals(position, rules.replay(start, listOf(Move(0, 1), Move(1, 2))))
        // A full one-colour sealed tube does not open itself.
        val selfish = board(1 to listOf(0), 2 to listOf(1), 2 to listOf(1))
        assertTrue(LevelRules(seals = listOf(Seal(0, 1))).let { it.sealed(0, it.start(selfish).open) })
    }

    @Test fun twoSealsOpenAtTheirOwnCounts() {
        val start = board(1 to listOf(0), 1 to listOf(1), 1 to emptyList(), 1 to listOf(2), 1 to listOf(3))
        val rules = LevelRules(seals = listOf(Seal(3, 2), Seal(4, 3)))
        // Two complete open tubes already: the first seal is open from the start, the second is not.
        val position = rules.start(start)
        assertFalse(rules.sealed(3, position.open))
        assertTrue(rules.sealed(4, position.open))
    }

    @Test fun shoresTradeOnlyThroughTheSharedTube() {
        val start = board(2 to listOf(0, 1), 2 to emptyList(), 2 to listOf(1, 0))
        val rules = LevelRules(bridge = Bridge(setOf(0), 1))
        assertEquals(LevelMode.CONNECTED, rules.mode(start))
        assertNull(rules.transfer(rules.start(start), Move(0, 2)))
        assertNotNull(rules.transfer(rules.start(start), Move(0, 1)))
        assertNotNull(rules.transfer(rules.start(start), Move(2, 1)))
        assertFalse(Move(0, 2) in rules.legalMoves(rules.start(start)))
    }

    @Test fun patternLevelsMoveOneStoneOntoAnythingAndFinishOnlyAtTheExactArrangement() {
        val start = board(2 to listOf(0, 0), 2 to listOf(1, 1), 2 to emptyList())
        val goal = board(2 to listOf(0, 1), 2 to listOf(1, 0), 2 to emptyList())
        val rules = LevelRules(target = goal, singleStone = true)
        assertEquals(LevelMode.PATTERN, rules.mode(start))
        assertFalse("sorted is not the goal here", rules.solved(start))
        val (after, count) = rules.transfer(rules.start(start), Move(0, 2))!!
        assertEquals(1, count)
        assertEquals(listOf(0), after.board.tubes[0].balls)
        assertNotNull("any colour may be covered", rules.transfer(after, Move(1, 2)))
        val solved = RuledSolver.solve(rules, rules.start(start)) as RuledSolveResult.Solved
        assertTrue(rules.verifiesSolution(start, solved.moves))
        // One stone steps aside, one crosses over, and the first comes back on top of it.
        assertEquals(3, solved.moves.size)
        assertThrows(IllegalArgumentException::class.java) { LevelRules(target = board(2 to listOf(0, 0))).check(start) }
    }

    @Test fun solverReportsImpossibleAndOversizedAndCancelledSeparately() {
        val stuck = board(2 to listOf(0, 1), 2 to listOf(1, 0))
        assertEquals(RuledSolveResult.Unsolvable, RuledSolver.solve(LevelRules.CLASSIC, RuledPosition(stuck)))
        val big = board(3 to listOf(0, 1, 2), 3 to listOf(1, 2, 0), 3 to listOf(2, 0, 1), 3 to emptyList(), 3 to emptyList())
        assertEquals(RuledSolveResult.TooLarge, RuledSolver.solve(LevelRules.CLASSIC, RuledPosition(big), maxStates = 5))
        assertTrue(RuledSolver.solve(LevelRules.CLASSIC, RuledPosition(big)) is RuledSolveResult.Solved)
        // A level whose every colour has a stone locked away can never open its seal.
        val locked = board(2 to listOf(0, 1), 2 to emptyList(), 2 to listOf(1, 0))
        assertEquals(RuledSolveResult.Unsolvable, RuledSolver.solve(LevelRules(listOf(Seal(2, 1))), RuledPosition(locked)))
    }

    @Test fun rulesSurviveTheLevelPackFormatAndOldEightFieldRecordsStillLoad() {
        val goal = board(2 to listOf(0, 1), 2 to listOf(1, 0), 2 to emptyList())
        val start = board(2 to listOf(0, 0), 2 to listOf(1, 1), 2 to emptyList())
        val pattern = LevelRules(target = goal, singleStone = true)
        val solution = (RuledSolver.solve(pattern, pattern.start(start)) as RuledSolveResult.Solved).moves
        val sealedStart = board(2 to listOf(1, 0), 2 to listOf(0), 2 to emptyList(), 2 to listOf(1))
        val seal = LevelRules(seals = listOf(Seal(3, 1)), bridge = Bridge(setOf(0, 1), 2))
        val sealedSolution = (RuledSolver.solve(seal, seal.start(sealedStart)) as RuledSolveResult.Solved).moves
        val pack = LevelPack(listOf(PackWorld("three", "Dusk", "Three-slot tubes")), listOf(
            PackLevel("classic", "three", LevelRole.RELAXED, null, true, board(1 to listOf(0), 2 to listOf(0), 3 to emptyList()), listOf(Move(0, 1))),
            PackLevel("pattern", "three", LevelRole.RELAXED, null, true, start, solution, pattern),
            PackLevel("sealed", "three", LevelRole.REGULAR, null, true, sealedStart, sealedSolution, seal),
        ))
        val text = LevelPackCodec.encode(pack)
        assertEquals(8, text.lines().first { it.startsWith("LEVEL\tclassic") }.split('\t').size)
        assertEquals(pack, LevelPackCodec.decode(text))
        assertEquals(listOf(LevelMode.MIXED, LevelMode.PATTERN, LevelMode.CONNECTED), LevelPackCodec.decode(text).levels.map { it.mode })
        // A certificate that is only valid under ordinary rules is refused for a ruled level.
        assertThrows(IllegalArgumentException::class.java) {
            PackLevel("wrong", "three", LevelRole.REGULAR, null, true, sealedStart, listOf(Move(3, 2)), seal)
        }
    }
}
