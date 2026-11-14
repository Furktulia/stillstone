# Remaining work after the menu follow-up

## Stillstone launcher icon (2026-11-12)

The creator selected B — Carved from the universal circle/squircle comparison. The
installed APK now uses a continuous carved violet surface with a recessed vial and
coral/mint/gold stones. The previous floating irregular plaque was rejected for clipping
and leftover padding; its source is retained as history but no longer built.
The regenerated B master was also rejected: it lost the approved top-left/bottom-right
stone planes. Active source is now launcher-carved-approved-reference.png.base64, the
original approved sheet. DecodeLauncherArtwork extracts B's actual circle/squircle pixels,
adds overscan OUTSIDE the visible crops and generates separate normal/round icons.
No regeneration in this correction; the sheet itself is not packaged in the APK.
Source and extraction details: app/artwork/launcher-README.md.
App name, application ID, signing and save paths were not changed.
Debug assemble, lintDebug and all 3 focused WorldArtworkTest emulator checks passed.
Installed Pixel app-drawer appearance reviewed; needs creator review on Samsung squircle
and themed icons.


## Infinite mode interaction examples (2026-11-12)

Four playable examples are available from Infinite → Ways to play: Mixed tube sizes,
Sealed tube, Connected boards and Restore a pattern. Separate per-mode saved histories,
mode-aware hints, undo/restart, existing motion/feedback and a persistent pattern target.
Each has a fixed board, explicitly labelled Example puzzle; no generator or tier changes.
Classic Infinite and hidden colors retain their existing behavior and saved board.
See [infinite-variants.md](infinite-variants.md) for rules, source map and validation.
Needs creator visual/interaction feedback before generation or difficulty work.

Design review is recorded in infinite-variants.md, with measured small-board results and
their limits. Recommendations: keep Mixed for everyday variety and Connected for distinct
planning; the empty Sealed spare mainly adds an opening phase. The creator then proposed
putting stones inside the sealed tube so unlocking introduces a second sorting phase;
keeping those contents visible is the suggested first prototype. That revision is not built and needs both pre-unlock and post-unlock validation.
The creator found current Pattern too similar to Classic; proposed replacement is
single-stone moves onto any color with an exact target. That is not built either.
All such Pattern moves would be reversible, unlike the matching-color modes, so playing
from a solvable position cannot create a losing position. Human difficulty/skill ceilings
are not established. These notes do not authorize generation or gameplay changes.
Verified: debug assemble, 48 app JVM tests, lintDebug; 7 focused emulator checks (4 variant,
1 group motion, 2 browser), with the 4 variant checks rerun after final pattern placement.
All four example screens and the picker reviewed on the normal portrait emulator.
No physical-phone or compact/large-font visual validation claimed.


This is the current queue; product-plan.md is the original planning snapshot,
not a record of everything still missing. Optional ideas below are not promises
to include them in the first release.

## Creator feedback 2026-11-11 (after playing campaign and Infinite)

- **Tier labels match their experience.** Direction: most campaign levels should be RELAXED.
  Less experienced players find even BALANCED hard until they have played for an hour or so.
  Harder levels are the exception, placed late or as optional branches, never the default.
- **World browser:** the entrance pictures "do not belong". Exploration in the working tree
  (not yet approved): entrances at 58% of width instead of 70%, and their silhouette edge
  dissolves into the mist (WorldArtwork.ENTRANCE_FEATHER, computed once when the art loads;
  source images unchanged). The creator likes this "much more" (emulator); phone review open.
  **2026-11-13:** later judged still wrong (detailed badges in undetailed fog; the badges
  were placeholders). Replaced by one continuous scene of four stretches built from the
  existing wide paintings; see world-ui.md. Entrance and fog code removed. Not yet reviewed.
- **Sound and haptics reported as not working.** Measured on the emulator: the impact clip
  does play on every landing, but at about -35 dB (media volume 5/15 plus a quiet clip).
  Sound is also skipped whenever the phone's ringer is on vibrate or silent, and haptics
  are off by default and use the system key-press effect, which many phones disable.
  The creator's phone was not on silent and has key-press vibration disabled (as they
  expect most players do). Earlier builds were already "really quiet" to them.
  Done since: clips are about 5 dB louder (ImpactSounds.LEVEL, rounded limiter) and
  playback volume is 0.75–1.0 instead of 0.55–0.9. A fourth comparison style, River stone,
  adds a bright clack to the Pebble body, because phone speakers reproduce little of
  Pebble's 185 Hz. Nobody has listened to either change yet; default style is still Pebble.
  Built 2026-11-11, unheard: SettlingColumns reports every contact above 2.5 slots/s as
  FLOOR, STONE or OVERHEAD with its closing speed. GameFeedback plays one short voice per
  contact (ImpactSounds.renderContact: lower tock, bright clack, lighter high tap, all derived
  from the chosen style), volume from speed (contactVolume), at most one voice per kind every
  35 ms, playback rate varied by ±4%. The old whole-landing clip now plays only with Reduced
  motion and in the sound comparison, which therefore does not preview contact voices.
  Gentle haptics is an explicit PRIMITIVE_TICK at 0.25–0.55 strength (8 ms one-shot fallback)
  on each landing, usage MEDIA, so it no longer depends on the touch-vibration setting; still
  off by default, and switching it on plays one tick. VIBRATE permission added.
  Creator verdict 2026-11-12 (phone): "the sounds and vibrations are good."
  The ringer-mode check was the reason they heard nothing earlier: their phone is always on
  vibrate and they listen through earphones. It is removed; game sound now follows media
  volume and the Stone sounds switch only.
- **Stones passing through each other in flight** (E2-B-11MK1N5UER: tube 2 to 5, then 4 to 5).
  Cause: the later, shorter group waits directly above tube 5, which is on the earlier
  group's path down the aisle. Assessment: physical collisions in flight are not worth it;
  a yield rule is (a later group holds short of an earlier group's remaining route, never
  the reverse, so nothing can deadlock, and if two groups already overlap it does nothing).
  Built 2026-11-12 (GroupRoute.claimed / entersClaimedSpace, stepped approach in
  PuzzleBoard): a group bound for the same tube keeps its whole remaining route clear;
  any other earlier group claims its body plus four slots ahead. A held group eases off
  and resumes; it never waits more than 2.5 s in total. The final fall is not covered.
  First version still failed on the creator's example: the held stone sat exactly at the
  edge of the claimed space, sample points shifted as the first stone moved, the held stone
  then counted as "already inside" and was let through. Fixed by measuring to the route
  line itself and requiring "already inside" to mean well inside (0.7 slot). Confirmed on
  the emulator with E2-B-11MK1N5UER: the stone from tube 4 waits beside the aisle.
  A stone falling onto one that is rebounding up out of the tube now allows for the rising
  stone's speed, so the two are not drawn overlapping for a frame.
  River stone is the default sound for players who never chose one (creator's request).
  Third cause found by logging every drawn stone position per frame: after a long drop the
  first stone rebounds about five slots, out of its tube, and went through the second
  stone still hovering above waiting to drop. SettlingColumns.lid(tube, limit) now makes
  any stone in flight over a tube solid to that tube's top stone (stones lifting out of the
  same tube are ignored). A falling stone is also handed to the settling motion one frame
  before contact instead of being caught inside the stone below. On the example the log
  went from 8 overlapping frames to 0. To repeat that check, log stone centres from the
  board's draw pass and flag any pair closer than 0.97 of a slot.
  Needs the creator's eye for pacing. The approach no longer uses a Compose tween, so it
  ignores the system animation scale by construction.

## Battery audit (2026-11-12, emulator only)

Measured with dumpsys gfxinfo / top / power on the debug build:
- Idle on the game screen (with and without a selected tube), the level grid and the world
  browser: 0 frames drawn in 6–8 s and 0% CPU. No animation loop runs while nothing moves.
- No wake lock is held and no audio track stays active after a sound finishes.
- Work happens only while stones move, while the world list scrolls, during a hint search
  and while Infinite prepares its next puzzles (foreground only).
- The one costly spot is scrolling the world browser: about 30 ms per frame on the emulator
  (mist layer, erase masks and gradients redrawn each frame). Brief, and unmeasured on a
  phone. Caching the static backdrop would be the fix if a phone shows dropped frames.
  (That mist backdrop was removed with the 2026-11-13 world screen; not re-measured.)
No change was needed. A real figure needs a release build on a phone (Android's battery
usage screen, or Android Studio's energy profiler) over a long session.

## One run per world, quieter screens (2026-11-12 evening, awaiting review)

Creator feedback: the pattern target was too small; tube numbers and level descriptions
were debugging aids; the level title should be far less visible and at the top; the mode
should not be in the level name; mode sections should become one varied progression per
world; and the level list scrolled badly (hidden edges, no sign it scrolls).

- **Order.** Each world is now ONE numbered run (Dusk 47, Moonlight 47, Midnight 46),
  superseding the "sections" described below and in campaign-modes.md. Introductions,
  then all non-challenge levels by an approximate difficulty score across modes, each
  mode introduced on its own a few levels apart by its gentlest level, and the CHALLENGE
  levels spread evenly through the run as jumps, never in the first eight and the hardest
  last. The score is a rough blend (classic: length; modes: the generator's hardness), not
  a measured cross-mode scale, so expect local bumps. Level IDs are unchanged.
- **Game screen.** No big title or "N slots" line. The level number sits small and dim in
  the top row. A level that differs from plain sorting shows a bubble (LevelTrait in
  ModeBubble.kt: Mixed sizes, Sealed tubes, Connected boards, Restore a pattern, Hidden
  colors); tapping it opens a small note under it that closes on the next tap anywhere,
  and the note opens by itself the first time a trait is met ("met_*" in the onboarding
  preferences). Tubes have no numbers; a sealed tube shows only "0 / 2". The status line
  is empty unless something needs saying. Hints say "Move the lifted stones to the
  glowing tube." Accessibility descriptions still name tubes and levels.
- **Pattern target** is about 60% larger and has no caption.
- **Level list.** One grid that scrolls as a whole with the world name and "N of M sorted",
  filling the window under the floating Back and Settings buttons, fading only at the
  screen's top and bottom edges. Each stone carries a small mark for its trait.
- 2026-11-13: challenge levels are marked. In the list a challenge stone has a second,
  warm rim and a small peak; in play it has a "Challenge" bubble whose note says it is
  harder and optional (LevelTrait.CHALLENGE, PlayableLevel.challenge from the pack role).
- 2026-11-13: the level number moved onto the Back stone ("‹ Dusk · 26") after the
  creator found the separate dim number out of place wherever it sat.
- 2026-11-13: the Infinite mock modes were removed at the creator's request (picker,
  example screens, their tests and saved histories' code). Infinite is classic play again,
  with occasional hidden-colours puzzles. infinite-variants.md is kept as history only.
- Not done: nothing viewed on a compact screen, with large text or on a phone.

## Campaign modes (2026-11-12, awaiting review)

Mixed sizes, Sealed tubes (one or two, with stones inside), Connected boards and Restore a
pattern (one-stone moves) are optional sections in every world: 80 new levels, graded from
straightforward to harder. See [campaign-modes.md](campaign-modes.md) for rules, the
level format, the generator and what was and was not verified. Needs the creator's play
ratings; the sealed and connected hard ends and the pattern mode's pacing are unproven.

## Hidden colours and the refreshed campaign (2026-11-12, awaiting review)

**Hidden colours** (Settings → Hidden colors, off by default, applies to every puzzle).
Only a tube's top stone shows its colour; a stone is revealed the first time it is on top
and stays revealed, including after Undo and Restart. Moving rules are unchanged, so a
move still carries every matching stone under the top one, seen or not. Unseen stones are
always the bottom ones of a tube: their number is one less than the smallest size the tube
has had (engine/HiddenColors.kt), so nothing new is saved. Hints still use full knowledge.

Creator feedback 2026-11-12: hidden colours is good as an occasional change, not all the
time. (They had seen every level hidden because the Settings switch had been left on after
testing.) So individual levels can now be hidden-colours levels: `LevelRules.hidden`,
written as `hidden` in the level pack, shown as "· hidden colors" under the level title.
Two calm classic levels per world carry it (Dusk 6 and 8, Moonlight 4 and 7, Midnight 4
and 7), each still solved by simple play without sight of the buried stones. The Settings
switch remains for players who want it everywhere.

Later the same day, at the creator's request:
- Hidden colours in Infinite: with the switch off, about one new puzzle in five is a
  hidden-colours puzzle (an H1 code, shown hidden wherever it is played). With the switch
  on, every new puzzle is.
- One easy level in each mixed, sealed and connected section is hidden (nine in all);
  pattern levels never are. These nine were NOT measured for blind play: the blind
  measure only exists for classic rules. They were chosen from each section's easy steps.
- 27 more classic levels: 21 calm ones (no losing position, simple play always wins, from
  the same candidate pool as the earlier 22) and two BALANCED-tier levels per world, the
  first of that tier in the campaign (simple play wins 56-79%; Dusk 74% and 59%).
  Order per world: introductions, calm levels by length with the two balanced ones about
  halfway and near the end, then the hard closing group. Four calm classic levels per
  world are hidden-colours levels. Dusk and Moonlight got fewer than the eight calm
  levels planned because the pool had no unused candidates at the longest lengths.

Does it make puzzles harder? Measured with the four simple strategies, with and without
sight of buried stones (`hidden-study`, 120 shuffles per recipe):
- two spare tubes: no difference. 1,077 of 1,080 starts that are relaxed in the open stay relaxed.
- one spare tube: clearly harder. Starts that are relaxed in the open are solved 66–90% of
  the time blind instead of ~100%, and roughly a third to a half of them stop being relaxed.
- shipped example: Moonlight 10 is solved 100% in the open and 27% blind.
So the E2 Infinite recipe is not reused. H1 codes (H1-R / H1-B / H1-C) use the same starts
but take the tier from the blind success rate (20 games per strategy). New Infinite puzzles
are H1 while the setting is on. PC timing: about 25 ms per puzzle against 3 ms for E2; not
measured on a phone. E1 and E2 codes are unchanged.
Limits: bot success is not a human rating, and the reveal happens when a move is made,
which can be a moment before the covering stones visibly lift.

**Campaign refresh** (playtest-v3.txt; playtest-v2.txt kept for reference and tests).
The creator asked for the relaxed levels to be redone as longer but still safe, keeping the
later medium and hard ones. 22 relaxed levels were replaced with new ones ("-calm-" IDs)
from `relaxed-campaign`, each with NO losing position anywhere in its state graph, solved
100% by simple play in the open and blind, and ordered by length within its world:
Dusk 6–13 moves (was 3–8), Moonlight 7–16 (was 7–13), Midnight 8–18 (was 7–16).
Kept unchanged: the five introductions and the six hard levels, which now close each
world (Midnight's former level 7 moved to the end group). Dusk 10, Moonlight 11, Midnight
10–12. 33 levels: 10 / 11 / 12.
Saves: progress on a replaced level is dropped with the existing "incompatible saved
puzzle was reset" notice; completed marks for kept levels survive.
Shortest solutions cannot grow much further with at most five colours and two spare
tubes; longer relaxed levels would need more colours or larger tubes (a product decision).
Not yet played by a person.

## Quiet controls and game-styled dialogs (2026-11-12, awaiting review)

A friend who playtested found the Undo / Restart / Hint stone buttons distracting.
- They are now QuietControl (NightUi.kt): thin outline icons at about 60% brightness, no
  surface, one row. Stone buttons remain for navigation (back, Resume, Next puzzle).
- ControlIntro.kt: each control shows its name underneath until it has been used once;
  any names left retire after three solved puzzles. Holding a control shows its name for
  about two seconds at any time. Stored in the "onboarding" preferences, not the save.
- Every dialog uses NightDialog instead of Material's AlertDialog: settings, restart
  confirmation, the "A way back" recovery tip, sound comparison, and the four Infinite
  dialogs (options, saved examples, enter code, save example). Switches use NightSwitch.
- Still stock Material inside those panels: the Infinite options list, filter chips,
  radio buttons, slider, text fields and progress bars. The "⋯" Infinite button and the
  "Tap a tube to begin." status line are unchanged.
- PlaytestUiTest and SoundComparisonTest use prefixed preferences and delete them, so
  running the emulator tests no longer changes the real app's name hints or settings.

## Continuous settling: implemented, awaiting phone review

ui/SettlingColumns.kt gives every standing stone its own vertical displacement and
velocity per tube. Stones bounce off the floor, each other and a tube directly above
(or the screen edge). A landing group enters with a speed derived from the existing
drop-based rebound height, meets the destination's top stone wherever it currently is,
and the earlier group keeps moving. Departing stones leave the rest in motion; undo,
restart and instant motion show a tube at rest. Approach, routing, taps and the logical
board are unchanged. A group retires once its destination tube is at rest.

Needs the creator's phone judgement: rebound height and pace, how overhead contact
reads, and whether stacked arrivals look natural. Constants are in SettlingColumns.
Still open: collisions between groups in flight, per-contact sound, and a stone leaving
a still-moving tube starts its flight with a short blended lift rather than true momentum.
The compact 360x640 dp / 130% text five-slot layout limit is also unchanged.

Creator feedback (emulator): the motion is liked. Rebound after long falls was too
small to reach an overhead tube, so LandingMotion.reboundHeight now returns up to 55%
of drops beyond about five slots (cap seven slots). Needs their review.

Verified on the emulator (Midnight 3, upper row into the empty tube below): drop 12.0
slots, rebound 6.6 slots, headroom to the tube above 5.6 slots, so the stone strikes it.

Not diagnosed: on the creator's Samsung A-series phone (Android 14) the stone animations
did not play at all. One plausible cause is now removed: stone flights no longer follow
the system animation scale (zero under "Remove animations" and some power saving),
covered by SystemAnimationScaleTest. Unconfirmed until that phone runs this build. If
it still fails, check the game's own Reduced motion setting next.

2026-11-14 audit for older Android (minSdk 29): lint reports no API-level errors; the only
version-dependent code is haptics, already guarded. The stone lift on selection was the
last board motion on a Compose tween and is now frame-driven too, so no board motion
follows the system animation scale. Still on that scale, deliberately cosmetic: the
press spring on stone buttons and the fade of control names; with animations removed they
snap. NOT verified on Android 14: only an API 37 emulator image is installed. To check on
the A51s, or to reproduce on an emulator: Settings > Accessibility > Remove animations,
or `adb shell settings put global animator_duration_scale 0` (1 restores it).

Compact boards: the space above the board and between rows now scales with stone size
(BoardGeometry) instead of a fixed 52/40 dp. Boards whose stones are at least about
32 dp are unchanged; a 360x640 dp screen with 130% text and six five-slot tubes gets
roughly 19 dp stones instead of 13 dp. Unit-tested only, not viewed at that size.

Responsiveness (2026-11-10): Hint is available while stones travel (it reads the committed
board). A move now starts as soon as its own stones are standing in its source tube: it no
longer waits for an earlier group that shares a tube, except when it would lift the stones
that group is still delivering. Arrivals in one tube stay in move order (a faster later
group waits above the tube). Two groups following each other closely on the same route can
overlap visually in the air; collisions between groups in flight are still not modelled.

## Material and sound polish

- Finish the nearly smooth stone/bone feel: restrained reflected lighting and subtle
  surface shading, with color always the strongest identification signal.
- Further review the tube appearance and world-specific gameplay atmosphere on phone.
- Improve the preferred Pebble/deep Pebble sound, group contacts and settling timing.
  Existing sounds are synthesized studies. Recorded licensed effects were the original
  preferred direction; retain licenses if/when adopting recordings.
- Test speakers/headphones, haptic strength, repeated impacts and responsiveness together.
- Keep no music by default. Jelly is an optional later cosmetic, not part of this pass.

## Puzzle quality and progression

Done 2026-11-10 (see puzzle-quality.md): shared exact PuzzleAnalyzer with provisional
tiers, the quality-screened E2 Infinite recipe with Relaxed / Balanced / Challenge, and
the tier-pool authoring command. Next step is the creator playing and rating Infinite
puzzles of each tier (especially Balanced, which has no human-rated example) and
candidates from a tier pool, then adding levels to the worlds from those ratings.

Already present: shared variable-capacity rules/solver, certified generation,
desktop workshop and decision-based challenge screening; three curated playtest
worlds with 32 levels; seeded offline Infinite with bookmarks and export.

Remaining:
- Expand the curated worlds with enjoyable breathing levels and optional hard branches.
  Use human feedback alongside decision/temptation measurements; bigger tubes and longer
  solutions alone do not establish difficulty.
- Calibrate Infinite recipes for variety and quality, including rejection/search cost
  and practical phone budgets. Current Challenge is experimental, not a calibrated
  human rating. Avoid turning every puzzle into a trap puzzle.
- Implement actual batch unlocking/progression after choosing thresholds. All content
  remains freely accessible during playtesting, intentionally.
- Mixed-capacity campaign/Infinite content and larger capacities need product review,
  readability checks and final goal semantics before enabling. The engine can represent
  per-tube capacities; shipping varied content is separate work.

## Before a private release

- Further phone checks of the updated menu, fog, glow and bundled lettering.
- Measure frame pacing, sustained power/memory and generation/hint latency on device.
- Complete minimum-API 29 compatibility, lifecycle/process-death, save durability and
  update/release-build smoke checks. Keep saves when installing updates.
- Exercise Infinite export through Android's document picker and retain a real exported
  report. There is currently no in-app import of exported example files.
- Choose signing/versioning and produce a release APK when the experience is ready.
- Landscape polish is explicitly deferred while portrait is being refined.

## Optional, not yet committed

Subtle mastery badges, cosmetic style rewards, more themes/materials, jelly motion,
additional modes, optional ambience, achievements and imported example management.
None should add pressure or distract from puzzle quality. Do not add these simply
because they appeared in discovery.
