package com.example.ballsortgame

import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.example.ballsortgame.ui.WorldArtwork
import org.junit.Assert.*
import org.junit.Test

class WorldArtworkTest {
    @Test fun displayTypefaceAndItsLicenseAreBundledOffline() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertNotNull(context.resources.getFont(R.font.libre_baskerville))
        val license = context.assets.open("licenses/LibreBaskerville-OFL.txt").bufferedReader().use { it.readText() }
        assertTrue(license.contains("Copyright 2012 The Libre Baskerville Project Authors"))
        assertTrue(license.contains("SIL OPEN FONT LICENSE Version 1.1"))
    }

    @Test fun productionArtHasCutoutEntrancesAndPhoneFramesWithEmptyCenters() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val entrances = listOf("dusk-depth-v2", "moonlight-depth-v2", "midnight-depth-v5", "infinite-entry-v1", "stone-button-v1")
        val frames = listOf("dusk-frame-v2", "moonlight-frame-v2", "midnight-frame-v2")
        val wide = listOf("dusk-wide-v2", "moonlight-wide-v2", "midnight-wide-v2")
        val continuous = listOf("midnight-portrait-v3")
        assertEquals((entrances + frames + wide + continuous + "browser-mist-v1").map { "$it.png" }.toSet(), context.assets.list("worlds")!!.toSet())
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
