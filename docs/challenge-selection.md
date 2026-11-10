# Decision-based challenge selection, protocol 1

## Current playtest collection

The active pack is playtest-v2.txt: 32 levels across Dusk (10), Moonlight (11),
and Midnight (11). Every previous board, certificate, stable ID and world-local
number is retained. Dusk 10 stays unchanged as the positive playtester reference.
New challenges were appended as Moonlight 11 and Midnight 11.

The former 30-level playtest-v1.txt remains a frozen compatibility resource.
Tests restore its completed and unfinished games, active level and undo history
into the larger catalog without resetting them.

All worlds and levels are available immediately during playtesting.

## What the challenge screen measures

The old selector primarily used shortest-move bands and selected a short qualifying
candidate for the first puzzle in each role. The investigation showed that
Moonlight 10 passed that test while being easy for simple strategies.

The new offline assessor tests four strategies:

- Largest group: largest transfer, then matching destination.
- Least buried color: fewest blockers above a color's lowest occurrences, then
  exposed pieces, transfer size and matching.
- Merge first: matching destination, completed-tube gain, transfer size, boundary gain.
- Free tube: empty the source, then completed-tube gain, boundary gain, matching
  and transfer size.

Each strategy runs on the same 100 seeded random tube permutations for each
candidate, with 200 moves and repeat-state avoidance. These bots do not use
Undo/backtracking. Random permutations control arbitrary index-based tie choices;
trials need not be distinct permutations. The trial seed is 51000 + trial.

A bounded complete reachable graph classifies solvable choices and losing choices.
An incomplete graph is UNKNOWN and cannot pass. A complete graph must agree with
the replay-certified solution; a contradiction is an error, not a normal rejection.

Dynamic programming over distance-decreasing edges computes minima over EVERY
shortest solution route:

- Decision checkpoint: a position with multiple effective choices and at least one
  choice that is losing or does not advance a shortest solution.
- Temptation checkpoint: at least one top-ranked choice of one of the four
  strategies is proven losing.
- Counter-greedy checkpoint: none of the top choices of any strategy advances a
  shortest solution.

Minimum counts prevent a difficult-looking certificate from hiding an easier
alternate shortest route. Maximum temptation counts are also exported to show
route variation. Effective choices exclude capacity-preserving tube permutations
and self-transitions.

These bounds describe shortest routes. A human can choose a longer, easier route,
notice patterns missing from the bots, or use Undo. The analysis does not prove
fairness, fun, a human difficulty rating, or a capacity-dependent skill ceiling.

## Reference-derived requirements

Dusk 10 under this protocol gives:

- Best of the four strategies: 8 successes / 100 trials.
- At least 6 decision checkpoints on every shortest route.
- At least 4 temptation checkpoints on every shortest route.

Refinement and workshop selection derive their requirements from that frozen
reference. A candidate must have a complete analysis, no simple bot succeeding
more often than the reference, at least its minimum decision count, and the
required minimum misleading/counter-greedy count. Unknowns never count as traps.

Finale refinement additionally requires at least one tempting losing OPENING.
This is a deliberate reference-style playtest criterion, not a rule that every
challenging puzzle must contain an early irreversible trap.

Eligible candidates are ordered by:

1. Lowest success rate of their easiest successful bot.
2. Most minimum decision checkpoints.
3. Most minimum temptation checkpoints.
4. Shorter solution length, then stable ID as tie-breakers.

There is no capacity bonus and no aggregate invented difficulty score.

## Selected results

| Puzzle | Shortest moves | Reachable states | Minimum decisions | Temptations min–max | Minimum counter-greedy | Best bot successes |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Dusk 10 reference | 12 | 46 | 6 | 4–5 | 0 | 8/100 |
| Moonlight 11 | 16 | 57 | 6 | 4–5 | 1 | 0/100 |
| Midnight 11 | 22 | 81 | 15 | 8–9 | 2 | 0/100 |

Moonlight 11 has two tempting losing openings; Midnight 11 has three.
Both are exactly solvable, and their certified lengths are proven shortest.
These observations make them candidates for human comparison, not automatically
better puzzles.

The checked refinement examined 48 seed requests per capacity. Two four-slot
candidates and thirteen five-slot candidates passed the full finale screen.
Selection retained all 30 existing levels and appended one winner per later world.

Stable IDs:

- Moonlight 11: four-challenge-40015-6dc0590dc1e5
- Midnight 11: five-challenge-50028-5946afb17f81
- Dusk 10 reference: three-challenge-22261-6cd50517eded

## Follow-up human playtest calibration

The developer subsequently played both new finales and reported:

- Moonlight 11: acceptable, somewhat more challenging than usual, and suitable
  as the current world's final level.
- Midnight 11: genuinely very hard, with multiple tempting rule-of-thumb choices
  that destroy solvability. The difficulty was welcomed. In a larger future
  world, it should sit among the late challenges rather than represent ordinary
  progression.

Retain both boards and stable IDs. Their current positions stay unchanged for
this playtest collection; the Midnight placement recommendation applies when
the world is expanded. Dusk 10 remains the earlier enjoyable-hard reference,
and Dusk 9 the forgiving reference from the initial investigation.

These are separate qualitative anchors, not numerical human difficulty scores.
This is one playtester's sequential experience and does not establish a universal
ordering. It supports the decision-based screen while showing that its passing
candidates can span a substantial difficulty range.

Do not raise the minimum for all generated levels to Midnight 11's measurements.
Use it as a late-hard reference. The next content pass should select easier and
intermediate puzzles around these anchors, with deliberate breathing levels and
occasional challenges, then check the transitions through playtesting. Capacity,
solution length and bot failures remain separate descriptors; human feedback
determines whether a puzzle is interesting, fair, tedious or frustrating.

## Commands

For an incremental update preserving the currently bundled catalog:

~~~powershell
.\gradlew.bat :puzzle-engine:run --args="refine-finales --count 48 --seed 60000 --attempts 40 --max-ms 1500 --max-states 150000 --output build/puzzle-lab/my-finales"
~~~

The exact checked cohort used seed 40000 and the previous playtest-v1.txt as its
base, before the new levels were bundled. To reproduce it now:

~~~powershell
.\gradlew.bat :puzzle-engine:run --args="refine-finales --input puzzle-engine/src/main/resources/levels/playtest-v1.txt --count 48 --seed 40000 --attempts 40 --max-ms 1500 --max-states 150000 --output build/puzzle-lab/reproduced-finales"
~~~

The retained run is build/puzzle-lab/refined-finales-v1. Commands refuse existing
output directories and never rewrite app resources. --analysis-states (50000),
--analysis-ms (1000), and --policy-trials (100) control the assessor budgets.

Outputs:

- parameters.txt: protocol, reference, thresholds, budgets, seeds and sort order.
- generation.csv: every requested seed and underlying generator rejection reasons.
- pool.txt: preserved base levels plus accepted candidates and actual certificates.
- candidates.csv: core challenge assessment; its eligible column applies the
  reference-derived core rules.
- screening.csv: final finale eligibility, including the additional early-opening
  requirement, with explicit rejection reasons.
- reference.csv: the four frozen investigation fixtures under the same core screen.
- selected-assessment.csv: measurements for the two winners.
- selection.txt / playtest.txt: the retained catalog and two appended finales,
  only exported if both worlds have qualifying candidates.

If a world has no qualifying candidate, the process fails with diagnostics
retained. It does not substitute a longer/easier candidate or export a partial
campaign.

To review a pack's challenge features independently:

~~~powershell
.\gradlew.bat :puzzle-engine:run --args="review-challenges --input puzzle-engine/src/main/resources/levels/playtest-v2.txt --output build/puzzle-lab/my-review.csv"
~~~

Standalone review uses the explicit generic ChallengeRequirements defaults
(35% best-bot success, 3 decisions, 1 misleading checkpoint).
Refinement/workshop use the stricter reference-derived requirements recorded
in their parameters; consult those files when comparing eligibility columns.

The general workshop analyzes challenge candidates before selection, pins
Dusk 10, and exports challenge-assessment.csv and challenge-parameters.txt.
--challenge-count defaults to 48 requests per challenge profile, independently
of --per-profile for introduction/relaxed/regular pools. Its checked run is
build/puzzle-lab/workshop-screened-v1. It still makes a fresh 30-level draft;
use refinement for incremental additions preserving a shipped collection.

After review, an IDE edit copies the chosen pack into the versioned active
resource. New generated IDs contain a board digest; old IDs are never reused
for changed boards.

## Verification recorded at implementation time

71 engine and 16 app local tests passed. Tests compare the route-minimum
calculation with independent enumeration of all shortest paths, reject unknown
analyses and single-bot shortcuts, preserve decision measurements under exact
capacity/ball scaling, verify selected finales and exercise failed selection
without exporting a partial pack. All 32 certificates were replayed and their
shortest lengths checked; structural distinctness and save compatibility passed.

11 emulator instrumentation tests passed. They included playing both new finales
through real Hint/source/destination controls and Undo. Debug build and lint passed
with the previously documented warnings. No physical-device validation was
performed. Human follow-up feedback is recorded above.

## Emulator test cleanup correction

The instrumentation runner's default cleanup was observed to uninstall the app,
removing the playtest emulator's local save. That earlier save could not be
recovered. This was a test-workflow error, distinct from save-format compatibility.

gradle.properties now enables:
android.injected.androidTest.leaveApksInstalledAfterRun=true

A real completion was saved, its SHA-256 recorded, the full 11-test emulator suite
rerun, and the identical private-save checksum verified afterward. The app remained
installed. The Compose tests use an in-memory store and do not overwrite the
player's save. Keep this retention setting when using the same emulator for both
manual playtesting and automated tests.
