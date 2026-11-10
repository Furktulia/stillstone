# Remaining work after the menu follow-up

This is the current queue; product-plan.md is the original planning snapshot,
not a record of everything still missing. Optional ideas below are not promises
to include them in the first release.

## Next: continuous settling and collisions

Phone feedback:
- A rebound can pass through a tube directly above it. It should contact the
  obstacle and rebound/settle naturally rather than tunnel through it.
- Sending another group into the same tube can cancel the first group's settling.
  Preserve each group's ongoing motion and handle the new contact continuously.

The current implementation is authored animation, not a general physics simulation.
Keep puzzle moves atomic and separate from visual motion. Build a persistent settling
model before layering in more bounce interactions; verify both examples, stacked
incoming groups, undo during motion and cancellation on changing puzzles.
Do not sacrifice responsive queued taps or obstacle-aware transfer routing.

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
