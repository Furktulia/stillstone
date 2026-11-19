package com.example.ballsortgame.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import kotlin.math.abs
import kotlin.math.roundToInt

/** Every hit starts a voice; only samples that overlap at the loudest peaks are softened. */
internal class ContactMixer {
    private class Voice(val pcm: ShortArray, val gain: Float, var delay: Int, var frame: Int = 0)
    private val voices = mutableListOf<Voice>()
    val active get() = voices.isNotEmpty()
    private var fadeFrames = 0
    fun clear() { voices.clear(); fadeFrames = 0 }
    fun finishMotion() { if (active) fadeFrames = FADE_FRAMES }
    fun hit(pcm: ShortArray, gain: Float, delayFrames: Int = 0) {
        if (pcm.isNotEmpty() && gain > 0f) {
            fadeFrames = 0
            voices.add(Voice(pcm, gain.coerceAtMost(1f), delayFrames))
        }
    }
    fun render(output: ShortArray, sums: FloatArray) {
        sums.fill(0f)
        val iterator = voices.iterator()
        while (iterator.hasNext()) {
            val voice = iterator.next()
            val start = minOf(output.size, voice.delay)
            voice.delay -= start
            val end = start + minOf(output.size - start, voice.pcm.size - voice.frame)
            for (i in start until end) sums[i] += voice.pcm[voice.frame++] * voice.gain
            if (voice.frame == voice.pcm.size) iterator.remove()
        }
        val fading = fadeFrames > 0
        for (i in output.indices) {
            val sample = if (fading) {
                val gain = fadeFrames.toFloat() / FADE_FRAMES
                fadeFrames = (fadeFrames - 1).coerceAtLeast(0)
                sums[i] * gain
            } else sums[i]
            val excess = abs(sample) - 26000f
            val limited = if (excess <= 0f) sample else
                (26000f + excess / (1f + excess / 6760f)) * kotlin.math.sign(sample)
            output[i] = limited.roundToInt().toShort()
        }
        if (fading && fadeFrames == 0) clear()
    }
    private companion object { const val FADE_FRAMES = ImpactSounds.rate / 125 }
}

/** A single output stream mixes short contacts and sleeps between landings. */
/** [clips] holds a few variants per kind, taken in turn so that successive taps are never identical. */
internal class ContactPlayer(private val clips: Map<ContactKind, List<ShortArray>>) {
    private val cursor = IntArray(ContactKind.entries.size)
    private val lock = Object()
    private val mixer = ContactMixer()
    private var closed = false
    private var interrupted = false
    private var moving = false
    private val minimum = AudioTrack.getMinBufferSize(ImpactSounds.rate, AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT)
    private val track = AudioTrack.Builder()
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .setAudioFormat(AudioFormat.Builder().setSampleRate(ImpactSounds.rate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
        .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        .setBufferSizeInBytes(minimum.coerceAtLeast(1024)).build()

    init {
        // Limit how much already-mixed audio a new impact must wait behind.
        val frames = track.setBufferSizeInFrames(384)
        if (android.os.Build.VERSION.SDK_INT >= 31 && frames > 0) track.setStartThresholdInFrames(minOf(192, frames))
        Thread(::play, "contact-mixer").start()
    }

    fun motion(active: Boolean) = synchronized(lock) {
        if (moving != active) {
            moving = active
            if (!active) mixer.finishMotion()
            lock.notifyAll()
        }
    }

    fun hit(kind: ContactKind, gain: Float, delaySeconds: Float = 0f) = synchronized(lock) {
        if (!closed) {
            val variants = clips.getValue(kind)
            mixer.hit(variants[cursor[kind.ordinal]++ % variants.size], gain, (delaySeconds * ImpactSounds.rate).roundToInt())
        }
        lock.notifyAll()
    }
    fun stop() = synchronized(lock) {
        mixer.clear()
        moving = false
        interrupted = true
        lock.notifyAll()
    }
    fun close() = synchronized(lock) {
        closed = true
        lock.notifyAll()
    }

    private fun play() {
        val output = ShortArray(192)
        val sums = FloatArray(output.size)
        var tail = 0
        var playing = false
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            while (true) {
                val idle = synchronized(lock) {
                    if (closed) return
                    val shouldPause = interrupted || (!moving && !mixer.active && tail <= 0)
                    interrupted = false
                    if (shouldPause) tail = 0
                    else {
                        if (mixer.active) tail = track.bufferSizeInFrames + output.size
                        else tail = (tail - output.size).coerceAtLeast(0)
                        mixer.render(output, sums)
                    }
                    shouldPause
                }
                if (idle) {
                    if (playing) { track.pause(); track.flush(); playing = false }
                    synchronized(lock) {
                        while (!closed && !moving && !mixer.active && !interrupted) lock.wait()
                    }
                    continue
                }
                if (!playing) { track.play(); playing = true }
                var offset = 0
                while (offset < output.size) {
                    val written = track.write(output, offset, output.size - offset, AudioTrack.WRITE_BLOCKING)
                    if (written <= 0) return
                    offset += written
                }
            }
        } catch (_: IllegalStateException) {
            // Losing an output device must not interrupt the game.
        } finally {
            synchronized(lock) { closed = true; mixer.clear() }
            track.release()
        }
    }
}
