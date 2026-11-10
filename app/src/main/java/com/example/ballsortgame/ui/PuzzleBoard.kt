package com.example.ballsortgame.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import com.example.ballsortgame.game.GameUiState

val PieceColors = listOf(Color(0xFFEB9791), Color(0xFF86CDB6), Color(0xFFE7C36D), Color(0xFF8CB7E8), Color(0xFFB7A0DE))
val PieceNames = listOf("coral", "mint", "gold", "blue", "lilac")

/**
 * Stone travel is part of play, not decoration. Android's "remove animations" and some
 * power-saving modes set the system animation scale to zero, which would make the
 * approach finish instantly. The game's own Reduced motion setting is the control.
 */
private object FullMotion : androidx.compose.ui.MotionDurationScale {
    override val scaleFactor: Float get() = 1f
}

private class GroupFlight {
    val progress = Animatable(0f)
    var started by mutableStateOf(false)
    var landed by mutableStateOf(false)
    /** Height above rest when the group left a tube that was still settling. */
    var lift = 0f
    val phase get() = when {
        landed -> FlightPhase.LANDED
        started -> FlightPhase.FLYING
        else -> FlightPhase.WAITING
    }
}

@Composable
fun PuzzleBoard(state: GameUiState, onTap: (Int) -> Unit, onTransferFinished: (Long) -> Unit, modifier: Modifier = Modifier, onImpact: (LandingImpact) -> Unit = {}) {
    val density = LocalDensity.current.density
    val look = LocalWorldLook.current
    val groups = state.transfers
    val runtimes = remember { mutableMapOf<Long, GroupFlight>() }
    runtimes.keys.retainAll(groups.map { it.id }.toSet())
    val flights = groups.map { runtimes.getOrPut(it.id) { GroupFlight() } }
    val frames = groups.mapIndexed { index, group -> GroupFrame(group, flights[index].phase) }
    val impactCallback by rememberUpdatedState(onImpact)
    val selectionLift by animateFloatAsState(if (state.selected != null) 1f else 0f,
        tween(if (state.reducedMotion) 0 else 150), label = "selection")
    BoxWithConstraints(modifier) {
        val width = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val height = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val capacities = state.board.tubes.map { it.capacity }
        val layout = remember(capacities, width, height, density) { boardGeometry(capacities, width, height, density) }
        val routes = groups.map { group ->
            key(group.id) {
                remember(group.id, layout) {
                    stoneRoute(layout, group.move.source, group.move.destination,
                        group.before.tubes[group.move.source].balls.size - group.count,
                        group.before.tubes[group.move.destination].balls.size, width, height, density)
                        ?.let { GroupRoute(it, group.count, layout.first().slot) }
                }
            }
        }
        val drops = routes.map { it?.fallHeight ?: 0f }
        val releaseFractions = routes.map { it?.releaseFraction ?: 0f }
        // Standing stones keep their own motion for as long as they stay in a tube, so
        // settling outlives the group that caused it and reacts to later arrivals.
        val settling = remember(layout) {
            SettlingColumns(layout.size, layout.first().slot) { tube, slot -> reboundClearance(layout, tube, slot, density) }
        }
        var settlingFrame by remember { mutableIntStateOf(0) }
        var settlingRunning by remember(settling) { mutableStateOf(false) }
        var settledEpoch by remember { mutableIntStateOf(0) }
        val stationary = stationaryPieces(frames, state.board)
        stationary.forEachIndexed { tube, stones -> settling.sync(tube, stones.size) }
        LaunchedEffect(settling, settlingRunning) {
            if (!settlingRunning) return@LaunchedEffect
            var last = withFrameNanos { it }
            while (settling.awake) {
                val now = withFrameNanos { it }
                val moving = settling.awakeCount
                settling.step((now - last) / 1_000_000_000f)
                last = now
                settlingFrame++
                if (settling.awakeCount != moving) settledEpoch++ // A tube came to rest: its group can retire.
            }
            settlingRunning = false
            settledEpoch++
        }
        groups.forEachIndexed { index, group ->
            key(group.id) {
                val flight = flights[index]
                val ready = canStartFlight(group, frames.take(index))
                val currentRoutes by rememberUpdatedState(routes[index])
                LaunchedEffect(group.id, ready) {
                    if (ready && !flight.started) {
                        val route = currentRoutes
                        val source = group.move.source
                        flight.lift = settling.offset(source, settling.size(source) - group.count)
                        flight.started = true
                        if (route != null) {
                            val slot = layout.first().slot
                            val longest = route.length / slot
                            val approachMs = (580 + longest * releaseFractions[index] * 12).toInt().coerceIn(600, 900)
                            val fallMs = (135 * kotlin.math.sqrt(drops[index] / slot)).toInt().coerceIn(120, 520)
                            kotlinx.coroutines.withContext(FullMotion) {
                                flight.progress.animateTo(LandingMotion.release, tween(approachMs, easing = FastOutSlowInEasing))
                            }
                            // The fall is stepped by hand so that contact happens wherever the
                            // destination's top stone actually is, even while it is still bouncing.
                            val destination = group.move.destination
                            val release = releaseFractions[index]
                            var elapsed = 0f
                            var last = withFrameNanos { it }
                            var height = 0f
                            while (true) {
                                val now = withFrameNanos { it }
                                elapsed += (now - last) / 1_000_000f
                                last = now
                                val progress = LandingMotion.release +
                                    (LandingMotion.contact - LandingMotion.release) * (elapsed / fallMs).coerceAtMost(1f)
                                flight.progress.snapTo(progress)
                                height = route.length * (1 - LandingMotion.distance(progress, release))
                                if (progress >= LandingMotion.contact || height <= settling.topOffset(destination)) break
                            }
                            val rebound = LandingMotion.reboundHeight(drops[index], slot, group.count)
                            settling.land(destination, group.count, height, SettlingColumns.impactSpeed(rebound, slot))
                            settlingRunning = true
                            flight.landed = true
                            impactCallback(LandingImpact(group.count, drops[index] / slot))
                        } else flight.landed = true
                    }
                }
            }
        }
        // Retire only the completed prefix: later independent moves may finish first.
        val head = groups.firstOrNull()
        val headDone = settledEpoch >= 0 && head != null && flights.first().landed &&
            !settling.awake(head.move.destination)
        LaunchedEffect(head?.id, headDone) {
            if (head != null && headDone) onTransferFinished(head.id)
        }
        val numberPaint = remember {
            Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT }
        }
        Canvas(Modifier.fillMaxSize().testTag("board_motion").semantics {
            stateDescription = "${frames.count { it.phase == FlightPhase.FLYING }} moving groups"
        }) {
            settlingFrame // Redraw while stones are settling.
            state.board.tubes.forEachIndexed { index, tube ->
                val geometry = layout[index]
                val left = geometry.centerX - geometry.width / 2
                val right = geometry.centerX + geometry.width / 2
                val radius = geometry.width * 0.35f
                val outline = when {
                    state.hint?.destination == index -> Color(0xFF9DD8BF)
                    state.selected == index -> look.accent
                    stationary[index].size == tube.capacity && stationary[index].distinct().size == 1 -> Color(0xFF8CA99F)
                    else -> look.tube
                }
                val path = Path().apply {
                    moveTo(left, geometry.top)
                    lineTo(left, geometry.bottom - radius)
                    quadraticTo(left, geometry.bottom, left + radius, geometry.bottom)
                    lineTo(right - radius, geometry.bottom)
                    quadraticTo(right, geometry.bottom, right, geometry.bottom - radius)
                    lineTo(right, geometry.top)
                }
                drawPath(path, look.light.copy(alpha = 0.025f))
                drawPath(path, outline, style = Stroke(if (state.selected == index || state.hint?.destination == index) 2.2f * density else 1.4f * density))

                stationary[index].forEachIndexed { pieceIndex, color ->
                    val lifted = groups.isEmpty() && state.selected == index && pieceIndex >= tube.balls.size - tube.topRun
                    val bounce = settling.offset(index, pieceIndex)
                    drawStone(
                        Offset(geometry.centerX, geometry.pieceY(pieceIndex, density) - bounce - if (lifted) 7f * density * selectionLift else 0f),
                        geometry.slot, color, state.colorLabels, numberPaint, look.light,
                    )
                }
                numberPaint.color = android.graphics.Color.rgb(168, 158, 184)
                numberPaint.textSize = 12f * density
                drawContext.canvas.nativeCanvas.drawText(
                    (index + 1).toString(), geometry.centerX, geometry.bottom + 17f * density, numberPaint,
                )
            }
            groups.forEachIndexed { index, group ->
                if (frames[index].phase == FlightPhase.FLYING) {
                    val color = requireNotNull(group.before.tubes[group.move.source].top)
                    val progress = flights[index].progress.value
                    val t = LandingMotion.distance(progress, releaseFractions[index])
                    val lift = flights[index].lift * (1 - (progress / (LandingMotion.release * 0.4f)).coerceIn(0f, 1f))
                    routes[index]?.at(t)?.forEach { point ->
                        drawStone(Offset(point.x, point.y - lift), layout[group.move.source].slot, color, state.colorLabels, numberPaint, look.light)
                    }
                }
            }
        }
        state.board.tubes.forEachIndexed { index, tube ->
            val position = layout[index]
            val description = if (tube.balls.isEmpty()) "Empty. Capacity ${tube.capacity}." else {
                "Capacity ${tube.capacity}. Bottom to top: " + tube.balls.joinToString(", ") { PieceNames.getOrElse(it) { "color ${it + 1}" } } +
                    if (tube.isComplete) ". Full and sorted." else "."
            }
            Box(
                Modifier
                    .offset(with(LocalDensity.current) { position.hitLeft.toDp() }, with(LocalDensity.current) { position.hitTop.toDp() })
                    .size(with(LocalDensity.current) { position.hitWidth.toDp() }, with(LocalDensity.current) { position.hitHeight.toDp() })
                    .testTag("tube_$index")
                    .semantics {
                        contentDescription = "Tube ${index + 1}"
                        stateDescription = description + when {
                            state.hint?.destination == index -> " Hint destination."
                            state.selected == index -> " Selected."
                            else -> ""
                        }
                        selected = state.selected == index
                    }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = !state.loading && !state.board.isSolved, role = Role.Button) { onTap(index) },
            )
        }
    }
}

private fun DrawScope.drawStone(center: Offset, slot: Float, colorId: Int, labels: Boolean, paint: Paint, light: Color) {
    val diameter = slot * 0.86f
    val color = lerp(PieceColors[colorId % PieceColors.size], light, 0.045f)
    drawOval(Color.Black.copy(alpha = 0.18f), topLeft = Offset(center.x - diameter * 0.43f, center.y + diameter * 0.34f), size = Size(diameter * 0.86f, diameter * 0.2f))
    translate(center.x - diameter / 2, center.y - diameter / 2) {
        drawRoundRect(
            brush = Brush.linearGradient(
                listOf(lerp(color, light, 0.18f), color, lerp(color, Color.Black, 0.32f)),
                start = Offset.Zero, end = Offset(diameter, diameter),
            ),
            size = Size(diameter, diameter), cornerRadius = CornerRadius(diameter * 0.46f),
        )
        drawRoundRect(
            Brush.radialGradient(listOf(light.copy(alpha = 0.12f), Color.Transparent),
                center = Offset(diameter * 0.28f, diameter * 0.18f), radius = diameter * 0.90f),
            topLeft = Offset.Zero,
            size = Size(diameter, diameter), cornerRadius = CornerRadius(diameter * 0.46f))
        repeat(4) { grain ->
            val x = diameter * (0.26f + grain * 0.115f)
            val y = diameter * (0.59f + ((grain + colorId) % 3) * 0.06f)
            drawLine(light.copy(alpha = 0.045f), Offset(x, y), Offset(x + diameter * 0.045f, y - diameter * 0.012f),
                strokeWidth = (diameter * 0.008f).coerceAtLeast(0.5f))
        }
        if (labels) {
            paint.textSize = diameter * 0.46f
            paint.color = android.graphics.Color.rgb(29, 22, 39)
            paint.typeface = Typeface.DEFAULT_BOLD
            val baseline = diameter / 2 - (paint.descent() + paint.ascent()) / 2
            drawContext.canvas.nativeCanvas.drawText((colorId + 1).toString(), diameter / 2, baseline, paint)
            paint.typeface = Typeface.DEFAULT
        }
    }
}
