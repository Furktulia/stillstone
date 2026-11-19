package com.example.ballsortgame.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.Until
import com.example.ballsortgame.engine.BundledLevelPacks
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

/**
 * Plays the game the way a new player does, so the code it runs is compiled ahead of time on
 * install: opening the game, playing the first Dusk levels, a hint, the level path, the world
 * journey, another world and Infinite.
 *
 * Runs only on the managed emulator set up in build.gradle.kts, never on a player's device:
 * it changes the save. Generate with `.\gradlew.bat :app:generateBaselineProfile`.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    private val dusk = BundledLevelPacks.playtest().levels.filter { it.worldId == "three" }

    @Test fun generate() = rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        // A run after the first one may open wherever the last one stopped: go to Dusk 1.
        if (!device.wait(Until.hasObject(By.text(Pattern.compile("Dusk\\W+1"))), 3_000)) openWorld("Dusk") { level(1) }
        for (level in dusk.take(4)) {
            click(By.desc("Replay"), wait = 500) // A level finished in an earlier run is played again.
            for (move in level.solution) {
                check(click(By.desc("Tube ${move.source + 1}"))) { "No tube ${move.source + 1}: " + screen() }
                check(click(By.desc("Tube ${move.destination + 1}"))) { "No tube ${move.destination + 1}: " + screen() }
            }
            check(click(By.textStartsWith("Next puzzle"), wait = 8_000)) { "${level.id} did not finish: " + screen() }
        }
        click(By.desc("Hint"))
        device.wait(Until.hasObject(By.textContains("glowing")), 5_000)

        device.pressBack() // The level path.
        scrollAll()
        device.pressBack() // The world journey.
        scrollAll()
        openWorld("Moonlight") { scrollAll(); device.pressBack() }
        if (click(By.desc(Pattern.compile("Infinite.*")))) device.wait(Until.hasObject(By.desc("Tube 1")), 10_000)
        // End where the next run begins: Dusk 1 in the campaign, so every run starts the same way.
        openWorld("Dusk") { level(1) }
    }

    /**
     * Clicks what [selector] finds, looked up again if the screen replaced it meanwhile; while it is
     * not there, scrolls the screen's list towards [scroll]. False when it never appears.
     */
    private fun MacrobenchmarkScope.click(selector: BySelector, wait: Long = 5_000, scroll: Direction? = null): Boolean {
        repeat(if (scroll == null) 3 else 8) {
            val target = device.wait(Until.findObject(selector), if (scroll == null) wait else 1_500)
            if (target == null) {
                if (scroll == null) return false
                try { device.findObject(By.scrollable(true))?.scroll(scroll, 0.8f) } catch (_: StaleObjectException) { }
                return@repeat
            }
            try { target.click(); device.waitForIdle(); return true } catch (_: StaleObjectException) {
                // Gone mid-click: usually because the click worked and the screen moved on.
                device.waitForIdle()
                if (!device.hasObject(selector)) return true
            }
        }
        return false
    }

    private fun MacrobenchmarkScope.level(number: Int) {
        // The path opens where the player is; level 1 may be above the screen.
        check(click(By.desc(Pattern.compile("Level $number(,.*)?")), scroll = Direction.UP)) { "No level $number: " + screen() }
        device.wait(Until.hasObject(By.desc("Tube 1")), 5_000)
    }

    /** From any screen: back out to the world journey, then into [name]. */
    private fun MacrobenchmarkScope.openWorld(name: String, then: MacrobenchmarkScope.() -> Unit) {
        val world = By.desc(Pattern.compile("$name\\..*"))
        repeat(5) {
            if (device.hasObject(world)) return@repeat
            // Back from the first level can leave the game: open it again.
            if (device.currentPackageName != PACKAGE) startActivityAndWait() else device.pressBack()
            device.waitForIdle()
        }
        check(click(world, scroll = Direction.DOWN)) { "No world $name: " + screen() }
        then()
    }

    /** Flings the screen's list down and back up, looked up again for every fling. */
    private fun MacrobenchmarkScope.scrollAll() {
        for (direction in listOf(Direction.DOWN, Direction.DOWN, Direction.UP, Direction.UP)) {
            val list = device.findObject(By.scrollable(true)) ?: return
            try {
                list.setGestureMargin(device.displayWidth / 5)
                list.fling(direction)
            } catch (_: StaleObjectException) { }
            device.waitForIdle()
        }
    }

    /** The labels on screen, for error messages: this emulator cannot be watched. */
    private fun MacrobenchmarkScope.screen(): String {
        val out = java.io.ByteArrayOutputStream()
        device.dumpWindowHierarchy(out)
        return Regex("(?:text|content-desc)=\"([^\"]+)\"").findAll(out.toString()).map { it.groupValues[1] }.distinct().joinToString(" | ") +
            " (package " + device.currentPackageName + ")"
    }

    private companion object { const val PACKAGE = "com.example.ballsortgame" }
}
