package com.example.ballsortgame

import com.example.ballsortgame.ui.ContactMixer
import org.junit.Assert.*
import org.junit.Test

class ContactPeaksTest {
    @Test fun secondContactStartsOnTheNextSampleWhileFirstTailContinues() {
        val mixer = ContactMixer()
        val block = ShortArray(2)
        val sums = FloatArray(2)
        mixer.hit(shortArrayOf(2000, 1000, 900, 800), 1f)
        mixer.render(block, sums)
        mixer.hit(shortArrayOf(3000, 2000), 1f)
        mixer.render(block, sums)
        assertArrayEquals(shortArrayOf(3900, 2800), block)
        assertFalse(mixer.active)
    }

    @Test fun settlingFadesTheTailAndTheNextHitCanStartImmediately() {
        val mixer = ContactMixer()
        val output = ShortArray(512)
        mixer.hit(ShortArray(4000) { 10000 }, 1f)
        mixer.finishMotion()
        mixer.render(output, FloatArray(output.size))
        assertEquals(10000, output.first().toInt())
        assertTrue(output[200] in 1..9999)
        assertEquals(0, output.last().toInt())
        assertFalse(mixer.active)
        mixer.hit(shortArrayOf(1234), 1f)
        mixer.render(output, FloatArray(output.size))
        assertEquals(1234, output.first().toInt())
    }

    @Test fun contactsFromOneVisualFrameKeepTheirSampleOffsets() {
        val mixer = ContactMixer()
        val block = ShortArray(3)
        val sums = FloatArray(block.size)
        mixer.hit(shortArrayOf(1000, 900, 800, 700, 600), 1f)
        mixer.hit(shortArrayOf(2000, 1800), 1f, delayFrames = 4)
        mixer.render(block, sums)
        assertArrayEquals(shortArrayOf(1000, 900, 800), block)
        mixer.render(block, sums)
        assertArrayEquals(shortArrayOf(700, 2600, 1800), block)
    }

    @Test fun denseOverlapsKeepTheirTailsAndNeverWrapAround() {
        val mixer = ContactMixer()
        repeat(12) { mixer.hit(shortArrayOf(30000, -30000, 100), 1f) }
        val block = ShortArray(3)
        mixer.render(block, FloatArray(3))
        assertTrue(block[0] > 30000 && block[1] < -30000)
        assertEquals(1200, block[2].toInt())
    }
}
