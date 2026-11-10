package com.example.ballsortgame.ui

import kotlin.math.sqrt

data class LandingImpact(val balls: Int, val dropSlots: Float)

/** Shared contact boundary for drawing, dependency release, sound and haptics. */
internal object LandingMotion {
    const val release = 0.56f
    const val contact = 0.82f
    fun distance(progress: Float, releaseFraction: Float): Float {
        if (progress <= release) return releaseFraction * (progress / release).coerceIn(0f, 1f)
        val falling = ((progress - release) / (contact - release)).coerceIn(0f, 1f)
        return releaseFraction + (1 - releaseFraction) * falling * falling
    }
    /**
     * Ordinary drops return about a third of their height. Long falls, such as from the
     * upper row into a lower tube, return progressively more (up to 55%) so the stone
     * can clear the rim and reach whatever is overhead.
     */
    fun reboundHeight(dropPixels: Float, slot: Float, balls: Int): Float {
        val high = ((dropPixels / slot - 5f) / 6f).coerceIn(0f, 1f)
        val share = 0.32f + 0.23f * high * high * (3 - 2 * high)
        return (dropPixels * share).coerceIn(slot * 0.12f, slot * 7f) / sqrt(1f + 0.10f * (balls - 1).coerceAtLeast(0))
    }
}
