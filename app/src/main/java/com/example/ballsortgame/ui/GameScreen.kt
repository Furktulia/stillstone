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
    val feedback = rememberGameFeedback(moving = state.transfers.isNotEmpty())
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    PlayOptions.load(appContext) // Reads the saved choice once; later calls do nothing.
    val tap: (Int) -> Unit = { index ->
        if (state.reducedMotion && state.selected != null && state.selected != index) {
            state.rules.transfer(com.example.ballsortgame.engine.RuledPosition(state.board, state.openSeals),
                com.example.ballsortgame.engine.Move(state.selected, index))?.let { (_, count) ->
                val destination = state.board.tubes[index]
                val impact = LandingImpact(count, destination.freeSpace.toFloat())
                if (PlayOptions.waterBeads) {
                    feedback.landing(impact)
                    val kind = if (destination.balls.isEmpty()) ContactKind.FLOOR else ContactKind.STONE
                    feedback.contact(StoneContact(kind, index, LandingMotion.fallSpeed(impact.dropSlots, 1f)))
                } else feedback.impact(impact)
            }
        }
        actions.tapTube(index)
    }
    val level = levels[state.levelIndex]
    val worldNumber = levels.take(state.levelIndex + 1).count { it.worldId == level.worldId }
    WorldTheme(level.worldId) {
    Box(Modifier.fillMaxSize()) {
    if (showBackdrop) PlayBackdrop(level.worldId, Modifier.matchParentSize())
    Scaffold(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground) { padding ->
        BoxWithConstraints(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).padding(horizontal = 20.dp),
        ) {
            if (state.loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = LocalWorldLook.current.accent.copy(alpha = .6f), strokeWidth = 2.dp)
            } else {
                val wide = maxWidth >= 600.dp && maxWidth > maxHeight
                val restart = {
                    if (state.canUndo && !state.solved) confirmRestart = true else actions.restart()
                }
                val last = nextPuzzle == null && state.levelIndex == levels.lastIndex
                val goNext: () -> Unit = {
                    if (nextPuzzle != null) nextPuzzle() else if (last) { if (browse != null) browse() else showLevels = true } else actions.chooseLevel(state.levelIndex + 1)
                }
                if (wide) {
                    Row(
                        Modifier.fillMaxSize().widthIn(max = 1100.dp).align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        PuzzleBoard(state, tap, actions.finishTransfer, Modifier.weight(1f).fillMaxHeight(), feedback::landing, feedback::contact, PlayOptions.hiddenColors)
                        Column(
                            Modifier.width(240.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            GameHeader(level, worldNumber, { if (browse != null) browse() else showLevels = true }, { showSettings = true }, showBrowse, extraAction, moved = state.canUndo)
                            GameStatus(state, last, goNext, teach = level.worldId != "endless")
                            GameControls(state, actions, restart)
                        }
                    }
                } else {
                    Column(
                        Modifier.fillMaxSize().widthIn(max = 640.dp).align(Alignment.Center).padding(vertical = 8.dp),
                    ) {
                        GameHeader(level, worldNumber, { if (browse != null) browse() else showLevels = true }, { showSettings = true }, showBrowse, extraAction, moved = state.canUndo)
                        state.rules.target?.let { PatternTarget(it, state.colorLabels, state.board) }
                        PuzzleBoard(
                            state, tap, actions.finishTransfer,
                            Modifier.weight(1f).fillMaxWidth(), feedback::landing, feedback::contact, PlayOptions.hiddenColors,
                        )
                        GameStatus(state, last, goNext, teach = level.worldId != "endless")
                        Spacer(Modifier.height(12.dp))
                        GameControls(state, actions, restart)
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
private fun GameHeader(level: PlayableLevel, worldNumber: Int, puzzles: () -> Unit, settings: () -> Unit, showBrowse: Boolean, extraAction: (() -> Unit)?, moved: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        // The level number rides on the way back to its world: it is where you are, not a title.
        if (showBrowse) NightButton(if (level.worldId == "endless") "‹ Worlds" else "‹ ${level.collection} · $worldNumber",
            Modifier.testTag("puzzles"), onClick = puzzles)
        Spacer(Modifier.weight(1f))
        if (extraAction != null) IconButton(extraAction,
            Modifier.semantics { contentDescription = "Infinite options" }.testTag("infinite_options")) { Text("⋯", style = MaterialTheme.typography.headlineMedium) }
        SettingsButton(settings, Modifier.testTag("settings"))
    }
    // Only levels that differ from plain sorting say so, in a bubble that explains itself when tapped.
    Box(Modifier.fillMaxWidth().padding(top = 2.dp), contentAlignment = Alignment.Center) { TraitBubbles(level, moved) }
}

@Composable
private fun GameStatus(state: GameUiState, last: Boolean, next: () -> Unit, teach: Boolean = true) {
    val blocked = !state.solved && state.rules.legalMoves(com.example.ballsortgame.engine.RuledPosition(state.board, state.openSeals)).isEmpty()
    val text = when {
        state.solved -> ""
        state.message.isNotBlank() -> state.message
        blocked -> "No moves available. Undo, restart, or ask for a hint."
        // Every tube holds one color, yet a color is still split between two tubes.
        state.rules.gather && state.board.isSolved -> "Almost: each color has to end up together in one tube."
        // The first two puzzles teach the game in a line each: how to move, then what for. After
        // that the line stays empty unless something needs saying; each mode explains itself in
        // its bubble instead.
        teach && state.levelIndex == 0 && !state.canUndo && state.rules.isClassic ->
            if (state.selected == null) "Tap a tube to pick up its top stones." else "Now tap the tube where they should go."
        teach && state.levelIndex == 1 && state.rules.isClassic -> "Give every color a tube of its own."
        else -> ""
    }
    // One slot of fixed height holds either a line of text or, once the puzzle is finished, the
    // way onward. Nothing above or below it moves when the stone appears.
    Column(Modifier.fillMaxWidth().heightIn(min = 60.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        if (state.solved && state.transfer == null) NightButton(if (last) "Choose another puzzle" else "Next puzzle  ›",
            modifier = Modifier.widthIn(min = 200.dp).testTag("next"), onClick = next)
        else Text(
            text, color = if (state.solved) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.testTag("game_status"),
        )
        if (state.thinking) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(Modifier.width(120.dp), color = LocalWorldLook.current.accent.copy(alpha = .6f), trackColor = Color.Transparent)
        }
        state.saveWarning?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun GameControls(state: GameUiState, actions: GameActions, restart: () -> Unit) {
    val intro = rememberControlIntro()
    val solved = state.solved
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
internal fun SettingsDialog(state: GameUiState, feedback: GameFeedback, change: (Boolean, Boolean) -> Unit, compare: () -> Unit, dismiss: () -> Unit) {
    NightDialog(
        onDismissRequest = dismiss, title = { Text("Make yourself comfortable") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                val materialContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Water beads", Modifier.weight(1f))
                    NightSwitch(PlayOptions.waterBeads, { PlayOptions.waterBeads(materialContext, it) },
                        modifier = Modifier.semantics { contentDescription = "Water beads" })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (PlayOptions.waterBeads) "Bead sounds" else "Stone sounds", Modifier.weight(1f))
                    NightSwitch(feedback.sound, feedback::sound, modifier = Modifier.semantics { contentDescription = if (PlayOptions.waterBeads) "Bead sounds" else "Stone sounds" })
                }
                // Pulled left so its lettering lines up with the rows around it.
                if (!PlayOptions.waterBeads) NightDialogAction("Sound · ${feedback.style.title} ›", Modifier.offset(x = (-14).dp), onClick = compare)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Gentle haptics", Modifier.weight(1f))
                    NightSwitch(feedback.haptics, feedback::haptics, modifier = Modifier.semantics { contentDescription = "Gentle haptics" })
                }
                Column {
                    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Hidden colors", Modifier.weight(1f))
                        NightSwitch(PlayOptions.hiddenColors, { PlayOptions.hiddenColors(context, it) },
                            modifier = Modifier.semantics { contentDescription = "Hidden colors" })
                    }
                    Text("Only the top stone of each tube shows its color until you uncover the ones beneath. " +
                        "Some levels and Infinite puzzles are like this anyway; switch it on to have it everywhere.", style = MaterialTheme.typography.bodySmall)
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
