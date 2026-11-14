package com.example.ballsortgame.game

import com.example.ballsortgame.engine.Board
import com.example.ballsortgame.engine.LevelMode
import com.example.ballsortgame.engine.LevelRules
import com.example.ballsortgame.engine.Move
import com.example.ballsortgame.engine.RuledPosition

data class PlayableLevel(
    val id: String,
    val collection: String,
    val initial: Board,
    val solution: List<Move>,
    val worldId: String = collection,
    val worldDescription: String = "",
    /** Classic sorting unless the level carries its own rules (seals, a bridge, a target pattern). */
    val rules: LevelRules = LevelRules.CLASSIC,
    /** A jump in difficulty, or simply a hard level. Always optional; shown so the player can choose. */
    val challenge: Boolean = false,
) {
    init {
        require(!rules.solved(initial))
        require(rules.verifiesSolution(initial, solution)) { "Invalid level certificate: $id" }
    }
    val mode: LevelMode get() = rules.mode(initial)
}

data class GameProgress(
    val activeLevelId: String,
    val histories: Map<String, List<Move>> = emptyMap(),
    val completed: Set<String> = emptySet(),
    val colorLabels: Boolean = false,
    val reducedMotion: Boolean = false,
)

/**
 * Pure Kotlin session logic. Android, persistence and animation are separate.
 *
 * Every move, undo and restored history goes through the active level's [LevelRules], so a
 * level with seals or a bridge is replayed to exactly the position it was left in. Only the
 * moves are saved; which seals are open follows from replaying them.
 */
class GameSession(val levels: List<PlayableLevel>, restored: GameProgress? = null) {
    var progress: GameProgress
        private set
    var repairedSave: Boolean = false
        private set
    private var frames: List<RuledPosition> = emptyList()

    init {
        require(levels.isNotEmpty() && levels.map { it.id }.toSet().size == levels.size)
        val byId = levels.associateBy { it.id }
        val histories = linkedMapOf<String, List<Move>>()
        for ((id, moves) in restored?.histories.orEmpty()) {
            val level = byId[id]
            if (level == null) { repairedSave = true; continue }
            try {
                level.rules.replay(level.initial, moves)
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
        if (solved) progress = progress.copy(completed = progress.completed + level.id)
    }

    val level: PlayableLevel get() = levels.first { it.id == progress.activeLevelId }
    val levelIndex: Int get() = levels.indexOf(level)
    val position: RuledPosition get() = frames.last()
    val board: Board get() = position.board
    val solved: Boolean get() = level.rules.solved(board)
    val moves: List<Move> get() = progress.histories[level.id].orEmpty()
    val canUndo: Boolean get() = moves.isNotEmpty()
    val boardHistory: List<Board> get() = frames.map { it.board }
    val positionHistory: List<RuledPosition> get() = frames.toList()

    /** How many stones [move] would carry from the current position, or null when it is not allowed. */
    fun carried(move: Move): Int? = if (solved) null else level.rules.transfer(position, move)?.second

    fun move(move: Move): Boolean {
        if (solved) return false
        val next = level.rules.transfer(position, move)?.first ?: return false
        frames = frames + next
        progress = progress.copy(
            histories = progress.histories + (level.id to (moves + move)),
            completed = if (solved) progress.completed + level.id else progress.completed,
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
        frames = listOf(level.rules.start(level.initial))
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
        val states = arrayListOf(level.rules.start(level.initial))
        for (move in moves) states.add(requireNotNull(level.rules.transfer(states.last(), move)).first)
        frames = states.toList()
    }
}
