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
tiles under `dev/garmin-img/golden/src/test/resources/golden/` are what
`DriverGoldenTest` checks against; this folder is the visual contract that tells you
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

## Scripted reference (not built)

A tile-by-tile reference renderer for Garmin sweeps, as MapLibre is for MVT; proposed in the
2026-11-18 review, parked until better Garmin rendering is in demand (ROADMAP Q21).

| Item | Plan |
|---|---|
| Renderer | [GPXSee](https://github.com/tumic0/GPXSee) (GPL-3.0, Qt): its IMG maps read `gmapsupp.img` and TYP and draw to a `QPainter`; Qt only, as recalled, not verified |
| Rejected | QMapShack: its Garmin code needs GDAL, PROJ and its draw context, about 2–4 days to run headless; BaseCamp: GUI only, no export of given bounds, opens only installed or device maps |
| Build | WSL Ubuntu 24.04, Qt 6 from `apt`; GPXSee cloned at a pinned tag into `local/` |
| Tool | ~150 lines of C++: open the IMG, set Web Mercator bounds per `z/x/y`, draw 512 px, write PNG; reads the `tiles.txt` of `GarminSweepTest` |
| Compare | per-tile difference scores to rank outliers (missing objects, level of detail, draw order, TYP colours), not a pixel golden: fonts, antialiasing and in-tile labels differ |
| Licence | GPL: the tool stays in `local/`, or in `dev/garmin-img/reference/` under its own GPL notice with GPXSee fetched at build time, which needs a [licence.md](../../licence.md) entry first; nothing ships |
| Effort | about 1 day |

It is a second reading of the format, not Garmin's rendering.

## Why not test-gate these?

Desktop renders depend on:
- Tool version (QMapShack 1.16 vs 1.17 differs slightly).
- OS font availability (label glyphs).
- The user's display DPI and AA settings.

Even small differences would flag false-positive regressions in CI. The
real visual contract is the **committed golden tiles** under
`dev/garmin-img/golden/src/test/resources/golden/`, which is what we control.
