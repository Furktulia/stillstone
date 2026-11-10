package com.example.ballsortgame.game

import com.example.ballsortgame.engine.Board
import com.example.ballsortgame.engine.Move
import com.example.ballsortgame.engine.PuzzleRules

data class PlayableLevel(
    val id: String,
    val collection: String,
    val initial: Board,
    val solution: List<Move>,
    val worldId: String = collection,
    val worldDescription: String = "",
) {
    init {
        require(!initial.isSolved)
        require(PuzzleRules.verifiesSolution(initial, solution)) { "Invalid level certificate: $id" }
    }
}

data class GameProgress(
    val activeLevelId: String,
    val histories: Map<String, List<Move>> = emptyMap(),
    val completed: Set<String> = emptySet(),
    val colorLabels: Boolean = false,
    val reducedMotion: Boolean = false,
)

/** Pure Kotlin session logic. Android, persistence and animation are separate. */
class GameSession(val levels: List<PlayableLevel>, restored: GameProgress? = null) {
    var progress: GameProgress
        private set
    var repairedSave: Boolean = false
        private set
    private var frames: List<Board> = emptyList()

    init {
        require(levels.isNotEmpty() && levels.map { it.id }.toSet().size == levels.size)
        val byId = levels.associateBy { it.id }
        val histories = linkedMapOf<String, List<Move>>()
        for ((id, moves) in restored?.histories.orEmpty()) {
            val level = byId[id]
            if (level == null) { repairedSave = true; continue }
            try {
                PuzzleRules.replay(level.initial, moves)
                histories[id] = moves.toList()
            } catch (_: IllegalArgumentException) {
                repairedSave = true
            }
        }
        val active = restored?.activeLevelId?.takeIf { it in byId } ?: levels.first().id
        if (restored != null && active != restored.activeLevelId) repairedSave = true
        progress = GameProgress(
            active, histories.toMap(), restored?.completed.orEmpty().intersect(byId.keys),
            restored?.colorLabels ?: false, restored?.reducedMotion ?: false,
        )
        rebuildFrames()
        if (board.isSolved) progress = progress.copy(completed = progress.completed + level.id)
    }

    val level: PlayableLevel get() = levels.first { it.id == progress.activeLevelId }
    val levelIndex: Int get() = levels.indexOf(level)
    val board: Board get() = frames.last()
    val moves: List<Move> get() = progress.histories[level.id].orEmpty()
    val canUndo: Boolean get() = moves.isNotEmpty()
    val boardHistory: List<Board> get() = frames.toList()

    fun move(move: Move): Boolean {
        if (board.isSolved) return false
        val transfer = PuzzleRules.transfer(board, move) ?: return false
        frames = frames + transfer.board
        progress = progress.copy(
            histories = progress.histories + (level.id to (moves + move)),
            completed = if (board.isSolved) progress.completed + level.id else progress.completed,
        )
        return true
    }

    fun undo(count: Int = 1): Boolean {
        require(count > 0)
        if (!canUndo) return false
        val remaining = moves.dropLast(count.coerceAtMost(moves.size))
        frames = frames.take(remaining.size + 1)
        progress = progress.copy(histories = progress.histories + (level.id to remaining))
        return true
    }

    fun restart() {
        frames = listOf(level.initial)
        progress = progress.copy(histories = progress.histories - level.id)
    }

    fun chooseLevel(id: String) {
        require(levels.any { it.id == id })
        progress = progress.copy(activeLevelId = id)
        rebuildFrames()
    }

    fun settings(colorLabels: Boolean, reducedMotion: Boolean) {
        progress = progress.copy(colorLabels = colorLabels, reducedMotion = reducedMotion)
    }

    private fun rebuildFrames() {
        val states = arrayListOf(level.initial)
        for (move in moves) states.add(requireNotNull(PuzzleRules.transfer(states.last(), move)).board)
        frames = states.toList()
    }
}
