# Architecture

Target design: one driver interface every vector format implements, one render pipeline, an
Android host API shaped for Locus, one viewer. Draft; `dev/bench/` exists since M2.7, the core
interfaces since M3.1, the rest comes later ([ROADMAP](ROADMAP.md)).

**Author:** Menion · **Started:** 2026-10-22 · **Revised:** 2026-10-22, after the Locus integration
analysis; 2026-10-24, driver table aligned with the M3.1 core

## Platforms and layers

Android is the product (Locus Map); JVM desktop runs benchmarks, the viewer and tools; no iOS (D22).

| Layer | Path | Content | Licence |
|---|---|---|---|
| Core | `core/` | driver interface, styles, tiles, `RenderTarget`, anchors, label placement | Apache-2.0 |
| Backends | `render/{android,skia,awt}` | Android `Canvas`; Skia via Skiko (desktop); AWT for upstream parity only | Apache-2.0 |
| Host API | `android/` | Locus-facing facade on `android.graphics` types | Apache-2.0 |
| Formats | `formats/<format>/` | one driver each, plus its golden set | per format |
| Tools | `viewer/` (M3.3), `dev/bench/` | written once for every driver | Apache-2.0 |

- Libraries are **Kotlin/JVM**: `java.*` allowed, `android.*` only in Android modules. KMP only
  for the shared Compose viewer UI.
- A format depends on `:core` only. The core is written fresh: kmp-garmin-img's `RenderTarget`
  and `LabelLayout` carry LGPL-2.1 headers and are references, not sources.

## Driver interface

| Type | Role |
|---|---|
| `MapFormatDriver` | map and style extensions; `probe(file)` → `MapInfo` or null; `open(files)` → `MapDataset`; `loadStyle(file)` → `MapStyle` |
| `MapFormats` | registry; probes the drivers claiming the file's extension, first success wins |
| `MapDataset` | one or more files: Mapsforge multi-map, IMG + TYP; `addMap` at runtime, then hosts recreate renderers; creates renderers |
| `MapStyle` | style source and menu: Mapsforge theme, Garmin TYP, MVT style; compiled per renderer |
| `StyleMenu` | layers, overlays, titles; **keeps Mapsforge ids** for Locus prefs; `StyleSelection` = layer + overlays |
| `MapRenderer` | per (dataset, style, selection, config); `render(tile, target, isCancelled)`, hooks from M6 |
| `RenderConfig` | language, device scale, text scale, label mode, tile size |
| `RenderKey` | hashable (style, selection, config) for host tile caches |
| `FeatureQuery` | optional pull API: tags at a zoom → resolved symbol (LoPoints) |

- **Threads:** a dataset is thread-safe; a renderer declares `maxParallelism` and bounds itself.
- **Cancellation** is checked before drawing and, where the format allows, between draw-list
  steps; it never leaves label state half-filled.
- **No globals:** scales and tile size travel in each renderer's `RenderConfig` (Mapsforge
  `DisplayModel`, `Parameters`).

## Android host API

Mirrors Locus' seam `MapSourceRenderer.renderTile(TileRequest): TileContent`, so TileManager,
caches, shading and screen drawing stay as they are (D23).

```kotlin
interface VectorMapRenderer : Closeable {
    fun addMap(spec: MapFileSpec): Boolean
    fun renderTile(request: TileRenderRequest, isCancelled: () -> Boolean): TileRenderResult
    fun labelPass(): LabelPass?          // null when labels are baked into tiles
}
sealed interface TileRenderResult {      // Image: mutable Bitmap, owned by the caller
    class Image(val bitmap: Bitmap, val singleColor: Boolean) : TileRenderResult
    object Empty : TileRenderResult; object Cancelled : TileRenderResult
}
interface LabelPass {                    // replaces Locus' MapsForgeV4LabelRenderer
    fun prepare(view: LabelViewport)     // async, cancels the previous run
    fun draw(canvas: Canvas, view: LabelViewport)
    var onPrepared: (() -> Unit)?
}
```

- The bitmap is mutable because Locus shades finished tiles (`UtilsShading.shadeTile`).
- `ThemeLoader` parses asset or directory themes once, off the UI thread, with the style-menu
  selection; `VectorMapDriver.probe` replaces Locus' own `.map` header sniffing.

## Draw list and anchors

No fixed stages: a renderer walks its style's ordered draw list and calls the host at named
anchors, each with a per-format default.

| Anchor | Mapsforge | Garmin IMG | MVT |
|---|---|---|---|
| `HILLSHADE` | theme `<hillshading>` (layer + level); else after all features | after polygons | style layer |
| `LABELS` | label pass | label layout | symbol layers |

- The hillshade hook gets geo bounds and pixel scale and draws with alpha and blend mode; the host
  prefetches DEM data.

## Labels

- A `LabelCandidate` has a stable identity, world coordinates, priority, **measured** collision
  geometry, a neutral text style and a kind: point, line symbol, path text.
- `SEPARATE`: tiles carry no labels; candidates are placed per screen, rotation-aware and
  upright — Locus' grid collision moved into the library.
- `IN_TILE`: placed over the tile plus a one-tile margin, independent of render order; replaces
  Mapsforge `TileDependencies` and Garmin's per-render `LabelLayout`.

## Golden strategy

| Layer | Gate | When |
|---|---|---|
| Differential pixels | vendored vs upstream 0.30.0, same host | ✅ M0 |
| Android pixels | Robolectric native graphics, vendored vs upstream | ✅ M2.4 |
| Text dumps | reader output, label store, `RenderCallback`, sorted | ✅ M2.2, M2.5 |
| Committed pixels | exact in a pinned container, tolerant outside | M6, once output changes |

- An override set per [upstream.md](upstream.md) deviation keeps one intentional change from
  dropping the whole corpus off the differential test.

## Notes

- `RenderTarget` is low-level (paths, paint handles, text measuring, clip, transforms, blend,
  shaders) and wraps a host-supplied canvas or bitmap; Garmin's two-pass borders and patterns
  become a small painter on top. Until a driver draws through it (M4.2), it only carries the size;
  each driver defines target types over its own graphics (Mapsforge: `TileBitmapTarget`, which
  receives the bitmap Mapsforge allocates until M5), and the host hands the driver its backend
  when it builds the registry.
- Paint caching belongs to the renderer, not to a zoom level (locus-core F2/F3).
