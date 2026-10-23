# Licence

This repository holds parts under different licences. Each part's licence applies to all files
under its path.

| Part | Path | Licence | Full text |
|---|---|---|---|
| Mapsforge format, incl. its Kotlin conversion | `formats/mapsforge/` | LGPL-3.0-or-later + additional permission below | [COPYING.LESSER](COPYING.LESSER), [COPYING](COPYING) |
| Core, renderers, Android host API, new viewer, benchmarks | `core/`, `render/`, `android/`, `bench/`, the M3.3 viewer | Apache-2.0 | [LICENSE-APACHE](LICENSE-APACHE) |
| Garmin IMG format (planned) | `formats/garmin-img/` | LGPL-2.1 | added with the module |
| Test data | `fixtures/` | per [fixtures/README.md](fixtures/README.md) | — |

The current development viewers under `viewer/` and the golden test harness are
LGPL-3.0-or-later, as stated in their file headers; the viewers are deleted in M3.3.

## Mapsforge

Derivative work of [Mapsforge](https://github.com/mapsforge/mapsforge) 0.30.0, © the Mapsforge
contributors as named in each file header, with modifications © 2026 Asamm Software.

### Additional permission: LGPL v3 §4(d) and §4(e) waived

Applies to everything under `formats/mapsforge/`: the vendored Mapsforge code and every
modification of it by Asamm Software, including its conversion to Kotlin.

As upstream Mapsforge states for its own code (README, since commit `c78c1d79`, 2018-10-31), the
constraints mentioned in sections LGPL v3 §4(d) and §4(e) are waived. This means you are allowed
to convey a Combined Work without providing the user any way to recombine or relink the
application and without providing any shared library mechanism.

Asamm Software grants the same additional permission, under GPL v3 §7, for its own contributions
to that code. It covers no other part of this repository.

## Background

Reasoning, obligations and open questions: [docs/licence.md](docs/licence.md).
