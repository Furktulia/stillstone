# Ball Sort Game

A calm, offline Android ball-sort puzzle game built with Kotlin and Jetpack Compose.
This is a working playtest app, not yet a finished release.

**New agent: start with [the handoff](docs/agent-handoff.md) and
[the current remaining queue](docs/next-steps.md).**
[AGENTS.md](AGENTS.md) contains collaboration and workspace precautions.

## Current build

- Android app module: `app`, minimum API 29, target/compile API 37.
- Pure Kotlin rules, solving, generation and desktop authoring: `puzzle-engine`.
- 32 curated puzzles across Dusk, Moonlight and Midnight; all available for testing.
- Seeded offline Infinite, hints, undo/restart, saved boards/history and example export.
- Nighttime world entrances, level selection, stone controls and generated art.
- Ball motion and sound are still being refined; known physics issues are documented.

## Build and test on Windows

Use Android Studio's configured Gradle JDK. From the project root:

~~~powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :puzzle-engine:test :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:lintDebug
~~~

Run app on the selected emulator through Android Studio.
Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
Do not uninstall or clear data when installing an update: player saves and
Infinite examples are private app data.

Art/font decoding is part of the offline Gradle build; no API key or runtime
download is required. Sync Gradle after build-script changes.

## Documentation map

| Document | Purpose |
| --- | --- |
| [Agent handoff](docs/agent-handoff.md) | Product intent, decisions, source map, known issues, validation limits |
| [Next steps](docs/next-steps.md) | Prioritized current backlog and optional ideas |
| [World UI](docs/world-ui.md) | Approved visual direction, rendering, assets and recent UI checks |
| [Feel playtest](docs/feel-playtest.md) | Movement, routing, synthetic sound and phone feedback |
| [Infinite mode](docs/infinite-mode.md) | Seed codes, examples, storage/export and limitations |
| [Engine README](puzzle-engine/README.md) | Rules, solver/generator contracts and lab commands |
| [Level workshop](docs/level-workshop.md) | Offline generation, review and pack selection |
| [Challenge selection](docs/challenge-selection.md) | Decision-based screening and human-feedback references |
| [Difficulty investigation](docs/difficulty-investigation.md) | Capacity/difficulty study and limits |
| [Algorithm validation](docs/algorithm-validation.md) | Correctness investigations and historical checks |
| [Playtesting](docs/playtesting.md) | Earlier playable milestone and save-retention precautions |
| [Original product plan](docs/product-plan.md) | Historical discovery/milestones; not the current completion status |

Validation counts in individual documents describe their particular milestone.
Read the handoff before interpreting old “not implemented” or “not tested” statements.
