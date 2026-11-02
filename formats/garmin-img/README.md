# kmp-garmin-img

Kotlin Multiplatform parser & renderer for **unencrypted** Garmin IMG vector maps.
A KMP rewrite of the Asamm fork of
[JGarminImgParser](https://sourceforge.net/projects/jgarminimg/), intended
primarily for [Locus Map](https://www.locusmap.app/).

## Status

**0.5.2 — feature-complete parser + dual-target renderer, perf-tuned
for country-scale IMGs.** Replaced the legacy
`libraries/libGarminImg` submodule in
[locus-core](https://github.com/asamm/locus-core) (PR #771); now the
only Garmin IMG implementation on locus-core's `development` branch.

Remaining work (point-icon polish, continent-scale file-backed reader,
iOS target, optional polishing items) is tracked in
[docs/ROADMAP.md](docs/ROADMAP.md).

## Targets

- **Android** (`com.android.kotlin.multiplatform.library`) — for Locus Map
  via [`AndroidRenderTarget`](lib/src/androidMain/kotlin/com/asamm/garminimg/render/AndroidRenderTarget.kt).
- **JVM** — for desktop tools and golden-PNG regression tests via
  [`AwtRenderTarget`](lib/src/jvmMain/kotlin/com/asamm/garminimg/render/AwtRenderTarget.kt).

iOS is on the architecture roadmap — `commonMain` is and remains
iOS-clean — but no `iosMain` source set ships yet.

## Scope

**In:** parsing of unencrypted IMG sub-files (TRE / RGN / LBL / NET / TYP);
rendering through a `RenderTarget` abstraction; TYP styling.

**Out:** encrypted/locked maps; routing (NET — Locus has its own router);
address search (MDR — Locus has its own search index); mkgmap-style
preprocessing.

## Coordinates

```kotlin
implementation("com.asamm.kmp:kmp-garmin-img-android:0.5.2")
```

Hosted on GitHub Packages
(`https://maven.pkg.github.com/asamm/kmp-garmin-img`). Authentication via
either `GPR_USERNAME` + `GPR_TOKEN` (CI convention) or `REPO_GITHUB` in
`username|token` format (locus-core convention).

## Modules

- **`:lib`** — the published library (parser, renderer, style resolver).
- **`:viewer:desktop`** — Compose Desktop dev tool. `./gradlew :viewer:desktop:run`.
  Pan / zoom, layer toggles, anti-alias toggle, "Save view" PNG+JSON
  export to a project-local `captures/` folder.
- **`:viewer:android`** — minimal one-Activity Android app for verifying the
  Android render target on real device hardware. `./gradlew :viewer:android:installDebug`.

## Licence

LGPL-2.1 — see [LICENSE](LICENSE). This is a derivative work of
JGarminImgParser (© 2006 Patrick Valsecchi) and the Asamm
`garmin-img-parser` fork.
