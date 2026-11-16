package com.example.ballsortgame

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.ballsortgame.game.AtomicGameSaveStore
import com.example.ballsortgame.game.GameViewModel
import com.example.ballsortgame.game.LevelCatalog
import com.example.ballsortgame.ui.GameHub
import com.example.ballsortgame.ui.applyOrientationPolicy
import com.example.ballsortgame.ui.theme.BallSortGameTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Folding or unfolding recreates the activity, so this is decided again for each screen.
        applyOrientationPolicy()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        window.isNavigationBarContrastEnforced = false
        val model = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(GameViewModel::class.java))
                return GameViewModel(LevelCatalog.levels, AtomicGameSaveStore(applicationContext)) as T
            }
        })[GameViewModel::class.java]
        setContent {
            BallSortGameTheme {
                GameHub(model, this@MainActivity)
            }
        }
    }
}
