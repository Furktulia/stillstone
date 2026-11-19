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
    fun fallMillis(distance: Float, slot: Float): Float =
        (1000f * sqrt(2f * distance.coerceAtLeast(0f) / (SettlingColumns.GRAVITY_SLOTS * slot))).coerceAtLeast(1f)

    fun fallSpeed(distance: Float, slot: Float): Float =
        sqrt(2f * SettlingColumns.GRAVITY_SLOTS * slot * distance.coerceAtLeast(0f))
}