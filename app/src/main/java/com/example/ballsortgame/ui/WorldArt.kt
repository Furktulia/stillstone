package com.example.ballsortgame.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

internal data class WorldLook(val art: String, val base: Color, val accent: Color, val light: Color, val tube: Color)
internal fun worldLook(id: String) = when (id) {
    "four" -> WorldLook("moonlight", Color(0xFF09151C), Color(0xFFA6CFD1), Color(0xFFD0E8EC), Color(0xFF5F7781))
    "five" -> WorldLook("midnight", Color(0xFF0B1024), Color(0xFFAEBEE8), Color(0xFFD4DEFF), Color(0xFF626F90))
    else -> WorldLook("dusk", Color(0xFF160D20), Color(0xFFC8AFE3), Color(0xFFE8D4F2), Color(0xFF796784))
}
internal val LocalWorldLook = staticCompositionLocalOf { worldLook("three") }

@Composable
internal fun WorldTheme(id: String, content: @Composable () -> Unit) {
    val look = worldLook(id)
    val colors = MaterialTheme.colorScheme.copy(
        primary = look.accent, onPrimary = look.base, secondary = look.accent, background = look.base,
        surfaceTint = look.accent,
        surfaceContainer = lerp(look.base, look.light, 0.055f),
        surfaceContainerLow = lerp(look.base, look.light, 0.035f),
        surfaceContainerHigh = lerp(look.base, look.light, 0.075f),
        surfaceContainerHighest = lerp(look.base, look.light, 0.10f),
        surface = lerp(look.base, Color.White, 0.045f),
        surfaceVariant = lerp(look.base, look.accent, 0.13f),
        secondaryContainer = lerp(look.base, look.accent, 0.18f),
        outline = look.tube,
    )
    CompositionLocalProvider(LocalWorldLook provides look, LocalContentColor provides colors.onBackground) {
        val type = MaterialTheme.typography
        val serif = com.example.ballsortgame.ui.theme.GameDisplayFont
        MaterialTheme(colorScheme = colors, typography = type.copy(
            headlineLarge = type.headlineLarge.copy(fontFamily = serif),
            headlineMedium = type.headlineMedium.copy(fontFamily = serif),
            headlineSmall = type.headlineSmall.copy(fontFamily = serif)),
            shapes = MaterialTheme.shapes.copy(extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)),
            content = content)
    }
}

/** Decode once off the UI thread. Evicted bitmaps are GC-owned, never recycled while displayed. */
internal object WorldArtwork {
    private val cache = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    @Synchronized fun load(context: Context, name: String): ImageBitmap {
        cache.get(name)?.let { return it }
        val options = BitmapFactory.Options().apply {
            inScaled = false
            inSampleSize = if (name.contains("-depth-") || name.startsWith("infinite-entry") || name.startsWith("stone-button")) 2 else 1
        }
        val bitmap = context.assets.open("worlds/$name.png").use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("Unreadable world artwork: $name")
        val entrance = name.contains("-depth-") || name.startsWith("infinite-entry")
        val trimmed = if (entrance || name.startsWith("stone-button")) trimBorder(bitmap) else bitmap
        val visible = if (entrance && ENTRANCE_FEATHER > 0f) featherEdges(trimmed, ENTRANCE_FEATHER) else trimmed
        return visible.asImageBitmap().also { cache.put(name, it) }
    }

    /** Depth of the soft edge of a world entrance, as a fraction of its width. Zero keeps the cut-out edge. */
    const val ENTRANCE_FEATHER = 0.035f

    /**
     * Dissolve the silhouette's edge into whatever is behind it. The alpha channel is blurred
     * and remapped so the fade happens inside the outline: the middle of the picture is
     * untouched, and thin leaves at the rim thin out instead of ending in a hard cut.
     */
    private fun featherEdges(bitmap: android.graphics.Bitmap, fraction: Float): android.graphics.Bitmap {
        val w = bitmap.width; val h = bitmap.height
        val radius = (w * fraction).roundToInt().coerceAtLeast(1)
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        var alpha = FloatArray(w * h) { pixels[it].ushr(24) / 255f }
        var scratch = FloatArray(w * h)
        repeat(2) {
            // Separable box blur; outside the picture counts as transparent.
            for (y in 0 until h) {
                var sum = 0f
                for (x in -radius until w) {
                    val enter = x + radius
                    if (enter < w) sum += alpha[y * w + enter]
                    val leave = x - radius - 1
                    if (leave >= 0) sum -= alpha[y * w + leave]
                    if (x >= 0) scratch[y * w + x] = sum / (radius * 2 + 1)
                }
            }
            for (x in 0 until w) {
                var sum = 0f
                for (y in -radius until h) {
                    val enter = y + radius
                    if (enter < h) sum += scratch[enter * w + x]
                    val leave = y - radius - 1
                    if (leave >= 0) sum -= scratch[leave * w + x]
                    if (y >= 0) alpha[y * w + x] = sum / (radius * 2 + 1)
                }
            }
        }
        for (i in pixels.indices) {
            val inside = ((alpha[i] - .5f) * 2f).coerceIn(0f, 1f)
            val soft = inside * inside * (3 - 2 * inside)
            val a = (pixels[i].ushr(24) * soft).roundToInt()
            pixels[i] = (a shl 24) or (pixels[i] and 0xFFFFFF)
        }
        return android.graphics.Bitmap.createBitmap(pixels, w, h, android.graphics.Bitmap.Config.ARGB_8888)
    }
    private fun trimBorder(bitmap: android.graphics.Bitmap): android.graphics.Bitmap {
        val w = bitmap.width; val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        var left = w; var right = -1; var top = h; var bottom = -1
        for (y in 0 until h) for (x in 0 until w) {
            if (pixels[y * w + x].ushr(24) > 24) {
                left = minOf(left, x); right = maxOf(right, x)
                top = minOf(top, y); bottom = maxOf(bottom, y)
            }
        }
        if (right < left) return bitmap
        left = (left - 2).coerceAtLeast(0); top = (top - 2).coerceAtLeast(0)
        right = (right + 2).coerceAtMost(w - 1); bottom = (bottom + 2).coerceAtMost(h - 1)
        return android.graphics.Bitmap.createBitmap(bitmap, left, top, right - left + 1, bottom - top + 1)
    }
}

@Composable
internal fun rememberArtwork(name: String): ImageBitmap? {
    val context = LocalContext.current.applicationContext
    return produceState<ImageBitmap?>(null, name) {
        value = withContext(Dispatchers.IO) { runCatching { WorldArtwork.load(context, name) }.getOrNull() }
    }.value
}

/** Draw the alpha silhouette itself as the rim; no rectangular hit-area decoration. */
@Composable
internal fun WorldEntrance(id: String, modifier: Modifier = Modifier, pressed: Boolean = false) {
    val look = worldLook(id)
    val art = rememberArtwork(when (id) { "five" -> "midnight-depth-v5"; "endless" -> "infinite-entry-v1"; else -> look.art + "-depth-v2" })
    Canvas(modifier) {
        if (art == null) return@Canvas
        val inset = 3.dp.toPx()
        val bounds = fittedArtwork(art.width, art.height,
            (size.width - inset * 2).roundToInt().coerceAtLeast(1),
            (size.height - inset * 2).roundToInt().coerceAtLeast(1))
        val width = bounds.width
        val height = bounds.height
        val left = bounds.x + inset.roundToInt()
        val top = bounds.y + inset.roundToInt()
        val rim = (if (pressed) 1.4.dp else 0.7.dp).toPx().roundToInt().coerceAtLeast(1)
        for (x in -1..1) for (y in -1..1) if (x != 0 || y != 0) {
            drawImage(art, dstOffset = IntOffset(left + x * rim, top + y * rim),
                dstSize = IntSize(width, height),
                colorFilter = ColorFilter.tint(look.light.copy(alpha = if (pressed) 0.60f else 0.06f)))
        }
        drawImage(art, dstOffset = IntOffset(left, top), dstSize = IntSize(width, height))
    }
}

/** [cloud] surrounds a world entrance with drifting-looking coloured fog; plain lights only glow. */
internal data class WorldLight(val bounds: androidx.compose.ui.geometry.Rect, val color: Color, val cloud: Boolean = false)

/**
 * Soft radial light drawn with a dithered native paint. Plain 8-bit gradients at these
 * low alphas form visible contour rings; where several overlapped and moved against
 * each other while scrolling, the rings crossed into a net-like pattern.
 */
private class SoftLight(color: Color, stops: FloatArray, alphas: FloatArray) {
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.DITHER_FLAG).apply {
        shader = android.graphics.RadialGradient(0f, 0f, 1f,
            IntArray(stops.size) { color.copy(alpha = alphas[it]).toArgb() }, stops, android.graphics.Shader.TileMode.CLAMP)
    }
    fun draw(canvas: android.graphics.Canvas, x: Float, y: Float, radiusX: Float, radiusY: Float = radiusX,
        strength: Float = 1f, erase: Boolean = false) {
        if (radiusX <= 0f || radiusY <= 0f) return
        paint.alpha = (strength.coerceIn(0f, 1f) * 255).roundToInt()
        paint.blendMode = if (erase) android.graphics.BlendMode.DST_OUT else android.graphics.BlendMode.SRC_OVER
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(radiusX, radiusY)
        canvas.drawCircle(0f, 0f, 1f, paint)
        canvas.restore()
    }
}

/** Irregular fog lobes around an entrance: angle (turns), distance and size relative to its width. */
private val CloudLobes = listOf(
    floatArrayOf(.03f, .56f, .46f, .23f), floatArrayOf(.15f, .64f, .36f, .25f),
    floatArrayOf(.27f, .54f, .50f, .21f), floatArrayOf(.41f, .66f, .38f, .24f),
    floatArrayOf(.53f, .58f, .48f, .22f), floatArrayOf(.66f, .66f, .36f, .26f),
    floatArrayOf(.78f, .52f, .52f, .21f), floatArrayOf(.90f, .64f, .40f, .24f),
)

@Composable
internal fun WorldBrowserBackdrop(modifier: Modifier = Modifier, lights: List<WorldLight> = emptyList(),
    scrollOffset: () -> Float = { 0f }) {
    val mist = rememberArtwork("browser-mist-v1")
    val clearing = remember { SoftLight(Color.Black, floatArrayOf(0f, 1f), floatArrayOf(.40f, 0f)) }
    val glows = remember { HashMap<Color, SoftLight>() }
    val clouds = remember { HashMap<Color, SoftLight>() }
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF182633), Color(0xFF26313C), Color(0xFF14242C))))
        val canvas = drawContext.canvas.nativeCanvas
        if (mist != null) {
            // The isolated mist layer lets local masks thin fog without erasing the base scene.
            drawContext.canvas.saveLayer(androidx.compose.ui.geometry.Rect(Offset.Zero, size), Paint())
            // Scroll-driven only: no idle animation loop, and reduced motion supplies zero.
            // The painted mist itself drifts; no synthetic layers slide across it.
            val phase = scrollOffset() / (size.height.coerceAtLeast(1f) * .8f)
            val overscan = 24.dp.toPx()
            val drift = kotlin.math.sin(phase) * overscan * .65f
            drawImage(mist,
                dstOffset = IntOffset(-overscan.roundToInt(), (-overscan + drift).roundToInt()),
                dstSize = IntSize((size.width + overscan * 2).roundToInt(),
                    (size.height + overscan * 2).roundToInt()), alpha = .92f)
            lights.forEach { light ->
                clearing.draw(canvas, light.bounds.center.x, light.bounds.center.y, light.bounds.width * .76f, erase = true)
            }
            drawContext.canvas.restore()
        }
        lights.forEach { light ->
            val center = light.bounds.center
            val width = light.bounds.width
            if (width <= 0f) return@forEach
            if (light.cloud) {
                // A bank of fog lit from inside by the world's colour, moving with its entrance.
                val lobe = clouds.getOrPut(light.color) {
                    SoftLight(lerp(light.color, Color.White, .12f), floatArrayOf(0f, .45f, 1f), floatArrayOf(.38f, .20f, 0f))
                }
                CloudLobes.forEach { (turn, distance, radiusX, radiusY) ->
                    val angle = turn * 2 * Math.PI.toFloat()
                    lobe.draw(canvas,
                        center.x + kotlin.math.cos(angle) * width * distance,
                        center.y + kotlin.math.sin(angle) * light.bounds.height * distance,
                        width * radiusX, width * radiusY)
                }
            }
            val glow = glows.getOrPut(light.color) {
                SoftLight(light.color, floatArrayOf(0f, .48f, .78f, 1f), floatArrayOf(.70f, .52f, .18f, 0f))
            }
            glow.draw(canvas, center.x, center.y, width * if (light.cloud) .86f else .80f,
                strength = if (light.cloud) 1f else .8f)
        }
        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .10f), Color.Transparent, Color.Black.copy(alpha = .15f))))
    }
}

/** Two width-scaled edge layers, separated by transparent sky. Never cover-crop a screen. */
@Composable
internal fun WorldBackdrop(id: String, modifier: Modifier = Modifier, selection: Boolean = false) {
    val look = worldLook(id)
    BoxWithConstraints(modifier) {
        val wide = maxWidth > maxHeight
        val continuousFrame = selection && id == "five" && !wide
        val frame = rememberArtwork(if (continuousFrame) "midnight-portrait-v3"
            else look.art + if (wide) "-wide-v2" else "-frame-v2")
        Canvas(Modifier.matchParentSize()) {
            val sky = when (id) {
                "four" -> listOf(Color(0xFF0C1926), Color(0xFF203543), Color(0xFF142A34))
                "five" -> listOf(Color(0xFF0D142C), Color(0xFF1B2A49), Color(0xFF152138))
                else -> listOf(Color(0xFF20152F), Color(0xFF38263F), Color(0xFF4A334A))
            }
            drawRect(if (selection) Brush.verticalGradient(sky) else
                Brush.verticalGradient(if (id == "three") listOf(Color(0xFF30203D), Color(0xFF21172F), Color(0xFF38263E)) else listOf(lerp(look.base, sky[0], 0.48f), look.base, lerp(look.base, sky[1], 0.25f))))
            drawRect(Brush.radialGradient(
                listOf(look.accent.copy(alpha = if (selection) 0.08f else if (id == "three") .12f else 0.095f), Color.Transparent),
                Offset(size.width * 0.83f, size.height * 0.08f), size.width * 0.95f))
            if (selection && !wide && id != "three" && id != "endless") {
                val moon = Offset(size.width * .80f, size.height * .16f)
                val moonColor = Color(0xFFE5E3DC)
                drawCircle(Brush.radialGradient(listOf(moonColor.copy(alpha = .10f), Color.Transparent),
                    moon, 16.dp.toPx()), 16.dp.toPx(), moon)
                drawCircle(Brush.radialGradient(listOf(moonColor, moonColor.copy(alpha = .55f)),
                    moon - Offset(1.dp.toPx(), 1.dp.toPx()), 7.dp.toPx()), 4.5.dp.toPx(), moon)
            }
            if (!selection && id == "five") {
                val beam = Path().apply {
                    moveTo(size.width * 0.84f, 0f); lineTo(size.width, 0f)
                    lineTo(size.width * 0.64f, size.height * 0.87f)
                    lineTo(size.width * 0.24f, size.height * 0.87f); close()
                }
                drawPath(beam, Brush.verticalGradient(listOf(look.light.copy(alpha = 0.027f), Color.Transparent)))
            }
        }
        Canvas(Modifier.matchParentSize().then(if (selection) Modifier else Modifier.blur(18.dp))) {
            if (frame == null) return@Canvas
            if (continuousFrame) {
                portraitWindowSlices(frame.width, frame.height, size.width.roundToInt(), size.height.roundToInt()).forEach { slice ->
                    drawImage(frame, srcOffset = IntOffset(slice.source.x, slice.source.y),
                        srcSize = IntSize(slice.source.width, slice.source.height),
                        dstOffset = IntOffset(slice.destination.x, slice.destination.y),
                        dstSize = IntSize(slice.destination.width, slice.destination.height), alpha = .95f)
                }
                return@Canvas
            }
            val band = (frame.height * 0.36f).roundToInt()
            val scale = size.width / frame.width
            val height = (band * scale).roundToInt().coerceAtLeast(1)
            val width = (frame.width * scale).roundToInt().coerceAtLeast(1)
            val left = ((size.width - width) / 2).roundToInt()
            val alpha = if (selection) 0.95f else if (id == "four") 0.23f else 0.18f
            // Portrait and wide layouts have distinct compositions. Both dimensions always
            // use the same scale; source sections contain alpha where content needs space.
            drawImage(frame, srcSize = IntSize(frame.width, band),
                dstOffset = IntOffset(left, 0), dstSize = IntSize(width, height), alpha = alpha)
            drawImage(frame, srcOffset = IntOffset(0, frame.height - band), srcSize = IntSize(frame.width, band),
                dstOffset = IntOffset(left, size.height.roundToInt() - height),
                dstSize = IntSize(width, height), alpha = alpha)
        }
        if (selection) Canvas(Modifier.matchParentSize()) {
            // Protect small navigation labels without dimming the entire illustration.
            drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.32f), Color.Transparent),
                startY = 0f, endY = size.height * 0.12f))
        }
    }
}
