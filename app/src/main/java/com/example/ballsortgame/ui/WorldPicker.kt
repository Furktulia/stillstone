package com.example.ballsortgame.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import com.example.ballsortgame.ui.theme.GameDisplayFont
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ballsortgame.game.GameUiState
import com.example.ballsortgame.game.PlayableLevel

@Composable
fun WorldPicker(levels: List<PlayableLevel>, state: GameUiState, onDismiss: () -> Unit, choose: (Int) -> Unit,
    initialWorld: String? = null, onInfinite: (() -> Unit)? = null, onSettings: (() -> Unit)? = null,
    /** What the system Back does on the world screen itself. The main screen is the top: Back leaves the app. */
    onBack: (() -> Unit)? = null) {
    var selectedWorld by rememberSaveable { mutableStateOf(initialWorld) }
    val worlds = remember(levels) { levels.withIndex().groupBy { it.value.worldId } }
    val entries = worlds[selectedWorld]
    val id = selectedWorld ?: "three"
    val listState = rememberLazyListState()
    val listStates = worlds.keys.associateWith { rememberLazyListState() }
    // Back always goes up: level list -> worlds -> out. It never returns to an earlier screen;
    // the Resume stone is the way back into the puzzle.
    Dialog(onDismissRequest = { if (selectedWorld != null) selectedWorld = null else (onBack ?: onDismiss)() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        WorldTheme(id) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val pageHeight = maxHeight
                val pageWidth = maxWidth
                val compact = pageHeight < 740.dp
                if (entries != null) WorldBackdrop(id, selection = true, modifier = Modifier.matchParentSize())
                val safe = WindowInsets.safeDrawing.asPaddingValues()
                Column(Modifier.fillMaxSize()) {
                    if (entries == null) {
                        // One continuous journey: each world is a stretch of sky and painted
                        // landscape, and the mist under one is the sky of the next. A little
                        // over two fit on a screen, so the third shows there is more below.
                        val all = worlds.entries.toList()
                        val stretch = (pageHeight * .46f).coerceAtLeast(300.dp)
                        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("world_list"), state = listState) {
                            all.forEachIndexed { index, entry ->
                                item(key = entry.key) {
                                    WorldVistaSection(entry.key, entry.value.first().value.collection,
                                        "${entry.value.count { it.value.id in state.completed }} of ${entry.value.size}",
                                        stretch, all.getOrNull(index + 1)?.key ?: if (onInfinite != null) "endless" else null) { selectedWorld = entry.key }
                                }
                            }
                            if (onInfinite != null) item(key = "endless") {
                                WorldVistaSection("endless", "Infinite", "Endless puzzles", stretch + safe.calculateBottomPadding(), null, open = onInfinite)
                            }
                        }
                    } else {
                        val world = entries.first().value
                        // The levels wind down through the world's painting as stones on one path.
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            LevelPath(id, world.collection, entries, state, listStates.getValue(id), safe, pageWidth, compact, choose)
                        }
                    }
                }
                if (entries != null) Row(Modifier.fillMaxWidth().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    NightButton("‹ Worlds", Modifier.testTag("world_back")) { selectedWorld = null }
                    SettingsButton(onClick = { onSettings?.invoke() }, modifier = Modifier.testTag("browser_settings"))
                }
                // The way back to the puzzle sits where Back sits on every other screen.
                if (entries == null) Row(Modifier.fillMaxWidth().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    NightButton("Resume ›", Modifier.testTag("world_close"), onClick = onDismiss)
                    SettingsButton(onClick = { onSettings?.invoke() }, modifier = Modifier.testTag("browser_settings"))
                }
            }
        }
    }
}


