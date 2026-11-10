package com.example.ballsortgame.game

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

data class StoredProgress(val progress: GameProgress?, val warning: String? = null)

interface GameSaveStore {
    fun load(): StoredProgress
    fun save(progress: GameProgress)
}

/** Called only on the ViewModel's IO worker; AtomicFile protects interrupted writes. */
class AtomicGameSaveStore(context: Context) : GameSaveStore {
    private val file = AtomicFile(File(context.filesDir, "game-progress-v1.bin"))

    override fun load(): StoredProgress = try {
        StoredProgress(ProgressCodec.decode(file.readFully()))
    } catch (_: FileNotFoundException) {
        StoredProgress(null)
    } catch (_: IOException) {
        StoredProgress(null, "Your saved session could not be read. A fresh session is ready.")
    } catch (_: IllegalArgumentException) {
        StoredProgress(null, "Your saved session could not be restored. A fresh session is ready.")
    }

    override fun save(progress: GameProgress) {
        val bytes = ProgressCodec.encode(progress)
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }
}
