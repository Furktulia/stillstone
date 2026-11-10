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
        val visible = if (name.contains("-depth-") || name.startsWith("infinite-entry") || name.startsWith("stone-button")) trimBorder(bitmap) else bitmap
        return visible.asImageBitmap().also { cache.put(name, it) }
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

internal data class WorldLight(val bounds: androidx.compose.ui.geometry.Rect, val color: Color)

@Composable
internal fun WorldBrowserBackdrop(modifier: Modifier = Modifier, lights: List<WorldLight> = emptyList(),
    scrollOffset: () -> Float = { 0f }) {
    val mist = rememberArtwork("browser-mist-v1")
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF182633), Color(0xFF26313C), Color(0xFF14242C))))
        if (mist != null) {
            // The isolated mist layer lets local masks thin fog without erasing the base scene.
            drawContext.canvas.saveLayer(androidx.compose.ui.geometry.Rect(Offset.Zero, size), Paint())
            // Scroll-driven only: no idle animation loop, and reduced motion supplies zero.
            val travel = scrollOffset()
            val phase = travel / (size.height.coerceAtLeast(1f) * .8f)
            val overscan = 24.dp.toPx()
            val drift = kotlin.math.sin(phase) * overscan * .65f
            drawImage(mist,
                dstOffset = IntOffset(-overscan.roundToInt(), (-overscan + drift).roundToInt()),
                dstSize = IntSize((size.width + overscan * 2).roundToInt(),
                    (size.height + overscan * 2).roundToInt()), alpha = .92f)
            // Broad translucent fog banks slide at a different rate to create gentle depth.
            for (i in 0..2) {
                val center = Offset(
                    size.width * (.2f + i * .34f) + kotlin.math.sin(phase * .7f + i) * 28.dp.toPx(),
                    size.height * (.22f + i * .30f) + kotlin.math.sin(phase + i * 1.6f) * 58.dp.toPx())
                val radius = size.width * .82f
                drawCircle(Brush.radialGradient(listOf(Color(0xFFAFB7DC).copy(alpha = .13f),
                    Color(0xFF8F9DC4).copy(alpha = .06f), Color.Transparent), center, radius),
                    radius, center)
            }
            lights.forEach { light ->
                val radius = light.bounds.width * .76f
                if (radius > 0f) {
                    drawCircle(Brush.radialGradient(listOf(Color.Black.copy(alpha = .40f), Color.Transparent),
                        light.bounds.center, radius), radius, light.bounds.center, blendMode = BlendMode.DstOut)
                }
            }
            drawContext.canvas.restore()
        }
        lights.forEach { light ->
            val radius = light.bounds.width * .80f
            if (radius > 0f) drawCircle(Brush.radialGradient(
                0f to light.color.copy(alpha = .56f),
                .48f to light.color.copy(alpha = .40f),
                .78f to light.color.copy(alpha = .13f),
                1f to Color.Transparent,
                center = light.bounds.center, radius = radius), radius, light.bounds.center)
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
