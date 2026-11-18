# Roadmap

Vector map rendering for Locus Map on Android — Mapsforge, Garmin IMG and Mapbox Vector Tiles —
behind one driver interface, with desktop JVM for benchmarks and tools.

**Author:** Menion · **Started:** 2026-10-22 · **Trigger:** locus-core plan
[`mapsforge_render_extraction.md`](https://github.com/asamm/locus-core/blob/development/docs/plans/mapsforge_render_extraction.md)
(findings F1–F12, hazards R1–R7) · **Design:** [architecture.md](architecture.md) ·
**Benchmarks:** [performance.md](performance.md)

**Status: M0–M7, M10 and M11 done (PRs #1–#69, M11 merged as `be74335` on 2026-11-18), then a
Mapsforge and Garmin review (2026-11-18, Garmin up to 69 % faster per tile on the A54); M7.1 ⏸,
the desktop stays AWT. M8 and M9 ⏸ since 2026-11-12, while locus-core reworks the libMapCore plan
(locus-core `docs/libraries/libMapCore/plan/`) around this library.** Next action: M8.0 (logging
through kmp-logger) when the libMapCore plan says so; M8 and M9 bring all three formats into
Locus; MBTiles waits for V1 ([mvt.md](mvt.md)).

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
| M7 | Performance and Skia desktop backend | L | ✅ PRs #47–#52, `6155c9d`; M7.1 ⏸ (Q15) |
| M8 | Android host API, proven on a throwaway Locus branch | M | ⏸ libMapCore plan |
| M9 | Locus integration | M | ⏸ libMapCore plan; needs Q14, Q16 |
| M10 | Mapbox Vector Tiles, Asamm tiles and styles online and offline ([mvt.md](mvt.md)) | L | ✅ PRs #53–#65, `34c4040` |
| M11 | MapLibre parity sweep: thousands of tiles against MapLibre Native, fixes by impact ([mvt.md](mvt.md)) | M | ✅ PRs #66–#69, `be74335` |

## Done

One row per step; the as-built notes, measurements and soak logs are in
[archive/roadmap-m0-m6.md](archive/roadmap-m0-m6.md), the M7 and M10 step tables in
[archive/roadmap-m7-m10.md](archive/roadmap-m7-m10.md), M11's in
[archive/roadmap-m11.md](archive/roadmap-m11.md), the full story in each PR.

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
| M7.0–M7.6 | No regression after M5–M6; lock contention, allocations, one compiled theme per style (D40), way geometry in arrays, dead code and idioms (D41); Skia desktop ⏸ | ✅ PRs #47–#52 |
| M10.0–M10.2b | MVT spike against MapLibre GL JS, symbols through the label pass, driver (PMTiles, host sources, `style.json`), online maps in the desktop viewer | ✅ PRs #53–#56 |
| M10.3a–f | MVT performance on the JVM and the A54, render settings, Android target and viewer, spike leftovers ([performance.md](performance.md)) | ✅ PRs #57–#62 |
| M10.4–M10.5 | Label robustness, spec coverage, MapLibre sizes on Android, `text-rotate`, `text-halo-blur` | ✅ PRs #63–#64 |
| M10 | Umbrella `feature/m10-mvt` to `main`, with the review's hardening | ✅ PR #65 |
| M11.1–M11.4 | MapLibre parity sweep against MapLibre Native (`dev/mvt/sweep`): fill outline, label priority, line breaking, anchors and baseline, `allow-overlap` blocking, Native's text size for line anchors, road shields; 13.9k subset labels 2.14 → 1.62 %, turned 2.97 → 2.49 %, tiles 0.29 → 0.15 % | ✅ PRs #66–#69 |
| After M11 | Mapsforge and Garmin review: Mapsforge pixel-identical to upstream on 1,463 Czech tiles, A54 at baseline; Garmin skips off-tile objects before their labels, A54 z17 63 → 19 ms ([performance.md](performance.md)); sweep tooling for both; a scripted Garmin reference parked (Q21) | 🔧 |

### Carried forward

The main points recorded but not settled, each routed to the milestone that takes it (— for
none yet); the archives have the full notes and the settled ones.

| ID | Point | From | To |
|---|---|---|---|
| P1 | The Mapsforge pass `draw` is right on AWT only: Android captions turn the canvas back | M6.1b | M8 |
| P2 | The label pass describes every stored label on each prepare; measure before caching | M6.1b | — |
| P5 | Garmin on the A54: reads ~6× the JVM; an allocation per pattern, dash, label, icon; off-tile reads cut after M11 | M4.3c | — |
| P15 | Garmin label halos draw each text nine times, on both targets | M4.3c | — |
| P6 | Garmin: boxed label and coordinate buffers; sub-division trees kept after add | M4.3c | — |
| P8 | The menu's per-instruction category filter is not covered by the corpus | M2.1 | — |
| P16 | Corpus gaps: explicit way label positions, water tiles, `readLabels`, `readPoiData` | M2.5 | — |
| P9 | `PaintCostTest` never ran on the A54; F1's native growth mostly unexplained (Q18) | M5.3 | — |
| P10 | The Android viewer redraws the Garmin label bitmap every pan frame, and the MVT one (PR #59) | PR #42 | M8 |
| P11 | A Garmin road name over a sharp turn draws its letters around the bend | M6.1d | — |
| P12 | Garmin: an archive's first TYP styles all its maps; maps across the antimeridian rejected | M4.3b | — |
| P13 | An external `.typ`'s night style is not offered by the viewers | M4.4 | M8 |
| P14 | The desktop viewer builds renderers on its UI thread | M4.2b | — |
| P17 | The PMTiles directory cache (100,000 entries, ~3 MB of columns) not measured on a planet file | M10.3d | — |
| P18 | MVT blurred halos draw each text up to 7 times (6 rings from a density of ~1.43), a turned path text a character at a time; not measured on the A54 | M10.5 | — |
| P19 | Mapsforge after M7: `Caption` holds its lock over lookup and layout (C14); the POI matching cache has no lock test; SVG rasterising on first render unmeasured | M7 | — |

- P1: M8 settles it with an Android golden of the pass.
- P10: the fix is an `AndroidRenderTarget` over an external `Canvas`, an engine API change.

## M8 — Android host API

Also takes P1, P10 and P13 (Carried forward).

- M8.0: logging through [kmp-logger](https://github.com/asamm/kmp-logger), as
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

Against the locus-core libMapCore plan, which moves `maps/` framework code out of
libLocusCore (2026-11-10):

- M8.0–M8.2 need nothing from locus-core: the host API has no Locus types, only the seam's shape.
- M8.1 is shaped after its N8 (its D18): `MapSourceRenderer` takes a plain `TileRenderRequest`
  with an `isCancelled` callback and returns a sealed render result, both in libMapCore. Its
  result adds `drawnDark`, `Loading` and `Error`; take them into `TileRenderResult` or map them in
  the adapter.
- M8.3 and I1 write against that seam, not against `TileRequest` / `TileContent`, which its P4e
  moves later without touching the adapter.
- Its N9 puts the screen-space labels behind an interface mirroring `LabelPass`, so
  `MapLayerLocal` no longer reaches `MapsForgeV4LabelRenderer` through `MapSourceMapsForgeV4`;
  M8.3 wraps it. Its P5 draw list later gives the label pass its place.
- Its N10 adds the `MapSourceVector` base that I4 switches the checks to. I4 removes
  `MapSourceMapsForgeV4` checks from `MapContent` and `MapLayerLocal`, both in its move set (P4f,
  P4e); I2 and I5 shrink what it moves.
- M9 lands before its N6 and P4e, never alongside: both rewrite `sources/` and `tiles/` (its H7).
- I6 (Q16) and its N6 both change the tile requests and the tile cache.
- D32 (GPU renderer, bucket delivery) is its P7 and the "later GL backend" behind its D16 seams.

## Decisions

D1–D23 taken 2026-10-22; D24–D25 2026-10-25; D26–D29 2026-10-26; D30–D31 2026-10-27; D32–D33 2026-11-02; D34–D35 2026-11-03; D36–D38 2026-11-04; D39 2026-11-05; D40 2026-11-09; D41 2026-11-10.

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
| D20 | 512 px tiles in goldens, benchmarks, viewers, but MVT in the Android viewer at 512 × density px with `deviceScale` 1 (Menion, 2026-11-15, M10.4); Locus per Q16 |
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
| D40 | A Mapsforge style keeps one compiled theme, for the settings of its latest renderer, shared by every renderer with those settings, as Locus keeps one active theme; it is kept until the style is closed or the settings change, and a replaced theme lives until its last renderer closes; no other theme is cached. `MapStyle.close()` releases it, and the host closes a style it replaces (M7.4) |
| D41 | Vendored code may leave upstream's form: M7.6 removes what no module of this repo uses (the golden harness, bench and viewers included) and turns the rest into idiomatic Kotlin; hot paths (reader, renderer, label placement, theme matching) keep allocation-free index loops unless a measurement shows no cost. Upstream tags are still followed (D36), ported by meaning, not line by line; upstream.md records each removal. Supersedes D28 for the view flags, which are removed |

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
| H5 | Committed PNGs break across OS, JDK, fonts | committed PNGs only for Mapsforge labels (AWT in the bundled DejaVu Sans, Android in Robolectric's fonts) and Garmin (DejaVu Sans), ≤ 0.2 % everywhere, and MVT labels (DejaVu Sans, ≤ 0.5 %); a tile without an exact dump can lose a small label within it (D37, M6.0b gaps) |
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

Answered questions are in the archives ([M0–M6](archive/roadmap-m0-m6.md),
[M7 and M10](archive/roadmap-m7-m10.md)).

| ID | Question | Blocks |
|---|---|---|
| Q14 | Does the Mapsforge README waiver cover pre-2018 contributors? (locus-core Q1) | M9 |
| Q16 | Locus tiles: keep 256 px × density, or fixed 512 px with a zoom/scale change? MVT counts `deviceScale` per 512 px tile (M10.3b): 512 × density px tiles with `deviceScale` 1 draw MapLibre's sizes (the Android viewer since M10.4), 256 × density px tiles with 1 draw half of them | M9 |
| Q17 | M5.3b first soak: the test process died ~1 min into `IN_TILE`, no trace; conversion or emulator? Later soaks stream logcat until it is closed; none recurred through the M6.1b and M6.1c A54 soaks. | — |
| Q18 | F1: the Paints' native heap explains ~15–22 MB of the native +93–170 MB zoom growth (M5.3 fixes); what holds the rest? A phone zoom soak after M5.6 shows how much the cache bound removes. | — |
| Q19 | C7's matching-cache keys hold copied tag lists (house numbers, `ele`, `ref`); upstream keyed on an `Integer`. How much heap do both caches retain on the corpus, against C7's Paint saving? | — |
| Q21 | Garmin has no reference renderer for sweeps: build a headless GPXSee tool when better Garmin rendering is in demand? Plan in [garmin-img/visual-reference](garmin-img/visual-reference/README.md#scripted-reference-not-built) | — |
