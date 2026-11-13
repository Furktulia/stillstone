package com.example.ballsortgame.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.ballsortgame.game.GameUiState
import com.example.ballsortgame.game.PlayableLevel
import com.example.ballsortgame.ui.theme.GameDisplayFont

private val Columns = floatArrayOf(.21f, .50f, .79f)

/** Where the [place]-th stone of [row] stands: rows run left to right, then back, like a path winding downhill. */
private fun columnOf(row: Int, place: Int) = if (row % 2 == 0) place else 2 - place

/** The path dips and rises a little so it never reads as a grid. */
private fun liftOf(row: Int, column: Int, rowHeight: Float): Float =
    rowHeight * (if (column == 1) (if (row % 2 == 0) -.10f else .10f) else 0f) +
        rowHeight * .035f * (((row * 7 + column * 3) % 5) - 2) / 2f

/** The light that drifts over each world's path: fireflies at dusk, pale motes by the lake, stars at midnight. */
private fun moteColor(world: String) = when (world) {
    "four" -> Color(0xFFD6F0FF)
    "five" -> Color(0xFFC9D4FF)
    else -> Color(0xFFFFE2A8)
}

/**
 * A world's levels as stones along one winding path through its painting. Stones that are
 * done glow, and the trail between two finished stones is lit, so progress reads as a path
 * of lights growing through the dusk. Nothing here animates while idle.
 */
@Composable
internal fun LevelPath(world: String, name: String, entries: List<IndexedValue<PlayableLevel>>, state: GameUiState,
    listState: LazyListState, safe: PaddingValues, pageWidth: Dp, compact: Boolean, choose: (Int) -> Unit) {
    val look = worldLook(world)
    val rows = remember(entries) { entries.chunked(3) }
    val rowHeight = if (compact) 94.dp else 112.dp
    val trackWidth = pageWidth.coerceAtMost(440.dp)
    val trackLeft = (pageWidth - trackWidth) / 2
    val done = state.completed
    val playingRow = remember(entries, state.levelIndex) { rows.indexOfFirst { row -> row.any { it.index == state.levelIndex } } }
    // Open where the player is, with a little of the path behind them still in view.
    LaunchedEffect(world) {
        if (playingRow >= 4 && listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0)
            listState.scrollToItem(playingRow - 1)
    }
    LazyColumn(state = listState,
        modifier = Modifier.fillMaxSize().testTag("level_list")
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val edge = 36.dp.toPx().coerceAtMost(size.height / 4)
                // Stones begin to thin out beside the floating buttons and are gone only at the screen edge.
                val away = if (listState.firstVisibleItemIndex > 0) 1f else (listState.firstVisibleItemScrollOffset / 90.dp.toPx()).coerceIn(0f, 1f)
                val reach = safe.calculateTopPadding().toPx() + 136.dp.toPx()
                if (away > 0f) drawRect(Brush.verticalGradient(0f to Color.Black.copy(alpha = 1f - away), 1f to Color.Black, endY = reach),
                    size = Size(size.width, reach), blendMode = BlendMode.DstIn)
                if (listState.canScrollForward) drawRect(
                    Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = size.height - edge, endY = size.height),
                    topLeft = Offset(0f, size.height - edge), size = Size(size.width, edge), blendMode = BlendMode.DstIn)
            },
        contentPadding = PaddingValues(top = safe.calculateTopPadding() + 72.dp, bottom = safe.calculateBottomPadding() + 56.dp)) {
        item(key = "world_title") {
            Column(Modifier.fillMaxWidth().padding(top = if (compact) 8.dp else 28.dp, bottom = if (compact) 6.dp else 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(name, fontFamily = GameDisplayFont,
                    style = if (compact) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge)
                Text("${entries.count { it.value.id in done }} of ${entries.size}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
        }
        items(rows.size, key = { rows[it].first().value.id }) { r ->
            val row = rows[r]
            val before = rows.getOrNull(r - 1)?.last()
            val after = rows.getOrNull(r + 1)?.first()
            Box(Modifier.fillMaxWidth().height(rowHeight).drawBehind {
                val left = trackLeft.toPx(); val wide = trackWidth.toPx(); val h = size.height
                fun at(place: Int): Offset {
                    val column = columnOf(r, place)
                    return Offset(left + wide * Columns[column], h / 2 + liftOf(r, column, h))
                }
                val motes = java.util.Random((world.hashCode() * 31 + r).toLong())
                val mote = moteColor(world)
                repeat(2) {
                    val p = Offset(motes.nextFloat() * size.width, motes.nextFloat() * h)
                    val bright = .25f + motes.nextFloat() * .55f
                    drawCircle(Brush.radialGradient(listOf(mote.copy(alpha = .16f * bright), Color.Transparent), p, 9.dp.toPx()), 9.dp.toPx(), p)
                    drawCircle(mote.copy(alpha = bright), (.7f + motes.nextFloat() * .7f).dp.toPx(), p)
                }
                val points = ArrayList<Offset>(); val lit = ArrayList<Boolean>()
                if (before != null) { points += Offset(at(0).x, 0f); lit += before.value.id in done && row.first().value.id in done }
                for (place in row.indices) {
                    points += at(place)
                    if (place < row.lastIndex) lit += row[place].value.id in done && row[place + 1].value.id in done
                }
                if (after != null) { lit += row.last().value.id in done && after.value.id in done; points += Offset(at(row.lastIndex).x, h) }
                for (i in 0 until points.size - 1) {
                    val a = points[i]; val b = points[i + 1]
                    val leg = Path().apply {
                        moveTo(a.x, a.y)
                        if (a.x == b.x) lineTo(b.x, b.y)
                        else cubicTo(a.x + (b.x - a.x) * .4f, a.y, b.x - (b.x - a.x) * .4f, b.y, b.x, b.y)
                    }
                    // A few small stones mark the way; lit ones are brighter, never large enough to touch.
                    val measure = PathMeasure().apply { setPath(leg, false) }
                    val step = 17.dp.toPx()
                    val fromTop = before != null && i == 0
                    val toBottom = after != null && i == points.size - 2
                    val keepX = 46.dp.toPx(); val keepY = 40.dp.toPx()
                    fun clear(p: Offset, stone: Offset) = ((p.x - stone.x) / keepX).let { it * it } + ((p.y - stone.y) / keepY).let { it * it } > 1f
                    var k = 0
                    while (true) {
                        val offsets = when {
                            fromTop -> listOf((k + .5f) * step)
                            toBottom -> listOf(measure.length - (k + .5f) * step)
                            else -> listOf(measure.length / 2 - (k + .5f) * step, measure.length / 2 + (k + .5f) * step)
                        }
                        if (offsets.all { it < 0f || it > measure.length }) break
                        for (d in offsets) if (d in 0f..measure.length) {
                            val p = measure.getPosition(d)
                            if ((fromTop || clear(p, a)) && (toBottom || clear(p, b)))
                                drawCircle(if (lit[i]) lerp(look.accent, Color.White, .4f).copy(alpha = .9f) else look.light.copy(alpha = .34f),
                                    (if (lit[i]) 1.7f else 1.3f).dp.toPx(), p)
                        }
                        k++
                    }
                }
            }) {
                for (place in row.indices) {
                    val entry = row[place]
                    val column = columnOf(r, place)
                    val local = r * 3 + place + 1
                    val hit = 92.dp
                    PathStone(local, world, entry.index == state.levelIndex, entry.value.id in done, compact,
                        Modifier.offset(x = trackLeft + trackWidth * Columns[column] - hit / 2,
                            y = with(androidx.compose.ui.platform.LocalDensity.current) { liftOf(r, column, rowHeight.toPx()).toDp() })
                            .size(hit, rowHeight).testTag("level_${entry.index}"),
                        traitsOf(entry.value).firstOrNull { it != LevelTrait.CHALLENGE }, entry.value.challenge) { choose(entry.index) }
                }
            }
        }
    }
}

private fun DrawScope.stoneOutline(w: Float, h: Float, bevel: Float) = Path().apply {
    moveTo(w * .28f, h * .09f)
    cubicTo(w * .48f, h * .05f, w * .75f, h * .07f, w * .84f, h * bevel)
    cubicTo(w * .96f, h * .40f, w * .96f, h * .69f, w * .81f, h * .85f)
    cubicTo(w * .64f, h * .95f, w * .28f, h * .96f, w * .14f, h * .78f)
    cubicTo(w * .05f, h * .60f, w * .07f, h * .27f, w * .28f, h * .09f)
    close()
}

/**
 * One level: a painted stone on the path. Its number is carved in the middle with the mode
 * mark beneath it. A finished stone is lit from within; the one being played is ringed with
 * light; a challenge wears a warm rim and a small peak that stands clear above it.
 */
@Composable
private fun PathStone(number: Int, world: String, playing: Boolean, completed: Boolean, compact: Boolean, modifier: Modifier,
    trait: LevelTrait?, challenge: Boolean, open: () -> Unit) {
    val look = worldLook(world)
    val texture = rememberArtwork("stone-button-v1")
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(modifier.semantics(mergeDescendants = true) {
        contentDescription = "Level $number" + (trait?.let { ", ${it.title}" } ?: "") + if (challenge) ", challenge" else ""
        selected = playing
        stateDescription = if (completed) "Completed" else if (playing) "Playing" else "Available"
    }.clickable(interactionSource = source, indication = null, role = Role.Button, onClick = open),
        contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(width = if (compact) 70.dp else 76.dp, height = if (compact) 58.dp else 64.dp).pressSink(pressed, .92f)) {
            val w = size.width; val h = size.height
            val centre = Offset(w / 2, h / 2)
            val ember = lerp(look.accent, Color(0xFFF0C58A), .7f)
            // Light on the ground around a stone that is finished or being played.
            if (completed || playing) drawCircle(Brush.radialGradient(
                listOf((if (playing) look.light else look.accent).copy(alpha = if (playing) .34f else .28f), Color.Transparent), centre, w * .95f), w * .95f, centre)
            // A soft shadow so the stone sits on the path instead of floating over it.
            drawOval(Color.Black.copy(alpha = .28f), Offset(w * .10f, h * .62f), Size(w * .80f, h * .42f))
            val p = stoneOutline(w, h, if (world == "five") .16f else .24f)
            val lift = if (completed) .12f else 0f
            drawPath(p, Brush.linearGradient(listOf(
                lerp(look.base, look.light, .27f + lift),
                lerp(look.base, look.light, .12f + lift), lerp(look.base, Color.Black, .16f))))
            if (texture != null) clipPath(p) {
                drawImage(texture, srcOffset = IntOffset(texture.width / 3, 0), srcSize = IntSize(texture.width / 3, texture.height),
                    dstSize = IntSize(w.toInt(), h.toInt()), alpha = if (completed) 1f else .80f)
            }
            drawPath(p, when {
                challenge -> ember.copy(alpha = .85f)
                playing || pressed -> look.light.copy(alpha = .92f)
                else -> look.accent.copy(alpha = if (completed) .50f else .28f)
            }, style = Stroke(if (playing || challenge) 1.6.dp.toPx() else .8.dp.toPx()))
            if (playing && challenge) drawPath(p, look.light.copy(alpha = .55f), style = Stroke(.8.dp.toPx()))
            val edgeLight = Path().apply {
                moveTo(w * .15f, h * .44f)
                cubicTo(w * .15f, h * .17f, w * .44f, h * .09f, w * .67f, h * .14f)
            }
            drawPath(edgeLight, look.light.copy(alpha = .16f), style = Stroke(.8.dp.toPx(), cap = StrokeCap.Round))
            // What kind of level this is, carved under the number.
            if (trait != null) drawTraitMark(trait, Offset(w * .50f, h * .735f), 13.dp.toPx(), look.light.copy(alpha = .86f))
            // The peak stands on top of the rim in its own small plate, so the rim never cuts through it.
            if (challenge) {
                val plate = Size(24.dp.toPx(), 15.dp.toPx())
                val corner = Offset(w * .50f - plate.width / 2, h * .075f - plate.height / 2)
                drawRoundRect(lerp(look.base, look.light, .10f), corner, plate, CornerRadius(plate.height / 2))
                drawRoundRect(ember.copy(alpha = .85f), corner, plate, CornerRadius(plate.height / 2), style = Stroke(1.2.dp.toPx()))
                drawTraitMark(LevelTrait.CHALLENGE, Offset(w * .50f, h * .075f), 9.dp.toPx(), ember)
            }
            if (completed) {
                val tick = Path().apply { moveTo(w * .69f, h * .76f); lineTo(w * .745f, h * .82f); lineTo(w * .84f, h * .69f) }
                drawPath(tick, lerp(look.accent, Color.White, .3f), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        Text(number.toString(), Modifier.offset(y = if (trait != null) (-8).dp else 0.dp).pressSink(pressed, .92f),
            style = MaterialTheme.typography.titleLarge, fontFamily = GameDisplayFont, color = look.light)
    }
}
