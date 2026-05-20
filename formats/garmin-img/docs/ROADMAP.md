# Roadmap

Remaining work for the project. Completed items are checked with a short
one-line note; open items carry enough context for the next contributor
(or future-me) to act without re-reading the original plan.

Full original plan archived at
[`docs/archive/PLAN-original.md`](docs/archive/PLAN-original.md).

## Completed

- [x] **Phase A** — KMP skeleton, GitHub Packages publishing, seed utilities.
- [x] **Phase B** — Full IMG parser in `commonMain` (TRE / RGN / LBL / NET /
  TYP), byte-parity with the legacy Java parser on Monaco fixtures.
- [x] **Phase C — core renderer** — `ImgRenderer`, `RenderTarget`,
  `StyleResolver` (TYP + internal defaults), polygon priority via
  shape-stacking, two-pass polyline draw, label de-clutter,
  patterned-polyline + asymmetric-tile + corner-clip fixes.
- [x] **Phase C — AWT target** — desktop `Graphics2D` backend with golden-PNG
  regression test.
- [x] **Phase C — Android target** — `android.graphics.Canvas` backend with
  feature parity to the AWT target.
- [x] **Phase F — viewers** — Compose Desktop viewer + minimal Android
  dev viewer.
- [x] **File-backed IMG reader** — `FileImgRandomAccessReader` (in
  `jvmAndAndroidMain`) replaces `ByteArrayImgRandomAccessReader` for
  the locus-core connector. Heap footprint independent of file size;
  country-scale (200 MB+) and multi-GB IMGs work on phones.
- [x] **0.5.0 release** — first public artifact on GitHub Packages.

## Open

- [ ] **Phase D.1 — finalise locus-core swap.**
  The `development` branch of locus-core now consumes
  `com.asamm.kmp:kmp-garmin-img-android` and routes `.img` maps through
  `MapSourceKmpGarminImg` (gated behind a compile-time flag). Once the
  KMP path has been the default for a few internal builds and no
  regressions surface, remove the legacy `MapSourceGarminImg` /
  `GarminImgRenderer` and the `libraries/libGarminImg` submodule
  reference from locus-core's `settings.gradle.kts` +
  `libLocusCore/build.gradle.kts`.

- [ ] **Phase E — archive the legacy upstream.**
  After Phase D.1, archive `https://github.com/asamm/garmin-img-parser`
  (the old `libGarminImg` upstream). Add a one-line README on that repo
  pointing at this project as the successor.

- [ ] **Phase C polish — point icons & extended-types polygons.**
  `StyleResolver.typAware` currently falls back to `InternalStyles`
  for point-icon styling because the TYP point record's icon-bitmap
  branch isn't wired through. Extended-types polygons (the `≥ 0x100`
  range) parse but skip TYP styling. Both are visual-only — the
  parser sees the records — so the diff vs. QMS is "less polished",
  not "missing data".

- [ ] **Phase C polish — label collision tuning.**
  `LabelLayout` already does same-text dedup within ~200 px, but at
  wide overviews short labels (city names) still occasionally pile up
  at level-pick boundaries. A priority bucket per label kind plus a
  collision pass against the actually-drawn label rects (not just the
  candidate rects) would clean this up.

- [ ] **iOS target reintroduction.**
  `commonMain` has stayed iOS-clean since Phase A. Adding `iosX64`,
  `iosArm64`, `iosSimulatorArm64` to `lib/build.gradle.kts` should
  be a few lines, plus an `iosMain` `RenderTarget` impl (Core
  Graphics?). Out of scope until an iOS consumer asks.

- [ ] **Docs polish.**
  `docs/visual-reference/` needs a short README explaining what the
  Monaco screenshot anchors. `docs/upstream_diff.md` referenced by
  `CLAUDE.md` doesn't exist yet — either write a one-pager
  documenting what was kept / dropped / rewrote vs. the Java fork,
  or drop the reference.
