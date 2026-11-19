package com.example.ballsortgame

import com.example.ballsortgame.ui.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class ImpactSoundsTest {
    private fun rms(pcm: ShortArray) = sqrt(pcm.sumOf { it.toDouble() * it } / pcm.size)
    @Test fun contactClipsDifferByWhatWasHitAndFasterKnocksAreLouder() {
        for (style in ImpactStyle.entries) {
            val clips = ContactKind.entries.map { ImpactSounds.renderContact(style, it) }
            assertEquals(3, clips.map { it.toList() }.distinct().size)
            clips.forEach { pcm ->
                assertTrue(pcm.size < ImpactSounds.rate / 5)
                assertTrue(pcm.maxOf { kotlin.math.abs(it.toInt()) } in 15000..28836)
                assertEquals(0, pcm.last().toInt())
            }
        }
        val soft = contactVolume(StoneContact(ContactKind.STONE, 0, 4f))
        val hard = contactVolume(StoneContact(ContactKind.STONE, 0, 40f))
        assertTrue(soft in 0.05f..0.5f && hard > soft * 1.8f && hard <= 1f)
    }

    @Test fun beadContactVariantsDifferAndStayInRange() {
        for (kind in ContactKind.entries) {
            val variants = List(3) { ImpactSounds.beadContact(kind, it) }
            assertEquals(3, variants.map { it.toList() }.distinct().size)
            variants.forEach { pcm ->
                val peak = pcm.maxOf { kotlin.math.abs(it.toInt()) }
                assertTrue("$kind peak $peak", peak in 4000..28000)
                assertEquals(0, pcm.last().toInt())
            }
        }
    }

    @Test fun riverStoneContactsCanClipWhenTheirPeaksOverlap() {
        val floor = ImpactSounds.renderContact(ImpactStyle.RIVER, ContactKind.FLOOR)
        val stone = ImpactSounds.renderContact(ImpactStyle.RIVER, ContactKind.STONE)
        val floorPeak = floor.indices.maxBy { floor[it] }
        val stonePeak = stone.indices.maxBy { stone[it] }
        val volume = 0.85f
        val unbounded = floor[floorPeak] + stone[stonePeak] * volume
        assertTrue("The original overlapping mix exceeds full scale", unbounded > Short.MAX_VALUE)

        val floorPeaks = StoneClipPeaks(floor)
        val stonePeaks = StoneClipPeaks(stone)
        val gain = stonePeaks.gainFor(volume, floorPeaks.fromFrame((floorPeak - stonePeak).coerceAtLeast(0)))
        assertTrue("The second hit stays audible", gain > 0f)
        assertTrue(floor[floorPeak] + stone[stonePeak] * gain <= Short.MAX_VALUE)
        assertEquals(volume, stonePeaks.gainFor(volume, floorPeaks.fromFrame(floor.size)), 0f)
        assertEquals(volume, stonePeaks.gainFor(volume, 0.02f), 0f)
    }

    @Test fun stylesAreDistinctRepeatableBoundedAndComparableInEnergy() {
        val clips = ImpactStyle.entries.map { ImpactSounds.render(it, variant = 1) }
        assertFalse(clips[0].contentEquals(clips[1]))
        assertFalse(clips[1].contentEquals(clips[2]))
        val energies = clips.map(::rms)
        // Peaks are rounded off by the limiter, so energies match a little less exactly than before.
        assertTrue(energies.max() / energies.min() < 1.10)
        ImpactStyle.entries.forEach { style ->
            assertArrayEquals(ImpactSounds.render(style), ImpactSounds.render(style))
            assertTrue(rms(ImpactSounds.render(style, deep = true)) > rms(ImpactSounds.render(style, deep = false)) * 1.5)
            for (deep in listOf(false, true)) for (balls in 1..4) {
                val pcm = ImpactSounds.render(style, deep, balls)
                assertTrue(pcm.all { it.toInt() in -28836..28836 })
                assertEquals(0, pcm.last().toInt())
                assertTrue(pcm.any { it != 0.toShort() })
            }
            // Loud enough to hear: a single deep hit uses most of the available range.
            val peak = ImpactSounds.render(style).maxOf { kotlin.math.abs(it.toInt()) } / 32767.0
            assertTrue("${style.name} peak $peak", peak > 0.45)
            val group = ImpactSounds.render(style, balls = 3)
            assertFalse(group.contentEquals(ImpactSounds.render(style)))
        }
    }
}
