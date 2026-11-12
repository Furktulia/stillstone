# Remaining work after the menu follow-up

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
No change was needed. A real figure needs a release build on a phone (Android's battery
usage screen, or Android Studio's energy profiler) over a long session.

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
