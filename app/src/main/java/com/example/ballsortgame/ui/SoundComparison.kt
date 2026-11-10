package com.example.ballsortgame.ui

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.*

@Composable
internal fun SoundComparison(feedback: GameFeedback, dismiss: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val manager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val player = remember { StoneAudio() }
    val scope = rememberCoroutineScope()
    var task by remember { mutableStateOf<Job?>(null) }
    var styleName by rememberSaveable { mutableStateOf(feedback.style.name) }
    val selected = ImpactStyle.valueOf(styleName)
    var deep by rememberSaveable { mutableStateOf(false) }
    var group by rememberSaveable { mutableStateOf(false) }
    var volume by rememberSaveable { mutableFloatStateOf(0.65f) }
    var message by remember { mutableStateOf("Choose a sound, then press Play.") }
    fun stop() { task?.cancel(); player.stop(); message = "Stopped." }
    DisposableEffect(player, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) { task?.cancel(); player.stop() }
        }
        lifecycle.addObserver(observer)
        onDispose { task?.cancel(); lifecycle.removeObserver(observer); player.release() }
    }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Compare drop sounds") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Three synthetic material studies. Use the same drop and volume to compare their character.")
            ImpactStyle.entries.forEach { style ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected == style, onClick = { stop(); styleName = style.name },
                        modifier = Modifier.semantics { contentDescription = style.title })
                    Column {
                        Text(style.title)
                        Text(style.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!group, { stop(); group = false }, label = { Text("Single") })
                FilterChip(group, { stop(); group = true }, label = { Text("3 balls") })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!deep, { stop(); deep = false }, label = { Text("Shallow") })
                FilterChip(deep, { stop(); deep = true }, label = { Text("Deep") })
            }
            Text("Preview volume")
            Slider(volume, { stop(); volume = it }, modifier = Modifier.semantics { contentDescription = "Preview volume" })
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    task?.cancel()
                    player.stop()
                    val sampleStyle = selected
                    val sampleDeep = deep
                    val sampleCount = if (group) 3 else 1
                    message = "Preparing…"
                    task = scope.launch {
                        withContext(Dispatchers.IO) { player.prepare(sampleStyle, sampleDeep, sampleCount, preview = true) }
                        if (!player.ready) message = "Audio unavailable on this device."
                        else if (manager.ringerMode != AudioManager.RINGER_MODE_NORMAL) message = "Switch the phone to normal ringer mode to hear the preview."
                        else if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                            player.impact(0, sampleStyle, volume)
                            message = sampleStyle.title + if (sampleCount == 1) " · single drop" else " · three-ball contact"
                        }
                    }
                }) { Text("Play") }
                TextButton(onClick = { stop() }) { Text("Stop") }
            }
            Text(message, style = MaterialTheme.typography.bodySmall)
            Text("Preview works even if game sounds are off. Media volume and silent mode still apply. Group clips add quieter contact taps; this is a sound study, not new collision physics.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }, confirmButton = {
        TextButton(onClick = { feedback.style(selected); dismiss() }) { Text("Use ${selected.title}") }
    }, dismissButton = { TextButton(onClick = dismiss) { Text("Back") } })
}
