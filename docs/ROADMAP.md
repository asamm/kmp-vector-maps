# Roadmap

Vector map rendering for Locus Map on Android — Mapsforge first, Garmin IMG next — behind one
driver interface, with desktop JVM for benchmarks and tools.

**Author:** Menion · **Started:** 2026-10-22 · **Trigger:** locus-core plan
[`mapsforge_render_extraction.md`](https://github.com/asamm/locus-core/blob/development/docs/plans/mapsforge_render_extraction.md)
(findings F1–F12, hazards R1–R7) · **Design:** [architecture.md](architecture.md)

**Status: M0–M3 and M5 done; M4.1 and M4.2 done (PRs #27–#30); M4.3a (parallel Garmin renders)
in review.** Next action: M4.3b, multi-map Garmin files; M6 follows M4.

Legend: ✅ done · 🔧 in review · ⬜ open · ⏸ deferred

## Milestones

Order follows dependencies: safety net before any change, a second format before the interface
settles, labels before speed, the Locus-facing API last.

| ID | Milestone | Size | Status |
|---|---|---|---|
| M0 | Foundation: vendored Mapsforge 0.30.0, golden test, CI | S | ✅ `d41acf5` |
| M1 | Review hardening: CI, supply chain, licence docs, this plan | S | ✅ PR #1, `42f1598` |
| M2 | Safety net: golden coverage of Locus modes, benchmark baseline | M | ✅ PR #2, `5e5b037` |
| M3 | Core v0, Mapsforge driver over the Java, shared viewer | M | ✅ PRs #3–#5, `028d746` |
| M4 | Garmin IMG joins | M | 🔧 |
| M5 | Mapsforge to Kotlin, hot-spot fixes per area, `legacy/` deleted | L | ✅ PRs #6–#26, `b22e989` |
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

**M2 — Safety net. DONE 2026-10-24** (PR #2, merged as `5e5b037`)

One PR (`feature/m2-golden-coverage`), one commit per row, each closing a gap the second deep
review found.

| Step | Content | Status |
|---|---|---|
| M2.1 | Corpus at 512 px, scale 1 / 2.625 / 3, z18–20 on land, menu `hike_bike` and `car` | ✅ |
| M2.2 | Locus primary mode: no labels in tile + `TileBasedLabelStore`, label dump | ✅ |
| M2.3 | Parallel 3×3 block: same pixels and labels as a serial render | ✅ |
| M2.4 | Android pixels via Robolectric native graphics, vendored vs upstream | ✅ |
| M2.5 | Text dumps: reader `MapReadResult`, `RenderCallback` output | ✅ |
| M2.6 | Paint-count sweep z8–20, proxy for the memory growth F2 | ✅ |
| M2.7 | `dev/bench/`: JMH per stage on the JVM, baseline recorded here; adds the Apache-2.0 text | ✅ |
| M2.7b | A54 run at N threads (`androidx.benchmark`), baseline recorded here | ✅ |

- M2.1: 18 tiles × 4 sets covering theme, menu layer, device scale and text scale. Not caught:
  the per-instruction menu filter — LoMaps sets a category on only 4 instructions (swamp, two
  lines), none on the Monaco tiles.
- M2.2: a fifth set in Locus' primary mode (no labels in tile, `TileBasedLabelStore(100)`) and its
  label dump in store order: 319 labels over 18 tiles — 219 symbols, 65 captions, 35 way texts.
- M2.3: a 3×3 block at z16 rendered on 4 threads through one long-lived renderer, 5 rounds,
  against a serial render. Neither a dropped paint lock nor dropped matching-cache locks were
  caught: the test guards render-order independence and gross races, not every lock.
- Determinism (M2.1, corrected in M2.3): upstream breaks label ties in `HashSet` order, which
  follows identity hashes (A7). Both renders run in their own JVM with a sequential identity-hash
  counter (`-XX:hashCode=3`), a serial collector on a fixed heap and synchronous JIT (`-Xbatch`),
  and align the counter to a fixed boundary before every theme parse and tile. Classpath jars open
  up front: the two sides list them in a different order, and a jar opened mid-render shifted the
  hashes of everything after it. Each side writes the hashes per tile to `identity_hashes.log`, the
  first place to look when a tie flips; one flipped on CI in 3 of 4 runs before this.
  The first fix, a constant hash (`-XX:hashCode=2`), made `Closed.YES` and `Closed.NO` collide in
  the theme's matching cache and rendered some areas as lines; M2.3 caught it.
- M2.4: the 90 corpus tiles through `map-android` under Robolectric native graphics (SDK 35), each
  side in its own test JVM with the M2.3 flags. Upstream varied in one tile between runs: the first
  rasterisation of each symbol starts a thread writing Mapsforge's SVG file cache, and those
  threads shift the main thread's identity hashes. A warm-up pass fills the never-evicted symbol
  cache, waits for the writers, then renders the kept pass. Caught: line stroke +0.1 %
  (90 of 90), caption x+1 (41), symbols not drawn (42). Labels and text dumps stay AWT-only.
- M2.5: `reader.txt` and `theme_callbacks.txt` over the corpus tiles plus z3 and z5, the theme
  driven through Mapsforge's own `StandardRenderer` with full paint state. Not covered by the
  Monaco map: explicit way label positions, water tiles, `readLabels` / `readPoiData`.
- M2.6: `paint_counts.txt` from one long-lived renderer (`hike_bike`) sweeping z8–20 twice, Paints
  created and still alive after a full GC: 3,359 alive after the theme parse, ~3,100 more per new
  zoom, 43,494 after z20, unchanged in the second sweep — the never-evicted caches of F2/F3. M5.6
  bounds `live` (D29). Caught here, no pixel changed: stroke scaling not cached per zoom (~2,100
  Paints per zoom in the second sweep; from M5.6, with every zoom evicted between sweeps,
  `ThemeCachesTest` catches it); per-zoom eviction changes `live`.
- Mutations caught (90 renders, 5 sets): menu rule filter 90 of 90, device scale ignored 54,
  caption offset flipped 45 plus the dump, labels not stored (dump and M2.3), way coordinate +1
  µ° (90 plus `reader.txt`); line stroke +0.1 % (90), caption text size ×1.05 (50) and line dash
  ×1.05 (33), each also in `theme_callbacks.txt`.
- M2.7: JVM baseline below, `./gradlew :dev:bench:jmh` on 2026-10-23, Ryzen AI 9 HX PRO 375 (24
  threads), JDK 17, 512 px tiles, Locus primary mode, LoMaps day theme without menu, p50 ms.
  Czech column: `czech_republic.osm.map` (765,299,145 B, LoMaps of 2026-10-08) with
  `-Pbench.center=50.0875,14.4214`. One fork: theme parse varies ~20 % between runs, so compare
  M7 deltas below that with `fork = 3`.

| Benchmark | Monaco | Czech map at Prague |
|---|---|---|
| theme parse | 63 | 52 |
| read tile z12 / z15 / z17 | 3.2 / 0.11 / 0.09 | 8.4 / 1.1 / 1.1 |
| render tile z12 / z15 / z17 | 64 / 30 / 12 | 105 / 85 / 25 |
| 4×4 block at z16, 1 / 2 / 4 / 8 threads | 364 / 188 / 115 / 90 | 595 / 318 / 194 / 135 |
| allocated per tile render at z12 | 12.7 MB | 31.5 MB |

- M2.7b: A54 baseline below, `dev/bench/android` (`androidx.benchmark` 1.5.0) on 2026-10-23, Galaxy
  A54 (SM-A546B, Android 16, clocks locked), same setup as M2.7, p50 ms; how to run in
  `dev/bench/android/build.gradle.kts`. Rendering takes up to ~2× the JVM time (z12); the block
  stops scaling above 4 threads (4 big cores). Theme parse is XML only on both: symbols rasterise
  lazily on first render, which no number here isolates.

| Benchmark | Monaco | Czech map at Prague |
|---|---|---|
| theme parse | 83 | 80 |
| read tile z12 / z15 / z17 | 11.3 / 0.64 / 0.52 | 37.6 / 5.2 / 5.7 |
| render tile z12 / z15 / z17 | 126 / 39 / 15 | 259 / 131 / 36 |
| 4×4 block at z16, 1 / 2 / 4 / 8 threads | 433 / 260 / 171 / 168 | 881 / 502 / 324 / 305 |

*Verified: each step's new test fails on a targeted mutation; `./gradlew build` and the PR CI run
green, 3 of 3 runs on the final commit.*

**M3 — Core v0 and shared viewer. DONE 2026-10-25** (PRs #3–#5, merged as `028d746`)

| PR | Content | Status |
|---|---|---|
| M3.1 | `core/` (Kotlin/JVM, Apache-2.0): driver, registry, dataset, style, renderer, render key | ✅ PR #3, `e0c45d8` |
| M3.2 | Mapsforge driver over the vendored Java; golden test runs through it | ✅ PR #4, `0189c2c` |
| M3.3 | Viewer rewritten to the kmp-garmin-img viewer's features, registry-driven tile engine; throwaway viewers deleted | ✅ PR #5, `028d746` |

- M3.1: interfaces and value types only, no implementation yet. Not in v0, added with their first
  user: draw hooks and anchors (M6), `FeatureQuery` (M6), label pass (M6), drawing calls on
  `RenderTarget` (unscheduled: the Garmin driver keeps its own target, M4.2a), style sources other
  than files (`MapDataset.styles` since M4.2a, Android asset themes M8).
- M3.2 must: take the Mapsforge `GraphicFactory` in the driver's constructor; read labels in the
  renderer's language (vendored `MapFile` fixes it per instance); publish the multi-map store
  copy-on-write, since `MultiMapDataStore` is not safe to grow while read; keep the
  `TileDependencies` label path for `IN_TILE` (`MapDataStoreLabelStore` is order-independent but
  changes pixels against the goldens); never map a swallowed render failure to `EMPTY`.
- M3.2 as built (`formats/mapsforge/driver`):
  - Device scale goes into each renderer's `DisplayModel` user scale; the JVM-wide Mapsforge
    device scale had to stay 1 (checked) until M5.2, where C4 made the renderer's scales its own.
  - `TileBitmapTarget` receives the bitmap Mapsforge allocates; drawing into a host bitmap waits
    for M8.1. Never returns `EMPTY`: Mapsforge fills tiles without data with the background.
  - Reads that Mapsforge logs and turns into null throw instead (`CheckedMapFile`); one bad file
    of a multi-map fails the tile. Errors inside a block stay swallowed (logged, block skipped); M5.1
    kept that catch narrow, so other exceptions fail the tile.
  - In-tile labels ask a key-only tile cache (`DrawnTiles`) which neighbours were drawn; Mapsforge's
    `InMemoryTileCache`, which Locus uses today, also pins up to its capacity in bitmaps.
  - Each renderer opens its own readers in its language, so no shared store grows while read (the
    copy-on-write must); hosts recreate renderers after `addMap` (core KDoc). Multi-maps return all
    data (`RETURN_ALL`) and text wraps at `0.7 × textScale` of the tile, as in Locus.
  - The style id holds the theme file time; `createRenderer` throws once the file changed.
  - Style menus missing a required attribute or naming an absent default layer give `menu = null`
    and render every category; so does a layer id the theme lacks, as in Locus.
  - Golden: the vendored AWT corpus tiles and label dumps render through the driver; the text
    dumps, Paint counts, `ParallelRenderTest` and the Android goldens stay on plain Mapsforge. The
    upstream side mirrors the driver's menu read and map probe before each set; the two sides
    still run different code, so a few slots in `identity_hashes.log` differ by class loading and
    JIT work; the vendored side repeats exactly, the upstream side varies slightly between runs.
- M3.3 as built (`viewer/`, Apache-2.0):
  - Written from scratch to the feature list of the kmp-garmin-img viewer (D18): no code from it
    or the throwaway viewers (licence.md).
  - `viewer/common`: Web Mercator `Viewport` and a `TileEngine` over any `MapRenderer`; a tile
    that leaves the screen before its render starts is skipped through `isCancelled` (Mapsforge
    checks only before drawing); kept tiles are evicted least recently used; empty and failed
    tiles wait for Re-render (desktop) or a restart (Android); an old engine closes in the
    background, since its renderer waits for renders in progress. A `TileImages` per driver and
    platform turns the driver's target into an image.
  - Both viewers open a map fitted to its bounds; the Mapsforge header's start position is not in
    the core API.
  - Desktop: the session (window, map, style, layer, settings, view) comes back on restart from
    `~/.kmp-vector-maps/viewer.properties`; Capture writes PNG + properties to `captures/`; Home
    fits the dataset bounds; style layer menu, text scale, tile size, threads; light and dark
    theme follow the system.
  - Both viewers fling: the map glides on after a drag and slows down.
  - Style files come from the map's driver (`MapFormatDriver.styleExtensions`); a map keeps the
    style when its driver reads that file; a style is committed only once its renderer builds.
  - Android takes the device scale into `RenderConfig`; the Mapsforge static keeps the density
    `AndroidGraphicFactory` sets, which the driver ignores since M5.2 (C4); 512 px tiles (D20), 4 render threads (M2.7b), ~50 MiB of kept tiles (100 Mapsforge RGB_565 or 50 Garmin ARGB_8888 at 512 px);
    rotation keeps the view; setup errors show as text.
  - Garmin's anti-aliasing, kind toggles and TRE level are back in the desktop viewer (M4.2b).

*Verified: `./gradlew build` green on PRs #3–#5; golden test byte-exact through the driver, and
red when the driver ignores the text scale (the wrap width is not covered: LoMaps sets its own on
most captions; a driver test pins it); viewer GUI pass on desktop and on the A54 (Android 16).*

**M4 — Garmin IMG.** LGPL-2.1-or-later, public in this repo (D33).

| PR | Content | Status |
|---|---|---|
| M4.1 | Audit, then import with history: secrets and local paths (`local.properties`), fixture licences (OTM Monaco `.img` and TYP); headers to LGPL-2.1-or-later with SPDX, missing ones added; KMP → Kotlin/JVM; drop unused `kotlinx-io`, coroutines, `api` logger | ✅ PR #27, `c0a7b2e` |
| M4.2a | Garmin driver (`formats/garmin-img/driver`): embedded TYP, built-in and `.typ` styles through `MapDataset.styles` and `loadStyle`; `GarminOptions` (anti-aliasing, kinds, TRE level); exact tile bounds | ✅ PR #28, `aa658a1` |
| M4.2b | Desktop and Android viewers render Garmin maps, the desktop with anti-aliasing, kind toggles and TRE level; `.img` assets in the viewer APK | ✅ PR #29, `6bf158b` |
| M4.2c | Garmin golden through the driver; the pixel test off Windows with a bundled font; the M4.1 leftovers below | ✅ PR #30, `a661dac` |
| M4.3a | Parallel renders: the map parsed once per dataset, a worker per core with its own readers and style cache | 🔧 |
| M4.3b | Multi-map IMGs (today first map only) and several `.img` per dataset; the nearest-finer level fallback | ⬜ |
| M4.3c | The performance items below, measured against a benchmark baseline | ⬜ |
| M4.4 | Measured label bounds, TYP night colours, text scale; TYP point icons and extended-type (`≥ 0x100`) polygon styles, both falling back to the internal styles today | ⬜ |

- M4.1 audit (2026-11-02, all 53 commits): no secrets or keys, `local.properties` never committed,
  4 local paths in comments and a capture (removed at the tip, history kept); one author identity. Fixtures: `otm-monaco` ODbL data plus
  OpenTopoMap style CC-BY-SA 4.0, `mkgmap-monaco` ODbL; both allow public redistribution with
  attribution.
- M4.1 as built: tags `garmin-img/v0.5.0`–`v0.5.2`; modules `formats/garmin-img/{engine,android,awt}`,
  tests in `dev/garmin-img/golden` (Kotlin friend of the engine, as the tests read its internals),
  maps in `dev/fixtures/maps/`; KMP source sets merged, the unused `BinaryReader` (the only
  `kotlinx-io` user) and its tests removed; packages `com.asamm.vectormaps.garminimg.*`; old docs in
  [docs/garmin-img/](garmin-img/). The pixel test ran on Windows only (fonts) until M4.2c, as in
  kmp-garmin-img; CI covers the reader, dumps and the render sanity tests.
- M4.1 review fixed in the import (PR #27): a crafted TRE can no longer multiply the sub-division
  tree; a crafted TYP is held to 32 MB of decoded bitmaps, each record decoded once; a malformed
  TYP header drops the TYP, not the map; the coordinate bit-stream escapes loop instead of
  recursing; a corrupt segment table skips its sub-division; labels read at most 1,024 bytes
  within the labels section; a corrupt point record ends its segment; the city lookup restores the
  RGN reader and stops at its index; POIs without readable POI data get no label; labels decode
  through `java.nio` charsets as in the legacy parser (cp1250, cp1251, 932, 950); dashes that
  start and end with a gap keep their length; AWT draws white dashes; the dump comparison counts
  duplicate records.
- Left for M4.2, done in M4.2c: fixtures as a `fixturesDir` (not the whole `dev/fixtures` as
  resources), `.img` assets kept out of the bench APK (the viewer reads them since M4.2b), the
  benchmark and the comparison sheet out of the golden gate, `test-output/` declared as a task
  output, the `kmpgi.*` properties as `bench.garmin.*` Gradle properties.
- M4.2c as built: three Monaco harbour tiles (z13, z15, z17, 512 px) and a Monte Carlo tile at
  device scale 2 (z16) render through the driver with the map's TYP, anti-aliasing off and the
  bundled DejaVu Sans
  (`dev/fixtures/fonts/dejavu/`, `AwtRenderTarget(font = …)`), compared with committed PNGs on
  every system, 0.2 % of pixels allowed to differ; the Windows-only Monaco PNG is gone. The
  benchmark and the comparison sheet run with `-Pbench.garmin=true`.
- Left for M4.3b (multi-map): nearest-finer level fallback, sub-files matched to the TRE tile
  (multi-tile gmapsupp renders the first tile only). Left for M4.3c (performance): multi-byte reads
  through `ImgSubFileStream`, reusable render targets, POI metadata skipped when rendering, one
  projection per polyline without a `FloatPair` per vertex, each sub-division frozen once, one
  sub-division overlap helper (`ImgRenderer`, `ImgFile`), label and coordinate buffers without
  boxing, AWT icons cached as on Android, fewer allocations per label and pattern on Android.
- M4.2a as built: the host passes a `GarminCanvasFactory` (`AwtRenderTarget`, `AndroidRenderTarget`)
  and reads the canvas from `GarminTileTarget`; one `.img` per dataset (`addMap` adds nothing) and
  one tile at a time (`maxParallelism` 1) until M4.3a; in-tile labels only, cut at the tile edge
  until M6.1; `GarminOptions` stay outside `RenderKey`; the engine's `Viewport.ofDegrees` projects
  the exact tile bounds, as a Garmin unit (~2.4 m) shifts high-zoom tiles by pixels, and the level
  pick reads that exact span. Edge strokes and icons stop at sub-division and map edges, and a tile
  partly outside the map gets the opaque background (both engine behaviour, with the M6.1 labels).
  Review left for M4.3: one parsed TRE tree and TYP per dataset instead of one per renderer (done in
  M4.3a); TYP patterns are converted per tile, as each tile gets a new canvas. PR #28 review:
  materialised levels and per-level sub-divisions cached per parsed map (shared by its `reopen`
  copies since M4.3a); the map file checked against the time it was opened.
- M4.2b as built: both viewers register the Garmin driver and start a map with its own first style
  (`MapDataset.styles`) when no style file of its format is open; the desktop style choice lists the
  map's styles next to the style files; the session keeps the last style file per format, which a
  map without styles of its own (Mapsforge) starts with; Reload keeps a map's own style and its
  Garmin settings, and a failing style file keeps its error. Building a renderer still runs on the
  desktop UI thread; since M4.3a a Garmin setting reuses the dataset's parsed map.
  Garmin controls are desktop only; the Android viewer has none, a Garmin map comes in by intent
  (MainActivity KDoc). Tiles outside a Garmin map are `EMPTY` and show as missing tiles.
- M4.3a as built: the dataset parses the map once (headers, sub-division tree, TYP) and closes its
  readers; each renderer keeps a worker per concurrent render, up to `maxParallelism` (the core
  count), and every worker reads through an `ImgFile.reopen` copy with its own readers and its own
  `typAware` cache; the tree's level index is built once and shared. Workers stay until `close`, so
  a renderer holds up to about 4 × `maxParallelism` file handles (M4.3b and M8 hosts: count them). A
  worker checks the file time before it opens the file. A test renders 16 tiles twice on that many
  threads and compares them with sequential renders pixel by pixel; it fails when the workers share
  one file.
- Carried over from the kmp-garmin-img roadmap: TYP point icons and extended-type polygons →
  M4.4; label collisions at level-pick boundaries → M6.1; Locus prefs from the helpdesk
  (label colour, line width and colour overrides, area/line/point toggles, per-type visibility,
  extended objects such as truck signs) → M8.2; an external `.typ` next to the `.img` → the
  driver's `loadStyle` (M4.2); the iOS target is dropped (D22).

**M5 — Mapsforge to Kotlin.** One area per PR, its hot-spot fixes in a follow-up PR on the same
area, so each conversion PR stays pixel-identical. Every PR gated by goldens, dumps and a device
15-minute zoom soak (the A54 up to M5.1, the dev AVD from M5.2, D27) with pixel check of the tiles
(D25; the locus-core PR #1635 soak ran in Locus by hand) and, from M5.5a, of each tile's label
store entry (D31). From M5.1 the PRs merge into the umbrella `feature/m5`, which reaches `main` in one
PR after M5.7 (D26).

| PR | Area | Fixes after conversion | Status |
|---|---|---|---|
| M5.0 | soak harness (D25), no conversion | — | ✅ PR #6, `cc5dbce` |
| M5.1 | reader | `IndexCache` race, positional reads instead of a locked seek, malformed-input guards | ✅ PRs #7–#9, `6c64044` |
| M5.2a | core model, util (17 files) | — | ✅ PR #10, `2bab7d9` |
| M5.2b | map model, util, datastore (12 files) | `DisplayModel` scales per model (C4, D28) | ✅ PR #11, `4346dbd`; fixes PR #12, `ee52c8d` |
| M5.3a | core graphics (27 files) | — | ✅ PR #13, `6841d56` |
| M5.3b | Android and AWT graphics, `AndroidParameters` (25 files) | native cost per Paint measured (F2); caches and `ANTI_ALIASING` moved to M5.6 (D29) | ✅ PR #14, `cc24934`; fixes PR #15, `dd4d89c` |
| M5.4a | hillshading and its thread pool removed before the conversion (17 files, C5, D30) | — | ✅ PR #16, `70710a3` |
| M5.4b | renderer, cache, queue (17 files) | `POLYGON_*`, `NUMBER_OF_THREADS` per renderer (D28); the graphics hillshading API (`shadeBitmap`, `createMonoBitmap`, `HillshadingBitmap` and its two implementations) removed | ✅ PR #17, `33d9a1c`; fixes PR #18, `099eb6d` |
| M5.5a | soak label store check (D31), no conversion | — | ✅ PR #19, `ab70bdf` |
| M5.5b | labels (7 files) | — (replaced in M6) | ✅ PR #20, `54f8d0e` |
| M5.6a | theme rules, matchers, `RenderTheme`, `RenderContext`, callbacks (20 files) | — | ✅ PR #21, `e37f872` |
| M5.6b | render instructions (9 files) | — | ✅ PR #22, `6475f90` |
| M5.6 fixes | theme caches and settings after the conversion | matching cache key compares tags in full; Paint caches bounded to a window of 4 zoom levels, a rendering job holds its zoom (F1–F3, D29); `ANTI_ALIASING` (theme Paints, the tile canvas, on Android its bitmap Paint) and `SYMBOL_SCALING` per `DisplayModel` (D28) | ✅ PR #23, `1f3766e` |
| M5.7a | XML theme parser (11 files); the theme members widened for it back to `internal` | — | ✅ PR #24, `cada457` |
| M5.7b | `legacy/` deleted; rename to `com.asamm.vectormaps.mapsforge.*`; 5 modules into `formats/mapsforge/{engine,android,awt}`, the golden modules to `dev/mapsforge/` (D24, K64) | — | ✅ PR #25, `de92503` |

- M5.1 fixes (upstream.md C1–C3): the index cache is locked, block reads are positional (measure
  the gain on Android or Linux: the JDK on Windows still serialises them), and an overflowing string
  length or index block size no longer throws. A missing debug signature skips its block, as a
  wrong one did upstream. The per-block catch stays narrow: other malformed data fails the tile, as
  the driver prefers a visible error to a blank tile.
- M5.2 fixes (upstream.md C4, D28): device, line, symbol and text scales are per `DisplayModel`;
  the statics only seed new models, so the driver no longer needs the global scale at 1.
- M5.3 fixes (D29): `PaintCostTest` measures heap per Mapsforge Paint, each drawn once. The
  Paints' native heap (224–320 B × ~68k new Paints) explains ~15–22 MB of F1's native +93–170 MB;
  their Java heap (~16 MB) is outside F1. Most of the growth is unexplained (Q18); M5.6 bounds the
  Paint caches regardless. No soak, goldens or dumps: no library code changed.
- M5.4 fixes (upstream.md C6): `NUMBER_OF_THREADS` and the polygon settings are per
  `DisplayModel`, the driver pins them to its parallelism and upstream's default; the graphics
  hillshading API is gone, as the host draws hillshading at the `HILLSHADE` anchor (D13).
- M5.6 fixes (upstream.md C7, D29): the matching caches compare tags, not only their hash. The
  theme keeps scaled Paints for its 4 most recently used zoom levels no job holds, plus
  those a `DatabaseRenderer` job holds, trimmed when a job starts or ends; matching keys with more
  than 256 characters of tags are not cached; `paint_counts.txt` (M2.6) peaks at 15,871 live Paints, upstream
  43,494, and the golden test checks that bound instead of equality with upstream. Other
  `RenderContext` users (LoPoints) only mark their zoom used. `ANTI_ALIASING` and
  `SYMBOL_SCALING` seed per-model settings.
- M5.5a (D31): `SoakTest` compares each `SEPARATE` render's label store entry with the
  serial render's. A reference at text scale 1.5 left all 5,026 renders' pixels equal but 2,563
  label entries different in two minutes, so the check catches what the pixel check misses.

*M5.0 baseline, Java code, 2026-10-26: A54 (SM-A546B, Android 16, clocks not locked), bundled
Monaco map, `soak.minutes=15` (7.5 per label mode), seed 20261025. `SEPARATE`: 59,746 renders
drawn, all compared, 0 differed (render time includes the hash). `IN_TILE`: 66,756 renders drawn,
no failure.*

*M5.1, reader in Kotlin, same setup, 2026-10-26: `SEPARATE` 56,558 renders, all compared, 0
differed; `IN_TILE` 66,591, no failure; goldens and dumps byte-exact.*

*M5.1 fixes, same setup, 2026-10-26: `SEPARATE` 59,962 renders, all compared, 0 differed;
`IN_TILE` 66,427, no failure; goldens and dumps byte-exact.*

*M5.2a on the dev AVD (`VectorMaps_Soak_API36`, API 36 x86_64, D27), 2026-10-26: `SEPARATE` 52,431
renders, all compared, 0 differed; `IN_TILE` 68,851, no failure; goldens and dumps byte-exact.*

*M5.2b, same AVD, 2026-10-26: `SEPARATE` 37,401 renders, all compared, 0 differed; `IN_TILE`
46,382, no failure; soak on the code before the review fixes, goldens and dumps byte-exact
after them.*

*M5.2 fixes, same AVD, 2026-10-26: `SEPARATE` 81,022 renders, all compared, 0 differed; `IN_TILE`
101,501, no failure; goldens and dumps byte-exact.*

*M5.3a, same AVD, 2026-10-26: `SEPARATE` 59,331 renders, all compared, 0 differed; `IN_TILE`
88,376, no failure; soak on the code before the review fixes, goldens and dumps byte-exact after
them.*

*M5.3b, same AVD, 2026-10-26: `SEPARATE` 54,531 renders, all compared, 0 differed; `IN_TILE`
61,443, no failure; goldens and dumps byte-exact. A first run had the test process die about a
minute into `IN_TILE` (`SEPARATE` 48,832 renders, 0 differed); no stack trace was captured. An
`IN_TILE` rerun and this full run with logcat streamed showed no crash (Q17).*

*M5.3 fixes, `PaintCostTest` on the same AVD (x86_64), 2026-10-26, 20,000 Paints, second of two
runs: native / Java bytes per Paint 224 / 240, with a dash effect 320 / 296, with a bitmap shader
544 / 384; a typeface or text size adds nothing. F2's 1.4–2.5 KB is F1's native growth divided by
the Paint count, on the A54; `PaintCostTest` has not run on the A54 yet. No soak, goldens or
dumps: no library code changed.*

*M5.4a, same AVD, 2026-10-27, logcat streamed (Q17): `SEPARATE` 54,271 renders, all compared, 0
differed; `IN_TILE` 71,578, no failure, no test-process crash; goldens and dumps byte-exact.*

*M5.4b, same AVD, 2026-10-27, logcat streamed (Q17): `SEPARATE` 43,780 renders, all compared, 0
differed; `IN_TILE` 58,583, no failure, no test-process crash; soak on the code before the review
fixes (KDoc, docs, a licence notice), goldens and dumps byte-exact after them.*

*M5.4 fixes, same AVD, 2026-10-27, logcat streamed (Q17): `SEPARATE` 54,309 renders, all compared,
0 differed; `IN_TILE` 65,641, no failure, no test-process crash; soak before the review fixes
(dead members, KDoc, a setter check), goldens and dumps byte-exact after them.*

*M5.5a, same AVD, 2026-10-27, logcat streamed (Q17): `SEPARATE` 52,831 renders, all
compared, 0 differed, labels of 0 differed; `IN_TILE` 62,407, no failure; render times now include
the label hashing. After the review fixes a 2-minute `SEPARATE` run: 5,699 renders, 2,863 with
labels, 0 differed. With way-label text and visibility added (PR #19 review), a 2-minute run:
11,229 renders, 5,723 with labels, 0 differed.*

*M5.5b, same AVD, 2026-10-27, logcat streamed (Q17): `SEPARATE` 47,615 renders, all compared, 0
differed, labels of 0 differed (24,265 with labels); `IN_TILE` 66,731, no failure; soak before the
review fixes (KDoc, an initializer), goldens and label dumps byte-exact after them.
`MapDataStoreLabelStore` has no caller, so neither the goldens nor the soak reach it.*

*M5.6a, same AVD, 2026-10-27, logcat streamed (Q17): `SEPARATE` 54,151 renders, all compared, 0
differed, labels of 0 differed (27,332 with labels); `IN_TILE` 70,813, no failure; review fixes
were docs only.*

*M5.6b, same AVD, 2026-10-27, logcat streamed (Q17): `SEPARATE` 59,536 renders, all compared, 0
differed, labels of 0 differed (30,194 with labels); `IN_TILE` 65,788, no failure; soak before the
review fixes (comments, a guard split), goldens and dumps byte-exact after them.*

*M5.6 fixes, same AVD, 2026-10-27, logcat streamed (Q17): `SEPARATE` 61,106 renders, all compared,
0 differed, labels of 0 differed (30,913 with labels); `IN_TILE` 65,901, no failure; on the code
after the PR #23 review, after two earlier soaks (62,204 and 58,166 renders, 0 differed).*

*M5.7a, same AVD, 2026-11-01, logcat streamed (Q17): `SEPARATE` 71,734 renders, all compared, 0
differed, labels of 0 differed (36,349 with labels); `IN_TILE` 98,505, no failure; soak before the
review fixes (KDoc and docs only), goldens and dumps byte-exact after them.*

*M5.7b, same AVD, 2026-11-01, logcat streamed (Q17): `SEPARATE` 71,062 renders, all compared, 0
differed, labels of 0 differed (36,151 with labels); `IN_TILE` 81,109, no failure; soak after the
rename and module merge, before the golden move and review fixes (build files, imports, docs);
goldens and dumps byte-exact after them.*

**M6 — Core features.** Output changes on purpose: goldens move to committed PNGs here (H5).
- M6.1 label model: `SEPARATE` with rotation and `onPrepared`, `IN_TILE` with a tile margin (D14);
  Garmin labels piling up at level-pick boundaries (M4.1 carry-over).
- M6.2 `HILLSHADE` anchor: theme `<hillshading>` element, else after features (D13, D16); re-capture its position, dropped in M5.4a (D30).
- M6.3 pull-based `FeatureQuery` returning a symbol bitmap, for LoPoints.
- M6.4 upstream bugs A8 and A9 fixed.

**M7 — Performance** (D21). Each change measured against the M2.7 baseline.
- M7.1 `render/skia` via Skiko (Q15), so desktop numbers track Android.
- M7.2 lock contention: per-draw `synchronized(paint)`, `Rule` subtree lock.
- M7.3 allocations: `RenderContext` lists, `ReadBuffer`, per-way text measuring.
- M7.4 theme parse (~80 ms on the A54, M2.7b): compiled themes cached per `RenderKey`; SVG
  rasterising on first render is not measured yet.

**M8 — Android host API.**
- M8.1 `android/`: `VectorMapDriver`, `VectorMapRenderer`, `LabelPass`, `ThemeLoader` (D23);
  `TileBitmapTarget` over a host-supplied bitmap.
- M8.2 style-menu ids, Locus-extended attributes and asset themes kept, so prefs migrate as-is;
  the Garmin style prefs from the helpdesk (M4.1 carry-over).
- M8 must: Mapsforge's Android SVG cache keys unsized symbols without the scale, so renderers at
  two device scales share them; key them by scale or clear the cache per scale.
- M8 must, while Locus still renders through Mapsforge directly: its `DISPLAY_MODEL` exists before
  `setDeviceScaleFactor(BASE_DENSITY)`, and models no longer read the static later (C4); set
  `DISPLAY_MODEL.deviceScale = BASE_DENSITY` instead of the `tileSizeMultiple` refresh.
- M8 must: on `addMap`, close and recreate the renderers and clear the tile cache; closing a
  dataset leaves its renderers, and their readers, open.
- M8 must: transparent tiles (a `RenderConfig` option), so a backup map shows through as in Locus.
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
| I9 | Garmin from this repo instead of `com.asamm.kmp:kmp-garmin-img-android:0.5.2`; only then delete `asamm/kmp-garmin-img`, as its GitHub Packages go with it |

## Decisions

D1–D23 taken 2026-10-22; D24–D25 2026-10-25; D26–D29 2026-10-26; D30–D31 2026-10-27; D32–D33 2026-11-02.

| ID | Decision |
|---|---|
| D1 | ~~Repo `kmp-mapsforge`~~ → one repo for all formats (D10) |
| D2 | Vendored Mapsforge ~~mirrors the upstream module split~~ → three modules `engine`, `android`, `awt` since M5.7b (K64) |
| D3 | Packages `org.mapsforge.*` until ~~M5~~ → M5.7, then `com.asamm.*` (D24) |
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
| D24 | M5 converts each area in place, in the `legacy/*` modules under `org.mapsforge.*`; M5.7 renames to `com.asamm.vectormaps.mapsforge.*` and merges the modules (K64) |
| D25 | The M5 soak is `dev/bench/android` `SoakTest`: driver plus viewer engine, pixel check of every `SEPARATE` render |
| D26 | M5 PRs from M5.1 merge into `feature/m5`; one PR to `main` after M5.7 and the owner's review |
| D27 | From M5.2 the device soak runs on the dev AVD `VectorMaps_Soak_API36`; the A54 serves other work |
| D28 | `DisplayModel` scales are per model from M5.2, the statics only seed new models, so the shared golden harness and Locus still compile; a model created before a static is set keeps the old value (Locus, see M8 must); each pixel-relevant `Parameters` field moves to per-renderer config with its area (M5.4, M5.6; D29); `MAXIMUM_BUFFER_SIZE`, `VALIDATE_COORDINATES` and the view flags stay global, as do `ANDROID_32BIT_COLOR` (the host's tile format until the host-bitmap target, M8.1) and `AndroidParameters.ANDROID_LIBHWUI_FIX` (a per-device workaround) |
| D29 | The Paint caches (F1–F3) are bounded in M5.6, in Kotlin: a window of 4 zoom levels, and each render job pins its zoom so a drawing tile never sees an unscaled Paint; where the window lives is M5.6's call. `ANTI_ALIASING` per renderer moves to M5.6 too, as on Android it is set on each theme Paint; on AWT the canvas reads it (`AwtCanvas`, set from `CanvasRasterer`), changed in the same step. 2026-10-27, M5.6 fixes: the canvas setting also reaches the Android canvas' bitmap Paint, which upstream kept on; the window lives in `RenderTheme`, which owns the scaled maps; `DatabaseRenderer` holds the zoom of each job, a bare `RenderContext` only marks it used, so a context never destroyed (LoPoints) cannot pin a zoom forever |
| D30 | M5.4 removes the unused hillshading code before converting the renderer, rather than converting it first; the renderer constructors keep their hills parameter as an ignored `Object`, so the golden harness shared with upstream still compiles; M6.2 re-captures the `<hillshading>` layer, level and `cat` in `RenderThemeHandler`, dropped here |
| D31 | The M5 soak compares each `SEPARATE` render's label store entry with a serial render's: kind, text, priority, position, clash box, visibility, standalone symbol size (way-label text through `toString`, as its field is private; the display mode itself is protected in Mapsforge); the soak fails if no render had labels. Not compared: `IN_TILE` labels (render order, M6.1), placement across tiles (the label pass, M6.1), fonts, colours and symbol pixels |
| D32 | Long term: our own OpenGL renderer in locus-core; this library moves from bitmap to bucket delivery (tessellated geometry, symbols, label data) and keeps its readers, style engines and label model; no VTM fork, VTM is a reference only. M6–M8 interfaces keep decode, style and draw separable so bucket output can join the bitmap one |
| D33 | Garmin IMG is LGPL-2.1-or-later, as upstream JGarminImgParser (Q13), imported with history into this public repo after an audit (Q12); the kmp-garmin-img headers become LGPL-2.1-or-later. Locus keeps R8 on the Garmin code, a known §6 gap the owner accepts; if raised, the package is excluded from R8 |

- D12, rejected: switching Locus to the Java fork after a memory fix. The growth (F1) has been in
  the app for years; one switch to the finished library is preferred.
- D16: Locus shades the finished tile today, so a theme without the marker keeps today's look.
- D20: Locus sizes vector tiles 256 px × density rounded down to 64 px, minimum 64 (704 px on the
  A54, 640 px at density 2.625); Garmin already uses 512 px.
- D22, rejected: KMP `commonMain` for the libraries. iOS Locus uses MapLibre with Asamm's vector
  tiles, and kmp-garmin-img uses no KMP feature (no `expect`/`actual`, dead `kotlinx-io`).
- D23: Locus changes shrink to an adapter plus type swaps; see [architecture.md](architecture.md).
- D24: a pure conversion per PR diffs cleanly and leaves the Java areas' imports alone; one rename
  at the end touches every file once.
- D25: `IN_TILE` labels depend on render order until M6.1, so that mode is only checked to render;
  a reference at a different device scale makes 1,670 of 3,036 compared renders (610 distinct
  tiles) differ in a minute, so the check bites. Labels go to the label store, not the tile, so
  they are compared from M5.5a on, by the label store check (D31).
- The former "Java hot spots" milestone is folded into M5: with no early switch it was double work.

## Hazards

See the locus-core plan, R1–R7. Repo-specific:

| ID | Hazard | Mitigation |
|---|---|---|
| H1 | Upstream and vendored Mapsforge, and the shared golden harness, on one classpath | golden generation in its own module |
| H2 | Vendored code misses upstream changes | changelog review and hand port per release, deviation tables in [upstream.md](upstream.md) |
| H3 | Interface shaped by Mapsforge alone | Garmin (M4) right after core v0 (M3) |
| H4 | Locus sees nothing until M9 | A54 runs in the shared viewer; I7 fallback flag |
| H5 | Committed PNGs break across OS, JDK, fonts | pinned container exact, tolerant diff outside |
| H6 | JitPack outage or rebuild | checksum verification; cache the jars if outages hurt |
| H7 | Host API drifts from Locus' real seam | mirrors `MapSourceRenderer`; M8.3 throwaway wiring |

## Adjacent, out of scope

A1–A6 found in locus-core `development` @ `79226d9`, A7–A9 in upstream Mapsforge 0.30.0; not fixed
here, except A8 and A9 in M6.4, as fixing them changes pixels.

| ID | Finding |
|---|---|
| A1 | `TileCreator` starts a raw `Thread` per tile, up to cores × 2 |
| A2 | Mapsforge `TileBitmap` never released, its reuse pool is dead weight |
| A3 | A running render is never interrupted |
| A4 | No redraw after the label pass prepares (`TODO force redraw`) |
| A5 | `MapContent` tests `is MapSourceMapsForgeV4` in both branches, the second is dead |
| A6 | Theme parse runs synchronously on the caller, dark variant under the pref lock |
| A7 | Equal-priority labels survive in identity-hash order (`DatabaseRenderer`), output varies between runs |
| A8 | `RenderInstruction.computeBoundary` scales the symbol boundary's top by width, not height (Mapsforge 0.30.0, kept in M5.6b) |
| A9 | `Line` with `scale="all"` and no `stroke-dasharray` throws in `scaleStrokeWidth` (Mapsforge 0.30.0, kept in M5.6b) |

## Open

| ID | Question | Blocks |
|---|---|---|
| Q5 | SVG outside Android: pre-rasterise at theme build, or Skia SVG? | M7.1 |
| Q14 | Does the Mapsforge README waiver cover pre-2018 contributors? (locus-core Q1) | M9 |
| Q15 | Desktop backend Skia via Skiko (recommended) or AWT only? | M7 |
| Q16 | Locus tiles: keep 256 px × density, or fixed 512 px with a zoom/scale change? | M9 |
| Q17 | M5.3b first soak: the test process died ~1 min into `IN_TILE`, no trace; conversion or emulator? Later soaks stream logcat until it is closed; none recurred through M5.7b. | M7 |
| Q18 | F1: the Paints' native heap explains ~15–22 MB of the native +93–170 MB zoom growth (M5.3 fixes); what holds the rest? A phone zoom soak after M5.6 shows how much the cache bound removes. | M7 |
| Q19 | C7's matching-cache keys hold copied tag lists (house numbers, `ele`, `ref`); upstream keyed on an `Integer`. How much heap do both caches retain on the corpus, against C7's Paint saving? | M7 |
| Q20 | No speed run after M5: JMH and the A54 `androidx.benchmark` against the M2.7/M2.7b baselines, so M7's deltas exclude the conversion and the C4, C6, C7 fixes. | M7 |
