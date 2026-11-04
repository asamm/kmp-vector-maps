# kmp-vector-maps

Vector map readers and renderers for [Locus Map](https://www.locusmap.app/): one driver interface,
render pipeline and viewer shared by every format.

## Status

**Pre-release.** Mapsforge 0.30.0 render subset, synced to 0.31.0, converted to Kotlin (M5;
differences in [docs/upstream.md](docs/upstream.md)); Garmin IMG imported from kmp-garmin-img 0.5.2
and done in M4: driver, parallel and multi-map renders, benchmarks, measured labels, TYP night
style; golden tests green, desktop and Android viewers running. Not published yet.
Milestones: [docs/ROADMAP.md](docs/ROADMAP.md).

| Format | State |
|---|---|
| Mapsforge `.map` v5 | vendored from [Mapsforge](https://github.com/mapsforge/mapsforge) 0.30.0, synced to 0.31.0 |
| Garmin IMG | from `asamm/kmp-garmin-img` 0.5.2 (history in [docs/garmin-img/](docs/garmin-img/)), extended in M4 |
| Mapbox Vector Tiles | planned |

Targets: Android (Locus Map), JVM desktop (benchmarks, tools). No iOS.

## Modules

- **`:core`** — format-neutral driver interface, registry, styles, render configuration
  (Apache-2.0). `./gradlew :core:test`.
- **`:formats:mapsforge:engine`** — vendored Mapsforge (0.30.0, synced to 0.31.0) in Kotlin (M5),
  platform-independent: model, `.map` reader, renderer, labels, theme engine.
  `./gradlew :formats:mapsforge:engine:test`.
- **`:formats:mapsforge:android`**, **`:awt`** — its graphics backends, also Kotlin.
- **`:formats:mapsforge:driver`** — the `:core` driver over the vendored Mapsforge; the host
  passes its `GraphicFactory`. `./gradlew :formats:mapsforge:driver:test`.
- **`:formats:garmin-img:engine`** — Garmin IMG reader, TYP styles and renderer.
  `./gradlew :formats:garmin-img:engine:test`.
- **`:formats:garmin-img:android`**, **`:awt`** — its render targets.
- **`:formats:garmin-img:driver`** — the `:core` driver over the Garmin engine; the host passes its
  `GarminCanvasFactory`. `./gradlew :formats:garmin-img:driver:test`.
- **`:dev:mapsforge:golden`** — pixel-exact render comparison against upstream 0.30.0.
  `./gradlew :dev:mapsforge:golden:test`.
- **`dev/mapsforge/upstream/`** — upstream sync helpers, Python 3 and `gh`, run by hand; see
  [docs/upstream.md](docs/upstream.md).
- **`:dev:garmin-img:golden`** — Garmin Monaco tiles through the driver against committed PNGs
  and the dump comparison against the legacy Java parser. `./gradlew :dev:garmin-img:golden:test`;
  the benchmark with `-Pbench.garmin=true`.
- **`:dev:bench`** — JMH benchmarks per render stage. `./gradlew :dev:bench:jmh`, another map with
  `-Pbench.mapFile=<path>`, for Garmin `-Pbench.garmin.mapFile=<path>`; one renderer with
  `-Pbench.includes=GarminBench`.
- **`:dev:bench:android`** — the same on a device (`androidx.benchmark`),
  `./gradlew :dev:bench:android:connectedReleaseAndroidTest`; also the M5 soak `SoakTest`, skipped
  unless run alone with
  `-Pandroid.testInstrumentationRunnerArguments.class=com.asamm.vectormaps.bench.android.SoakTest
  -Pandroid.testInstrumentationRunnerArguments.soak.minutes=<n>`; `PaintCostTest`, heap per
  Paint, likewise with `...class=com.asamm.vectormaps.bench.android.PaintCostTest` and
  `-Pandroid.testInstrumentationRunnerArguments.bench.paintCost=true`.
- **`:viewer:desktop`** — Compose Desktop dev tool for every registered format; restores the last
  session. `./gradlew :viewer:desktop:run`.
- **`:viewer:android`** — minimal Android dev tool. `./gradlew :viewer:android:installDebug`.

## Licence

Per part — Mapsforge LGPL-3.0-or-later with the LGPL §4(d)/(e) waiver, shared code Apache-2.0,
Garmin IMG LGPL-2.1-or-later — see [LICENSE.md](LICENSE.md). Mapsforge sync state:
[docs/upstream.md](docs/upstream.md).
