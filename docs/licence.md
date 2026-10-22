# Licence position

What the Mapsforge licence means for this repo and for Locus, now and after the Kotlin conversion.
Engineering reading, not legal advice; the legal check is locus-core plan Q1.

**Author:** Menion · **Started:** 2026-10-22

## Upstream terms

| Item | Value | Evidence |
|---|---|---|
| Licence | LGPL-3.0-or-later | file headers, `COPYING.LESSER` |
| Exception | LGPL §4(d) and §4(e) **waived** | upstream `README.md`, commit `c78c1d79` (2018-10-31) |
| Copyright holders | mapsforge.org plus individual contributors per file | headers, e.g. `DatabaseRenderer.java` |
| Dependencies | androidsvg Apache-2.0, kxml2 BSD-style, svgSalamander LGPL-2.1 / BSD | upstream build files |

- The waiver is only in the upstream README, **not** in file headers or `COPYING.LESSER`.
- It is an additional permission under GPL-3.0 §7, which a redistributor may drop. This repo must
  carry it forward explicitly, or Locus loses it.

## Kotlin conversion

- A translation is a modification under GPL-3.0 §0, so the converted code stays a derivative work:
  **LGPL-3.0-or-later**, whatever share of Java lines survives.
- Relicensing (Apache, proprietary) needs consent of every copyright holder; not realistic.
- Only a clean-room rewrite escapes it, which is option O4, rejected in the locus-core plan.
- Files written from scratch (core, viewers, tools) could carry any licence; they use
  LGPL-3.0-or-later. Licences stay per format module: Garmin IMG keeps LGPL-2.1 when it joins (M3).

## Obligations of this repo

| ID | Obligation | Source | How |
|---|---|---|---|
| L1 | Keep every copyright notice and licence header | GPL-3.0 §4 | hard rule in `CLAUDE.md` |
| L2 | Mark modified files with a notice and date | GPL-3.0 §5(a) | add `Copyright 2026 Asamm Software` to the header list on first change |
| L3 | Ship `COPYING` and `COPYING.LESSER` | LGPL §4(b) | repo root, done |
| L4 | Carry the §4(d)/(e) waiver, and grant it for Asamm's own changes | GPL-3.0 §7 | `README.md` licence section, POM description |
| L5 | Make source available to recipients of binaries | GPL-3.0 §6 | public repo, one tag per release |

## Obligations of Locus

| Item | Without waiver | With waiver |
|---|---|---|
| Notice that the library is used and is LGPL | required | required, already in `LibLicensesLco` |
| Licence texts in the app | required | required |
| User can relink a modified library, §4(d) | required | **waived** |
| Installation information, §4(e) | required | **waived** |
| Library may be modified, R8-minified, statically merged | only with §4(d) | allowed |
| iOS (static linking) | effectively blocked | allowed |

- Net change for Locus: none in kind. It moves from upstream Mapsforge to an Asamm-modified
  Mapsforge under the same terms.
- locus-core [`third_party_licenses.md`](https://github.com/asamm/locus-core/blob/development/docs/reference/third_party_licenses.md)
  risk item 2 says the dependency is acceptable "only while the library stays replaceable/relinkable
  (separate artifact, not modified)". That ignores the waiver and needs updating when Locus switches
  (P5).

## Repository visibility

**Public**, as now. L5 is then met with no extra process. Publishing via GitHub Packages still
needs a token to read, independent of visibility.
