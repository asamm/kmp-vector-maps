# Changelog

All notable changes to this project will be documented in this file.
This project adheres to [Semantic Versioning](https://semver.org/).

## [0.1.0] — unreleased

Phase A bootstrap + Phase B.1 first slice. Sets up the Kotlin Multiplatform
skeleton for Android + JVM, ports the seed utilities and the random-access IMG
reader from the Java predecessor `libGarminImg`, and wires GitHub Packages
publishing.

### Added — Phase A
- Gradle KMP skeleton (Kotlin 2.3.20, AGP 9.1.1, Gradle 9.3.1, JDK 17).
- `:lib` module with Android + JVM targets.
- `com.asamm.garminimg.coord.CoordUtils` — fixed-point ↔ WGS84 conversions and
  bounding-box helpers, ported from `org.free.garminimg.CoordUtils`.
- `com.asamm.garminimg.io.BinaryReader` — endian-aware sequential reader over
  `kotlinx.io.Source`, the seed of the binary IO layer.
- GitHub Actions: CI build, manual snapshot publish, tag-driven release publish.
- LGPL-2.1 LICENSE copied from the libGarminImg fork (derivative work).

### Added — Phase B.1
- `com.asamm.garminimg.io.ImgRandomAccessReader` — interface for seekable byte-level
  access to an IMG container. Replaces Java `ImgFileInputStream`. The original's
  XOR/lock byte handling for encrypted maps is intentionally dropped (out of scope —
  unencrypted maps only).
- `com.asamm.garminimg.io.ByteArrayImgRandomAccessReader` — in-memory implementation
  used by tests and small fixtures. File-backed `expect/actual` impls land in B.2 once
  sub-file readers need them.
- `com.asamm.garminimg.model.ObjectKind` — bit-flag constants for IMG object kinds
  (POINT, INDEXED_POINT, POLYLINE, POLYGON, EXTENDED_*) and map kinds (BASE_MAP,
  NORMAL_MAP). Ported verbatim from the Java predecessor.
- `lib/src/commonTest/resources/fixtures/otm-monaco/` — OpenTopoMap Monaco IMG
  (8.5 MB unencrypted) as the canonical real-world parser fixture, with NOTES.md
  (provenance, regeneration recipe) and LICENCE (ODbL data + CC-BY-SA style).
- `OtmMonacoFixtureTest` (jvmTest) — smoke test that the fixture loads from the
  classpath, has the unencrypted DSKIMG signature, and carries the expected
  OpenTopoMap header description. Acts as a canary on regeneration.

### Added — Phase B.2.1
- `com.asamm.garminimg.parser.SubFileEntry` — data class describing one entry in
  the IMG container's FAT directory: file name, file type, size, first block,
  source FAT offset.
- `com.asamm.garminimg.parser.ImgArchive` — top-level reader that opens an IMG,
  validates the DSKIMG signature + partition-table-end marker, derives the block
  size from header exponents, scans for the FAT start past padding blocks, and
  enumerates every primary FAT entry. Continuation entries (partNumber != 0) are
  filtered out. Replaces the head of the Java `ImgFileBagExtractor`.
- `ImgArchiveTest` (commonTest) — 10 cases driven by synthetic byte streams
  built via the test-only `SyntheticImg` helper.
- Extended `OtmMonacoFixtureTest` to parse the Monaco FAT and assert primary
  TRE/RGN/LBL entries are present.

### Added — Phase B.2.2
- `com.asamm.garminimg.parser.ImgSubFileStream` — logical random-access view over
  a sub-file's contiguous block run inside the IMG container. Implements
  [ImgRandomAccessReader] so sub-file readers (TRE/RGN/LBL/NET, coming in B.2.3+)
  can take any reader type without caring whether they're reading from memory or
  from a logical sub-file slice. Multiple streams over the same container are
  safe for serial use — each tracks its own logical position and re-seeks the
  container when needed.
- `com.asamm.garminimg.parser.SubFileCommonHeader` data class +
  `ImgRandomAccessReader.readSubFileCommonHeader()` extension — parses the
  common header that every IMG sub-file starts with (header length at offset 0,
  lock-flag check at offset 0xD). Refuses locked sub-files loudly.
- `ImgArchive.openSubFile(entry, reader)` convenience factory.
- `ImgSubFileStreamTest` (commonTest) — 8 cases driven by synthetic IMGs with
  injected sub-file payloads (`SyntheticImg.payloadAtBlock`), including
  multi-stream-over-one-container correctness.
- Extended `OtmMonacoFixtureTest` to open the real Monaco TRE sub-file via
  `ImgSubFileStream` and parse its common header — first end-to-end validation
  that block-level demux works against a real IMG.

### Added — Phase B.2.3.a
- `ImgRandomAccessReader` extended with `readUInt24`, `readInt24`, `readUInt32`
  helpers — IMG uses 24-bit signed coordinates and 32-bit unsigned section
  offsets; both are needed by the TRE parser and beyond.
- `com.asamm.garminimg.parser.TreSubFile.parseHeader(reader)` — reads a TRE
  sub-file's common header, the four-direction bounding box (north/east/south/
  west, each Int24), and (when the sub-file's headerLength covers offset 0x82)
  the TRE7 extended-types section descriptor.
- `TreHeader` data class — bounds, headerLength, optional `ExtendedTypesInfo`,
  plus a derived `fullSurface` (used by the Java predecessor's IMG comparator).
- `ExtendedTypesInfo` data class — TRE7 offset/length/recordSize + derived
  `recordCount`.
- `TreSubFileTest` (commonTest) — 9 cases via the `TreBuilder` helper covering
  short headers, southern-hemisphere negative bounds, TRE7 absence/presence,
  pathological `recordSize=0`, and the rejection paths (locked, header too
  short for bounds, north < south).
- Extended `OtmMonacoFixtureTest` to parse the real Monaco TRE bbox and assert
  the bounds land within ±1° of Monaco's geographic position, plus that the
  TRE7 extended-types block is present.

### Added — Phase B.2.3.b
- `TreSubFile.parseLevels(reader)` — reads the levels-section descriptor at
  TRE offset 0x21, then walks the levels table emitting one [TreLevel] per
  4-byte record (zoom/inherited byte, bitsPerCoord byte, sub-division count
  UInt16). Returns [TreLevels] with the list, totalSubDivisions running total,
  and convenient queries (`maxLevel`, `minLevel`, `bitsPerCoordOf`,
  `isPresent`, `isInherited`, `extendedTypesDecalage`).
- 6 new TreSubFileTest cases covering empty section, single/multi-level,
  inherited flag, level=15 sentinel, and the `decalajeExtendedTypes`
  formula.
- Extended Monaco fixture test: TRE levels parse, level numbers in 0..15,
  bitsPerCoord in 0..24, minLevel ≤ maxLevel.

### Added — Phase B.2.3.c
- `com.asamm.garminimg.parser.SubDivision` data class — one entry in the TRE
  sub-division tree: index, level, RGN data range, object-types bit-mask,
  centre/half-extent, child list, and (when TRE7 is present) the three
  extended-types data ranges (polygon / polyline / POI).
- `com.asamm.garminimg.parser.SubDivisionParser.parse()` — reads the
  sub-divisions descriptor at TRE offset 0x29, walks the on-disk run
  level-by-level (FULL 16-byte records for non-leaf levels, SMALL 14-byte
  for leaf levels in multi-level trees), assembles the tree via the
  parent's `indexNextLevel` field, attaches TRE7 records via the Java
  predecessor's `decalajeExtendedTypes` formula, and back-fills
  data-end / extended-end offsets from neighbour records (with the very last
  record's ends supplied by an optional [RgnEndInfo] from RGN parsing).
- Returns a [SubDivisionTree] with the root list and a flat `byIndex`
  lookup; tree.all() yields every node depth-first.
- `SubDivisionParserTest` (commonTest) — 6 cases via the extended
  [TreBuilder] covering single-leaf-level, two-level, RGN end back-fill,
  object-type flag round-trip, empty tree, and TRE7-per-sub-division
  attachment.
- Monaco fixture test `subDivisionTreeWalksToLeavesAndCarriesData` —
  parses the real Monaco TRE end-to-end through Header → Levels →
  SubDivisions and asserts the tree carries data offsets and object-type
  flags.

### Added — fixtures
- `mkgmap-monaco/monaco.img` — additional 152 KB self-built Monaco fixture
  produced by mkgmap r4924 from the Geofabrik PBF. Smaller than the
  OpenTopoMap fixture (8.5 MB) and reproducible from a documented recipe.
  Used for fast smoke tests; OpenTopoMap remains the canonical real-world
  fixture for stress testing.
- Documented in `fixtures/README.md` that NT/GMP-packed IMG layout is
  exercised via synthetic fixtures only — mkgmap and OpenTopoMap both
  emit the legacy layout.

### Added — Phase B.2.4.a
- `com.asamm.garminimg.io.LabelCharset` — codepage-aware byte-to-string
  decoder. UTF-8 (codepage 65001) and Latin-1/cp1252 (codepage 0/850/1252)
  decoded directly in commonMain; exotic codepages fall back to Latin-1
  (round-trips bytes correctly, may misglyph 0x80..0x9F).
- `com.asamm.garminimg.parser.LblSubFile.parseHeader(reader)` — reads the
  labels-section descriptor (offset/length/multiplier), label coding byte,
  and codepage. Other section descriptors (country/region/city/POI/ZIP)
  are deferred to B.2.4.b.
- `com.asamm.garminimg.parser.LblSubFile.readLabel(reader, header, offset)`
  — resolves a logical label offset to its decoded String. Supports the
  three Garmin label codings:
  - Coding 6 (6-bit packed) — custom decoder with NORMAL/SYMBOL/SPECIAL
    character sets via shift codes 0x1B/0x1C; matches the Java predecessor
    output exactly.
  - Coding 9 (8-bit codepage) and 10 (multibyte) — null-terminated byte
    sequence decoded via [LabelCharset].
  - Unknown codings return `LblSubFile.NOCHAR` ("???") sentinel.
- `LblSubFileTest` (commonTest) — 7 cases via `LblBuilder` covering
  ASCII/UTF-8/Latin-1 decoding, header parsing, locked sub-file rejection,
  out-of-range offset rejection, unknown coding fallback.
- Monaco fixture test `lblHeaderParsesAndCanReadFirstLabel` — opens the
  real Monaco LBL, validates header fields, decodes labels at offset 0
  (empty sentinel) and offset 1 without throwing.

### Added — Phase B.2.5
- `com.asamm.garminimg.model.MapObject` — sealed hierarchy: `Point` (POI /
  marker / indexed) and `PolyShape` (polyline / polygon, discriminated by
  `closed`). Replaces the Java predecessor's `MapListener.addPoint` /
  `addPoly` callback shape with a Kotlin-idiomatic value type.
- `com.asamm.garminimg.parser.RgnSubFile` — full RGN sub-file parser:
  - `parseHeader(reader)` — plain-types data section + (when headerLength
    >= 0x55) the three extended-types sections.
  - `endInfo(header)` — feeds [SubDivisionParser] the section ends needed
    to back-fill the last sub-division's data range.
  - `parseSubDivision(reader, header, subDivision, lblReader, lblHeader,
    objectKindFilter)` — yields a `Sequence<MapObject>` with every plain
    POINT / INDEXED_POINT / POLYLINE / POLYGON and (when present) the
    extended POLYGON / POLYLINE / POINT records. Yields in the
    Java-predecessor's polygon-first draw order (background → city → zone
    → forest → others → polylines/points).
- `com.asamm.garminimg.parser.RgnHeader`, `SectionRange` data classes.
- Internal `BitStreamReader` — variable-bit-width reader for the
  coordinate-delta stream that's the densest part of an IMG. Faithful port
  of the Java predecessor's recursive `readCoordOffset`. Wrapped in
  per-record try/catch so an over-running record doesn't kill the whole
  sub-division parse (matches Java's behaviour for extended polys).
- Monaco fixture test `rgnSubDivisionParseEmitsFeatures` — walks the
  whole tree, parses every sub-division, asserts non-trivial counts:
  >50 points, >50 poly shapes, >10 with resolved labels. End-to-end proof
  the parser correctly assembles archive → FAT → block-demux → TRE
  (header/levels/subdivisions) → LBL (header) → RGN (objects).

### Added — Phase B.2.6
- `com.asamm.garminimg.parser.NetSubFile` + `NetHeader` — minimal NET
  reader covering `parseHeader` and `resolveRoadName`. Routing data is
  out of scope per CLAUDE.md/PLAN.md; we only port what the renderer
  needs to follow polyline `dataInNet` label references.
- `com.asamm.garminimg.parser.TypSubFile` + `TypHeader` — TYP header
  parser only. Section bodies (point/line/polygon styling tables) are
  deferred to Phase C — the Java predecessor itself never decoded them.

### Added — Phase B.2.7 (top-level orchestrator)
- `com.asamm.garminimg.ImgFile` — single entry-point API replacing the
  Java predecessor's `ImgFilesBag` + `ImgFileBag` pair. Opens an IMG,
  parses every sub-file present (TRE / RGN / LBL / NET / TYP), assembles
  the sub-division tree with RGN ends back-filled, and exposes
  `readMap()` and `readMapInBounds()` returning a `Sequence<MapObject>`.
- `ImgFile.open(byteSourceFactory)` convenience factory for the common
  case of reading from a single `ByteArray` — produces fresh readers
  per sub-file so concurrent seeks don't clash.
- `OtmMonacoImgFileTest` (jvmTest) — end-to-end `ImgFile.open` against
  Monaco. Asserts >100 features emitted, with non-zero counts of points,
  polylines, and polygons. Also verifies `objectKindFilter` correctly
  restricts the output.

## Phase B summary

Phase B (parser port) is **complete**. The full parsing chain works
end-to-end on the Monaco fixture: 89 tests pass, including 11 jvmTest
fixture-backed tests that exercise every sub-file type plus the top-level
`ImgFile` orchestrator. Total parser surface in commonMain:
~2 500 LOC of Kotlin replacing ~4 800 LOC of Java predecessor (compaction
ratio ~52% — fewer files, sealed model types, idiomatic sequences instead
of listener callbacks, no listener/Bag/Context wrappers).

Deferred to later phases:
- B.2.4.b — POI metadata (city/zip/phone) and country/region tables.
  The current parser yields plain labels; richer POI fields land when
  needed.
- TYP styling body parse + StyleResolver — Phase C ("Wire TYP styling
  end-to-end").
- RenderTarget abstraction + Android Canvas / AWT G2D impls — Phase C.
- iOS targets — deferred indefinitely until an iOS consumer materialises.
