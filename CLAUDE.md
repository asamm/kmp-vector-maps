# CLAUDE.md — kmp-vector-maps

Asamm-owned vector map renderers for Locus Map behind one driver interface: Mapsforge `.map` first
(extracted from Mapsforge 0.30.0), Garmin IMG next, Mapbox Vector Tiles later. Android is the
product; JVM desktop runs benchmarks and tools; no iOS.

**Read [docs/ROADMAP.md](docs/ROADMAP.md) before starting work** — status, next action, commit-sized
steps, decisions D1–D31, open questions. Design: [docs/architecture.md](docs/architecture.md). Do
not relitigate a decision without asking. Update the roadmap status in the same commit as the work.

## Scope

| Part | Verdict |
|---|---|
| Core: driver interface, styles, anchors, label placement, `RenderTarget` | in |
| Mapsforge: reader, theme engine, renderer, labels, SVG, `RenderCallback` | in |
| Garmin IMG (from `kmp-garmin-img`) | in, M4 |
| Android host API for Locus | in, M8 |
| Mapbox Vector Tiles | deferred, M10 |
| Hillshading data, Locus tile pipeline and adapters | locus-core |
| Mapsforge POI database, map writer, views, layers, MBTiles | out |

## Layout

| Path | Content |
|---|---|
| `formats/mapsforge/legacy/*` | vendored Mapsforge, upstream module split; Kotlin per area from M5.1 (`map-reader`, core and map model, util, datastore, graphics and its backends, renderer, labels), deleted after M5 |
| `formats/mapsforge/driver` | Mapsforge driver: the `core/` interface over `legacy/` |
| `formats/mapsforge/golden`, `golden-upstream` | pixel test vs upstream 0.30.0 (JitPack); vendored corpus tiles render through the driver |
| `formats/mapsforge/golden-android`, `golden-android-upstream` | Android pixel test through `map-android`, Robolectric; plain Mapsforge, not the driver |
| `viewer/*` | dev viewers for every registered format: `common` tile engine, `desktop` (Compose), `android` |
| `dev/fixtures/` | test maps and themes, see [dev/fixtures/README.md](dev/fixtures/README.md) |
| `dev/bench/` | JMH benchmarks, `./gradlew :dev:bench:jmh`; baseline in ROADMAP M2.7 |
| `dev/bench/android/` | device benchmarks, `androidx.benchmark`; A54 baseline in ROADMAP M2.7b; M5 soak `SoakTest` (D25, D31); Paint cost `PaintCostTest` |
| `core/` | driver interface, registry, styles, render configuration; Apache-2.0, see architecture.md |
| `render/`, `android/` | `render/skia` from M7.1, `android/` from M8.1, other backends unscheduled; see architecture.md |

- New Mapsforge code goes beside `legacy/`; an M5 conversion replaces its area's Java inside
  `legacy/*` (D24).
- `dev/` holds development helpers (benchmarks, test data); nothing in it ships.
- Large local maps stay outside the repo, referenced from `local.properties` (`viewer.mapFile`,
  read by the desktop viewer when no saved session map exists; see `dev/fixtures/README.md`).

## Hard rules

- **Vendored Mapsforge stays upstream-identical** until its area's M5 conversion, except rows in
  [docs/upstream.md](docs/upstream.md).
- **`./gradlew :formats:mapsforge:golden:test` before and after every Mapsforge change.**
- **Never remove a licence header** or `COPYING*`; on a vendored file's first change, append
  `Copyright 2026 Asamm Software` ([licence.md](docs/licence.md) L2).
- New Asamm files: `Copyright 2026 Asamm Software` + SPDX id — `LGPL-3.0-or-later` under
  `formats/mapsforge/`, `Apache-2.0` for `core/`, `render/`, `android/`, `dev/bench/` and
  `viewer/`.
- **Formats depend on the core only.** No Locus types anywhere.
- Kotlin/JVM libraries: `android.*` only in `render/android`, `android/`, `legacy/map-android`.
- Packages `org.mapsforge.*` until M5.7, then `com.asamm.*`; M5 converts each area in place (D24).
- The host API mirrors Locus' `MapSourceRenderer` seam (D23).

## Versions

Aligned with locus-core: Kotlin 2.4.20, AGP 9.4.1, Gradle 9.7.1, JDK 17, Android min SDK 24,
compile SDK 37. Tests: JUnit 5 on JVM; JUnit 4 only where Robolectric or androidx.benchmark
need it (`golden-android*`, `dev/bench/android`).

## Reference sources (read-only)

| Source | Where |
|---|---|
| Mapsforge 0.30.0 (`54e8654f`) | local clone `c:\menion\asamm\tools\mapsforge` |
| LoMaps themes | [asamm/lomaps-mapsforge](https://github.com/asamm/lomaps-mapsforge) |
| `kmp-garmin-img` (private, Q12) | local clone `c:\menion\asamm\tools\kmp-garmin-img` |
| Locus map pipeline | locus-core `libLocusCore/.../maps/` — `MapSourceRenderer`, `MapsForgeV4*` |

- The Mapsforge clone has local changes on branch `asamm`: **never check out, fetch or write**
  there; read with `git show 54e8654f:<path>`.

## Style

- Inherits the user-level rules. Vendored Java keeps upstream formatting — **do not reformat**.
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
