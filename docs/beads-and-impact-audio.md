# Water beads and impact audio review

Handoff from the 2026-11-19 session. Start with [the project handoff](agent-handoff.md)
for general constraints. This review covers the material switch, falling and settling,
bead rendering, and impact playback.

## Creator requirements and observations

- Keep stones as the familiar option. Water beads are an optional Settings switch, available
  immediately. They need their own rebound and should feel like stylized gel.
- Bounce should be proportional to the final fall. A brief drop after slowing to align
  should produce a small rebound. A bead or stone aligned far above the destination
  should retain the energy of that remaining fall. The creator preferred the stronger
  stone rebound as the default and asked to remove the extra bounce setting. Recheck
  Dusk 25, top-right to bottom-right with two purple pieces, including the overhead vial.
- Bead palette should retain the brightness and saturation of stones. The objection in
  Midnight was the white glare that looked like a direct lamp. Dimming the entire bead
  made the colors worse. Lighting should respond to position and world, with a subtler
  reflection in Midnight. Keep the silhouette clean at game size.
- A single squash looked acceptable. On Dusk 29 successive beads looked identical,
  and a bead above a compressed one appeared to hover. Three at once made this especially
  obvious. Check deformation, spacing, rebound and sound as one event. The bottom bead
  should bear more load when the group hits glass, with weaker deformation transmitted
  up the stack. The creator asked for research before changing the behavior and wants
  the reviewer to look for adjacent problems proactively.
- The original stone sound was well liked apart from rare radio-like crackle, sometimes
  perceived on successive rebounds. This was a distortion report rather than a request
  to raise the volume. A later revision made stones sound bass boosted and consistently
  distorted. The original clips and playback path were restored before the narrower
  clipping fix. Preserve articulation on closely spaced contacts and avoid a tap
  that arrives after the pieces settle. The creator also described the older bead
  sound as bass-heavy and cartoonishly springy.

## Current implementation

- `PlayOptions` persists the global material preference. `GameScreen` handles the
  switch and routes reduced-motion feedback. The sound comparison is for the stone
  styles. Ball colors, legal moves, level IDs and save semantics are independent of
  material.
- `TransferMotion`, `GroupRoute`, `LandingMotion` and `PuzzleBoard` use the final
  aligned drop to compute fall timing and contact speed. Visual settling is isolated
  from puzzle state. Stones retain rigid contacts. Beads step a damped unilateral
  contact model in `SettlingColumns`. Glass compression deforms the single contacting
  bead, and each bead pair shares its overlap between the two surfaces. The resulting
  asymmetric trims determine both the center and the rendered squash. The model
  retains slight compression under static load. Its constants are tuned for readable
  gameplay at phone size.
- `WaterBead` draws one antialiased gradient silhouette. The board and pattern target
  use the same renderer. Per-world reflection strength lives in `WorldArt`. The
  [Midnight glare comparison](mocks/midnight-bead-glare.png) shows the correction to
  the over-dimmed intermediate design. The [three-bead impact frames](mocks/three-bead-impact.png)
  use the actual solver and renderer. The creator said the latest result looked good.
- `ImpactSounds` gives beads a short damped tap, with a brighter glass component for
  floor and overhead hits. The previous pitch glide was removed. Bead-on-bead contacts
  play more softly. `ContactPlayer` mixes their overlaps and uses the relative contact
  times within each visual frame. Listen for chatter, an unnatural repeated pulse
  or audio that trails visible motion, especially with rapid successive transfers.
- `GameFeedback` still uses preloaded static tracks for animated stone sounds, with the
  existing pitch variation and per-kind cadence. Two loud River clips can exceed
  full scale if their peaks coincide. The regression reproduces that sum from the
  original PCM. The fix reserves headroom for the incoming tap using the remaining
  peaks of tracks already playing. The fix addresses clipping as a plausible source
  of the reported crackle. The original intermittent device symptom has not been
  reproduced under controlled phone conditions.

## Evidence and review gaps

- The app unit suite passed after the bead and audio timing changes. Targeted emulator
  checks passed for the bead renderer, both group-motion UI cases, and sound comparison
  and material switching. Unit coverage includes three-bead deformation order,
  touching surfaces, rapid arrivals onto a stack, contacts at rest, sample offsets
  within a mixer block, and overlapping stone peaks.
- In one earlier stone-only Dusk 29 emulator recording, the first tap appeared about
  30 ms after the visible floor contact and the first rebound tap appeared later.
  The capture did not isolate mixer, device output or recorder latency. Do not use it
  as a current timing number for either material.
- No comparative physical-phone trace or phone listening check was run. Code
  inspection showed the same 2 ms step size for both solvers and fewer per-substep
  loops for beads, with more arithmetic per visit. That does not establish a CPU
  ratio. Compare frame times and time to settle on the same device and move, with
  sound on and off. Include a long fall, Dusk 25 overhead contact, Dusk 29 rapid
  drops, three onto empty glass, three onto another bead, and consecutive arrivals.
- Keep `android.injected.androidTest.leaveApksInstalledAfterRun=true` and isolated
  test preferences. Preserve the playtest save. Confirm actual video and sound
  before making aesthetic or phone performance claims.

## Independent review (2026-11-19, after 8756022)

Rebound against the real fall was replayed in a JVM probe on the actual Dusk 25 and Dusk 29
layouts (392×560 dp board). Every route into Dusk 25's bottom-right tube aligns just under
the tube above it, so the fall is 3.2 slots whatever the route length. The purple pair
(3>6) rebounds 1.3–1.4 slots as stones and 0.2–0.6 slots as beads, well below the 3.3-slot
overhead clearance. Dusk 29's long falls from the top row are 13.3 slots and rebound 6.6
slots (stone) or 5.1 slots (bead) under a 14-slot clearance. The model matches the fall;
four defects in the surrounding code were fixed:

- **Hold before impact.** A landing woke the settling loop through recomposition, so it
  started a frame late: the stone was drawn just above the contact for an extra frame. With
  stones the restart also cancelled the running loop, losing a frame for every stone still
  bouncing. The landing now starts the loop itself, continuing from the landing's frame.
  `GroupMotionUiTest.stoneLandingIsHeardOnTheNextFrame` failed (contact two frames after
  landing) while the loop was still restarted through recomposition, and passes now.
- **Echo taps on a bead stack.** A bead landing on resting beads reported a second, quieter
  contact about 2 ms later, as the pulse passed through the pair that already touched.
  Contacts are now reported only when a separated pair meets (gap over 0.01 slot).
- **Identical bead taps.** Every bead contact used one clip per kind. Three pitch variants
  per kind are now taken in turn.
- **Bead group springing apart at pickup.** A departing group used the lowest stone's packed
  offset for all its stones, so the beads above it jumped up 0.14 slot each in the first
  frame of flight. Each stone now starts from its own drawn position.

Not changed, for a listening check: the stone headroom cap ducks a contact that starts
within about 4 ms of a loud one by up to about 6 dB (0.85 → 0.44 gain in River), while
the true sample-aligned sum would exceed full scale by at most about 1.2 dB. The cap is
deliberately conservative because the system mixer's start alignment is not known.

Emulator cost comparison (managed pixel6Api34, debug build, two rounds; emulator results, not
phone figures). One 60 Hz settling step while three groups of three land: median 0.16–0.24 ms
for stones and 0.09–0.15 ms for beads. Recording the whole window with a 32-piece board into a
software Picture: median 5.8–8.9 ms for stones and 3.8–4.2 ms for beads. Run-to-run noise is
large (p99 up to 2.4 ms and 64 ms), but nothing suggests beads cost more than stones. Real-frame
FrameMetrics reported no frames on the headless managed device, so GPU and frame-time cost on
a phone remain unmeasured.

Physics references used for the behavior are [soft gel-ball impacts on a rigid surface](https://eprints.lib.hokudai.ac.jp/repo/huscap/all/8431/PRE_73_031403.pdf)
and [impulsive loading of bead chains](https://experts.illinois.edu/en/publications/experimental-study-of-embedded-and-non-embedded-ordered-granular-/).
