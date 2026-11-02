# Changelog — pre-0.5 work history

> Snapshot of the original CHANGELOG before the 0.5.0 release. The
> entries below tracked the Phase A bootstrap through the C-series
> rendering work, one entry per landed slice. Kept here for historical
> context; the project-root CHANGELOG.md from 0.5.0 onward records only
> released versions.

## [0.5.0] — 2026-05-20

First public release. The library is now consumable from
[locus-core](https://github.com/asamm/locus-core) as a drop-in
replacement for the legacy `libraries/libGarminImg` submodule.

What's included:

- Full IMG parser in `commonMain` — TRE / RGN / LBL / NET / TYP sub-files.
  Byte-parity with the legacy Java parser on the OpenTopoMap Monaco and
  mkgmap Monaco fixtures (~99.999 % matching records on a side-by-side
  dump diff).
- `ImgRenderer` + platform `RenderTarget` interface in `commonMain`.
- `AwtRenderTarget` in `jvmMain` — desktop Graphics2D backend, used by
  the JVM viewer and the golden-PNG regression tests.
- `AndroidCanvasRenderTarget` in `androidMain` — `android.graphics.Canvas`
  backend with equivalent feature coverage (patterned polygons /
  polylines, on-path text with line lift, dashed-from-pattern strokes,
  fill + pattern stack, 8-direction text halo, asymmetric-tile y-anchor,
  no-corner-overshoot patterned-polyline clip).
- TYP-driven styling via `StyleResolver.typAware`, fallback
  `StyleResolver.internalDefaults` for IMGs without an embedded TYP.
- `:viewer:desktop` — Compose Desktop dev tool with pan/zoom, layer
  toggles, anti-alias toggle, "Save view" PNG+JSON export to a
  project-local `captures/` folder. Anchors visual regression review.
- `:viewer:android` — minimal one-Activity dev tool that verifies the
  Android target on real device hardware.

Maven coordinate: `com.asamm.kmp:kmp-garmin-img-android:0.5.0` (Android
artifact) — published to GitHub Packages at
`https://maven.pkg.github.com/asamm/kmp-garmin-img` by the
tag-triggered release workflow.

Versioning note: skipping straight from `0.1.0` to `0.5.0` reflects the
amount of work between Phase A bootstrap and the feature-complete
parser + dual-target renderer that ships here. The minor digit jumps
again once Phase E (old submodule archived in locus-core) lands.

## [0.1.0] — superseded

Phase A bootstrap + Phase B.1 first slice. Sets up the Kotlin Multiplatform
skeleton for Android + JVM, ports the seed utilities and the random-access IMG
reader from the Java predecessor `libGarminImg`, and wires GitHub Packages
publishing.

### Added — Phase C.7 v1 (DRAWORDER / shape-stacking)

Polygon z-ordering now driven by the TYP's shape-stacking section
(header offset 0x51), the highest-impact remaining gap from C.7 v0.

- `TypHeader.shapeStacking` descriptor at offset 0x51.
- `TypBodyParser.parseShapeStacking` — reads 5-byte entries (byte type +
  UInt32 subTypeMask), file position = priority. Empty entries
  (zoom-level separators) are kept to preserve priority indices.
- `TypBodyParser.parse` looks up each `(type, subType)` polygon style's
  priority via `findPriority` (plain types match by `type` byte;
  extended types `≥ 0x100` match the type modulo 256 with the subType
  bit set in `subTypeMask`). Result lands on
  `TypPolygonStyle.drawPriority`.
- `StyleResolver.typAware` propagates the TYP priority through to
  `Style.PolygonStyle.drawPriority`.
- `ImgRenderer.render` was previously "iterate-and-draw"; now buckets
  every map object into polygons / polylines / points, **stable-sorts
  polygons ascending by drawPriority**, then draws polygons first
  (background-first), polylines next, points last. Memory-wise this
  pre-collect is fine for country-scale fixtures (Monaco yields a few
  hundred polygons); a streaming variant for continent-scale renders
  would be a later optimisation.

Visible result on Monaco: white residential areas now show under the
green forest patches rather than being painted over. Map structure
matches QMapShack's reference layout much more closely. The remaining
gap is mostly point-icon styling and minor type-specific quirks
(e.g. type 0x04 built-up polygons render as opaque red where
QMapShack shows a subtle pink tint — likely an alpha-channel issue
in TYP-record parsing for that specific type).

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

### Added — Phase C (rendering pipeline)

End-to-end renderer producing a recognisable PNG for the Monaco fixture
under 200 LOC of test code.

- `com.asamm.garminimg.style.Style` — sealed Point/Line/Polygon style
  records. Each carries a draw priority for label de-cluttering.
- `com.asamm.garminimg.style.Color` — `@JvmInline` value class wrapping
  ARGB; `Bitmap` data class for icons / fill patterns.
- `com.asamm.garminimg.style.InternalStyles` — hardcoded fallback styles
  covering common type ranges (motorway / principal / arterial / local
  roads, railway, river, contour, water / forest / built-up areas,
  buildings, city / POI labels). Replaces a small subset of the Java
  predecessor's `StyleInternal.java`.
- `com.asamm.garminimg.render.RenderTarget` interface with
  beginFrame / drawPolygon / drawPolyline / drawPoint / drawText /
  drawBitmap / endFrame. Top-left origin, screen-pixel coords.
- `com.asamm.garminimg.render.AwtRenderTarget` (jvmMain) — `Graphics2D`
  + `BufferedImage` impl, with PNG writer.
- `com.asamm.garminimg.render.Projection` — Web Mercator (with
  near-pole clamping); equirectangular fallback for tests.
- `com.asamm.garminimg.render.Viewport` — screen-pixel size + Garmin-
  coord bounding box, `Viewport.ofTreBounds` convenience.
- `com.asamm.garminimg.render.ImgRenderer` — walks `ImgFile.readMapInBounds()`,
  resolves styles via `StyleResolver`, dispatches to a `RenderTarget`,
  collects label candidates, then emits non-overlapping labels in
  priority order in a second pass.
- `com.asamm.garminimg.render.LabelLayout` — bbox-collision label
  de-clutterer.
- `com.asamm.garminimg.render.StyleResolver` `fun interface` —
  pluggable; `internalDefaults()` is used for now, a TYP-aware
  variant lands in a follow-up session.
- `lib/src/jvmTest/resources/fixtures/golden/otm-monaco-tight.png` —
  golden reference rendered without anti-aliasing for byte-deterministic
  comparison across JDKs.
- `MonacoRenderTest.tightMonacoMatchesGoldenPng` — golden-PNG diff
  regression test (pixel fraction tolerance 1%, per-channel tolerance 8).

Deferred to a future session (architecture supports them without
rework):
- TYP body parser (POINTS / LINES / POLYGONS / ICONS / DRAWORDER) +
  TYP-aware StyleResolver. The TYP header is parsed; the body uses a
  variable-record format with bitmap-encoded patterns and per-type
  multilingual labels — ~1 KLOC of careful porting work.
- Android Canvas RenderTarget impl (Locus consumption side).
- Curve-following text along polylines (currently labels at midpoint).

### Added — Phase C.7 v0 (TYP body parser)

The TYP styling framework is in place — TYP-styled polygons and lines
now use the IMG's own colour palette instead of our hardcoded fallback.

- `com.asamm.garminimg.parser.TypBodyParser` walks the polygon and
  line indexes (header descriptors at offsets 0x33 / 0x3D / 0x47),
  parses each record's scheme byte + BGR colour triples + bitmap-skip /
  label-skip / extended-font-skip, and produces a `TypStyleTable` of
  `(type, subType) → (fillColor, strokeColor, widths)`.
- `TypHeader` extended with `pointIndex`, `lineIndex`, `polygonIndex`
  descriptors (offset / itemSize / size).
- `StyleResolver.typAware(table)` — looks up `(type, subType)` in the
  TYP first, falls back to `InternalStyles` for unmatched pairs;
  preserves InternalStyles defaults (border, label colour) where the
  TYP doesn't override.
- `ImgFile.typStyles` auto-populated when an IMG has a TYP sub-file.
- Viewer + golden test now use the TYP-aware resolver when available.
- Handles mkgmap's "simple 2-colour polygon" special case where the
  scheme byte is patched to set all transparency flags but only one
  BGR triple is actually written — naive reading drifts the byte
  cursor by 3.

Format reference: mkgmap source `uk.me.parabola.imgfmt.app.typ.*`
(read-only — no code copied; LGPL/GPL hygiene per CLAUDE.md).

**Known C.7 follow-ups (next iteration with the viewer):**
- Line styling looks regressed vs InternalStyles in the Monaco render
  — likely a byte-order or width-units issue inside line record
  parsing. Visible-but-fixable when a side-by-side reference (Locus /
  QMapShack) is at hand.
- Point / icon body parsing (icons skipped; points still use
  InternalStyles).
- DRAWORDER / shape-stacking section for polygon z-ordering.
- TYP labels (multilingual strings) — skipped past correctly but not
  surfaced.

### Improved — Phase F polish + InternalStyles ocean coverage

- `InternalStyles.resolvePolygon` now covers more water type codes
  (0x28, 0x32, 0x4A..0x4C in addition to the existing 0x3B..0x44).
  OpenTopoMap's huge Mediterranean polygon now renders as blue —
  previously fell to the light-grey `DEFAULT_POLYGON` and was
  invisible against the beige background. Added a separate
  `AREA_OCEAN_BACKGROUND` style with cooler blue + lower draw
  priority so smaller water bodies still pop on top.
- Refreshed the Monaco golden PNG to reflect the new ocean coverage.
- `:viewer` now persists across restarts via `java.util.prefs.Preferences`
  (`ViewerPrefs`):
  - Window width/height (saved on resize, distinct-until-changed).
  - AA toggle (saved on change).
  - Last-opened IMG path (saved on `loadAndRender`).
  - Auto-loads the last file on launch if it still exists.
  Cross-platform: Windows registry, macOS plist, Linux XDG config.

### Added — Phase F MVP (desktop viewer)

New `:viewer` Gradle module — a Compose Multiplatform Desktop
development tool. **Not** a published artifact; lives in its own module
so the published `:lib` artifact stays UI-framework-agnostic.

- Compose Multiplatform 1.7.3 + Kotlin Compose compiler plugin + JVM
  17 toolchain. Plugins added to root `build.gradle.kts` as
  `apply false` so versions are catalog-managed.
- `viewer/src/main/kotlin/com/asamm/garminimg/viewer/`:
  - `Main.kt` — `application { Window(...) }` entry point.
  - `ViewerState.kt` — mutable state: loaded file, rendered
    `BufferedImage`, status, AA toggle. `loadAndRender(file)` opens an
    IMG via `ImgFile.open()`, renders the TRE bbox at 1024×1024 via
    `AwtRenderTarget`, populates status with description + bbox + TYP
    presence.
  - `ViewerApp.kt` — three-row Compose layout: toolbar (Open IMG /
    Reload / AA toggle) → map area (Compose `Image` from
    `BufferedImage.toComposeImageBitmap()`) → status bar.
  - File picker via AWT `FileDialog` (Compose Desktop has no native
    picker yet); `*.img` filter as a hint.

Run with `./gradlew :viewer:run`. Native bundles via
`./gradlew :viewer:packageDistributionForCurrentOS` (Dmg / Msi / Deb).

Deferred polish (will arrive when Phase C.7 friction calls for it):
- Mouse pan + scroll-wheel zoom.
- Recent-files menu, screenshot button, layer toggles.

### Added — Phase C.9 (centroids) + C.11
- `ImgRenderer.centroid` now uses the polygon-area-weighted geometric
  centroid (signed-area + first-moment formula) instead of the simple
  arithmetic mean. Area labels stay inside concave / elongated shapes
  (lakes, fjords, narrow buildings) where the arithmetic centroid drifts
  outside. Falls back to arithmetic mean for degenerate (zero-area)
  polygons. `CentroidTest` (commonTest) covers L-shape, square, and
  collinear-degenerate cases.
- `AwtRenderTarget.beginFrame` uses `AlphaComposite.Src` for the
  transparent-background path so SrcOver-with-alpha-0 doesn't no-op.
  Tile renders with `Color.Transparent` background now reliably leave
  unpainted pixels at alpha=0 — regression-tested via
  `BackgroundTransparencyTest` against Monaco.

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
