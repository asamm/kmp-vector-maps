# Upstream sync

Which Mapsforge release this repo is based on, and every place where the vendored code or its
build intentionally differs from it.

| Item | Value |
|---|---|
| Upstream | [mapsforge/mapsforge](https://github.com/mapsforge/mapsforge) |
| Last synced tag | `0.30.0` (commit `54e8654f`) |
| Synced on | 2026-10-22 |
| Scope | 177 files, closure of the classes locus-core imports, see [ROADMAP](ROADMAP.md) M0; 15 converted to Kotlin, see below; the sync routine compares the other 162 |

## Sync routine

- Check the vendored files against the tag, from anywhere in this repo; it prints the count and
  exits non-zero on any difference:

  ```sh
  M=<path to a mapsforge clone>; root=$(git rev-parse --show-toplevel); n=0; fail=0
  for f in $(git -C "$root" ls-files 'formats/mapsforge/legacy/*/src/main/java/*'); do
    n=$((n+1)); u=mapsforge-${f#formats/mapsforge/legacy/}
    git -C "$M" show 54e8654f:"$u" | diff -q --strip-trailing-cr - "$root/$f" >/dev/null \
      || { echo "DIFF $f"; fail=1; }
  done; echo "$n files compared"; [ "$fail" = 0 ]
  ```

- Read each upstream release's changelog; port theme-format changes and render bug fixes by hand.
- Diff a module directory against the tag: `git -C <mapsforge> diff 54e8654f <newTag> -- <module>`.
- Update the table above and both deviation lists in the same change.

## Converted to Kotlin

M5 converts one area per PR in place (D24); a converted file keeps its upstream licence header and
package, and the sync routine above skips it, as it lists `.java` files only. Port upstream
changes to it by hand: `git -C <mapsforge> diff 54e8654f <newTag> -- <upstream path>`.

| Area | Step | Upstream path |
|---|---|---|
| reader, 15 files | M5.1 | `mapsforge-map-reader/src/main/java/org/mapsforge/map/reader` |

API differences of the reader (M5.1):

- Getters and the public fields of `MapFileInfo` / `SubFileParameter` are Kotlin properties; Java
  calls getters.
- Classes are final except `MapFile`; its own members (`mapFileHeader`, `mapFileInfo`,
  `mapLanguages`, `restrictToZoomRange`) are final, the `MapDataStore` overrides stay open.
- Declared nullability: `MapFile(String)` takes a non-null path; the six `read*` return
  `MapReadResult?`; `startPosition()` and `startZoomLevel()` are non-null.
- `MapFileHeader.mapFileInfo` is `lateinit`: read before `readHeader` it throws, where Java returned
  null.
- Package-private classes and members are `internal`, which is public in bytecode (members
  name-mangled), so Java can reach them.
- Dropped as dead: `MapFile.TEST_MAP_FILE` (test-only, unused), the water-flag computation, the
  never-thrown `IllegalArgumentException` in `readMapData`. New facade class `MapFileKt` holds
  `clampLongitude`.

## Code deviations

| ID | Where | What | Why |
|---|---|---|---|
| — | — | none yet | — |

## Build deviations

The Java sources are identical; how they are built is not. The JVM modules (`core`, `map`,
`map-awt`) build like upstream: `--release 8`, UTF-8. `map-reader` is Kotlin since M5.1 and keeps
the Java 8 target (B6).

| ID | What | Upstream | Here |
|---|---|---|---|
| B1 | Bytecode level, `map-android` | `--release 8` | Java 17; D8 at min-api 24 rewrites `ByteBuffer.clear()` (verified) |
| B2 | `map-android` packaging | plain JAR against `android.jar` | Android library (AAR), minSdk 24 in its manifest |
| B3 | Kotlin stdlib | none | AGP's built-in Kotlin adds `kotlin-stdlib` to `map-android` |
| B4 | Source encoding | `UTF-8` set explicitly | resolved: JVM modules set UTF-8, `map-android` gets it as AGP's default |
| B5 | Line endings | CRLF in the upstream tree | LF via `.gitattributes`; the sync routine ignores CR |
| B6 | `map-reader` language | Java, `--release 8` | Kotlin, `jvmTarget` 1.8 and `-Xjdk-release=1.8`; depends on `kotlin-stdlib` |
