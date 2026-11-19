package com.example.ballsortgame.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ballsortgame.game.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GameHub(world: GameViewModel, owner: ViewModelStoreOwner) {
    val context = LocalContext.current
    val endless = remember {
        ViewModelProvider(owner, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>) = EndlessViewModel(context.applicationContext) as T
        })[EndlessViewModel::class.java]
    }
    val preferences = remember { context.getSharedPreferences("mode", Context.MODE_PRIVATE) }
    PlayOptions.load(context.applicationContext) // Reads the saved choice once; later calls do nothing.
    var mode by rememberSaveable { mutableStateOf(preferences.getBoolean("endless", false)) }
    // "challenge" is the earlier two-way preference; it only seeds the first three-way choice.
    var recipe by rememberSaveable { mutableStateOf(preferences.getString("recipe", null) ?: if (preferences.getBoolean("challenge", false)) "C" else "B") }
    // New Infinite puzzles follow the way of playing that is switched on when they are made.
    fun newPuzzle(entered: String? = null) = endless.next(recipe.first(), entered, PlayOptions.hiddenColors)
    var browseWorlds by rememberSaveable { mutableStateOf(false) }
    var pickerWorld by rememberSaveable { mutableStateOf<String?>(null) }
    var browserSettings by rememberSaveable { mutableStateOf(false) }
    var browserCompare by rememberSaveable { mutableStateOf(false) }
    val worldState by world.state.collectAsStateWithLifecycle()
    var menu by rememberSaveable { mutableStateOf(false) }
    var enter by rememberSaveable { mutableStateOf(false) }
    var bookmark by rememberSaveable { mutableStateOf(false) }
    var testers by rememberSaveable { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf<List<String>?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(message) { if (message.isNotEmpty()) { kotlinx.coroutines.delay(3000); message = "" } }
    val state by endless.state.collectAsStateWithLifecycle()
    DisposableEffect(mode, browseWorlds, owner) {
        val lifecycle = (owner as LifecycleOwner).lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) endless.setActive(false)
            if (event == Lifecycle.Event.ON_START) endless.setActive(mode && !browseWorlds)
        }
        lifecycle.addObserver(observer)
        endless.setActive(mode && !browseWorlds && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        onDispose { lifecycle.removeObserver(observer); endless.setActive(false) }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-ndjson")) { uri ->
        if (uri != null) scope.launch {
            message = withContext(Dispatchers.IO) {
                try { context.contentResolver.openOutputStream(uri)?.use { it.write(endless.examples().toByteArray()) }
                    ?: error("No output"); "Examples exported." } catch (_: Exception) { "Export failed. Examples are still saved locally." }
            }
        }
    }
    fun openBrowser(worldId: String?) {
        if (mode) state.game?.settlePresentation() else world.settlePresentation()
        pickerWorld = worldId
        browseWorlds = true
    }
    LaunchedEffect(mode) { preferences.edit().putBoolean("endless", mode).apply() }
    BackHandler(enabled = !browseWorlds) {
        openBrowser(if (mode) null else world.levels[worldState.levelIndex].worldId)
    }
    WorldTheme(if (mode) "endless" else world.levels[worldState.levelIndex].worldId) {
        Surface(Modifier.fillMaxSize(), color = LocalWorldLook.current.base) {
            if (!browseWorlds) {
                if (!mode) {
                    GameScreen(worldState, world, browse = { openBrowser(world.levels[worldState.levelIndex].worldId) })
                } else {
                    Column(Modifier.fillMaxSize()) {
                        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().statusBarsPadding(),
                            color = LocalWorldLook.current.accent.copy(alpha = .6f), trackColor = androidx.compose.ui.graphics.Color.Transparent)
                        if (state.error.isNotEmpty()) Text(state.error, Modifier.padding(20.dp), color = MaterialTheme.colorScheme.error)
                        val game = state.game
                        if (game != null) {
                            val gameState by game.state.collectAsStateWithLifecycle()
                            // A new difficulty starts a puzzle at once when nothing has been played yet;
                            // a puzzle already under way is never thrown away by a tap on a pill.
                            fun choose(difficulty: InfiniteDifficulty) {
                                recipe = difficulty.letter.toString(); preferences.edit().putString("recipe", recipe).apply()
                                message = if (!gameState.canUndo && !state.busy) { newPuzzle(); difficulty.explanation }
                                    else "Your next puzzle will be ${difficulty.title}. ${difficulty.explanation}"
                            }
                            Box(Modifier.weight(1f)) {
                                GameContent(gameState, game.levels, GameActions(
                                    game::tapTube, game::finishTransfer, game::undo, game::restart,
                                    game::hint, game::cancelHint, game::chooseLevel, game::settings,
                                    game::dismissRecovery, game::recover),
                                    browse = { openBrowser(null) },
                                    nextPuzzle = { if (!state.busy) newPuzzle() },
                                    extraAction = { menu = true },
                                    belowHeader = { DifficultyPills(state.code.split("-").getOrNull(1)?.singleOrNull() ?: recipe.first(), recipe.first(), gameState.canUndo, ::choose) },
                                    note = message)
                            }
                        } else Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            if (state.busy) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = LocalWorldLook.current.accent.copy(alpha = .6f), strokeWidth = 2.dp)
                                Spacer(Modifier.height(14.dp))
                                Text("Making a puzzle…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            else NightButton("Try another puzzle", onClick = { newPuzzle() })
                        }
                    }
                }
            }
        }
        if (browseWorlds) WorldPicker(world.levels, worldState,
            onDismiss = { browseWorlds = false },
            choose = { world.chooseLevel(it); mode = false; browseWorlds = false },
            initialWorld = pickerWorld,
            onInfinite = { mode = true; browseWorlds = false },
            onSettings = { browserSettings = true },
            onBack = {
                var owner: Context? = context
                while (owner is android.content.ContextWrapper && owner !is android.app.Activity) owner = owner.baseContext
                (owner as? android.app.Activity)?.finish()
            })
        if (browserSettings || browserCompare) {
            val activeGame = if (mode) state.game ?: world else world
            val ui by activeGame.state.collectAsStateWithLifecycle()
            val feedback = rememberGameFeedback()
            if (browserSettings) SettingsDialog(ui, feedback, activeGame::settings,
                { browserSettings = false; browserCompare = true }, { browserSettings = false })
            if (browserCompare) SoundComparison(feedback) { browserCompare = false; browserSettings = true }
        }
    // What a player needs: a new puzzle, and keeping one to come back to. Codes, ratings and export
    // are for playtesting and wait one step further in, so the menu reads as part of the game.
    if (menu) NightDialog(onDismissRequest = { menu = false }, title = { Text("Infinite") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            NightMenuRow("New puzzle", detail = InfiniteDifficulty.of(recipe.first()).title, enabled = !state.busy) { newPuzzle(); menu = false }
            NightMenuRow("Save this puzzle", detail = "Keep it to play again later", enabled = state.game != null) {
                endless.bookmark("saved", "") { message = it }; menu = false }
            NightMenuRow("Saved puzzles") { scope.launch { saved = withContext(Dispatchers.IO) { endless.examples().lines().filter { it.isNotBlank() } } }; menu = false }
            NightMenuRow("For testers ›", detail = "Puzzle codes, ratings and export") { testers = true; menu = false }
        }
    }, confirmButton = { NightDialogAction("Done", primary = true) { menu = false } })
    if (testers) NightDialog(onDismissRequest = { testers = false }, title = { Text("For testers") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            NightMenuRow(state.code.ifEmpty { "Making a puzzle…" }, detail = "Copy this puzzle's code") {
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("Puzzle code", state.code))
                message = "Code copied."; testers = false
            }
            NightMenuRow("Play a puzzle code") { enter = true; testers = false }
            NightMenuRow("Rate this puzzle", detail = "Save it with a note on what worked", enabled = state.game != null) { bookmark = true; testers = false }
            NightMenuRow("Export saved puzzles") { export.launch("ball-sort-examples.ndjson"); testers = false }
        }
    }, confirmButton = { NightDialogAction("Done", primary = true) { testers = false } })
    saved?.let { examples ->
        NightDialog(onDismissRequest = { saved = null }, title = { Text("Saved puzzles") },
            text = { androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 400.dp)) {
                if (examples.isEmpty()) item { Text("Puzzles you save appear here.") }
                items(examples.size) { index ->
                    val value = org.json.JSONObject(examples[index])
                    val puzzleCode = value.optString("code")
                    // "E2-B-…": the middle letter is the difficulty; H codes are hidden-colour puzzles.
                    val title = InfiniteDifficulty.of(puzzleCode.split("-").getOrNull(1)?.singleOrNull() ?: 'B').title +
                        if (puzzleCode.startsWith("H")) " · hidden colors" else ""
                    NightMenuRow(title, detail = value.optString("note").ifBlank { puzzleCode }) {
                        endless.loadSaved(examples[index]); saved = null }
                }
            } }, confirmButton = { NightDialogAction("Done", primary = true) { saved = null } })
    }
    if (enter) NightDialog(onDismissRequest = { enter = false }, title = { Text("Play a puzzle code") },
        text = { NightTextField(code, { code = it }, "For example E2-B-1") },
        confirmButton = { NightDialogAction("Play", primary = true) { newPuzzle(code); enter = false } },
        dismissButton = { NightDialogAction("Cancel") { enter = false } })
    if (bookmark) NightDialog(onDismissRequest = { bookmark = false }, title = { Text("Rate this puzzle") },
        text = { Column { Text("Hard can be good. Describe what worked or felt wrong."); Spacer(Modifier.height(10.dp)); NightTextField(note, { note = it }, "Optional note") } },
        confirmButton = { NightDialogAction("Good example") { endless.bookmark("good", note) { message = it }; bookmark = false; note = "" } },
        dismissButton = { NightDialogAction("Needs work") { endless.bookmark("needs-work", note) { message = it }; bookmark = false; note = "" } })
    }
}
