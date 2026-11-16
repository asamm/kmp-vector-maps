# Roadmap archive: M7 and M10

The step tables of M7 and M10, with the questions and carried points they settled, moved
unchanged from [ROADMAP.md](../ROADMAP.md) on 2026-11-16 (at `34c4040`); frozen. Status,
decisions and open points live in the roadmap; the PRs named here hold the rest. Benchmarks are in
[performance.md](../performance.md), the MVT engine in [mvt.md](../mvt.md).

## M7 — Performance

Each change measured against the baselines in [performance.md](../performance.md) (D21).

| Step | Content | Verify |
|---|---|---|
| M7.0 | Re-run JMH and the A54 benchmarks on `main` (Q20, P4) | no regression against the baselines ✅ 2026-11-09 |
| M7.1 | `render/skia` via Skiko, so desktop numbers track Android | ⏸ the desktop stays AWT (Q15, Q5) |
| M7.2a | Tests that fail without the matching-cache or the Paint lock (P7) | each fails with its lock removed ✅ PR #47 |
| M7.2b | Lock contention: per-draw `synchronized(paint)`, `Rule` subtree lock, `PathText` (C14) | 8-thread block −27 to −32 % JVM, −15 to −29 % A54 ✅ PR #48 |
| M7.3 | Allocations: `RenderContext` lists, `ReadBuffer`, per-way text measuring (C15) | allocated per render −14 to −32 % JVM ✅ PR #49 |
| M7.4 | Theme parse: one compiled theme per style, shared by its renderers (D40) | a second renderer does not parse ✅ PR #50 |
| M7.5 | Way geometry in `DoubleArray`s from the reader to the path (C16) | allocated per render −7 to −32 % JVM, A54 objects −10 to −53 %; render time level ✅ PR #51 |
| M7.6a | Garmin cleanup: dead code, idiomatic Kotlin | golden unchanged, allocation level ✅ PR #52 |
| M7.6b | Mapsforge dead code, `@Jvm*` annotations (D41, C17) | goldens unchanged ✅ PR #52 |
| M7.6c | Mapsforge idioms outside hot paths (D41) | goldens unchanged, allocation within ±0.2 % ✅ PR #52 |

Left after M7, each measured before it is changed:

- `Caption` keeps its lock over caption lookup and text layout (C14), still among the blocked sites
  of the 8-thread profile.
- The POI matching cache shares the way cache's lock but has no test of its own (M7.2a).
- SVG rasterising on first render is not measured; a theme parse is ~45 % XML tokenising (M7.4).
- Fewer allocations did not make single tiles faster (M7.5); the 512 px tile bitmap (1 MB) is the
  host's.

## M10 — Mapbox Vector Tiles

Asamm's tiles and MapLibre styles, online and offline, one engine ([mvt.md](../mvt.md)). PRs go into
the umbrella `feature/m10-mvt`, which merges into `main` when M10 is done.

| Step | Content | Verify |
|---|---|---|
| M10.0 | Desktop spike: decoder, style subset, renderer for every layer type but symbols, MapLibre reference renders, golden | goldens within mean 4.0 / 6 % of MapLibre ✅ PR #53 |
| M10.1 | Symbol layers: icons, text, along-line placement and collision, through the label pass | label goldens with the bundled font; MapLibre `labels` renders compared, reported ✅ PR #54 |
| M10.2 | Driver behind `core/` (PMTiles files, the host's tile sources for online maps, `style.json` with its sprite), desktop viewer; MBTiles waits for V1; `MvtLabelPass` built by the driver only | goldens through the driver on a PMTiles fixture; driver and viewer tests ✅ PR #55 |
| M10.2b | Online maps in the desktop viewer for UI testing: a TileJSON file (the server's `planet.json`) opens through `openSource` with a viewer-only HTTP source and disk cache | source test on cached tiles; a live tile from the Asamm server ✅ PR #56 |
| M10.3a | Engine performance on the desktop: JMH `MvtBench`; geometry decoded once per feature, features outside an overzoomed tile skipped; feature-constant paint evaluated once per layer; one read and decode per data tile and PMTiles directory under concurrent misses; directories as `LongArray` columns, cached by entries; fewer decoded tiles kept (12) | JMH time and memory before/after; goldens byte-identical ✅ PR #57 |
| M10.3b | `RenderConfig.deviceScale` (style pixels times the tile size over 512, as MapLibre's), `textScale` on text and halos, `language` reading `name:xx` for `name` in text fields | engine and driver tests; goldens at scale 1 unchanged ✅ PR #58 |
| M10.3c | Android target (`formats/mvt/android`: `AndroidMvtGraphics` over `Canvas`, platform text, sprite size guard), a reusable label-pass target (`MvtRenderTarget.clear()`, both viewers), Android viewer opens `.pmtiles` | `dev/mvt/golden-android` (Robolectric native graphics) within the JVM golden's limits of MapLibre ✅ PR #59 |
| M10.3d | A54 benchmarks (`androidx.benchmark`); LoMaps Czech MBTiles converted to PMTiles (`dev/mvt/mbtiles2pmtiles.py`, local only) for JVM and A54 runs at Prague; decoded tiles kept only above the data zoom, within a byte budget | baselines in performance.md; directory cache on a planet file in P17 ✅ PR #60 |
| M10.3e | Render costs of the A54 profile: filters evaluated only on the features their selected property admits (per-render index), `in` and `match` by hash, exact-size gzip inflate and decode arrays; draw batching, vertex thinning and lazy layer decode measured and dropped | goldens unchanged; A54 and JVM before/after in performance.md ✅ PR #61 |
| M10.3f | Spike leftovers (mvt.md Spike and Symbols): a render result telling a cancelled tile from an empty one, fill patterns scaled with the tile size and `deviceScale`, the `@2x` sprite decoded on first use above a style pixel per screen pixel, expression details as MapLibre, each text shaped once per tile layout | goldens unchanged; engine and driver tests ✅ PR #62 |
| M10.4 | Label robustness and spec coverage (mvt.md Spec coverage): the layout stops when a render is cancelled; a label golden of four neighbouring tiles at their corner with a placement dump; `text-keep-upright`, `text-justify`, `text-opacity`, `icon-anchor`, `icon-opacity`, `circle-opacity`, `circle-stroke-opacity`, `fill-outline-color`; legacy `{"stops"}` functions and the missing simple expressions; the Android viewer draws MVT in MapLibre's sizes (512 × density px tiles, `deviceScale` 1) | corner golden and dump, label goldens nearer MapLibre; engine tests per property; A54 viewer check ✅ PR #63 |
| M10.5 | `text-rotate` (a point text's box about its anchor, each glyph of a path text about its centre, so the `hike_bike` contour labels read as in MapLibre) and `text-halo-blur` (the halo's edge faded in up to 6 rings, as MapLibre's shader) | label goldens regenerated and compared; engine tests ✅ PR #64 |

## Settled questions

| ID | Question | Blocks |
|---|---|---|
| Q5 | SVG outside Android: pre-rasterise at theme build, or Skia SVG? | — (answered 2026-11-09: AndroidSVG 1.4 on Android, as locus-core; svg-salamander on the AWT desktop) |
| Q15 | Desktop backend Skia via Skiko (recommended) or AWT only? | — (answered 2026-11-09: AWT only for now; the desktop serves tests and benchmarks, not production; M7.1 ⏸) |
| Q20 | No speed run after M5 and M6: JMH and the A54 `androidx.benchmark` against the baselines ([performance.md](../performance.md)), so M7's deltas exclude the conversion, the C4, C6, C7 fixes and the label pass. | — (answered by M7.0: no regression, performance.md) |

## Settled carried points

| ID | Point | From | To |
|---|---|---|---|
| P3 | `FeatureQuery.point` builds a full `RenderContext`, ~0.7 MB garbage for LoMaps | M6.3 | M7.3 |
| P4 | Garmin tile benchmarks exclude labels since M6.1d; M4.3c is not like for like | M6.1d | M7.0 |
| P7 | No test catches a dropped Paint or matching-cache lock (M2.3): add one before M7.2b | M2.3 | M7.2a |
