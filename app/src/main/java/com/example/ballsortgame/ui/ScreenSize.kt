package com.example.ballsortgame.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Screens whose shorter side is at least this wide are "large": an unfolded foldable or a
 * tablet. Every phone the game is played on is narrower, so phone layouts never take the
 * large-screen branches.
 */
internal const val LargeScreenDp = 600

internal fun isLargeScreen(configuration: Configuration) = configuration.smallestScreenWidthDp >= LargeScreenDp

/** True on an unfolded foldable or tablet; false on every phone, whichever way it is held. */
@Composable
internal fun isLargeScreen(): Boolean = isLargeScreen(LocalConfiguration.current)

/**
 * Phones stay upright: a sideways phone is too short for a five-stone tube with travel room
 * above it. Large screens follow the sensor. (Android 16+ ignores an orientation lock on large
 * screens anyway, which is why this is decided at runtime and not in the manifest.)
 */
internal fun Activity.applyOrientationPolicy() {
    val wanted = if (isLargeScreen(resources.configuration)) ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    if (requestedOrientation != wanted) requestedOrientation = wanted
}
