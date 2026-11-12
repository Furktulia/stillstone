package com.example.ballsortgame.ui

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

/**
 * Which play controls still show their name to a new player.
 *
 * A control's name stays visible until that control has been used once. Whatever is
 * left retires after a few solved puzzles, so a player who never restarts is not
 * followed by a "Restart" caption forever. Holding a control shows its name at any time.
 */
@Stable
internal class ControlIntro(context: Context) {
    private val prefs = context.getSharedPreferences("onboarding", Context.MODE_PRIVATE)
    private val used = mutableStateMapOf<NightSymbol, Boolean>().apply {
        NightSymbol.entries.forEach { put(it, prefs.getBoolean(key(it), false)) }
    }
    private var solved by mutableIntStateOf(prefs.getInt("solved_with_names", 0))

    fun showName(control: NightSymbol) = solved < PUZZLES_WITH_NAMES && used[control] != true
    val active get() = NightSymbol.entries.any(::showName)

    fun used(control: NightSymbol) {
        if (used[control] == true) return
        used[control] = true
        prefs.edit().putBoolean(key(control), true).apply()
    }

    fun puzzleSolved() {
        if (!active) return
        solved++
        prefs.edit().putInt("solved_with_names", solved).apply()
    }

    private fun key(control: NightSymbol) = "used_" + control.name.lowercase()

    companion object { const val PUZZLES_WITH_NAMES = 3 }
}

@Composable
internal fun rememberControlIntro(): ControlIntro {
    val context = LocalContext.current
    return remember(context) { ControlIntro(context) }
}
