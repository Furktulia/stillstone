# Earlier playable milestone: playtesting notes

Historical snapshot. The level/save guidance and test-retention warning remain useful,
but some behavior and test counts below have been superseded. Sounds, haptics,
Infinite and newer UI now exist; tube taps during transfers are responsive.
For current status start with [agent-handoff.md](agent-handoff.md),
[next-steps.md](next-steps.md), [world-ui.md](world-ui.md) and
[feel-playtest.md](feel-playtest.md).

The game now contains 32 solution-verified puzzles across three freely available
worlds: Dusk (three slots), Moonlight (four slots), and Midnight (five slots).
Dusk has ten puzzles; Moonlight and Midnight each have eleven. All previous thirty
puzzles retain their boards, certificates and save IDs. Level 11 in each later world
is a newly screened challenge; Dusk 10 remains the comparison reference.
This is a playtest collection; final world sizes, unlocking, materials and difficulty
calibration are still open.

## Run

The Android 37 emulator is now working. Select app and the emulator in Android
Studio, then Run. The debug APK is app/build/outputs/apk/debug/app-debug.apk.

~~~powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :puzzle-engine:test :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:lintDebug
~~~

First launch opens Dusk level 1. Later launches restore the active puzzle.
Worlds opens the world cards and level grid. All puzzles can be skipped/replayed;
there are no gates or ratings. Each world's displayed numbering starts at 1.

See level-workshop.md for PC generation, candidate review, edited selection and
pack validation. playtest-feedback.csv maps current world/local numbers to stable
IDs and provides space for feedback.

## Manual playtest

1. Dusk level 1: tap tube 1, then tube 2; both matching pieces should transfer.
2. Undo should return to the initial board. Solve again and continue.
3. Hint selects its source and highlights its destination. Tap the destination
   directly, or tap the source once then the destination. The source remains
   selected after confirming the hint. Ordinary repeated source taps deselect it.
4. Make a move in Dusk level 2, visit a different world, then return. The board
   and undo history should be retained. Restart confirmation resets only that board.
5. Visit Midnight immediately, including level 10, without completing earlier worlds.
6. Complete a puzzle, reopen Worlds, and check the Sorted marker and world count.
7. Enable color labels and reduced motion. Settings should preserve the board.
8. Close/reopen and verify active world/level, arrangements, settings and undo.
9. Rotate with a puzzle open and with the world/level browser open. Check layout,
   current page retention and reachability with large text.
10. Try hints after moves away from the stored certificate. Proven impossible
    positions and search-budget limits must have different wording; recovery
    should return to a known-solvable history position.
11. Play a sequence within each world and record whether transitions feel fair,
    engaging, repetitive or frustrating. Larger tubes should feel like variety;
    the first puzzle in each new capacity is deliberately simpler.

Tube numbers run left-to-right, top-to-bottom. There are no visible move targets,
timers, lives, currency or pressure systems. Sounds, haptics, endless mode, rewards,
mixed-capacity campaign levels and final artwork are deferred.

## Checks actually performed

87 local tests pass (71 engine, 16 app), including replay/undo of all 32 certificates,
exact shortest-length checks, structural uniqueness, pack parsing/selection,
exhausted authoring batches, old-save compatibility, save-codec corruption and
board geometry. 11 emulator UI tests pass, including world entry/selection,
cross-world board retention, completion, the hint-selection regression and both
new finales played to completion with actual Hint/tube/Undo interactions.

Android debug build and lint finish successfully. Existing dependency/update and
template warnings remain; the older Lifecycle lint registry skips two custom checks.
World cards and the level grid were visually inspected on the Android 37 emulator.
This does not establish phone performance, human difficulty or release readiness.
Process-death/AtomicFile durability, rotation/large-text combinations and physical
touch/audio/haptics need further hands-on milestone testing.

## Emulator test retention

The default Gradle instrumentation cleanup uninstalled the app and removed the
playtest emulator's save. This was discovered and corrected during the finale
milestone. gradle.properties now sets
android.injected.androidTest.leaveApksInstalledAfterRun=true.
A real saved completion was created, its SHA-256 recorded, all 11 emulator tests
rerun, and the unchanged save hash verified afterward. The earlier removed save
could not be recovered. The UI tests themselves use an in-memory store.
Do not enable test-runner package-data clearing on the playtest device.

## State and saving

GameSession is pure Kotlin and shares the rules engine. GameViewModel owns the
session across activity recreation. Hints use verified cached continuations, then
a cancellable worker-thread solver with a 750 ms/state-count budget. Limited search
never claims impossibility; stale results are ignored when the board changes.

Accepted logical moves are atomic. Animation presents an accepted move; Undo can
cancel the visual safely. Tube taps during travel are ignored. Reduced motion
skips travel animation.

A serialized IO worker writes the latest progress via AtomicFile. Save format 1
stores stable level IDs, histories, completion and settings. Incompatible histories
reset individually; unreadable saves start fresh with a notice. Clearing app storage
or uninstalling removes progress.
