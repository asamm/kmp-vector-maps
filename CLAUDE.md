# CLAUDE.md — kmp-vector-maps

Asamm-owned vector map renderers for Locus Map behind one driver interface: Mapsforge `.map` first
(extracted from Mapsforge 0.30.0), Garmin IMG next, Mapbox Vector Tiles later. Android is the
product; JVM desktop runs benchmarks and tools; no iOS.

**Read [docs/ROADMAP.md](docs/ROADMAP.md) before starting work** — status, next action, commit-sized
steps, decisions D1–D23, open questions. Design: [docs/architecture.md](docs/architecture.md). Do
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
| `formats/mapsforge/legacy/*` | vendored Mapsforge Java, upstream module split; deleted after M5 |
| `formats/mapsforge/golden`, `golden-upstream` | pixel test: vendored vs upstream 0.30.0 (JitPack) |
| `viewer/*` | throwaway dev viewers; replaced by the kmp-garmin-img viewer in M3 |
| `fixtures/` | test maps and themes, see [fixtures/README.md](fixtures/README.md) |
| `bench/` from M2.7; `core/`, `render/`, `android/` from M3 | see architecture.md |

- New Mapsforge code goes beside `legacy/`, never into it.
- Large local maps stay outside the repo, referenced from `local.properties` (`viewer.mapFile`).

## Hard rules

- **Vendored Mapsforge stays upstream-identical** except rows in [docs/upstream.md](docs/upstream.md).
- **`./gradlew :formats:mapsforge:golden:test` before and after every Mapsforge change.**
- **Never remove a licence header** or `COPYING*`; on a vendored file's first change, append
  `Copyright 2026 Asamm Software` ([licence.md](docs/licence.md) L2).
- New Asamm files: `Copyright 2026 Asamm Software` + SPDX id — `LGPL-3.0-or-later` under
  `formats/mapsforge/` and the throwaway `viewer/`, `Apache-2.0` for `core/`, `render/`,
  `android/`, `bench/` and the M3.3 viewer.
- **Formats depend on the core only.** No Locus types anywhere.
- Kotlin/JVM libraries: `android.*` only in `render/android`, `android/`, `legacy/map-android`.
- Packages `org.mapsforge.*` until M5, then `com.asamm.*`.
- The host API mirrors Locus' `MapSourceRenderer` seam (D23).

## Versions

Aligned with locus-core: Kotlin 2.4.20, AGP 9.4.1, Gradle 9.7.1, JDK 17, Android min SDK 24,
compile SDK 37. Tests: JUnit 5 on JVM.

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
