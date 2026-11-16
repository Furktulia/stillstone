package com.example.ballsortgame.engine.tools

import com.example.ballsortgame.engine.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The shipped campaign keeps the rules in [LevelQuality]; a pack that breaks one cannot ship. */
class CampaignAuditTest {
    private val pack = BundledLevelPacks.playtest()

    @Test fun measuresDuskFortyOneAsItWasPlayed() {
        // Playtest v7, Dusk 41: six colours, one of them already finished; 2 real choices in 12 moves.
        val board = LevelPackCodec.decode(
            "BALLSORT-PACK\t1\tRULES\t1\nWORLD\tthree\tDusk\tThree-slot tubes\n" +
                "LEVEL\tthree-calm-3e1a9a7ba60a\tthree\tRELAXED\t-\ttrue\t3:3,4,2|3:1,4,0|3:2,0,2|3:5,5,5|3:1,4,0|3:1,3,3|3:|3:\t" +
                "0>6,0>7,2>6,1>2,1>7,5>0,1>5,2>1,2>6,4>1,4>7,4>5\n",
        ).levels.single().board
        assertEquals(1, LevelQuality.finishedTubes(board, LevelRules.CLASSIC))
        val measure = requireNotNull(measureLevel(board, LevelRules.CLASSIC))
        assertEquals(12, measure.moves)
        assertEquals(2, measure.decisions)
        assertEquals(null, classify(measure, hidden = false))
    }

    @Test fun shippedLevelsFitTheirClass() {
        val problems = pack.levels.parallelStream().map { level ->
            if (level.id in LevelQuality.TUTORIALS) return@map null
            val flaws = LevelQuality.structuralFlaws(level.board, level.rules)
            if (flaws.isNotEmpty()) return@map "${level.id}: $flaws"
            if (level.rules.target != null) return@map null
            val measure = measureLevel(level.board, level.rules) ?: return@map "${level.id}: too large to measure"
            if (measure.choiceShare < LevelQuality.MIN_CHOICE_SHARE) return@map "${level.id}: choices ${measure.decisions}/${measure.moves}"
            LevelQuality.KEPT_BY_CREATOR[level.id]?.let { kept -> return@map if (level.role == kept) null else "${level.id}: kept as $kept" }
            val fits = classify(measure, level.rules.hidden) ?: return@map "${level.id}: fits no class " +
                "(choices ${measure.decisions}/${measure.moves}, depth ${measure.trapDepth}, success ${measure.success})"
            val twist = level.mode != LevelMode.CLASSIC || level.rules.hidden
            val ok = when (level.role) {
                LevelRole.INTRODUCTION -> fits == LevelRole.INTRODUCTION
                // A mode or hidden colours is the twist; such a level may also be one that cannot be lost.
                LevelRole.RELAXED -> fits == LevelRole.RELAXED || (twist && fits == LevelRole.INTRODUCTION)
                else -> fits == level.role
            }
            if (ok) null else "${level.id}: ${level.role} but measures as $fits"
        }.toList().filterNotNull()
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun firstWorldIntroducesEachModeWithALevelThatCannotBeLost() {
        val dusk = pack.levels.filter { it.worldId == pack.worlds.first().id }
        for (mode in listOf(LevelMode.MIXED, LevelMode.SEALED, LevelMode.CONNECTED, LevelMode.PATTERN)) {
            val meet = dusk.first { it.mode == mode }
            assertEquals("${meet.id} introduces $mode", LevelRole.INTRODUCTION, meet.role)
        }
        assertEquals(LevelRole.INTRODUCTION, dusk.first { it.rules.hidden }.role)
    }

    @Test fun everyWorldOpensWithIntroductions() {
        for (world in pack.worlds) {
            val run = pack.levels.filter { it.worldId == world.id }
            assertTrue(world.id, run.take(5).all { it.role == LevelRole.INTRODUCTION })
        }
    }
}
