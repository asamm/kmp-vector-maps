# kmp-vector-maps

Vector map readers and renderers for [Locus Map](https://www.locusmap.app/): one driver interface,
render pipeline and viewer shared by every format.

## Status

**Pre-release.** Mapsforge 0.30.0 render subset vendored unchanged, golden corpus green, desktop and
Android viewers running. Not published yet. Milestones: [docs/ROADMAP.md](docs/ROADMAP.md).

| Format | State |
|---|---|
| Mapsforge `.map` v5 | vendored from [Mapsforge](https://github.com/mapsforge/mapsforge) 0.30.0 |
| Garmin IMG | in `asamm/kmp-garmin-img` (private for now), moves here |
| Mapbox Vector Tiles | planned |

Targets: Android (Locus Map), JVM desktop (benchmarks, tools). No iOS.

## Modules

- **`:formats:mapsforge:legacy:core`**, **`:map`**, **`:map-reader`** — vendored Mapsforge,
  platform-independent Java.
- **`:formats:mapsforge:legacy:map-android`**, **`:map-awt`** — vendored graphics backends.
  `legacy/` goes once the Kotlin conversion replaces it.
- **`:formats:mapsforge:golden`** — pixel-exact render comparison against upstream 0.30.0.
  `./gradlew :formats:mapsforge:golden:test`.
- **`:dev:bench`** — JMH benchmarks per render stage. `./gradlew :dev:bench:jmh`, another map with
  `-Pbench.mapFile=<path>`.
- **`:dev:bench:android`** — the same on a device (`androidx.benchmark`),
  `./gradlew :dev:bench:android:connectedReleaseAndroidTest`.
- **`:viewer:desktop`** — Compose Desktop dev tool. `./gradlew :viewer:desktop:run`.
- **`:viewer:android`** — minimal Android dev tool. `./gradlew :viewer:android:installDebug`.

## Licence

Per part — Mapsforge LGPL-3.0-or-later with the LGPL §4(d)/(e) waiver, shared code Apache-2.0,
Garmin IMG LGPL-2.1 — see [LICENSE.md](LICENSE.md). Mapsforge sync state:
[docs/upstream.md](docs/upstream.md).
