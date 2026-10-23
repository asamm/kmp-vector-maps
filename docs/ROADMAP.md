# Roadmap

Vector map rendering for Locus Map on Android — Mapsforge first, Garmin IMG next — behind one
driver interface, with desktop JVM for benchmarks and tools.

**Author:** Menion · **Started:** 2026-10-22 · **Trigger:** locus-core plan
[`mapsforge_render_extraction.md`](https://github.com/asamm/locus-core/blob/development/docs/plans/mapsforge_render_extraction.md)
(findings F1–F12, hazards R1–R7) · **Design:** [architecture.md](architecture.md)

**Status: M0–M1 done, M2 in progress.** Next action: M2.7, benchmark baseline.

Legend: ✅ done · 🔧 in review · ⬜ open · ⏸ deferred

## Milestones

Order follows dependencies: safety net before any change, a second format before the interface
settles, labels before speed, the Locus-facing API last.

| ID | Milestone | Size | Status |
|---|---|---|---|
| M0 | Foundation: vendored Mapsforge 0.30.0, golden test, CI | S | ✅ `d41acf5` |
| M1 | Review hardening: CI, supply chain, licence docs, this plan | S | ✅ PR #1, `42f1598` |
| M2 | Safety net: golden coverage of Locus modes, benchmark baseline | M | 🔧 in progress |
| M3 | Core v0, Mapsforge driver over the Java, shared viewer | M | ⬜ |
| M4 | Garmin IMG joins | M | ⬜ needs Q12, Q13 |
| M5 | Mapsforge to Kotlin, hot-spot fixes per area, `legacy/` deleted | L | ⬜ |
| M6 | Core features: label model, hillshade anchor, feature query | M | ⬜ |
| M7 | Performance and Skia desktop backend | L | ⬜ needs Q15 |
| M8 | Android host API, proven on a throwaway Locus branch | M | ⬜ |
| M9 | Locus integration | M | ⬜ needs Q14, Q16 |
| M10 | Mapbox Vector Tiles | L | ⏸ |

Renumbered 2026-10-22 against `d41acf5`: old M0 and M1 → M0; M2 → M3; M3 → M4; M4 → folded into
M5; M5 → M5; M6 (KMP) → dropped (D22); M7 → M6; M8 → M9; M9 → M10. M1, M2, M7, M8 are new.

**M0 — Foundation. DONE 2026-10-22** (`d41acf5`)
- 177 vendored files, the `jdeps` closure of the Mapsforge types locus-core imports; identical
  to upstream 0.30.0 ([upstream.md](upstream.md)).
- Golden test: 72 renders, vendored vs upstream from JitPack, pixel-exact on one host; 9 of 9
  mutations caught.

*Verified: `./gradlew build` and CI run 39843874517 green.*

**M1 — Review hardening. DONE 2026-10-23** (PR #1, merged as `42f1598`)
- Upstream sync script that fails on drift; build deviations B1–B5 recorded.
- CI pinned to `ubuntu-24.04` and action SHAs; JitPack exclusive and checksum-verified.
- `LICENSE.md` with licence per part and the Mapsforge waiver, fixture attribution; this plan.

*Verified: `./gradlew build` and the PR CI run green; 6 review threads resolved in `5b47f55`.*

**M2 — Safety net.** One PR (`feature/m2-golden-coverage`), one commit per row, each closing a gap
the second deep review found.

| Step | Content | Status |
|---|---|---|
| M2.1 | Corpus at 512 px, scale 1 / 2.625 / 3, z18–20 on land, menu `hike_bike` and `car` | 🔧 |
| M2.2 | Locus primary mode: no labels in tile + `TileBasedLabelStore`, label dump | 🔧 |
| M2.3 | Parallel 3×3 block: same pixels and labels as a serial render | 🔧 |
| M2.4 | Android pixels via Robolectric native graphics, vendored vs upstream | ⏸ after M2.7 |
| M2.5 | Text dumps: reader `MapReadResult`, `RenderCallback` output | 🔧 |
| M2.6 | Paint-count sweep z8–20, proxy for the memory growth F2 | 🔧 |
| M2.7 | `bench/`: JMH per stage + A54 run at N threads, baseline recorded here; adds the Apache-2.0 text | ⬜ |

- M2.1: 18 tiles × 4 sets covering theme, menu layer, device scale and text scale. Not caught:
  the per-instruction menu filter — LoMaps sets a category on only 4 instructions (swamp, two
  lines), none on the Monaco tiles.
- M2.2: a fifth set in Locus' primary mode (no labels in tile, `TileBasedLabelStore(100)`) and its
  label dump in store order: 319 labels over 18 tiles — 219 symbols, 65 captions, 35 way texts.
- M2.3: a 3×3 block at z16 rendered on 4 threads through one long-lived renderer, 5 rounds,
  against a serial render. Neither a dropped paint lock nor dropped matching-cache locks were
  caught: the test guards render-order independence and gross races, not every lock.
- Determinism (M2.1, corrected in M2.3): upstream breaks label ties in `HashSet` order, which
  follows identity hashes (A7). Both renders run in their own JVM from one entry point that aligns
  a sequential identity-hash counter (`-XX:hashCode=3`), with a serial collector on a fixed heap.
  The first fix, a constant hash (`-XX:hashCode=2`), made `Closed.YES` and `Closed.NO` collide in
  the theme's matching cache and rendered some areas as lines; M2.3 caught it.
- M2.4: deferred after M2.7 — Robolectric runs inside Gradle's test JVM and needs its own
  determinism setup.
- M2.5: `reader.txt` and `theme_callbacks.txt` over the corpus tiles plus z3 and z5, the theme
  driven through Mapsforge's own `StandardRenderer` with full paint state. Not covered by the
  Monaco map: explicit way label positions, water tiles, `readLabels` / `readPoiData`.
- M2.6: `paint_counts.txt` from one long-lived renderer (`hike_bike`) sweeping z8–20 twice, Paints
  created and still alive after a full GC: 3,359 alive after the theme parse, ~3,100 more per new
  zoom, 43,494 after z20, unchanged in the second sweep — the never-evicted caches of F2/F3. M5.3
  bounds `live`. Caught only here, no pixel changed: stroke scaling not cached per zoom (~2,100
  Paints per zoom in the second sweep); per-zoom eviction changes `live`.
- Mutations caught (90 renders, 5 sets): menu rule filter 90 of 90, device scale ignored 54,
  caption offset flipped 45 plus the dump, labels not stored (dump and M2.3), way coordinate +1
  µ° (90 plus `reader.txt`); line stroke +0.1 % (90), caption text size ×1.05 (50) and line dash
  ×1.05 (33), each also in `theme_callbacks.txt`.

*Verify: each step's new test fails on a targeted mutation; CI green.*

**M3 — Core v0 and shared viewer.**

| PR | Content |
|---|---|
| M3.1 | `core/` (Kotlin/JVM, Apache-2.0): driver, registry, dataset, style, renderer, render key |
| M3.2 | Mapsforge driver over the vendored Java; golden test runs through it |
| M3.3 | kmp-garmin-img viewer moved here, registry-driven tile engine; throwaway viewers deleted |

*Verify: golden test green through the driver; viewer renders Monaco and Czech on desktop and A54.*

**M4 — Garmin IMG.** Blocked by Q12 (visibility) and Q13 (licence wording).

| PR | Content |
|---|---|
| M4.1 | Import with history; KMP → Kotlin/JVM; drop unused `kotlinx-io`, coroutines, `api` logger |
| M4.2 | Garmin driver; its goldens and dump-diff into the shared tooling |
| M4.3 | Positional reads → parallel renders; multi-map IMGs (today first map only) |
| M4.4 | Measured label bounds, TYP night colours, text scale; missing licence headers |

**M5 — Mapsforge to Kotlin.** One area per PR, its hot-spot fixes in a follow-up PR on the same
area, so each conversion PR stays pixel-identical. Every PR gated by goldens, dumps and an A54
15-minute zoom soak with pixel check (locus-core PR #1635).

| PR | Area | Fixes after conversion |
|---|---|---|
| M5.1 | reader | `IndexCache` race, positional reads instead of a locked seek |
| M5.2 | model, util | `DisplayModel` / `Parameters` statics → per-renderer config |
| M5.3 | graphics | bounded Paint caches, native cost per Paint (F1–F3) |
| M5.4 | renderer | hillshading classes and thread pool removed |
| M5.5 | labels | — (replaced in M6) |
| M5.6 | theme rules, instructions | hash-only matching cache key |
| M5.7 | XML parser; `legacy/` deleted | — |

**M6 — Core features.** Output changes on purpose: goldens move to committed PNGs here (H5).
- M6.1 label model: `SEPARATE` with rotation and `onPrepared`, `IN_TILE` with a tile margin (D14).
- M6.2 `HILLSHADE` anchor: theme `<hillshading>` element, else after features (D13, D16).
- M6.3 pull-based `FeatureQuery` returning a symbol bitmap, for LoPoints.

**M7 — Performance** (D21). Each change measured against the M2.7 baseline.
- M7.1 `render/skia` via Skiko (Q15), so desktop numbers track Android.
- M7.2 lock contention: per-draw `synchronized(paint)`, `Rule` subtree lock.
- M7.3 allocations: `RenderContext` lists, `ReadBuffer`, per-way text measuring.
- M7.4 theme parse (437 ms on the A54): lazy SVG, cached `MapStyle`.

**M8 — Android host API.**
- M8.1 `android/`: `VectorMapDriver`, `VectorMapRenderer`, `LabelPass`, `ThemeLoader` (D23).
- M8.2 style-menu ids, Locus-extended attributes and asset themes kept, so prefs migrate as-is.
- M8.3 throwaway locus-core branch wires the adapter end to end (H7).

**M9 — Locus integration** (locus-core side).

| ID | Item |
|---|---|
| I1 | ~100-line adapter behind `MapSourceRenderer`; `isStillWanted` as cancel |
| I2 | Delete `MapsForgeV4Renderer*`, `MapsForgeV4LabelRenderer`, `GarminImgRenderer` |
| I3 | Type swaps: `MapManager`, `PrefMapVectorV4Theme`, theme fragment, LoPoints |
| I4 | `MapSourceVector` base for the 22 `is MapSourceMapsForgeV4` checks and 6 casts |
| I5 | Remove the Mapsforge globals Locus sets |
| I6 | Tile size per Q16, with `MapAutoLoader` filter and memory budget |
| I7 | One release behind a remote flag, upstream Mapsforge as fallback (`com.asamm.*` by then) |
| I8 | A54 A/B of 7 views, zoom soak vs F1, `third_party_licenses.md` updated |

## Decisions

All taken 2026-10-22.

| ID | Decision |
|---|---|
| D1 | ~~Repo `kmp-mapsforge`~~ → one repo for all formats (D10) |
| D2 | Vendored Mapsforge mirrors the upstream module split |
| D3 | Packages `org.mapsforge.*` until M5, then `com.asamm.*` |
| D4 | Vendored from commit `54e8654f` (tag 0.30.0) |
| D5 | Test theme LoMaps 1.8.0 (Apache-2.0) |
| D6 | Locus consumes GitHub Packages builds |
| D7 | Maven `com.asamm.kmp:kmp-vector-maps-<module>` (D15) |
| D8 | Mapsforge part LGPL-3.0-or-later + §4(d)/(e) waiver, public repo |
| D9 | Committed map `monaco.osm.map`; Czech map local only |
| D10 | One repo: format modules, shared core, viewer, golden and bench tooling |
| D11 | GDAL-like driver interface for every format |
| D12 | Locus integration last, fully Kotlin and optimised |
| D13 | Hillshading stays in Locus, drawn at a `HILLSHADE` anchor |
| D14 | Separate screen-space label pass, upright under rotation, for every format |
| D15 | Repo name `kmp-vector-maps` |
| D16 | Mapsforge anchor = theme `<hillshading>`; without it after all features |
| D17 | This roadmap is the source of truth; the locus-core plan stays the record |
| D18 | Shared viewer starts from the kmp-garmin-img viewer |
| D19 | From-scratch code is Apache-2.0; formats keep their licence |
| D20 | 512 px tiles in goldens, benchmarks, viewers; Locus per Q16 |
| D21 | Speed work after the Kotlin conversion, against a Java baseline |
| D22 | No iOS; Kotlin/JVM libraries, KMP only for the viewer UI |
| D23 | Host API mirrors Locus' `MapSourceRenderer` seam |

- D12, rejected: switching Locus to the Java fork after a memory fix. The growth (F1) has been in
  the app for years; one switch to the finished library is preferred.
- D16: Locus shades the finished tile today, so a theme without the marker keeps today's look.
- D20: Locus sizes vector tiles 256 px × density rounded down to 64 px, minimum 64 (704 px on the
  A54, 640 px at density 2.625); Garmin already uses 512 px.
- D22, rejected: KMP `commonMain` for the libraries. iOS Locus uses MapLibre with Asamm's vector
  tiles, and kmp-garmin-img uses no KMP feature (no `expect`/`actual`, dead `kotlinx-io`).
- D23: Locus changes shrink to an adapter plus type swaps; see [architecture.md](architecture.md).
- The former "Java hot spots" milestone is folded into M5: with no early switch it was double work.

## Hazards

See the locus-core plan, R1–R7. Repo-specific:

| ID | Hazard | Mitigation |
|---|---|---|
| H1 | Upstream and vendored `org.mapsforge` on one classpath | golden generation in its own module |
| H2 | Vendored Java drifts from upstream | sync script, deviation tables in [upstream.md](upstream.md) |
| H3 | Interface shaped by Mapsforge alone | Garmin (M4) right after core v0 (M3) |
| H4 | Locus sees nothing until M9 | A54 runs in the shared viewer; I7 fallback flag |
| H5 | Committed PNGs break across OS, JDK, fonts | pinned container exact, tolerant diff outside |
| H6 | JitPack outage or rebuild | checksum verification; cache the jars if outages hurt |
| H7 | Host API drifts from Locus' real seam | mirrors `MapSourceRenderer`; M8.3 throwaway wiring |

## Adjacent, out of scope

A1–A6 found in locus-core `development` @ `79226d9`, A7 in upstream Mapsforge 0.30.0; not fixed here.

| ID | Finding |
|---|---|
| A1 | `TileCreator` starts a raw `Thread` per tile, up to cores × 2 |
| A2 | Mapsforge `TileBitmap` never released, its reuse pool is dead weight |
| A3 | A running render is never interrupted |
| A4 | No redraw after the label pass prepares (`TODO force redraw`) |
| A5 | `MapContent` tests `is MapSourceMapsForgeV4` in both branches, the second is dead |
| A6 | Theme parse runs synchronously on the caller, dark variant under the pref lock |
| A7 | Equal-priority labels survive in identity-hash order (`DatabaseRenderer`), output varies between runs |

## Open

| ID | Question | Blocks |
|---|---|---|
| Q5 | SVG outside Android: pre-rasterise at theme build, or Skia SVG? | M7.1 |
| Q12 | Make `kmp-garmin-img` public (its LGPL already requires source for app users)? | M4 |
| Q13 | Garmin licence: declare LGPL-2.1-or-later, as upstream JGarminImgParser? | M4 |
| Q14 | Does the Mapsforge README waiver cover pre-2018 contributors? (locus-core Q1) | M9 |
| Q15 | Desktop backend Skia via Skiko (recommended) or AWT only? | M7 |
| Q16 | Locus tiles: keep 256 px × density, or fixed 512 px with a zoom/scale change? | M9 |
