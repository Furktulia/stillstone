# Ball Sort Game

An offline Android puzzle game built with Kotlin and Jetpack Compose. Sort matching pieces into vials across 247 campaign puzzles in Dusk, Moonlight, and Midnight, or create another puzzle in Infinite mode.

The game includes undo, hints, saved progress, optional color labels, reduced motion, and a choice of stones or water beads. Phones use a portrait layout. Large screens reflow, and a tester reported that the foldable layout worked well on a Samsung Fold 8.

## Build and test

Open the repository in Android Studio with Android SDK 37 installed. The app supports Android 10 (API 29) and later. The `app` module contains the Android UI. The `puzzle-engine` module contains the rules, solver, and offline puzzle generator.

```powershell
.\gradlew.bat :app:assemblePlaytest
.\gradlew.bat :puzzle-engine:test :app:testDebugUnitTest
```

The optimized playtest APK is written to `app/build/outputs/apk/playtest/app-playtest.apk`. Debug and playtest builds use the local Android debug key. For anyone already using a private playtest build, keep updating from the private build channel to preserve saved progress. Android requires the same signing key for in-place updates.

Art and fonts are bundled and decoded during the build. Gameplay does not require a network connection.

See [the engine README](puzzle-engine/README.md) for puzzle tooling, [the layout notes](docs/world-ui.md) for device layout details, and [the motion and audio review](docs/beads-and-impact-audio.md) for material behavior and validation.
