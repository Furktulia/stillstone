package com.example.ballsortgame.ui

import kotlin.math.*

internal enum class ImpactStyle(val title: String, val description: String) {
    PEBBLE("Pebble", "Low, rounded and dry"),
    BONE("Bone / wood", "Hollow, warm and short"),
    CERAMIC("Ceramic", "Clear, light and gently ringing"),
    RIVER("River stone", "Pebble body with a bright, short clack")
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
        val base = when (style) { ImpactStyle.PEBBLE, ImpactStyle.RIVER -> 185.0; ImpactStyle.BONE -> 365.0; ImpactStyle.CERAMIC -> 735.0 }
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
                // Small phone speakers reproduce little below about 400 Hz, so the low Pebble
                // body alone sounds faint there. The clack sits where they are loudest.
                ImpactStyle.RIVER -> 0.75 * sin(2 * PI * frequency * (t + 0.0014 * (1 - exp(-t * 70)))) * exp(-t * 40) +
                    0.70 * sin(2 * PI * frequency * 7.3 * t) * exp(-t * 115) +
                    0.45 * sin(2 * PI * frequency * 12.1 * t) * exp(-t * 170) +
                    0.25 * sin(2 * PI * frequency * 19.4 * t) * exp(-t * 240) + noise * exp(-t * 260) * 0.6
            }
            single[frame] = signal * min(1.0, t / 0.0025) * min(1.0, (0.28 - t) / 0.025).coerceAtLeast(0.0)
        }
        val rms = sqrt(single.sumOf { it * it } / length)
        val gain = LEVEL / rms * if (deep) 1.0 else 0.62
        val result = DoubleArray(length + rate * 12 / 100)
        repeat(balls) { index ->
            val offset = index * rate * 34 / 1000
            val weight = if (index == 0) 1.0 else 0.58 / index
            for (i in single.indices) if (i + offset < result.size) result[i + offset] += single[i] * gain * weight
        }
        // Rounded limiting instead of a hard clip: loud group hits compress rather than crackle.
        return ShortArray(result.size) { (CEILING * tanh(result[it] / CEILING) * 32767).roundToInt().toShort() }
    }

    /**
     * One short hit for a single physical contact, in the character of [style]:
     * a lower, rounder tock on the tube floor, a bright clack between stones, and a
     * lighter, higher tap against a tube overhead. Playback volume carries the energy.
     */
    fun renderContact(style: ImpactStyle, kind: ContactKind, variant: Int = 0): ShortArray {
        val seconds = 0.16
        val length = (rate * seconds).toInt()
        val body = render(style, deep = true, balls = 1, variant = variant)
        val pitch = when (kind) { ContactKind.FLOOR -> 0.9; ContactKind.STONE -> 1.12; ContactKind.OVERHEAD -> 1.6 }
        val bodyShare = when (kind) { ContactKind.FLOOR -> 1.0; ContactKind.STONE -> 0.5; ContactKind.OVERHEAD -> 0.6 }
        val clackShare = when (kind) { ContactKind.FLOOR -> 0.12; ContactKind.STONE -> 0.7; ContactKind.OVERHEAD -> 0.3 }
        val clack = 1350.0 * (1 + (variant - 1) * 0.03) * if (kind == ContactKind.OVERHEAD) 1.25 else 1.0
        val signal = DoubleArray(length) { frame ->
            val t = frame.toDouble() / rate
            // Resample the style's own hit to move its pitch, so each material keeps its voice.
            val position = frame * pitch
            val index = position.toInt()
            val tone = if (index + 1 < body.size)
                (body[index] + (body[index + 1] - body[index]) * (position - index)) / 32767.0 else 0.0
            val knock = 0.70 * sin(2 * PI * clack * t) * exp(-t * 115) +
                0.45 * sin(2 * PI * clack * 1.66 * t) * exp(-t * 170) +
                0.25 * sin(2 * PI * clack * 2.66 * t) * exp(-t * 240)
            (tone * bodyShare * 1.6 + knock * clackShare * min(1.0, t / 0.0015)) *
                min(1.0, (length - 1 - frame) / (0.03 * rate))
        }
        val peak = signal.maxOf { abs(it) }.coerceAtLeast(1e-9)
        val level = if (kind == ContactKind.OVERHEAD) 0.6 else 0.8
        return ShortArray(length) { (signal[it] / peak * level * 32767).roundToInt().toShort() }
    }

    /** Single-hit RMS over the clip. Was 0.052, which peaked near half scale and read as faint. */
    private const val LEVEL = 0.095
    private const val CEILING = 0.88
}
