# Changelog

All notable changes to this project will be documented in this file.
This project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

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
