package com.example.ballsortgame.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** How hard Infinite puzzles are, in the player's words. The letters are the puzzle-code recipes. */
internal enum class InfiniteDifficulty(val letter: Char, val title: String, val explanation: String) {
    RELAXED('R', "Relaxed", "A simple approach nearly always works."),
    BALANCED('B', "Balanced", "Some choices matter, and a few lead to dead ends."),
    CHALLENGE('C', "Challenge", "Tempting moves can lose the puzzle. Expect to use Undo.");

    companion object {
        fun of(letter: Char) = entries.firstOrNull { it.letter == letter } ?: BALANCED
    }
}

/**
 * Infinite's difficulty as three quiet pills under the header, in the same style as a level's
 * mode bubbles, so it can be seen and changed without opening a menu. On the first visit a note
 * says what Infinite is; like a mode's first note it lets taps through and closes at the first move.
 */
@Composable
internal fun DifficultyPills(current: Char, next: Char, moved: Boolean, choose: (InfiniteDifficulty) -> Unit) {
    val look = LocalWorldLook.current
    val context = LocalContext.current.applicationContext
    var welcome by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { welcome = firstVisit(context) }
    LaunchedEffect(moved) { if (moved) welcome = false }
    Box(Modifier.fillMaxWidth().padding(top = 2.dp), contentAlignment = Alignment.Center) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            for (difficulty in InfiniteDifficulty.entries) {
                // Filled: the puzzle being played. Outlined: chosen for the next puzzle.
                val chosen = difficulty.letter == current
                val pending = !chosen && difficulty.letter == next
                val shape = RoundedCornerShape(50)
                Text(difficulty.title,
                    Modifier.clip(shape)
                        .background(lerp(look.base, look.light, if (chosen) .17f else .06f))
                        .border(1.dp, look.accent.copy(alpha = if (chosen || pending) .55f else .18f), shape)
                        .clickable(role = Role.Button) { welcome = false; choose(difficulty) }
                        .semantics { this.selected = chosen }
                        .testTag("difficulty_" + difficulty.letter)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = look.light.copy(alpha = if (chosen) .95f else if (pending) .8f else .62f), maxLines = 1)
            }
        }
        if (welcome) ExplanationNote("Infinite",
            "New puzzles without end, made fresh each time. Choose how hard they are with these.",
            40.dp, Modifier.testTag("infinite_note"), letTapsThrough = true) { welcome = false }
    }
}

private fun firstVisit(context: Context): Boolean {
    val prefs = context.getSharedPreferences("onboarding", Context.MODE_PRIVATE)
    if (prefs.getBoolean("met_infinite", false)) return false
    prefs.edit().putBoolean("met_infinite", true).apply()
    return true
}
