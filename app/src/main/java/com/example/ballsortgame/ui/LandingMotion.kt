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
}
