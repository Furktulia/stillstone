package com.example.ballsortgame.ui

import kotlin.math.*

internal enum class ImpactStyle(val title: String, val description: String) {
    PEBBLE("Pebble", "Low, rounded and dry"),
    BONE("Bone / wood", "Hollow, warm and short"),
    CERAMIC("Ceramic", "Clear, light and gently ringing")
}

/** Original synthetic studies, not recordings. Equal single-hit energy for fairer comparison. */
internal object ImpactSounds {
    const val rate = 44100
    fun render(style: ImpactStyle, deep: Boolean = true, balls: Int = 1, variant: Int = 0): ShortArray {
        require(balls in 1..4)
        val length = rate * 28 / 100
        val single = DoubleArray(length)
        val random = java.util.Random(740L + variant)
        var noise = 0.0
        val base = when (style) { ImpactStyle.PEBBLE -> 185.0; ImpactStyle.BONE -> 365.0; ImpactStyle.CERAMIC -> 735.0 }
        val frequency = base * (1 + (variant - 1) * 0.025)
        for (frame in single.indices) {
            val t = frame.toDouble() / rate
            noise = noise * 0.62 + (random.nextDouble() * 2 - 1) * 0.38
            val signal = when (style) {
                ImpactStyle.PEBBLE -> sin(2 * PI * frequency * (t + 0.0014 * (1 - exp(-t * 70)))) * exp(-t * 40) +
                    0.18 * sin(2 * PI * frequency * 0.51 * t) * exp(-t * 48) +
                    0.20 * sin(2 * PI * frequency * 2.71 * t) * exp(-t * 82) + noise * exp(-t * 150) * 0.72
                ImpactStyle.BONE -> sin(2 * PI * frequency * t) * exp(-t * 58) +
                    0.5 * sin(2 * PI * frequency * 1.58 * t) * exp(-t * 72) +
                    0.35 * sin(2 * PI * frequency * 0.51 * t) * exp(-t * 42) + noise * exp(-t * 120) * 0.2
                ImpactStyle.CERAMIC -> sin(2 * PI * frequency * t) * exp(-t * 24) +
                    0.26 * sin(2 * PI * frequency * 2.12 * t) * exp(-t * 36) +
                    0.09 * sin(2 * PI * frequency * 3.57 * t) * exp(-t * 60)
            }
            single[frame] = signal * min(1.0, t / 0.0025) * min(1.0, (0.28 - t) / 0.025).coerceAtLeast(0.0)
        }
        val rms = sqrt(single.sumOf { it * it } / length)
        val gain = 0.052 / rms * if (deep) 1.0 else 0.62
        val result = DoubleArray(length + rate * 12 / 100)
        repeat(balls) { index ->
            val offset = index * rate * 34 / 1000
            val weight = if (index == 0) 1.0 else 0.58 / index
            for (i in single.indices) if (i + offset < result.size) result[i + offset] += single[i] * gain * weight
        }
        return ShortArray(result.size) { (result[it].coerceIn(-0.88, 0.88) * 32767).roundToInt().toShort() }
    }
}
