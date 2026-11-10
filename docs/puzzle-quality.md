# Puzzle quality: shared analyzer, tiers and the E2 Infinite recipe

Written 2026-11-10 after reviewing generation and selection. This supersedes the
"Infinite does no quality filtering" statements in infinite-mode.md and the handoff.
challenge-selection.md and difficulty-investigation.md remain the background.

## What the review found

1. **Every shipped non-challenge level is solved by simple rules of thumb.** Running the
   existing challenge review over all 32 levels: 26 are solved 100/100 times by all four
   greedy strategies and have no misleading position on any shortest route. Only the six
   levels authored as challenges differ, and Moonlight 10 is also 100/100. There is nothing
   in between: the campaign jumps from "cannot fail" to "rules of thumb always fail".
2. **Spare tubes decide this, not capacity or colors.** Sampling 300 uniformly shuffled
   starts per recipe (colors 3–5, capacity 3–5):
   - two spare tubes: 2,690 of 2,700 starts are RELAXED, whatever the size;
   - one spare tube: a real mix. For five colors, 49–85% are unsolvable, and of all starts
     roughly 2–10% are BALANCED and 6–9% CHALLENGE. EXPERT is about 1 in 300, only at 5×5.
3. **Infinite (E1) checked nothing.** Balanced was three colors with two spares, so always
   trivial. Its stored solution was the 50–100 step construction walk, so hints could wander.
4. **The exact analysis is cheap.** One-spare boards have tens to low hundreds of reachable
   states, so a complete state graph is affordable on a phone. E1 skipped it unnecessarily.
5. **Only five stone colors exist** (PieceColors). More colors would need a product/art decision.

Full numbers: `tier-survey` output in build/puzzle-lab/tiers-2 (not committed; rerun to reproduce).

## PuzzleAnalyzer and tiers

`PuzzleAnalyzer.analyze(board)` (engine/PuzzleAnalysis.kt) builds the complete reachable graph,
bounded by a state count and never by time, and returns a shortest solution plus: reachable and
losing states, opening choices and losing openings, the minimum number of decision, temptation
and counter-greedy positions over every shortest route, and how often the best of four simple
strategies wins. It is used by desktop tools and by the phone.

The strategies now play on the graph with random tie-breaks. The older desktop review shuffles
tube order instead; values are close but not identical (Dusk 10: 10/100 here, 8/100 there).

`PuzzleTier` is a provisional machine label, anchored to the creator's ratings:

| Tier | Rule | Human anchors |
| --- | --- | --- |
| RELAXED | best simple strategy wins at least 90% | Dusk 9 (easy), Moonlight 10 (straightforward) |
| BALANCED | wins 20–89%, at least 3 required decisions | none yet rated |
| CHALLENGE | wins under 20%, at least 5 decisions, at least 2 misleading positions | Dusk 10, Moonlight 11 |
| EXPERT | wins at most 5%, at least 12 decisions, at least 6 temptations | Midnight 11 |

Midnight 10 (rated between Moonlight 10 and Dusk 10) measures near the BALANCED/CHALLENGE
line and lands on either side depending on tie-breaking. Boards fitting no tier are not offered.
**BALANCED has no human-rated example. It is the tier most in need of playtest feedback.**
These are not human difficulty ratings; thresholds should move when feedback disagrees.

## E2 Infinite recipe

Codes are `E2-R-seed`, `E2-B-seed`, `E2-C-seed` (Relaxed, Balanced, Challenge). A seed picks a
recipe, shuffles a start, analyzes it exactly, and repeats (up to 48 candidates) until the tier
matches and the shortest solution is not too short. Challenge also accepts the rare EXPERT board.
The stored solution is a shortest one. Relaxed draws mostly from two-spare boards; Balanced and
Challenge always have one spare tube. E1 codes and saved examples still replay unchanged.
Recipe lists and analyzer behaviour define what an E2 code means: change them only as E3.

The app offers the three tiers under Infinite ⋯. New puzzles are always E2; queued E1 puzzles
from an older install are skipped.

Measured generation time per puzzle (200 seeds per tier on the PC; 8 on the emulator):

| | PC p50 / p90 / max | Emulator, debug build |
| --- | --- | --- |
| Relaxed | 6 / 34 / 82 ms | 18–1457 ms |
| Balanced | 4 / 11 / 22 ms | 47–138 ms |
| Challenge | 7 / 19 / 30 ms | 121–883 ms |

No seed failed for Relaxed or Balanced; 4 of 200 Challenge seeds had no puzzle (the app retries
with another seed). No physical phone was measured.

## Authoring new campaign levels

~~~powershell
.\gradlew.bat :puzzle-engine:run --args="tier-pool --count 8 --seed 80000 --output build/puzzle-lab/my-pool"
.\gradlew.bat :puzzle-engine:run --args="tier-survey --count 300 --seed 70000 --output build/puzzle-lab/my-survey"
.\gradlew.bat :puzzle-engine:run --args="seed-benchmark --count 200 --seed 1"
~~~

`tier-pool` writes pool.txt (a normal level pack with shortest solutions) and candidates.csv
(tier and every measurement, with empty playtest columns) for each capacity, excluding
structural duplicates of each other and of the shipped pack. Use `pack` and `verify-pack` as
before. Nothing is imported into the app automatically. A checked run of 79 candidates is in
build/puzzle-lab/tier-pool-2; capacities 3 and 4 produced no EXPERT board in 4,000 shuffles.

## Not done / open

- No new campaign levels were added or reordered. The middle of each world is where
  BALANCED levels would go; that needs the creator's play ratings first.
- The older workshop and finale refinement still select by move bands plus the challenge
  screen. They were left unchanged; `tier-pool` is the tier-aware path.
- Ideas not pursued: a smaller second spare tube (mixed capacity) as a likely source of
  moderate puzzles, more than five colors, and trap-depth measures. All need a product decision.
