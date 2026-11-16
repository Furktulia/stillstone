package com.example.ballsortgame.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.ballsortgame.ui.theme.GameDisplayFont
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * How one world looks from the world screen: its sky, the part of its painted landscape
 * that is shown, and where its light comes from. The landscapes are the same paintings the
 * level lists use, so every screen of a world is recognisably the same place.
 */
internal class Vista(
    val art: String?,
    /** 0 shows the left end of the wide painting, 1 the right end. */
    val anchor: Float,
    val skyTop: Color, val skyLow: Color,
    val glow: Color, val stars: Int,
    /** Horizontal position of a moon in the painting (0..1 of its width), or negative for none. */
    val moonAt: Float = -1f,
)

internal fun vistaOf(world: String): Vista = when (world) {
    "four" -> Vista("moonlight-wide-v2", 1f, Color(0xFF0B1722), Color(0xFF1E3442), Color(0xFF9CC9D6), 34, moonAt = 0.685f)
    "five" -> Vista("midnight-wide-v2", 0f, Color(0xFF0A1024), Color(0xFF1B2A49), Color(0xFF8E9FE6), 60)
    "endless" -> Vista(null, .5f, Color(0xFF0B0E22), Color(0xFF211F45), Color(0xFFCDB4FF), 110)
    else -> Vista("dusk-wide-v2", 0f, Color(0xFF241732), Color(0xFF5A3A58), Color(0xFFF0A9B8), 0)
}

/**
 * One world as a stretch of a single continuous journey: sky, a lettered name, and its
 * landscape along the bottom, dissolving into mist that becomes the next world's sky.
 * The whole stretch is the button.
 */
@Composable
internal fun WorldVistaSection(world: String, name: String, detail: String, height: Dp, next: String?,
    modifier: Modifier = Modifier, open: () -> Unit) {
    val vista = remember(world) { vistaOf(world) }
    val below = remember(next) { next?.let(::vistaOf) }
    val look = worldLook(world)
    val art = vista.art?.let { rememberArtwork(it) }
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(modifier.fillMaxWidth().height(height).testTag("world_$world")
        .semantics(mergeDescendants = true) { contentDescription = "$name. $detail"; role = Role.Button }
        .clickable(interactionSource = source, indication = null, onClick = open)) {
        // A still painting: drawn once into its own layer, so scrolling the journey only moves it.
        Canvas(Modifier.matchParentSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            drawRect(Brush.verticalGradient(listOf(vista.skyTop, vista.skyLow)))
            // The light of this place: low on the horizon at dusk, high and cool at night.
            val glowAt = Offset(size.width * .5f, size.height * if (world == "three") .74f else .30f)
            drawRect(Brush.radialGradient(listOf(vista.glow.copy(alpha = if (world == "three") .30f else .13f), Color.Transparent),
                glowAt, size.width * .85f))
            // The top edge is exactly the colour the stretch above dissolves into, so no seam shows.
            drawRect(Brush.verticalGradient(listOf(vista.skyTop, Color.Transparent), endY = size.height * .14f),
                size = Size(size.width, size.height * .14f))
            val random = java.util.Random(world.hashCode().toLong())
            repeat(vista.stars) {
                val at = Offset(random.nextFloat() * size.width, random.nextFloat() * size.height * if (vista.art == null) .92f else .56f)
                drawCircle(Color.White.copy(alpha = .18f + random.nextFloat() * .55f), (.5f + random.nextFloat() * .9f).dp.toPx(), at)
            }
            if (art != null) {
                // Only the landscape along the bottom of the wide painting is used, in the lower half
                // of the stretch; the open sky above it is what lets one world run into the next.
                // Same scale in both directions; only a window of the painting's width is shown.
                val land = art.height / 2
                val scale = maxOf(size.height * .5f / land, size.width / art.width)
                val shownWidth = (size.width / scale).roundToInt().coerceAtMost(art.width)
                val left = ((art.width - shownWidth) * vista.anchor).roundToInt()
                if (vista.moonAt >= 0f) {
                    val moon = Offset((vista.moonAt * art.width - left) * scale, size.height * .15f)
                    val pale = Color(0xFFECE8DA)
                    drawCircle(Brush.radialGradient(listOf(pale.copy(alpha = .22f), Color.Transparent), moon, 46.dp.toPx()), 46.dp.toPx(), moon)
                    val full = Path().apply { addOval(androidx.compose.ui.geometry.Rect(moon, 12.dp.toPx())) }
                    val bite = Path().apply { addOval(androidx.compose.ui.geometry.Rect(moon + Offset(6.dp.toPx(), -4.dp.toPx()), 11.dp.toPx())) }
                    drawPath(Path().apply { op(full, bite, androidx.compose.ui.graphics.PathOperation.Difference) }, pale)
                }
                val drawnHeight = (land * scale).roundToInt()
                val top = size.height - drawnHeight
                // Anything the painting carries up past the strip (a pillar, a branch) thins out
                // into the sky instead of ending on a cut edge.
                val canvas = drawContext.canvas
                canvas.saveLayer(androidx.compose.ui.geometry.Rect(0f, top, size.width, size.height), androidx.compose.ui.graphics.Paint())
                drawImage(art, srcOffset = IntOffset(left, art.height - land), srcSize = IntSize(shownWidth, land),
                    dstOffset = IntOffset(0, top.roundToInt()), dstSize = IntSize(size.width.roundToInt(), drawnHeight))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = top, endY = top + drawnHeight * .22f),
                    topLeft = Offset(0f, top), size = Size(size.width, drawnHeight.toFloat()),
                    blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
                canvas.restore()
            } else if (vista.art == null) {
                // The endless place has no ground: only its sign among the stars.
                val a = size.width * .17f
                val centre = Offset(size.width / 2, size.height * .36f)
                val sign = Path()
                for (step in 0..120) {
                    val t = step / 120f * 2 * Math.PI.toFloat()
                    val d = 1 + sin(t) * sin(t)
                    val p = centre + Offset(a * 1.35f * cos(t) / d, a * 1.35f * sin(t) * cos(t) / d)
                    if (step == 0) sign.moveTo(p.x, p.y) else sign.lineTo(p.x, p.y)
                }
                drawPath(sign, vista.glow.copy(alpha = .10f), style = Stroke(9.dp.toPx(), cap = StrokeCap.Round))
                drawPath(sign, lerp(vista.glow, Color.White, .35f).copy(alpha = .85f), style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
            }
            // Ground gives way to mist, and the mist is the next world's sky.
            val into = below?.skyTop ?: vista.skyTop
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, into.copy(alpha = .55f), into),
                startY = size.height * .80f, endY = size.height),
                topLeft = Offset(0f, size.height * .80f), size = Size(size.width, size.height * .20f))
        }
        Column(Modifier.align(BiasAlignment(0f, if (vista.art == null) .16f else -.34f)).padding(horizontal = 24.dp).pressSink(pressed, .93f),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(name, fontFamily = GameDisplayFont, style = MaterialTheme.typography.displaySmall, color = look.light)
            Spacer(Modifier.height(4.dp))
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = look.light.copy(alpha = .66f))
        }
    }
}

/**
 * The same place while you play: its sky, a few faint stars and its ground low and dim under
 * the controls. Nothing here is bright or sharp enough to compete with the stones.
 */
@Composable
internal fun PlayBackdrop(world: String, modifier: Modifier = Modifier) {
    val vista = remember(world) { vistaOf(world) }
    val art = vista.art?.let { rememberArtwork(it) }
    // Drawn once into its own layer: moving stones above it must not repaint the painting every frame.
    Canvas(modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val night = Color(0xFF07060F)
        drawRect(Brush.verticalGradient(listOf(lerp(vista.skyTop, night, .25f), lerp(vista.skyLow, night, .50f), lerp(vista.skyLow, night, .30f))))
        drawRect(Brush.radialGradient(listOf(vista.glow.copy(alpha = .07f), Color.Transparent),
            Offset(size.width * .5f, size.height * if (world == "three") .95f else .05f), size.width * 1.1f))
        val random = java.util.Random(world.hashCode().toLong())
        repeat(vista.stars * 2 / 3) {
            val at = Offset(random.nextFloat() * size.width, random.nextFloat() * size.height * if (vista.art == null) 1f else .7f)
            drawCircle(Color.White.copy(alpha = .05f + random.nextFloat() * .13f), (.5f + random.nextFloat() * .6f).dp.toPx(), at)
        }
        if (art != null) {
            val land = art.height / 2
            val scale = size.width / art.width.coerceAtMost((size.width / (size.height * .30f / land)).roundToInt().coerceAtLeast(1))
            val shownWidth = (size.width / scale).roundToInt().coerceAtMost(art.width)
            val left = ((art.width - shownWidth) * vista.anchor).roundToInt()
            val drawnHeight = (land * scale).roundToInt()
            val top = size.height - drawnHeight
            val canvas = drawContext.canvas
            canvas.saveLayer(androidx.compose.ui.geometry.Rect(0f, top, size.width, size.height), androidx.compose.ui.graphics.Paint())
            drawImage(art, srcOffset = IntOffset(left, art.height - land), srcSize = IntSize(shownWidth, land),
                dstOffset = IntOffset(0, top.roundToInt()), dstSize = IntSize(size.width.roundToInt(), drawnHeight), alpha = .36f)
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = top, endY = top + drawnHeight * .45f),
                topLeft = Offset(0f, top), size = Size(size.width, drawnHeight.toFloat()),
                blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
            canvas.restore()
        }
    }
}
