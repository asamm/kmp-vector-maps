# Vector tiles (MVT): feasibility

Rendering Asamm's own vector tiles with their MapLibre styles in this library, on Android, as a
third format next to Mapsforge and Garmin IMG, is doable as an own Kotlin renderer (size L).

**Author:** Menion · **Analysed:** 2026-11-12 · **Milestone:** M10 (see [ROADMAP.md](ROADMAP.md)) ·
**Status:** spike done 2026-11-12; M10.1 symbols 🔧 2026-11-12, see [Symbols](#symbols)

## Goal

| Requirement | From |
|---|---|
| One engine for online and offline vector maps on Android | Menion, 2026-11-12 |
| One tile source: Asamm tiles online now, and later offline too, replacing the separate Mapsforge and MBTiles offline packages | Menion, 2026-11-12 |
| As close to the web and iOS maps as possible, not 1:1 | Menion, 2026-11-12 |
| Online and offline render the same on one device; small differences between devices are fine | Menion, 2026-11-12 |

## What Asamm serves today

| Part | Fact | Source |
|---|---|---|
| Tiles | One planet tileset: OpenMapTiles 3.16 schema plus Asamm layers (`hiking`, `cycling`, `ski`, `contour`, `lm_transportation`, `outdoor_poi`, …), z0–14, overzoomed by clients to z19–20 | `tiles.locusmap.app/data/planet.json` |
| Generator | Planetiler with the Asamm fork of the OpenMapTiles profile; Asamm layers in `src/main/java/org/openmaptiles/addons/layers/` | [asamm/planetiler-openmaptiles](https://github.com/asamm/planetiler-openmaptiles) |
| Online server | tileserver-gl over PMTiles behind a CDN (`tiles.locusmap.app`); `/data/planet/{z}/{x}/{y}.pbf` and 512 px PNG rasters of the same styles | Asamm server setup |
| Offline (iOS) | MBTiles per Locus Store map, plus a style template whose `mbtiles://`, sprite and glyph placeholders the app fills; elevation stays online | Locus Map iOS |
| Styles | `hike_bike`, `ski` and their dark variants, released for online, offline and tileserver use | Asamm style sources |
| Style content | ~455 layers: 336 line, 71 symbol, 42 fill, hillshade, 1 `color-relief` (bathymetry), circle, background; no 3D, heatmap or terrain | `hike_bike` style JSON |
| Expressions | ~25 of the spec's 88 operators: `get`, `all`, `==`, `in`, `interpolate`, `case`, `step`, `let`/`var`, `index-of`, `slice`, `concat`, `geometry-type`, `has`, `upcase`, arithmetic | `hike_bike` style JSON |
| Text | 4 Noto stacks; the iOS offline glyph download is 131.7 MB | offline release JSON |
| Consumers | Locus Map web (maplibre-gl 6), Locus Map iOS (MapLibre Native); iOS online still reads MapTiler, to be switched to Asamm tiles | both apps |

## Options

| Option | Effort | Verdict |
|---|---|---|
| **A. Own Kotlin renderer** behind the driver interface: MVT decoder, MBTiles and PMTiles readers, an HTTP tile source, style JSON with the expressions the Asamm styles use, fill / line / circle / background on its own `MvtRenderTarget`, symbols through the cross-tile label pass, platform text | L (XL for broad spec parity) | **Recommended** |
| B. MapLibre Native `MapSnapshotter` as a tile rasteriser | M | Rejected: contradicts libMapCore D20 (own GL renderer, no MapLibre); +4–8 MB native code per ABI; a GL context and Looper per worker; no desktop path, no goldens |
| C. Convert each GL style to a Mapsforge-like theme (as VTM's `openmaptiles.xml`) | M per style | Rejected: hand work per style, lossy, two style sources to keep in sync |
| D. GeoTools `gt-mbstyle`, VTM, maplibre-rs | — | Not usable: AWT-only and unsupported; a GL map view with own themes; a proof of concept |

Why A fits:

- The styles are Asamm's, so the supported feature set is a finite list, tested against them.
- Online and offline share one decoder, style engine and renderer; only the tile source differs,
  which gives the same result on one device by construction.
- Platform text (Android Canvas, Java2D) shapes complex scripts that MapLibre glyph PBFs cannot,
  and Android needs no glyph download.
- Hillshade and `color-relief` map to the existing `HILLSHADE` anchor; the host draws elevation.
- MapLibre GL JS renders of the same styles are the goldens' reference (`dev/mvt/reference`), the
  tileserver-gl PNGs a cross-check; not pixel parity.

## Work

| Part | Size |
|---|---|
| MVT 2.1 decoder, MBTiles and PMTiles v3 readers, HTTP source | S |
| Style JSON: sources, layers, zoom ranges, filters, legacy filters and `{stops}` functions | M |
| Expression engine for the operators above, tested on the Asamm styles | M |
| Background, fill (incl. pattern), line (dasharray, offset, pattern), circle on `MvtRenderTarget` | M |
| Symbols: icons from sprites, text, along-line placement, collision, sort keys, through the label pass | L |
| Overzoom from z14, 512 px tile convention, tile-buffer clipping | S |
| Goldens against MapLibre GL JS renders, JMH and A54 benchmarks | M |

## Risks

- Visual drift from GL, mostly symbol placement, dash scaling, patterns and halos.
- Spec churn: MapLibre adds operators and layer types every year; follow only what the styles use.
- The styles may need small Android-only changes (fonts, unsupported properties); keep them in the
  style repo's targets, not in code.

## Open

| ID | Question |
|---|---|
| V1 | Offline package: PMTiles (as the server) or MBTiles (as iOS) for Android? One package for both apps? |
| V2 | Does the offline Mapsforge LoMaps package go once vector tiles cover it? Answered 2026-11-12: no influence here; the Mapsforge driver stays for third-party maps. |
| V3 | Attribution: the tileserver credits OpenMapTiles, but the web app overrides it with a list without it (schema CC-BY); the web's internal 3D-buildings layer names a source `openmaptiles` the Asamm styles do not have. |

## Spike

2026-11-12, M10.0: `formats/mvt/engine` and `awt`, the golden `dev/mvt/golden`, desktop only:

- Decoder, the whole `hike_bike` style (455 layers) and every filter and property evaluate on the
  fixture tiles; only the bathymetry `color-relief` is left to the host.
- Background, fill, pattern fill, line (dash, offset) and circle layers, overzoom from z14.
- Against MapLibre GL JS on the same layers (`dev/mvt/reference`, headless Edge): mean pixel
  difference 0.6–2.6 of 255, 0.2–4.1 % of pixels off by more than 32, on 8 tiles z10–16.
- 7–160 ms per 512 px tile on the JVM, untuned.
- Not yet: the driver, the viewer, MBTiles / PMTiles / HTTP sources.
- Left from its review (M10.3): geometry decoded once per feature and tile, an overzoom
  bounding-box cull, feature-constant properties evaluated once per layer, a tri-state render
  result for cancelling, patterns scaled for non-512 px tiles and `@2x` sprites; expression
  details MapLibre handles differently and the Asamm styles do not use (`string`/`number` type
  assertions, `to-number` of null, negative `slice`, `round` of halves, uint64 ids).

## Symbols

2026-11-12, M10.1: `SymbolLayout` (engine) lays out each tile's symbol layers into labels;
`MvtLabelPass` (`formats/mvt/driver`) places them per screen through the core `LabelPlacer` and
draws them on the target (D39).

| MapLibre behaviour | Here |
|---|---|
| Point at its point, line at its first point, polygon at its pole of inaccessibility | same |
| `line` placement along lines and polygon rings, none at points | same |
| `line` every `symbol-spacing` (spec minimum 1, widened to a longer label) from half a spacing in, else the middle; `line-center` once | same |
| `text-max-angle`: absolute turns within 3/5 of the text size, for every text, not for icons alone | same |
| `line` pieces with text joined first (`mergeLines`), each chain with its first piece's symbol | same |
| Icons on lines turn with them (`icon-rotation-alignment` `auto` = `map`), `icon-offset` with `icon-rotate`, `icon-translate` in the map's frame | same |
| Later layers place first, then lower `symbol-sort-key` | label priority |
| `allow-overlap` | `ALWAYS`, which also never blocks others |
| Text: `text-field`, `-transform`, `-size`, `-font` face, `-max-width` wrapping at spaces and hyphens, `-line-height`, `-letter-spacing`, `-anchor`, `-offset`, `-padding`, halo | same; lines wrap greedily, not balanced |
| Icons: `icon-image`, `-size`, `-offset`, `-rotate`, `-padding`, `icon-translate` | same |
| SDF glyphs from the font server | platform fonts, shaped (joining, bidi base direction) for measuring and drawing; goldens use the bundled DejaVu Sans |

- Each tile keeps the labels whose anchors fall inside it, so a screen sees each anchor once; only
  the segments and polygons meeting the tile are anchored, so a tile above the data zoom does no
  more than its share.
- The golden checks each fixture tile on a screen of its size and on one turned 30°.
- Against MapLibre GL JS with labels (`labels` references, hillshade off): mean 1.6–12.6 of 255,
  2.0–13.7 % of pixels off by more than 32; text in another font dominates, and DejaVu Sans is
  wider than Noto, so some labels lose collisions MapLibre's win. Reported, not checked.
- A malformed size, halo or padding drops or clamps the value; a symbol layer that throws is left
  out whole and listed in `SymbolLayout.failedLayers`.
- Left: font stacks mapped to platform fonts (M10.2), balanced line wrapping, `text-variable-anchor`
  and `icon-text-fit` (unused by the Asamm styles), MapLibre's exact line anchor offsets, path
  text placed along the line by `text-anchor` or `text-offset[0]` (centred here), an icon beside a
  path text (no Asamm style has one), `symbol-sort-key` beyond 0–999, mixed-direction text runs
  (one base direction per line); with M10.2 the label golden on a four-tile corner with a
  placement dump and `isCancelled` for the layout; with M10.3 shaped text cached per layer.
