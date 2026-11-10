package com.example.ballsortgame.ui

import android.content.Context
import android.media.*
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Small locally synthesized stone taps. No downloads, music, or audio service. */
internal class StoneAudio {
    private val tracks = mutableListOf<AudioTrack>()
    private var next = 0
    private var closed = false
    private var loadedStyle: ImpactStyle? = null
    private var previewPrepared = false
    val ready: Boolean @Synchronized get() = tracks.isNotEmpty()
    @Synchronized fun prepare(style: ImpactStyle, deep: Boolean = true, balls: Int = 1, preview: Boolean = false) {
        if (closed) return
        if (!preview && !previewPrepared && loadedStyle == style && tracks.isNotEmpty()) return
        tracks.forEach { it.release() }; tracks.clear()
        loadedStyle = style
        previewPrepared = preview
        try {
            repeat(if (preview) 1 else 8) { voice ->
                val rate = ImpactSounds.rate
                val pcm = ImpactSounds.render(style, if (preview) deep else true, if (preview) balls else voice / 2 + 1, voice % 2)
                val track = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.size * 2).build()
                tracks.add(track)
                check(track.write(pcm, 0, pcm.size) == pcm.size)
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
    @Synchronized fun stop() { tracks.forEach { runCatching { it.stop() } } }
    @Synchronized fun release() { closed = true; tracks.forEach { it.release() }; tracks.clear() }
}

@Stable
internal class GameFeedback(context: Context, private val impactAction: (Boolean, Boolean, LandingImpact, ImpactStyle) -> Unit) {
    private val prefs = context.getSharedPreferences("feedback", Context.MODE_PRIVATE)
    var sound by mutableStateOf(prefs.getBoolean("sound", true))
        private set
    var haptics by mutableStateOf(prefs.getBoolean("haptics", false))
        private set
    var style by mutableStateOf(ImpactStyle.entries.firstOrNull { it.name == prefs.getString("style", "") } ?: ImpactStyle.PEBBLE)
        private set
    fun style(value: ImpactStyle) { style = value; prefs.edit().putString("style", value.name).apply() }
    fun sound(value: Boolean) { sound = value; prefs.edit().putBoolean("sound", value).apply() }
    fun haptics(value: Boolean) { haptics = value; prefs.edit().putBoolean("haptics", value).apply() }
    fun impact(event: LandingImpact) { impactAction(sound, haptics, event, style) }
}

@Composable
internal fun rememberGameFeedback(): GameFeedback {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val audio = remember { StoneAudio() }
    val manager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val feedback = remember(view, lifecycle) {
        GameFeedback(context) { sound, haptic, event, style ->
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                if (sound && manager.ringerMode == AudioManager.RINGER_MODE_NORMAL) audio.impact(0, style, (0.5f + 0.16f * kotlin.math.sqrt(event.dropSlots.coerceAtLeast(0f))).coerceIn(0.55f, 0.9f), event.balls)
                if (haptic) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
        }
    }
    LaunchedEffect(audio, feedback.style) { val style = feedback.style; withContext(Dispatchers.IO) { audio.prepare(style) } }
    DisposableEffect(audio, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) audio.stop() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); audio.release() }
    }
    LaunchedEffect(feedback.sound) { if (!feedback.sound) audio.stop() }
    return feedback
}
