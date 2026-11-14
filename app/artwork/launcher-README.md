# Stillstone launcher artwork

## Current source: exact approved B pixels (2026-11-12)

The creator chose B — Carved and explicitly requested the SAME image, including the
top-left and bottom-right stone planes. An attempted image_gen recreation changed those
details and was rejected. Do not regenerate this icon or use launcher-carved-v2 as a
replacement: the active icon is extracted directly from the original approved sheet.

Portable input: launcher-carved-approved-reference.png.base64, imported through Android
Studio's write tool. It is the original 1536x1024 comparison sheet encoded as PNG Base64.
The SHEET IS NOT PACKAGED in the APK. DecodeLauncherArtwork extracts only the two B icons
and writes the actual launcher resources offline. There is no runtime network dependency
or dependency on the creator's generated_images directory.

Reference provenance:
C:/Users/furtu/.codex/generated_images/01a1cca3-223b-7950-a20c-ebfe1bf64361/exec-afaed3f3-690e-411b-8b41-50021a614901.png

## Extraction and masks

DecodeLauncherArtwork uses two bounded regions of the unchanged source sheet:
- Squircle B: x=1136..1459, y=8..341.
- Circle B: x=800..1135, y=8..341.

Within each region, purple material bounds locate the icon. Crops are squared about
their centers. The mockup rim was already anti-aliased against gray; retaining that
blend produced pale specks on dark wallpaper. The extraction now rejects gray-matted
rim pixels and extends clean stone material from two source pixels inside the rim.
This affects only the outer fringe and material outside the original mask. Original
interior stone texture, groove, shadows, balls, colors and corner planes are unchanged.
The reference source itself remains untouched; no image regeneration is used.

Overscan is added OUTSIDE each visible crop, forming the Android 108dp layer around its
72dp visible area. This is crucial: cropping an oversized regenerated master previously
removed the approved corner planes. Normal density resampling is still necessary when
Android draws an app icon.

Generated adaptive masters:
- drawable-nodpi/launcher_stone.png: 512px squircle-source master.
- drawable-nodpi/launcher_stone_round.png: 512px circle-source master.

Generated legacy mipmaps use the visible crops directly. The manifest's icon is
ic_stillstone; roundIcon is ic_stillstone_round, allowing circular launchers to use the
approved circular artwork. Each adaptive background contains the complete composition.
The foreground is transparent and adds no badge, inset or border. The existing separate
native monochrome vial-and-stones vector remains for themed icons.

Android controls the final mask. The two original previews are not pixel-identical to
each other: they have their own approved crop/texture. We preserve both rather than
claiming one generated variation matches both. Samsung/themed-icon appearance still
requires phone review.

## History, not active build inputs

- launcher-stone-v1.png.base64: original irregular plaque; rejected for badge/padding and
  edge-clipping appearance even after reduced padding.
- launcher-carved-v2.png.base64: regenerated B interpretation; rejected for losing the
  original top-left/bottom-right stone detail. It must remain unused.
- No image generation occurred in the final extraction correction.

## Verification

Debug assemble, lintDebug and all three focused WorldArtworkTest emulator checks passed.
The launcher check covers both adaptive icon variants and their opaque 512px masters,
transparent foregrounds, composed icon and monochrome resources. Existing world/font
packaging checks also passed. Original corner planes were visually checked on the
installed circular Pixel launcher icon at actual size. The suggested dock ring is
launcher-owned; the normal app icon contains no extra ring.

App name ("Ball sort game"), application ID, signing and save paths are unchanged.
No app data was cleared or app uninstalled.

Android adaptive-icon reference:
https://developer.android.com/develop/ui/compose/system/icon_design_adaptive
