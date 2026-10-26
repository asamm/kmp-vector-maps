# Upstream sync

Which Mapsforge release this repo is based on, and every place where the vendored code or its
build intentionally differs from it.

| Item | Value |
|---|---|
| Upstream | [mapsforge/mapsforge](https://github.com/mapsforge/mapsforge) |
| Last synced tag | `0.30.0` (commit `54e8654f`) |
| Synced on | 2026-10-22 |
| Scope | 177 files, the closure of locus-core's imports ([ROADMAP](ROADMAP.md) M0); 44 now Kotlin |

## Sync routine

The routine compares the 133 files still in Java; the Kotlin ones are listed under "Converted to
Kotlin".

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
| core model and util, 17 files | M5.2a | `mapsforge-core/src/main/java/org/mapsforge/core/{model,util}` |
| map model, util and datastore, 12 files | M5.2b | `mapsforge-map/src/main/java/org/mapsforge/map/{model,util,datastore}` |

API differences of the reader (M5.1):

- K1 Properties: getters and the public fields of `MapFileInfo` / `SubFileParameter` are Kotlin
  properties; Java calls getters.
- K2 Finality: classes are final except `MapFile`; its own members (`mapFileHeader`, `mapFileInfo`,
  `mapLanguages`, `restrictToZoomRange`) are final, the `MapDataStore` overrides stay open.
- K3 Statics: `Deserializer`, `QueryCalculations` and `RequiredFields` are `object`s, Java calls go
  through `INSTANCE`; `OptionalFields.readOptionalFields` is in its `Companion`; `MapFile` and
  `SubFileParameter` have a public `Companion`, while `wayFilterEnabled`, `wayFilterDistance` and
  `BYTES_PER_INDEX_ENTRY` stay static fields.
- K4 Nullability: `MapFile(String)` takes a non-null path; the six `read*` return `MapReadResult?`;
  `startPosition()` and `startZoomLevel()` are non-null. Nullable: `mapLanguages`,
  `readUTF8EncodedString()`, `getSubFileParameter()`, and the optional `MapFileInfo` fields
  `comment`, `createdBy`, `languagesPreference`, `startPosition`, `startZoomLevel`.
- K5 `MapFileHeader.mapFileInfo` is `lateinit`: read before `readHeader` it throws, where Java
  returned null.
- K6 Visibility: the 9 formerly package-private classes are `internal`, public in bytecode with
  plain member names; `ReadBuffer`'s package-private members (`bufferPosition`, `bufferSize`,
  `readTags`, `skipBytes`) are `internal` and name-mangled. Constructors are never mangled.
- K7 Dropped as dead: `MapFile.TEST_MAP_FILE` (test-only, unused), the water-flag computation, the
  never-thrown `IllegalArgumentException` in `readMapData`.
- K8 Added: the facade classes `MapFileKt` (`clampLongitude`) and `ReadBufferKt` (`readFully`).

API differences of core model and util (M5.2a, K9–K15); fields, statics and getter names stay as Java sees
them (`@JvmField`, `@JvmStatic`, `const`):

- K9 Properties for Kotlin: the no-argument getters (`Tile.getOrigin()`, `Rectangle.getWidth()`,
  `BoundingBox.getCenterPoint()` and the like) are Kotlin properties, except `LatLong.getLatitude()`
  and `getLongitude()`, which stay functions beside the fields.
- K10 `LatLongUtils`, `MercatorProjection`, `Parameters`, `Utils`, `IOUtils` are `object`s; Java still
  calls them statically.
- K11 Nullability declared: `Tag.key` and `Tag.value`, `Tile.parent`, `LineString.interpolate`,
  `LineSegment.clipToRectangle`; `Utils.deduplicate*` take `MutableCollection<T?>`.
- K12 `Tile`'s cached box and origin fields are renamed, so its serialized form differs from upstream.
- K13 Finality: the classes stay open, their members are final, except `equals`, `hashCode`,
  `toString`, `compareTo`, `removeEldestEntry` and `WorkingSetCache.setWorkingSet`. `LRUCache`'s
  `size()`, `keySet()`, `values()` and `entrySet()` are Kotlin's final collection bridges over new
  open `getSize()`, `getKeys()`, `getValues()` and `getEntries()`.
- K14 Added statics: public `Companion` fields, `INSTANCE` on the objects, `getEntries()` on the
  `Parameters` enums, `Rotation.rotate$default`.
- K15 Non-null parameters throw at entry: `Rotation.rotate(Point)` / `rotate(Rectangle)` with no
  rotation, `Tile.tileAreasOverlap` across zoom levels, `LineSegment(Point, Point)` and
  `LatLongUtils.contains` with an empty polygon accepted null upstream; elsewhere null failed already, now with Kotlin's message.

API differences of map model, util and datastore (M5.2b, K16–K22), on the same pattern as K9–K15:

- K16 `MapDataStore` declares what upstream returns: `boundingBox()`, `startPosition()`,
  `startZoomLevel()` and the `read*` are nullable; the `Tile`, `BoundingBox` and tag-list
  parameters are non-null; `language`, `extract` and `extractLocalized` stay nullable as upstream.
  Its non-abstract members stay open; `mapCallback` is a property; `priority` stays a protected
  field, so Kotlin outside a subclass calls `getPriority()`. A
  Kotlin override must use the declared types: Locus's `wayAsLabelTagFilter(MutableList<Tag>?)` in
  `MapsForgeV4RendererCore` becomes `(List<Tag>)`. The never-thrown `IllegalArgumentException` in
  the three area reads is dropped, as in K7.
- K17 `DisplayModel` getters with setters are properties (`backgroundColor`, `themeCallback`,
  `tileSizeMultiple`, `userScaleFactor`; read-only `maxTextWidth`, `scaleFactor`, `tileSize`); its
  static scale-factor accessors lock the companion, and Java callers also the class. Since C4 the
  device, line, symbol and text scales are per model (`deviceScale`, `lineScaleFactor`,
  `symbolScaleFactor`, `textScaleFactor`, all in `equals`/`hashCode`); the statics only seed new
  models, so a model created before a static is set keeps the old value.
- K18 `Way` and `PointOfInterest` compare to a nullable other (`Comparable<Way?>`), so a null still
  sorts last; `Way.labelPosition` is nullable.
- K19 `PoiWayBundle`'s package-private fields are `internal`, public in bytecode.
- K20 `Observable` and the `ObservableInterface` take a nullable observer and throw
  `IllegalArgumentException` for null, as upstream.
- K21 `MultiMapDataStore(null)` throws at entry; upstream failed on the first read. Its six
  `readAll*` share one loop; behaviour is unchanged.
- K22 `LayerUtil` is an `object`, as K10; its private constructor no longer throws.

## Code deviations

| ID | Where | What | Why |
|---|---|---|---|
| C1 | reader `IndexCache` | the LRU map is read and written under its lock | upstream races on it from concurrent reads (M5.1) |
| C2 | reader `IndexCache`, `ReadBuffer` | positional reads until full, no seek under a lock | concurrent block reads no longer queue in the reader; on Windows the JDK still serialises them (M5.1) |
| C3 | reader `ReadBuffer`, `MapFile`, `IndexCache` | overflow-safe string length and index block size; a missing debug signature handled like a wrong one | an overflowing string or index size no longer throws; other malformed data still fails the tile (M5.1) |
| C4 | `DisplayModel`, theme `RenderTheme`, `RenderThemeBuilder`, `RenderInstruction` | device, line, symbol and text scales per model; the theme reads them from its model; the statics seed new models | a static set by one host (`AndroidGraphicFactory` sets the density) no longer changes other renderers (M5.2, D28) |

## Build deviations

The Java sources are identical; how they are built is not. The JVM module `map-awt` builds
like upstream: `--release 8`, UTF-8. `map-reader` (M5.1), `core` (M5.2a) and `map` (M5.2b) are
Kotlin, `core` and `map` with their remaining Java, and keep the Java 8 target (B6).

| ID | What | Upstream | Here |
|---|---|---|---|
| B1 | Bytecode level, `map-android` | `--release 8` | Java 17; D8 at min-api 24 rewrites `ByteBuffer.clear()` (verified) |
| B2 | `map-android` packaging | plain JAR against `android.jar` | Android library (AAR), minSdk 24 in its manifest |
| B3 | Kotlin stdlib | none | `kotlin-stdlib` on every legacy module: through `core` (B6) and AGP's built-in Kotlin in `map-android` |
| B4 | Source encoding | `UTF-8` set explicitly | resolved: JVM modules set UTF-8, `map-android` gets it as AGP's default |
| B5 | Line endings | CRLF in the upstream tree | LF via `.gitattributes`; the sync routine ignores CR |
| B6 | `map-reader`, `core` and `map` language | Java, `--release 8` | Kotlin, `jvmTarget` 1.8 and `-Xjdk-release=1.8`, the remaining Java with `--release 8`; depends on `kotlin-stdlib` |
