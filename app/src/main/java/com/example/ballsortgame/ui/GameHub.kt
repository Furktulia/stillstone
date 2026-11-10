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
    var mode by rememberSaveable { mutableStateOf(preferences.getBoolean("endless", false)) }
    var challenge by rememberSaveable { mutableStateOf(preferences.getBoolean("challenge", false)) }
    var browseWorlds by rememberSaveable { mutableStateOf(false) }
    var pickerWorld by rememberSaveable { mutableStateOf<String?>(null) }
    var browserSettings by rememberSaveable { mutableStateOf(false) }
    var browserCompare by rememberSaveable { mutableStateOf(false) }
    val worldState by world.state.collectAsStateWithLifecycle()
    var menu by rememberSaveable { mutableStateOf(false) }
    var enter by rememberSaveable { mutableStateOf(false) }
    var bookmark by rememberSaveable { mutableStateOf(false) }
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
    WorldTheme(if (mode) "three" else world.levels[worldState.levelIndex].worldId) {
        Surface(Modifier.fillMaxSize(), color = LocalWorldLook.current.base) {
            if (!browseWorlds) {
                if (!mode) {
                    GameScreen(worldState, world, browse = { openBrowser(world.levels[worldState.levelIndex].worldId) })
                } else {
                    Column(Modifier.fillMaxSize()) {
                        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().statusBarsPadding())
                        if (state.error.isNotEmpty()) Text(state.error, Modifier.padding(20.dp), color = MaterialTheme.colorScheme.error)
                        if (message.isNotEmpty()) Text(message, Modifier.padding(horizontal = 20.dp))
                        val game = state.game
                        if (game != null) {
                            val gameState by game.state.collectAsStateWithLifecycle()
                            Box(Modifier.weight(1f)) {
                                GameContent(gameState, game.levels, GameActions(
                                    game::tapTube, game::finishTransfer, game::undo, game::restart,
                                    game::hint, game::cancelHint, game::chooseLevel, game::settings,
                                    game::dismissRecovery, game::recover),
                                    browse = { openBrowser(null) },
                                    nextPuzzle = { if (!state.busy) endless.next(challenge) },
                                    extraAction = { menu = true })
                            }
                        } else Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            if (state.busy) CircularProgressIndicator()
                            else NightButton("Try another puzzle", onClick = { endless.next(challenge) })
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
            onSettings = { browserSettings = true })
        if (browserSettings || browserCompare) {
            val activeGame = if (mode) state.game ?: world else world
            val ui by activeGame.state.collectAsStateWithLifecycle()
            val feedback = rememberGameFeedback()
            if (browserSettings) SettingsDialog(ui, feedback, activeGame::settings,
                { browserSettings = false; browserCompare = true }, { browserSettings = false })
            if (browserCompare) SoundComparison(feedback) { browserCompare = false; browserSettings = true }
        }
    if (menu) AlertDialog(onDismissRequest = { menu = false }, title = { Text("Infinite play") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            TextButton(onClick = {
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("Puzzle code", state.code))
                message = "Code copied."
            }) { Text(state.code.ifEmpty { "Preparing puzzle…" } + " · Copy") }
            Text("Challenge uses less spare space. Difficulty is still experimental.")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Challenge")
                Switch(challenge, { challenge = it; preferences.edit().putBoolean("challenge", it).apply() }, modifier = Modifier.semantics { contentDescription = "Use challenge puzzles" })
            }
            TextButton(onClick = { endless.next(challenge); menu = false }, enabled = !state.busy) { Text("Skip / new puzzle") }
            TextButton(onClick = { enter = true; menu = false }) { Text("Enter puzzle code") }
            TextButton(onClick = { bookmark = true; menu = false }, enabled = state.game != null) { Text("Save example") }
            TextButton(onClick = { scope.launch { saved = withContext(Dispatchers.IO) { endless.examples().lines().filter { it.isNotBlank() } } }; menu = false }) { Text("Saved examples") }
            TextButton(onClick = { export.launch("ball-sort-examples.ndjson"); menu = false }) { Text("Export saved examples") }
        }
    }, confirmButton = { TextButton(onClick = { menu = false }) { Text("Done") } })
    saved?.let { examples ->
        AlertDialog(onDismissRequest = { saved = null }, title = { Text("Saved examples") },
            text = { androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 400.dp)) {
                if (examples.isEmpty()) item { Text("No examples saved yet.") }
                items(examples.size) { index ->
                    val value = org.json.JSONObject(examples[index])
                    TextButton(onClick = { endless.loadSaved(examples[index]); saved = null }) {
                        Text(value.optString("code") + " · " + value.optString("quality") + "\n" + value.optString("note"))
                    }
                }
            } }, confirmButton = { TextButton(onClick = { saved = null }) { Text("Done") } })
    }
    if (enter) AlertDialog(onDismissRequest = { enter = false }, title = { Text("Replay a code") },
        text = { OutlinedTextField(code, { code = it }, label = { Text("E1-B-seed or E1-C-seed") }) },
        confirmButton = { TextButton(onClick = { endless.next(challenge, code); enter = false }) { Text("Load") } },
        dismissButton = { TextButton(onClick = { enter = false }) { Text("Cancel") } })
    if (bookmark) AlertDialog(onDismissRequest = { bookmark = false }, title = { Text("Save this example") },
        text = { Column { Text("Hard can be good. Describe what worked or felt wrong."); OutlinedTextField(note, { note = it }, label = { Text("Optional note") }) } },
        confirmButton = { TextButton(onClick = { endless.bookmark("good", note) { message = it }; bookmark = false; note = "" }) { Text("Good example") } },
        dismissButton = { TextButton(onClick = { endless.bookmark("needs-work", note) { message = it }; bookmark = false; note = "" }) { Text("Needs work") } })
    }
}
