package com.example.ballsortgame.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ballsortgame.engine.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MovingGroup(val id: Long, val before: Board, val move: Move, val count: Int)
data class Recovery(val undoCount: Int, val provenUnsolvable: Boolean)

data class GameUiState(
    val loading: Boolean = true,
    val levelIndex: Int = 0,
    val board: Board,
    val canUndo: Boolean = false,
    val completed: Set<String> = emptySet(),
    val selected: Int? = null,
    val hint: Move? = null,
    val thinking: Boolean = false,
    val transfer: MovingGroup? = null,
    val queuedTransfers: Int = 0,
    val transfers: List<MovingGroup> = emptyList(),
    val message: String = "",
    val recovery: Recovery? = null,
    val colorLabels: Boolean = false,
    val reducedMotion: Boolean = false,
    val saveWarning: String? = null,
    val revision: Long = 0,
    /**
     * For play with hidden colours: how many bottom stones of each tube the player has never
     * seen. Always tracked; whether it is shown is a display choice (see HiddenColors).
     */
    val unseen: List<Int> = emptyList(),
)

class GameViewModel(
    val levels: List<PlayableLevel>,
    private val store: GameSaveStore,
) : ViewModel() {
    private var session = GameSession(levels)
    private val mutableState = MutableStateFlow(GameUiState(board = session.board))
    val state = mutableState.asStateFlow()
    private val saves = Channel<GameProgress>(Channel.CONFLATED)
    private var hintJob: Job? = null
    private var revision = 0L
    private var animations = emptyList<MovingGroup>()
    private val knownSolutions = HashMap<Board, List<Move>>()
    // Smallest size each tube of the current puzzle has had while the player watched. Undo and
    // restart do not shrink it back: a colour that has been seen stays seen. Not saved; after
    // a restart of the app it is rebuilt from the saved moves, which is all that is known.
    private var smallestSizes = HiddenColors.smallestSizes(session.level.initial, session.moves)
    private fun forgetSeen() { smallestSizes = HiddenColors.smallestSizes(session.level.initial, session.moves) }

    init {
        levels.forEach { cacheSolution(it.initial, it.solution) }
        viewModelScope.launch {
            for (snapshot in saves) {
                try {
                    withContext(Dispatchers.IO) { store.save(snapshot) }
                    mutableState.update { it.copy(saveWarning = null) }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    mutableState.update { it.copy(saveWarning = "Progress could not be saved. Please check free storage.") }
                }
            }
        }
        viewModelScope.launch {
            val restored = withContext(Dispatchers.IO) {
                val stored = store.load()
                GameSession(levels, stored.progress) to stored.warning
            }
            session = restored.first
            forgetSeen()
            publish(message = restored.second ?: if (session.repairedSave) {
                "An incompatible saved puzzle was reset. Your other progress was kept."
            } else "")
            persist()
        }
    }

    private fun publish(
        selected: Int? = null,
        hint: Move? = null,
        thinking: Boolean = false,
        message: String = "",
        recovery: Recovery? = null,
    ) {
        mutableState.value = GameUiState(
            loading = false, levelIndex = session.levelIndex, board = session.board,
            canUndo = session.canUndo, completed = session.progress.completed,
            selected = selected, hint = hint, thinking = thinking, transfer = animations.firstOrNull(),
            queuedTransfers = (animations.size - 1).coerceAtLeast(0), transfers = animations,
            message = message, recovery = recovery,
            colorLabels = session.progress.colorLabels, reducedMotion = session.progress.reducedMotion,
            saveWarning = mutableState.value.saveWarning, revision = revision,
            unseen = HiddenColors.hiddenCounts(smallestSizes),
        )
    }

    private fun invalidate() {
        revision++
        hintJob?.cancel()
        hintJob = null
    }

    fun snapshotProgress(): GameProgress = session.progress

    private fun persist() { saves.trySend(session.progress) }

    fun tapTube(index: Int) {
        val ui = state.value
        if (ui.loading || session.board.isSolved) return
        if (index !in session.board.tubes.indices) return
        invalidate()
        val selected = ui.selected
        if (selected == index) {
            // Confirm a hinted source without toggling off its automatic selection.
            publish(selected = index.takeIf { ui.hint?.source == index })
            return
        }
        if (selected == null) {
            publish(selected = index.takeIf { session.board.tubes[it].balls.isNotEmpty() })
            return
        }
        val move = Move(selected, index)
        val before = session.board
        val transfer = PuzzleRules.transfer(before, move)
        if (transfer == null) {
            publish(
                selected = index.takeIf { before.tubes[it].balls.isNotEmpty() },
                message = "Choose an empty tube or a matching top color with room.",
            )
            return
        }
        check(session.move(move))
        HiddenColors.observe(smallestSizes, session.board)
        if (!session.progress.reducedMotion) animations = animations + MovingGroup(revision, before, move, transfer.count)
        publish()
        persist()
    }

    fun finishTransfer(id: Long) {
        if (animations.firstOrNull()?.id != id) return
        animations = animations.drop(1)
        mutableState.update { it.copy(transfer = animations.firstOrNull(), queuedTransfers = (animations.size - 1).coerceAtLeast(0), transfers = animations) }
    }

    /** Logical moves are committed immediately; leaving the board retires only their presentation. */
    fun settlePresentation() {
        if (state.value.loading) return
        invalidate()
        animations = emptyList()
        publish()
    }

    fun undo() {
        if (state.value.loading) return
        invalidate()
        animations = emptyList()
        session.undo()
        publish()
        persist()
    }

    fun restart() {
        if (state.value.loading) return
        invalidate()
        animations = emptyList()
        session.restart()
        publish()
        persist()
    }

    fun chooseLevel(index: Int) {
        if (state.value.loading || index !in levels.indices) return
        invalidate()
        animations = emptyList()
        session.chooseLevel(levels[index].id)
        forgetSeen()
        publish()
        persist()
    }

    fun settings(colorLabels: Boolean, reducedMotion: Boolean) {
        if (state.value.loading) return
        invalidate()
        animations = emptyList()
        session.settings(colorLabels, reducedMotion)
        publish()
        persist()
    }

    fun cancelHint() {
        invalidate()
        publish(message = "Hint cancelled.")
    }

    fun dismissRecovery() { mutableState.update { it.copy(recovery = null) } }

    fun recover() {
        val recovery = state.value.recovery ?: return
        invalidate()
        animations = emptyList()
        check(session.undo(recovery.undoCount))
        publish(message = "Back at a position with a known solution.")
        persist()
    }

    fun hint() {
        // Hints read the committed board, so they never need to wait for stones to finish travelling.
        if (state.value.loading || session.board.isSolved) return
        invalidate()
        val board = session.board
        val cached = knownSolutions[board]
        if (!cached.isNullOrEmpty()) {
            showHint(cached.first())
            return
        }
        val requestRevision = revision
        publish(thinking = true, message = "Looking for a useful move…")
        hintJob = viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                val context = currentCoroutineContext()
                PuzzleSolver().solve(
                    board,
                    SearchOptions(
                        SearchLimits(25_000, 30_000, 40_000, 750),
                        heuristicWeight = 4,
                    ),
                    cancelled = { !context.isActive },
                )
            }
            if (revision != requestRevision || session.board != board) return@launch
            when (result) {
                is SolveResult.Solved -> {
                    check(PuzzleRules.verifiesSolution(board, result.moves))
                    cacheSolution(board, result.moves)
                    result.moves.firstOrNull()?.let(::showHint) ?: publish()
                }
                is SolveResult.Unsolvable -> offerRecovery(proven = true)
                is SolveResult.LimitReached -> offerRecovery(proven = false)
                is SolveResult.Cancelled -> publish()
            }
        }
    }

    private fun showHint(move: Move) {
        publish(
            selected = move.source, hint = move,
            message = "Move the top group from tube ${move.source + 1} to tube ${move.destination + 1}.",
        )
    }

    private fun offerRecovery(proven: Boolean) {
        val history = session.boardHistory
        val knownIndex = (history.lastIndex - 1 downTo 0).firstOrNull { knownSolutions.containsKey(history[it]) }
        val recovery = knownIndex?.let { Recovery(history.lastIndex - it, proven) }
        publish(
            message = if (proven) "This position cannot be solved. Undo can get you back."
                else "No hint found within the search budget. This does not mean the puzzle is impossible.",
            recovery = recovery,
        )
    }

    private fun cacheSolution(initial: Board, moves: List<Move>) {
        var board = initial
        for (index in 0..moves.size) {
            val tail = moves.drop(index)
            val previous = knownSolutions[board]
            if (previous == null || tail.size < previous.size) knownSolutions[board] = tail
            if (index < moves.size) board = requireNotNull(PuzzleRules.transfer(board, moves[index])).board
        }
    }

    override fun onCleared() {
        hintJob?.cancel()
        saves.close()
        super.onCleared()
    }
}
