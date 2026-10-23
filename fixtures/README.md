# Fixtures

Test data shared by the golden corpus (`formats/mapsforge/golden*`), the benchmarks and both
viewers.

| Path | Content | Source | Licence |
|---|---|---|---|
| `maps/monaco.osm.map` | LoMaps vector map of Monaco, 2.3 MB | Locus Map download, copied 2026-10-22 from the A54 | see attribution below, [ODbL](https://opendatacommons.org/licenses/odbl/) |
| `themes/lomaps/` | LoMaps theme 1.8.0, day, night and POI themes with symbols | [asamm/lomaps-mapsforge](https://github.com/asamm/lomaps-mapsforge) tag `1.8.0`, `theme/` | Apache-2.0, see `themes/lomaps/LICENSE`; the release has no NOTICE file |

Map attribution, as stated in the map header and its tags:
- Map data © OpenStreetMap contributors, ODbL.
- Contour lines: SRTM and Viewfinder Panoramas.
- Low-zoom features (`ne_*` tags): Natural Earth, public domain.

Rendered tiles (golden PNGs, CI artifacts, viewer captures) are ODbL Produced Works and carry the
"© OpenStreetMap contributors" notice wherever they are published.

- Replace a fixture only together with a fresh golden run; the corpus tiles are chosen for this
  Monaco map.
- Large maps for performance runs stay outside the repo, referenced from `local.properties`.
