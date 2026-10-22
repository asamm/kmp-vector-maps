# CLAUDE.md — kmp-vector-maps

Asamm-owned vector map readers and renderers for Locus Map: one driver interface, one render
pipeline and one viewer shared by every format. Mapsforge `.map` first (extracted from Mapsforge
0.30.0), Garmin IMG joins from `kmp-garmin-img`, Mapbox Vector Tiles later. Android + JVM; iOS later.

**Always read [docs/ROADMAP.md](docs/ROADMAP.md) before starting work** — milestones, decisions
(D1–D17) and open questions. Target design: [docs/architecture.md](docs/architecture.md). The
original Mapsforge decision record is the locus-core plan
[`mapsforge_render_extraction.md`](https://github.com/asamm/locus-core/blob/development/docs/plans/mapsforge_render_extraction.md);
do not relitigate either without asking.

## Scope

| Part | Verdict |
|---|---|
| Shared core: driver interface, registry, render pipeline, label placement, `RenderTarget` | in |
| Mapsforge: `.map` v5 reader, theme engine, renderer, labels, SVG symbols, `RenderCallback` | in |
| Garmin IMG (from `kmp-garmin-img`) | in, milestone M3 |
| Mapbox Vector Tiles, further formats | later, M9 |
| Hillshading data and rendering | Locus side; this repo only gives it a pipeline hook |
| Mapsforge POI database, map writer, views, layers, MBTiles | out |
| Locus tile pipeline and adapters | locus-core |

## Layout

| Path | Content |
|---|---|
| `formats/mapsforge/legacy/{core,map,map-reader}` | vendored Mapsforge Java, platform-independent |
| `formats/mapsforge/legacy/{map-android,map-awt}` | vendored graphics backends |
| `formats/mapsforge/golden` | golden test: vendored render vs upstream render, pixel-exact |
| `formats/mapsforge/golden-upstream` | renders the corpus with upstream 0.30.0 from JitPack |
| `viewer/{common,desktop,android}` | dev viewers; become one Compose Multiplatform viewer in M2 |
| `fixtures/` | test maps and themes, see [fixtures/README.md](fixtures/README.md) |
| `core/` | shared KMP core, created in M2 |

- The vendored Mapsforge modules mirror upstream's split, so a module diffs 1:1 against
  `mapsforge-<module>` upstream. `legacy/` is deleted as a whole once the Kotlin conversion (M5)
  replaces it; new Mapsforge code goes beside it, never into it.
- Large local maps stay outside the repo, referenced from `local.properties` (`viewer.mapFile`,
  `viewer.themeFile`).

## Hard rules

- **Vendored Mapsforge code stays upstream-identical** except deviations listed in
  [docs/upstream.md](docs/upstream.md). Every intentional change gets a row there.
- **`./gradlew :formats:mapsforge:golden:test` before and after every change to Mapsforge code.**
  AWT and Android golden sets are separate, never mixed.
- **Never remove a licence header** or `COPYING` / `COPYING.LESSER`. On the first change to a
  vendored file, append `Copyright 2026 Asamm Software` to its copyright list
  ([licence.md](docs/licence.md) L2).
- New Asamm files start with `Copyright 2026 Asamm Software` and the module's SPDX identifier
  (`LGPL-3.0-or-later` for Mapsforge, core and tools).
- **Formats depend on the core only, never on each other.**
- **No Locus types.** Locus-side adapters live in locus-core.
- Packages stay `org.mapsforge.*` until the Kotlin conversion (M5), then `com.asamm.*`.
- Kotlin code meant for `commonMain` stays free of `android.*` and `java.*`.

## Targets & versions

Aligned with locus-core: Kotlin 2.4.20, AGP 9.4.1, Gradle 9.7.1, JDK 17, Android min SDK 24,
compile SDK 37. Tests: JUnit 5 on JVM.

## Reference sources (read-only)

| Source | Where | Use |
|---|---|---|
| Mapsforge | `c:\menion\asamm\tools\mapsforge`, tag `0.30.0` = commit `54e8654f` | vendoring, sync diffs |
| LoMaps themes | [asamm/lomaps-mapsforge](https://github.com/asamm/lomaps-mapsforge), Apache-2.0 | test theme |
| `kmp-garmin-img` | `c:\menion\asamm\tools\kmp-garmin-img` | Garmin format until M3, `RenderTarget` |

- The Mapsforge clone sits on branch `asamm` with local changes: **never check out, fetch or
  write** there. Read with `git show 54e8654f:<path>` or `git archive 54e8654f`.

## Style

Inherits the user-level rules. Repo specifics:
- Vendored Java keeps upstream formatting — **do not reformat**, it breaks sync diffs.
- New Kotlin follows the locus-core Kotlin style (block bodies, braced guards, KDoc block form).
- Docs follow the locus-core
  [documentation guide](https://github.com/asamm/locus-core/blob/development/docs/guides/documentation.md):
  tables and bullets, next action near the top, absolute dates, stable IDs.

## Git

- **Never commit or push without explicit confirmation.**
- Commit prefixes: `- fix:` / `- chg:` / `- add:` / `- var:`. No Conventional Commits.
- Branches: `feature/<slug>`, `fix/<slug>`, `refactor/<slug>`.

## What lives where

| File | Content |
|---|---|
| `docs/ROADMAP.md` | milestones, decisions, status, open questions |
| `docs/architecture.md` | driver interface, pipeline stages, module layers |
| `docs/upstream.md` | Mapsforge: last synced tag, deviations from upstream |
| `docs/licence.md` | Mapsforge LGPL position, obligations of this repo and of Locus |
| `README.md` | public description, consumption recipe |
| `CHANGELOG.md` | released versions only |
