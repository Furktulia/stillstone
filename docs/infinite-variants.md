# Infinite interaction examples (2026-11-12)

**Removed on 2026-11-13.** These mock screens were replaced by real campaign levels
(campaign-modes.md) and deleted at the creator's request. This file is history: the
design review below led to the campaign modes, and nothing in it describes current code.

Implemented at the creator's request to explore appearance and interactions before
working on reliable generation and difficulty selection. No puzzle-engine source,
published seed recipe or campaign board was changed for this feature.

## Entry and navigation

Tap the Infinite entrance for Ways to play. Classic retains the existing generated
Infinite board, difficulty selection, hidden-color setting, codes, bookmarks and export.
Classic and example screens now both have a visible Modes button in their top row,
opening Ways to play directly. Classic's ⋯ options retain the secondary entry.
Navigation follow-up: debug assemble and all five InfiniteVariantUiTest emulator
checks passed, including direct navigation from Classic with options kept separate.
The selected mode is remembered. Browsing and campaign play retain all existing saves.

The four new modes each have one fixed, solvable example, explicitly labelled Example
puzzle. Replay example restarts that board; no difficulty rating or random next puzzle is
claimed. Each example saves its own move history in infinite-variants-v1 preferences.
Classic's current.json and bookmarks are independent and are not replaced.
Hidden colors is unchanged for Classic/campaign and is not applied to these examples.

## Rules and presentation

- Mixed tube sizes: capacities 3 / 2 / 2 / 1. Existing full-and-monochrome-or-empty goal,
  including allowing a color in multiple full tubes. This is a prototype choice, not a
  final decision about all future mixed-capacity content.
- Sealed tube: a visible cap and Sealed label block the final spare tube until the first
  full monochrome tube is completed. Once earned the seal stays open, even if that color
  is moved again. Undo restores the seal state at the corresponding historical position;
  restart closes it.
- Connected boards: left and right shores each have two tubes, with one shared middle
  tube. Direct moves across shores are rejected with an explanation. Both shores can
  use the shared tube. Same-shore moves retain ordinary matching-color rules.
- Restore a pattern: a persistent, numbered three-tube target above the board. Exact tube
  contents must match (including empty tubes); monochrome sorting is not its goal.
  Matching target tubes have completion outlines. Ordinary group transfer rules remain.

All examples share the actual PuzzleBoard rendering, group animation, settling, sound,
haptics, color labels, reduced motion, undo and restart confirmation. Mode-aware hints
search the small fixed board under its actual rules on a worker, with cancellation and
a bounded state count; they do not use the ordinary sorting solver for different goals.

App-side rules/history live in game/InfiniteVariant.kt. The picker, example UI and
separate preferences live in ui/InfiniteVariants.kt. Connected geometry is in
BoardGeometry.kt; PuzzleBoard has optional variant decoration/goal parameters whose
defaults retain existing campaign/Classic behavior.

## Design review and proposed direction (2026-11-12)

This section records discussion and exploratory analysis after the examples were built.
It does not describe additional shipped behavior or authorize generator work.
The recommendations below are design judgements, not measured human skill ceilings.
The creator specifically proposed putting stones inside the sealed tube; that change
and the proposed Pattern rules are not implemented.

| Mode | Recommendation from the review | Main value / risk |
| --- | --- | --- |
| Mixed sizes | Keep; potentially an ordinary board variation within Infinite | Manage the shape of free space; different capacities are not automatically harder |
| Connected boards | Keep as a distinct mode; strongest potential for deeper planning | Coordinate sorting and exchanges; avoid obvious solutions padded with bridge transfers |
| Sealed tube | Empty-spare version suits occasional variations; investigate the creator's filled-tube proposal | A meaningful second phase could give it more lasting identity |
| Restore a pattern | Replace matching-color version with an optional relaxed arrangement mode | Freer construction adds a purpose; repetitive single-stone handling may still outweigh it |

### Mixed tube sizes

- Partial group transfers and unequal working spaces can make capacity allocation matter.
  Several separated free slots are not equivalent to the same number of contiguous slots.
- Reachable losing states can exist, including positions with legal moves but no solution.
  Keep unlimited Undo and distinguish those states from immediate no-move dead ends.
- Keep goal semantics explicit: the current rule permits one color across multiple full
  tubes, so a two-slot and a one-slot tube can both be final homes for a three-stone color.
  Treating short tubes as temporary spaces that must finish empty would be a different rule.
- Evaluate the choices created by small tubes, not capacity, solution length or trap count
  alone. Mixed capacities can also provide more recovery options and make a start easier.
- This is a good candidate for broad everyday variety; a separate difficulty ladder is
  not yet justified by human feedback.

### Connected boards

- Useful skills: choose which shore will collect each color, preserve local working room,
  schedule exchanges, and keep the shared tube available for competing demands.
- The connection restriction can turn a safe ordinary board into one with losing choices,
  including immediate no-move dead ends. Solvability must be checked under the actual
  connection graph; an ordinary sorting certificate is insufficient.
- Quality comes from competing uses of the shared tube, not merely requiring two moves
  where ordinary play needed one. Avoid repetitive shuttle work with an obvious answer.
- Keep both shores and the shared tube visible in portrait, with clear move restrictions
  and mode-aware hints. More tubes, bridges or smaller bridges need fresh readability and
  reachability checks; do not assume they improve difficulty.
- Promising for optional deeper puzzles, but no higher human skill ceiling than Classic
  has been established.

### Sealed tube: current behavior and creator's proposed revision

Current example: the sealed tube is EMPTY and opens on the first completed usable tube.
The opening is constrained; after unlocking, it becomes ordinary sorting with more room.
Unlocking alone only adds legal moves, so it cannot remove an existing solution.
The constrained phase can contain losing choices or be impossible from the outset.

Creator's suggestion: the sealed tube could CONTAIN STONES. Unlocking would then expose
another part of the sorting task rather than only granting a spare space. This simplifies
what is initially actionable, not necessarily the mathematical difficulty of the start.
It could connect the phases: choose the first color and prepare room with the later
contents in mind.

Suggested first prototype, not an approved final ruleset:

- One sealed tube whose stones remain visible; concealment stays a separate variation.
- No transfers into or out of it until a usable tube is full and monochrome.
- Keep it open after earning access, even if that completed color moves again.
- Its stones count toward the final sorting goal.
- Preserve the historical seal state through Undo, restart and save restoration.

Validate both phases together. At least one full monochrome usable tube must be reachable
without accessing the sealed contents. A circular dependency, where every possible first
completion requires a sealed stone, makes the opening impossible. Also check the space and
choices available after unlocking. Completing a color should not imply that the remainder
is automatically easy or safe. Multiple seals and unlock chains are optional later ideas.
The empty-seal measurements below do NOT assess this filled-seal proposal.

### Restore a pattern: why the proposed rules differ

The creator found the matching-color example too similar to ordinary sorting. That
criticism is valid: matching moves cannot create new adjacent color boundaries, so they
mainly uncover and consolidate existing layers rather than freely construct a pattern.
Bottom stones are not explicitly immovable; they can move once exposed.

Recommended replacement for a future prototype:
- Transfer ONE stone per move, onto ANY color when there is room.
- Match a persistent, numbered target exactly, including required empty tubes.
- Retain unlimited Undo, restart, color labels and mode-aware hints.
- Keep the target legible and aligned with playable tube columns. Watch for repetitive
  tapping, long sequences of flights, and excessive visual comparison work.

With those proposed rules every move has an ordinary legal reverse. Thus a solvable
position cannot become unsolvable through play; this does not depend on an Undo button.
An initially unreachable target is still possible with incompatible counts, capacities
or insufficient working space. In equal-capacity C-slot tubes with F total vacant slots,
each tube must retain at least C-F stones when F < C: those bottom stones cannot leave.
For example, three-slot tubes with only one vacant slot each retain their bottom two.
Reducing spare space can therefore freeze the interesting layers rather than deepen play.

Likely value: relaxed ordering, exposing stones, protecting correct bottom sequences,
and reducing repeated handling. Do not market a high challenge ceiling based on longer
solutions alone. A legal scramble of a target proves reachability under these reversible
rules, but does not prove quality, a shortest solution or a human difficulty rating.
The ordinary matching-color solver and tier labels cannot be reused unchanged.

### Exploratory evidence and its limits

These were in-memory JavaScript graph experiments during the discussion, not committed
engine tooling, app tests or physical-device measurements. No solver/generator source,
seed meanings or shipped boards were changed. Results should be reproduced in dedicated
tooling before using them to set generation thresholds.

For Mixed, empty Sealed and Connected, enumerate all states reachable from a start,
including the seal-open flag; stop expansion at solved boards. Reverse reachability from
all solved states identifies losing states exactly for each fully enumerated graph.
Tubes remain labelled. The fixed examples had no reachable losing states:
Mixed 56 states / 4-move shortest solution; Sealed 352 / 4; Connected 72 / 6.
Their simplicity does not establish the modes' general difficulty.

Larger survey: 80 shuffled starts per recipe, four colors with three stones each.
Shuffle the 12 stones into four full three-slot tubes; other tubes start empty.
Use maximal matching-group transfers and the existing full-monochrome-or-empty goal.

| Recipe | Capacities and restrictions | Solvable starts | Solvable starts with a reachable losing state |
| --- | --- | --- | --- |
| Mixed | 3 / 3 / 3 / 3 / 2 / 1 | 80/80 | 80/80 |
| Empty Sealed | Six three-slot tubes; final empty tube sealed | 74/80 | 67/74 |
| Connected | Six three-slot tubes; two left, shared, three right; left/right each start with two full tubes | 71/80 | 71/71 |

All sampled graphs completed within the 150,000-state cutoff (largest: 7,013 states).
For reproduction: one continuous unsigned 32-bit LCG stream, seed 38471, update
r = (1664525*r + 1013904223) mod 2^32, random = r / 2^32; Fisher-Yates from last
index downward. Run all 80 Mixed, then all 80 Sealed, then all 80 Connected shuffles.
Build the initial stone list color-major, colors 0..3 repeated three times. Connected
tube order is left 1, left 2, shared, right 1, right 2, right 3; full shuffled tubes
occupy indices 0, 1, 3, 4. These are sample counts, not player failure probabilities.

Concrete Connected comparison, bottom-to-top, colors 0..3:
[122] / [301] / [] shared / [033] / [021] / [].
With unrestricted connections: shortest solution 7, no reachable losing states.
With the shared-tube restriction: shortest 11, with 75 losing states among 1,030 reachable.
Every shortest connected solution visits at least three positions offering a losing move.
Length alone is not the evidence of deeper decisions; changed move consequences are.

For the proposed unrestricted single-stone Pattern rules, enumerate the undirected
reachable component from target [012] / [120] / [201] / [], capacities three.
It contains 33,600 arrangements. Among 1,680 arrangements with the first three tubes full
and the fourth empty, mean shortest distance is 12.13 and maximum 17. With a second spare,
the SAME starts average 11.36. Every tested full start has at least one shortest solution
that never reduces the number of stones in correct bottom prefixes. This supports a
foundation-building approach for this target, not a universal strategy or skill ceiling.
No human playtest or broad target survey was performed.

### Follow-up priorities

1. Review the actual examples with the creator; preserve responsive taps, saved histories,
   mode navigation and unrestricted Undo while iterating.
2. Investigate Mixed for everyday variety and Connected for distinct planning.
3. Prototype the creator's filled sealed tube and reassess both phases; do not apply the
   empty-spare verdict or measurements to it automatically.
4. Revisit Pattern's rules before investing in generation. Keep proposed single-stone
   behavior local to that mode; Classic and the other modes retain group transfers.
5. Later generation/assessment must use each mode's real rules and goal. Screen solvability,
   decision quality and repetition separately, and calibrate difficulty through human play.
   No generator work is authorized by this documentation update.

## Validation

- Debug assemble, 48 app JVM tests and lintDebug passed.
- Four isolated-preference InfiniteVariantUiTest emulator tests passed: all picker
  entries, playing every example through completion and replay, connected restrictions,
  undo, hinted-source confirmation and history restored on reentry.
- Unit tests solve every example using mode-aware hints, replay histories, check the seal
  and exact target goal, and ensure connected routes/touch bounds at two portrait sizes.
- Seven focused emulator checks passed together: InfiniteVariantUiTest (4),
  GroupMotionUiTest (1), WorldBrowserStateTest (2). After the final pattern placement
  adjustment, all four InfiniteVariantUiTest checks passed again.
- Emulator visual review covered all four modes and the picker at normal portrait size,
  including the final aligned pattern target/board layout. The latest debug APK was
  installed over the existing app; no player data was cleared.
- Physical phone review, large-font aesthetic review and difficulty/generation work remain.
