# kmp-garmin-img

Kotlin Multiplatform parser & renderer for **unencrypted** Garmin IMG vector maps.
A KMP rewrite of the Asamm fork of [JGarminImgParser](https://sourceforge.net/projects/jgarminimg/),
intended primarily for [Locus Map](https://www.locusmap.app/).

## Status

**Phase A — bootstrap.** This repository currently contains the Gradle KMP skeleton
plus two seed utilities (`CoordUtils`, `BinaryReader`) with their unit tests. Parser
sub-files (TRE, RGN, LBL, NET, TYP) and the rendering pipeline arrive in subsequent
phases — see [PLAN.md](PLAN.md).

## Targets

- Android (`com.android.kotlin.multiplatform.library`) — for Locus Map.
- JVM — for local testing and the future desktop CLI.

iOS is on the architecture roadmap (`commonMain` is and will remain iOS-clean) but
is not built or published in Phase A.

## Scope

In: parsing of unencrypted IMG sub-files; rendering through a `RenderTarget`
abstraction with Android Canvas and AWT `Graphics2D` impls; TYP styling.

Out: encrypted/locked maps; routing (NET); address search (MDR);
mkgmap-style preprocessing.

## Coordinates

```
com.asamm.kmp:kmp-garmin-img:0.1.0
```

Hosted on GitHub Packages
(`https://maven.pkg.github.com/asamm/kmp-garmin-img`). Authentication via the
`GPR_USERNAME` + `GPR_TOKEN` environment variables; the `REPO_GITHUB` username
fallback used by `locus-core` is also accepted.

## Licence

LGPL-2.1 — see [LICENSE](LICENSE). This is a derivative work of JGarminImgParser
(© 2006 Patrick Valsecchi) and the Asamm `garmin-img-parser` fork.
