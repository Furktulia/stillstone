# Stone interaction playtest

This pass uses deterministic animation rather than a physics simulation. Puzzle state
still commits one exact legal group move; rendering, sound and haptics only present it.
Routes now consider every tube as an obstacle expanded by the stone radius plus
clearance. A visibility grid finds an open corridor between the source and destination
rims, including sideways detours between rows. Corners are rounded within that clearance.
The layout reserves room for these corridors. There is no straight-line fallback when
an extremely small window cannot provide a route.

All pieces in a transferred group start together and share one travel clock, with
an eased approach followed by an accelerating final fall (no ease-out at contact).
Travel remains roughly 0.8–1.2 seconds. Rebound begins immediately at contact with
nonzero upward velocity, using a ballistic curve rather than an eased wiggle.
Height is based on the full continuous descent since the last climb, including a drop
down an aisle followed by a horizontal turn into a tube. Horizontal travel does not
reset the accumulated drop. Rebound is about 32% of that descent, capped at three slots
and damped for heavier groups. Two ballistic hops settle over roughly 200–1200 ms,
depending on rebound height. The destination rim is not a ceiling: a group may rebound
above it. Clearance constraints attempt to limit rebound near screen edges and
overhead tubes, but phone testing found cases that still pass through a tube above.
Another incoming group can also cancel ongoing settling in the same tube.
Both are queued for the next physics pass (see next-steps.md); these constraints
must not be treated as a complete collision simulation.

A moving group now follows one shared route. Following pieces maintain real center
separation around bends, rather than advancing independently along separate paths.
Members of the same group remain separated; different moving groups may still cross
each other. This is presentation geometry, not a full collision simulation.
Contact, sound, haptics and dependency release share one boundary.

Tube taps remain active during travel. Legal moves commit and save immediately against
the latest logical board. Independent groups animate concurrently. A move sharing a
tube with an earlier move starts when that earlier group lands, before its settling
animation finishes. Selecting the next tube highlights it immediately. Dependent moves
can be entered before incoming pieces arrive. Playback no longer accelerates with the
queue; the slower material feel is preserved. Stationary pieces are reconstructed from
the accepted move sequence and each group's phase, preventing duplicate or lost pieces
when independent animations finish out of order.
Undo, restart, changing a puzzle or changing motion settings clears pending presentation;
stale completion callbacks cannot change subsequent state. Restoring saved progress
shows the committed board directly rather than replaying an interrupted animation.

Path tests sample the full stone radius against all intervening tubes on portrait,
landscape, short-window and mixed-capacity layouts. A frozen-animation touch test checks
three rapid moves, immediate selection, committed move order, Undo cancellation and
a stale completion callback.

The board has quieter outlines, a very faint interior tint, no empty-slot dots, and
a muted completion outline. All stones share a rounded silhouette and mild diffuse
shading. Color labels and instant movement remain available.

## Sound implementation

Open Settings → Compare sounds to audition Pebble (low/dry), Bone / wood (warm/hollow),
and Ceramic (clear/ringing). Pebble is the preferred direction after user feedback;
it now has a small downward pitch transient, more low body and less upper ringing.
The other directions remain comparison references. Select Single or 3 balls and
Shallow or Deep, then Play.
Keep the same preview volume and drop settings for A/B comparisons. Single-hit energy
is matched across materials, but perceived loudness still needs human judgement.
Three-ball previews add two quieter contacts, 34 ms apart. Pebble has a slightly lower
fundamental and stronger short texture; group contacts are more pronounced. Deep previews have a stronger
impact; these controls are sound studies, not changes to gameplay bounce or collision
physics. Preview does not save a choice until Use is pressed. The chosen material is
saved globally. Gameplay now passes actual group size and drop distance to feedback:
single, two-, three- and four-or-more-ball clips are preloaded with two voices each.
Larger groups use the four-contact sound rather than an arbitrarily long rattle.
Drop distance controls playback strength continuously. No samples are synthesized or
loaded at the instant of impact. Reduced motion uses group size too, with an estimated
drop from destination capacity and occupancy.

Original PCM clips are synthesized locally during worker initialization with slight
variant changes for repeated gameplay. These are synthetic material studies, not
recordings or downloaded assets. Preview plays even with game sounds disabled, but
respects silent/vibrate mode and media volume. Stop, Back, changing the preview controls,
and app pause stop the preview. Audition on the phone with both speaker and headphones. Android AudioTrack static buffers retain
the clips for reuse, avoiding live synthesis on each move. No dependencies, network,
audio service or new permissions were added. Reference:
https://developer.android.com/reference/android/media/AudioTrack

Sounds follow media volume and additionally stay silent in silent/vibrate ringer
modes. They stop on pause and resources release on leaving the screen. They do not
request audio focus or interrupt another music app. Group impacts alternate variations.
Reduced motion gives one immediate tap per
legal transfer. Haptics use the system view feedback setting, are off by default,
and fire once per group. Sound/haptic preferences persist globally, independently
of puzzle saves.

## Phone review

Install/update the APK without uninstalling to keep saves. Start around 30–40% media
volume in normal ringer mode, then compare speakers and headphones.

- Play several single-piece and large-group transfers; judge pace, weight, overlap,
  and whether the landing sound feels too tonal, sharp, dull or repetitive.
- Try transfers between rows, not just adjacent tubes.
- Enable Gentle haptics in Settings; judge whether it is subtle enough.
- Toggle sound, reduced motion and color labels; confirm each remains comfortable.
- Undo during travel, restart, switch modes, background and resume. Listen for
  lingering sounds or feedback from an abandoned move.
- Try landscape and large font settings. Report clipped controls or small targets.

Real speaker balance, perceived latency, haptic strength, sustained frame pacing and
battery behavior require device testing. This is a first material/motion study, not
a finalized sound library or physically simulated collision system.
