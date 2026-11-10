# Algorithm validation: corpus v1 and authoring diagnostics

## Scope

This increment improves offline puzzle authoring and measurement. It does not add
the playable screen, an automatic human difficulty rating, or phone optimization.

The engine now:
- Reports exactly one terminal classification per generation attempt.
- Separates proved unsolvability from time/state/expansion/frontier limits.
- Distinguishes an exact move-band failure from insufficient bounds.
- Rejects duplicate structures under tube permutation and global color renaming.
- Retries rejected candidates within the existing attempt budget.
- Exports diagnostics alongside accepted boards and replay-verified certificates.

The structural matcher is bounded exact backtracking, preceded by necessary
invariants. A matching fingerprint is not a duplicate proof. If matching runs out
of its pairing budget, the candidate is conservatively rejected as uncertain.
This is an optional, single-worker authoring index, separate from solver keys.
Broader near-duplicate/solution-pattern filtering remains future work.

## Fixed regression corpus

PuzzleCorpus.VERSION = 1 contains 12 literal root fixtures plus 10 intermediate
positions along two known optimal solutions: 22 cases total. The hint positions
are correlated and are labeled separately in reports.

Fixtures cover solved, blocked, legal-but-unsolvable, infeasible quantities,
matching groups, capacities 2/3/4/6, mixed capacities, moving a completed tube,
four-color and eight-color puzzles. Solvable roots have explicit expected shortest
lengths. Exact search must agree; all returned solutions are replay-checked.

The feasible-quantity dead end has four three-slot tubes, bottom-to-top:
- [1, 0, 2]
- [1, 0, 2]
- [0, 2]
- [1]

There are three pieces of each color, so a full monochrome goal fits. Initially,
either of the first two tubes can send its top 2 into the third tube. Each choice
leaves a non-solved board with no legal moves. Thus legal moves are not a
solvability certificate, even when quantities and capacities are compatible.

Corpus execution: one warm-up pass, three measured repetitions, three algorithms,
1,500 ms / 100,000-state limits per search. The saved report has 198 observations:
168 solutions, 27 unsolvability proofs and three stored-state limits. All limits
were the no-symmetry algorithm on the eight-color root. Warm-up is excluded.
The exact solver with symmetry solved every solvable fixture within budget.

Summaries include outcome counts and nearest-rank p50/p95 durations, both across
all outcomes and conditional on solving. Unsolvable/limited groups do not receive
fake zero-valued solved-only percentiles. Millisecond resolution reports zero for
sub-millisecond searches; this is not zero cost. JVM measurements are not phone
latency estimates, and three repetitions are only an initial baseline.

## Single-attempt candidate surveys

Three ordered batches used seeds 42 through 141, four colors, four slots,
one attempt per seed, exact search, 250 ms / 20,000-state limits, and a shared
structural diversity index within each batch.

| Candidate method | Template empty tubes | Accepted | Proven unsolvable | Structural duplicates | Search limits |
|---|---:|---:|---:|---:|---:|
| Shuffle | 1 | 55 | 45 | 0 | 0 |
| Shuffle | 2 | 100 | 0 | 0 | 0 |
| Reverse | 2 | 92 | 0 | 8 | 0 |

These are finite sample results, not general solvability probabilities.
Accepted verified path lengths ranged 4–14 (shuffle/one empty), 4–14
(shuffle/two empty), and 5–11 (reverse). Their medians were 11, 12 and 8.
These values are not human difficulty scores.

Reverse walks can change the occupancy profile: accepted reverse candidates had
one OR two empty tubes and partially filled tubes; shuffled candidates preserve
the template's full/empty occupancy. Consequently these pools are not controlled
comparisons of equivalent board layouts. The one-empty shuffled pool also excludes
45 unsolvable candidates, so its accepted median cannot establish that one empty
tube is easier than two.

The useful finding is that unchecked shuffling would have shipped impossible
levels in the one-empty sample. The reverse pool also contained eight color/tube
permutations of already accepted boards, now removed by structural comparison.

## Verification performed

- 44 unit tests passed.
- Existing independent BFS comparisons still cover 1,014 small boards with
  symmetry both enabled and disabled.
- Structural matching agrees with an independent brute-force canonicalization
  over 160 deterministic pairs, plus adversarial/mixed-capacity cases.
- A tiny two-color batch exhausts its two genuinely different unsolved structures
  rather than continuing to emit recolored duplicates.
- Rejection accounting, cancellation, immutable diagnostics and uncertainty are tested.
- The updated Android debug APK builds successfully.
- No emulator, physical-device, sound, haptic or battery tests were run.

One hand-estimated shortest length in an initial new fixture was corrected from
five to four after search found a valid shorter certificate. Move targets should
come from verified analysis, not estimates made while authoring.

## Artifacts and commands

Generated CSVs are build artifacts under build/puzzle-lab:
- corpus-v1.csv and corpus-v1.summary.csv
- survey-shuffle-one-empty.csv and its summary
- survey-shuffle-two-empty.csv and its summary
- survey-reverse.csv and its summary

The versioned fixture definitions are stored as project source in
puzzle-engine/src/main/kotlin/com/example/ballsortgame/engine/tools/PuzzleCorpus.kt.
Do not rely on build reports surviving a clean. The implementation lives in this
workspace; repository initialization has not been performed.

Run from the project root:

~~~powershell
.\gradlew.bat :puzzle-engine:test
.\gradlew.bat :puzzle-engine:run --args="corpus --warmup 1 --repeats 3 --max-ms 1500 --max-states 100000 --output build/puzzle-lab/new-corpus.csv"
.\gradlew.bat :puzzle-engine:run --args="survey --strategy shuffle --count 100 --seed 42 --colors 4 --capacity 4 --empty 1 --attempts 1 --max-ms 250 --max-states 20000 --output build/puzzle-lab/new-survey.csv"
~~~

Existing report/export paths are never deliberately overwritten. Choose a new
output name when rerunning. Survey reports contain measurements and seed IDs,
not campaign level files; the generate command exports actual boards/certificates.

## Next decisions

Keep PC authoring as the primary workflow. Next evaluate stronger admissible
heuristics and compact state encodings against the same fixed corpus before
porting performance expectations to phones. Expand mixed-capacity/adversarial
fixtures and examine solution branching before inventing a composite difficulty
score. Ultimately a small playable review board is needed to calibrate human
difficulty, pacing and fun.
