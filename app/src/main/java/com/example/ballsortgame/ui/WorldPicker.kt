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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import com.example.ballsortgame.ui.theme.GameDisplayFont
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ballsortgame.game.GameUiState
import com.example.ballsortgame.game.PlayableLevel

@Composable
fun WorldPicker(levels: List<PlayableLevel>, state: GameUiState, onDismiss: () -> Unit, choose: (Int) -> Unit,
    initialWorld: String? = null, onInfinite: (() -> Unit)? = null, onSettings: (() -> Unit)? = null) {
    var selectedWorld by rememberSaveable { mutableStateOf(initialWorld) }
    val worlds = remember(levels) { levels.withIndex().groupBy { it.value.worldId } }
    val entries = worlds[selectedWorld]
    val id = selectedWorld ?: "three"
    val listState = rememberLazyListState()
    val gridStates = worlds.keys.associateWith { rememberLazyGridState() }
    val lights = remember { mutableStateMapOf<String, WorldLight>() }
    var fogScroll by remember { mutableFloatStateOf(0f) }
    val fogConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                fogScroll += consumed.y
                return Offset.Zero
            }
        }
    }
    Dialog(onDismissRequest = { if (selectedWorld != null) selectedWorld = null else onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        WorldTheme(id) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val pageHeight = maxHeight
                val pageWidth = maxWidth
                val compact = pageHeight < 740.dp
                if (entries != null) WorldBackdrop(id, selection = true, modifier = Modifier.matchParentSize())
                else WorldBrowserBackdrop(Modifier.matchParentSize(), lights.values.toList(),
                    scrollOffset = { if (state.reducedMotion) 0f else fogScroll })
                val safe = WindowInsets.safeDrawing.asPaddingValues()
                Column(if (entries == null) Modifier.fillMaxSize() else
                    Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    if (entries != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        NightButton("‹ Worlds", Modifier.testTag("world_back")) { selectedWorld = null }
                        SettingsButton(onClick = { onSettings?.invoke() }, modifier = Modifier.testTag("browser_settings"))
                    }
                    if (entries == null) {
                        val all = worlds.entries.toList()
                        val badgeWidth = (pageWidth * .58f).coerceAtMost(420.dp)
                        LazyColumn(Modifier.weight(1f).fillMaxWidth().nestedScroll(fogConnection).testTag("world_list")
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                val edge = 28.dp.toPx().coerceAtMost(size.height / 4)
                                if (listState.canScrollBackward) drawRect(
                                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black), endY = edge),
                                    size = androidx.compose.ui.geometry.Size(size.width, edge), blendMode = BlendMode.DstIn)
                                if (listState.canScrollForward) drawRect(
                                    Brush.verticalGradient(listOf(Color.Black, Color.Transparent),
                                        startY = size.height - edge, endY = size.height),
                                    topLeft = Offset(0f, size.height - edge),
                                    size = androidx.compose.ui.geometry.Size(size.width, edge), blendMode = BlendMode.DstIn)
                            },
                            state = listState, contentPadding = PaddingValues(
                                start = 20.dp, end = 20.dp, top = safe.calculateTopPadding() + 64.dp,
                                bottom = safe.calculateBottomPadding() + 24.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            item(key = "resume") {
                                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Spacer(Modifier.height((pageHeight * .14f).coerceIn(44.dp, 120.dp)))
                                    NightButton("Resume ›", Modifier.fillMaxWidth(.88f).testTag("world_close")
                                        .onGloballyPositioned { coordinates ->
                                            lights["resume"] = WorldLight(Rect(coordinates.positionInRoot(), coordinates.size.toSize()), worldLook("three").accent)
                                        }, large = true, onClick = onDismiss)
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                            all.forEachIndexed { index, entry ->
                                item(key = entry.key) {
                                    Box(Modifier.fillMaxWidth(), contentAlignment = if (index % 2 == 0) Alignment.CenterStart else Alignment.CenterEnd) {
                                        WorldEntry(entry.key, entry.value.first().value.collection,
                                            "${entry.value.count { it.value.id in state.completed }} of ${entry.value.size} sorted. All levels available.",
                                            Modifier.width(badgeWidth), lights) { selectedWorld = entry.key }
                                    }
                                }
                            }
                            if (onInfinite != null) item(key = "endless") {
                                Box(Modifier.fillMaxWidth(), contentAlignment = if (all.size % 2 == 0) Alignment.CenterStart else Alignment.CenterEnd) {
                                    WorldEntry("endless", "Infinite", "Continue an endless puzzle. Play offline.",
                                        Modifier.width(badgeWidth), lights, onInfinite)
                                }
                            }
                        }
                    } else {
                        val world = entries.first().value
                        Spacer(Modifier.height((pageHeight * if (compact) .04f else .07f).coerceAtMost(60.dp)))
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(world.collection, fontFamily = GameDisplayFont,
                                style = if (compact) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge)
                            Text("${entries.size} puzzles · play freely", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, bottom = if (compact) 12.dp else 22.dp))
                        }
                        LazyVerticalGrid(columns = GridCells.Adaptive(82.dp),
                            state = gridStates.getValue(id),
                            modifier = Modifier.weight(1f).widthIn(max = 340.dp)
                                .fillMaxWidth().align(Alignment.CenterHorizontally).testTag("level_list"),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(entries, key = { _, entry -> entry.value.id }) { local, entry ->
                                LevelStone(local + 1, id, entry.index == state.levelIndex,
                                    entry.value.id in state.completed, compact, Modifier.testTag("level_${entry.index}")) { choose(entry.index) }
                            }
                        }
                        Spacer(Modifier.height((pageHeight * if (compact) .18f else .23f).coerceIn(90.dp, 210.dp)))
                    }
                }
                // The list fills the screen; the gear floats above it without defining a clip edge.
                if (entries == null) SettingsButton(onClick = { onSettings?.invoke() },
                    modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding()
                        .padding(horizontal = 20.dp, vertical = 8.dp).testTag("browser_settings"))
            }
        }
    }
}

@Composable
private fun WorldEntry(id: String, name: String, detail: String, modifier: Modifier,
    lights: MutableMap<String, WorldLight>, open: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val art = rememberArtwork(when (id) { "five" -> "midnight-depth-v5"; "endless" -> "infinite-entry-v1"; else -> worldLook(id).art + "-depth-v2" })
    val ratio = art?.let { it.width.toFloat() / it.height } ?: 1f
    DisposableEffect(id) { onDispose { lights.remove(id) } }
    Box(modifier.aspectRatio(ratio).testTag("world_$id").semantics(mergeDescendants = true) {
        contentDescription = "$name. $detail"
        role = Role.Button
    }.onGloballyPositioned { coordinates ->
        val color = when (id) {
            "four" -> Color(0xFF75D6E6)
            "five" -> Color(0xFF91A2FF)
            "endless" -> Color(0xFFD0B2FF)
            else -> Color(0xFFD69BFA)
        }
        val light = WorldLight(Rect(coordinates.positionInRoot(), coordinates.size.toSize()), color, cloud = true)
        if (lights[id] != light) lights[id] = light
    }.clickable(interactionSource = source, indication = null, onClick = open),
        contentAlignment = Alignment.Center) {
        WorldEntrance(id, Modifier.fillMaxSize(), pressed)
    }
}

@Composable
private fun LevelStone(number: Int, world: String, playing: Boolean, completed: Boolean, compact: Boolean, modifier: Modifier, open: () -> Unit) {
    val look = worldLook(world)
    val texture = rememberArtwork("stone-button-v1")
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(modifier.fillMaxWidth().height(if (compact) 68.dp else 76.dp).semantics(mergeDescendants = true) {
        contentDescription = "Level $number"
        selected = playing
        stateDescription = if (completed) "Sorted" else if (playing) "Playing" else "Available"
    }.clickable(interactionSource = source, indication = null, role = Role.Button, onClick = open),
        contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(width = if (compact) 70.dp else 76.dp, height = if (compact) 58.dp else 64.dp)) {
            val w = size.width; val h = size.height
            val bevel = if (world == "five") .16f else .24f
            val p = Path().apply {
                moveTo(w * .28f, h * .09f)
                cubicTo(w * .48f, h * .05f, w * .75f, h * .07f, w * .84f, h * bevel)
                cubicTo(w * .96f, h * .40f, w * .96f, h * .69f, w * .81f, h * .85f)
                cubicTo(w * .64f, h * .95f, w * .28f, h * .96f, w * .14f, h * .78f)
                cubicTo(w * .05f, h * .60f, w * .07f, h * .27f, w * .28f, h * .09f)
                close()
            }
            drawPath(p, Brush.linearGradient(listOf(
                lerp(look.base, look.light, if (pressed) .36f else .24f),
                lerp(look.base, look.light, .10f), lerp(look.base, Color.Black, .20f))))
            if (texture != null) clipPath(p) {
                drawImage(texture, srcOffset = androidx.compose.ui.unit.IntOffset(texture.width / 3, 0),
                    srcSize = androidx.compose.ui.unit.IntSize(texture.width / 3, texture.height),
                    dstSize = androidx.compose.ui.unit.IntSize(w.toInt(), h.toInt()), alpha = .85f)
            }
            drawPath(p, look.accent.copy(alpha = if (playing || pressed) .88f else .30f), style = Stroke(if (playing) 1.5.dp.toPx() else .7.dp.toPx()))
            val edgeLight = Path().apply {
                moveTo(w * .15f, h * .44f)
                cubicTo(w * .15f, h * .17f, w * .44f, h * .09f, w * .67f, h * .14f)
            }
            drawPath(edgeLight, look.light.copy(alpha = .12f), style = Stroke(.7.dp.toPx(), cap = StrokeCap.Round))
            if (completed) {
                val tick = Path().apply { moveTo(w * .63f, h * .75f); lineTo(w * .70f, h * .81f); lineTo(w * .82f, h * .67f) }
                drawPath(tick, look.accent, style = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round))
            }
            if (playing) drawCircle(look.light, 1.5.dp.toPx(), Offset(w * .50f, h * .84f))
        }
        Text(number.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Normal,
            fontFamily = GameDisplayFont, color = look.light)
    }
}

