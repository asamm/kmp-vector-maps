# Vector tiles (MVT)

Asamm's vector tiles with their MapLibre styles, rendered by an own Kotlin engine behind the driver
interface, as the third format next to Mapsforge and Garmin IMG.

**Author:** Menion · **Done:** 2026-11-16, M10 (PRs #53–#65, step tables in
[archive/roadmap-m7-m10.md](archive/roadmap-m7-m10.md)) · **Benchmarks:**
[performance.md](performance.md)

## Goal

| Requirement | From |
|---|---|
| One engine for online and offline vector maps on Android | Menion, 2026-11-12 |
| One tile source: Asamm tiles online now, offline later, replacing the Mapsforge and MBTiles offline packages | Menion, 2026-11-12 |
| As close to the web and iOS maps (MapLibre) as possible, not 1:1 | Menion, 2026-11-12 |
| Online and offline render the same on one device | Menion, 2026-11-12 |

The Mapsforge driver stays for third-party maps when vector tiles replace the LoMaps Mapsforge
package (answered 2026-11-12). Android-only style changes go into the style repo's targets, not
into code.

## What Asamm serves

| Part | Fact |
|---|---|
| Tiles | One planet tileset, OpenMapTiles 3.16 schema plus Asamm layers (`hiking`, `cycling`, `ski`, `contour`, …), z0–14, overzoomed by clients; Planetiler with [asamm/planetiler-openmaptiles](https://github.com/asamm/planetiler-openmaptiles) |
| Server | tileserver-gl over PMTiles behind a CDN, `tiles.locusmap.app/data/planet.json` |
| Offline (iOS) | MBTiles per Locus Store map with a style template; elevation stays online |
| Styles | `hike_bike`, `ski` and their dark variants; `hike_bike` has ~455 layers (line, symbol, fill, hillshade, `color-relief`, circle, background) |
| Consumers | Locus Map web (maplibre-gl), Locus Map iOS (MapLibre Native) |

Why an own renderer: the styles are Asamm's, so the supported features are a finite, tested list;
online and offline share one decoder, style engine and renderer; platform text shapes scripts
glyph PBFs cannot and needs no glyph download; elevation maps to the `HILLSHADE` anchor. Rejected:
MapLibre Native as a rasteriser (native size, GL per worker, against libMapCore D20), converting
styles to Mapsforge themes (lossy, per style), GeoTools, VTM, maplibre-rs (unfit).

## Driver

`MvtDriver` (`formats/mvt/driver`) behind `:core`, as the other formats.

| Part | What |
|---|---|
| Offline | `open(files)`: PMTiles v3 (`PmTilesSource`), files of one max zoom; MBTiles waits for V1 |
| Online | `openSource(MvtTileSource)`: the host's downloader and cache; the desktop viewer opens a TileJSON file through its own `HttpTileSource`, cached in `~/.kmp-vector-maps/tiles` |
| Style | `loadStyle(style.json)`, at most 16 MB, with its sprite beside it and the `@2x` sheet above a style pixel per screen pixel, read by the first renderer created |
| Platform | `MvtGraphics`: `AwtMvtGraphics` (desktop), `AndroidMvtGraphics` (`Canvas`, Android text shaping); sprite sheets up to 4096 × 4096 pixels |
| Renderer | tiles above the data zoom cut from it, decoded data tiles kept for their children within 16 MB estimated; hillshade hook per elevation layer (`HillshadeLayer`); labels stored per tile for the label pass |
| Limits | a tile at most 16 MB stored or inflated; a malformed property or filter lands in `Style.problems`, a layer that throws is left out (`failedLayers`) |

- `RenderConfig`: a style pixel is `deviceScale` screen pixels on a 512 px tile, scaled with the
  tile size, as MapLibre's. A host draws MapLibre's sizes with 512 × density px tiles and
  `deviceScale` 1, as the Android viewer does (D20); `textScale` scales text and halos.
- `language` reads `name:xx` for `name`, as the MapLibre language plugin: `zh-Hant` reads
  `name:zh-Hant`, then `name:zh`; `de-AT` reads `name:de`.

## Labels

`SymbolLayout` (engine) lays out each tile's symbol layers; `MvtLabelPass` places them per screen
through the core `LabelPlacer` and draws them (D39). As MapLibre:

- Points at their point, lines at their first point, polygons at their pole of inaccessibility,
  found once per data tile above the data zoom.
- `line` placement on lines clipped to their data tile, every `symbol-spacing` from half a spacing
  in for a line crossing the tile's edge, from half the label plus two text sizes for one starting
  in it (times the overscale above the data zoom), the middle of such a line only where no spaced
  anchor fits; laid out with the text at the tile's zoom size, z18 for a size varying by feature,
  as MapLibre Native (GL JS takes z18 for every size, so the GL JS goldens differ there);
  `line-center` at the middle of the whole line; pieces with one text joined first; a label's
  length its text's or its icon's, the icon at its sprite size times the text size over 24 px; a
  `line` text's anchor dropped within half a spacing of an earlier one with the same text in the
  layer (`anchorIsTooClose`), the earlier ones seen within a spacing of the tile, MapLibre's in the
  whole data tile.
- Point text broken into lines least ragged against their average width (`determineLineBreaks`:
  empty lines kept, a break before `(`, CJK and Yi between any characters); a line's baseline the
  font's ascent less 17/24 of the size below its centre, as MapLibre's glyph layout puts it.
- `text-max-angle` over 3/5 of the text size; icons on lines turn with them; `icon-translate` in
  the map's frame, its collision box too.
- Later layers place first, then lower `symbol-sort-key`, then the tile's feature order (the
  OpenMapTiles rank); an `allow-overlap` symbol is always placed but still blocks the labels
  placed after it, unless it also has `ignore-placement` (honoured only with `allow-overlap`).
- Text in platform fonts (joining, one bidi base direction per line); the goldens use the bundled
  DejaVu Sans.

## Spec coverage

Against the [MapLibre style spec](https://maplibre.org/maplibre-style-spec/): the `hike_bike` style
uses nothing outside "Supported" but the elevation paint, which is the host's.

| Layer | Supported | Not yet |
|---|---|---|
| background | `-color`, `-opacity` | `-pattern` |
| fill | `-color`, `-opacity`, `-antialias`, `-pattern`, `-outline-color` (by default the fill colour; a line one device pixel wide, antialiased as MapLibre's; not with a pattern) | `-translate`, `-sort-key` |
| line | `-color`, `-width`, `-opacity`, `-dasharray`, `-offset`, `-cap`, `-join` (miter limit 2) | `-pattern`, `-gap-width`, `-blur`, `-translate`, `-gradient`, `-miter-limit`, `-round-limit`, `-sort-key` |
| circle | `-radius`, `-color`, `-opacity`, `-stroke-width`, `-stroke-color`, `-stroke-opacity` | `-blur`, `-translate`, `-pitch-*`, `-sort-key` |
| symbol, placement | `symbol-placement`, `-spacing`, `-sort-key` (0–999), `*-allow-overlap`, `*-ignore-placement` (with `allow-overlap`), `*-rotation-alignment`, `text-max-angle`, `text-keep-upright` | `text-variable-anchor`, `-radial-offset`, `-optional`, `icon-text-fit`, `icon-keep-upright`, `symbol-avoid-edges`, `-z-order`, `-pitch-alignment`, `text-writing-mode` |
| symbol, text | `text-field`, `-font` (face), `-size`, `-max-width`, `-line-height`, `-letter-spacing`, `-justify`, `-anchor`, `-offset`, `-transform`, `-padding`, `-rotate`, `-color`, `-opacity`, `-halo-color`, `-halo-width`, `-halo-blur` | `-translate`, `formatted` text |
| symbol, icon | `icon-image`, `-size`, `-offset`, `-rotate`, `-padding`, `-translate`, `-anchor`, `-opacity` | SDF `icon-color`, `-halo-*` |
| hillshade, color-relief | the host's hook, with source and paint | drawn by the engine |
| raster, fill-extrusion, heatmap | — | all |

- Every layer: `minzoom`, `maxzoom` (none by default), `filter` (expression or legacy),
  `visibility`.
- Expressions: `let`, `var`, `literal`, `get`, `has`, `id`, `zoom`, `geometry-type`,
  `interpolate` (linear, exponential; cubic-bezier as linear), `step`, `case`, `match`,
  `coalesce`, `all`, `any`, `!`, comparisons, `in`, `index-of`, `slice`, `at`, `length`, `concat`,
  `upcase`, `downcase`, `to-*`, `rgb`, `rgba`, `string`, `number`, `boolean`, `array`, `typeof`,
  `image`, arithmetic and math, `pi`, `e`, `ln2`; legacy `{"stops"}` functions and `{token}`
  strings in `text-field` and `icon-image`.
- Not yet: `interpolate-hcl`/`-lab`, `format`, `number-format`, `collator`, `resolved-locale`,
  `is-supported-script`, `properties`, `feature-state`, `line-progress`, `heatmap-density`,
  `elevation`, `accumulated`, `distance`, `within`, `global-state`, `split`, `join`, `object`; a
  zoom and property function in one. A property using one is left out and listed in
  `Style.problems`; a layer whose filter it is draws unfiltered.
- Next candidates, small and checkable: `line-gap-width`, `text-translate`, `fill-translate`.

## Differences from MapLibre

| Area | Here |
|---|---|
| Pixels | goldens against MapLibre GL JS renders (`dev/mvt/reference`), 8 tiles z10–16: without labels mean 0.4–2.1 of 255 and 0.2–1.5 % of pixels off by more than 32 (the golden allows 4.0 and 6 %); with labels in DejaVu Sans against MapLibre's Noto, reported only. The parity sweep (below), 83k tiles z1–18 in Noto on both sides: 0.29 % of pixels off without labels, 2.2 % with labels, 3.0 % turned (before M11.1 0.70, 3.0 and 3.6 %); after M11.1's anchor fixes, M11.2's antialiased outline and the label fixes of M11.3 and M11.4, a 13.9k subset 0.15, 1.6 and 2.5 % |
| Text | path text around sharp bends where MapLibre picks a straighter stretch; a few placement differences left in dense city centres; font stacks map to a face (serif, bold, italic), not named fonts; path text centred on its anchor, not moved by `text-anchor` or `text-offset[0]`; no icon beside a path text (no Asamm style has one) |
| Fill | a line feature in a fill layer draws nothing; MapLibre fills its parts as rings, bridging them (OpenMapTiles' platform lines) |
| Halo blur | up to 6 strokes of stepped alpha over 1.19 × blur, not clipped to a glyph buffer |
| Android | a turned path text is drawn a character at a time, unshaped and in logical order; translucent halo rings blend twice where glyphs overlap |
| Expressions | a legacy function's default type comes from its stop values, except never-interpolated properties; `image` returns the name of a missing image; `typeof` says `array` without the item type |

## Parity sweep

`dev/mvt/sweep` (M11) compares many tiles of a large PMTiles file with MapLibre Native, the
renderer of Locus Map iOS, to find what the eight goldens miss; nothing in it ships.

| Step | Command, from the repository |
|---|---|
| Sample | `node dev/mvt/reference/sweep.mjs sample <file.pmtiles> <run>/tiles.txt <perZoom> <seed> 1 18`: distinct data tiles per zoom, denser likelier, overzoomed children above z14 |
| MapLibre | `node dev/mvt/reference/sweep-native.mjs <file.pmtiles> <run>/tiles.txt <run> <cache> [processes]`, with Node 22 or 24 (the package's Windows binaries): views `base` (no symbols), `labels`, `labels_r30` (turned 30°), elevation layers left out |
| Ours | `./gradlew :dev:mvt:sweep:sweep -Psweep.args="compare map=<file.pmtiles> run=<run> fonts=<dir>"`: the same views through the driver, labels in Noto (`NotoSans-Regular`, `-Bold`, `-Italic`, `NotoSerif-Italic`), scored as the goldens; `<run>/results.csv` and `report.html` with the worst tiles |
| Frozen build | `./gradlew :dev:mvt:sweep:sweepInstall -Psweep.into=<dir>`, then `java -DfixturesDir=dev/fixtures -cp "<dir>/*" com.asamm.vectormaps.mvt.sweep.MainKt compare ...`: scores while the sources change |
| Placed labels | `labels map=<file> run=<run> tiles=<list>` writes ours to `<run>/labels.tsv`, `node dev/mvt/reference/sweep.mjs labels <file> <list> <out.tsv>` GL JS's: tile, layer, text and anchor of each label placed on the screen |
| One tile | `trace map=<file> tile=z/x/y px=x,y`: the pixel's colour as the style's layers add up; `features map=<file> tile=z/x/y layer=<name> [key=value]`: the data tile's features |

- The Noto faces are Google's Noto Sans and Noto Serif (OFL) `.ttf` files; MapLibre's glyphs come
  from the LoMaps tile server on the first run, then from `<cache>/glyphs`.
- `sweep.mjs render` draws the same views with MapLibre GL JS in Edge, about 50 times slower.
- A turned view rotates our finished tiles as the hosts do, so its road edges differ more than
  MapLibre's natively turned ones; compare labels there, not lines.

## Open

| ID | Question |
|---|---|
| V1 | Offline package: PMTiles (as the server) or MBTiles (as iOS) for Android? One package for both apps? |
| V3 | Attribution: the tileserver credits OpenMapTiles, the web app's list does not (schema CC-BY). |
