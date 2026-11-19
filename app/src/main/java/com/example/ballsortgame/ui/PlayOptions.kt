package com.example.ballsortgame.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Ways of playing that apply to every puzzle, campaign and Infinite alike. One shared
 * holder, because the settings panel can be opened from several screens at once.
 */
internal object PlayOptions {
    private var loaded = false

    /** Only a tube's top stone shows its colour until the stones under it have been uncovered. */
    var hiddenColors by mutableStateOf(false)
        private set

    var waterBeads by mutableStateOf(false)
        private set

    fun waterBeads(context: Context, enabled: Boolean) {
        waterBeads = enabled
        context.getSharedPreferences("mode", Context.MODE_PRIVATE).edit().putBoolean("waterBeads", enabled).apply()
    }

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val preferences = context.getSharedPreferences("mode", Context.MODE_PRIVATE)
        waterBeads = preferences.getBoolean("waterBeads", false)
        hiddenColors = preferences.getBoolean("hidden", false)
    }

    fun hiddenColors(context: Context, value: Boolean) {
        hiddenColors = value
        context.getSharedPreferences("mode", Context.MODE_PRIVATE).edit().putBoolean("hidden", value).apply()
    }
}
