# otm-monaco — OpenTopoMap Monaco fixture

A pre-rendered Garmin IMG covering Monaco, downloaded from
[OpenTopoMap](https://opentopomap.org/). This is the **canonical real-world
fixture** for parser tests — it carries the full feature richness of an
OpenTopoMap build: contour lines, custom TYP styling, road priorities, POIs,
extended-types data. The smaller `mkgmap-monaco` fixture in the sibling
directory is the bare-OSM stripped-down version used for fast smoke tests.

## Source

- URL: <https://garmin.opentopomap.org/europe/monaco/otm-monaco.zip>
- Downloaded: 2026-05-04
- Embedded build date in IMG header: 2026-04-30
- Map description (read from header bytes 0x49+): `OpenTopoMap Monaco 2`
- Header magic at 0x10: `DSKIMG` — confirms unencrypted

## Why both this and mkgmap-monaco

| | otm-monaco | mkgmap-monaco |
|---|---|---|
| Size | 8.5 MB | 152 KB |
| Source | Pre-built by OpenTopoMap | Self-built from Geofabrik PBF |
| Reproducible | No (depends on OpenTopoMap's build pipeline) | Yes (recipe in mkgmap-monaco/NOTES.md) |
| Feature richness | High (contours, TYP, POIs, extended types) | Low (basic OSM features only) |
| Used for | Real-world stress tests, TYP parsing | Fast parser correctness checks |

## Provenance

- **Map data:** OpenStreetMap contributors. Licensed under the Open Database
  License (ODbL) v1.0. <https://www.openstreetmap.org/copyright>
- **Style / rendering:** OpenTopoMap. Style and pre-rendered cartography
  licensed under CC-BY-SA 4.0. <https://opentopomap.org/about>
- **Build chain:** mkgmap (LGPL-3.0). Specific mkgmap version not embedded
  in the IMG header.

See `LICENCE` in this directory for the full attribution text.

## Regeneration

To refresh the fixture (only when OSM changes meaningfully break parser
tests — see the replacement rule in `../../README.md`):

```bash
curl -L -o otm-monaco.zip https://garmin.opentopomap.org/europe/monaco/otm-monaco.zip
unzip -o otm-monaco.zip
# regenerate any reference values in the affected tests
```

When regenerating, update this NOTES.md with the new download date and
embedded header date, and bump any reference values asserted in tests in
the same commit.
