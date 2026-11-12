# Vector tiles (MVT): feasibility

Rendering Asamm's own vector tiles with their MapLibre styles in this library, on Android, as a
third format next to Mapsforge and Garmin IMG, is doable as an own Kotlin renderer (size L).

**Author:** Menion · **Analysed:** 2026-11-12 · **Milestone:** M10, ⏸ behind the libMapCore plan
(see [ROADMAP.md](ROADMAP.md)) · **Status:** analysis only, no code

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
| Online server | tileserver-gl workers over PMTiles on shared storage, Bunny CDN in front (`tiles.locusmap.app`); `/data/planet/{z}/{x}/{y}.pbf` and 512 px PNG rasters of the same styles | [asamm/lomaps-server-updater](https://github.com/asamm/lomaps-server-updater) (private) |
| Offline (iOS) | MBTiles per Locus Store map, plus a style template whose `mbtiles://`, sprite and glyph placeholders the app fills; elevation stays online | locus-map-ios `OfflineMapsFileManager.swift` |
| Styles | `hike_bike`, `ski` and their dark variants; generated from Glug sheets and released to online, offline and tileserver targets | [asamm/lomaps-vector-hike-bike-theme](https://github.com/asamm/lomaps-vector-hike-bike-theme) (private), `lib/glug_sheets/` |
| Style content | ~455 layers: 336 line, 71 symbol, 42 fill, hillshade, 1 `color-relief` (bathymetry), circle, background; no 3D, heatmap or terrain | `hike_bike` style JSON |
| Expressions | ~25 of the spec's 88 operators: `get`, `all`, `==`, `in`, `interpolate`, `case`, `step`, `let`/`var`, `index-of`, `slice`, `concat`, `geometry-type`, `has`, `upcase`, arithmetic | `hike_bike` style JSON |
| Text | 4 Noto stacks; the iOS offline glyph download is 131.7 MB | offline release JSON |
| Consumers | web (`locus-server/web-ui`, maplibre-gl 6), iOS (MapLibre Native); iOS online still reads MapTiler, to be switched to Asamm tiles | both repos |

## Options

| Option | Effort | Verdict |
|---|---|---|
| **A. Own Kotlin renderer** behind the driver interface: MVT decoder, MBTiles and PMTiles readers, an HTTP tile source, style JSON with the expressions the Asamm styles use, fill / line / circle / background on the existing `RenderTarget`, symbols through the cross-tile label pass, platform text | L (XL for broad spec parity) | **Recommended** |
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
- tileserver-gl's PNGs of the same styles are a visual reference for the goldens (not pixel parity).

## Work, if started

| Part | Size |
|---|---|
| MVT 2.1 decoder, MBTiles and PMTiles v3 readers, HTTP source | S |
| Style JSON: sources, layers, zoom ranges, filters, legacy filters and `{stops}` functions | M |
| Expression engine for the operators above, tested on the Asamm styles | M |
| Background, fill (incl. pattern), line (dasharray, offset, pattern), circle on `RenderTarget` | M |
| Symbols: icons from sprites, text, along-line placement, collision, sort keys, through the label pass | L |
| Overzoom from z14, 512 px tile convention, tile-buffer clipping | S |
| Goldens against tileserver-gl PNGs, JMH and A54 benchmarks | M |

## Risks

- Visual drift from GL, mostly symbol placement, dash scaling, patterns and halos.
- Spec churn: MapLibre adds operators and layer types every year; follow only what the styles use.
- The styles may need small Android-only changes (fonts, unsupported properties); keep them in the
  style repo's targets, not in code.

## Open

| ID | Question |
|---|---|
| V1 | Offline package: PMTiles (as the server) or MBTiles (as iOS) for Android? One package for both apps? |
| V2 | Does the offline Mapsforge LoMaps package go once vector tiles cover it? Third-party Mapsforge and Garmin maps stay. |
| V3 | Attribution: the web credits OSM but not OpenMapTiles (schema CC-BY); the web's internal 3D-buildings layer names a source `openmaptiles` the Asamm styles do not have. |

## Next step

A desktop-only spike (S–M): fetch a few `planet` tiles, parse `hike_bike`, draw background, fill
and line layers in the desktop viewer, and compare them with tileserver-gl's PNGs of the same
tiles. It shows how far the expression subset reaches before M10 is planned.
