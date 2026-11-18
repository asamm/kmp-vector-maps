# Roadmap archive: M11

The step table of M11, the MapLibre parity sweep, moved unchanged from [ROADMAP.md](../ROADMAP.md)
on 2026-11-18 (at `be74335`); frozen. The PRs named here hold the rest; benchmarks are in
[performance.md](../performance.md), the sweep and the differences left in [mvt.md](../mvt.md).

## M11 — MapLibre parity sweep

The eight MVT goldens miss what only shows on other places and zooms; the sweep scores a large
sample of a Central Europe PMTiles extract (Asamm schema, z0–14) against MapLibre Native, the
renderer of Locus Map iOS, and fixes the worst causes first.

| Step | Content | Verify |
|---|---|---|
| M11.1 | The sweep (`dev/mvt/sweep`, `dev/mvt/reference/sweep*.mjs`); fixes it found: the default fill outline (a hairline, performance.md M11.1), label priority by feature order, balanced line breaking, MapLibre's line anchors on lines clipped to their tile (at the z18 text size, times the overscale; `line-center` unclipped), MapLibre's text baseline; the legacy `{token}` regex Android's ICU rejected (no MVT style parsed on Android since PR #65) | 83k tiles z1–18 in three views, mean pixels off 0.70 → 0.29 % (tiles), 2.97 → 2.16 % (labels), 3.63 → 2.99 % (turned); the review's anchor fixes on the 13.9k subset 2.14 → 2.02 % and 2.97 → 2.86 %; goldens regenerated and compared; JVM and A54 A/B in performance.md ✅ PR #66 |
| M11.2 | The fill outline antialiased, one device pixel wide, as MapLibre's lines (the hairline's overrides removed) | the 13.9k subset 0.29 → 0.15 % (tiles), 2.02 → 1.89 % (labels), 2.86 → 2.76 % (turned); label goldens regenerated, 7 of 8 closer to MapLibre; JVM +13–52 % per tile, A54 level (performance.md M11.2) ✅ PR #67 |
| M11.3 | Labels where the sweep still differs most, found by comparing the placed labels of both renderers (`sweep labels`, `sweep.mjs labels`): an `allow-overlap` symbol still blocks later labels (`LabelDisplay.OVERLAP`; `*-ignore-placement` supported), so dense centres no longer fill with POIs and route names; line anchors laid out with the text at the tile's zoom size as MapLibre Native, not GL JS's z18 | the 13.9k subset labels 1.888 → 1.776 %, turned 2.760 → 2.646 %, label tiles over 10 % 30 → 5; label goldens regenerated (they compare with GL JS) ✅ PR #68 |
| M11.4 | Road shields: on 120 subset tiles z10–16 we placed 134 shields to MapLibre's 108, 40 % of MapLibre's within 3 px of ours (`sweep labels` against `sweep.mjs labels`, whose anchors equal Native's for the shields' constant text size). Fixed: a line label's icon counts at its sprite size times the text size over 24 px, as MapLibre's `getAnchors`; a `line` text's anchor within half a spacing of an earlier one with the same text is dropped (`anchorIsTooClose`), one shield on a dual carriageway | shields 106 to MapLibre's 108, 88 % within 3 px; the 13.9k subset labels 1.776 → 1.626 %, turned 2.646 → 2.497 %; label goldens regenerated, 3 of 8 closer to GL JS, none further ✅ PR #69 |
