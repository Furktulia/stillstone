# Seeded Infinite playtest

Open the world browser and tap the **Infinite** entrance. Seed/bookmark/export tools
are under **⋯** in Infinite gameplay. World progress is kept separately. The last mode,
current board, moves, undo history and two prepared puzzles are stored on device.
Everything works offline; there is no service, account or network dependency.
Generation runs on a worker and pauses when leaving Infinite or backgrounding the app.

## On a flight

- Use **Options → Skip / new puzzle** at any time, or Next after solving.
- Balanced uses three colors and two spare tubes; Challenge uses five colors and
  one spare tube in the goal. Capacity varies between three, four and five.
- Open **Options** and tap the `E1-B-…` or `E1-C-…` code to copy it.
- **Options → Save example** stores the literal board, certificate, played moves,
  a Good / Needs work label and an optional note. Hard can still be good.
- **Saved examples** reopens a bookmarked position, including undo history.
- **Export saved examples** writes one JSON record per line through Android's
  document picker. Save locally while offline; share the file later.
- Entering a code regenerates the initial position; reopening an example restores
  the position you actually bookmarked. Restart returns to its initial board.

Skipping replaces the current Infinite puzzle. Bookmark it first to retain it.
Examples survive app restarts and updates, but app data clearing/uninstallation
removes local saves. Export before reinstalling. The export currently has no
in-app importer; keep the original app data to reopen examples locally.

## Reproducing a report on PC

From Android Studio's terminal at the project root:

~~~powershell
.\gradlew.bat :puzzle-engine:run --args="replay-seed --code E1-C-2 --output build/puzzle-lab/replayed.txt"
.\gradlew.bat :puzzle-engine:run --args="verify-pack --input build/puzzle-lab/replayed.txt --max-ms 5000 --max-states 400000"
~~~

The output is the normal versioned engine level pack. The export's `pack` field
also preserves this exact board/certificate independently of future generators;
`progress` contains the app's Base64-encoded move-history format.

## What this experiment measures

E1 fixes the recipe and seed interpretation. Acceptance uses deterministic work
limits, not wall-clock solver deadlines. Every accepted puzzle has a replay-verified
winning certificate. A malformed, unsupported or unsuccessful entered code is
reported as an error, never silently replaced. New random requests can try up to
12 seeds, with 16 construction attempts and a finite walk per seed.

The certificate proves solvability, **not optimality or human difficulty**.
Challenge describes a denser recipe, not a calibrated rating. Reverse construction
can produce easy or repetitive boards. This first Infinite version deliberately
collects feedback before adding expensive difficulty filters, mixed-capacity
recipes, or an adaptive progression. The curated worlds and their hard finales
remain the reference challenges; Infinite does not replace their selection.

Two queued puzzles reduce waiting. They are prepared only while Infinite is in
the foreground, without continuous background work. Accessibility settings carry
between Infinite puzzles; world and Infinite session settings are currently separate.
Stone sounds and optional haptics are available in Settings and shared between modes.
Physical-device latency/battery verification and final visual polish remain ongoing.

## Validation

Engine tests cover repeated seed construction (both recipes, capacities 3–5),
certificate replay, malformed codes and cancellation. The isolated-storage emulator
test covers moves, bookmark export data, process-owner recreation, undo, recipe
switching with settings retained, and bookmarked-position replay. Existing world
UI tests protect the hint interaction, level selection and saved world positions.
The document picker export and real flight / physical-phone behavior still need
manual verification.
