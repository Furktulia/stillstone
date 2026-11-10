# Capacity, misleading moves and human difficulty

## Trigger and feedback to retain

The playtester reported Dusk 9 as very easy, Dusk 10 as enjoyably challenging,
Moonlight 10 as noticeably easier/straightforward, and Midnight 10 as better but
still less challenging to them than Dusk 10. Their explanation emphasized that
a surface-first strategy fails in Dusk 10 and moving purple into the empty tube
can destroy solvability.

This is one person's qualitative feedback, in a sequential play session, without
recorded timings or move traces. It is valuable calibration evidence, not a
population difficulty ranking. Learning from earlier puzzles may affect later
ratings.

## Exact examination of those boards

The study exhaustively enumerated every reachable state after capacity-preserving
tube-permutation equivalence, with solved states terminal. Reverse breadth-first
search from all goals gives exact remaining distances and distinguishes losing
states from solvable detours. Colors are kept fixed. No heuristic pruning is used
in graph exploration, and partial graphs return unknown rather than classify losses.

| Puzzle | Shortest moves | Reachable quotient states | Winning / losing states | Safe distinct opening choices |
| --- | ---: | ---: | ---: | ---: |
| Dusk 9 | 8 | 84 | 84 / 0 | 4 / 4 |
| Dusk 10 | 12 | 46 | 30 / 16 | 4 / 5 |
| Moonlight 10 | 15 | 39 | 25 / 14 | 1 / 5 |
| Midnight 10 | 19 | 119 | 73 / 46 | 3 / 5 |

Dusk 9 has no reachable unsolvable position. The player can take detours, but
cannot make an irreversible solvability mistake. That is consistent with it
feeling forgiving.

Dusk 10 starts with single-ball top groups everywhere. The tempting opening
choices are visually similar, but their consequences differ. Purple from tube 5
to empty tube 6 is proven losing, despite legal moves still being available.
Purple from tube 2 to tube 6 remains solvable (its shortest continuation is 12,
making 13 including that first move). Subsequently adding tube 5's purple to
that same tube is also proven losing. The general color choice alone does not
specify the outcome: source order and the buried dependencies matter.

Gold from tube 3 or 4 into tube 6 starts a shortest solution (11 moves remain).
A useful interpretation is that the empty tube must first help expose useful
buried pieces; immediately collecting the accessible purples can consume that
flexibility. This is an interpretation of the exact state results, not a claim
about every player's thought process.

Moonlight 10 actually has MORE losing openings: four of five. However, its only
safe opening is also the uniquely largest top group: the two purple balls from
tube 3 into tube 6. Both simple surface strategies below solve it. Raw trap count
or a narrow solution route alone would therefore mis-rank these two puzzles.

Midnight 10 has safe two-red-ball openings. On the stored optimal route, a later
position (step 14) makes both surface heuristics prefer losing choices. This is
one route, not proof that every solution must pass through that position.

## Two simple strategy probes

These are deliberately limited computer rules, not validated human models:

1. Largest group: prefer the largest actual transfer, then a matching destination.
2. Least buried color: prefer the exposed color with the fewest nonmatching pieces
   above its lowest occurrences; then prefer more exposed pieces, larger transfers,
   and matching destinations.

Both avoid repeating a tube-symmetry-equivalent state, use at most 200 moves, and
have no Undo/backtracking. Failure to find a new successor is a policy failure,
not a proof that the current board is unsolvable. Scores can tie, so a single
tube-index tie order would be a weak experiment.

Each exact board was therefore tested under 100 random tube permutations per
strategy. The logical puzzle is unchanged; this controls arbitrary index-based
tie-breaking. Trials can repeat a permutation and are not human success rates.

| Puzzle | Largest-group successes | Least-buried-color successes |
| --- | ---: | ---: |
| Dusk 9 | 100 / 100 | 100 / 100 |
| Dusk 10 | 9 / 100 | 13 / 100 |
| Moonlight 10 | 100 / 100 | 100 / 100 |
| Midnight 10 | 0 / 100 | 0 / 100 |

This supports the Dusk-versus-Moonlight observation. It does NOT reproduce the
playtester's complete ranking: the bots fail more consistently on Midnight.
These two rules omit pattern recognition, learned strategies, visual salience,
lookahead and use of Undo. They are useful candidate features, not a final
difficulty formula.

## Capacity does not determine decision difficulty

Our rules move a maximal matching group. There is an exact counterexample to
automatic difficulty growth:

- Multiply every tube capacity by an integer q.
- Replace every individual ball with q consecutive balls of the same color.
- Every legal move remains legal at the same source/destination, transferring
  q times as many balls. The successor is exactly the expanded original successor.
- Full/empty/goal status is preserved, and every reachable expanded state remains
  within this scaled family.

Consequently the reachable decision graphs are isomorphic: shortest group-move
length, branch structure and solvability consequences are identical. This is
our own derivation from the grouped-transfer rule.

It was checked against every physical legal transition at all 288 reachable
quotient states of the four fixtures. Dusk 10 expanded from capacity 3 to 6 still
requires exactly 12 group moves and has the same 46 quotient states and traps.
Equivalent checks covered capacities 4 to 8 and 5 to 10, plus a mixed-capacity
partial-transfer regression test.

More generally, with five initially full tubes and one empty tube, there are
five physical opening moves at ANY equal capacity. More balls do not create
more opening choices automatically. Larger capacities permit more initial
orderings, longer interleavings and potentially deeper dependencies, but may also
just enlarge existing groups. They provide design possibilities; these tests do
not establish a strictly increasing human skill ceiling for capacities 3, 4, 5, 6.

## Controlled sampling across capacities

The study also evaluated 800 single-attempt shuffled starts: five colors,
capacities 3–6, one or two empty tubes, 100 seeds per profile (90000–90099).
No solvability or move-length filtering was applied to this pool. Each color
occurs exactly one tube's capacity times; filled tubes start full.

These are profile-matched samples, not isomorphic paired boards across capacities.
The same numeric seed shuffles different bag lengths. Search used exact A-star,
1500 ms and 300000 expanded/stored/frontier limits per board. All 800 outcomes
were classified with that budget; no limits remained. An earlier smaller-budget
pass had ten unknowns, so its solved-only tail was not used as complete evidence.

With TWO empty tubes:

| Capacity | Solvable starts | Median / p90 shortest moves | Largest-group successes | Least-buried-color successes |
| --- | ---: | ---: | ---: | ---: |
| 3 | 100 / 100 | 11 / 12 | 95 / 100 | 100 / 100 |
| 4 | 100 / 100 | 15 / 16 | 92 / 100 | 99 / 100 |
| 5 | 100 / 100 | 18 / 21 | 90 / 100 | 97 / 100 |
| 6 | 100 / 100 | 23 / 25 | 87 / 100 | 97 / 100 |

Larger capacity increased path length and mixing in this pool, while a simple
surface strategy remained very successful. This is evidence that many larger
puzzles can add execution length without demanding much new strategy.

With ONE empty tube:

| Capacity | Solvable / unsolvable starts | Median / p90 shortest among solvable | Largest successes among solvable | Least-buried successes among solvable |
| --- | ---: | ---: | ---: | ---: |
| 3 | 51 / 49 | 10 / 12 | 17 / 51 | 35 / 51 |
| 4 | 27 / 73 | 15 / 16 | 4 / 27 | 9 / 27 |
| 5 | 18 / 82 | 17 / 20 | 0 / 18 | 4 / 18 |
| 6 | 7 / 93 | 23 / 25 | 0 / 7 | 0 / 7 |

Capacity and spare tubes interact strongly in these samples. Bigger one-empty
starts were more often unsolvable, and the solvable subset defeated the simple
rules more often. The capacity-6 solvable subset has only seven cases, so its
percentages are particularly uncertain. Conditional success rates must not be
compared as if the underlying accepted sets were identical. This is not evidence
that two empties guarantee solvability at these capacities.

For future phone generation, blind shuffling at scarce-space/larger-capacity
profiles may waste many attempts. These are PC observations, not a phone latency
benchmark or a decision to change the current generation backend.

## Relevant research and limits of inference

Ito et al. analyze formal ball/water-sort solvability, including capacity and
empty-bin parameters. Our maximal group transfers match their water-move
definition. Their general NP-completeness results concern computational
solvability; they do not rank human difficulty at our small fixed capacities.
[Sorting Balls and Water](https://arxiv.org/abs/2202.09495).

Jarušek and Pelánek studied human solving of other transport puzzles and found
that state-space size and shortest path alone did not explain difficulty.
Their findings support looking at decision structure. Transferring that
principle to this game is an inference; it does not validate our two bots as a
human model.
[What Determines Difficulty of Transport Puzzles?](https://www.fi.muni.cz/~xpelanek/publications/flairs-transport.pdf).

This investigation did not locate a controlled human Ball Sort study establishing
a capacity-3-to-6 skill-ceiling curve. Neither the sampled distributions nor the
scaling proof answers how experts' maximum attainable skill changes across those
capacities. Human testing, including varied level order, is still necessary.

## Implications for the next selector revision

The existing workshop gates intended challenge pools primarily by exact shortest
move bands. For the first selection in each role, suggestPlaytest chooses the
shortest qualifying candidate. Opening branch data is reported after selection
and does not influence it. This is a real limitation: it admitted the longer but
greedy-solvable Moonlight finale.

Recommended next changes:

- Retain Dusk 10 as a fixed positive human-feedback benchmark, and Dusk 9 as a
  forgiving/breathing reference. The four literal fixtures are now stored
  independently of future campaign reordering.
- For challenge selection, measure success of several cheap strategies across
  tie orders, tempting LOSING choices, ambiguous safe/unsafe ties, and later
  dependencies. Keep moves and capacity as separate descriptors.
- Do not reward trap count alone: Moonlight 10 demonstrates why that fails.
- Avoid requiring every hard puzzle to have irreversible traps. Recoverable
  detours, multi-stage planning and resisting premature consolidation may also
  be interesting; validate these with people.
- Distinguish an interesting inference from arbitrary trial-and-error punishment.
  Use playtester feedback about why a mistake felt fair or unfair.
- Keep new-capacity introductions gentle; select world finales for actual
  reasoning demands rather than their larger tubes.
- Evaluate new selection criteria on additional human-rated puzzles before
  claiming calibration. The two bots already disagree with part of this user's
  ranking, so a single weighted score would be premature.

The playable boards, rules and campaign selection were not changed by this study.

## Reproduction and verification

~~~powershell
.\gradlew.bat :puzzle-engine:run --args="difficulty-study --count 100 --seed 90000 --max-ms 1500 --max-states 300000 --graph-states 100000 --graph-ms 5000 --output build/puzzle-lab/my-difficulty-study"
.\gradlew.bat :puzzle-engine:test :app:testDebugUnitTest :app:assembleDebug
~~~

Use a fresh output directory. The checked final run is
build/puzzle-lab/difficulty-study-v4. It exports levels.csv, openings.csv,
decisions.csv (the stored optimal route only), scaling.csv, policy-summary.csv,
capacity-raw.csv, capacity-summary.csv and parameters.txt. Summary p90 is nearest
rank; all reported path lengths use group moves.

The frozen boards/certificates are in
puzzle-engine/src/main/resources/levels/difficulty-study-v1.txt.
The PC implementation is engine/tools/DifficultyStudy.kt, exposed through PuzzleLab.
The graph is bounded and offline; it is not invoked by Android hints or generation.

62 engine tests and 15 app tests pass, and the Android debug APK builds. Seven
new tests compare backward-graph classifications and exact distances with the
existing solver at every reachable fixture state, preserve unknown outcomes for
truncated exploration, check integer scaling and mixed partial transfers, and
regress the observed Dusk/Moonlight cases and purple commitment trap.

Emulator UI tests were not rerun for this PC analysis change. No phone, human
timing experiment, audio or battery test was performed.
