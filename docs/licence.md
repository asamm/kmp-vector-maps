# Licence position

What each part's licence means for this repo and for Locus. Engineering reading, not legal advice;
the legal check is locus-core plan Q1.

**Author:** Menion · **Started:** 2026-10-22

## Parts

| Part | Licence |
|---|---|
| `formats/mapsforge/**`, including its Kotlin conversion | LGPL-3.0-or-later + §4(d)/(e) waiver |
| `core/`, `render/`, `android/`, `dev/bench/`, `viewer/` (written fresh) | Apache-2.0 (D19) |
| `formats/garmin-img/` (M4) | LGPL-2.1, -only or -or-later open (Q13) |
| Fixtures | per [dev/fixtures/README.md](../dev/fixtures/README.md): ODbL map data, Apache-2.0 theme |

- An Apache-2.0 core keeps LGPL-2.1 and LGPL-3.0 code out of one derivative work.
- The golden harness carries LGPL-3.0-or-later headers, as did the throwaway `viewer/`; that
  viewer was deleted in M3.3, not relicensed. Neither it nor the kmp-garmin-img viewer gave code to
  the new one, only its feature list (D18).

## Mapsforge

| Item | Value |
|---|---|
| Licence | LGPL-3.0-or-later (headers, `COPYING.LESSER`) |
| Waiver | §4(d) and §4(e), upstream `README.md` only, commit `c78c1d79` (2018-10-31) |
| Headers | 176 of 177 vendored files; `TileDependencies.java` has none upstream |
| Dependencies | androidsvg Apache-2.0; kxml2 BSD + public domain; svgSalamander BSD (1.1.3 POM) |
| Embedded code | `TextStroke.java` and the libGDX-marked parts of `LatLongUtils.kt`: Apache-2.0 |

- The waiver is a GPL-3.0 §7 additional permission a redistributor may drop, so this repo carries
  it in [LICENSE.md](../LICENSE.md) and grants it for Asamm's changes.
- A Kotlin translation is a modification (GPL-3.0 §0): the converted code stays LGPL-3.0-or-later.
  Relicensing needs every copyright holder; only a clean-room rewrite (rejected O4) escapes it.
- Open (Q14): 40 of 66 upstream authors committed only before the waiver; vendored headers name
  mapsforge.org (122 files) and Ludwig M Brinckmann (75 files), counted with:

  ```sh
  git ls-files 'formats/mapsforge/legacy/*.java' 'formats/mapsforge/legacy/*.kt' | xargs grep -l 'Copyright.*<name>' | wc -l
  ```

## Obligations of this repo

| ID | Obligation | How |
|---|---|---|
| L1 | Keep every copyright notice and licence header | hard rule in `CLAUDE.md` |
| L2 | Mark modified files with a notice and date | add `Copyright 2026 Asamm Software` on first change |
| L3 | Ship `COPYING` and `COPYING.LESSER` | repo root |
| L4 | Carry the waiver | `LICENSE.md`, later the POM |
| L5 | Source for recipients of binaries | public repo, one tag per release |

## Obligations of Locus

| Item | Mapsforge (waiver) | Garmin IMG (LGPL-2.1) |
|---|---|---|
| Notice and licence texts in the app | required, in `LibLicensesLco` | required |
| User can relink a modified library | waived | required (§6) |
| R8-minified, merged into the APK | allowed | only with a relinking path |
| Public source | this repo | `kmp-garmin-img` is private (Q12) |

- Garmin: Locus already ships kmp-garmin-img R8-processed, without a relinking path or public
  source — an existing gap. Asamm can waive §6 only for its own code, not for the parts derived
  from JGarminImgParser (Q13, locus-core Q1).
- locus-core `third_party_licenses.md` risk item 2 assumes Mapsforge must stay "replaceable,
  not modified"; it ignores the waiver and is updated in M9 (I8).
