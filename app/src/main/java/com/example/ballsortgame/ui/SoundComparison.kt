package com.example.ballsortgame.ui

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
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
    NightDialog(onDismissRequest = dismiss, title = { Text("Stone sound") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Choose how the stones sound when they land.")
            ImpactStyle.entries.forEach { style ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .selectable(selected == style, role = Role.RadioButton) { stop(); styleName = style.name }
                    .semantics(mergeDescendants = true) { contentDescription = style.title }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    NightRadio(selected == style)
                    Column {
                        Text(style.title, color = LocalWorldLook.current.light)
                        Text(style.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NightChip("Single", !group) { stop(); group = false }
                NightChip("3 balls", group) { stop(); group = true }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NightChip("Shallow", !deep) { stop(); deep = false }
                NightChip("Deep", deep) { stop(); deep = true }
            }
            Text("Preview volume")
            NightSlider(volume, { stop(); volume = it }, modifier = Modifier.semantics { contentDescription = "Preview volume" })
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                NightButton("Play", Modifier.widthIn(min = 112.dp), onClick = {
                    task?.cancel()
                    player.stop()
                    val sampleStyle = selected
                    val sampleDeep = deep
                    val sampleCount = if (group) 3 else 1
                    message = "Preparing…"
                    task = scope.launch {
                        withContext(Dispatchers.IO) { player.prepare(sampleStyle, sampleDeep, sampleCount, preview = true) }
                        if (!player.ready) message = "Audio unavailable on this device."
                        else if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                            player.impact(0, sampleStyle, volume)
                            message = sampleStyle.title + if (sampleCount == 1) " · single drop" else " · three-ball contact"
                        }
                    }
                })
                NightDialogAction("Stop") { stop() }
            }
            Text(message, style = MaterialTheme.typography.bodySmall)
            Text("The preview plays even when stone sounds are off, at your media volume.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }, confirmButton = {
        NightDialogAction("Use ${selected.title}", primary = true) { feedback.style(selected); dismiss() }
    }, dismissButton = { NightDialogAction("Back", onClick = dismiss) })
}
