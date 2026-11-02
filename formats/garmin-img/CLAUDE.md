# CLAUDE.md — kmp-garmin-img

Project-level guidance for `asamm/kmp-garmin-img`, a Kotlin Multiplatform
library that parses and renders unencrypted Garmin IMG vector maps.

**Always read [docs/ROADMAP.md](docs/ROADMAP.md) before starting work** — it
lists what's done and what's open. The original multi-phase plan is archived
at [`docs/archive/PLAN-original.md`](docs/archive/PLAN-original.md) for
historical context; do not relitigate the design without asking.

## What this project is

A KMP rewrite of the (now-deprecated) Java library `asamm/garmin-img-parser`,
itself an Android port of the desktop **JGarminImgParser** (SourceForge,
dormant since 2008). Hard fork — no upstream sync.

Consumer: [Locus Map](https://github.com/asamm/locus-core) via Maven dep
on `com.asamm.kmp:kmp-garmin-img-android:<ver>`. Wired into locus-core's
`development` branch as of 0.5.0.

## Scope

In scope:
- Parse unencrypted IMG sub-files: TRE, RGN, LBL, NET, TYP.
- Render to a `RenderTarget` abstraction with concrete impls for Android
  `Canvas` (androidMain) and AWT `Graphics2D` (jvmMain).
- Apply TYP styling (the headline rendering improvement vs the old
  Java fork).
- Fixture-backed tests on JVM (golden PNG, parser dump-diff).

Out of scope (do not build, do not propose):
- **Encrypted / locked Garmin maps.** Permanently out — legal + DRM.
- **NET routing** — Locus has its own router.
- **MDR address / POI search** — Locus has its own search index.
- **mkgmap-style preprocessing** — we are a renderer, not a converter.
- **iOS native render impl** — `commonMain` stays iOS-clean so an iOS
  target can be added later; no `iosMain` source set ships yet (see
  ROADMAP).

## KMP layout

```
lib/
  src/
    commonMain/kotlin/com/asamm/garminimg/    # parser, model, style, RenderTarget interface
    commonTest/kotlin/                        # fixture-backed parser & coord tests
    commonTest/resources/fixtures/            # small, redistributable IMG/TYP samples
    androidMain/kotlin/                       # AndroidRenderTarget
    jvmMain/kotlin/                           # AwtRenderTarget
    jvmTest/kotlin/                           # render-diff + dump-diff tests
viewer/
  desktop/                                    # Compose Desktop dev tool
  android/                                    # minimal Android dev tool
captures/                                     # PNG+JSON dumps from the desktop viewer
```

Hard rules:
- **`commonMain` must contain zero `android.*` references.**
- **`commonMain` must stay iOS-clean** — Android and JVM are the active
  targets, but the source set has to keep compiling for future iosX64 /
  iosArm64 / iosSimulatorArm64.
- **No Locus types.** This library does not depend on `locus-core` or any
  Asamm non-KMP module. The Locus-side adapter lives in `locus-core`.
- **Public API is Kotlin-idiomatic.** Don't preserve the Java Bag/Listener
  shapes from the old fork — design fresh.

## Targets & versions

- Kotlin **2.3.20** (matches `locus-core`).
- AGP **9.1.1** (Android target only; AGP 9 has built-in Kotlin support).
- Gradle **9.3.1**.
- JDK **17** for compile + tests.
- Min Android SDK 24; compile SDK 36.
- Coroutines **1.10.x** (kotlinx-coroutines-core multiplatform).
- Logging: `com.asamm.kmp:kmp-logger` (matches the rest of Asamm).
- Tests: JUnit 5 (`org.junit.jupiter`) on JVM via `useJUnitPlatform()`.
- Binary I/O: `kotlinx-io-core` (multiplatform). Avoid `java.io.*` in
  commonMain.

## Distribution

- Maven coordinates: `com.asamm.kmp:kmp-garmin-img[-android|-jvm]:<ver>`.
- Hosted on GitHub Packages Maven, repo `asamm/kmp-garmin-img`.
- Snapshots: workflow-dispatched (`snapshot.yml`).
- Releases: tag `vX.Y.Z` triggers `release.yml`.
- Credentials: either `GPR_USERNAME` + `GPR_TOKEN`, or `REPO_GITHUB` in
  `username|token` format (the locus-core convention is accepted as a
  fallback so a single env var covers both repos).

## Style (inherits from user's global rules)

- No emojis in code or commit messages, unless asked.
- KDoc: always block form `/** … */`, even single sentence.
- Comment placement: line above the declaration, never end-of-line.
- Block-body Kotlin functions when the body has any function call;
  expression body only for trivial property-style accessors.
- Guard clauses: always braces; multi-condition guards split one
  condition per line.
- Wrap prose / comments at ~100 chars.
- Section dividers `//****…****` with `// SECTION` inside — sparingly,
  never inside a function body.

## Commit messages

Same as Asamm convention:
| Prefix   | Use for |
|----------|---------|
| `- fix:` | Bug fix |
| `- chg:` | Change / refactor / behaviour tweak / doc update |
| `- add:` | New feature, file, capability |
| `- var:` | Various / version bump |

No `Co-Authored-By` lines. No Conventional Commits. **Never commit / push
without explicit confirmation.**

## Branch naming

`feature/<slug>` (in full — never `feat/`), `fix/<slug>`, `chore/<slug>`.
Lowercase-hyphens.

## What lives where

- **`docs/ROADMAP.md`** — remaining work, checkbox list. Update when scope shifts.
- **`README.md`** — short public-facing description and consumption recipe.
- **`CHANGELOG.md`** — released versions only; bump on every release tag.
- **`docs/archive/`** — superseded planning + changelog history kept for
  context.
- **`docs/visual-reference/`** — reference screenshots from other
  renderers (QMS / GPSMapEdit) anchoring visual regression review.
- **`captures/`** — per-session PNG+JSON dumps from the desktop viewer's
  "Save view" button; not auto-committed.

## Reference materials (read-only)

- Upstream JGarminImgParser tarball:
  https://sourceforge.net/projects/jgarminimg/files/jgarminimg/ — pull to
  a directory **outside the repo**, treat as documentation only.
- mkgmap source: https://github.com/openstreetmap/mkgmap —
  `uk.me.parabola.imgfmt.app.*` documents the IMG/TYP write side.
  **Do not copy code** (LGPL/GPL hygiene); read for behaviour, then write
  fresh.
- Legacy `libGarminImg` parser — vendored under
  `lib/src/jvmTest/java/org/free/garminimg/` as the byte-parity reference
  for the dump-diff regression rig.
