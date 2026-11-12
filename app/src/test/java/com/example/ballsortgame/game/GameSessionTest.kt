package com.example.ballsortgame.game

import com.example.ballsortgame.engine.Move
import org.junit.Assert.*
import org.junit.Test

class GameSessionTest {
    private val levels = LevelCatalog.levels

    @Test fun everyBundledPuzzleCanBePlayedAndUndoneThroughItsCertificate() {
        assertEquals(33, levels.size)
        for (level in levels) {
            val session = GameSession(levels)
            session.chooseLevel(level.id)
            for (move in level.solution) assertTrue(session.move(move))
            assertTrue(level.id, session.board.isSolved)
            assertTrue(level.id in session.progress.completed)
            assertFalse(session.move(Move(0, 1)))
            repeat(level.solution.size) { assertTrue(session.undo()) }
            assertEquals(level.initial, session.board)
            assertFalse(session.canUndo)
            assertTrue(level.id in session.progress.completed)
        }
    }

    @Test fun switchingPuzzlesRetainsBothArrangementsAndUndoHistory() {
        val session = GameSession(levels)
        assertTrue(session.move(levels[0].solution.first()))
        val first = session.board
        session.chooseLevel(levels[1].id)
        assertTrue(session.move(levels[1].solution.first()))
        val second = session.board
        session.chooseLevel(levels[0].id)
        assertEquals(first, session.board)
        session.chooseLevel(levels[1].id)
        assertEquals(second, session.board)
        assertTrue(session.undo())
        assertEquals(levels[1].initial, session.board)
    }

    @Test fun savedProgressRestoresActivePuzzleSettingsAndUndoHistory() {
        val session = GameSession(levels)
        session.chooseLevel(levels[5].id)
        levels[5].solution.take(3).forEach { assertTrue(session.move(it)) }
        session.settings(colorLabels = true, reducedMotion = true)
        val restored = GameSession(levels, ProgressCodec.decode(ProgressCodec.encode(session.progress)))
        assertEquals(session.board, restored.board)
        assertEquals(session.moves, restored.moves)
        assertEquals(5, restored.levelIndex)
        assertTrue(restored.progress.colorLabels && restored.progress.reducedMotion)
        assertTrue(restored.undo())
        assertNotEquals(session.board, restored.board)
    }

    @Test fun badHistoryOnlyResetsAffectedPuzzle() {
        val goodId = levels[1].id
        val snapshot = GameProgress(
            activeLevelId = levels[0].id,
            histories = mapOf(
                levels[0].id to listOf(Move(99, 0)),
                goodId to listOf(levels[1].solution.first()),
                "removed-level" to emptyList(),
            ),
            completed = setOf("removed-level", goodId),
        )
        val session = GameSession(levels, snapshot)
        assertTrue(session.repairedSave)
        assertEquals(levels[0].initial, session.board)
        assertFalse("removed-level" in session.progress.completed)
        session.chooseLevel(goodId)
        assertTrue(session.canUndo)
    }

    @Test fun restartOnlyClearsCurrentHistoryAndKeepsCompletion() {
        val session = GameSession(levels)
        session.move(levels[0].solution.first())
        session.chooseLevel(levels[1].id)
        session.move(levels[1].solution.first())
        session.restart()
        assertEquals(levels[1].initial, session.board)
        assertFalse(session.canUndo)
        assertTrue(levels[0].id in session.progress.completed)
        session.chooseLevel(levels[0].id)
        assertTrue(session.board.isSolved)
    }

    @Test fun progressOnKeptLevelsSurvivesTheCampaignRefreshAndRetiredLevelsAreDroppedSafely() {
        val old = com.example.ballsortgame.engine.BundledLevelPacks.playtestV2().levels.map {
            PlayableLevel(it.id, it.worldId, it.board, it.solution)
        }
        // A level that was kept: the arrangement, undo history and settings come back.
        val kept = "four-challenge-40015-6dc0590dc1e5"
        val previous = GameSession(old)
        previous.chooseLevel(kept)
        old.first { it.id == kept }.solution.take(4).forEach { assertTrue(previous.move(it)) }
        previous.settings(colorLabels = true, reducedMotion = true)
        val restored = GameSession(levels, ProgressCodec.decode(ProgressCodec.encode(previous.progress)))
        assertFalse(restored.repairedSave)
        assertEquals(kept, restored.level.id)
        assertEquals(previous.board, restored.board)
        assertEquals(previous.moves, restored.moves)
        assertTrue(restored.progress.colorLabels && restored.progress.reducedMotion)
        assertTrue(restored.undo())

        // A level that was replaced: its history is dropped, nothing else is lost, and play
        // starts from the first level instead of crashing on an unknown ID.
        val retired = GameSession(old)
        retired.chooseLevel("three-01")
        old.first { it.id == "three-01" }.solution.forEach { assertTrue(retired.move(it)) }
        retired.chooseLevel("four-04")
        old.first { it.id == "four-04" }.solution.take(3).forEach { assertTrue(retired.move(it)) }
        val repaired = GameSession(levels, ProgressCodec.decode(ProgressCodec.encode(retired.progress)))
        assertTrue(repaired.repairedSave)
        assertEquals(levels.first().id, repaired.level.id)
        assertTrue("three-01" in repaired.progress.completed)
        assertFalse(repaired.progress.histories.containsKey("four-04"))
    }

    @Test fun previousThirtyPuzzleSavesAndCompletionSurviveNewFinales() {
        val old = com.example.ballsortgame.engine.BundledLevelPacks.previousPlaytest().levels.map {
            PlayableLevel(it.id, it.worldId, it.board, it.solution)
        }
        val previous = GameSession(old)
        val moonlightTen = old.filter { it.worldId == "four" }.last()
        previous.chooseLevel(moonlightTen.id)
        moonlightTen.solution.forEach { assertTrue(previous.move(it)) }
        val midnightTen = old.filter { it.worldId == "five" }.last()
        previous.chooseLevel(midnightTen.id)
        midnightTen.solution.take(4).forEach { assertTrue(previous.move(it)) }
        val restored = GameSession(levels, ProgressCodec.decode(ProgressCodec.encode(previous.progress)))
        assertFalse(restored.repairedSave)
        assertEquals(midnightTen.id, restored.level.id)
        assertEquals(previous.board, restored.board)
        assertEquals(previous.moves, restored.moves)
        assertTrue(moonlightTen.id in restored.progress.completed)
        assertTrue(restored.undo())
        restored.chooseLevel(moonlightTen.id)
        assertTrue(restored.board.isSolved)
    }

    @Test fun illegalMovesDoNotEnterHistory() {
        val session = GameSession(levels)
        assertFalse(session.move(Move(0, 0)))
        assertFalse(session.move(Move(99, 0)))
        assertFalse(session.canUndo)
    }
}
