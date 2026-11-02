# Licence position

What each part's licence means for this repo and for Locus. Engineering reading, not legal advice;
the legal check is locus-core plan Q1.

**Author:** Menion · **Started:** 2026-10-22

## Parts

| Part | Licence |
|---|---|
| `formats/mapsforge/**`, including its Kotlin conversion, and its golden tests `dev/mapsforge/**` | LGPL-3.0-or-later + §4(d)/(e) waiver |
| `core/`, `render/`, `android/`, `dev/bench/`, `viewer/` (written fresh) | Apache-2.0 (D19) |
| `formats/garmin-img/**` and its tests `dev/garmin-img/**` (M4) | LGPL-2.1-or-later, as upstream JGarminImgParser (D33) |
| Fixtures | per [dev/fixtures/README.md](../dev/fixtures/README.md): ODbL map data, CC-BY-SA 4.0 OpenTopoMap style, Apache-2.0 theme, Bitstream Vera licence font (DejaVu Sans) |

- An Apache-2.0 core keeps LGPL-2.1 and LGPL-3.0 code out of one derivative work.
- The golden harness carries LGPL-3.0-or-later headers, as did the throwaway `viewer/`; that
  viewer was deleted in M3.3, not relicensed. Neither it nor the kmp-garmin-img viewer gave code to
  the new one, only its feature list (D18).

## Mapsforge

| Item | Value |
|---|---|
| Licence | LGPL-3.0-or-later (headers, `COPYING.LESSER`) |
| Waiver | §4(d) and §4(e), upstream `README.md` only, commit `c78c1d79` (2018-10-31) |
| Headers | all 157 vendored files (177 in M0, 20 hillshading files removed, C5, C6); `TileDependencies` has none upstream, so since M5.4b it carries only the Asamm line and the SPDX identifier |
| Dependencies | androidsvg Apache-2.0; kxml2 BSD + public domain; svgSalamander BSD (1.1.3 POM) |
| Embedded code | `TextStroke.kt` and the libGDX-marked parts of `LatLongUtils.kt`: Apache-2.0 |

- The waiver is a GPL-3.0 §7 additional permission a redistributor may drop, so this repo carries
  it in [LICENSE.md](../LICENSE.md) and grants it for Asamm's changes.
- A Kotlin translation is a modification (GPL-3.0 §0): the converted code stays LGPL-3.0-or-later.
  Relicensing needs every copyright holder; only a clean-room rewrite (rejected O4) escapes it.
- Open (Q14): 40 of 66 upstream authors committed only before the waiver; vendored headers name
  mapsforge.org (122 files) and Ludwig M Brinckmann (75 files), counted with:

  ```sh
  git ls-files 'formats/mapsforge/engine/*.kt' 'formats/mapsforge/android/*.kt' 'formats/mapsforge/awt/*.kt' | xargs grep -l 'Copyright.*<name>' | wc -l
  ```

## Garmin IMG

| Item | Value |
|---|---|
| Licence | LGPL-2.1-or-later (upstream headers, [COPYING.LGPL-2.1](../COPYING.LGPL-2.1)) |
| Upstream | JGarminImgParser, Copyright (C) 2006 Patrick Valsecchi, via the Asamm fork libGarminImg |
| Headers | the Kotlin rewrite names both and Asamm, with the SPDX identifier; Asamm-only files carry the Asamm line and the identifier; the legacy Java parser in `dev/garmin-img` keeps its upstream headers, plus the L2 notice where modified |
| Foreign test files | `SparseArrayCompat.java` Apache-2.0 (AOSP); `EndianDataInputStream.java` GPL-2.0-or-later (JUMP, Vivid Solutions); both only in the legacy reference parser, never shipped |
| Not imported | upstream `visad/*` (LGPL-2.0-or-later) and `swing/*` |
| Fixtures | `otm-monaco` ODbL + CC-BY-SA 4.0, `mkgmap-monaco` ODbL, see [dev/fixtures/README.md](../dev/fixtures/README.md) |

## Obligations of this repo

| ID | Obligation | How |
|---|---|---|
| L1 | Keep every copyright notice and licence header | hard rule in `CLAUDE.md` |
| L2 | Mark modified files with a notice and date | add `Copyright 2026 Asamm Software` on first change |
| L3 | Ship `COPYING`, `COPYING.LESSER` and `COPYING.LGPL-2.1` | repo root |
| L4 | Carry the waiver | `LICENSE.md`, later the POM |
| L5 | Source for recipients of binaries | public repo, one tag per release |

## Obligations of Locus

| Item | Mapsforge (waiver) | Garmin IMG (LGPL-2.1-or-later) |
|---|---|---|
| Notice and licence texts in the app | required, in `LibLicensesLco` | required |
| User can relink a modified library | waived | required (§6) |
| R8-minified, merged into the APK | allowed | only with a relinking path |
| Public source | this repo | this repo from M4.1 (D33) |

- Garmin: Locus ships the library R8-processed, without a relinking path; the owner accepts this
  §6 gap and excludes the package from R8 if it is raised (D33). Asamm can waive §6 only for its
  own code, not for the parts derived from JGarminImgParser (locus-core Q1).
- locus-core `third_party_licenses.md` risk item 2 assumes Mapsforge must stay "replaceable,
  not modified"; it ignores the waiver and is updated in M9 (I8).
