# Garmin IMG support — keep & improve, or drop?

## Context

Locus Map currently supports rendering Garmin IMG vector maps via the
`libraries/libGarminImg` git submodule (https://github.com/asamm/garmin-img-parser),
which is a heavily modified Android port of the desktop **JGarminImgParser**
(https://sourceforge.net/projects/jgarminimg/files/jgarminimg/).

The current implementation works but is widely considered "not great":
- Styling is **hardcoded** in `StyleInternal.java` (1 794 lines of Java) — TYP
  files are parsed but ignored, so user/community/Mapwel styling is lost.
- Several incomplete branches survive in the parser (Spanish-language `// TODO
  tipos no extendidos solo` in `SubDivision.java`, multiple unfinished `LBL`/`TRE`
  paths).
- The whole 22 698 LOC Java module has **zero tests** — risky to refactor.
- No commits to the submodule with rendering improvements since 2018; only
  dependency bumps.
- No mention in `docs/reference/pain_areas.md`.
- Helpdesk demand is **low but real**: the long-running idea
  [Garmin IMG — better control of visible content](https://help.locusmap.eu/topic/garmin-img)
  has 65 votes / 80 replies (last meaningful interaction ~2015) and ticket
  [#22569](https://help.locusmap.eu/topic/22569-how-can-locus-display-in-garmin-img-feature-style-properly)
  explicitly asks for proper TYP styling. Most "Garmin" tickets are about
  Garmin watches, not IMG maps.

User's answers:
1. No fresh demand expected, but proper IMG support is a **competitive
   advantage**: keep, don't drop.
2. **Scope = rendering-quality focus** — TYP styling + label polish + extended
   subtypes. **No** routing (NET), **no** address search (MDR).
3. Upstream desktop **JGarminImgParser** exists on SourceForge.
4. **Hard-fork & rewrite to Kotlin** — do *not* try to stay in sync with the
   upstream. Upstream is dormant since 2008; the Asamm fork is already
   irreconcilably divergent. Treat upstream as a **read-only format
   reference**, not as a git remote we track.
5. **New, completely separate KMP repository** (`asamm/kmp-garmin-img`),
   published to GitHub Packages Maven as `com.asamm.kmp:kmp-garmin-img:<ver>`.
   Not a submodule of `locus-core`. Aligns with `kmp-logger`, `kmp-commons`,
   `lopoints-offline-multiplatform`.
6. **iOS = architecture only.** `commonMain` must compile for iOS targets;
   `iosMain` is a stub. Concrete render implementations: Android Canvas
   (`androidMain`) and AWT `Graphics2D` (`jvmMain`). A real iOS render impl
   is deferred to the iOS team.
7. **Old submodule** `asamm/garmin-img-parser` is **archived on GitHub** once
   the new KMP lib reaches feature parity. The Phase A.1 work currently
   sitting uncommitted in the submodule (build.gradle.kts modernisation,
   16 JUnit 5 tests, fixtures README) lands as the **final snapshot commit**
   on the submodule before archive — purely as a reference checkpoint.

## Honest assessment (recommendation: KEEP & IMPROVE, do not drop)

| Factor | Verdict |
|---|---|
| Format alive in the wild | Yes — OSM/mkgmap, Mapwel, OpenMTBMap, Freizeitkarte, custom contour overlays — all unencrypted IMG. |
| Realistic to reach "full-featured renderer for unencrypted maps" | **Yes**, with bounded effort. The parser is 80 % complete; the gap is rendering fidelity (TYP) + a few subtype branches. |
| Routing / search worth doing | No. Out of scope per user. |
| Encrypted (locked) Garmin maps | Permanently out of scope (legal + DRM). |
| Cost to reach the goal | ~3–5 weeks focused (estimate below). |
| Cost to drop | ~1–2 days, but loses a real differentiator and 65-vote feature. |

**Recommendation:** Stand up a new **KMP library** in a separate repository
(`asamm/kmp-garmin-img`), incrementally rewrite the parser + renderer to
Kotlin Multiplatform there, publish via GitHub Packages Maven. Replace the
submodule consumption in `locus-core` with a Maven dependency. This unlocks
"your IMG map looks the same in Locus as on a Garmin device" (the actual
user complaint thread), keeps the door open for iOS / web preview, and
matches Asamm's existing KMP direction.

### Why new KMP repo (not "improve in place" / not "hard-fork submodule")

| Question | Answer |
|---|---|
| Is upstream JGarminImgParser still active? | No — first commit 2008, last meaningful commit 2008–2010, project dormant. |
| Have we ever pulled an upstream change since the fork? | No. The Asamm fork has its own rendering pipeline, hardcoded styles, Android `Canvas` integration — none of that is upstream-shaped. |
| Why a new repo instead of editing the existing submodule? | (a) iOS is on the table — KMP needs `commonMain`/`androidMain`/`jvmMain`/`iosMain` source-set layout, which is awkward to retrofit into the existing flat `lib/src/main/java` Android-library tree. (b) Asamm publishes KMP libs as separate repos (`kmp-logger`, `kmp-commons`); fits the same convention. (c) Clean break from a dormant fork: new history, new licence headers, new package, new Maven coordinates. |
| Why Kotlin / KMP, not Java? | Mismatch with rest of repo (Coroutines / Hilt / Flow); iOS target requires Kotlin; Kotlin equivalent of the ~7 500 LOC parser is ~4 500 LOC; tests written first act as the oracle. |
| What we lose by not syncing upstream | Nothing — there is nothing upstream to receive. We keep the upstream tarball as a **read-only format reference**, alongside mkgmap source. |
| What about the existing submodule | Final snapshot commit (the Phase A.1 work), then **archived on GitHub** once the new lib reaches feature parity. Submodule entry removed from `locus-core/.gitmodules`. |

## Plan of work

The work splits into six phases. Each phase has an exit gate so we can stop
and re-evaluate before the next.

### Status overview (as of 2026-05-04)

| Phase | Status | Summary |
|---|---|---|
| A | ✅ Done | KMP library scaffold (Kotlin 2.3.20 / AGP 9.1.1 / Gradle 9.3.1, Android + JVM targets, GitHub Packages publishing). `CoordUtils` + `BinaryReader` ported as parser oracle. |
| B | ✅ Done | Full IMG parser in `commonMain`: archive / FAT / block-demux / TRE (header + bbox + TRE7 + levels + sub-divisions) / LBL (codings 6/9/10) / RGN (points / polylines / polygons / extended types / BitStreamReader) / NET (label-only) / TYP (header). Sealed `MapObject` + `Sequence<MapObject>` API replaces the Java `MapListener` callback. ~2.5 KLOC Kotlin replaces ~4.8 KLOC Java. |
| C | 🟡 Partial | Done: rendering pipeline, label de-cluttering, area-weighted centroids, AWT impl, golden-PNG regression test, transparent-background support, **TYP body parser v0** (polygon + line records → TYP-aware StyleResolver), Monaco renders to recognisable PNG with TYP-derived styling. Deferred: C.7 v1 (visual quality polish, point/icon parsing, DRAWORDER), C.8 extended-types-subdivision branch, C.9 curve-following text, Android Canvas RenderTarget. |
| D | ⬜ Pending | Documentation + integration polish (docs/format/, docs/upstream_diff.md, README, CHANGELOG release notes, Locus-side adapter wiring). |
| E | ➖ Scope | Out-of-scope log only (NET routing / MDR / encrypted IMG / mkgmap preprocessing). Never "completes" — reopened only if user demand surfaces. |
| F | 🟡 MVP done | Desktop viewer (Compose Multiplatform Desktop). MVP: open IMG, render TRE bbox, reload, AA toggle. Pan / zoom / status-bar polish deferred until C.7 needs them. |

Detailed sub-items live in their respective phase sections below. Completed
phases (A, B) are summarised here; their detailed plan-of-work descriptions
were the design notes used while executing them.


**Repo strategy:** stand up a new repository **`asamm/kmp-garmin-img`** as a
KMP library, published to GitHub Packages Maven as
`com.asamm.kmp:kmp-garmin-img:<ver>`. The existing
`libraries/libGarminImg` submodule remains the in-tree consumer until the
new library reaches feature parity (Phase D), then is removed from
`.gitmodules` and archived on GitHub.

**KMP target set:**
- `commonMain` — parser, model, style resolver, render-target *interface*.
- `androidMain` — Android `Canvas` render target, density / text-scale glue,
  consumer-facing `MapTileGenerator`-equivalent API.
- `jvmMain` — AWT `Graphics2D` render target, `RenderImgToPng` CLI.
- `commonTest` — fixture-backed parser & coord tests.
- `jvmTest` — render-diff tests (reference PNG comparison).

**iOS target — deferred.** PLAN drafts originally included an `iosMain`
stub that compiled but threw `NotImplementedError`. As of Phase A bootstrap
(2026-05-04) iOS is dropped from the build entirely — no `iosArm64` /
`iosSimulatorArm64` targets, no `iosMain` source set. `commonMain` is kept
iOS-clean (no `java.*` / `android.*` / JVM-only kotlinx APIs) so the
targets can be added back in a few lines when an iOS consumer materialises.
Reintroducing iOS will require: adding the targets in `lib/build.gradle.kts`,
adding an `iosMain` source set with the `RenderTarget` `actual` stub, and
re-enabling the iOS jobs in the CI workflows.

**Distribution:** GitHub Packages Maven, version `0.1.0` initial. Group
`com.asamm.kmp`, artifact `kmp-garmin-img`. Snapshot publishes on every push
to `main`; release publishes on tag.

### Phase A — Bootstrap `asamm/kmp-garmin-img` skeleton — ✅ Done

KMP library scaffold (Kotlin 2.3.20 / AGP 9.1.1 / Gradle 9.3.1, Android +
JVM, GitHub Packages publishing). `CoordUtils` + `BinaryReader` ported
from libGarminImg as the Phase B parser oracle; 16 commonTest cases.

See [CHANGELOG.md](CHANGELOG.md#added---phase-a) for landed details.

### Phase B — Parser port to `commonMain` — ✅ Done

Full IMG parser ported to `commonMain`: archive / FAT / block-demux / TRE
(header + bbox + TRE7 + levels + sub-divisions) / LBL (codings 6/9/10) /
RGN (points / polylines / polygons / extended types) / NET (label-only) /
TYP (header). Sealed `MapObject` + `Sequence<MapObject>` API replaces
the Java `MapListener` callback shape. ~2.5 KLOC Kotlin replaces ~4.8 KLOC
Java. Two Monaco fixtures (8.5 MB OpenTopoMap + 152 KB self-built mkgmap).

See [CHANGELOG.md](CHANGELOG.md#added---phase-b) for landed details.

The "Phase B was *file-by-file port preserving working logic*" decision
(rather than clean-room rewrite) is recorded in CHANGELOG; some originally
deferred items (POI metadata, country/region tables) still sit in
`LblSubFile.java` paths we haven't touched and would land as B.2.4.b if
needed.

### Phase C — Rendering pipeline rewrite + quality improvements — 🟡 Partial

Goal: a Locus-rendered IMG looks the same as on a Garmin device of the
same era, given the map's own TYP file.

Sub-items, in priority order:

- [x] **C.1–C.6 — Rendering pipeline.** `Style` / `RenderTarget` /
      `Projection` / `ImgRenderer` / `LabelLayout` in commonMain;
      `AwtRenderTarget` in jvmMain; golden-PNG regression test. Monaco
      renders to a recognisable PNG with `InternalStyles` defaults.
- [x] **C.10 — Label de-cluttering.** bbox-collision + priority sort.
- [x] **C.9 (centroids) — Polygon-area-weighted centroid** for area
      labels. Replaces arithmetic mean — labels stay inside concave /
      elongated shapes. Curve-following polyline text remains deferred.
- [x] **C.11 — Background tile transparency.** `AwtRenderTarget` uses
      `AlphaComposite.Src` for transparent fills (SrcOver-with-alpha-0
      is a no-op); regression-tested against the Monaco fixture.
- [~] **C.7 v0 — TYP body parser + TYP-aware StyleResolver.** Framework
      lands: `TypBodyParser` walks the polygon + line indexes and
      extracts fill / stroke / width per `(type, subType)`;
      `StyleResolver.typAware(table)` consults the TYP first and falls
      back to `InternalStyles`. `ImgFile.typStyles` is auto-populated
      when an IMG has TYP. The viewer + Monaco golden test both
      consume the TYP-aware resolver when available.
      **Follow-ups (C.7 v1):**
      - Visual quality: OpenTopoMap render shows TYP-derived greens /
        blues correctly but line styling looks regressed vs the
        InternalStyles defaults — likely BGR/RGB byte order or
        line-width-units issue. Inspect with the viewer side-by-side
        with QMapShack reference.
      - TYP point body parsing (icons / labels) — currently points fall
        back to `InternalStyles`.
      - DRAWORDER / shape-stacking section — affects polygon
        z-ordering.
      - TYP labels (multilingual). Currently the parser skips past
        label blocks correctly but doesn't surface the strings.
      - Extended-types section in TYP (only base section parses).
- [ ] **C.8 — Extended-types subdivision branch.** Recover the
      `// tipos no extendidos solo` Spanish TODO from libGarminImg.
- [ ] **C.9 (curve text) — Curve-following text along polylines.**
      Currently labels at polyline midpoint vertex; proper text-on-path
      with character rotation along the line is a Phase F (viewer)
      visual-feedback workflow item.
- [ ] **C.12 — Optional tile cache** (per-map LRU on disk; only if
      perf needs it).
- [ ] **Android Canvas RenderTarget impl** in `androidMain` — Locus-side
      consumption; ~150 LOC against the existing `RenderTarget` interface.

For each landed item: regression PNG goes into the test fixtures.

Exit gate: side-by-side comparison (BaseCamp / QMapShack) on three
reference maps shows visually equivalent rendering, ±label placement.

Natural sequencing for the next session: Phase F (desktop viewer) first
so each C.7 TYP-styling change is visually verifiable in <1s, then
C.7 → golden refresh → C.8 / C.9 polish.

### Phase D — Documentation & integration polish — ⬜ Pending

Sub-items:

- [ ] Add `docs/libraries/libGarminImg/` (consumer-facing): format
      overview, supported sub-files, TYP coverage matrix, known
      limitations, how to add a fixture, how to debug a misrendered map.
- [ ] Add a row to `docs/reference/pain_areas.md` (in `locus-core`)
      noting IMG scope (no encrypted, no routing, no MDR search).
- [ ] Add IMG row to the "Topical guides" table at the top of root
      `CLAUDE.md` (also in `locus-core`).
- [ ] Update `docs/reference/structure.md` entry for libGarminImg with
      the new module code if it gains one.
- [ ] Wire the Locus-side adapter (`MapSourceGarminImg.kt`,
      `GarminImgRenderer.kt` in `locus-core`) onto the new `:lib`
      Maven dep, replacing the in-tree submodule consumption.
- [ ] Cut `0.1.0` release on `asamm/kmp-garmin-img` (tag → GitHub
      Release → GitHub Packages publish via existing workflow).

### Phase E — Out-of-scope log — ➖ Scope-only

Items deliberately **not** in scope. Never "completes" — reopened only
if user demand shifts.

Things deliberately **not** in scope and explicitly deferred:
- NET routing — never used by the app's router (LoRouter handles routing).
- MDR address/POI search — duplicates LoPoint/LoMap search.
- Encrypted/locked IMG — see breakdown below.
- mkgmap-style preprocessing — out of scope; we're a renderer.

#### Encrypted IMG — what "out of scope" actually means

Two distinct things are commonly called "encrypted" in the Garmin world. The
Java predecessor `libGarminImg` supported the first, not the second. This
KMP rewrite supports neither.

1. **XOR-obfuscated IMG (trivial — not real encryption).** Single-byte XOR
   across the whole file; the key *is the first byte*. The Java
   `ImgFileInputStream` (lines 38–41, 49, 71–73) handles this in 5 lines:
   read first byte, XOR every subsequent byte. We deliberately dropped this
   path from `ImgRandomAccessReader` in Phase B.1.
   - **Used by:** many community/free maps (some OpenMTBMap variants, some
     Mapwel exports), and as a marker on official maps before the actual
     lock check.
   - **Cost to add back:** ~5 LOC + a constructor flag. Trivial.

2. **DRM-locked IMG (real lock).** The XOR layer plus a separately locked
   sub-file checked against a device-bound unlock code. Without that code
   you literally don't have the key. Used by paid Garmin City Navigator /
   Topo. **Even the Java predecessor cannot display these** — the unlock
   path was never written.
   - **Cost to add:** unbounded. Requires obtaining device unlock codes,
     which is the part that's clearly off-limits.

**Legal stance.** Czech / EU law (Article 6 of Directive 2001/29/EC, Czech
Copyright Act §43) prohibits circumventing "effective technological
measures" on copyrighted works. Whether trivial XOR qualifies as
"effective" is unsettled, but the *content* licence matters more than the
XOR — copying Garmin's commercial maps is infringement regardless of the
XOR layer. Reading XOR'd *unlicensed/community* maps (typically
OSM-derived, ODbL) is widely done by tools like QMapShack without
apparent issue.

**Decision:** keep both forms out of scope. Reasons:
- The legal grey zone is real even if narrow; Asamm shouldn't be the one
  to test it.
- Adding XOR support is a 5-line change behind a constructor flag — easy
  to add later if specific user demand surfaces with clearly
  redistributable maps. Trade-off favours staying clean.
- Real DRM-locked maps are unreachable without device-bound unlock codes;
  no scenario where Locus could legally display them.

**Where this would be revisited:** if helpdesk demand materialises for
opening community-distributed XOR'd maps with unambiguous redistribution
licences. Reopen Phase E at that point with a narrow scope: "XOR
obfuscation only; locked sub-files remain unsupported".

If Phase C hits unexpected complexity (e.g., TYP styling reveals deep
parser bugs that demand rewriting `RgnSubFile`), reassess at the Phase B/C
boundary whether to keep going or freeze IMG at "current state + tests".

### Phase F — Desktop viewer (development tool) — 🟡 MVP done

Standalone development tool — **not** a deliverable, but a productivity
multiplier for Phase C.7 (TYP body parser) work and for ad-hoc inspection
of unknown IMGs. Lives in a new Gradle module so the published `:lib`
artifact stays UI-framework-agnostic.

**Stack: Compose Multiplatform Desktop.**
- Same Compose APIs Locus already uses on Android.
- Declarative state-driven UI maps cleanly to viewport
  (`mutableStateOf<Viewport>` + recomposition on pan/zoom).
- Multiplatform path: the same code could become an Android preview app
  or browser viewer later if there's demand.

**Module layout:**
```
viewer/
  build.gradle.kts           # JetBrains Compose plugin + :lib dep + JDK 17
  src/main/kotlin/com/asamm/garminimg/viewer/
    ViewerApp.kt             # @Composable entry point, window scaffolding
    ViewerState.kt           # mutableState for ImgFile + Viewport + zoom
    MapPanel.kt              # @Composable map area + gesture handlers
    Toolbar.kt               # file picker, screenshot, info readout
```

**Implementation path (estimated half-day to a full day):**
1. Gradle wiring — JetBrains Compose plugin (`org.jetbrains.compose`),
   `compose.desktop.currentOs` runtime, `compose.material3`. JDK 17+.
2. MVP screen (~1 hour): file picker → `ImgFile.open()` → render via
   the existing `AwtRenderTarget` to a `BufferedImage` → wrap as Compose
   `ImageBitmap` → display in `Image` composable.
3. Pan/zoom (~1–2 hours): pan with `Modifier.pointerInput { detectDragGestures }`,
   zoom with `Modifier.onPointerEvent(PointerEventType.Scroll)`. Pragmatic
   approach — re-render on gesture-end (snappy enough for country-scale
   fixtures); tile-shift during pan as a later optimisation.
4. Polish (~half-day): file picker dialog (AWT `FileDialog`), status bar
   showing current bbox + zoom + feature count, screenshot button that
   exports current view as PNG, optional hide/show layers (POI / road /
   contour toggles).

**Path 1 (BufferedImage wrap)** is the recommended starting point.
**Path 2** would be to write a `ComposeRenderTarget` that draws directly
via `DrawScope` — avoids the BufferedImage intermediate, better for
animation; ~2× the code. Worthwhile if the BufferedImage path turns out
to lag on >5 MP renders.

**MVP landed (commit pending).** Compose Multiplatform Desktop 1.7.3,
JetBrains Kotlin Compose plugin, JVM 17 toolchain. Three-row layout:
toolbar (Open IMG / Reload / AA toggle) → map area (Compose `Image`
backed by our `AwtRenderTarget`'s `BufferedImage`) → status bar
(file name + description + bbox + TYP presence). Run with
`./gradlew :viewer:run`.

**Deferred polish — pick up alongside C.7 if friction surfaces:**
- Pan via `Modifier.pointerInput { detectDragGestures }` + viewport
  bbox shift on gesture-end.
- Zoom via `onPointerEvent(PointerEventType.Scroll)` — focal-point
  centred, geometric zoom factor.
- Recent-files menu, screenshot button, layer toggles
  (POI / road / contour visibility).

**Considered and rejected — MapLibre as the renderer.** A path where
our lib produces RGBA tiles per (z, x, y) and MapLibre Native renders
them via a Raster Source is *technically* feasible — the tile-shaped
output is a 30-line wrapper around `ImgRenderer.render()`. However
for the Phase F dev-viewer use case it adds:
- a heavy MapLibre Native binary dependency,
- tile-cache invalidation friction on every TYP code change (which
  defeats the "fast feedback for TYP iteration" purpose), and
- two-component (tile producer + tile consumer) plumbing that's
  unnecessary for single-image inspection.
For Phase D (Locus integration) the tile-output API may still be
useful — Locus's existing renderer or any other tile-display engine
could consume it. That's a 30-line follow-up on `ImgRenderer`,
parked.

**Quick CLI alternative.** A bare `cli/` module with a one-class `RenderImgToPng`
main is a 30-minute bolt-on if scripted PNG generation is needed before
the full viewer lands. Args: `(img_path, west, east, south, north, w, h, out_path)`.

## Critical files modified

In `libraries/libGarminImg/` (after Phase B/C the tree becomes Kotlin under
`src/main/kotlin/`; Java tree is removed):
- All of `src/main/java/org/free/garminimg/**` and
  `src/main/java/com/asamm/garminimg/**` → ported to
  `src/main/kotlin/com/asamm/garminimg/**` and deleted.
- New package layout (suggested):
  - `com.asamm.garminimg.parser.*` — sub-file readers (sealed types).
  - `com.asamm.garminimg.model.*` — domain types (Label, SubDivision,
    Coord, Bounds).
  - `com.asamm.garminimg.style.*` — `StyleResolver`, `StyleInternal`
    fallback, `Typ*` model + parser.
  - `com.asamm.garminimg.render.*` — `RenderTarget` interface,
    `AndroidCanvasTarget`, `AwtTarget`, painters, label declutter.
  - `com.asamm.garminimg.io.*` — `BinaryReader`, endian utilities.
- New: `src/test/kotlin/...` — JUnit 5 tests.
- New: `src/test/resources/fixtures/*.img`, `*.typ`, `reference/*.png`.
- New: `desktop/` Gradle module + `RenderImgToPng` CLI (Kotlin).
- New: `docs/format_reference.md` — distilled from upstream + mkgmap, our
  own words; the upstream tarball does **not** live in the repo.

In `libraries/libLocusCore/`:
- `MapSourceGarminImg.kt`, `GarminImgRenderer.kt` — should not need to change
  beyond passing TYP path through. Verify.

In top-level repo:
- `docs/libraries/libGarminImg/README.md` (new page).
- `docs/reference/pain_areas.md` — add row.
- `CLAUDE.md` topical-guides table — add row.

Reused existing utilities (do not duplicate):
- `MapThemesUtils.kt` for density / text scale propagation.
- `libAndroidCore` LRU disk cache for the optional tile cache step.
- Repo's existing JUnit 5 test conventions
  ([`docs/guides/testing.md`](docs/guides/testing.md)).

## Verification

End-to-end checks before each phase exit:
1. `./gradlew :libGarminImg:test` green (Phase A onwards).
2. `./gradlew :libGarminImg:desktop:run --args="<fixture> <bbox> <zoom> out.png"`
   produces a sane PNG for every fixture.
3. `./gradlew assembleMapGooglePlayAfaDebug` builds; Locus Map debug APK
   opens each fixture IMG and renders without ANR or visible regression
   against a pre-Phase-A screenshot baseline.
4. Manual visual diff against BaseCamp / QMapShack on:
   - OSM/mkgmap-built region (no TYP).
   - OpenMTBMap (rich TYP).
   - Freizeitkarte (custom TYP).
5. Re-test ticket [#22569](https://help.locusmap.eu/topic/22569-how-can-locus-display-in-garmin-img-feature-style-properly)'s
   Mapwel custom TYP: feature symbols + colours match Mapwel preview.
6. `./gradlew :libGarminImg:lint` clean.

## Effort & risk summary

| Phase | Effort | Risk |
|---|---|---|
| A — self-contained + tests (still Java) | ~1 wk | Low. Mostly mechanical. |
| B — Kotlin rewrite of parser | 1.5–2 wk | Medium. Mitigated by Phase A tests as oracle. |
| C — Kotlin rewrite of render + TYP + subtypes + labels | 2–3 wk | Medium. TYP format has quirks; mitigated by mkgmap docs and visual fixtures. |
| D — docs | 2–3 d | None. |
| **Total** | **5–7 weeks one engineer** | Bounded. |

Stop-loss: if Phase B reveals the parser has fundamental data-loss bugs that
TYP can't paper over, freeze at Phase A (we still got: tests, desktop runner,
unchanged behaviour) and revisit drop vs deeper rewrite.

## Sources

- [asamm/garmin-img-parser (current fork)](https://github.com/asamm/garmin-img-parser)
- [JGarminImgParser upstream on SourceForge](https://sourceforge.net/projects/jgarminimg/files/jgarminimg/)
- [mkgmap (TYP / IMG format reference)](https://github.com/openstreetmap/mkgmap)
- Helpdesk: [Garmin IMG visible content idea](https://help.locusmap.eu/topic/garmin-img),
  [#22569 Garmin IMG feature style](https://help.locusmap.eu/topic/22569-how-can-locus-display-in-garmin-img-feature-style-properly)
