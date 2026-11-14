package com.example.ballsortgame.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.*
import com.example.ballsortgame.ui.theme.GameDisplayFont
import androidx.compose.ui.unit.*
import kotlin.math.*

internal enum class NightSymbol { UNDO, RESTART, HINT }

/**
 * A stone gives under a finger: it sinks a little at once and springs back with a small
 * overshoot when let go. Shared by every stone-like control so they all feel the same.
 */
@Composable
internal fun Modifier.pressSink(pressed: Boolean, depth: Float = .945f): Modifier {
    val scale by animateFloatAsState(if (pressed) depth else 1f,
        if (pressed) androidx.compose.animation.core.spring(dampingRatio = 1f, stiffness = 1400f)
        else androidx.compose.animation.core.spring(dampingRatio = .38f, stiffness = 420f), label = "press")
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Native labels over a shared, horizontally stretchable stone surface. */
@Composable
internal fun NightButton(label: String, modifier: Modifier = Modifier, enabled: Boolean = true,
    symbol: NightSymbol? = null, large: Boolean = false, onClick: () -> Unit) {
    val art = rememberArtwork("stone-button-v1")
    val look = LocalWorldLook.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(modifier.heightIn(min = if (large) 78.dp else 52.dp).pressSink(pressed)
        .clickable(enabled = enabled, role = Role.Button, interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) { stoneSurface(art, enabled, pressed, look) }
        Column(Modifier.padding(horizontal = if (large) 24.dp else 14.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            if (symbol != null) {
                NightIcon(symbol, Modifier.size(18.dp), look.light.copy(alpha = if (enabled) 1f else .35f))
                Spacer(Modifier.height(5.dp))
            }
            val labelStyle = if (large) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium
            BasicText(label, style = labelStyle.copy(
                color = look.light.copy(alpha = if (enabled) 1f else .35f), fontFamily = GameDisplayFont),
                maxLines = 1, softWrap = false,
                autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = labelStyle.fontSize))
        }
    }
}

private fun DrawScope.stoneSurface(art: ImageBitmap?, enabled: Boolean, pressed: Boolean, look: WorldLook) {
    if (art == null) {
        drawRoundRect(look.tube.copy(alpha = .35f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()))
        return
    }
    val edge = (art.width * .18f).roundToInt()
    val edgeTo = minOf(edge * size.height / art.height, size.width * .28f).roundToInt().coerceAtLeast(1)
    val sx = listOf(0, edge, art.width - edge, art.width)
    val dx = listOf(0, edgeTo, size.width.roundToInt() - edgeTo, size.width.roundToInt())
    for (i in 0..2) {
        if (dx[i + 1] <= dx[i]) continue
        drawImage(art, srcOffset = IntOffset(sx[i], 0), srcSize = IntSize(sx[i + 1] - sx[i], art.height),
            dstOffset = IntOffset(dx[i], 0),
            dstSize = IntSize(dx[i + 1] - dx[i], size.height.roundToInt()),
            alpha = if (enabled) 1f else .42f,
            // Pressed in, a stone falls into its own shadow; it does not light up.
            colorFilter = if (pressed) ColorFilter.tint(Color.Black.copy(alpha = .16f), BlendMode.SrcAtop) else null)
    }
}

/** Same minimal gear and touch target on every screen. */
@Composable
internal fun SettingsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color = LocalWorldLook.current.light
    IconButton(onClick, modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = "Settings" }) {
        Canvas(Modifier.size(24.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension * .43f
            val p = Path()
            for (i in 0 until 32) {
                val angle = i * PI.toFloat() / 16f - PI.toFloat() / 2
                val r = radius * if (i % 4 == 0 || i % 4 == 3) .78f else 1f
                val point = center + Offset(cos(angle) * r, sin(angle) * r)
                if (i == 0) p.moveTo(point.x, point.y) else p.lineTo(point.x, point.y)
            }
            p.close()
            drawPath(p, color, style = Stroke(1.4.dp.toPx(), join = StrokeJoin.Round))
            drawCircle(color, size.minDimension * .14f, center, style = Stroke(1.4.dp.toPx()))
        }
    }
}

/**
 * A play control that stays out of the way: a thin outline icon, dimmer than the stones,
 * with no surface of its own. Its name sits underneath and is normally invisible; it shows
 * while [showName] is set (a player's first puzzles) and for a moment after a long press.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun QuietControl(symbol: NightSymbol, name: String, modifier: Modifier = Modifier, enabled: Boolean = true,
    showName: Boolean = false, onClick: () -> Unit) {
    val look = LocalWorldLook.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    var held by remember { mutableStateOf(false) }
    LaunchedEffect(held) { if (held) { kotlinx.coroutines.delay(1800); held = false } }
    val nameAlpha by animateFloatAsState(if (showName || held) 1f else 0f, tween(220), label = "control name")
    val tint by animateFloatAsState(if (!enabled) .22f else if (pressed) 1f else .62f, tween(120), label = "control tint")
    Column(modifier.heightIn(min = 56.dp)
        .semantics(mergeDescendants = true) { contentDescription = name }
        .combinedClickable(enabled = enabled, role = Role.Button, interactionSource = source, indication = null,
            onLongClickLabel = "Show name", onLongClick = { held = true }, onClick = onClick)
        .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        NightIcon(symbol, Modifier.size(24.dp), look.light.copy(alpha = tint))
        Spacer(Modifier.height(5.dp))
        // Always laid out so the row never jumps when a name appears.
        BasicText(name, Modifier.clearAndSetSemantics { }, style = MaterialTheme.typography.labelMedium.copy(
            color = look.light.copy(alpha = .78f * nameAlpha * if (enabled) 1f else .55f)), maxLines = 1, softWrap = false)
    }
}

/** The game's own dialog panel: night-coloured, softly lit from one corner, display-serif title. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NightDialog(onDismissRequest: () -> Unit, title: @Composable () -> Unit, text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit, dismissButton: (@Composable () -> Unit)? = null) {
    val look = LocalWorldLook.current
    androidx.compose.ui.window.Dialog(onDismissRequest) {
        val shape = androidx.compose.foundation.shape.RoundedCornerShape(26.dp)
        Column(Modifier.fillMaxWidth().widthIn(max = 420.dp).clip(shape)
            .background(Brush.verticalGradient(listOf(lerp(look.base, look.light, .15f), lerp(look.base, look.light, .07f))))
            .drawBehind {
                drawRect(Brush.radialGradient(listOf(look.accent.copy(alpha = .14f), Color.Transparent),
                    center = Offset(size.width * .82f, 0f), radius = size.width * .95f))
            }
            .border(1.dp, look.accent.copy(alpha = .28f), shape)
            .padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 12.dp)) {
            CompositionLocalProvider(LocalContentColor provides look.light) {
                ProvideTextStyle(MaterialTheme.typography.headlineSmall, title)
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.weight(1f, fill = false)) {
                CompositionLocalProvider(LocalContentColor provides lerp(look.light, look.tube, .22f)) {
                    ProvideTextStyle(MaterialTheme.typography.bodyMedium, text)
                }
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                itemVerticalAlignment = Alignment.CenterVertically) {
                dismissButton?.invoke()
                confirmButton()
            }
        }
    }
}

/** A dialog choice. The [primary] one is a small stone; the others are quiet lettering. */
@Composable
internal fun NightDialogAction(label: String, modifier: Modifier = Modifier, primary: Boolean = false,
    enabled: Boolean = true, onClick: () -> Unit) {
    if (primary) NightButton(label, modifier.widthIn(min = 112.dp), enabled = enabled, onClick = onClick)
    else Box(modifier.heightIn(min = 48.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center) {
        Text(label, fontFamily = GameDisplayFont, style = MaterialTheme.typography.titleMedium,
            color = LocalWorldLook.current.light.copy(alpha = if (enabled) .82f else .3f), maxLines = 1)
    }
}

@Composable
internal fun NightSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val look = LocalWorldLook.current
    Switch(checked, onCheckedChange, modifier, colors = SwitchDefaults.colors(
        checkedThumbColor = look.base, checkedTrackColor = look.accent, checkedBorderColor = look.accent,
        uncheckedThumbColor = look.tube, uncheckedTrackColor = Color.Transparent, uncheckedBorderColor = look.tube))
}

@Composable
private fun NightIcon(symbol: NightSymbol, modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val p = Path()
        when (symbol) {
            NightSymbol.UNDO -> {
                p.moveTo(w * .15f, h * .37f); p.lineTo(w * .15f, h * .14f)
                p.moveTo(w * .15f, h * .37f); p.lineTo(w * .39f, h * .37f)
                p.moveTo(w * .16f, h * .36f)
                p.cubicTo(w * .55f, -h * .03f, w * 1.15f, h * .40f, w * .73f, h * .81f)
                p.cubicTo(w * .58f, h * .95f, w * .31f, h * .90f, w * .22f, h * .77f)
            }
            NightSymbol.RESTART -> {
                p.moveTo(w * .78f, h * .22f); p.lineTo(w * .78f, h * .04f)
                p.moveTo(w * .78f, h * .22f); p.lineTo(w * .59f, h * .22f)
                p.moveTo(w * .77f, h * .21f)
                p.cubicTo(w * .05f, -h * .15f, -w * .09f, h * .93f, w * .53f, h * .90f)
                p.cubicTo(w * .79f, h * .89f, w * .96f, h * .66f, w * .89f, h * .46f)
            }
            NightSymbol.HINT -> {
                p.moveTo(w * .34f, h * .71f); p.lineTo(w * .66f, h * .71f)
                p.moveTo(w * .40f, h * .88f); p.lineTo(w * .60f, h * .88f)
                p.moveTo(w * .34f, h * .71f)
                p.cubicTo(w * .33f, h * .51f, w * .18f, h * .46f, w * .22f, h * .28f)
                p.cubicTo(w * .29f, -h * .03f, w * .74f, -h * .03f, w * .79f, h * .28f)
                p.cubicTo(w * .83f, h * .46f, w * .67f, h * .51f, w * .66f, h * .71f)
            }
        }
        drawPath(p, color, style = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** One line of a menu: quiet serif lettering on a full-width touch target, no surface of its own. */
@Composable
internal fun NightMenuRow(label: String, modifier: Modifier = Modifier, enabled: Boolean = true, detail: String? = null, onClick: () -> Unit) {
    val look = LocalWorldLook.current
    Column(modifier.fillMaxWidth().heightIn(min = 48.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(vertical = 8.dp),
        verticalArrangement = Arrangement.Center) {
        Text(label, fontFamily = GameDisplayFont, style = MaterialTheme.typography.titleMedium,
            color = look.light.copy(alpha = if (enabled) .9f else .32f))
        if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = lerp(look.light, look.tube, .45f))
    }
}

/** One of a few choices, in the same quiet bubble the level traits use. */
@Composable
internal fun NightChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val look = LocalWorldLook.current
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(50)
    Box(modifier.heightIn(min = 40.dp).clip(shape)
        .background(if (selected) look.accent.copy(alpha = .20f) else lerp(look.base, look.light, .06f))
        .border(1.dp, look.accent.copy(alpha = if (selected) .75f else .22f), shape)
        .clickable(role = Role.RadioButton, onClick = onClick)
        .semantics { this.selected = selected }
        .padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1,
            color = look.light.copy(alpha = if (selected) 1f else .72f))
    }
}

/** A ring that fills when chosen, for a list where each choice has a description beside it. */
@Composable
internal fun NightRadio(selected: Boolean, modifier: Modifier = Modifier) {
    val look = LocalWorldLook.current
    Canvas(modifier.size(22.dp)) {
        drawCircle(if (selected) look.accent else look.tube, size.minDimension / 2 - 1.dp.toPx(), style = Stroke(1.4.dp.toPx()))
        if (selected) drawCircle(look.accent, size.minDimension * .24f)
    }
}

@Composable
internal fun NightSlider(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val look = LocalWorldLook.current
    Slider(value, onValueChange, modifier, colors = SliderDefaults.colors(
        thumbColor = look.light, activeTrackColor = look.accent, inactiveTrackColor = look.tube.copy(alpha = .45f)))
}

@Composable
internal fun NightTextField(value: String, onValueChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier) {
    val look = LocalWorldLook.current
    OutlinedTextField(value, onValueChange, modifier.fillMaxWidth(), label = { Text(hint) },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = look.light, unfocusedTextColor = look.light, cursorColor = look.accent,
            focusedBorderColor = look.accent.copy(alpha = .8f), unfocusedBorderColor = look.tube.copy(alpha = .7f),
            focusedLabelColor = look.accent, unfocusedLabelColor = lerp(look.light, look.tube, .5f),
            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent))
}
