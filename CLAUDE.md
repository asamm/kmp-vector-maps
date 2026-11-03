# CLAUDE.md — kmp-vector-maps

Asamm-owned vector map renderers for Locus Map behind one driver interface: Mapsforge `.map` first
(extracted from Mapsforge 0.30.0), Garmin IMG next, Mapbox Vector Tiles later. Android is the
product; JVM desktop runs benchmarks and tools; no iOS.

**Read [docs/ROADMAP.md](docs/ROADMAP.md) before starting work** — status, next action, commit-sized
steps, decisions D1–D33, open questions. Design: [docs/architecture.md](docs/architecture.md). Do
not relitigate a decision without asking. Update the roadmap status in the same commit as the work.

## Scope

| Part | Verdict |
|---|---|
| Core: driver interface, styles, anchors, label placement, `RenderTarget` | in |
| Mapsforge: reader, theme engine, renderer, labels, SVG, `RenderCallback` | in |
| Garmin IMG: reader (TRE, RGN, LBL, NET, TYP), TYP styling, renderer | in, M4 |
| Android host API for Locus | in, M8 |
| Mapbox Vector Tiles | deferred, M10 |
| Hillshading data, Locus tile pipeline and adapters | locus-core |
| Mapsforge POI database, map writer, views, layers, MBTiles | out |
| Garmin encrypted or locked maps, NET routing, MDR search, mkgmap-style conversion | out |

## Layout

| Path | Content |
|---|---|
| `formats/mapsforge/engine`, `android`, `awt` | vendored Mapsforge 0.30.0 in Kotlin (M5), `com.asamm.vectormaps.mapsforge.*`: the engine (model, reader, renderer, labels, theme), its Android and AWT graphics |
| `formats/mapsforge/driver` | Mapsforge driver: the `core/` interface over the engine |
| `formats/garmin-img/engine`, `android`, `awt` | Garmin IMG from kmp-garmin-img (M4.1, D33), `com.asamm.vectormaps.garminimg.*`: the reader, TYP styles and renderer over its own `RenderTarget`, its Android and AWT targets |
| `formats/garmin-img/driver` | Garmin driver: the `core/` interface over the engine |
| `viewer/*` | dev viewers for every registered format: `common` tile engine, `desktop` (Compose), `android` |
| `dev/mapsforge/golden`, `golden-upstream` | pixel test vs upstream 0.30.0 (JitPack); vendored corpus tiles render through the driver; the shared harness in `golden/harness` |
| `dev/mapsforge/golden-android`, `golden-android-upstream` | Android pixel test through `formats/mapsforge/android`, Robolectric; plain Mapsforge, not the driver |
| `dev/garmin-img/golden` | Garmin pixel test of Monaco tiles through the driver against committed PNGs (bundled font, every system) and the dump comparison against the legacy Java parser libGarminImg |
| `dev/fixtures/` | test maps, themes and the golden font, see [dev/fixtures/README.md](dev/fixtures/README.md) |
| `dev/bench/` | JMH benchmarks, `./gradlew :dev:bench:jmh`; baselines in ROADMAP M2.7 (Mapsforge) and M4.3c (Garmin) |
| `dev/bench/android/` | device benchmarks, `androidx.benchmark`; A54 baselines in ROADMAP M2.7b and M4.3c; M5 soak `SoakTest` (D25, D31); Paint cost `PaintCostTest` |
| `core/` | driver interface, registry, styles, render configuration; Apache-2.0, see architecture.md |
| `render/`, `android/` | `render/skia` from M7.1, `android/` from M8.1, other backends unscheduled; see architecture.md |

- `dev/` holds development helpers (benchmarks, golden tests, test data); nothing in it ships;
  `formats/` holds only shipped code.
- Large local maps stay outside the repo, referenced from `local.properties` (`viewer.mapFile`,
  read by the desktop viewer when no saved session map exists; see `dev/fixtures/README.md`).

## Hard rules

- **Every difference from upstream Mapsforge 0.30.0** (code, API, build) has a row in
  [docs/upstream.md](docs/upstream.md).
- **`./gradlew :dev:mapsforge:golden:test` before and after every Mapsforge change**, and
  `./gradlew :dev:garmin-img:golden:test` before and after every Garmin change.
- **Never remove a licence header** or `COPYING*`; on a vendored file's first change, append
  `Copyright 2026 Asamm Software` ([licence.md](docs/licence.md) L2).
- New Asamm files: `Copyright 2026 Asamm Software` + SPDX id — `LGPL-3.0-or-later` under
  `formats/mapsforge/` and `dev/mapsforge/`, `LGPL-2.1-or-later` under `formats/garmin-img/` and
  `dev/garmin-img/`, `Apache-2.0` for `core/`, `render/`, `android/`, `dev/bench/` and `viewer/`.
- **Formats depend on the core only.** No Locus types anywhere.
- Kotlin/JVM libraries: `android.*` only in `render/android`, `android/`,
  `formats/mapsforge/android`, `formats/garmin-img/android`.
- Vendored Mapsforge is `com.asamm.vectormaps.mapsforge.*` (upstream `org.mapsforge.*`, K64); the
  golden harness keeps upstream's imports, the vendored side compiles a generated copy.
- The host API mirrors Locus' `MapSourceRenderer` seam (D23).
- The Garmin API is Kotlin-idiomatic; the Java Bag and Listener shapes stay in the legacy
  reference parser (`dev/garmin-img`), which the engine never copies.

## Versions

Aligned with locus-core: Kotlin 2.4.20, AGP 9.4.1, Gradle 9.7.1, JDK 17, Android min SDK 24,
compile SDK 37. Tests: JUnit 5 on JVM; JUnit 4 only where Robolectric or androidx.benchmark
need it (`golden-android*`, `dev/bench/android`).

## Reference sources (read-only)

| Source | Where |
|---|---|
| Mapsforge 0.30.0 (`54e8654f`) | local clone `c:\menion\asamm\tools\mapsforge` |
| LoMaps themes | [asamm/lomaps-mapsforge](https://github.com/asamm/lomaps-mapsforge) |
| `kmp-garmin-img` (imported in M4.1, D33) | local clone `c:\menion\asamm\tools\kmp-garmin-img`, kept until locus-core leaves its packages (M9) |
| JGarminImgParser (Garmin upstream, LGPL-2.1-or-later) | `c:\menion\asamm\tools\_refs\GarminImg` |
| mkgmap (IMG and TYP write side) | [openstreetmap/mkgmap](https://github.com/openstreetmap/mkgmap): read for behaviour, never copy code |
| Locus map pipeline | locus-core `libLocusCore/.../maps/` — `MapSourceRenderer`, `MapsForgeV4*` |

- The Mapsforge clone has local changes on branch `asamm`: **never check out, fetch or write**
  there; read with `git show 54e8654f:<path>`.

## Style

- Inherits the user-level rules. Vendored code keeps upstream structure and order — **do not
  reformat** beyond the conversion.
- New Kotlin follows the locus-core Kotlin style.
- Docs follow the locus-core
  [documentation guide](https://github.com/asamm/locus-core/blob/development/docs/guides/documentation.md):
  tables, next action near the top, stable IDs, absolute dates, ✅/🔧/⬜/⏸ status.

## Git

- **Never commit or push without explicit confirmation.**
- Commit prefixes `- fix:` / `- chg:` / `- add:` / `- var:`; branches `feature/`, `fix/`,
  `refactor/`.
- One roadmap step (`M2.1`, …) per commit, each with its own `/deep-review`; related steps share
  one PR, named after the milestone or group.
