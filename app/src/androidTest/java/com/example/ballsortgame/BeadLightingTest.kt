package com.example.ballsortgame

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import com.example.ballsortgame.ui.*
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BeadLightingTest {
    @Test fun renderThreeBeadImpact() {
        val look = worldLook("five")
        val slot = 62f
        val diameter = slot * SettlingColumns.BEAD_DIAMETER
        val floor = 259f
        val columns = SettlingColumns(1, slot).apply { waterBeads = true }
        columns.land(0, 3, 0f, 21f * slot)
        val times = listOf(0, 10, 20, 32, 64, 128)
        val width = times.size * 120
        val bitmap = Bitmap.createBitmap(width, 280, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(look.base.toArgb())
        val labels = android.graphics.Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = look.light.toArgb(); textSize = 16f }
        var time = 0
        val peak = FloatArray(3)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()), Size(width.toFloat(), 280f)) {
            times.forEachIndexed { frame, end ->
                while (time < end) {
                    columns.step(0.002f)
                    for (i in 0..2) peak[i] = maxOf(peak[i], columns.squash(0, i))
                    time += 2
                }
                val x = 60f + frame * 120f
                drawLine(look.tube, Offset(x - 37f, floor), Offset(x + 37f, floor), strokeWidth = 2f)
                for (i in 0..2) {
                    val pos = Offset(x, floor - diameter / 2f - i * slot - columns.visibleOffset(0, i))
                    translate(pos.x - diameter / 2f, pos.y - diameter / 2f) {
                        drawWaterBead(pos, diameter, PieceColors[3], look, columns.squash(0, i))
                    }
                }
                labels.drawText("$end ms", x - 28f, 23f, paint)
            }
        }
        assertTrue(peak[0] > peak[1] && peak[1] > peak[2])
        if (InstrumentationRegistry.getArguments().getString("exportBeadSheet") == "true") {
            val dir = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
            File(dir, "three-bead-impact.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    @Test fun midnightBeadsReflectLessLightThanDusk() {
        val rows = listOf("Midnight" to worldLook("five"), "Moonlight" to worldLook("four"),
            "Dusk" to worldLook("three"))
        val sheet = Bitmap.createBitmap(640, 116 * rows.size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(sheet)
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 20f }
        val samples = rows.mapIndexed { index, (name, look) ->
            val tile = Bitmap.createBitmap(sheet.width, 116, Bitmap.Config.ARGB_8888)
            tile.eraseColor(look.base.toArgb())
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(tile.asImageBitmap()), Size(640f, 116f)) {
                PieceColors.forEachIndexed { i, color ->
                    val pos = Offset(40f + 80f * i, 75f)
                    translate(pos.x - 28f, pos.y - 28f) {
                        drawWaterBead(pos, 56f, lerp(color, look.light, 0.045f), look, 0f)
                    }
                }
            }
            label.color = look.light.toArgb()
            android.graphics.Canvas(tile).drawText(name, 12f, 26f, label)
            canvas.drawBitmap(tile, 0f, index * 116f, null)
            PieceColors.mapIndexed { i, color ->
                val tint = lerp(color, look.light, 0.045f).luminance()
                val x = 40 + 80 * i
                val center = androidx.compose.ui.graphics.Color(tile.getPixel(x, 75)).luminance()
                assertTrue("Body lost its palette brightness: $name $i", center >= tint - 0.025f)
                (55..92).maxOf { y -> (x - 17..x + 17).maxOf { px ->
                    androidx.compose.ui.graphics.Color(tile.getPixel(px, y)).luminance()
                } } - tint
            }
        }
        for (i in PieceColors.indices) {
            assertTrue("Midnight glare: $i", samples[0][i] < 0.055f)
            assertTrue("Dusk reflection: $i", samples[0][i] <= samples[2][i] + 0.01f)
        }
        if (InstrumentationRegistry.getArguments().getString("exportBeadSheet") == "true") {
            val dir = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
            File(dir, "bead-lighting.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
