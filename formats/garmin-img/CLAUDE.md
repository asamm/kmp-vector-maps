# CLAUDE.md — kmp-garmin-img

This file gives Claude Code project-level guidance for `asamm/kmp-garmin-img`,
a Kotlin Multiplatform library that parses and renders unencrypted Garmin
IMG vector maps.

**Always read [PLAN.md](PLAN.md) before starting work.** It is the source of
truth for the multi-phase rewrite (Phase A bootstrap → Phase E archive of
the old submodule). The plan is approved; do not relitigate it without
asking.

## What this project is

A KMP rewrite of the (now-deprecated) Java library `asamm/garmin-img-parser`,
which itself was an Android port of the desktop **JGarminImgParser**
(SourceForge, dormant since 2008). Hard fork — no upstream sync.

Consumers: initially [Locus Map](https://github.com/asamm/locus-core)
(Android), via Maven dep on `com.asamm.kmp:kmp-garmin-img:<ver>`. iOS is on
the roadmap as architecture-only (commonMain compiles for iOS, iosMain is a
stub).

## Scope

In scope:
- Parse unencrypted IMG sub-files: TRE, RGN, LBL, NET, TYP.
- Render to a `RenderTarget` abstraction with concrete impls for Android
  `Canvas` and AWT `Graphics2D`.
- Apply TYP styling (this is the headline rendering improvement vs the old
  Java fork).
- Fixture-backed tests on JVM.

Out of scope (do not build, do not propose):
- **Encrypted / locked Garmin maps.** Permanently out — legal + DRM.
- **NET routing** — Locus Map has its own router (LoRouter).
- **MDR address / POI search** — Locus Map has its own search index.
- **mkgmap-style preprocessing** — we are a renderer, not a converter.
- **iOS native render impl** — `iosMain.RenderTarget` throws
  `NotImplementedError`; an iOS team adds the actual impl later.

## KMP layout

```
lib/
  src/
    commonMain/kotlin/com/asamm/garminimg/    # parser, model, style, RenderTarget interface
    commonTest/kotlin/                        # fixture-backed parser & coord tests
    commonTest/resources/fixtures/            # small, redistributable IMG/TYP samples
    androidMain/kotlin/                       # Android Canvas RenderTarget; density helpers
    jvmMain/kotlin/                           # AWT Graphics2D RenderTarget
    jvmTest/kotlin/                           # render-diff tests against reference PNGs
    iosMain/kotlin/                           # stub: throws NotImplementedError
cli/                                           # desktop CLI module: RenderImgToPng
```

Hard rules:
- **`commonMain` must contain zero `android.*` references.** CI enforces.
- **`commonMain` must compile for all targets** (Android, JVM, iosX64,
  iosArm64, iosSimulatorArm64). If a piece of code can't be common, push it
  to the relevant `*Main` source set.
- **No Locus types.** This library does not depend on `locus-core` or any
  Asamm non-KMP module. The Locus-side adapter lives in `locus-core`.
- **Public API is Kotlin-idiomatic.** Don't preserve the Java Bag/Listener
  shapes from the old fork — design fresh.

## Targets & versions

- Kotlin **2.3.20** (matches `locus-core`).
- AGP **9.1.0** (Android target only).
- Gradle **9.3.1**.
- JDK **17** for compile + tests.
- Min Android SDK 24; compile SDK 36.
- Coroutines **1.10.x** (kotlinx-coroutines-core multiplatform).
- Logging: `com.asamm.kmp:kmp-logger` (matches the rest of Asamm).
- Tests: JUnit 5 (`org.junit.jupiter`) on JVM; the same on Android unit
  tests via `useJUnitPlatform()`.
- Binary I/O: `kotlinx-io-core` (multiplatform). Avoid `java.io.*` in
  commonMain.

## Distribution

- Maven coordinates: `com.asamm.kmp:kmp-garmin-img:<ver>`.
- Hosted on GitHub Packages Maven, repo `asamm/kmp-garmin-img`.
- Snapshots on push to `main`; releases on tag `vX.Y.Z`.
- Credentials in `REPO_GITHUB` env var (same convention as `locus-core`).

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

- **`PLAN.md`** — multi-phase plan; the working contract. Update it when
  scope shifts.
- **`README.md`** — short public-facing description (write at end of
  Phase A).
- **`CHANGELOG.md`** — start at Phase A; bump on every release tag.
- **`docs/format/`** — distilled IMG / TYP format reference (our own
  words, drawing on upstream JGarminImgParser + mkgmap as documentation).
- **`docs/upstream_diff.md`** — Phase B output: which Asamm-fork stubs we
  recovered, dropped, or rewrote.

## Reference materials (read-only)

- Upstream JGarminImgParser tarball:
  https://sourceforge.net/projects/jgarminimg/files/jgarminimg/ — pull to
  a directory **outside the repo**, treat as documentation only.
- mkgmap source: https://github.com/openstreetmap/mkgmap —
  `uk.me.parabola.imgfmt.app.*` documents the IMG/TYP write side.
  **Do not copy code** (LGPL/GPL hygiene); read for behaviour, then write
  fresh.
- Old Asamm fork (snapshot only, will be archived): the
  `libraries/libGarminImg` submodule in `locus-core`.
