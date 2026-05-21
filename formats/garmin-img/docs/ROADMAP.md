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
- [x] **Phase D.1 — locus-core swap finalised** — legacy
  `libraries/libGarminImg` submodule + `MapSourceGarminImg` /
  `GarminImgRenderer` classes removed in locus-core PR #771. The KMP
  artifact is the only IMG implementation on `development`.
- [x] **0.5.1 release** — 8 KB read-ahead buffer in
  `FileImgRandomAccessReader` collapses per-byte JNI cost; per-tile
  parse on country IMGs dropped ~65× (verified on-device).
- [x] **0.5.2 release** — primitive `IntArrayList` in
  `com.asamm.garminimg.utils.collections` swapped in for the RGN
  coord decoder's `ArrayList<Int>` accumulators (no more per-coord
  boxing).

## Open

- [ ] **Phase E — archive the legacy upstream.**
  Archive `https://github.com/asamm/garmin-img-parser` (the old
  `libGarminImg` upstream). Add a one-line README on that repo
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
  Monaco screenshot anchors.

- [ ] **Helpdesk-driven feature follow-ups** — waiting on user feedback
  on the 4.35 Locus build before committing to scope. From the long
  threads on
  [#5474](https://help.locusmap.eu/topic/hello) and
  [#3731](https://help.locusmap.eu/topic/garmin-img):
  - configurable label text colour (Locus-side `StyleResolver` wrap)
  - global / per-type line-thickness + line-colour overrides
  - layer toggles (areas / lines / points) exposed as Locus prefs
  - external `.typ` file loading next to the `.img` — highest-value
    item; lib has the parsing primitives, only Locus-side
    file-discovery + wiring needed
  - extended objects (truck-routing signs, weight limits, road-blocks)
  - per-type visibility prefs (hide buildings / forest labels / …)
