# Agent handoff — Ball Sort Game

Last consolidated: 2026-11-10. This document is the starting context for continuing
the game with its creator. It describes the latest session's state, not a claim
that the app is complete. Inspect current source and new user feedback before acting.

## 1. Who you are working with

The creator is a CS student who programs but started without Android experience.
They want an active implementation partner who makes reasoned recommendations,
handles much of the coding and explains important decisions without lecturing.
Work in small runnable increments and use actual feedback to steer development.
Do not replace a clear implementation request with another speculative plan.
For genuinely open product decisions, ask a small manageable group of questions.

Visual quality matters a great deal. The creator has rejected generated-looking,
incoherent or cramped art even when code/tests worked. They notice geometry,
perspective, cropping, textures, timing and unnecessary UI. Do not describe a build
as polished simply because tests pass. Minor mock mistakes such as an unwanted word
should be fixed in the app, not trigger another image generation.

The previous handoff request is documentation only. It does not authorize completing
every item below automatically. Continue from the creator's next request.

## 2. Product direction and agreed rules

A calm, satisfying offline ball-sort game, privately shared as APKs initially,
possibly Google Play later. No backend/account is needed. No timers, lives, energy,
intrusive advertisements, competitive pressure or music by default.

Tap a source and destination. Transfer ALL contiguous matching top balls that fit,
not just one ball. Destination must be empty or have the same top color.
Tubes have explicit individual capacities; contents are bottom-to-top.
Current completion means every tube is empty or FULL and monochrome.
Completed tubes remain movable. The engine permits one color in multiple full tubes;
final mixed-capacity content semantics are still open. Never silently introduce
a one-tube-per-color restriction or hardcode four slots.

Unlimited undo, restart and hints are present. Hints must work from the current
position. A hinted source can be confirmed with one tap and remain selected;
the hinted destination can also be tapped directly. No-legal-moves, proven
unsolvability and search-budget exhaustion are different outcomes.
Preserve autosaves and undo history.

Bigger tubes introduce variety, not guaranteed difficulty. The creator wants larger,
meaningfully distinct worlds, unlocked in batches eventually: introductory content,
more interesting puzzles, a new world, then returning to older worlds for harder
branches/variations. Opening a new world need not mean the previous world is nearly
finished. Alternating relaxing puzzles and genuinely hard optional challenges is
desired. Skip/replay are essential; metrics/rewards should stay unobtrusive.

All worlds/levels are currently UNLOCKED FOR PLAYTESTING. Actual gates, thresholds,
large final world sizes and mastery rewards are not implemented/finalized.
Do not add restrictions while playtesting without a new decision.

## 3. Current navigation and visual intent

First launch immediately enters Dusk's introductory puzzle, without a start/menu
button. Subsequent cold launches restore the last campaign or Infinite game.

- Campaign gameplay Back → current world's level grid → world browser.
- Infinite gameplay Back → world browser.
- Resume → previously active mode/board; browsing alone must not change that mode.
- Infinite has its own fourth entrance. Its ⋯ menu holds seed/bookmark/export tools.
- Settings uses the same minimal outline gear everywhere.
- System Back (button or edge swipe) always goes UP, never to an earlier screen
  (creator, 2026-11-14): dialog or note -> closes it; level -> its world's level list
  (Infinite -> worlds); level list -> worlds; worlds -> leaves the app. The Resume stone
  is the only way from the world screen back into the puzzle. The manifest opts in to the
  system back gesture (enableOnBackInvokedCallback).

**Superseded 2026-11-13:** the world screen is now one continuous painted journey of
four full-width stretches (WorldVistas.kt); see the first section of world-ui.md.
The entrance, mist and fog description below, down to "Level selection", is history
and those assets are no longer packaged. The creator approved it on the emulator; the play
screen, level list, Infinite options and sound panel were then restyled to match
(second section of world-ui.md) and those await review.

The browser is a tall scrolling composition of irregular depth entrances, never
rectangular image cards. Each entrance gets its own row, about 65–75% screen width
(70% as committed; a 58% soft-edged variant is under review, see next-steps.md), alternately left/right with moderate gaps. Dusk is not deliberately
larger than the rest. No Worlds heading, Infinite/Endless captions or scrollbar.
Names appear inside worlds and in accessible descriptions.

Resume is a substantial stone button below the top edge, comfortable to reach,
without the enormous empty upper half of an earlier mock.
The list now occupies the FULL window; the gear floats over it. Entrances should
disappear at the screen edges, not below the gear's row. Safe padding positions
initial/final content; it must not shrink the scrolling viewport.
Short fades at physical edges blend partially visible art into mist.

Background: dense soft slate/blue/plum mist with only sparse nature at the lowest
corners. No pond, roads, implausible edge trees or busy central scenery.
World-colored glow/clearing follows each entrance's real bounds.
Latest follow-up strengthened the purple/cyan/blue glows and added scroll-driven
fog drift plus broad moving translucent banks. No idle animation loop.
Reduced motion stops fog movement.

World identities:
- Dusk: violet garden arch, warm dusk sky and winding path.
- Moonlight: depth through willow/reeds toward lake, moon/reflection and distant hills.
- Midnight: compact old-house/window facade looking into an indigo forest/mountains.
  Use the approved v5 facade, with coherent roof overhang and paired shutters matched
  to the opening. Earlier flat/overcrowded/stretched variations were rejected.
- Infinite: pale stone arch with infinity carving and a misty receding landscape.

Level selection can be scenic, with native stylized numbered stones. Gameplay
must be quiet/minimal: world atmosphere and defocused peripheral forms rather
than a detailed illustration competing with the puzzle. Dusk should be moderately
brighter/more vibrant than the other worlds, never glaring.

All pieces should initially share one shape and nearly smooth stone/bone material.
Distinct color is primary; busy stone grain must not distract. Material should be
conveyed through restrained light/reflection, motion, weight and sound.
Jelly and different shapes/material interactions are optional later explorations.

Typography: generated mocks have no exact recoverable font metadata.
The previous implementation used Android's generic serif, which the creator disliked.
Latest code bundles Libre Baskerville unchanged, for a closer broad/bracketed serif
match on every device. Use GameDisplayFont for display text; utility text stays sans.
Stone labels auto-fit on one line. This new choice still needs creator phone review.

## 4. What exists and where to look

| Area | Files / behavior |
| --- | --- |
| Entry/navigation | app/.../MainActivity.kt and ui/GameHub.kt; saveable browser state, mode preference, lifecycle activation |
| Logical game | game/GameSession.kt, GameViewModel.kt, ProgressCodec.kt, GameSaveStore.kt |
| Rules/search/generation | puzzle-engine/.../Puzzle.kt, PuzzleSolver.kt, PuzzleGenerator.kt, SeededPuzzle.kt |
| Active campaign | LevelCatalog → BundledLevelPacks.playtest → puzzle-engine/src/main/resources/levels/playtest-v8.txt (classes and quality rules: docs/campaign-modes.md) |
| Desktop authoring | engine/tools/PuzzleLab.kt, LevelWorkshop.kt, ChallengeAssessment.kt, DifficultyStudy.kt, FinaleRefiner.kt |
| Board/presentation | ui/PuzzleBoard.kt, GroupPresentation.kt, GroupRoute.kt, TransferMotion.kt, LandingMotion.kt, BoardGeometry.kt |
| World UI | ui/WorldPicker.kt, WorldArt.kt, ArtworkLayout.kt, NightUi.kt |
| Sound/feedback | ui/GameFeedback.kt, ImpactSounds.kt, SoundComparison.kt |
| Typography | ui/theme/Type.kt; app/typography/README.md; packaged OFL license |

“app/...” above means app/src/main/java/com/example/ballsortgame/.
Engine sources are under puzzle-engine/src/main/kotlin/com/example/ballsortgame/engine/.

Since later on 2026-11-12 the shipped pack is playtest-v5.txt: these 33 classic levels, 27
more classic ones (60: Dusk 19, Moonlight 19, Midnight 22) and 80 optional mode levels with their own rules (docs/campaign-modes.md). Levels are no
longer all classic: use LevelRules / GameSession.solved, never Board.isSolved, for goals.

33 classic levels (playtest-v3.txt since 2026-11-12): Dusk (world ID three, capacity 3,
10 levels); Moonlight (four, capacity 4, 11); Midnight (five, capacity 5, 12).
The 22 relaxed levels were replaced at the creator's request with longer ones that have no
losing position; introductions and the six hard levels keep their IDs, boards and
certificates. See next-steps.md. The table row above still names playtest-v2, now the
previous pack. Hidden colours (Settings) and H1 Infinite codes are described there too.

Android: Kotlin/Compose, single app plus pure JVM engine module, minSdk 29,
target/compile 37. AGP 9 built-in Kotlin, existing coroutines/ViewModel/manual
construction. Do not add a game engine, navigation/DI/physics framework or backend
without a concrete benefit and explanation. Existing architecture remains simple.

Moves commit immediately to the logical board and save. Immutable before-board
snapshots present accepted moves. Tube taps remain responsive during animation.
All balls in a matching group travel together. Independent groups can overlap;
moves sharing a tube wait until the previous contact, NOT its whole settling time.
Routes avoid intervening tube bodies and preserve group separation around turns.
Do not return to serial ball-by-ball travel, ignored taps or accelerated queues.

## 5. Immediate known problems and desired physics

Items 1 and 2 below were addressed by ui/SettlingColumns.kt (see next-steps.md) and
need the creator's phone review; item 3 is open.
1. Rebounding balls could pass through a tube directly above.
2. A second incoming group in the same tube cancelled the first group's settling.
3. A compact 360×640 dp layout with 130% text makes five-slot gameplay very small;
   latest button-label fix avoids wrapping but does not solve this board-layout limit.

The world browser's three synthetic sliding fog banks were removed after the creator
saw a net-like pattern while scrolling; lights are now dithered native gradients and
each world entrance has a coloured fog cloud (WorldArt.kt). Needs phone review.

Current motion is authored animation, NOT a complete physical simulation.
Rebound is based on the full continuous descent, not only the drop below the rim;
it should begin immediately at landing, be visibly substantial after high falls,
possibly rise above the destination rim, and settle longer for energetic drops.
Tiny delayed wiggles were explicitly rejected.
Different groups may cross; inter-group collision handling was deferred, not solved.

In PuzzleBoard, settling pieces currently suppress an earlier group's bounce when
a later non-waiting group uses that tube. This is a useful investigation starting
point, not a complete diagnosis of every observed artifact.
Inspect runtime ownership, piece identity, dependency release and stationary-piece
reconstruction before patching curves. Keep exact puzzle state independent of
physical presentation. Test same-tube queued groups, overhead contact, large groups,
undo/restart/navigation during flight and stale completion callbacks.

Sound: Pebble/deep Pebble won the comparison; bone/wood and ceramic were disliked.
Even Pebble is provisional. Group impacts must be audibly different without
cacophony and should correspond to contact energy/material. Existing clips are
synthetic PCM studies prepared off the UI thread and reused through AudioTrack,
not licensed recordings. Minimal sound, optional gentle haptics, no default music.
Recorded licensed effects were the original preference if better sources are found.

## 6. Puzzle quality: the project's most important foundation

The creator explicitly called generation/validation “make or break.”
Use PC power to author and assess quality first, then optimize phone Infinite/hints
using measurement. A certificate proves solvability, not optimality or fun.

Solver distinguishes solved, exhausted/unsolvable, limited and cancelled.
Bounded/time-limited search must never claim impossibility or proven optimality.
Reverse construction validates each maximal forward move and replays certificates;
it often produces easy puzzles. Shuffle-and-solve broadens the desktop pool.
Desktop time-bounded acceptance may differ across machines: store actual boards
and certificates, not seeds alone.

Human reference feedback:
- Dusk 9 was easy; Dusk 10 was genuinely enjoyable/hard and must remain a reference.
  A tempting purple move into an empty tube can lose solvability, and the usual
  “take the most exposed color first” intuition fails there.
- Moonlight 10 was surprisingly straightforward despite larger capacity.
- Added Moonlight 11 was acceptable as a harder finale.
- Added Midnight 11 was very hard, with several tempting dead ends. Good as a late
  optional challenge in a larger world, not a target for every ordinary puzzle.

ChallengeAssessment uses multiple greedy strategies and bounded complete graphs
to assess decisions/temptations/counter-greedy choices. Unknowns are not proven traps.
This does not prove a capacity-dependent skill ceiling or human difficulty rating.
See challenge-selection.md and difficulty-investigation.md before changing selection.

UPDATE 2026-11-10: read puzzle-quality.md first. New Infinite puzzles use the E2 recipe
(exactly analyzed, tiered Relaxed / Balanced / Challenge, shortest solutions); the E1
description below now applies only to replaying old codes and saved examples.

Infinite is an offline playtest instrument for flights: reproducible codes,
Good/Needs work bookmarks with notes and later export. Hard can be good.
E1-B/E1-C seed meanings use deterministic work limits and capacities 3–5.
Do not silently change existing code meanings; version a new recipe if necessary.
Current Balanced uses 3 colors/2 goal spare tubes; Challenge 5 colors/1 spare.
Challenge is experimental, not a calibrated difficulty rating. Two puzzles prefetch
while foreground/active only. Mixed-capacity Infinite, advanced quality filters and
adaptive progression are not implemented. Entered codes must not silently substitute
an unrelated puzzle on failure.

## 7. Saves, exports and test precautions

Campaign: filesDir/game-progress-v1.bin, AtomicFile, versioned histories/settings.
Infinite: filesDir/endless/current.json; example-<timestamp>.json bookmarks in the
same directory. Records retain literal level pack/certificate plus played history.
A code replays the initial puzzle; a bookmark reopens the saved played position.
The user cannot browse this private app directory normally on their phone.
Use Infinite ⋯ → Saved examples / Export saved examples via Android document picker.
Export is newline-delimited JSON. No in-app importer exists yet.
Skipping replaces the active Infinite puzzle; bookmark first if it should be retained.
Clearing app storage or uninstalling removes saves/examples; exported files survive.

Mode/challenge preferences live in SharedPreferences mode. Sound/style/haptics use
feedback. Color labels/reduced motion are session progress; campaign and Infinite
session settings are currently separate (carried between Infinite puzzles).
Browsing must preserve the previous active mode and board/history.
settlePresentation retires visual flights on leaving the board, not committed moves.

Instrumentation once removed the real emulator save during cleanup.
KEEP android.injected.androidTest.leaveApksInstalledAfterRun=true and use
in-memory/isolated test stores. Never clear real player data to simplify a test.
Install updates over the app without uninstalling.
Debug builds use the committed app/playtest-debug.keystore (see README). Do not delete or
regenerate it: a changed signature forces every tester to uninstall and lose their save.

**Give testers the playtest build, never the debug one** (2026-11-16): `.\gradlew.bat :app:assemblePlaytest`
→ `app/build/outputs/apk/playtest/app-playtest.apk`. It is the release build (R8, library baseline
profiles), signed with the same playtest key, so it installs over a debug build and keeps the save.
A tester on a Xiaomi 12 Lite (Snapdragon 778G, 120 Hz) found the debug build laggy: Compose runs
much slower in a debuggable, unoptimised app. The release build had never been run before and crashed
at start (R8 `packageScope` limited to libraries); it now optimises the whole app.

Keep drawing cheap for 120 Hz phones: still paintings (world sections, backdrops) and level path rows
are drawn once into cached layers (`CompositingStrategy.Offscreen`), the board has its own layer so
moving stones repaint only the board, and sounds and vibrations start on the `stone-feedback` thread,
never on the main thread. The emulator renders through GPU translation with a ~16 ms GPU floor: it
can show app-side and draw-command costs, not a phone's GPU cost.

## 8. Art and portability

Production art sources are app/artwork/*.png.base64; Gradle DecodeWorldArtwork
packages only 13 active normal PNGs offline. Old source variants are retained as
history but excluded from the APK. Font source is app/typography/*.ttf.base64;
DecodeDisplayFont generates res/font/libre_baskerville.ttf.
Its OFL license is shipped under assets/licenses.

Launcher icon update 2026-11-12: the creator selected B — Carved from the universal
circle/squircle sheet after rejecting the floating stone plaque's clipping and padding.
The regenerated launcher-carved-v2 was rejected too for changed corner details. Active
input is app/artwork/launcher-carved-approved-reference.png.base64, the ORIGINAL sheet.
DecodeLauncherArtwork extracts actual B pixels, adds overscan outside the approved crop
and packages only individual icons, never the comparison sheet. Main icon ic_stillstone
uses the squircle artwork; roundIcon ic_stillstone_round uses the circular artwork.
Both have full-composition backgrounds and transparent foregrounds; the themed vector
remains. Older launcher-stone-v1 and launcher-carved-v2 sources are history only.
Details/validation: app/artwork/launcher-README.md. No app-name, identity, signing or save
changes. Do not regenerate the selected logo to repair layout/cropping.
Do not edit generated build files or require a runtime network/API key.

world-ui.md records active assets, rendering choices and exact generation prompts.
Prior mock/source image paths under C:/Users/furtu/.codex/generated_images are
provenance on the creator's machine, not portable build dependencies. On another
machine use the packaged production sources; ask for a particular missing mock only
if its visual content is actually necessary. Do not package a wide multi-phone
comparison mock as a portrait background or stretch a badge to fit a rectangle.
Portrait foreground framing preserves edges; Midnight needs continuous side jambs.
Generated artwork is an asset, never the source of native buttons/text.

## 9. Validation status and environment

Windows Android Studio project, originally:
C:/Users/furtu/AndroidStudioProjects/Ballsortgame.
Primary testing: Android Emulator; meaningful milestones: Samsung Galaxy S25+.
Portrait first. Phones are locked to portrait; large screens (unfolded foldables, sw >= 600 dp)
rotate and reflow; see "Phones portrait, large screens reflow" in world-ui.md (2026-11-15).

Latest UI follow-up actually ran:
- debug assemble and all 30 app JVM tests passed;
- four focused WorldBrowserStateTest / WorldArtworkTest emulator checks passed,
  both normal portrait and compact 360×640 dp with 130% text;
- emulator screenshots reviewed for full-height scrolling, gear overlay, glows,
  new lettering and compact label fitting;
- original emulator resolution and font scale 1.0 restored, no data cleared.
Earlier navigation pass ran the full 18-test emulator suite before the latest
font/fog follow-up. Do not claim that full suite was rerun afterward.
Engine tests were not rerun in this UI/documentation pass.
The user has tested earlier builds on their phone and reported the above issues.
The latest edge/glow/fog/font corrections still need their phone review.
No release readiness, battery quality, frame pacing or acoustic quality is proven
by these tests. API 29, process-death/upgrade/export/release checks remain work.

Build commands are in the root README. Run relevant tests after code changes,
with connected device operations/builds sequential in the IDE environment.
Use Android Studio's Gradle JDK; do not guess a different system Java installation.

## Infinite interaction examples added 2026-11-12

At the creator's request, Infinite now offers Mixed tube sizes, Sealed tube, Connected
boards and Restore a pattern alongside Classic. They use fixed playable examples with
independent saved histories; reliable generation/difficulty is deliberately deferred.
No engine generation code was modified for this feature. See
[infinite-variants.md](infinite-variants.md) and the new next-steps section before continuing.
That feature document now also separates implemented examples from design review:
Mixed/Connected recommendations, the creator's filled sealed-tube proposal, and a proposed
unrestricted single-stone Pattern replacement after the creator rejected its similarity
to Classic. Neither proposed rules change is implemented. Exploratory graph results are
small-board evidence, not human difficulty calibration or a proven skill ceiling.

## 10. What remains open

See next-steps.md for priorities. Campaign batch counts/unlock thresholds, final
large world sizes, mixed-capacity goal semantics, mastery scoring and cosmetic
rewards need decisions. Jelly, ambience, extra modes and achievements are optional
ideas, not mandatory first-version requirements.
Keep the relaxing experience and puzzle quality ahead of adding systems.

Some older documents describe work as future that now exists, or list old test
counts. product-plan.md is historical; playtesting.md mainly describes an earlier
milestone. Use this handoff, next-steps.md and feature-specific recent sections for
current status. Update these entry points as the project evolves.
