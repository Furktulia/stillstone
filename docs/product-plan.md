# Ball Sort Game: approved direction

Historical planning snapshot: this file records the original approved direction.
Much of the engine, playable app, campaign browser, Infinite mode and feedback is
now implemented. See next-steps.md for the current remaining queue and world-ui.md,
infinite-mode.md and feel-playtest.md for implementation/validation updates.
The old milestones and test-status statements below are retained as planning history.

## Product

A calm, polished, offline Android puzzle game, initially shared as a private APK.
Minimum API 29. Kotlin/Compose project on Windows; emulator is the primary
development device and Samsung Galaxy S25+ is used at meaningful milestones.

Moves transfer as many matching top pieces as destination capacity permits.
A destination is empty or has the same top color. Every nonempty tube must be
full and monochrome to finish. Tube capacity is explicit per tube; initially
equal within a level. Larger capacity adds variety, not necessarily difficulty.

Capacity-based worlds are substantial collections unlocked in batches.
Players may skip, replay and return to harder optional branches after opening
another world. Unlock thresholds, level counts and exact capacity sequence are
not yet fixed. Resume the current puzzle on launch; worlds are an optional screen.
Difficulty rises gently overall with plateaus, breathing levels and optional hard
challenges. Performance metrics are unobtrusive.

Unlimited undo, restart and current-position hints; no extra-tube aid.
No-legal-moves feedback is separate from a proof of unsolvability.
A proven unsolvable position should offer undo recovery; solver timeouts must
not be described as proof. Automatic saves should retain undo history.

Identical piece shapes with clearly distinct colors. Nearly smooth stone/bone
material: reflections, weight, restrained bounce and low soft landing sounds
convey material, without distracting surface texture. Darker purple nighttime
atmosphere. Tubes and overall interface appearance remain unapproved; generated
concept art is only a material/color reference. Jelly is a later cosmetic.

Licensed recorded effects are the preferred sound source, with licenses retained.
Minimal sound; no music by default. Sound and optional haptics are configurable.
No timers, lives, energy systems, intrusive advertising or backend requirement.

## Architecture

Shared pure Kotlin engine in a JVM module, callable from desktop tools and Android.
Compose presentation and a ViewModel-owned session remain planned.
Rules are independent of rendering, coordinates, animation, sound and storage.
Versioned local saves, atomic logical moves and cancellable background searches.
Manual dependency construction until complexity warrants more.
No game engine, physics framework, DI framework or backend is currently needed.

The additional JVM module is intentional: the user prioritized PC-based puzzle
authoring and algorithm verification, with phone optimization following measurement.
The same rules and solver must be used on both platforms.

## Revised milestones and acceptance

1. Algorithm baseline: variable capacities, group transfers, exact/bounded solving,
   certified generation and desktop experiments. Verify against independent tiny
   exhaustive searches, replay every accepted certificate, distinguish all outcomes.
2. Authoring quality: expand fixed solvable/unsolvable corpus, measure rejection,
   search cost and diversity; generate difficulty bands with honest proof status.
   Establish practical limits before claiming phone-ready generation or hints.
3. Playable review board: minimal readable UI, handcrafted/generated sample puzzles,
   undo/restart, hints and durable saving. First meaningful S25+ check. Use human
   feedback to calibrate puzzle difficulty and pacing.
4. Campaign: two initial capacity-based worlds, batch unlocks, skipping and stable
   replay. Review progression by playing the sequence, not just sorting a score.
5. Polish: approve tube appearance, tune stone shading/bounce/sound, accessibility,
   reduced motion and optional haptics. Recheck on the phone.
6. Private release: signed APK, minimum/target-API checks, lifecycle and upgrade
   tests, release-build smoke tests, sustained device performance and saved progress.

## Later and open

Mixed-capacity worlds; five/six-slot worlds; endless mode; optional mastery badges
and milestone cosmetic rewards; jelly materials; optional ambience.
Mixed-capacity goal semantics need review before shipping that content, particularly
whether a color can occupy multiple full tubes. The engine currently implements
the agreed full-monochrome condition without inventing a one-tube-per-color rule.

Campaign ordering and a human difficulty model are not implemented by the initial
engine. No claim is made that a long solution or a large tube is inherently harder.

## Testing direction

Host JVM tests for rules, independent solver comparisons, certificates and generator
invariants. Compose behavior/restoration tests when UI exists. Versioned-save tests
when persistence exists. Keep existing JUnit4/Compose dependencies before adding
test frameworks.

Primary phone-sized target-API AVD plus API 29 compatibility AVD. Verify Windows
hypervisor acceleration and host-matched emulator architecture during setup;
no emulator was configured as part of the engine work. Test small screens, larger
text, system bars, reduced motion and optional non-color identification.

S25+ milestones cover touch, sound timing, haptics, high-refresh animation,
background/resume, sustained performance and release candidate use. It does not
substitute for lower-end device coverage. No phone, emulator, audio, haptic or
battery tests have yet been run.
