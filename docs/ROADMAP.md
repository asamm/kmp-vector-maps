# Roadmap

Multi-format vector map rendering for Locus Map: a shared driver interface, pipeline and viewer,
with Mapsforge as the first format and Garmin IMG joining from `kmp-garmin-img`.

**Author:** Menion · **Started:** 2026-10-22 · **Decision record:** locus-core
[`mapsforge_render_extraction.md`](https://github.com/asamm/locus-core/blob/development/docs/plans/mapsforge_render_extraction.md)
(findings F1–F12, hazards R1–R7) · **Target design:** [architecture.md](architecture.md)

Status 2026-10-22: M0 and M1 done — Mapsforge 0.30.0 subset vendored unchanged under
`formats/mapsforge/legacy/`, golden corpus green against upstream, desktop and Android viewers
running, repo renamed to `kmp-vector-maps`. Nothing committed yet.

**Next action:** first commit and push, so CI runs; then M2.

Legend: done · open · deferred.

## Decisions

| ID | Decision | Date |
|---|---|---|
| D1 | ~~Own repository `kmp-mapsforge`~~ → one repository for all vector formats (D10) | 2026-10-22 |
| D2 | Mapsforge Java stage mirrors the upstream module split | 2026-10-22 |
| D3 | Packages stay `org.mapsforge.*` until the Kotlin conversion, then `com.asamm.*` | 2026-10-22 |
| D4 | Vendor from the local Mapsforge clone at commit `54e8654f` (tag 0.30.0), read-only | 2026-10-22 |
| D5 | Test theme is LoMaps 1.8.0 from [asamm/lomaps-mapsforge](https://github.com/asamm/lomaps-mapsforge) (Apache-2.0) | 2026-10-22 |
| D6 | Locus consumes GitHub Packages builds, workflows as in `kmp-garmin-img` | 2026-10-22 |
| D7 | Maven group `com.asamm.kmp`, artifact per module (D15) | 2026-10-22 |
| D8 | Mapsforge code LGPL-3.0-or-later with the §4(d)/(e) waiver, public repo; see [licence.md](licence.md) | 2026-10-22 |
| D9 | Committed corpus map LoMaps `monaco.osm.map`; Czech map local-only for perf | 2026-10-22 |
| D10 | One repository: per-format modules, shared core, viewer, golden and bench tooling | 2026-10-22 |
| D11 | GDAL-like driver interface every format implements, see [architecture.md](architecture.md) | 2026-10-22 |
| D12 | Locus integration is one of the **last** steps: fully Kotlin, KMP-ready, optimised | 2026-10-22 |
| D13 | Hillshading stays in Locus; the pipeline gives it a hook after areas, before lines | 2026-10-22 |
| D14 | Labels can render as a separate screen-space pass, upright under map rotation, for every format | 2026-10-22 |
| D15 | Repository `kmp-vector-maps`; artifacts `com.asamm.kmp:kmp-vector-maps-<module>` | 2026-10-22 |
| D16 | Mapsforge hillshading hook is a marker the theme places; without it, after all area fills | 2026-10-22 |
| D17 | This ROADMAP is the source of truth; the locus-core plan doc stays the original record | 2026-10-22 |
| D18 | The shared viewer starts from the `kmp-garmin-img` viewer; the current Mapsforge viewers are throwaway | 2026-10-22 |

- D9: both maps come from the A54, `/sdcard/Android/media/menion.android.locus/mapsVector/europe/`.
- D12 replaces the locus-core plan's early switch (its P1 "Locus switches to the module").
- D13: Mapsforge's own hillshading (15 vendored classes) goes when the Mapsforge driver is built,
  no longer constrained by Locus' current constructor calls.

## Milestones

| ID | Milestone | Size | Status |
|---|---|---|---|
| M0 | Foundation: skeleton, vendored Mapsforge, golden corpus, first viewers | S | done |
| M1 | Restructure to the multi-format layout, rename, CI | S | done, CI unverified |
| M2 | Core interface v0, Mapsforge driver over the vendored Java, shared Compose MP viewer | M | open |
| M3 | Garmin IMG joins: history import, driver, golden set | M | open |
| M4 | Mapsforge hot spots: bounded paint caches, globals, locking | M | open |
| M5 | Mapsforge Kotlin conversion, area by area | L | open |
| M6 | Mapsforge in KMP `commonMain` on the core `RenderTarget` | L | open |
| M7 | Locus pipeline features: hillshading hook, separate label pass with rotation, feature query | M | open |
| M8 | Locus integration: publish, replace Mapsforge and `kmp-garmin-img` dependencies | S | open |
| M9 | Further formats (Mapbox Vector Tiles first), iOS | L | deferred |

**M0 — Foundation.** Done 2026-10-22.
- 177 vendored files (25.5k lines): the `jdeps` closure of every `org.mapsforge` type locus-core
  imports, plus `AwtGraphicFactory`; byte-identical to 0.30.0.
- `./gradlew :formats:mapsforge:golden:test`: 72 renders (18 tiles × day, night × text scale 1.0, 1.5),
  vendored vs upstream 0.30.0 from JitPack, pixel-exact, same machine. Fails on an anti-alias
  mutation.
- Viewers: desktop (Compose, AWT) and Android (A54: Monaco z12, 704 px tiles, theme parse 437 ms,
  tile median 73 ms).

**M1 — Restructure.**
- Done 2026-10-22: GitHub repo renamed to `asamm/kmp-vector-maps`, local copy in
  the `kmp-vector-maps` folder next to the old one, vendored Mapsforge modules under
  `formats/mapsforge/legacy/`, deleted as a whole after M5.
- CI (`.github/workflows/ci.yml`) builds and tests on push and PR; publishing workflows wait for M8.

**M2 — Interface v0.**
- `:core` in KMP `commonMain`: driver, registry, dataset, renderer, tile request, pipeline stages.
- Mapsforge driver adapts the vendored Java renderer; golden corpus runs through the driver.
- One Compose Multiplatform viewer for desktop and Android replaces both current viewers, built
  from the fine-tuned `kmp-garmin-img` viewer: open any registered format, style picker, render
  stats, label mode, rotation (D18).

**M3 — Garmin IMG.** Import `kmp-garmin-img` with history (`git subtree`), wrap its renderer as a
driver, move its golden PNG and dump-diff tests into the shared golden tooling.

**M4 — Hot spots** (locus-core P2). Bound the per-zoom Paint caches, find the 1.4–2.5 KB native cost
per Paint, replace `DisplayModel` / `Parameters` statics, document locks. Measured in the viewer and
on the A54.

**M5 — Kotlin** (locus-core P3). Reader, model, graphics, renderer, labels, theme, XML parser last;
one PR per area, each gated by the golden corpus.

**M6 — KMP** (locus-core P4). Expect/actual for XML pull parsing, SVG rasterising, random-access
reads, fonts, locks.

**M7 — Locus features.** Hillshading hook (D13), separate label pass with rotation (D14) moved from
locus-core's `MapsForgeV4LabelRenderer` into `:core`, `FeatureQuery` for LoPoints.

**M8 — Integration** (locus-core P5). Publish, swap Locus to the new artifacts, delete its Mapsforge
renderer classes, run the upstream sync routine of [upstream.md](upstream.md) per release.

## Hazards

See the locus-core plan, R1–R7. Repo-specific additions:

| ID | Hazard | Mitigation |
|---|---|---|
| H1 | Upstream and vendored `org.mapsforge` classes on one classpath | golden generation in its own module |
| H2 | Vendored Java drifts from upstream unnoticed | deviation table in [upstream.md](upstream.md) |
| H3 | Interface shaped by Mapsforge alone, Garmin and MVT fit badly | Garmin driver (M3) right after interface v0 (M2) |
| H4 | Long gap before Locus sees any of it (D12) | A54 runs in the shared viewer each milestone |

## Open

| ID | Question |
|---|---|
| Q5 | SVG strategy outside Android (locus-core Q3); svgSalamander on AWT for now |
