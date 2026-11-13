# Fixtures

Test data shared by the golden tests (`dev/mapsforge/golden*`, `dev/garmin-img/golden`), the
engine tests, the benchmarks and both viewers.

| Path | Content | Source | Licence |
|---|---|---|---|
| `maps/monaco.osm.map` | LoMaps vector map of Monaco, 2.3 MB | Locus Map download, copied 2026-10-22 from the A54 | see attribution below, [ODbL](https://opendatacommons.org/licenses/odbl/) |
| `maps/otm-monaco/` | OpenTopoMap Garmin IMG of Monaco, 8.5 MB, with contours and TYP; Garmin tests and benchmarks | [garmin.opentopomap.org](https://garmin.opentopomap.org/), downloaded 2026-05-04, see its `NOTES.md` | ODbL data, CC-BY-SA 4.0 style, see its `LICENCE` |
| `maps/mkgmap-monaco/` | Garmin IMG of Monaco built with mkgmap r4924, 152 KB; fast Garmin parser tests | Geofabrik extract, recipe in its `NOTES.md` | ODbL, see its `LICENCE` |
| `fonts/dejavu/` | DejaVu Sans 2.37 (`DejaVuSans.ttf`, 757 KB, SHA-256 `7da195a7…ed848954`), the label font of the Garmin pass goldens, the Mapsforge AWT label golden tiles and the MVT label goldens | [dejavu-fonts](https://github.com/dejavu-fonts/dejavu-fonts) release `version_2_37`, `ttf/`, downloaded 2026-11-02 | Bitstream Vera and Arev licences, DejaVu changes public domain, see its `LICENCE` |
| `mvt/` | Asamm vector tiles (OpenMapTiles 3.16 schema plus Asamm layers, Planetiler): `tiles/planet/` 6 tiles of Monaco and Sněžka at z10, z12, z14, uncompressed, and the same in `tiles/fixtures.pmtiles` (written by `dev/mvt/pmtiles.py`); `tiles/planet.json` the server's TileJSON, which the desktop viewer opens as an online map; `styles/hike_bike/` the LoMaps `hike_bike` style with its sprites; `reference/server/` the server's 512 px PNGs of them, `reference/maplibre/` MapLibre GL JS renders from `dev/mvt/reference` (`base` without symbols, `labels` without elevation, `full`) | `tiles.locusmap.app`, downloaded 2026-11-12, style as served | tiles and renders: OSM data ODbL, OpenMapTiles schema CC-BY 4.0, see the vector tile attribution below; style and sprites © Asamm Software, all rights reserved, included as a test fixture only |
| `themes/lomaps/` | LoMaps theme 1.8.0, day, night and POI themes with symbols | [asamm/lomaps-mapsforge](https://github.com/asamm/lomaps-mapsforge) tag `1.8.0`, `theme/` | Apache-2.0, see `themes/lomaps/LICENSE`; the release has no NOTICE file |

Map attribution, as stated in the map header and its tags:
- Map data © OpenStreetMap contributors, ODbL.
- Contour lines: SRTM and Viewfinder Panoramas.
- Low-zoom features (`ne_*` tags): Natural Earth, public domain.

Vector tile attribution (`mvt/`), as the style's sources state it:
- Tiles: © OpenMapTiles, © OpenStreetMap contributors (ODbL), Natural Earth, Tom Patterson.
- Hillshade and bathymetry, in the `full` MapLibre and the server renders only: Sonny
  (CC BY 4.0), Mapterhorn, SRTM, Viewfinder Panoramas, GEBCO 2025 Grid.

Rendered tiles (golden PNGs, CI artifacts, viewer captures, the `mvt/reference` PNGs and the MVT
test renders) are ODbL Produced Works and carry the "© OpenStreetMap contributors" notice wherever
they are published; renders of `otm-monaco` also carry "Map style © OpenTopoMap (CC-BY-SA)",
MVT renders also "© OpenMapTiles".

- Replace a fixture only together with a fresh golden run; the corpus tiles are chosen for this
  Monaco map.
- A Garmin IMG fixture is unencrypted (header magic `DSKIMG`), redistributable, about 2 MB at
  most (10 MB with a reason in its `NOTES.md`), and carries its own `NOTES.md` and `LICENCE`.
- Large maps stay out of git, in the git-ignored `local/` at the repo root (an MBTiles tileset
  converted by `dev/mvt/mbtiles2pmtiles.py` into `local/mvt/`) or anywhere else: the desktop
  viewer reads them from `local.properties` when no saved session map exists, the Android viewer
  from `--es map` / `--es theme` intent extras (`am start -S`), the benchmarks from
  `-Pbench.mapFile` (JVM) or the `bench.mapFile` instrumentation argument, Garmin ones from
  `bench.garmin.mapFile`, MVT ones from `bench.mvt.mapFile`.
