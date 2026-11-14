# Campaign modes (2026-11-12)

Built at the creator's request after the Infinite mock examples (infinite-variants.md):
all four ideas are now real, optional campaign sections. Not yet played by a person.

## What the player sees

Each world's level list has its classic levels first, then four titled sections, each
numbered from one and marked optional: Mixed sizes, Sealed tubes, Connected boards and
Restore a pattern. Seven levels per section in Dusk and Moonlight, six in Midnight: 80
mode levels. With the enlarged classic campaign (60 levels: Dusk 19, Moonlight 19,
Midnight 22) that is 140 levels in all. Everything stays unlocked for playtesting.
One easy level in each mixed, sealed and connected section is a hidden-colours level.

- **Mixed sizes.** Ordinary sorting; the spare tubes are shorter than the home tubes
  (for example 2+1). A colour may end in more than one full tube.
- **Sealed tubes.** One or two tubes start under a stone stopper with stones inside. A
  sealed tube opens for good once enough other tubes are full and one colour; its label
  shows progress ("Sealed · 0/2"). Its stones count towards the goal.
- **Connected boards.** Two shores and a shared tube; stones cross only through the
  shared tube. Each shore has a faint panel behind it.
- **Restore a pattern.** One stone per move onto any colour; the level ends when the
  board equals the small target shown above it, in the same columns. Nothing can be lost.
  Hidden colours never applies here.

Undo, restart, hints, the "go back" tip, saves, sound, haptics and stone motion all work
in every mode. Hints search the level under its own rules.

## How it is built

- `engine/LevelRules.kt`: `LevelRules(seals, bridge, target, singleStone)`, `RuledPosition`
  (board + which seals are open) and `RuledSolver` (shortest solution under the rules;
  breadth-first for sorting, A* for an exact target). The default is classic and behaves
  exactly as before. `LevelMode` is derived from the rules and tube sizes.
- Level pack: an optional ninth field carries the rules, e.g. `seal=5@1,6@2`,
  `bridge=0,1,2/3`, `target=3:0,1,2|…;single`. Classic records keep eight fields.
  The campaign is `playtest-v5.txt`: every level of `playtest-v3.txt` unchanged, 27 added
  classic levels, and the mode levels.
- `GameSession` and `GameViewModel` play, undo and restore through the level's rules.
  Only moves are saved; seal state follows from replaying them, so the save format did
  not change. Known solutions are cached for the level being played, not all at launch.
- `PuzzleBoard` draws stoppers, shore panels and labels from `GameUiState.rules`;
  `rowGeometry` / `connectedGeometry` lay out pattern and connected boards. The Infinite
  mock screens use the same drawing by describing themselves as rules.
- `mode-campaign` (engine/tools/ModeCampaign.kt) generates candidates per mode and tube
  size, solves each exactly, measures it and picks a ladder:
  easy, easy, a step up, easy again, harder, a breather, hardest.

~~~powershell
.\gradlew.bat :puzzle-engine:run --args="mode-campaign --attempts 220 --analysis-states 25000 --output build/puzzle-lab/my-modes"
~~~

It writes levels.txt (LEVEL lines to append to the classic pack) and chosen.csv.

## How levels were graded, and the limits

For sorting modes: the share of reachable positions that are dead ends, how often a plain
player wins (finish a colour, else join a match, never repeat, no Undo), and length.
For patterns only length, since nothing can go wrong. These describe how the rules behave;
they are not human ratings. Known weak spots:
- Two-seal levels are rare at random (1 usable in 220 for five-slot tubes), so Midnight
  has a single two-seal level and it is an easy one.
- Pattern levels have no real hard end; later ones are longer (up to 22 moves), not harder.
- Connected and sealed levels were limited to graphs under 25,000 positions and pattern
  levels to ones solved within 40,000 search states, so phone hints stay quick.
  Hint speed on a phone was not measured.
- Launch on the emulator's debug build takes about 6 s; reading and checking 113 levels
  is about 0.75 s of that. A release build on a phone was not measured.

## Verified

Engine and app unit tests (including: every mode level re-solved under its rules with the
stored shortest length; rules through the pack format; seals, bridge and pattern rules),
lint, and all 26 emulator tests. Seen on the emulator: the Dusk list sections, and one
level each of mixed, sealed (two seals), connected and pattern; one pattern move made.
Not done: a full playthrough of any mode level by hand, Moonlight/Midnight mode levels on
screen, compact screens or large text, any phone.

## Classic expansion (2026-11-13): playtest-v6

The creator's direction: the game is still primarily ball sort, so plain sorting levels
must dominate in number, with hidden colours as an occasional modifier.

`playtest-v6.txt` is the current campaign (`BundledLevelPacks.playtest()`): 236 levels,
156 plain sorting and 80 mode levels (Dusk 79, Moonlight 79, Midnight 78). Every v5 level
is unchanged under its ID and in the same relative order, so saved progress carries over;
only level numbers shift. `playtest-v5.txt` stays as `playtestV5()` for comparison.

Added per world by the `classic-expansion` lab command (ClassicExpansion.kt):
- 24 calm levels: no position from which the level is lost, simple play finishes at
  least 95% of the time, and at least 90% without sight of buried stones. IDs `*-calm-*`.
  Four of them per world carry the hidden-colours rule (now 8 hidden classics per world).
- 6 steadier levels (tier BALANCED, simple play finishes at least half the time),
  role REGULAR, IDs `*-steady-*`.
- 2 optional challenges (tier CHALLENGE), role CHALLENGE, IDs `*-peak-*`.
Boards use up to six colours with three- and four-slot tubes, so later levels have more
to sort. New levels are woven into each run: calm ones by rising length through the whole
run, steadier ones from the first third on, challenges late and never three in a row.

```
.\gradlew.bat :puzzle-engine:run --args="classic-expansion --output build/puzzle-lab/my-classic"
```
It writes the complete pack (playtest-v6.txt), added.csv and parameters.txt, and took
about 14 minutes on the development PC with the defaults (seed 160000, 1200 attempts per
world). These measurements describe simple strategies, not human difficulty; none of the
new levels has been playtested by a person yet.

## Modes tried and dropped (2026-11-13)

Home tubes, the pile, a covered tube and long stones each got one test level. The creator
played them and rejected the pile, the covered tube and long stones outright ("no point
in them"). Home tubes was dropped too: with a goal-only rule a colour can be gathered
anywhere and then moved home in one group move, so it does not change how a level is
played; making home tubes refuse other colours only removes spare tubes, which is a
harder plain level, not a new idea. All four were removed from the code. Do not rebuild
them without a rule that changes strategy.

## Campaign rebuild after play (2026-11-14): playtest-v7

The creator played v6 and found: a sealed tube holding an already sorted colour; a mode
first met combined with another; a trivial level marked challenge and a medium one not;
mixed sizes ending two different ways; connected boards that were only extra taps; no
challenge in Midnight until level 42; late levels too easy. `playtest-v7.txt` is the
answer (`BundledLevelPacks.playtest()`; v6 stays as `playtestV6()`), built by the
`campaign-rebuild` lab command (CampaignRebuild.kt), about 11 minutes:

```
.\gradlew.bat :puzzle-engine:run --args="campaign-rebuild --attempts 600 --classic-attempts 900 --output build/puzzle-lab/my-rebuild"
```

- **Roles are measured**, no longer taken from a generator's list position. Score = how
  often simple play fails the level: under 0.15 relaxed, up to 0.80 regular, above that
  challenge. Plain levels use PuzzleAnalyzer; mode levels use the plain player in
  ModeCampaign over the full state graph; patterns use length only.
- **Pacing:** 246 levels (Dusk 81, Moonlight 83, Midnight 82). Each run starts relaxed,
  regular levels grow more common (position follows a square-root curve), and challenges
  are spread from about a quarter of the way in (first at level 19-20), the hardest
  last. Challenges alternate which mode they come from.
- **Modes met alone:** mixed, hidden, sealed, connected, pattern each first appear by
  themselves through their gentlest level, one at a time. A combined level always comes
  after both parts.
- **Mixed sizes:** rule `gather` (pack text `gather`): every colour must end in ONE tube.
  Same boards and IDs as before, re-solved for that goal.
- **Sealed tubes** always hold at least two colours; the others were replaced.
- **Connected boards are all new.** Rule change: a stone crosses the middle tube in one
  move while it is empty; anything parked in it closes the crossing. Shores have little
  or no spare room, and every level is PROVEN unsolvable with the middle tube removed
  (which is exactly "never parking in it"). The plain player rates these harder than
  they probably play, so they were picked from the gentler half of what was found.
- **Plain levels:** the eight shortest recently added calm levels per world were dropped;
  about ten regular (BALANCED tier) and up to two challenge levels were added per world.
- LevelPackTest states all of the above as rules for the current pack.

Consequences to know: levels that were replaced or dropped lose their saved progress, and
a save whose current level was replaced opens on the world's first level (the session
repairs this by design). Nothing in v7 has been playtested by a person.

## Level stones (2026-11-14)

Finished levels have no tick. Three looks were captured for the creator (FinishedLook in
LevelPath.kt); they chose the glow: **LANTERN**. A finished stone is lit warm from
within, its number and rim turn warm, and warm light lies on the ground; neighbouring
lights are combined with "lighten", so a run of finished levels is one soft field without
hot spots, and the first and last rows let the light spill past their edge. The challenge
badge is pale blue so it cannot be mistaken for that light. A level shows a mark for each
of its traits (up to two). SETTLED and MOONLIT remain in the code as alternatives.