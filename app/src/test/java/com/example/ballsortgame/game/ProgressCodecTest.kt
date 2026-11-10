package com.example.ballsortgame.game

import com.example.ballsortgame.engine.Move
import org.junit.Assert.*
import org.junit.Test

class ProgressCodecTest {
    private val snapshot = GameProgress(
        "four-03", mapOf("three-01" to listOf(Move(0, 1)), "four-03" to listOf(Move(2, 4), Move(1, 2))),
        setOf("three-01"), colorLabels = true, reducedMotion = true,
    )

    @Test fun roundTripIncludesEverySettingAndLevelHistory() {
        assertEquals(snapshot, ProgressCodec.decode(ProgressCodec.encode(snapshot)))
    }

    @Test fun emptyProgressRoundTrips() {
        val progress = GameProgress("three-01")
        assertEquals(progress, ProgressCodec.decode(ProgressCodec.encode(progress)))
    }

    @Test fun truncatedSaveNeverRestoresPartialData() {
        val bytes = ProgressCodec.encode(snapshot)
        for (length in bytes.indices) {
            assertThrows(Exception::class.java) { ProgressCodec.decode(bytes.copyOf(length)) }
        }
    }

    @Test fun rejectsWrongFormatVersionAndTrailingGarbage() {
        val bytes = ProgressCodec.encode(snapshot)
        val badMagic = bytes.copyOf().apply { this[0] = 0 }
        val badVersion = bytes.copyOf().apply { this[7] = 99 }
        assertThrows(IllegalArgumentException::class.java) { ProgressCodec.decode(badMagic) }
        assertThrows(IllegalArgumentException::class.java) { ProgressCodec.decode(badVersion) }
        assertThrows(IllegalArgumentException::class.java) { ProgressCodec.decode(bytes + byteArrayOf(1)) }
    }

    @Test fun longUndoHistoryIsRetainedWithoutTruncation() {
        val longHistory = snapshot.copy(histories = mapOf("three-01" to List(2000) { Move(it % 3, (it + 1) % 3) }))
        assertEquals(longHistory, ProgressCodec.decode(ProgressCodec.encode(longHistory)))
    }
}
