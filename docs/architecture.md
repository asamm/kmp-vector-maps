# Architecture

Target shape of the multi-format repo: one driver interface every vector format implements, one
render pipeline, one viewer. Modelled on GDAL's driver registry. Draft; none of the interface
exists yet (milestone M2).

**Author:** Menion · **Started:** 2026-10-22

## Layers

| Layer | Path (target) | Content |
|---|---|---|
| Core | `core/` (KMP) | driver interface, tile model, projection, `RenderTarget`, pipeline stages, label placement |
| Backends | `render/{android,awt}` | `RenderTarget` on `android.graphics.Canvas` / `java.awt.Graphics2D` |
| Formats | `formats/{mapsforge,garmin-img}`, later `formats/mvt` | one driver each, plus its golden set |
| Tools | `viewer/` (Compose MP: desktop + Android), `bench/` | written once, work for every driver |

- A format depends on `:core` only; the viewer and tools depend on `:core` and pick drivers from the
  registry.
- Format licences stay per module (Garmin LGPL-2.1, Mapsforge LGPL-3.0).

## Today

| Path | State | Becomes |
|---|---|---|
| `formats/mapsforge/legacy/` | vendored Mapsforge 0.30.0 Java, unchanged | deleted after the Kotlin conversion (M5) |
| `formats/mapsforge/golden`, `golden-upstream` | pixel-exact check against upstream | the format's golden set, on committed PNGs once output changes on purpose |
| `viewer/{common,desktop,android}` | two Mapsforge-only viewers, shared viewport maths | one Compose MP viewer over the registry (M2) |
| `core/`, `render/`, `bench/` | not created | M2 onwards |
| Garmin IMG | separate repo `kmp-garmin-img` | `formats/garmin-img/` (M3) |

## Driver interface

| Type | Role |
|---|---|
| `MapFormatDriver` | id, name, file extensions; `probe(source)` → confidence; `open(source)` → `MapDataset` |
| `MapFormats` | registry; `open(source)` asks every registered driver to probe, takes the best |
| `MapDataset` | bounds, zoom range, languages, attribution, available styles; `createRenderer(style, config)` |
| `MapRenderer` | `render(request, target, hooks)` for one tile; thread-safe, one instance per style |
| `TileRequest` | tile x/y/zoom, tile size, scale, text scale, language, label mode |
| `FeatureQuery` | optional capability: features in a bbox with their resolved style (LoPoints today) |

## Render pipeline

Every renderer draws in fixed stages, so a host can insert its own layer between two of them.

| Stage | Content | Host hook after it |
|---|---|---|
| S1 `BACKGROUND` | map background, sea, land | — |
| S2 `AREAS` | land cover, water, buildings | **hillshading** (Locus) |
| S3 `LINES` | roads, paths, borders | — |
| S4 `SYMBOLS` | icons, line symbols | — |
| S5 `LABELS` | captions, path text | — |

- Label mode per request: `IN_TILE` (labels baked into the tile), `SEPARATE` (tile without labels,
  label candidates returned), `NONE`.
- `SEPARATE` is what Locus uses today for map rotation: tiles render unrotated, labels are placed
  and drawn upright in screen space by
  [`MapsForgeV4LabelRenderer`](https://github.com/asamm/locus-core/blob/development/libraries/libLocusCore/src/main/java/com/asamm/locus/maps/sources/renderers/MapsForgeV4LabelRenderer.kt).
  That placement moves into `:core`, shared by all formats.
- Mapsforge themes order instructions by rule, not by kind, so the theme places a hillshading marker
  where the hook runs; without one, the hook runs after all area fills ([ROADMAP](ROADMAP.md) D16).

## Sources

- `RenderTarget` starts from `kmp-garmin-img`'s
  [`RenderTarget`](https://github.com/asamm/kmp-garmin-img/blob/main/lib/src/commonMain/kotlin/com/asamm/garminimg/render/RenderTarget.kt)
  and Mapsforge's `GraphicFactory` / `Canvas`; it needs paint caching owned by the renderer, not
  per zoom level (locus-core F2/F3).
- Until the Kotlin conversion, the Mapsforge driver adapts the vendored Java renderer.
