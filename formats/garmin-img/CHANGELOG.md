# Changelog

All notable changes to this project will be documented in this file.
This project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.5.2] — 2026-05-21

Follow-up parser perf pass on top of 0.5.1's read-ahead buffer.

- Added primitive-int `IntArrayList` in
  `com.asamm.garminimg.utils.collections`. Shape mirrors the
  locus-core equivalent in `libUtilsBase` so the API feels familiar;
  copied rather than depended on (the lib stays free of any
  locus-core dep per CLAUDE.md).
- `RgnSubFile.decodePolyCoordinates` swapped its two
  `ArrayList<Int>` coord accumulators for the new
  `IntArrayList` — no more per-coord boxing in the parser's hot loop.
  Bench delta on the desktop (CzechRep z17, local SSD):
  parse-only −23 % (6.2 ms → 4.8 ms), full render −7 %
  (9.5 ms → 8.8 ms). Larger relative gain expected on Android where
  GC pressure costs more.
- Parser parity preserved — DumpDiff regression rig stays green and
  the otm-monaco golden render is unchanged.

## [0.5.1] — 2026-05-21

Performance fix for country-scale IMGs.

- `FileImgRandomAccessReader` gained an 8 KB read-ahead buffer.
  The RGN bit-stream decoder reads byte-at-a-time; on a 192 MB
  country IMG that was thousands of `RandomAccessFile.read()` JNI
  crossings per polyline. On the desktop bench (CzechRep z17, local
  SSD) per-tile parse dropped from 402 ms to 6 ms, and the
  end-to-end `ImgRenderer.render` from 471 ms to 10 ms. Verified
  on-device through Locus. Memory cost is one 8 KB ByteArray per
  open sub-file reader (≤ 48 KB per open map).
- New `RenderBenchmarkTest` + `TimingRenderTarget` under
  `lib/src/jvmTest/.../bench/` — diagnostic harness, point at any
  IMG via `-Dkmpgi.bench.img=…`. Doesn't assert; only prints
  per-phase timings.

## [0.5.0] — 2026-05-20

First public release. Drop-in replacement for the legacy
`libraries/libGarminImg` submodule in
[locus-core](https://github.com/asamm/locus-core).

- Full IMG parser in `commonMain` — TRE / RGN / LBL / NET / TYP.
  Byte-parity with the legacy Java parser on the OpenTopoMap and
  mkgmap Monaco fixtures (~99.999 % matching records, validated by
  a dump-diff regression rig in `jvmTest`).
- `FileImgRandomAccessReader` (`jvmAndAndroidMain`) — `RandomAccessFile`-
  backed reader so country-scale (200 MB+) and multi-GB IMGs work on
  phones without OOM. `ByteArrayImgRandomAccessReader` stays for
  small in-memory fixtures and tests.
- `ImgFile` and `ImgRandomAccessReader` both implement `AutoCloseable`
  so consumers can wrap an `ImgFile` in `.use { … }` and release the
  underlying file descriptors deterministically. The byte-source-factory
  overload of `ImgFile.open` owns every reader it builds; the
  explicit-readers overload leaves ownership with the caller.

Pre-tag hardening, driven by a deep-review pass on the public API
surface:

- Concurrency: dropped the public mutable diagnostic counters on
  `RgnSubFile` (a global `object` shared across threads), added an
  explicit "not thread-safe; one `ImgFile` per worker" contract to
  `ImgFile` + `ImgRenderer`.
- Malformed-input DoS: bounded every length-from-disk allocation across
  the parser (TRE / TYP / sub-division / FAT). Capped per-polyline
  `bitStreamLen`. Guarded `1 shl exp` reads against out-of-range
  exponents. Tightened POI / road-definition offset bounds.
- Resource leaks: factory-overload of `ImgFile.open` now owns its
  readers and releases them on `close()`; `FileImgRandomAccessReader`
  closes its `RandomAccessFile` if construction fails mid-way.
- Renderer: identity-keyed pattern caches on both backends (was
  walking the bitmap byte-array per polygon lookup), per-resolver
  Style cache (was allocating ~10k Style instances per tile per
  frame), aligned polygon outline stroke + on-path font sizing
  across AWT and Android backends.
- API hygiene: demoted `data class` to `class` on public types where
  `.copy()` ABI evolution is likely (`ImgFile.{Tre,Rgn,Lbl,Net}Data`,
  `SubDivision`, `Style.{Point,Line,Polygon}Style`, all `Typ*Style`
  types). Sub-file readers now `internal val` so consumers can't
  accidentally race the parser's seek pointer.
- Publishing: artifacts now ship a `-sources.jar` alongside the AAR
  / JAR for IDE source navigation.
- `ImgRenderer` + platform-agnostic `RenderTarget` interface in
  `commonMain`. TYP-driven styling via `StyleResolver.typAware`;
  `StyleResolver.internalDefaults` for IMGs without an embedded TYP.
- `AwtRenderTarget` (`jvmMain`) — desktop Graphics2D backend; powers
  the desktop viewer and the golden-PNG regression tests.
- `AndroidRenderTarget` (`androidMain`) — `android.graphics.Canvas`
  backend with feature parity (patterned polygons / polylines,
  on-path text with line lift, dashed-from-pattern strokes, fill +
  pattern stack, 8-direction text halo, asymmetric-tile y-anchor,
  no-corner-overshoot patterned-polyline clip).
- `:viewer:desktop` — Compose Desktop dev tool with pan/zoom, layer
  toggles, anti-alias toggle, "Save view" PNG+JSON export to a
  project-local `captures/` folder.
- `:viewer:android` — minimal one-Activity dev tool for verifying
  the Android target on real device hardware.

Maven coordinate: `com.asamm.kmp:kmp-garmin-img-android:0.5.0`,
published to `https://maven.pkg.github.com/asamm/kmp-garmin-img` by
the tag-triggered release workflow.

> Pre-0.5 work history (one entry per landed Phase A → C slice) was
> moved to [`docs/archive/CHANGELOG-pre-0.5.md`](docs/archive/CHANGELOG-pre-0.5.md).
