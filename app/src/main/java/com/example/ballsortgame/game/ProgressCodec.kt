package com.example.ballsortgame.game

import com.example.ballsortgame.engine.Move
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/** Versioned private save format; histories store moves, not redundant board snapshots. */
object ProgressCodec {
    private const val MAGIC = 0x42534F52
    private const val VERSION = 1

    fun encode(progress: GameProgress): ByteArray {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(VERSION)
            out.writeUTF(progress.activeLevelId)
            out.writeBoolean(progress.colorLabels)
            out.writeBoolean(progress.reducedMotion)
            out.writeInt(progress.completed.size)
            progress.completed.sorted().forEach(out::writeUTF)
            out.writeInt(progress.histories.size)
            for ((id, moves) in progress.histories.toSortedMap()) {
                out.writeUTF(id)
                out.writeInt(moves.size)
                for (move in moves) { out.writeInt(move.source); out.writeInt(move.destination) }
            }
        }
        return buffer.toByteArray()
    }

    fun decode(bytes: ByteArray): GameProgress = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
        require(input.readInt() == MAGIC) { "Unrecognized save" }
        require(input.readInt() == VERSION) { "Unsupported save version" }
        val active = input.readUTF()
        val labels = input.readBoolean()
        val motion = input.readBoolean()
        fun count(minimumBytes: Int): Int {
            val value = input.readInt()
            require(value >= 0 && value <= input.available() / minimumBytes) { "Invalid save count" }
            return value
        }
        val completed = linkedSetOf<String>()
        repeat(count(2)) { require(completed.add(input.readUTF())) { "Duplicate completion" } }
        val histories = linkedMapOf<String, List<Move>>()
        repeat(count(6)) {
            val id = input.readUTF()
            val moves = List(count(8)) {
                val source = input.readInt()
                val destination = input.readInt()
                require(source >= 0 && destination >= 0)
                Move(source, destination)
            }
            require(histories.put(id, moves) == null) { "Duplicate history" }
        }
        require(input.available() == 0) { "Unexpected trailing save data" }
        GameProgress(active, histories, completed, labels, motion)
    }
}
