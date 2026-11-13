package com.example.ballsortgame

import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.example.ballsortgame.ui.WorldArtwork
import org.junit.Assert.*
import org.junit.Test

class WorldArtworkTest {
    @Test fun launcherUsesFullBleedCarvedArtworkAndThemedSymbol() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(context.applicationInfo.icon in setOf(R.mipmap.ic_stillstone, R.mipmap.ic_stillstone_round))
        for ((iconId, artId) in listOf(R.mipmap.ic_stillstone to R.drawable.launcher_stone,
            R.mipmap.ic_stillstone_round to R.drawable.launcher_stone_round)) {
        val icon = context.resources.getDrawable(iconId, context.theme)
        assertTrue(icon is android.graphics.drawable.AdaptiveIconDrawable)
        val adaptive = icon as android.graphics.drawable.AdaptiveIconDrawable
        val art = BitmapFactory.decodeResource(context.resources, artId)
        assertEquals(512, art.width)
        assertEquals(512, art.height)
        // The master is a continuous stone surface, not a plaque floating inside a mask.
        assertEquals(255, art.getPixel(0, 0).ushr(24))
        assertEquals(255, art.getPixel(511, 511).ushr(24))
        assertTrue(art.getPixel(256, 256).ushr(24) > 240)
        art.recycle()
        // Composition is in the full-bleed background; foreground must not add another badge.
        val side = (108 * context.resources.displayMetrics.density).toInt()
        val rendered = android.graphics.Bitmap.createBitmap(side, side, android.graphics.Bitmap.Config.ARGB_8888)
        adaptive.foreground.setBounds(0, 0, side, side)
        adaptive.foreground.draw(android.graphics.Canvas(rendered))
        assertEquals(0, rendered.getPixel(0, 0).ushr(24))
        assertEquals(0, rendered.getPixel(side / 2, side / 2).ushr(24))
        adaptive.setBounds(0, 0, side, side)
        adaptive.draw(android.graphics.Canvas(rendered))
        assertTrue(rendered.getPixel(side / 2, side / 2).ushr(24) > 240)
        rendered.recycle()
        if (android.os.Build.VERSION.SDK_INT >= 33) assertNotNull(adaptive.monochrome)
        }
    }

    @Test fun displayTypefaceAndItsLicenseAreBundledOffline() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertNotNull(context.resources.getFont(R.font.libre_baskerville))
        val license = context.assets.open("licenses/LibreBaskerville-OFL.txt").bufferedReader().use { it.readText() }
        assertTrue(license.contains("Copyright 2012 The Libre Baskerville Project Authors"))
        assertTrue(license.contains("SIL OPEN FONT LICENSE Version 1.1"))
    }

    @Test fun productionArtIsCutOutSoSkyShowsThrough() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val entrances = listOf("stone-button-v1")
        val frames = listOf("dusk-frame-v2", "moonlight-frame-v2", "midnight-frame-v2")
        val wide = listOf("dusk-wide-v2", "moonlight-wide-v2", "midnight-wide-v2")
        val continuous = listOf("midnight-portrait-v3")
        assertEquals((entrances + frames + wide + continuous).map { "$it.png" }.toSet(), context.assets.list("worlds")!!.toSet())
        for (name in entrances + frames + wide + continuous) {
            val bitmap = context.assets.open("worlds/$name.png").use { BitmapFactory.decodeStream(it) }!!
            assertTrue("$name needs actual alpha", bitmap.hasAlpha())
            var transparent = 0; var opaque = 0
            for (x in 0..20) for (y in 0..20) {
                val alpha = bitmap.getPixel(x * (bitmap.width - 1) / 20, y * (bitmap.height - 1) / 20).ushr(24)
                if (alpha < 10) transparent++
                if (alpha > 240) opaque++
            }
            assertTrue("$name contains painted art", opaque > 30)
            assertTrue("$name has an open silhouette", transparent > 50)
            if (name in wide) assertTrue("$name is a dedicated landscape composition", bitmap.width.toFloat() / bitmap.height in 2.1f..2.5f)
            if (name in frames) {
                assertTrue("$name is a phone composition, not a comparison strip", bitmap.height.toFloat() / bitmap.width in 2.1f..2.5f)
                for (x in 0..20) for (y in 40..60 step 5) {
                    val alpha = bitmap.getPixel(x * (bitmap.width - 1) / 20, y * (bitmap.height - 1) / 100).ushr(24)
                    assertTrue("$name must leave the level-marker area transparent", alpha < 10)
                }
            }
            if (name in continuous) {
                // The previous frame had no middle jambs: both walls abruptly ended on screen.
                for (y in 15..85 step 5) {
                    assertTrue("left wall must continue to the sill",
                        bitmap.getPixel(bitmap.width * 2 / 100, bitmap.height * y / 100).ushr(24) > 200)
                    assertTrue("right shutter must continue to the sill",
                        bitmap.getPixel(bitmap.width * 98 / 100, bitmap.height * y / 100).ushr(24) > 200)
                    assertTrue("window opening stays clear",
                        bitmap.getPixel(bitmap.width / 2, bitmap.height * minOf(y, 60) / 100).ushr(24) < 10)
                }
            }
            bitmap.recycle()
            val cached = WorldArtwork.load(context, name)
            assertTrue(cached.width >= 300)
            assertSame(cached, WorldArtwork.load(context, name))
        }
    }
}
