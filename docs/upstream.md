# Upstream sync

Which Mapsforge release this repo is based on, and every place where the vendored code
intentionally differs from it.

| Item | Value |
|---|---|
| Upstream | [mapsforge/mapsforge](https://github.com/mapsforge/mapsforge) |
| Last synced tag | `0.30.0` (commit `54e8654f`) |
| Synced on | 2026-10-22 |
| Scope | 177 files, closure of the classes locus-core imports, see [ROADMAP](ROADMAP.md) M0 |

## Sync routine

- Diff the vendored files against the tag:
  `for f in $(git ls-files 'formats/mapsforge/legacy/*/src/main/java/*'); do u=mapsforge-${f#formats/mapsforge/legacy/}; git -C <mapsforge> show 54e8654f:$u | diff -q --strip-trailing-cr - $f; done`.
- Read each upstream release's changelog; port theme-format changes and render bug fixes by hand.
- Diff a module directory against the tag: `git -C <mapsforge> diff 54e8654f <newTag> -- <module>`.
- Update the table above and the deviation list in the same change.

## Deviations

| ID | Where | What | Why |
|---|---|---|---|
| — | — | none yet | — |
