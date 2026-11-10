package com.example.ballsortgame.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
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

/** Native labels over a shared, horizontally stretchable stone surface. */
@Composable
internal fun NightButton(label: String, modifier: Modifier = Modifier, enabled: Boolean = true,
    symbol: NightSymbol? = null, large: Boolean = false, onClick: () -> Unit) {
    val art = rememberArtwork("stone-button-v1")
    val look = LocalWorldLook.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(modifier.heightIn(min = if (large) 78.dp else 52.dp)
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
            dstOffset = IntOffset(dx[i], if (pressed) 1.dp.toPx().roundToInt() else 0),
            dstSize = IntSize(dx[i + 1] - dx[i], size.height.roundToInt()),
            alpha = if (enabled) 1f else .42f,
            colorFilter = if (pressed) ColorFilter.tint(Color.White.copy(alpha = .10f), BlendMode.SrcAtop) else null)
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

