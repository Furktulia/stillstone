# Level workshop and playtest worlds

The PC workflow now generates a candidate pool, produces an offline visual review,
suggests a varied 30-level draft, accepts an edited selection manifest, and validates
the exported pack. Android loads the same versioned pack format; generation and
exact search do not run when the app starts. No dependency was added.
For incremental updates, refine-finales preserves the current catalog and appends
screened challenges. See challenge-selection.md for the new selection protocol.

## Current collection

| World | Tube capacity | Levels | Verified shortest lengths, in play order |
| --- | --- | --- | --- |
| Dusk | 3 | 10 | 1, 3, 4, 5, 3, 5, 8, 6, 8, 12 |
| Moonlight | 4 | 11 | 4, 11, 11, 9, 11, 13, 7, 13, 13, 15, 16 |
| Midnight | 5 | 11 | 4, 7, 13, 16, 10, 14, 18, 10, 15, 19, 22 |

Every world/level is immediately selectable. The game still opens directly into
the active puzzle. Worlds opens a world browser, then a level grid with local
numbering, current-puzzle highlighting and completion marks. Switching keeps each
board and its undo history. These 32 puzzles are a playtest sample; final world
sizes, unlocking schedules and visual identities are still open. Dusk 10 and all
previous thirty boards/IDs are retained.

The original twelve boards, tube order, certificates and stable IDs are preserved.
Their positions/display numbers can change; saved progress is keyed by ID. A test
restores an original-collection save into the expanded catalog.

## Generate and review

Run from the project root in Android Studio's terminal:

~~~powershell
.\gradlew.bat :puzzle-engine:run --args="workshop --seed 20260 --per-profile 4 --challenge-count 48 --attempts 40 --max-ms 1500 --max-states 150000 --output build/puzzle-lab/my-workshop"
~~~

Choose a fresh output directory each time. The tool refuses to overwrite a batch.
The checked screened run is in build/puzzle-lab/workshop-screened-v1.
It creates a fresh 30-level draft; use refine-finales to preserve and extend an
already shipped collection instead of replacing it with a new draft.

Open review.html in a desktop browser. It shows colored/numbered board diagrams,
checkbox selection, intended role and structural observations. Download selection.txt
to choose candidates; the download preserves the suggested order of retained
levels and appends additional choices. Edit its line order to set your own sequence.
The page is self-contained, works offline and sends nothing online.

Files in each batch:

- pool.txt: actual boards/certificates and world metadata for all accepted candidates,
  plus the twelve preserved originals.
- candidates.csv: features, exactness, accepted-candidate search measurements,
  opening classifications, and blank playtest feedback columns.
- attempts.csv: every requested seed's attempts, acceptance/exhaustion and rejection
  reasons. Effort includes rejected attempts; it is distinct from candidate search.
- selection.txt: editable level IDs, one per line, allowing comments beginning with #.
- challenge-assessment.csv and challenge-parameters.txt: four-strategy probes,
  complete-graph decision features, eligibility/rejection reasons and reference-derived thresholds.
- playtest.txt: suggested 30-level export, only written if all requested roles can
  be filled. An exhausted batch retains its pool/reports/review instead of inventing
  substitute puzzles or writing an incomplete draft.
- One JSON file per newly accepted candidate: the existing detailed research export.
  JSON remains an export format; the text pack is the app interchange format.

Profiles use equal capacities within a world, two to five colors, one or two
empty tubes, and different exact shortest-length bands. New capacity introductions
use two colors; regular and challenge pools vary color count and available space.
The generator retains its earlier reverse-construction API; this workshop uses
shuffle-and-solve to explore broader arrangements. Every accepted workshop
candidate has an exact A-star solution. Unsolvability, search limits, duplicates,
and unmet bounds remain distinct rejection reasons.

A shared diversity index rejects equivalent candidates under capacity-preserving
tube permutation and global color renaming. Draft selection chooses within role
bands and favors variation in length, rearrangements, opening successors and top
runs. Challenge choices now pass four simple-strategy probes and minimum decision
requirements over ALL shortest routes, using Dusk 10 as the reference. Dusk 10
itself is pinned. This remains a provisional screen, not a human difficulty model.
Near-duplicates are not guaranteed to be excluded.

## Export an edited selection

~~~powershell
.\gradlew.bat :puzzle-engine:run --args="pack --input build/puzzle-lab/my-workshop/pool.txt --selection build/puzzle-lab/my-workshop/selection.txt --output build/puzzle-lab/my-selection.txt"
.\gradlew.bat :puzzle-engine:run --args="verify-pack --input build/puzzle-lab/my-selection.txt --max-ms 5000 --max-states 150000"
~~~

The pack command rejects unknown or repeated IDs and replay-checks all certificates.
World order comes from the pool; manifest order determines order within each world.
verify-pack independently reruns exact search and checks declared shortest lengths.
A budget limit fails validation; it never becomes a false impossibility claim.

After review, replace the content of
puzzle-engine/src/main/resources/levels/playtest-v2.txt using the IDE, then build/run
the app. This is intentionally an explicit authoring step: CLI exports never
rewrite Android source resources automatically. No Kotlin level-list editing is
needed. The file is packaged in the shared module and loaded by LevelCatalog.

Retain an existing ID only for the same board/rules. New generated IDs include the
world, intended role, seed and a board-content digest. Seeds alone are not identity:
time-limited acceptance can differ with hardware or future generator changes.
The shipped text pack stores the board, not instructions to regenerate it.

The codec version-checks format/rules, bounds input sizes, requires unique IDs and
known worlds, and replays certificates at import/export. It stores explicit per-tube
capacities and occupancy, including partial/mixed-capacity boards; those are covered
by a round-trip test. A mixed-capacity campaign world is deliberately deferred until
we agree its color quantities and completion rules as a product mechanic.

## Interpreting the reports

Use several observations, not a single difficulty number:

- Shortest moves measure required path length.
- Boundaries/runs describe initial mixing.
- Top runs and empty tubes describe transfer size and maneuvering room.
- Rearrangements count certificate moves removing no boundary; they are not proven
  counterintuitive human decisions.
- Distinct opening successors remove tube-permutation duplicates.
- Opening analysis searches each distinct successor and separately counts solvable,
  proven-dead and unresolved branches. This probes only the first decision; it is
  not a complete map of traps through the level.
- Search effort depends on the heuristic, pruning and PC, and is not player effort.

The initial unscreened batch (workshop-v3, retained for comparison) requested 44 generated candidates across 11 profiles. It accepted
43 and retained 12 originals: 55 candidates for review. One seed exhausted its
attempt budget; enough other candidates remained to fill the complete 30-level
draft. The report analyzed 190 distinct opening branches: 166 solvable, 24 proven
dead and 0 unresolved under that run's budgets. These are related samples, not a
population estimate or a phone benchmark.

In particular, several one-empty challenges required longer solutions but fewer
A-star expansions than regular puzzles. This is why neither capacity, solution
length nor solver time is presented as an established difficulty rating.

## Human calibration

Use docs/playtest-feedback.csv for the shipped collection, or the blank columns
in candidates.csv. Record the world/local level and whether it felt relaxing,
interesting, tedious or frustrating, plus hint use and a short reason. Actual move
counts/metrics remain absent from the game's main interface.

First compare the introduction to each capacity, the transitions into harder
puzzles, and whether the easier puzzles feel welcome or repetitive. Reorder or
replace candidates based on that evidence. We cannot validate fun or smooth
progression through automated search alone.

## Verification and remaining limits

- 71 engine and 16 app JVM tests pass.
- 11 emulator instrumentation tests pass: movement/hints/undo/restart, world entry,
  free access to the last level, cross-world unfinished-board retention, completion,
  browser back navigation and settings.
- All 32 shipped certificates/shortest lengths were checked by tests and by the
  standalone pack/verify-pack commands.
- Android debug build and lint finish successfully; existing lint warnings remain.
- World cards and the level grid were visually inspected on the emulator.
- The HTML selection code was executed with mocked DOM/Blob APIs: initial 30-ID
  selection, recommended order, checkbox removal/addition and manifest contents.
  Browser automation was unavailable (native helper pipe absent), so real browser
  rendering/download integration remains a manual check.
- No physical-device, battery, audio or haptic validation was performed in this
  milestone. Difficulty/variety/fun still need human playtesting.
