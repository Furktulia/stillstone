package com.example.ballsortgame.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp

/** Coordinates inside the bead are local. Its reflection follows a light fixed in the world. */
internal fun DrawScope.drawWaterBead(position: Offset, diameter: Float, tint: Color, look: WorldLook,
    squash: Float) {
    val r = diameter / 2
    val center = Offset(r, r)
    val dx = (0.28f - position.x / size.width.coerceAtLeast(1f)).coerceIn(-1f, 1f)
    val dy = (-0.24f - position.y / size.height.coerceAtLeast(1f)).coerceIn(-1f, 0f)
    val reflection = center + Offset(dx * r * 0.70f, dy * r * 0.72f)
    val flatten = squash.coerceIn(-0.09f, 0.30f)
    scale(1f / kotlin.math.sqrt(1f - flatten), 1f - flatten, center) {
        // A single antialiased silhouette. The edge stays in the bead's own shadow.
        val shade = lerp(tint, look.base, 0.30f)
        // A broad reflection of the sky shares the bead's hue. No point-source glint.
        drawCircle(Brush.radialGradient(
            0f to lerp(tint, look.light, look.beadReflection),
            0.52f to tint,
            0.80f to lerp(tint, shade, 0.42f),
            1f to shade, center = reflection, radius = r * 1.60f), r, center)
    }
}
