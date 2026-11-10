package com.example.ballsortgame

import com.example.ballsortgame.ui.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class ImpactSoundsTest {
    private fun rms(pcm: ShortArray) = sqrt(pcm.sumOf { it.toDouble() * it } / pcm.size)
    @Test fun stylesAreDistinctRepeatableBoundedAndComparableInEnergy() {
        val clips = ImpactStyle.entries.map { ImpactSounds.render(it, variant = 1) }
        assertFalse(clips[0].contentEquals(clips[1]))
        assertFalse(clips[1].contentEquals(clips[2]))
        val energies = clips.map(::rms)
        assertTrue(energies.max() / energies.min() < 1.05)
        ImpactStyle.entries.forEach { style ->
            assertArrayEquals(ImpactSounds.render(style), ImpactSounds.render(style))
            assertTrue(rms(ImpactSounds.render(style, deep = true)) > rms(ImpactSounds.render(style, deep = false)) * 1.5)
            for (deep in listOf(false, true)) for (balls in 1..4) {
                val pcm = ImpactSounds.render(style, deep, balls)
                assertTrue(pcm.all { it.toInt() in -28836..28836 })
                assertEquals(0, pcm.last().toInt())
                assertTrue(pcm.any { it != 0.toShort() })
            }
            val group = ImpactSounds.render(style, balls = 3)
            assertFalse(group.contentEquals(ImpactSounds.render(style)))
        }
    }
}
