# Night worlds UI — portrait-first rebuild

The first integration drifted from the approved depth badges and cropped comparison
sheets into phone screens. Those sheets are references only now and are excluded
from the APK. Visual quality still needs the user's review; functional tests are
not approval of the art direction.

## World screen as one journey (2026-11-13, replaces the entrance badges)

The creator judged the entrance badges to be early placeholders: detailed pictures
floating in undetailed fog. The world screen is now a single scrolling scene
(`WorldVistas.kt`, `WorldVistaSection`), and everything below in this document that
describes entrances, mist, halos or fog drift is history.

- Four full-width stretches: Dusk, Moonlight, Midnight, Infinite. Each is 46% of the
  screen height (at least 300 dp), so a little over two are visible and the next one
  shows there is more below.
- A stretch is a native sky gradient with its own light, seeded stars and (Moonlight)
  a crescent, the world's name in the display serif with "N of M sorted", and the
  lower half of that world's existing wide painting (`*-wide-v2`) along the bottom.
  These are the paintings the level lists already use, so no new art was generated
  and a world looks like the same place on every screen.
- The painting's top edge is faded into the sky, and its ground dissolves into the
  next world's sky colour, so there is no seam between stretches.
- Infinite has no ground: a drawn infinity sign among denser stars.
- The whole stretch is the button (tag `world_<id>`). Resume is a small stone at the
  top left (tag `world_close`), where Back sits on other screens; the gear is top right.
- Nothing animates or redraws while idle. The old per-frame mist layer and erase masks
  are gone, which removes the one costly spot noted in the battery review.

No longer packaged (sources remain in app/artwork as history): the `*-depth-*`
entrances, `infinite-entry-v1` and `browser-mist-v1`. Nine assets are active.
Status: reviewed only on the emulator by the agent; the creator has not approved it.

## The other screens brought into the same style (2026-11-13)

Creator approved the world screen on the emulator ("looks really good") and asked for
the rest to follow. All of this is emulator-reviewed by the agent only.

- **Returning to the world screen** no longer flashes infinity signs: decoded art is
  read from the cache in the first frame (`WorldArtwork.ready`), and the sign is drawn
  only for the stretch that has no painting.
- **Play screen** (`PlayBackdrop` in WorldVistas.kt, replacing the blurred frame): the
  world's own sky, darkened, a few very faint stars, and its landscape low and dim
  under the controls. No blur layer.
- **Infinite has its own look** (`worldLook("endless")`: indigo with violet light),
  matching its stretch on the world screen, instead of borrowing Dusk's.
- **Board position** (`boardGeometry`): a row is only as tall as its tubes plus one
  stone of air; spare height is shared above and below. Small boards sit mid-screen
  and rows stay together. Pattern and connected layouts are unchanged.
- **Status line** is centred; **Next puzzle** is a centred stone, not a full-width bar.
- **Level list**: stones dissolve before reaching the floating Worlds and gear buttons;
  the fade grows with the first scroll so nothing pops.
- **Infinite options, saved puzzles, code entry and the sound panel** use the game's
  own controls from NightUi.kt (`NightMenuRow`, `NightChip`, `NightRadio`,
  `NightSlider`, `NightTextField`) instead of stock Material ones. Debug-flavoured
  wording was replaced ("Stone sound", "Saved puzzles", "New puzzle").

### Creator corrections to the above (2026-11-13, same day)

- **Board position reverted.** Tubes rest low again. The creator plays on large phones
  (S25+, A51s) and wants interaction in the lower half. Do not centre the board.
- **Level list fade:** stones start to thin beside the floating buttons but are only
  gone at the screen edge; they must not vanish before reaching the buttons.
- **No jump on completion:** the status line is a fixed 60 dp slot that holds either
  text or the Next puzzle stone. "Nicely sorted." was dropped.
- **Counts say "6 of 47"**, without "sorted": most modes are no longer plain sorting.
- **Level screen as a path (LevelPath.kt), prototype awaiting review.** A flat chat
  mock was rejected as "soulless, minimalistic": this screen must be relaxing and
  atmospheric like the paintings, not minimal. The prototype replaces the grid with
  stones along one winding trail (three per row, alternating direction) through the
  world's painting: finished stones glow, the trail between two finished stones is
  lit, the level being played has a halo, and static motes drift over each world
  (fireflies at dusk). It opens at the level being played. The mode mark is centred
  under the number; Sealed is a corked tube; Pattern is a column of three coloured
  stones; the challenge peak sits in its own plate on top of the rim. Screenshots of
  the prototype are in docs/mocks/. Nothing animates while idle.
  Creator's second pass: glow and lights are good, the trail was too crowded. Now only
  two small stones between neighbours, none touching, no ribbon, fewer motes, smaller
  level stones. All marks are the same monochrome line style (Pattern lost its colours);
  Hidden is a ringed question mark.
- **Press feel** (`Modifier.pressSink` in NightUi.kt): stone buttons, level stones and
  the world titles sink and spring back; a pressed stone darkens instead of whitening.
- **Header position** is the same on the world screen, level screen and play screen.

## Phone follow-up: scrolling, light and lettering

The browser list now fills the entire window. Its top/bottom clip and short mist
fades are at the screen edges, not below the settings row. Content padding keeps
the initial Resume position and final entrance comfortable; the settings gear
floats above scrolling content and retains its safe-area touch target.
Stronger world-colored radial halos follow measured entrance bounds.
The fog image has a small scroll-driven drift and three broad translucent banks
move at a different rate. There is no idle animation timer or background loop.
Reduced motion holds fog movement still.

Libre Baskerville is bundled unchanged as an Android font resource and is used
consistently for headings, stone labels and level numerals. Small utility text
remains sans serif. This is a closer chosen match to the generated mock, not a
claim to recover its unidentified font. Source, blob SHA and license are recorded
in app/typography/README.md; the full OFL is included in APK assets.
No runtime font download or new dependency is required.

Current outstanding work is tracked in [next-steps.md](next-steps.md), including
the phone reports of overhead-tube rebound clipping and interrupted settling.
Those physics behaviors were not changed in this UI follow-up.

## Current scope

Portrait appearance is the priority. Landscape polish and further landscape testing
are deferred at the user's request. Work already done on wide assets is retained,
but landscape is not considered visually finished or approved.

The approved badge reference is the BOTTOM ROW of
exec-50a899e0-c4e7-4e4a-a435-a10fa9128c67.png.
World selection uses transparent irregular entrances, an alpha-derived thin rim,
pressed lighting and a dedicated mist background with sparse low foliage and no pond.
Each entrance has its own alternating left/right row at 70% of available width,
with moderate spacing and no deliberate size bias. The browser scrolls without a
scrollbar. Runtime radial masks thin the mist and add local colored light using
each entrance's actual bounds, so the glow follows scrolling. Names are inside
each world and in accessible descriptions; there is no browser heading or Infinite caption.

Dusk uses the violet arch and winding garden path. Moonlight contains a painted sky,
lake, moon, reflection and receding hills inside the opening. Midnight now uses the approved v5 small house facade with roof overhang and
paired shutters matched to the window. Transparent margins are trimmed at load time. fittedArtwork uses
one scale for both dimensions and never fills an arbitrary button rectangle by stretching.

## Level selection and Midnight correction

Dusk and Moonlight use native sky gradients plus separate transparent portrait framing
art. Upper framing and lower scenery are width-scaled and anchored independently.
The central title and stone markers remain native. Comparison-sheet strips are not used.

Midnight's first separated frame had floating jambs that abruptly ended. Its portrait
selection now uses midnight-portrait-v3: a complete frame with continuous left stone
jamb, right shutter/jamb, top lintel and lower sill. portraitWindowSlices draws eight
connected perimeter sections, omits the transparent center, preserves corner proportions,
and adjusts middle jamb height to the available phone height. Mountains/pines/sill remain
in the bottom sections. This avoids cutting off either side halfway down the screen.
The quieter gameplay background still uses dim defocused peripheral scenery.

Short portrait screens use smaller marker spacing, less heading clearance and a reduced
bottom reserve. The current eleven Midnight markers remain readable. The grid stays
scrollable for longer worlds and larger text. Current/completed/available states retain
native semantics, outline/dot/check feedback. All levels remain available for playtesting.

## Gameplay and supporting screens

Gameplay uses world-specific plum, blue-green or indigo lighting and deliberately dim,
defocused edge scenery. The center stays quiet and the five piece colors stay stable.
Midnight has a faint window-light beam. Compose blur is supported on newer Android;
older versions retain the dim edge layer without that blur effect.

A compact serif level heading leaves more room for the board. Resume, Back and
undo/restart/hint share the same blank stone artwork with native text and symbols.
The minimal settings gear is consistent on all screens. Dusk gameplay has moderately
brighter plum and warmer peripheral lighting while keeping the puzzle center quiet.

First launch enters the introductory puzzle; subsequent launches restore the last
campaign or Infinite game. Gameplay Back opens the current world's levels, then
Back opens the world browser. Infinite is a separate browser entrance and its
gameplay Back opens the browser directly. Resume returns to the previously active
mode without changing its board or undo history. Infinite seed/bookmark/export
tools remain under its gameplay options button. Browsing retires in-flight visual
presentations while retaining committed moves. No navigation dependency was added.
Game rules, generator, save format, transfer routing, bounce and sound are unchanged.

## Assets and loading

Built-in image_gen was used with transparent_background=true. No CLI, API key or new
runtime library was added. Project originals are stored as wrapped Base64 for IDE-safe
import; DecodeWorldArtwork packages normal PNGs and only the thirteen active assets.
The mist background is opaque; the entrances and stone control have genuine alpha.
Older originals remain as history but are excluded from the APK.
Output: app/build/generated/world-artwork/worlds/.

- `app/artwork/dusk-depth-v2.png.base64` — source: `C:/Users/furtu/.codex/generated_images/01a1bc7a-116b-78f2-b39c-2bd6ce4ad4e2/exec-c8bbd593-cf87-430a-a548-1eb28737cccd.png`
- `app/artwork/moonlight-depth-v2.png.base64` — source: `C:/Users/furtu/.codex/generated_images/01a1bc7a-116b-78f2-b39c-2bd6ce4ad4e2/exec-8e48e426-4fa5-479a-b545-2338cd357c85.png`
- `app/artwork/midnight-depth-v5.png.base64` — approved geometry-corrected facade: `exec-b13a0920-91b6-4a57-8917-5fe0366db7c4.png`
- `app/artwork/infinite-entry-v1.png.base64` — silver stone arch: `exec-bbd984f6-346e-4eef-b885-f39a1d9e5d24.png`
- `app/artwork/stone-button-v1.png.base64` — blank reusable control: `exec-ea63ab0d-118d-4092-baa0-03fac8401552.png`
- `app/artwork/browser-mist-v1.png.base64` — no pond, no baked badge glows: `exec-f87d916c-92a0-4629-818d-a1f2d6c88c9e.png`
- `app/artwork/dusk-frame-v2.png.base64` — source: `C:/Users/furtu/.codex/generated_images/01a1bc7a-116b-78f2-b39c-2bd6ce4ad4e2/exec-410b635f-1bb4-46fa-9f83-7edd35327aff.png`
- `app/artwork/moonlight-frame-v2.png.base64` — source: `C:/Users/furtu/.codex/generated_images/01a1bc7a-116b-78f2-b39c-2bd6ce4ad4e2/exec-018e149e-541e-4e78-b2c2-3d222d398ea6.png`
- `app/artwork/midnight-frame-v2.png.base64` — source: `C:/Users/furtu/.codex/generated_images/01a1bc7a-116b-78f2-b39c-2bd6ce4ad4e2/exec-e16eb89b-8583-450f-aa7f-d36d0ff09df4.png`
- `app/artwork/midnight-portrait-v3.png.base64` — source: `C:/Users/furtu/.codex/generated_images/01a1bc7a-116b-78f2-b39c-2bd6ce4ad4e2/exec-b780696b-ad1c-4462-a412-fc1fece83e60.png`
- `app/artwork/dusk-wide-v2.png.base64` — source: `C:/Users/furtu/.codex/generated_images/01a1bc7a-116b-78f2-b39c-2bd6ce4ad4e2/exec-38be07b4-7a32-4694-889c-f4c80e113f0e.png`
- `app/artwork/moonlight-wide-v2.png.base64` — source: `C:/Users/furtu/.codex/generated_images/01a1bc7a-116b-78f2-b39c-2bd6ce4ad4e2/exec-ee0c9032-2171-43bd-8945-111e02adee7c.png`
- `app/artwork/midnight-wide-v2.png.base64` — source: `C:/Users/furtu/.codex/generated_images/01a1bc7a-116b-78f2-b39c-2bd6ce4ad4e2/exec-6efc06d6-1a34-47e1-a4f9-f6d46b895cb8.png`

Loading happens off the UI thread. Badges decode at half resolution. The existing
24 MiB LRU bounds cached decoded artwork and never recycles a bitmap held by a screen.

## Follow-up validation

- Debug build and all 30 app unit tests passed.
- Emulator visual checks covered the bundled lettering, glows and full-height
  scrolling, including content behind the settings row.
- All four focused browser/artwork tests passed at normal portrait resolution and
  again at 360×640 dp with 130% text. They cover viewport bounds, entrance callbacks,
  restoration, the packaged font/license and existing artwork requirements.
- The compact enlarged-text check caught a wrapping Restart label; stone labels now
  auto-fit on one line. Five-slot gameplay still becomes visually small on this
  extreme compact profile and needs a later board-layout review.
- Physical-phone review of these changes is still pending.

## Earlier validation

- 30 app unit tests passed, including perimeter continuity at 360×640, 393×852,
  411×914 and 480×1040, plus uniform badge scaling in different button shapes.
- The full 18-test emulator suite passed after the navigation and visual changes.
  This includes leaving mid-flight without losing the committed board or undo history.
- After adding soft scroll-edge fades, all three focused artwork/browser tests passed
  at 360×640 dp with 130% text. They cover the thirteen packaged assets, alpha centers,
  continuous side walls, decode/cache, scrolling, selected-world restoration and
  separate Infinite/Resume callbacks.
- Manual checks covered campaign Back to its levels, Back to the browser, Infinite
  entry and its seed/save/export options, Resume retaining Infinite and shared settings.
  Existing campaign and Infinite boards remained available; player data was not cleared.
- Actual portrait screenshots were reviewed at default 1080×2400; at 945×1680
  (360×640 dp at density 420); and at 1260×2730 (480×1040 dp), with 130% text on the
  larger profile. The last profile approximates the S25+ aspect ratio, not its hardware.
- The emulator's original resolution, font scale 1.0, portrait user rotation 0 and
  automatic rotation 1 were restored afterward. No app data was cleared.
- Physical S25+ appearance, brightness, touch feel and GPU frame pacing remain untested.

The artwork checks protect structural requirements; final aesthetics require visual
judgment. They must not be described as proof that the design looks good.

## New browser asset prompts

Midnight v5 reuses the approved geometry-corrected facade directly rather than generating
another variation. All new sources are in the same generated_images directory above.
The browser uses a short native alpha fade at scrolling edges, preventing a hard art cutoff.

### infinite-entry-v1

Use case: background-extraction. Extract ONLY the bottom INFINITE gateway from reference image. One isolated production Android game entrance on genuine TRANSPARENT background. Preserve simple pale silver-gray worn stone arch with small engraved infinity symbol, wide quiet opening into receding lavender/blue hills and winding misty path, sparse grass at base. Match original proportions and soft painterly style. No text, UI, captions, framing rectangle or large outer glow. Painted scene inside opening opaque, outside actual irregular silhouette true alpha. Center on square canvas, 5% clear margin. Intended170-280dp. Do not include other entrances.

### stone-button-v1

Use case: precise-object-edit / background-extraction. Reference is a PHONE UI study. Extract and recreate ONE EMPTY reusable dark violet stone button slab from the Resume button. Remove ALL lettering, arrow, symbols and engravings from its central face, replacing with natural quiet stone. Horizontal wide rounded slightly irregular slab, 3:1 width:height, subtle bevel and soft lavender top-edge lighting, diffuse very fine stone grain, gentle few worn edges. Empty broad flat-ish center for NATIVE text overlay. Center80% face free of cracks, decorations and moss; no random scratches. Genuine alpha outside slab, tight 3% margin, no baked shadow halo or background rectangle. No text, symbols, UI, examples, duplicated buttons. Can scale into Resume and smaller Back/Undo controls. Same mature painterly material, understated.

### browser-mist-v1

Use case: precise-object-edit / background extraction. Reference image is approved PHONE UI mood. Produce ONLY its SHARED BACKGROUND as a single portrait 9:20 phone wallpaper, 1080x2400 composition. Remove all four entrance badges completely, all local badge halos, all text, controls, headings, icons, roads and ponds. Repair with continuous layered MIST. Quiet dense soft charcoal slate-blue, desaturated blue-teal and faint muted plum variation; uneven broad low-contrast fog with depth, NOT storm clouds, not flat solid purple. Extremely subtle low grass/leaves only at very lowest 6-8% corners, fading into fog; no trees, trunks, canopy, rocks, paved roads, water/reflections, visible horizon or scenery in center. No ghost outlines where badges were. Keep dark calm nighttime palette. No bright white fog, big light spots, stars, particles, portals, architecture or lettering. The app supplies each badge's glow separately at runtime. Actual phone proportions, one complete background, not a comparison sheet.

## Earlier production generation/edit prompts

All badge edits use the approved depth reference. Portrait frame edits use the approved
scenery study; Moonlight/Midnight separated frames also use Dusk's layer as structure
reference. Wide assets were generated before landscape work was deferred.

### dusk-depth-v2

Use case: background-extraction / precise-object-edit. Edit target is provided approved reference sheet. Extract and faithfully reproduce ONLY the BOTTOM LEFT purple garden arch: preserve broad spreading violet canopy, asymmetric tree trunks, softly worn arch, large opening, winding pink stone path and receding slender trees. Keep exact inviting composition. Output ONE isolated production game entrance badge, on genuinely transparent background outside its irregular silhouette, centered on square canvas with 5% transparent clearance around entire object. Preserve the BOTTOM ROW (B depth) design, painterly shapes, soft texture, colors and lighting; do not redesign or make it more detailed. Interior sky and scene stay opaque painted; only outside silhouette and gaps outside framing are transparent. Fine restrained lavender/silver edge lighting following real silhouette. No labels, text, UI, rectangle, oval backing, unrelated badges, particles or neon. Designed to read at 170dp on a phone.

### moonlight-depth-v2

Use case: background-extraction / precise-object-edit. Edit target is provided approved reference sheet. Extract and faithfully reproduce ONLY the BOTTOM MIDDLE depth lake opening: preserve arch-like framing of willow and reeds, enclosed painted blue sky with crescent moon, receding mountain ridges and trees, broad still lake and silver reflection. Opening must be filled with landscape and sky, NOT transparent behind moon and NOT isolated plants framing empty alpha. Preserve depth and balanced broad opening. Output ONE isolated production game entrance badge, on genuinely transparent background outside its irregular silhouette, centered on square canvas with 5% transparent clearance around entire object. Preserve the BOTTOM ROW (B depth) design, painterly shapes, soft texture, colors and lighting; do not redesign or make it more detailed. Interior sky and scene stay opaque painted; only outside silhouette and gaps outside framing are transparent. Fine restrained lavender/silver edge lighting following real silhouette. No labels, text, UI, rectangle, oval backing, unrelated badges, particles or neon. Designed to read at 170dp on a phone.

### midnight-depth-v4

Use case: background-extraction. Edit target: approved comparison image. Isolate ONLY BOTTOM RIGHT badge, the B depth-through-window variation, faithfully. This is extraction and cleanup, NOT a redesign. Preserve its nearly square overall silhouette, compact diagonal slate roof, upright arched window opening, single tall open shutter at right and small ivy at bottom left. Preserve exact relative proportions from BOTTOM RIGHT original: opening about 45% of overall badge width, upright rather than squat; the roof must NOT dominate, and window must NOT be widened sideways into a panoramic window. Copy original scene: moon, a few sparse pines, three soft receding indigo mountain layers. Simplify fine interior detail only enough to read on a phone, retain depth and large clean sky space. Keep the original painterly shapes, violet stone and wood tones and restrained lighting. Output ONE isolated RGBA badge on square canvas, genuine alpha outside irregular silhouette, opaque painted interior view. 5% transparent clearance. No text, backing shape, additional objects, thick white outline, stretched architecture or new decoration. Faithful original bottom-right badge, with no horizontal or vertical distortion.

### dusk-frame-v2

Use case: stylized-concept. Asset type: ONE production portrait Android phone level-selection foreground frame, 9:20 aspect ratio (1080x2400 composition), NOT a comparison sheet. Reference image role: style and world identity ONLY; redesign framing for a normal phone width. LEFT Dusk garden: worn arch stones and soft violet tree canopy enter only from top corners; bottom scene has a short winding flagstone path, violet shrubs, low garden wall and receding narrow cypress trees. Warm plum and dusky pink against cool purple. Fine sparse branches can extend down outermost 7% edges.
Crucial layout: this is an RGBA foreground layer, TRANSPARENT SKY and EMPTY CENTRAL AREA. Top framing fits top 22% only, lower garden scenery and distant trees fits bottom 25% only. Entire horizontal band from y=30% to y=68% must be completely transparent across full width. A generous quiet opening across x=15%-85%,y=12%-75% will hold native title and level markers. Keep upper art and bottom scene separate; nothing crosses the middle band. Paint receding garden landscape and path only at bottom, fade distant silhouettes into transparency upward, no opaque sky anywhere. Transparent gaps between leaves. Soft mature hand-painted illustration matching reference, simplified broad shapes, attractive twilight colors. No UI, text, numbers, buttons, stars, sparkles, moon, border, black or colored rectangular backdrop. Canvas extends to edges; no sheet headings. This layer will be composited over a plum sky gradient and top/bottom anchored independently, so the fully transparent middle is essential.

### moonlight-frame-v2

Use case: stylized-concept. Asset type: ONE production portrait Android phone level-selection foreground frame, 9:20 aspect ratio (1080x2400 composition). Image 1 is style/world reference, choose only its MIDDLE Moonlight lake: drooping willow leaves enter from upper left, a sparse branch from upper right; bottom scene still blue-green lake, distant hills and tiny far shore pavilion, foreground reeds and riverstones in lower corners. Soft silver moonlight, no large moon in center. Image 2 is APPROVED LAYER STRUCTURE: transparent center, upper edge framing and lower scenery. Match its structure exactly but with the specified moonlight world.
RGBA foreground layer with TRANSPARENT SKY and EMPTY CENTRAL AREA. Top framing fits top 22% only. Lower scenery including distant landscape fits bottom 25% only. Entire horizontal band y=30%-68% must be fully transparent across width. Wide quiet opening x=15%-85%,y=12%-75% reserved for native title/markers. No continuous side columns crossing middle band. Keep upper framing and lower scene separate. Landscape and any sky below horizon painted only at bottom, distant silhouettes fade into alpha upward; no opaque sky elsewhere. Bottom artwork reaches bottom canvas edge and both lower corners. Transparent gaps between leaves. Soft mature restrained hand-painted illustration, simplified readable shapes, quiet nighttime colors, no UI/text/numbers/buttons/labels/stars/sparkles, no colored rectangle backdrop. The two edge sections will be anchored independently; transparent middle essential.

### midnight-frame-v2

Use case: stylized-concept. Asset type: ONE production portrait Android phone level-selection foreground frame, 9:20 aspect ratio (1080x2400 composition). Image 1 is style/world reference, choose only its RIGHT Midnight window: stone masonry in narrow side edges, timber lintel across top, open shutter only at uppermost right edge; bottom sill with a little ivy and beyond it receding indigo mountains and sparse pine silhouettes. Spacious view through window, no cramped ornaments. Image 2 is APPROVED LAYER STRUCTURE: transparent center, upper edge framing and lower scenery. Match its structure exactly but with the specified midnight world.
RGBA foreground layer with TRANSPARENT SKY and EMPTY CENTRAL AREA. Top framing fits top 22% only. Lower scenery including distant landscape fits bottom 25% only. Entire horizontal band y=30%-68% must be fully transparent across width. Wide quiet opening x=15%-85%,y=12%-75% reserved for native title/markers. No continuous side columns crossing middle band. Keep upper framing and lower scene separate. Landscape and any sky below horizon painted only at bottom, distant silhouettes fade into alpha upward; no opaque sky elsewhere. Bottom artwork reaches bottom canvas edge and both lower corners. Transparent gaps between leaves. Soft mature restrained hand-painted illustration, simplified readable shapes, quiet nighttime colors, no UI/text/numbers/buttons/labels/stars/sparkles, no colored rectangle backdrop. The two edge sections will be anchored independently; transparent middle essential.

### midnight-portrait-v3

Use case: stylized-concept. Reference is RIGHT panel only: the approved midnight window view. Create ONE production phone portrait foreground frame 9:20 (1080x2400 composition), not a comparison strip. View from inside a quiet old stone house through a TALL CONTINUOUS WINDOW into indigo mountains. Complete continuous frame: dark timber lintel at top, narrow stone jamb along ENTIRE LEFT EDGE from top to sill, narrow open wooden shutter/jamb along ENTIRE RIGHT EDGE from top to sill, stone sill across bottom. Side jambs approximately outer 7% width each, MUST remain continuous at every height, with no abrupt ends, no gaps, no horizontal cutoffs in middle, and must join the bottom sill. The structural frame continues beyond outside canvas edges like looking through a real window; never floating fragments. Window opening is broad quiet transparent negative space x=12%-88%, y=14%-75%, for native title and numbered level stones. Landscape only bottom 20%: a few sparse pines and three soft receding indigo mountain layers; no sky painted in middle, genuine alpha there. Top structure restrained, no bulky roof in upper third. Keep refined hand-painted style, cool dark blue stone/wood with subtle warm sill light. No UI, text, numbers, buttons, moon, sparkles or backing rectangle. RGBA foreground art: opaque structural edges all the way around, transparent open center. Important: Do NOT split this into a top and bottom fragment. It must be ONE continuous frame with both vertical sides running from top all the way to bottom.

### dusk-wide-v2

Use case: stylized-concept. Production Android foreground frame for LANDSCAPE PHONE, WIDE horizontal 20:9 canvas (2400x1080 composition). Exactly ONE wide composition, not a multi-panel sheet. Image1: approved reference for LEFT Dusk garden: worn arch stones and soft violet tree canopy enter only from top corners; bottom scene has a short winding flagstone path, violet shrubs, low garden wall and receding narrow cypress trees. Warm plum and dusky pink against cool purple. Fine sparse branches can extend down outermost 7% edges. Image2: production portrait layer, STYLE ONLY, adapt to wide phone. RGBA art with genuine transparent sky/middle. Compose for landscape: framing art only in OUTERMOST TOP CORNERS and top 15%; lower scenery occupies bottom 22% with distant silhouettes fading into alpha. Entire middle horizontal band y=25%-67% fully transparent across full width for native title/level markers. All scenery extends to screen edges, no portrait picture/rectangle in center. Top center mostly empty so native world title can be read there. Bottom scenery should span full width with a spacious low horizon. Preserve quiet mature hand-painted illustration, restrained nighttime lighting and clear broad silhouettes. No opaque sky or colored rectangular backing, no text, UI, buttons, numbers, labels, border, sparkle or invented objects. Output 20:9 WIDE horizontal foreground layer, never tall.

### moonlight-wide-v2

Use case: stylized-concept. Production Android foreground frame for LANDSCAPE PHONE, WIDE horizontal 20:9 canvas (2400x1080 composition). Exactly ONE wide composition, not a multi-panel sheet. Image1: approved reference for MIDDLE Moonlight lake: drooping willow leaves enter from upper left, a sparse branch from upper right; bottom scene still blue-green lake, distant hills and tiny far shore pavilion, foreground reeds and riverstones in lower corners. Soft silver moonlight, no large moon in center. Image2: production portrait layer, STYLE ONLY, adapt to wide phone. RGBA art with genuine transparent sky/middle. Compose for landscape: framing art only in OUTERMOST TOP CORNERS and top 15%; lower scenery occupies bottom 22% with distant silhouettes fading into alpha. Entire middle horizontal band y=25%-67% fully transparent across full width for native title/level markers. All scenery extends to screen edges, no portrait picture/rectangle in center. Top center mostly empty so native world title can be read there. Bottom scenery should span full width with a spacious low horizon. Preserve quiet mature hand-painted illustration, restrained nighttime lighting and clear broad silhouettes. No opaque sky or colored rectangular backing, no text, UI, buttons, numbers, labels, border, sparkle or invented objects. Output 20:9 WIDE horizontal foreground layer, never tall.

### midnight-wide-v2

Use case: stylized-concept. Production Android foreground frame for LANDSCAPE PHONE, WIDE horizontal 20:9 canvas (2400x1080 composition). Exactly ONE wide composition, not a multi-panel sheet. Image1: approved reference for RIGHT Midnight window: stone masonry in narrow side edges, timber lintel across top, open shutter only at uppermost right edge; bottom sill with a little ivy and beyond it receding indigo mountains and sparse pine silhouettes. Spacious view through window, no cramped ornaments. Image2: production portrait layer, STYLE ONLY, adapt to wide phone. RGBA art with genuine transparent sky/middle. Compose for landscape: framing art only in OUTERMOST TOP CORNERS and top 15%; lower scenery occupies bottom 22% with distant silhouettes fading into alpha. Entire middle horizontal band y=25%-67% fully transparent across full width for native title/level markers. All scenery extends to screen edges, no portrait picture/rectangle in center. Top center mostly empty so native world title can be read there. Bottom scenery should span full width with a spacious low horizon. Preserve quiet mature hand-painted illustration, restrained nighttime lighting and clear broad silhouettes. No opaque sky or colored rectangular backing, no text, UI, buttons, numbers, labels, border, sparkle or invented objects. Output 20:9 WIDE horizontal foreground layer, never tall.
