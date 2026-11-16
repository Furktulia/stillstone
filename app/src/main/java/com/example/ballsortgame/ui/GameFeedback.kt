package com.example.ballsortgame.ui

import android.content.Context
import android.media.*
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Where sounds and vibrations are started. Each start is a call into an Android system service
 * that can take milliseconds on a phone, and settling stones make several per frame: done on the
 * main thread they made the bouncing stutter. A post to this waiting thread takes microseconds.
 */
private object FeedbackThread {
    private val handler = Handler(HandlerThread("stone-feedback", Process.THREAD_PRIORITY_URGENT_AUDIO).apply { start() }.looper)
    fun post(block: () -> Unit) { handler.post(block) }
}

/** Small locally synthesized stone taps. No downloads, music, or audio service. */
internal class StoneAudio {
    private val tracks = mutableListOf<AudioTrack>()
    private var next = 0
    private var closed = false
    private var loadedStyle: ImpactStyle? = null
    private var previewPrepared = false
    private val contactCursor = IntArray(ContactKind.entries.size)
    val ready: Boolean @Synchronized get() = tracks.isNotEmpty()
    private fun load(pcm: ShortArray) {
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(ImpactSounds.rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.size * 2).build()
        tracks.add(track)
        check(track.write(pcm, 0, pcm.size) == pcm.size)
    }
    @Synchronized fun prepare(style: ImpactStyle, deep: Boolean = true, balls: Int = 1, preview: Boolean = false) {
        if (closed) return
        if (!preview && !previewPrepared && loadedStyle == style && tracks.isNotEmpty()) return
        tracks.forEach { it.release() }; tracks.clear()
        loadedStyle = style
        previewPrepared = preview
        try {
            // Whole-landing clips: used when stones move instantly, and by the sound comparison.
            repeat(if (preview) 1 else GROUP_CLIPS) { voice ->
                load(ImpactSounds.render(style, if (preview) deep else true, if (preview) balls else voice / 2 + 1, voice % 2))
            }
            // One short voice per real contact, a few per kind so quick knocks can overlap.
            if (!preview) for (kind in ContactKind.entries) repeat(CONTACT_VOICES) { voice ->
                load(ImpactSounds.renderContact(style, kind, voice % 3))
            }
        } catch (_: Exception) { release() } // Feedback must never prevent play.
    }
    @Synchronized fun impact(index: Int, style: ImpactStyle, gain: Float = 0.8f, balls: Int = 1) {
        if (tracks.isEmpty() || loadedStyle != style) return
        try {
            val track = tracks[if (previewPrepared) 0 else (balls.coerceIn(1, 4) - 1) * 2 + (next++ % 2)]
            track.stop()
            track.reloadStaticData()
            track.setVolume(gain.coerceIn(0f, 1f) * if (index == 0) 1f else 0.68f)
            track.play()
        } catch (_: IllegalStateException) { }
    }
    /** [pitch] is a playback-rate factor near 1, so repeated knocks are not identical. */
    @Synchronized fun contact(kind: ContactKind, style: ImpactStyle, volume: Float, pitch: Float) {
        if (previewPrepared || loadedStyle != style || tracks.size < GROUP_CLIPS + ContactKind.entries.size * CONTACT_VOICES) return
        try {
            val track = tracks[GROUP_CLIPS + kind.ordinal * CONTACT_VOICES + contactCursor[kind.ordinal]++ % CONTACT_VOICES]
            track.stop()
            track.reloadStaticData()
            track.setVolume(volume.coerceIn(0f, 1f))
            track.playbackRate = (ImpactSounds.rate * pitch).toInt()
            track.play()
        } catch (_: IllegalStateException) { }
    }
    @Synchronized fun stop() { tracks.forEach { runCatching { it.stop() } } }
    @Synchronized fun release() { closed = true; tracks.forEach { it.release() }; tracks.clear() }

    private companion object {
        const val GROUP_CLIPS = 8
        const val CONTACT_VOICES = 4
    }
}

/**
 * A very light tick sent as an explicit effect. The system's key-press haptic is not used
 * because many people switch touch vibration off, which silently disabled it.
 */
internal class GentleHaptics(context: Context) {
    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else context.getSystemService(Vibrator::class.java)
    }.getOrNull()?.takeIf { it.hasVibrator() }
    private val tick = Build.VERSION.SDK_INT >= 30 &&
        vibrator?.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK) == true

    /** [strength] 0..1; even 1 is only the device's lightest crisp effect. */
    fun tap(strength: Float) {
        val device = vibrator ?: return
        try {
            val effect = if (tick && Build.VERSION.SDK_INT >= 30)
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, strength.coerceIn(0.05f, 1f)).compose()
            else VibrationEffect.createOneShot(8, if (device.hasAmplitudeControl())
                (strength * 90).toInt().coerceIn(1, 255) else VibrationEffect.DEFAULT_AMPLITUDE)
            if (Build.VERSION.SDK_INT >= 33)
                device.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_MEDIA))
            else device.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).build())
        } catch (_: Exception) { } // Feedback must never prevent play.
    }
}

@Stable
internal class GameFeedback(
    context: Context,
    private val impactAction: (sound: Boolean, haptics: Boolean, LandingImpact, ImpactStyle) -> Unit,
    private val contactAction: (StoneContact, ImpactStyle) -> Unit = { _, _ -> },
    private val tapAction: (strength: Float) -> Unit = {},
) {
    private val prefs = context.getSharedPreferences("feedback", Context.MODE_PRIVATE)
    var sound by mutableStateOf(prefs.getBoolean("sound", true))
        private set
    var haptics by mutableStateOf(prefs.getBoolean("haptics", false))
        private set
    var style by mutableStateOf(ImpactStyle.entries.firstOrNull { it.name == prefs.getString("style", "") } ?: ImpactStyle.RIVER)
        private set
    fun style(value: ImpactStyle) { style = value; prefs.edit().putString("style", value.name).apply() }
    fun sound(value: Boolean) { sound = value; prefs.edit().putBoolean("sound", value).apply() }
    fun haptics(value: Boolean) {
        haptics = value; prefs.edit().putBoolean("haptics", value).apply()
        if (value) tapAction(0.5f) // Lets the player feel what they just switched on.
    }
    /** A whole landing in one clip: stones that move instantly have no contacts to hear. */
    fun impact(event: LandingImpact) { impactAction(sound, haptics, event, style) }
    /** A group touching down in animated play. Its sound comes from [contact]. */
    fun landing(event: LandingImpact) { impactAction(false, haptics, event, style) }
    fun contact(event: StoneContact) { if (sound) contactAction(event, style) }
}

@Composable
internal fun rememberGameFeedback(): GameFeedback {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val audio = remember { StoneAudio() }
    val haptics = remember { GentleHaptics(context.applicationContext) }
    val feedback = remember(lifecycle) {
        val lastContact = LongArray(ContactKind.entries.size)
        val random = java.util.Random()
        // Game sound follows media volume and the in-game switch only. The ringer mode is not
        // consulted: a phone kept on vibrate with earphones in should still be heard.
        fun audible() = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        GameFeedback(context, impactAction = { sound, haptic, event, style ->
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                if (sound && audible()) FeedbackThread.post { audio.impact(0, style, (0.7f + 0.12f * sqrt(event.dropSlots.coerceAtLeast(0f))).coerceIn(0.75f, 1f), event.balls) }
                if (haptic) FeedbackThread.post { haptics.tap((0.25f + 0.07f * sqrt(event.dropSlots.coerceAtLeast(0f))).coerceAtMost(0.55f)) }
            }
        }, contactAction = { contact, style ->
            val now = SystemClock.uptimeMillis()
            // A group's stones knock together within a few milliseconds; one voice per kind
            // is enough for that instant, and later bounces are heard separately.
            if (audible() && now - lastContact[contact.kind.ordinal] >= CONTACT_GAP_MS) {
                lastContact[contact.kind.ordinal] = now
                val volume = contactVolume(contact); val pitch = 0.96f + random.nextFloat() * 0.08f
                FeedbackThread.post { audio.contact(contact.kind, style, volume, pitch) }
            }
        }, tapAction = { strength -> FeedbackThread.post { haptics.tap(strength) } })
    }
    LaunchedEffect(audio, feedback.style) { val style = feedback.style; withContext(Dispatchers.IO) { audio.prepare(style) } }
    DisposableEffect(audio, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) FeedbackThread.post { audio.stop() } }
        lifecycle.addObserver(observer)
        // After any sounds still waiting to start, so none starts on a released track.
        onDispose { lifecycle.removeObserver(observer); FeedbackThread.post { audio.release() } }
    }
    LaunchedEffect(feedback.sound) { if (!feedback.sound) FeedbackThread.post { audio.stop() } }
    return feedback
}

private const val CONTACT_GAP_MS = 35L

/** Faster knocks are louder, on a curve that keeps small settling taps audible but soft. */
internal fun contactVolume(contact: StoneContact): Float {
    val energy = ((contact.speedSlots - SettlingColumns.MIN_CONTACT_SLOTS) / 45f).coerceIn(0f, 1f).pow(0.6f)
    val kind = when (contact.kind) { ContactKind.FLOOR -> 1f; ContactKind.STONE -> 0.85f; ContactKind.OVERHEAD -> 0.7f }
    return (0.16f + 0.84f * energy) * kind
}
