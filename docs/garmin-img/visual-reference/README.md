# Visual reference — what the IMG fixtures _should_ look like

Side-by-side reference renders for each canonical fixture in
`dev/fixtures/maps/`, captured from desktop tools that
render Garmin IMG with full TYP styling fidelity:

- **QMapShack** (https://github.com/Maproom/qmapshack/releases) — free,
  cross-platform, the recommended primary reference.
- **Garmin BaseCamp** (https://www.garmin.com/en-US/software/basecamp/)
  — Garmin's own desktop tool; the most authoritative reference for "what
  a Garmin device would render".

These files are **documentation only** — they're not test-gated. They
exist so a developer iterating on TYP styling (Phase C.7) can keep our
renderer side-by-side with a known-good reference. The committed golden
PNG under `dev/garmin-img/golden/src/test/resources/golden/` is what regression
tests check against; this folder is the visual contract that tells you
whether the golden is _itself_ correct.

## Files

| File | Source | Fixture | Notes |
|---|---|---|---|
| `qmapshack-monaco.png` | QMapShack | `otm-monaco/otm-monaco.img` | Reference for the OpenTopoMap Monaco IMG. Lat ~43.55..43.85, Lon ~7.25..7.85. |

Map data © OpenStreetMap contributors (ODbL); map style © OpenTopoMap (CC-BY-SA 4.0).

## Adding a new reference

1. Open the source IMG in the desktop tool.
2. Pan / zoom to a viewport whose lat/lon range matches what our viewer or
   `MonacoRenderTest` renders by default — same area means a 1:1 visual
   compare without mental scaling.
3. Take a screenshot of the map area only (no toolbars or sidebars). Crop
   to the map.
4. Save as PNG under this directory. Use a name that makes the source
   obvious (`qmapshack-`, `basecamp-`, ...).
5. Add a row to the table above with bbox + any quirks worth noting.

## Why not test-gate these?

Desktop renders depend on:
- Tool version (QMapShack 1.16 vs 1.17 differs slightly).
- OS font availability (label glyphs).
- The user's display DPI and AA settings.

Even small differences would flag false-positive regressions in CI. The
real visual contract is the **committed golden** under
`dev/garmin-img/golden/src/test/resources/golden/`, which is what we control.
