# kmp-vector-maps

Vector map readers and renderers for [Locus Map](https://www.locusmap.app/): one driver interface,
render pipeline and viewer shared by every format.

## Status

**Pre-release.** Mapsforge 0.30.0 render subset vendored, converting to Kotlin one area at a time
(M5, pixel-identical); golden corpus green, desktop and Android viewers running. Not published yet.
Milestones: [docs/ROADMAP.md](docs/ROADMAP.md).

| Format | State |
|---|---|
| Mapsforge `.map` v5 | vendored from [Mapsforge](https://github.com/mapsforge/mapsforge) 0.30.0 |
| Garmin IMG | in `asamm/kmp-garmin-img` (private for now), moves here |
| Mapbox Vector Tiles | planned |

Targets: Android (Locus Map), JVM desktop (benchmarks, tools). No iOS.

## Modules

- **`:core`** — format-neutral driver interface, registry, styles, render configuration
  (Apache-2.0). `./gradlew :core:test`.
- **`:formats:mapsforge:legacy:core`**, **`:map`**, **`:map-reader`** — vendored Mapsforge,
  platform-independent; `:map-reader`, the model, util and datastore areas and core graphics
  converted to Kotlin (M5.1–M5.3a), the rest Java.
- **`:formats:mapsforge:legacy:map-android`**, **`:map-awt`** — vendored graphics backends, in
  Kotlin since M5.3b.
  `legacy/` goes once the Kotlin conversion replaces it.
- **`:formats:mapsforge:driver`** — the `:core` driver over the vendored Mapsforge; the host
  passes its `GraphicFactory`. `./gradlew :formats:mapsforge:driver:test`.
- **`:formats:mapsforge:golden`** — pixel-exact render comparison against upstream 0.30.0.
  `./gradlew :formats:mapsforge:golden:test`.
- **`:dev:bench`** — JMH benchmarks per render stage. `./gradlew :dev:bench:jmh`, another map with
  `-Pbench.mapFile=<path>`.
- **`:dev:bench:android`** — the same on a device (`androidx.benchmark`),
  `./gradlew :dev:bench:android:connectedReleaseAndroidTest`; also the M5 soak `SoakTest`, skipped
  unless run alone with
  `-Pandroid.testInstrumentationRunnerArguments.class=com.asamm.vectormaps.bench.android.SoakTest
  -Pandroid.testInstrumentationRunnerArguments.soak.minutes=<n>`.
- **`:viewer:desktop`** — Compose Desktop dev tool for every registered format; restores the last
  session. `./gradlew :viewer:desktop:run`.
- **`:viewer:android`** — minimal Android dev tool. `./gradlew :viewer:android:installDebug`.

## Licence

Per part — Mapsforge LGPL-3.0-or-later with the LGPL §4(d)/(e) waiver, shared code Apache-2.0,
Garmin IMG LGPL-2.1 — see [LICENSE.md](LICENSE.md). Mapsforge sync state:
[docs/upstream.md](docs/upstream.md).
