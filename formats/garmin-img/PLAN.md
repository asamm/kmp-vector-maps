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

### Phase A — Bootstrap `asamm/kmp-garmin-img` skeleton (1 week)

Goal: empty KMP library, builds for the active targets, tests run on JVM,
published locally to `mavenLocal()`.

Steps:
1. Create the new GitHub repo `asamm/kmp-garmin-img`. Licence: same as
   submodule (LGPL — derived work). Initial branch `main`.
2. Gradle skeleton:
   - `settings.gradle.kts` with `:lib` (and later `:cli` for the desktop CLI).
   - Root `build.gradle.kts` with KMP plugin, Android library plugin,
     version-catalog `libs.versions.toml` mirroring the relevant versions
     from `locus-core` / `kmp-logger` (Kotlin 2.3.20, AGP 9.1.1, Gradle
     9.3.1, JDK 17).
   - `lib/build.gradle.kts`:
     - `kotlin { android { ... }; jvm() }` — iOS targets deferred (see
       above); `iosMain` source set is **not** created.
     - `commonMain` / `commonTest` source sets, `androidMain`,
       `jvmMain` + `jvmTest`.
     - JUnit 5 wired in `jvmTest` via `kotlin("test-junit5")`.
   - Maven publishing: `maven-publish` plugin pointed at GitHub Packages
     (`https://maven.pkg.github.com/asamm/kmp-garmin-img`); credentials via
     `GPR_USERNAME` / `GPR_TOKEN` (with `REPO_GITHUB` accepted as a
     username fallback to match `locus-core`).
   - `kmp-logger` consumed as a real `commonMain` `api` dependency from
     GitHub Packages, both as the future log channel and as a structural
     reference for the scaffold.
   - GitHub Actions: ubuntu-only `build` job (no macOS runner); manual
     `snapshot` workflow; tag-driven `release` workflow.
3. Port the Phase A.1 tests from the old submodule into `commonTest`:
   - `CoordUtilsTest` (8 cases) — translate the Java to Kotlin, no logic
     change. Drives a tiny temporary Kotlin port of `CoordUtils` in
     `commonMain/kotlin/com/asamm/garminimg/coord/CoordUtils.kt`. This is
     the first commonMain code in the repo and proves the cross-target build
     works.
   - `EndianDataInputStreamTest` (8 cases) → `BinaryReaderTest` — translate
     to test a Kotlin `BinaryReader` built on `kotlinx-io-core` in
     `commonMain/kotlin/com/asamm/garminimg/io/BinaryReader.kt`.
4. Verify locally:
   - `./gradlew :lib:jvmTest` — green (16 tests).
   - `./gradlew :lib:build` — green (Android + JVM).
   - `./gradlew :lib:publishToMavenLocal -PisSnapshot -PsnapshotSuffix=<sha>`
     — installs `com.asamm.kmp:kmp-garmin-img:0.1.0-dev.<sha>`.

Exit gate: `kmp-garmin-img:0.1.0-dev.<sha>` is consumable from a throwaway
test Gradle project; `:lib:build` green.

Note: the existing `libraries/libGarminImg` submodule is **untouched**
during Phase A — Locus continues to render IMG via the old code. The
Phase A.1 work I already did locally (build.gradle.kts + 16 tests +
fixtures README) stays uncommitted in the worktree until Phase E
("final-snapshot commit, then archive").

### Phase B — Parser port to `commonMain` (2 weeks)

Goal: complete IMG parser running in `commonMain`, fixture-tested on JVM,
zero `android.*` references.

Steps:
1. Pull the upstream tarball
   (https://sourceforge.net/projects/jgarminimg/files/jgarminimg/) to a
   working directory **outside both repos**. Read alongside the existing
   submodule and the relevant mkgmap classes
   (`uk.me.parabola.imgfmt.app.*`). Note every stubbed/commented branch in
   the Asamm fork (`// TODO tipos no extendidos solo` in `SubDivision.java`,
   `// TODO: implement that...` in `LblSubFile.java`) and decide:
   recover / drop / rewrite.
2. Add the first IMG fixture under `lib/src/commonTest/resources/fixtures/`
   (small mkgmap-built OSM extract, ODbL attribution alongside). The same
   fixture acceptance rules from the old submodule's
   `fixtures/README.md` apply — port that README too.
3. File-by-file Kotlin port into `commonMain/kotlin/com/asamm/garminimg/`:
   - `parser.subfile.*` — sealed `SubFile` + per-type readers
     (`TreSubFile`, `RgnSubFile`, `LblSubFile`, `NetSubFile`,
     `TypSubFile`).
   - `parser.ImgFile` — replaces `ImgFilesBag`. Uses `kotlinx.coroutines`
     `suspend` + `Dispatchers.Default` for parallel sub-file parse.
   - `model.*` — `Label` (sealed), `SubDivision`, `Coord`, `Bounds`,
     `ProductInfo`.
   - `io.*` — `BinaryReader` (already started in Phase A), random-access
     wrapper.
   - Replace `int` magic-number type codes with enums + inline value
     classes.
   - Drop dead code (`UnknownSubFile`, empty `MapSubFileReader`,
     unfinished placeholder TODOs).
4. Each port commit must keep `commonTest` + `jvmTest` green and
   `commonMain` iOS-clean (no `java.*` / `android.*` / JVM-only kotlinx
   APIs) so iOS targets can be re-enabled in a single follow-up commit.
5. Public API surface: design it Kotlin-idiomatic from scratch. The
   adapter that maps it onto Locus's `MapSource` / `Renderer` interfaces
   lives in `locus-core` (Phase D), not here. The new lib does not know
   about Locus.

Exit gate: parser is 100 % Kotlin in `commonMain`; all fixture-backed
tests green on JVM; no `android.*` / `java.*` references in `commonMain`
(CI check) — keeps the door open for the deferred iOS targets.

### Phase C — Rendering pipeline rewrite + quality improvements (2–3 weeks)

Includes Kotlin rewrite of the render-side classes (`StyleInternal`,
`MapDrawer`, `MapTileGenerator`, painters, label declutter) plus the actual
quality improvements.

Goal: a Locus-rendered IMG looks the same as on a Garmin device of the same
era, given the map's own TYP file.

This is the meat of the work. In rough priority:

1. **Wire TYP styling end-to-end** *(highest impact — directly fixes
   [#22569](https://help.locusmap.eu/topic/22569-how-can-locus-display-in-garmin-img-feature-style-properly))*
   - Today: `TypFileHandler.java` (130 lines) parses TYP signature, year,
     codepage, family ID — and stops there.
   - Need: parse the TYP type tables (POINTS, LINES, POLYGONS, DRAWORDER,
     ICONS) and produce a `TypStyle` table keyed by `(type, subtype)`.
   - Replace the static `StyleInternal.lookupXxx(...)` lookups with a
     `StyleResolver` that consults TYP first, then falls back to
     `StyleInternal` defaults for types the TYP doesn't define.
   - Reference: TYP format is documented in mkgmap source
     (https://github.com/openstreetmap/mkgmap) — `uk.me.parabola.imgfmt.app.typ.*`.
     Read-only port, do not pull mkgmap code (LGPL/GPL hygiene).
2. **Finish the `// tipos no extendidos solo` branch in `SubDivision.java`**
   — extended polygon/line type codes; recover from upstream + mkgmap docs.
3. **Label rendering polish** in `LabelDeClutteringFilter.java`:
   - Priority by POI class (consistent with Garmin device behaviour: airports
     before villages, etc.).
   - Anti-collision based on bounding box, not centre point only.
   - Halo / outline on labels via `Paint.setStrokeJoin(ROUND)` + double-pass
     draw (already a documented Compose UI pattern in the repo's drawables
     guide).
4. **DPI / text-scale correctness** — `UtilsGarminImg.setBase()` already
   carries density+text scale from `MapThemesUtils`, but it's used unevenly
   across painters; audit every `Paint.setTextSize` / `setStrokeWidth`.
5. **Background tile transparency** — last meaningful fix from 2020 in main
   repo (`f9bc4d4536`). Re-verify still works with the new style pipeline,
   regression-test it.
6. **Tile cache** — IMG tiles are regenerated on every pan; raster output is
   deterministic given (TYP version, style version, bbox, zoom). Add a
   per-map LRU on disk (reuse `libAndroidCore` cache infra). Out of scope if
   it bloats the diff; flag and time-box separately.

For each item: regression PNG goes into the test fixtures.

Exit gate: side-by-side comparison (BaseCamp / QMapShack) on three reference
maps shows visually equivalent rendering, ±label placement.

### Phase D — Documentation & integration polish (2–3 days)

1. Add `docs/libraries/libGarminImg/` with: format overview, supported
   sub-files, TYP coverage matrix, known limitations, how to add a fixture,
   how to debug a misrendered map.
2. Add a row to `docs/reference/pain_areas.md` describing the limits of IMG
   support so future Claude/devs know the scope (no encrypted, no routing,
   no MDR search).
3. Add IMG row to the table at the top of root `CLAUDE.md`
   (the "Topical guides" table) pointing to the new doc.
4. Update [`docs/reference/structure.md`](docs/reference/structure.md) entry
   for libGarminImg with the new module code if it gains one.

### Phase E (optional, evaluate after C) — Defer or drop

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
