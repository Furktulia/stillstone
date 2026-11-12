package com.example.ballsortgame.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.ballsortgame.engine.PuzzleRules
import com.example.ballsortgame.game.GameUiState
import com.example.ballsortgame.game.PlayableLevel

@Composable
fun GameContent(state: GameUiState, levels: List<PlayableLevel>, actions: GameActions, browse: (() -> Unit)? = null, nextPuzzle: (() -> Unit)? = null, showBrowse: Boolean = true, showBackdrop: Boolean = true, extraAction: (() -> Unit)? = null) {
    var showLevels by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var compareSounds by rememberSaveable { mutableStateOf(false) }
    var confirmRestart by rememberSaveable { mutableStateOf(false) }
    val feedback = rememberGameFeedback()
    val tap: (Int) -> Unit = { index ->
        if (state.reducedMotion && state.selected != null && state.selected != index) {
            PuzzleRules.transfer(state.board, com.example.ballsortgame.engine.Move(state.selected, index))?.let { transfer ->
                val destination = state.board.tubes[index]
                feedback.impact(LandingImpact(transfer.count, (destination.capacity - destination.balls.size).toFloat()))
            }
        }
        actions.tapTube(index)
    }
    val level = levels[state.levelIndex]
    val worldNumber = levels.take(state.levelIndex + 1).count { it.worldId == level.worldId }
    WorldTheme(level.worldId) {
    Box(Modifier.fillMaxSize()) {
    if (showBackdrop) WorldBackdrop(level.worldId, modifier = Modifier.matchParentSize())
    Scaffold(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground) { padding ->
        BoxWithConstraints(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).padding(horizontal = 20.dp),
        ) {
            if (state.loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                val wide = maxWidth >= 600.dp && maxWidth > maxHeight
                val restart = {
                    if (state.canUndo && !state.board.isSolved) confirmRestart = true else actions.restart()
                }
                if (wide) {
                    Row(
                        Modifier.fillMaxSize().widthIn(max = 1100.dp).align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        PuzzleBoard(state, tap, actions.finishTransfer, Modifier.weight(1f).fillMaxHeight(), feedback::landing, feedback::contact)
                        Column(
                            Modifier.width(240.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            GameHeader(level, worldNumber, { if (browse != null) browse() else showLevels = true }, { showSettings = true }, showBrowse, extraAction)
                            GameStatus(state)
                            GameControls(state, actions, restart)
                            Completion(state, nextPuzzle == null && state.levelIndex == levels.lastIndex) {
                                if (nextPuzzle != null) nextPuzzle() else if (state.levelIndex == levels.lastIndex) { if (browse != null) browse() else showLevels = true } else actions.chooseLevel(state.levelIndex + 1)
                            }
                        }
                    }
                } else {
                    Column(
                        Modifier.fillMaxSize().widthIn(max = 640.dp).align(Alignment.Center).padding(vertical = 8.dp),
                    ) {
                        GameHeader(level, worldNumber, { if (browse != null) browse() else showLevels = true }, { showSettings = true }, showBrowse, extraAction)
                        PuzzleBoard(
                            state, tap, actions.finishTransfer,
                            Modifier.weight(1f).fillMaxWidth(), feedback::landing, feedback::contact,
                        )
                        GameStatus(state)
                        Spacer(Modifier.height(12.dp))
                        GameControls(state, actions, restart)
                        Completion(state, nextPuzzle == null && state.levelIndex == levels.lastIndex) {
                            if (nextPuzzle != null) nextPuzzle() else if (state.levelIndex == levels.lastIndex) { if (browse != null) browse() else showLevels = true } else actions.chooseLevel(state.levelIndex + 1)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
    }
    if (showLevels) WorldPicker(levels, state, onDismiss = { showLevels = false }, choose = {
        actions.chooseLevel(it)
        showLevels = false
    }, onSettings = { showSettings = true })
    if (showSettings) SettingsDialog(state, feedback, actions.settings, { showSettings = false; compareSounds = true }) { showSettings = false }
    if (compareSounds) SoundComparison(feedback) { compareSounds = false; showSettings = true }
    if (confirmRestart) NightDialog(
        onDismissRequest = { confirmRestart = false },
        title = { Text("Start this puzzle again?") },
        text = { Text("Your current arrangement and undo history for this puzzle will be reset. Other puzzles are kept.") },
        confirmButton = { NightDialogAction("Restart puzzle", primary = true) { actions.restart(); confirmRestart = false } },
        dismissButton = { NightDialogAction("Keep playing") { confirmRestart = false } },
    )
    state.recovery?.let { recovery ->
        NightDialog(
            onDismissRequest = actions.dismissRecovery,
            title = { Text(if (recovery.provenUnsolvable) "A way back" else "No hint found yet") },
            text = {
                Text(
                    (if (recovery.provenUnsolvable) "This position cannot be solved. "
                    else "The search reached its limit; this position may still be solvable. ") +
                        "Undo ${recovery.undoCount} ${if (recovery.undoCount == 1) "move" else "moves"} to return to a position with a known solution.",
                )
            },
            confirmButton = { NightDialogAction("Go back", primary = true, onClick = actions.recover) },
            dismissButton = { NightDialogAction("Keep playing", onClick = actions.dismissRecovery) },
        )
    }
}

}

@Composable
private fun GameHeader(level: PlayableLevel, worldNumber: Int, puzzles: () -> Unit, settings: () -> Unit, showBrowse: Boolean, extraAction: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (showBrowse) NightButton(if (level.worldId == "endless") "‹ Worlds" else "‹ ${level.collection}",
            Modifier.testTag("puzzles"), onClick = puzzles)
        Spacer(Modifier.weight(1f))
        if (extraAction != null) IconButton(extraAction,
            Modifier.semantics { contentDescription = "Infinite options" }.testTag("infinite_options")) { Text("⋯", style = MaterialTheme.typography.headlineMedium) }
        SettingsButton(settings, Modifier.testTag("settings"))
    }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (level.worldId == "endless") "Infinite" else "Level $worldNumber",
            style = MaterialTheme.typography.headlineMedium)
        Text(level.initial.tubes.map { it.capacity }.distinct().joinToString(" / ") + " slots",
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun GameStatus(state: GameUiState) {
    val blocked = !state.board.isSolved && PuzzleRules.legalMoves(state.board).isEmpty()
    val text = when {
        state.board.isSolved -> "Nicely sorted."
        state.message.isNotBlank() -> state.message
        blocked -> "No moves available. Undo, restart, or ask for a hint."
        state.selected != null -> "Choose a matching color or an empty tube."
        state.queuedTransfers > 0 -> ""
        else -> "Tap a tube to begin."
    }
    Column(Modifier.fillMaxWidth().heightIn(min = 42.dp), verticalArrangement = Arrangement.Center) {
        Text(
            text, color = if (state.board.isSolved) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("game_status"),
        )
        if (state.thinking) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        state.saveWarning?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun GameControls(state: GameUiState, actions: GameActions, restart: () -> Unit) {
    val intro = rememberControlIntro()
    val solved = state.board.isSolved
    LaunchedEffect(solved) { if (solved) intro.puzzleSolved() }
    // Quiet outline icons, one row at any text size: the board is what the player looks at.
    Row(Modifier.fillMaxWidth()) {
        QuietControl(NightSymbol.UNDO, "Undo", Modifier.weight(1f).testTag("undo"), enabled = state.canUndo,
            showName = intro.showName(NightSymbol.UNDO)) { intro.used(NightSymbol.UNDO); actions.undo() }
        QuietControl(NightSymbol.RESTART, if (solved) "Replay" else "Restart", Modifier.weight(1f).testTag("restart"),
            showName = intro.showName(NightSymbol.RESTART)) { intro.used(NightSymbol.RESTART); restart() }
        QuietControl(NightSymbol.HINT, if (state.thinking) "Cancel hint" else "Hint", Modifier.weight(1f).testTag("hint"),
            enabled = !solved, showName = state.thinking || intro.showName(NightSymbol.HINT)) {
            intro.used(NightSymbol.HINT)
            if (state.thinking) actions.cancelHint() else actions.hint()
        }
    }
}

@Composable
private fun Completion(state: GameUiState, last: Boolean, next: () -> Unit) {
    if (state.board.isSolved && state.transfer == null) {
        Spacer(Modifier.height(12.dp))
        NightButton(if (last) "Choose another puzzle" else "Next puzzle",
            modifier = Modifier.fillMaxWidth().testTag("next"), onClick = next)
    }
}

@Composable
internal fun SettingsDialog(state: GameUiState, feedback: GameFeedback, change: (Boolean, Boolean) -> Unit, compare: () -> Unit, dismiss: () -> Unit) {
    NightDialog(
        onDismissRequest = dismiss, title = { Text("Make yourself comfortable") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Stone sounds", Modifier.weight(1f))
                    NightSwitch(feedback.sound, feedback::sound, modifier = Modifier.semantics { contentDescription = "Stone sounds" })
                }
                // Pulled left so its lettering lines up with the rows around it.
                NightDialogAction("Sound · ${feedback.style.title} ›", Modifier.offset(x = (-14).dp), onClick = compare)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Gentle haptics", Modifier.weight(1f))
                    NightSwitch(feedback.haptics, feedback::haptics, modifier = Modifier.semantics { contentDescription = "Gentle haptics" })
                }
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Color labels", Modifier.weight(1f))
                        NightSwitch(
                            checked = state.colorLabels, onCheckedChange = { change(it, state.reducedMotion) },
                            modifier = Modifier.semantics { contentDescription = "Color labels" },
                        )
                    }
                    Text("Add numbers to pieces so you can identify them without color.", style = MaterialTheme.typography.bodySmall)
                }
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Reduced motion", Modifier.weight(1f))
                        NightSwitch(
                            checked = state.reducedMotion, onCheckedChange = { change(state.colorLabels, it) },
                            modifier = Modifier.semantics { contentDescription = "Reduced motion" },
                        )
                    }
                    Text("Move pieces instantly, without travel animations.", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { NightDialogAction("Done", primary = true, onClick = dismiss) },
    )
}
