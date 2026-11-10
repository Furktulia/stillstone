# Puzzle engine and desktop lab

This is the algorithm-first foundation for Ball Sort Game. Android and the host-JVM
lab share the same rules, solver and generator. Android now has a playable
prototype with 32 puzzles in three freely selectable playtest worlds, undo/restart,
hints and local saves. See ../docs/playtesting.md for running it and validation limits.
The PC workshop generates/reviews candidate pools and exports edited level selections;
see ../docs/level-workshop.md. Final unlocking and human difficulty calibration remain
later milestones.

## Rules and state

- Tube contents are bottom-to-top; colors are nonnegative integer IDs.
- Each tube has its own positive capacity. No four-slot assumption exists.
- A move transfers the entire matching top run, limited by destination space.
- Destinations must be empty or have a matching top color.
- A goal has only empty tubes or full monochrome tubes.
- Completed tubes remain movable. This matters especially for mixed capacities.
- The core goal permits one color in multiple full tubes. Initial equal-capacity
  generation gives each color exactly one tube's worth; the final mixed-capacity
  campaign rule remains a product decision.
- Tube/board collections are defensively copied and unmodifiable.

## Solver correctness contract

A-star uses unit-cost group moves and an admissible lower bound:
the maximum of color-boundary count and half the number of partial occupied tubes,
rounded up. One move can remove at most one boundary and resolve at most two partial
tubes. Both terms are zero at a goal.

Weight 1 proves shortest solutions when successful. Larger heuristic weights can
find a valid path sooner, but never claim optimality. All state belongs to an
invocation; independent calls can run on separate workers.

The transposition key sorts complete (capacity, contents) tube records. Only
capacity-preserving exchanges of contents become equivalent. There is no unsafe
pruning of completed tubes, monochrome transfers or mixed-capacity destinations.
Search reopens a state when a shorter path is found.

Outcomes are separate: Solved, Unsolvable (complete exhaustion), LimitReached
(time/expansions/stored states/frontier), and Cancelled. A timeout is not proof
of impossibility. Limits bound search counts, not exact byte usage; these defaults
are research defaults, not a validated phone memory/performance configuration.
Callbacks or thread interruption support cancellation. Search is synchronous:
the future Android caller must run it off the main thread.

## Two complementary generators

CERTIFIED_REVERSE starts at a full monochrome goal, samples legal predecessors,
and checks that each predecessor's actual maximal forward transfer reconstructs
the previous board. It retains and replays the complete winning certificate.
Self-avoiding walks can terminate early. Walk depth is NOT puzzle difficulty,
and requested depth is a maximum, not a guarantee. These candidates often skew easy.

SHUFFLE_AND_SOLVE shuffles the goal's color quantities into the same occupancy
profile, preserving full/empty or partial tube layouts. Solvability is unknown
until search succeeds. Unsolvable candidates and search timeouts are rejected.
This is the CLI's default and a broader starting pool for desktop authoring.
Mixed-capacity goals work through the Kotlin API and benchmark fixtures.

Both paths reject already solved candidates, filter boundary/empty-tube constraints,
and replay the accepted solution. Neither generator claims to sample uniformly.
Seeds reproduce candidate randomness, but time-limited acceptance can differ across
machines or JVM warm-up. Export the actual board and certificate, never only a seed.

minSolutionMoves applies to a proven lower bound on the shortest solution.
maxSolutionMoves requires a verified certificate no longer than that bound.
With requireOptimalSolution, both constrain the exact shortest length.
Reverse generation can retain its original certificate after evaluation times out;
shuffle generation cannot. Generation has attempt and walk budgets; search time
budgets are per candidate, not an overall batch deadline.

## Difficulty and variety

Reports include boundaries, runs, empty tubes, raw legal moves, distinct successor
states, verified path length, shortest-path proof status, rearrangement moves and
search effort. A rearrangement here means a move that does not remove a boundary,
not necessarily a counterintuitive human move.

There is intentionally no invented single difficulty score. Search effort varies
with algorithm/hardware, and minimum moves alone do not establish human challenge.
The workshop now offers a provisional order with introductions, breathing levels,
regular puzzles and challenges, plus candidate reports and structural diversity.
Human feedback is still needed to calibrate that order. Larger capacity is a
variety dimension, not an automatic difficulty promotion.

Batch authoring now rejects equivalent boards under tube permutation AND a consistent
global color renaming. A cheap fingerprint only selects candidates for exact
backtracking comparison; it is not itself a duplicate verdict. Per-comparison
limits return uncertainty, which conservatively rejects that candidate. The index
retains accepted boards only, is single-threaded, and is optional for engine callers.
The solver still uses its cheaper capacity-aware state key. Reversing pieces inside
tubes and near-duplicate puzzle structures are not treated as equivalent.

Every generation result includes diagnostics with one terminal reason per started
attempt. Counts reconcile exactly: attempts = accepted + rejected, including
cancellation during an attempt. Search totals include rejected candidates. Reasons
distinguish proven unsolvability, each search limit, duplicate candidates, batch
equivalence, and unmet selection constraints. With non-optimal search, an
insufficient shortest-path bound is MINIMUM_UNPROVEN rather than proof that a
puzzle is too easy; a long certificate is MAXIMUM_UNPROVEN rather than proof that
no shorter solution fits. Generator version 2 exports these diagnostics.

## Run on Windows

From the project root, using Android Studio's terminal:

~~~powershell
.\gradlew.bat :puzzle-engine:test
.\gradlew.bat :puzzle-engine:run --args="help"
.\gradlew.bat :puzzle-engine:run --args="generate --strategy shuffle --colors 4 --capacity 4 --empty 2 --count 5 --seed 42 --require-optimal true --min-moves 8 --max-moves 20 --output build/puzzle-lab/levels"
.\gradlew.bat :puzzle-engine:run --args="generate --strategy reverse --colors 3 --capacity 3 --count 1 --seed 7"
.\gradlew.bat :puzzle-engine:run --args="benchmark --count 2 --seed 42 --max-ms 2000 --max-states 100000 --output build/puzzle-lab/benchmark.csv"
~~~

The run task uses the root as its working directory and a maximum 1 GiB JVM heap.
Use larger budgets deliberately for offline work; neither solver mode is advertised
as phone-ready. CLI files refuse to overwrite existing exports. A partially
successful batch reports failures with a nonzero exit code.
Generation JSON stores versioned rules/generator/format IDs, capacities, contents,
solution moves, metrics and search statistics. This JSON remains export-only.
The separate versioned text-pack format supports Android loading and edited selection;
it stores explicit boards, per-tube capacities, certificates and stable IDs.

## Verification

JUnit4 is reused without adding mocking, DI or UI-test frameworks.
The independent oracle implements its own ordered-state breadth-first search,
move generation and goal check: it does not reuse production moves, heuristics
or canonical keys. Exhaustive tests cover 1,014 small equal/mixed-capacity boards,
including three colors, with symmetry enabled and disabled. They compare
solvability and exact shortest length, heuristic bounds/consistency and piece
conservation.

Other tests cover malformed states, immutability, maximal/partial transfers,
legal-but-unsolvable states, all four search budgets, cancellation, certificate
replay, capacities 3 through 6, mixed capacities, repeatable generation,
intermediate-position solving and shortest-solution bands.

Initial PC measurements (seed 42, 1,500 ms / 100,000-state budget) found the
eight-color/four-slot shuffled candidate needed 26 moves optimally:
9,313 expansions with tube symmetry. Without symmetry it hit the 100,000 stored
state limit. Weighted search found a verified 27-move solution in 30 expansions.
These are illustrative fixtures, not representative performance guarantees.

The initial verification ran 28 passing tests and successfully built :app:assembleDebug.
Three shuffled levels (seeds 100, 101, 102) were exported with proven shortest
solutions of 11, 8, and 11 moves. Their JSON was also parsed independently.
Generated examples are in ../build/puzzle-lab/verified-levels/ (build artifacts,
not a committed campaign).

The saved ../build/puzzle-lab/baseline.csv contains 84 runs: seven profiles,
two seeds, two candidate methods and three solver configurations. 82 solved;
two no-symmetry searches reached the stored-state limit. All reported solutions
were replay-verified. These seed-selected fixtures do not estimate the acceptance
rate or the full complexity of arbitrary shuffled boards.

The initial lab measurements above did not exercise Android runtime behavior. Current
emulator verification is documented in ../docs/playtesting.md; phone, haptic, audio
and battery testing remain future milestones.

## Level workshop

~~~powershell
.\gradlew.bat :puzzle-engine:run --args="workshop --seed 20260 --per-profile 4 --attempts 30 --max-ms 1500 --max-states 150000 --output build/puzzle-lab/my-workshop"
.\gradlew.bat :puzzle-engine:run --args="pack --input build/puzzle-lab/my-workshop/pool.txt --selection build/puzzle-lab/my-workshop/selection.txt --output build/puzzle-lab/my-selection.txt"
.\gradlew.bat :puzzle-engine:run --args="verify-pack --input build/puzzle-lab/my-selection.txt --max-ms 5000 --max-states 150000"
~~~

Open the batch's review.html to inspect diagrams and select candidates, or edit
selection.txt directly. Reports preserve rejected-attempt reasons and distinguish
solvable, proven-dead and unresolved opening branches. Suggested roles and ordering
are authoring intent, not a human difficulty score. Bundled resources are replaced
explicitly after review; commands do not overwrite app resources.

## Next algorithm work

1. Expand the fixed corpus and single-attempt surveys further, especially difficult
   mixed-capacity and unsolvable states; review acceptance and latency by profile.
   Current corpus/report details are in ../docs/algorithm-validation.md.
2. Improve reverse exploration/restarts and structural diversity; compare against
   the shuffled pool rather than assuming a single generator is sufficient.
3. Evaluate denser state encoding, stronger admissible heuristics, and memory-bounded
   searches against the exhaustive oracle before accepting optimizations.
4. For phone hints, use cached verified continuations where applicable, then bounded
   fast search. A failed search should offer return to a certified history position,
   never falsely announce unsolvability.
5. Calibrate human difficulty through a small playable review board before selecting
   a final campaign or claiming smooth progression.

## Corpus and acceptance reports

~~~powershell
.\gradlew.bat :puzzle-engine:run --args="corpus --warmup 1 --repeats 3 --max-ms 1500 --max-states 100000 --output build/puzzle-lab/corpus-v1.csv"
.\gradlew.bat :puzzle-engine:run --args="survey --strategy shuffle --count 100 --seed 42 --colors 4 --capacity 4 --empty 1 --attempts 1 --max-ms 250 --max-states 20000 --output build/puzzle-lab/survey.csv"
~~~

Corpus v1 stores literal roots with declared optimal lengths and unsolvable
fixtures, plus intermediate positions from two known optimal certificates.
Its 22 cases include infeasible color quantities and a separate dead end with
feasible quantities and legal moves. Prefix cases are correlated hint positions,
not additional independent puzzle designs. The runner warms up, then records
repeated observations for exact, weighted and no-symmetry search. It validates
every returned solution and checks optimal results against the fixture.

Raw CSV and a sibling .summary.csv are written without overwriting files.
Corpus summaries report p50/p95 both for all outcomes and solved-only outcomes,
alongside limit/unsolvability counts. Solved-only percentiles are conditional,
so do not ignore censored searches. Millisecond resolution can report zero for
short searches. Repeated fixtures and JVM timings do not establish phone latency.

Survey defaults to one candidate attempt per seed; this exposes rejection rates
instead of hiding rejected candidates behind retries. --attempts can override it.
Surveys use a shared diversity index; acceptance depends on batch order as well
as selection constraints. The CSV records all terminal rejection counts.
A search limit is unknown solvability, not an unsolvable sample. Survey CSVs
are measurements, not campaign level exports; use generate for actual boards.
