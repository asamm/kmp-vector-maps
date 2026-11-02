# IMG / TYP test fixtures

This directory holds small, redistributable map fixtures used by the JUnit 5
smoke tests in `lib/src/commonTest/`.

## Acceptance criteria

A fixture may be checked in here only if **all** of the following hold:

1. **Unencrypted.** Locked / DRM-protected Garmin IMG files are out of scope
   (legal + format scope). Confirm with `xxd <file>.img | head` that the
   header magic is plain (not the locked variant).
2. **Redistributable.** Source data licence allows redistribution under this
   library's licence (LGPL-2.1, see `../../../../../LICENSE`). Practical
   sources:
   - **mkgmap-built OSM extracts** — output is OpenStreetMap data
     (ODbL 1.0) packaged in IMG containers; redistribution allowed with
     attribution. Recommended.
   - **OpenMTBMap / Freizeitkarte sample tiles** — check their per-region
     licence; many are OK.
   - **Ad-hoc TYP files** authored from scratch (declare CC0 in a sibling
     `<name>.LICENCE` file).
3. **Small enough.** Soft target ~2 MB per fixture; up to ~10 MB acceptable
   if the fixture is the smallest realistic instance of what's being
   tested. The canonical full-country fixture (`otm-monaco`, 8.5 MB) is
   above the soft target because Monaco is already the smallest available
   complete OpenTopoMap build. Anything bigger needs justification in the
   fixture's `NOTES.md`.
4. **Stable.** Fixtures must be byte-stable across machines so reference
   PNGs in Phase C can be diffed deterministically. Re-generating from OSM
   churns IDs; once committed, do not regenerate without bumping the
   reference PNGs in the same commit.

## Naming

```
fixtures/
  otm-monaco/        # canonical real-world fixture (8.5 MB, OpenTopoMap)
    otm-monaco.img   # the IMG file
    NOTES.md         # how it was sourced, embedded build date, attribution
    LICENCE          # ODbL (data) + CC-BY-SA (style) attribution
  mkgmap-monaco/     # minimal smoke fixture (152 KB, self-built from PBF)
    monaco.img
    NOTES.md         # build recipe (mkgmap + Geofabrik PBF)
    LICENCE          # ODbL only (no styling layer)
  typ-mapwel-sample/
    sample.typ
    LICENCE
```

Each fixture lives in its own directory named after its source — never a
bare `*.img` at the top of `fixtures/`. This keeps the per-fixture
`NOTES.md` and `LICENCE` adjacent and the test code can reference them by
a stable path.

## Pick the right fixture for your test

| Test purpose | Fixture |
|---|---|
| Real-world stress (rich features, contours, TYP) | `otm-monaco` |
| Fast parser correctness, deterministic regeneration | `mkgmap-monaco` |
| NT/GMP-packed layout | synthetic only (mkgmap and OpenTopoMap both emit legacy) |
| Locked / DRM-encrypted | out of scope — see PLAN.md |

## How to generate a mkgmap fixture

```sh
# 1. download a tiny OSM extract (e.g. via Geofabrik, Overpass, or
#    osmconvert with a 0.05° bbox)
# 2. run mkgmap:
java -jar mkgmap.jar \
    --description="kmp-garmin-img-fixture" \
    --family-id=42 \
    --product-id=1 \
    --mapname=00000001 \
    --gmapsupp \
    tiny.osm.pbf
# 3. extract the IMG out of gmapsupp.img using gmt or gpsbabel
```

## Tests that need fixtures

The CoordUtils + BinaryReader tests already in this module run without
fixtures. Fixture-backed tests will arrive in:

- Phase B — parser parity tests (Java vs Kotlin port).
- Phase C — reference-PNG diff for the desktop renderer.

Until a real fixture lands here, fixture-backed tests are intentionally
absent rather than skipped.
