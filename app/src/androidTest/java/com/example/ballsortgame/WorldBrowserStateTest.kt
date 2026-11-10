package com.example.ballsortgame

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import com.example.ballsortgame.game.*
import com.example.ballsortgame.ui.WorldPicker
import com.example.ballsortgame.ui.theme.BallSortGameTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WorldBrowserStateTest {
    @get:Rule val compose = createComposeRule()
    @Test fun infiniteAndResumeAreSeparateEntrancesInTheScrollableBrowser() {
        val levels = LevelCatalog.levels
        val state = GameUiState(loading = false, board = levels.first().initial)
        var infinite = 0
        var resumed = 0
        compose.setContent {
            BallSortGameTheme {
                WorldPicker(levels, state, { resumed++ }, choose = {},
                    onInfinite = { infinite++ })
            }
        }
        val viewport = compose.onNodeWithTag("world_list").getUnclippedBoundsInRoot()
        val gear = compose.onNodeWithTag("browser_settings").getUnclippedBoundsInRoot()
        assertEquals("Menu must scroll to the screen edge, behind the floating gear", 0f, viewport.top.value, .01f)
        assertTrue(gear.bottom > viewport.top)
        compose.onNodeWithTag("world_list").performScrollToNode(hasTestTag("world_endless"))
        compose.onNodeWithTag("world_endless").assertIsDisplayed().performClick()
        compose.onNodeWithTag("browser_settings").assertIsDisplayed().assertIsEnabled()
        assertEquals(1, infinite)
        assertEquals(0, resumed)
        compose.onNodeWithTag("world_list").performScrollToNode(hasTestTag("world_close"))
        compose.onNodeWithTag("world_close").assertIsDisplayed().performClick()
        assertEquals(1, resumed)
    }

    @Test fun scrollingEntrancesAndSelectedWorldSurviveStateRestoration() {
        val restoration = StateRestorationTester(compose)
        val levels = LevelCatalog.levels
        val state = GameUiState(loading = false, board = levels.first().initial)
        var chosen = -1
        restoration.setContent {
            BallSortGameTheme { WorldPicker(levels, state, {}, { chosen = it }) }
        }
        for (id in listOf("three", "four", "five")) {
            compose.onNodeWithTag("world_list").performScrollToNode(hasTestTag("world_$id"))
            compose.onNodeWithTag("world_$id").assertIsDisplayed().assertIsEnabled()
        }
        compose.onNodeWithTag("world_five").performClick()
        compose.onNodeWithText("Midnight").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Midnight").assertIsDisplayed()
        compose.onNodeWithTag("level_list").performScrollToNode(hasTestTag("level_31"))
        compose.onNodeWithTag("level_31").performClick()
        assertEquals(31, chosen)
    }
}
