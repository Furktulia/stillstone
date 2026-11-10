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
    fun reboundHeight(dropPixels: Float, slot: Float, balls: Int): Float =
        (dropPixels * 0.32f).coerceIn(slot * 0.12f, slot * 3f) / sqrt(1f + 0.10f * (balls - 1).coerceAtLeast(0))
    fun rebound(progress: Float, height: Float): Float {
        val t = ((progress - contact) / (1 - contact)).coerceIn(0f, 1f)
        val firstShare = 1f / 1.4f
        val local = if (t <= firstShare) t / firstShare else (t - firstShare) / (1 - firstShare)
        val amplitude = if (t <= firstShare) height else height * 0.16f
        return 4 * amplitude * local * (1 - local) // Two ballistic hops, no eased-in delay.
    }
    fun settlingMillis(height: Float, slot: Float) = (550 * sqrt(height / slot)).toInt().coerceIn(200, 1200)
}
