# Roadmap

Vector map rendering for Locus Map on Android — Mapsforge first, Garmin IMG next — behind one
driver interface, with desktop JVM for benchmarks and tools.

**Author:** Menion · **Started:** 2026-10-22 · **Trigger:** locus-core plan
[`mapsforge_render_extraction.md`](https://github.com/asamm/locus-core/blob/development/docs/plans/mapsforge_render_extraction.md)
(findings F1–F12, hazards R1–R7) · **Design:** [architecture.md](architecture.md)

**Status: M0–M3, M5.0–M5.5 and M5.6a done; M5.6b (render instructions in Kotlin) in review; M5
runs on `feature/m5` (D26).** Next action: M5.6 fixes (cache key, Paint caches, per-renderer
`ANTI_ALIASING`); M4 waits for Q12 and Q13.

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
| M4 | Garmin IMG joins | M | ⬜ needs Q12, Q13 |
| M5 | Mapsforge to Kotlin, hot-spot fixes per area, `legacy/` deleted | L | 🔧 |
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
  bounds `live` (D29). Caught only here, no pixel changed: stroke scaling not cached per zoom (~2,100
  Paints per zoom in the second sweep); per-zoom eviction changes `live`.
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
  `RenderTarget` (first needed by the Garmin driver, M4.2), style sources other than files
  (built-in and embedded TYP styles M4.2, Android asset themes M8).
- M3.2 must: take the Mapsforge `GraphicFactory` in the driver's constructor; read labels in the
  renderer's language (vendored `MapFile` fixes it per instance); publish the multi-map store
  copy-on-write, since `MultiMapDataStore` is not safe to grow while read; keep the
  `TileDependencies` label path for `IN_TILE` (`MapDataStoreLabelStore` is order-independent but
  changes pixels against the goldens); never map a swallowed render failure to `EMPTY`.
- M3.2 as built (`formats/mapsforge/driver`):
  - Device scale goes into each renderer's `DisplayModel` user scale; the JVM-wide Mapsforge
    device scale had to stay 1 (checked) until M5.2, where C4 made the renderer's scales its own.
  - `TileBitmapTarget` receives the bitmap Mapsforge allocates; drawing into a host bitmap waits
    for M5. Never returns `EMPTY`: Mapsforge fills tiles without data with the background.
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
    `AndroidGraphicFactory` sets, which the driver ignores since M5.2 (C4); 512 px tiles (D20), 4 render threads (M2.7b), 100 kept tiles (~50 MiB RGB_565);
    rotation keeps the view; setup errors show as text.
  - Garmin's anti-aliasing, kind toggles and TRE level return with its driver (M4).

*Verified: `./gradlew build` green on PRs #3–#5; golden test byte-exact through the driver, and
red when the driver ignores the text scale (the wrap width is not covered: LoMaps sets its own on
most captions; a driver test pins it); viewer GUI pass on desktop and on the A54 (Android 16).*

**M4 — Garmin IMG.** Blocked by Q12 (visibility) and Q13 (licence wording).

| PR | Content |
|---|---|
| M4.1 | Import with history; KMP → Kotlin/JVM; drop unused `kotlinx-io`, coroutines, `api` logger |
| M4.2 | Garmin driver; its goldens and dump-diff into the shared tooling |
| M4.3 | Positional reads → parallel renders; multi-map IMGs (today first map only) |
| M4.4 | Measured label bounds, TYP night colours, text scale; missing licence headers |

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
| M5.6b | render instructions (9 files) | — | 🔧 |
| M5.6 fixes | theme fixes after the conversion | hash-only matching cache key; Paint caches bounded to 4 zoom levels with in-use pinning (F1–F3); `ANTI_ALIASING` per renderer, Android Paints and AWT canvas, and `SYMBOL_SCALING` (D28, D29); upstream bugs kept in M5.6b, fixing them changes pixels (ask first): `computeBoundary` scales top by width, `Line` with `scale="all"` and no dasharray throws | ⬜ |
| M5.7 | XML parser (11 files); `legacy/` deleted; rename to `com.asamm.*`, modules moved (D24) | — | ⬜ |

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
review fixes (comments, a guard split), goldens byte-exact after them.*

**M6 — Core features.** Output changes on purpose: goldens move to committed PNGs here (H5).
- M6.1 label model: `SEPARATE` with rotation and `onPrepared`, `IN_TILE` with a tile margin (D14).
- M6.2 `HILLSHADE` anchor: theme `<hillshading>` element, else after features (D13, D16); re-capture its position, dropped in M5.4a (D30).
- M6.3 pull-based `FeatureQuery` returning a symbol bitmap, for LoPoints.

**M7 — Performance** (D21). Each change measured against the M2.7 baseline.
- M7.1 `render/skia` via Skiko (Q15), so desktop numbers track Android.
- M7.2 lock contention: per-draw `synchronized(paint)`, `Rule` subtree lock.
- M7.3 allocations: `RenderContext` lists, `ReadBuffer`, per-way text measuring.
- M7.4 theme parse (~80 ms on the A54, M2.7b): compiled themes cached per `RenderKey`; SVG
  rasterising on first render is not measured yet.

**M8 — Android host API.**
- M8.1 `android/`: `VectorMapDriver`, `VectorMapRenderer`, `LabelPass`, `ThemeLoader` (D23).
- M8.2 style-menu ids, Locus-extended attributes and asset themes kept, so prefs migrate as-is.
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

## Decisions

D1–D23 taken 2026-10-22; D24–D25 2026-10-25; D26–D29 2026-10-26; D30–D31 2026-10-27.

| ID | Decision |
|---|---|
| D1 | ~~Repo `kmp-mapsforge`~~ → one repo for all formats (D10) |
| D2 | Vendored Mapsforge mirrors the upstream module split |
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
| D24 | M5 converts each area in place, in the `legacy/*` modules under `org.mapsforge.*`; M5.7 renames to `com.asamm.*` and moves the modules |
| D25 | The M5 soak is `dev/bench/android` `SoakTest`: driver plus viewer engine, pixel check of every `SEPARATE` render |
| D26 | M5 PRs from M5.1 merge into `feature/m5`; one PR to `main` after M5.7 and the owner's review |
| D27 | From M5.2 the device soak runs on the dev AVD `VectorMaps_Soak_API36`; the A54 serves other work |
| D28 | `DisplayModel` scales are per model from M5.2, the statics only seed new models, so the shared golden harness and Locus still compile; a model created before a static is set keeps the old value (Locus, see M8 must); each pixel-relevant `Parameters` field moves to per-renderer config with its area (M5.4, M5.6; D29); `MAXIMUM_BUFFER_SIZE`, `VALIDATE_COORDINATES` and the view flags stay global, as do `ANDROID_32BIT_COLOR` (the host's tile format until the host-bitmap target) and `AndroidParameters.ANDROID_LIBHWUI_FIX` (a per-device workaround) |
| D29 | The Paint caches (F1–F3) are bounded in M5.6, in Kotlin: a window of 4 zoom levels, and each render job pins its zoom so a drawing tile never sees an unscaled Paint; where the window lives is M5.6's call. `ANTI_ALIASING` per renderer moves to M5.6 too, as on Android it is set on each theme Paint; on AWT the canvas reads it (`AwtCanvas`, set from `CanvasRasterer`), changed in the same step |
| D30 | M5.4 removes the unused hillshading code before converting the renderer, rather than converting it first; the renderer constructors keep their hills parameter as an ignored `Object`, so the golden harness shared with upstream still compiles; M6.2 re-captures the `<hillshading>` layer, level and `cat` in `RenderThemeHandler`, dropped here |
| D31 | The M5 soak compares each `SEPARATE` render's label store entry with a serial render's: kind, text, priority, position, clash box, visibility, standalone symbol size (way-label text through `toString`, as its field is private; the display mode itself is protected in Mapsforge); the soak fails if no render had labels. Not compared: `IN_TILE` labels (render order, M6.1), placement across tiles (the label pass, M6.1), fonts, colours and symbol pixels |

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
| Q17 | M5.3b first soak: the test process died ~1 min into `IN_TILE`, no trace; conversion or emulator? Later soaks stream logcat until it is closed. | M5 → `main` (D26) |
| Q18 | F1: the Paints' native heap explains ~15–22 MB of the native +93–170 MB zoom growth (M5.3 fixes); what holds the rest? A phone zoom soak after M5.6 shows how much the cache bound removes. | M5 → `main` (D26) |
