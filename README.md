# kmp-vector-maps

Vector map readers and renderers for [Locus Map](https://www.locusmap.app/): one driver interface,
render pipeline and viewer shared by every format.

## Status

**Pre-release.** Mapsforge 0.30.0 render subset converted to Kotlin (M5; differences in
[docs/upstream.md](docs/upstream.md)); Garmin IMG imported from kmp-garmin-img 0.5.2 (M4.1), its
driver since M4.2a; golden tests green, desktop and Android viewers running. Not published yet.
Milestones: [docs/ROADMAP.md](docs/ROADMAP.md).

| Format | State |
|---|---|
| Mapsforge `.map` v5 | vendored from [Mapsforge](https://github.com/mapsforge/mapsforge) 0.30.0 |
| Garmin IMG | from `asamm/kmp-garmin-img` 0.5.2, its history in [docs/garmin-img/](docs/garmin-img/) |
| Mapbox Vector Tiles | planned |

Targets: Android (Locus Map), JVM desktop (benchmarks, tools). No iOS.

## Modules

- **`:core`** — format-neutral driver interface, registry, styles, render configuration
  (Apache-2.0). `./gradlew :core:test`.
- **`:formats:mapsforge:engine`** — vendored Mapsforge 0.30.0 in Kotlin (M5), platform-independent:
  model, `.map` reader, renderer, labels, theme engine. `./gradlew :formats:mapsforge:engine:test`.
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
- **`:dev:garmin-img:golden`** — Garmin Monaco render against a committed PNG (Windows only) and
  the dump comparison against the legacy Java parser. `./gradlew :dev:garmin-img:golden:test`.
- **`:dev:bench`** — JMH benchmarks per render stage. `./gradlew :dev:bench:jmh`, another map with
  `-Pbench.mapFile=<path>`.
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
