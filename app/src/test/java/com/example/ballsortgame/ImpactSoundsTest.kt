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
