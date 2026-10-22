# kmp-vector-maps

Vector map readers and renderers for [Locus Map](https://www.locusmap.app/): one driver interface,
render pipeline and viewer shared by every format.

## Status

**Pre-release.** Mapsforge 0.30.0 render subset vendored unchanged, golden corpus green, desktop and
Android viewers running. Not published yet. Milestones: [docs/ROADMAP.md](docs/ROADMAP.md).

| Format | State |
|---|---|
| Mapsforge `.map` v5 | vendored from [Mapsforge](https://github.com/mapsforge/mapsforge) 0.30.0 |
| Garmin IMG | in [kmp-garmin-img](https://github.com/asamm/kmp-garmin-img), moves here |
| Mapbox Vector Tiles | planned |

Targets: Android, JVM (desktop); iOS later.

## Modules

- **`:formats:mapsforge:legacy:core`**, **`:map`**, **`:map-reader`** — vendored Mapsforge,
  platform-independent Java.
- **`:formats:mapsforge:legacy:map-android`**, **`:map-awt`** — vendored graphics backends.
  `legacy/` goes once the Kotlin conversion replaces it.
- **`:formats:mapsforge:golden`** — pixel-exact render comparison against upstream 0.30.0.
  `./gradlew :formats:mapsforge:golden:test`.
- **`:viewer:desktop`** — Compose Desktop dev tool. `./gradlew :viewer:desktop:run`.
- **`:viewer:android`** — minimal Android dev tool. `./gradlew :viewer:android:installDebug`.

## Licence

Per format. Mapsforge code and the shared tooling: LGPL-3.0-or-later, see
[COPYING.LESSER](COPYING.LESSER) and [COPYING](COPYING); derivative work of Mapsforge (© the
Mapsforge contributors).

As upstream Mapsforge, and for Asamm's own changes too: **the constraints of LGPL v3 §4(d) and
§4(e) are waived.** You may convey a Combined Work without providing any way to recombine or
relink the application and without providing any shared library mechanism.

Details: [docs/licence.md](docs/licence.md). Mapsforge sync state: [docs/upstream.md](docs/upstream.md).
