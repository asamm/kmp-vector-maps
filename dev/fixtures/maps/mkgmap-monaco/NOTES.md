# mkgmap-monaco — Self-built Monaco IMG fixture

A 152 KB unencrypted Garmin IMG covering the principality of Monaco, built
in-house from OpenStreetMap data via [mkgmap](https://www.mkgmap.org.uk/).
Smaller and fully reproducible companion of the `otm-monaco` (OpenTopoMap, 8.5 MB) fixture, with
the same geographic content.

## Source data

- OSM extract: <https://download.geofabrik.de/europe/monaco-latest.osm.pbf>
  (downloaded 2026-05-04)

## Build chain

- mkgmap r4924: <https://www.mkgmap.org.uk/download/mkgmap-r4924.zip>
  (downloaded 2026-05-04)
- JDK 17

## Build recipe

```bash
# 1. Download the inputs to a working directory outside this repo
curl -L -o monaco-latest.osm.pbf \
  https://download.geofabrik.de/europe/monaco-latest.osm.pbf
curl -L -o mkgmap.zip https://www.mkgmap.org.uk/download/mkgmap-r4924.zip
unzip mkgmap.zip

# 2. Run mkgmap with the same flags we used to produce monaco.img
java -Xmx2G -jar mkgmap-r4924/mkgmap.jar \
    --description=monaco \
    --family-id=42 \
    --product-id=1 \
    --mapname=00000001 \
    --output-dir=out \
    --gmapsupp \
    monaco-latest.osm.pbf

# 3. Copy the resulting gmapsupp.img into this directory as monaco.img
cp out/gmapsupp.img monaco.img
```

## Layout

This fixture uses the **legacy** IMG layout: separate `TRE`, `RGN`, `LBL`,
`NET` sub-files as top-level FAT entries (no GMP envelope). mkgmap does not
emit the NT/GMP-packed layout that newer Garmin in-house tooling produces;
NT support in the Garmin engine is exercised via synthetic fixtures only
(see [SyntheticImg](../../../../formats/garmin-img/engine/src/test/kotlin/com/asamm/vectormaps/garminimg/parser/SyntheticImg.kt)).

If a real-world NT fixture is ever needed, the only practical sources are
locked Garmin commercial maps — which are out of scope per
the [CLAUDE.md](../../../../CLAUDE.md) scope table — or hand-crafted synthetic ones.

## Provenance

- **Map data:** OpenStreetMap contributors. Licensed under the
  [Open Database License (ODbL) v1.0](https://opendatacommons.org/licenses/odbl/1-0/).
  Attribution: © OpenStreetMap contributors.
- **Build chain:** mkgmap, [LGPL-3.0](https://www.gnu.org/licenses/lgpl-3.0.txt).
  The LGPL applies to mkgmap itself; the IMG output is not LGPL — only the
  source OSM data's ODbL applies.

See `LICENCE` in this directory for the full attribution text.

## Regeneration

If the fixture is ever regenerated:
- Update the download dates above.
- Bump any reference values asserted in tests in the same commit.
- Confirm the file stays under ~200 KB (mkgmap's deterministic output makes
  this stable; if it suddenly bloats, something has changed in the
  invocation flags).
