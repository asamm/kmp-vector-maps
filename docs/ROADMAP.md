# Roadmap

Vector map rendering for Locus Map on Android — Mapsforge first, Garmin IMG next — behind one
driver interface, with desktop JVM for benchmarks and tools.

**Author:** Menion · **Started:** 2026-10-22 · **Trigger:** locus-core plan
[`mapsforge_render_extraction.md`](https://github.com/asamm/locus-core/blob/development/docs/plans/mapsforge_render_extraction.md)
(findings F1–F12, hazards R1–R7) · **Design:** [architecture.md](architecture.md) ·
**Benchmarks:** [performance.md](performance.md)

**Status: M0–M6 done (PRs #1–#45, the last merged as `41f6968` on 2026-11-06); M7.0 done on
2026-11-09: no regression ([performance.md](performance.md)); M7.2 done (PRs #47, #48); M7.1 ⏸,
the desktop stays AWT (Q15).** Next action: M7.4, theme parse, after M7.3's allocation changes.

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
| M4 | Garmin IMG joins | M | ✅ PRs #27–#34, `e3c3aef` |
| M5 | Mapsforge to Kotlin, hot-spot fixes per area, `legacy/` deleted | L | ✅ PRs #6–#26, `b22e989` |
| M6 | Core features: label model, hillshade anchor, feature query | M | ✅ PRs #36–#45, `41f6968` |
| M7 | Performance and Skia desktop backend | L | 🔧 M7.1 ⏸ (Q15) |
| M8 | Android host API, proven on a throwaway Locus branch | M | ⬜ |
| M9 | Locus integration | M | ⬜ needs Q14, Q16 |
| M10 | Mapbox Vector Tiles | L | ⏸ |

## Done: M0–M6

One row per step; the as-built notes, measurements and soak logs are in
[archive/roadmap-m0-m6.md](archive/roadmap-m0-m6.md), the full story in each PR.

| Step | Content | Done |
|---|---|---|
| M0 | 177 vendored files, golden test vendored vs upstream from JitPack | ✅ `d41acf5` |
| M1 | Upstream drift check, pinned CI and actions, licence per part | ✅ PR #1 |
| M2.1–M2.6 | Corpus at 512 px and 3 scales, Locus primary mode, parallel block, Android pixels, dumps | ✅ PR #2 |
| M2.7, M2.7b | JMH and A54 baselines ([performance.md](performance.md)) | ✅ PR #2 |
| M3.1 | `:core` interfaces and value types | ✅ PR #3 |
| M3.2 | Mapsforge driver over the vendored Java | ✅ PR #4 |
| M3.3 | Shared viewer, desktop and Android, registry-driven tile engine | ✅ PR #5 |
| M4.1 | Garmin IMG imported with history after an audit, LGPL-2.1-or-later (D33) | ✅ PR #27 |
| M4.2a–c | Garmin driver, both viewers, golden through the driver in a bundled font | ✅ PRs #28–#30 |
| M4.3a–c | Parallel renders, multi-map files, benchmarks ([performance.md](performance.md)) | ✅ PRs #31–#33 |
| M4.4 | Measured label bounds, TYP font sizes, TYP night style, DRAWORDER | ✅ PR #34 |
| After M4 | Garmin files with damaged maps open with the readable ones (D35) | ✅ PR #35 |
| M5.0, M5.5a | Device soak, then with the label store check (D25, D31) | ✅ PRs #6, #19 |
| M5.1–M5.7a | Mapsforge to Kotlin area by area, each with its fixes (C1–C7) | ✅ PRs #7–#18, #20–#24 |
| M5.7b | `legacy/` deleted, packages `com.asamm.vectormaps.mapsforge.*`, 3 modules (K64) | ✅ PR #25 |
| M5 | Umbrella `feature/m5` to `main` | ✅ PR #26 |
| After M5 | Synced to upstream Mapsforge 0.31.0, nothing to port; sync tooling (D36) | ✅ `644ac63` |
| M6.0a–b | Mapsforge goldens split: drawing vs upstream, labels in a bundled font (D37, C8) | ✅ PRs #36–#37 |
| M6.4 | Upstream fixes A8, A9 (C9) | ✅ PR #38 |
| M6.1a–e | Core label model, label pass for both formats, labels only through the pass (D39) | ✅ PRs #39–#43 |
| M6.2 | `HILLSHADE` anchor, host hook on the platform canvas (C12) | ✅ PR #44 |
| M6.3 | `FeatureQuery` for LoPoints, Mapsforge only (D38, C13) | ✅ PR #45 |

### Carried forward

The main points M0–M6 recorded but did not settle, each routed to the milestone that takes it;
the archive has the full notes.

| ID | Point | From | To |
|---|---|---|---|
| P1 | The Mapsforge pass `draw` is right on AWT only: Android captions turn the canvas back | M6.1b | M8 |
| P2 | The label pass describes every stored label on each prepare; measure before caching | M6.1b | M7 |
| P3 | `FeatureQuery.point` builds a full `RenderContext`, ~0.7 MB garbage for LoMaps | M6.3 | M7.3 |
| P4 | Garmin tile benchmarks exclude labels since M6.1d; M4.3c is not like for like | M6.1d | M7.0 |
| P5 | Garmin on the A54: reads ~6× the JVM; an allocation per pattern, dash, label, icon | M4.3c | M7 |
| P15 | Garmin label halos draw each text nine times, on both targets | M4.3c | M7 |
| P6 | Garmin: boxed label and coordinate buffers; sub-division trees kept after add | M4.3c | M7 |
| P7 | No test catches a dropped Paint or matching-cache lock (M2.3): add one before M7.2b | M2.3 | M7.2a |
| P8 | The menu's per-instruction category filter is not covered by the corpus | M2.1 | — |
| P16 | Corpus gaps: explicit way label positions, water tiles, `readLabels`, `readPoiData` | M2.5 | — |
| P9 | `PaintCostTest` never ran on the A54; F1's native growth mostly unexplained (Q18) | M5.3 | M7 |
| P10 | The Android viewer redraws the Garmin label bitmap every pan frame | PR #42 | M8 |
| P11 | A Garmin road name over a sharp turn draws its letters around the bend | M6.1d | — |
| P12 | Garmin: an archive's first TYP styles all its maps; maps across the antimeridian rejected | M4.3b | — |
| P13 | An external `.typ`'s night style is not offered by the viewers | M4.4 | M8 |
| P14 | The desktop viewer builds renderers on its UI thread | M4.2b | — |

- P1: M8 settles it with an Android golden of the pass.
- P10: the fix is an `AndroidRenderTarget` over an external `Canvas`, an engine API change.

## M7 — Performance

Each change measured against the baselines in [performance.md](performance.md) (D21).

| Step | Content | Verify |
|---|---|---|
| M7.0 | Re-run JMH and the A54 benchmarks on `main` (Q20, P4) | new tables beside the baselines ✅ 2026-11-09 |
| M7.1 | `render/skia` via Skiko, so desktop numbers track Android | ⏸ the desktop stays AWT (Q15, Q5) |
| M7.2a | Tests that fail without the matching-cache or the Paint lock (P7) | each fails with its lock removed: 6/6 and 8/8 runs ✅ PR #47 |
| M7.2b | Lock contention: per-draw `synchronized(paint)`, `Rule` subtree lock, `PathText` (C14) | M7.2a's tests stay green; blocks against M7.0: 8 threads −27 to −32 % JVM, −15 to −29 % A54 ✅ PR #48 |
| M7.3 | Allocations: `RenderContext` lists, `ReadBuffer`, per-way text measuring (C15) | allocated per render: JVM −14 to −32 %, z12 −15 to −17 % 🔧 |
| M7.4 | Theme parse (~80 ms on the A54): compiled themes cached per `RenderKey` | first-render time |
| M7.5 | Way geometry in `DoubleArray`s from the reader to the path: no `LatLong` or `Point` per node | allocated per render; render time, idle desktop and A54 |

- M7.2a: `ThemeCachesTest` drives 8 × 25,000 new tag lists through the 8,192-entry way matching
  cache and checks its size, entry order and hash table agree; `SharedPaintRenderTest`
  pattern-fills every closed way and renders a 4×4 block on 8 threads against a serial render.
  The POI matching cache has the same lock and is not tested separately.
- M7.2b: a JFR profile of the 8-thread Czech block found the three locks; the Paint lock stays for
  pattern Paints only. Unlocked text measuring exposed a shared `Rect` in `AndroidPaint`, caught
  by the A54 soak, not by a JVM test (performance.md). `Caption` keeps its lock over caption lookup
  and text layout (still among the blocked sites in the after profile); not narrowed yet.
- M7.3: the lazy `RenderContext` lists also take P3's ~0.7 MB per `FeatureQuery.point`. Left: the
  way nodes (M7.5) and the host's tile bitmap ([performance.md](performance.md)).
- M7.4: SVG rasterising on first render is not measured yet.
- M7.5: today a way node is a `LatLong`, an absolute and a tile-relative `Point` (~84 B on ART,
  ~25–35 % of a render's allocation, M7.3); packed: two doubles per node in the `Way`, two in the
  projected ring, the tile-relative coordinates computed while the path is built. `Way.latLongs`
  stays as a view for the golden dumps; label parts (`LineString`, `WayTextContainer`) keep
  `Point`s, built only for a label that fits. Same arithmetic, so dumps and pixels must stay
  identical.

## M8 — Android host API

Also takes P1, P10 and P13 (Carried forward).

- M8.0, once M7 is done: logging through [kmp-logger](https://github.com/asamm/kmp-logger), as
  locus-core, from JitPack (public): the engine's `java.util.logging`, the Android graphics'
  `android.util.Log` and the drivers' calls; Locus registers its own backend; an upstream.md row.

- M8.1 `android/`: `VectorMapDriver`, `VectorMapRenderer`, `LabelPass`, `ThemeLoader` (D23);
  `TileBitmapTarget` over a host-supplied bitmap.
- M8.2 style-menu ids, Locus-extended attributes and asset themes kept, so prefs migrate as-is;
  the Garmin style prefs from the helpdesk (M4.1 carry-over).
- M8 must: one `AndroidBitmapCache` per opened Garmin map or style, dropped with it; a cache kept
  for the app holds the bitmaps of every TYP opened.
- M8 must: Mapsforge's Android SVG cache keys unsized symbols without the scale, so renderers at
  two device scales share them; key them by scale or clear the cache per scale.
- M8 must, while Locus still renders through Mapsforge directly: its `DISPLAY_MODEL` exists before
  `setDeviceScaleFactor(BASE_DENSITY)`, and models no longer read the static later (C4); set
  `DISPLAY_MODEL.deviceScale = BASE_DENSITY` instead of the `tileSizeMultiple` refresh.
- M8 must: on `addMap`, close and recreate the renderers and clear the tile cache; closing a
  dataset leaves its renderers, and their readers, open.
- M8 must: transparent tiles (a `RenderConfig` option), so a backup map shows through as in Locus.
- M8.3 throwaway locus-core branch wires the adapter end to end (H7).

## M9 — Locus integration

locus-core side.

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

D1–D23 taken 2026-10-22; D24–D25 2026-10-25; D26–D29 2026-10-26; D30–D31 2026-10-27; D32–D33 2026-11-02; D34–D35 2026-11-03; D36–D38 2026-11-04; D39 2026-11-05.

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
| D30 | M5.4 removes the unused hillshading code before converting the renderer, rather than converting it first; the renderer constructors keep their hills parameter as an ignored `Object` (M6.2 types it `HillshadingDrawer?` and draws it, upstream.md C12), so the golden harness shared with upstream still compiles; M6.2 re-captures the `<hillshading>` layer, level and `cat` in `RenderThemeHandler`, dropped here |
| D31 | The M5 soak compares each `SEPARATE` render's label store entry with a serial render's: kind, text, priority, position, clash box, visibility, standalone symbol size (way-label text through `toString`, as its field is private; the display mode itself is protected in Mapsforge); the soak fails if no render had labels. Not compared: `IN_TILE` labels (render order, M6.1), placement across tiles (the label pass, M6.1), fonts, colours and symbol pixels |
| D32 | Long term: our own OpenGL renderer in locus-core; this library moves from bitmap to bucket delivery (tessellated geometry, symbols, label data) and keeps its readers, style engines and label model; no VTM fork, VTM is a reference only. M6–M8 interfaces keep decode, style and draw separable so bucket output can join the bitmap one |
| D33 | Garmin IMG is LGPL-2.1-or-later, as upstream JGarminImgParser (Q13), imported with history into this public repo after an audit (Q12); the kmp-garmin-img headers become LGPL-2.1-or-later. Locus keeps R8 on the Garmin code, a known §6 gap the owner accepts; if raised, the package is excluded from R8 |
| D34 | No host night mode: a night look is a style, as LoMaps' night themes are; a Garmin TYP's own night colours become a night style next to its day one, listed only when the TYP has night data, never day colours inverted (M4.4). |
| D35 | A Garmin file opens with the maps that parse; the others are skipped and listed (`GarminDataset.unreadableMaps`; a read error mid-parse counts as damage, reopening retries), as gmapsupps merged from many tiles carry damaged ones; a file without a readable map, or with overlapping sub-files, is still rejected. Replaces the M4.3b "one unreadable map rejects its whole file". |
| D36 | Upstream Mapsforge is followed tag by tag: each sync ports the changes to vendored files by hand and proves pixel and dump parity with both goldens against the new tag, built from source when JitPack has no jars; CI's golden reference stays on the last tag with jars while that proof passes. Procedure and log in upstream.md |
| D37 | Mapsforge goldens from M6: reading (reader and `RenderCallback` dumps) identical to upstream, except a caption placed against a non-square symbol (C9, A8), which the corpus does not hit; drawing without labels close to upstream, a per-tile share of differing pixels, a tile over the limit accepted only through a committed entry with its C-row; labels (text and the symbols stored with them) against committed goldens, as our label model replaces upstream's placement on purpose. A significant change is confirmed by its entry or golden update in the PR. Replaces "goldens move to committed PNGs" (H5) |
| D38 | `FeatureQuery` is format-neutral in `:core`, implemented by the Mapsforge driver only in M6.3 for LoPoints; Garmin follows when a host needs it |
| D39 | Labels are drawn only by a label pass over label-free tiles; no driver draws them into tiles. Locus' in-tile uses (text rotation off, the world map at low zooms, `MapDataPreviewGenerator`) move to the pass at M8: previews and prints render their tiles, then `LabelPass.prepareNow` and the format's draw. Tiles stay label-free, so equal `RenderKey` gives equal tiles; a tile is not self-contained (an exported or cached tile shows no labels). Supersedes M6.1c's order-independent `IN_TILE`, the in-tile half of M6.1d and the two soak modes of D25 and D31 (the soak is single-mode); Garmin draws into its tiles until M6.1d |

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
| H2 | Vendored code misses upstream changes | per tag: `upstream_diff.py` grouping, hand port, parity with both goldens against the tag (`verify_upstream.py`, D36), deviation tables in [upstream.md](upstream.md) |
| H3 | Interface shaped by Mapsforge alone | Garmin (M4) right after core v0 (M3) |
| H4 | Locus sees nothing until M9 | A54 runs in the shared viewer; I7 fallback flag |
| H5 | Committed PNGs break across OS, JDK, fonts | committed PNGs only for Mapsforge labels (AWT in the bundled DejaVu Sans, Android in Robolectric's fonts) and Garmin (DejaVu Sans), ≤ 0.2 % everywhere; a tile without an exact dump can lose a small label within it (D37, M6.0b gaps) |
| H6 | JitPack outage or rebuild | checksum verification; cache the jars if outages hurt |
| H7 | Host API drifts from Locus' real seam | mirrors `MapSourceRenderer`; M8.3 throwaway wiring |

## Adjacent, out of scope

A1–A6 found in locus-core `development` @ `79226d9`, A7–A10 in upstream Mapsforge 0.30.0; not
fixed here, except A8 and A9, fixed in M6.4 (C9), and A10, fixed in M6.1b (C11), as fixing them
changes output against upstream.

| ID | Finding |
|---|---|
| A1 | `TileCreator` starts a raw `Thread` per tile, up to cores × 2 |
| A2 | Mapsforge `TileBitmap` never released, its reuse pool is dead weight |
| A3 | A running render is never interrupted |
| A4 | No redraw after the label pass prepares (`TODO force redraw`) |
| A5 | `MapContent` tests `is MapSourceMapsForgeV4` in both branches, the second is dead |
| A6 | Theme parse runs synchronously on the caller, dark variant under the pref lock |
| A7 | Equal-priority labels survive in identity-hash order (`DatabaseRenderer`), output varies between runs |
| A8 | `RenderInstruction.computeBoundary` scales the symbol boundary's top by width, not height (Mapsforge 0.30.0, kept in M5.6b, fixed in M6.4) |
| A9 | `Line` with `scale="all"` and no `stroke-dasharray` throws in `scaleStrokeWidth` (Mapsforge 0.30.0, kept in M5.6b, fixed in M6.4) |
| A10 | AWT `AwtCanvas.rotate` hands `Graphics2D.rotate` degrees, which takes radians (Mapsforge 0.30.0, fixed in M6.1b) |

## Open

| ID | Question | Blocks |
|---|---|---|
| Q5 | SVG outside Android: pre-rasterise at theme build, or Skia SVG? | — (answered 2026-11-09: AndroidSVG 1.4 on Android, as locus-core; svg-salamander on the AWT desktop) |
| Q14 | Does the Mapsforge README waiver cover pre-2018 contributors? (locus-core Q1) | M9 |
| Q15 | Desktop backend Skia via Skiko (recommended) or AWT only? | — (answered 2026-11-09: AWT only for now; the desktop serves tests and benchmarks, not production; M7.1 ⏸) |
| Q16 | Locus tiles: keep 256 px × density, or fixed 512 px with a zoom/scale change? | M9 |
| Q17 | M5.3b first soak: the test process died ~1 min into `IN_TILE`, no trace; conversion or emulator? Later soaks stream logcat until it is closed; none recurred through the M6.1b and M6.1c A54 soaks. | M7 |
| Q18 | F1: the Paints' native heap explains ~15–22 MB of the native +93–170 MB zoom growth (M5.3 fixes); what holds the rest? A phone zoom soak after M5.6 shows how much the cache bound removes. | M7 |
| Q19 | C7's matching-cache keys hold copied tag lists (house numbers, `ele`, `ref`); upstream keyed on an `Integer`. How much heap do both caches retain on the corpus, against C7's Paint saving? | M7 |
| Q20 | No speed run after M5 and M6: JMH and the A54 `androidx.benchmark` against the baselines ([performance.md](performance.md)), so M7's deltas exclude the conversion, the C4, C6, C7 fixes and the label pass. | — (answered by M7.0: no regression, performance.md) |
