package com.example.ballsortgame.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.example.ballsortgame.game.GameUiState
import com.example.ballsortgame.game.GameViewModel
import com.example.ballsortgame.game.LevelCatalog
import com.example.ballsortgame.ui.theme.BallSortGameTheme

/** UI events stay separate from state so the actual screen can be previewed and tested. */
data class GameActions(
    val tapTube: (Int) -> Unit = {},
    val finishTransfer: (Long) -> Unit = {},
    val undo: () -> Unit = {},
    val restart: () -> Unit = {},
    val hint: () -> Unit = {},
    val cancelHint: () -> Unit = {},
    val chooseLevel: (Int) -> Unit = {},
    val settings: (Boolean, Boolean) -> Unit = { _, _ -> },
    val dismissRecovery: () -> Unit = {},
    val recover: () -> Unit = {},
)

@Composable
fun GameScreen(state: GameUiState, model: GameViewModel, showBrowse: Boolean = true, showBackdrop: Boolean = true, browse: (() -> Unit)? = null) {
    val actions = remember(model) {
        GameActions(
            model::tapTube, model::finishTransfer, model::undo, model::restart,
            model::hint, model::cancelHint, model::chooseLevel, model::settings,
            model::dismissRecovery, model::recover,
        )
    }
    GameContent(state, model.levels, actions, browse = browse, showBrowse = showBrowse, showBackdrop = showBackdrop)
}

@Preview(name = "Phone", widthDp = 400, heightDp = 840, showBackground = true)
@Preview(name = "Small phone", widthDp = 320, heightDp = 640, showBackground = true)
@Preview(name = "Landscape", widthDp = 840, heightDp = 400, showBackground = true)
@Preview(name = "Large text", widthDp = 400, heightDp = 840, fontScale = 1.5f, showBackground = true)
@Composable
fun PlaytestPreview() {
    BallSortGameTheme {
        GameContent(
            GameUiState(loading = false, levelIndex = 6, board = LevelCatalog.levels[6].initial),
            LevelCatalog.levels, GameActions(),
        )
    }
}
