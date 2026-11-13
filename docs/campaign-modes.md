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
