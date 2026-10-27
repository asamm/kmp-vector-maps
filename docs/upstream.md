# Upstream sync

Which Mapsforge release this repo is based on, and every place where the vendored code or its
build intentionally differs from it.

| Item | Value |
|---|---|
| Upstream | [mapsforge/mapsforge](https://github.com/mapsforge/mapsforge) |
| Last synced tag | `0.30.0` (commit `54e8654f`) |
| Synced on | 2026-10-22 |
| Scope | 177 files, the closure of locus-core's imports ([ROADMAP](ROADMAP.md) M0); 117 now Kotlin, 20 hillshading files removed (C5, C6) |

## Sync routine

The routine compares the 40 files still in Java; the Kotlin ones are listed under "Converted to
Kotlin".

- Check the vendored files against the tag, from anywhere in this repo; it prints the count, marks
  the files a C-row changes as `EXPECTED` and exits non-zero on any other difference:

  ```sh
  M=<path to a mapsforge clone>; root=$(git rev-parse --show-toplevel); n=0; fail=0
  # changed by C4, C5, C6
  r=map/src/main/java/org/mapsforge/map/rendertheme
  expected=" $r/rule/RenderTheme.java $r/rule/RenderThemeBuilder.java \
  $r/renderinstruction/RenderInstruction.java $r/rule/RenderThemeHandler.java \
  $r/renderinstruction/Area.java "
  for f in $(git -C "$root" ls-files 'formats/mapsforge/legacy/*/src/main/java/*'); do
    n=$((n+1)); u=mapsforge-${f#formats/mapsforge/legacy/}
    git -C "$M" show 54e8654f:"$u" | diff -q --strip-trailing-cr - "$root/$f" >/dev/null && continue
    case "$expected" in
      *" ${f#formats/mapsforge/legacy/} "*) echo "EXPECTED $f" ;;
      *) echo "DIFF $f"; fail=1 ;;
    esac
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
| core graphics, 27 files (26 after C6) | M5.3a | `mapsforge-core/src/main/java/org/mapsforge/core/graphics` |
| Android and AWT graphics, 25 files (23 after C6) | M5.3b | `mapsforge-map-android/src/main/java/org/mapsforge/map/android/{graphics,util}`, `mapsforge-map-awt/src/main/java/org/mapsforge/map/awt/graphics` |
| renderer, tile cache, job queue, `TilePosition`, 17 files | M5.4b | `mapsforge-map/src/main/java/org/mapsforge/map/layer/{renderer,cache,queue}`, `mapsforge-map/src/main/java/org/mapsforge/map/layer/TilePosition.java` |
| map elements and label stores, 7 files | M5.5b | `mapsforge-core/src/main/java/org/mapsforge/core/mapelements`, `mapsforge-map/src/main/java/org/mapsforge/map/layer/labels/{LabelStore,MapDataStoreLabelStore,TileBasedLabelStore}.java` |

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
  models, so a model created before a static is set keeps the old value. Java `model.lineScale`
  still compiles and reads the static; use `getLineScaleFactor()`.
- K18 `Way` and `PointOfInterest` compare to a nullable other (`Comparable<Way?>`), so a null still
  sorts last; `Way.labelPosition` is nullable.
- K19 `PoiWayBundle`'s package-private fields are `internal`, public in bytecode.
- K20 `Observable` and the `ObservableInterface` take a nullable observer and throw
  `IllegalArgumentException` for null, as upstream.
- K21 `MultiMapDataStore(null)` throws at entry; upstream failed on the first read. Its six
  `readAll*` share one loop; behaviour is unchanged.
- K22 `LayerUtil` is an `object`, as K10; its private constructor no longer throws.

API differences of core graphics (M5.3a, K23–K25), on the same pattern:

- K23 Interface getters are properties with the same Java names: `Bitmap` `mutex`, `height`,
  `width`, `isDestroyed`; `Canvas` `dimension`, `height`, `width`; `GraphicContext` `isAntiAlias`,
  `isFilterBitmap`; `Paint` `color`, `strokeWidth`, `isComplexStyle`, `isTransparent`; `Path`
  `isEmpty`; `HillshadingBitmap` `areaRect`, `padding`, `sizeBytes`; `TileBitmap` `timestamp`,
  `isExpired`; `Position` `isOn*`. Parameters are non-null except where upstream passes or
  accepts null: `Canvas.setBitmap` (AWT), `shadeBitmap`, `createMonoBitmap` buffer and area, the
  back paint and symbol of `createPointTextContainer`, `setBitmapShader`, `setDashPathEffect`, the
  prefix and result of `platformSpecificSources`; `HillshadingBitmap.areaRect` is nullable, as the
  area passed in.
- K24 `BaseBitmap.mutex` is no longer a protected field, only a final `getMutex()`; `GraphicUtils` is an
  `object` (K10); `Paint.FONT_PADDING_FACTOR` stays a static field of the interface, from a
  companion.
- K25 `fromString` is static through a companion; `Align.fromString` stays an instance method, as
  upstream. `SymbolOrientation` and `TextOrientation.fromString(null)` throw at entry; upstream's
  `switch` threw too.

API differences of the Android and AWT graphics (M5.3b, K26–K30):

- K26 Interface getters are overriding properties, as K23; also
  `AndroidGraphicFactory.nonTransparentBitmapConfig`. Statics sit in companions with the same Java
  names (`INSTANCE`, `TRANSPARENT`, `TRANSPARENT_BITMAP`, `MONO_ALPHA_BITMAP`, `DEBUG_BITMAPS`,
  `KEEP_RESOURCE_BITMAPS`, `DEFAULT_SIZE`, `getBitmap`, `getPaint`, `getFontPadding`, ...);
  `AndroidParameters` and `AndroidSvgBitmapStore` are objects (K10).
- K27 Package-private becomes `internal`, public in bytecode (K6): `AwtCanvas`, `AwtMatrix`,
  `AwtPath`, `AndroidCanvas`, `AndroidMatrix`, `AndroidPaint`, `AndroidPath`, the platform objects
  they wrap (`canvas`, `paint`, `path`, `matrix`, `AwtBitmap.bufferedImage`, the `AwtPaint` font,
  stroke, style and texture paint) and the factories' `getColor`, `getMatrix`, `getPath`,
  `getAffineTransform`, mangled for Java. `AndroidBitmap.bitmap` goes from protected to `internal`,
  `AwtPointTextContainer.drawClashBounds` from protected to `internal`; the package-private
  constructors of `AwtPaint`, `AwtResourceBitmap`, `AwtPointTextContainer`, `AndroidBitmap(width,
  height, config)`, `AndroidResourceBitmap`, `AndroidPointTextContainer` and both
  `AndroidTileBitmap` ones, the `MonoColorModel` constructor and fields and the `HillshadingTemps`
  constructor are wider.
- K28 Classes stay open, overrides and public members are final (K13); Android's protected
  `destroy`, `destroyBitmap`, `getBoundary` and `drawClashBounds` stay open. `AwtPaint`'s Java
  color field is `awtColor`, clear of `Paint.color`.
- K29 Declared nullability: `AndroidGraphicFactory.getBitmap()` and `getCanvas()` are null once
  destroyed; `AwtGraphicFactory.getGraphics()` and `AwtCanvas.getGraphicObject()` nullable;
  `AwtBitmap.bufferedImage`, both `getPaint()` non-null. Null that upstream returned on now throws
  at entry: the text of `drawText`, `drawPathText`, `drawTextRotated` and both canvases' `rotate`
  (non-null in K23), `getBytesPerPixel(null)`, `convertToAndroidBitmap` of a bitmap-less drawable
  before Android P, `createTileBitmap` from a null stream (upstream threw
  `CorruptedInputStreamException` on Android, `IllegalArgumentException` on AWT),
  `AwtSvgBitmap.getResourceBitmap` from a null stream (upstream wrapped it in `IOException`). The
  `AwtBitmap` family (`AwtTileBitmap`, `AwtHillshadingBitmap`) takes a non-null image; upstream
  accepted null as destroyed, so `isDestroyed` is always false.
- K30 Dropped as dead (K7): the `unknown ...` throws after exhaustive enum mappings, AWT's
  `UNKNOWN_STYLE`, a write-only local in `TextStroke`. AWT overrides declare `@Throws(IOException)`.

API differences of the renderer, tile cache and job queue (M5.4b, K31–K38):

- K31 Getters are properties with the same Java names: `Job.key`; `PolylineContainer` `tags`,
  `layer`, `upperLeft`, `lowerRight`, `isClosedWay`, `centerAbsolute`, `coordinatesAbsolute`,
  `coordinatesRelativeToOrigin`; `ShapeContainer.shapeType`; `StandardRenderer` `startPosition`,
  `startZoomLevel`, `zoomLevelMax`; `DatabaseRenderer.mapDatabase`. `TileCache.getCapacity()`,
  `getCapacityFirstLevel()` stay functions, which leaves the driver's `DrawnTiles` unchanged;
  `StandardRenderer.getRenderCallback()` stays one as the golden harness, also compiled against
  upstream, overrides it.
- K32 Statics: `RendererUtils` is an object (K10), `GeometryUtils` and `WayDecorator` internal
  objects; `Job.composeKey` sits in a companion;
  `StandardRenderer.DEFAULT_START_ZOOM_LEVEL` is a primitive `const` (upstream a boxed `Byte`),
  `ZOOM_MAX` a `const`, `TAG_NATURAL_WATER` and `DatabaseRenderer.LOGGER` protected fields.
- K33 Declared nullability: nullable are the `tile` of `Job` and `RendererJob` and `RendererJob`'s
  `mapFile` (upstream's `IllegalArgumentException` kept), `RendererJob.renderThemeFuture`,
  `StandardRenderer.mapDataStore`, `startPosition`, `startZoomLevel`, the `DatabaseRenderer` tile
  cache, label store and `mapDatabase`, `executeJob`'s job and result, `processReadMapData`'s
  result, `WayDecorator.renderText`'s stroke, the cache observers, `TileCache.get` and
  `getImmediately`. The rest is non-null and throws at entry: `InMemoryTileCache.put` (an NPE where
  upstream threw `IllegalArgumentException`), the render callbacks (except the symbol `boundary`),
  the `TileCache` keys (upstream returned null or false), `RendererJob.otherTile` (an NPE where
  upstream threw `IllegalArgumentException`), and, where upstream accepted null: `RendererJob`'s
  `displayModel`, the `PolylineContainer` `upperLeft`, `lowerRight` and `tags`, the `TilePosition`
  tile and point, the `ShapePaintContainer` container, paint and curve style, the renderers'
  `graphicFactory` (upstream failed per tile, logged) and `CanvasRasterer`'s `renderContext`.
- K34 Package-private becomes `internal`, public in bytecode (K6): `CircleContainer`,
  `GeometryUtils`, `WayDecorator`, `BitmapLRUCache`; mangled for Java: the `ShapePaintContainer`
  fields, the `TileDependencies` constructor and members (the class stays public, as
  `DatabaseRenderer.tileDependencies` exposes it), `CanvasRasterer` `drawMapElements`, `fill`, both
  `fillOutsideAreas` and `setCanvasBitmap`, `StandardRenderer.renderBitmap`;
  `ShapeType` is public, as `ShapeContainer.shapeType` exposes it.
- K35 Classes stay open, except the internal `CircleContainer` and `BitmapLRUCache`, now final, and
  the objects of K32 and K34; public members are final (K13); the protected render hooks,
  `getRenderCallback`, `MyRenderCallback` and `equals`/`hashCode` of `Job` and `RendererJob` stay
  open.
- K36 `DatabaseRenderer`'s own `renderLabels` field is `drawLabels`: Kotlin cannot hide
  `StandardRenderer.renderLabels`, which on a `DatabaseRenderer` reads
  `renderLabels || cacheLabels`.
- K37 `StandardRenderer`'s 3-argument constructor comes from `@JvmOverloads`;
  `CanvasRasterer.DEBUG_COUNTS` is a private constant.
- K38 The private constructors of `GeometryUtils`, `RendererUtils` and `WayDecorator` that threw
  are gone (K22).

API differences of the map elements and label stores (M5.5b, K39–K45):

- K39 Getters are properties with the same Java names: `MapElementContainer` `priority`, `point`,
  `isNotVisible` (the protected `priority` field is gone, `PointTextContainer`'s public
  `isNotVisible` field is an overriding property); `LabelStore.getVersion()` stays a function, as
  locus-core calls it.
- K40 Fields read elsewhere stay `@JvmField`: the protected `xy`, `display`, `boundaryAbsolute`,
  `clashRect`, `clashRotationDegrees`, the public `PointTextContainer` fields, `SymbolContainer`
  `alignCanvas`, `symbol`, `theta`, the protected `boundary` of `SymbolContainer` and
  `WayTextContainer`. `PointTextContainer.DEBUG_CLASH_BOUNDS` is a protected constant,
  `getRotatedRelativePosition` and `getClashRectangleTransformed` sit in its companion.
- K41 Added: an internal `displayInternal` on `MapElementContainer`, for `clashesWith` to read
  another container's protected `display`, which Java reached through package access.
- K42 Declared nullability: nullable are the `draw` parameters (as the Android and AWT overrides),
  the `rotation` of the clash and position helpers, the results of `getClashRect`,
  `getClashRectangleTransformed` and `getBoundary`, the back paints, `PointTextContainer`'s
  symbol, the `SymbolContainer` boundary and both tiles of `LabelStore.getVisibleItems` (locus-core
  passes nullable ones). The rest is non-null and throws at entry; a null
  `MapDataStoreLabelStore` store, theme future or display model throws in the constructor, where
  upstream's every query returned an empty list (the query swallows the exception).
- K43 `getVisibleItems` returns and `storeMapItems` takes a `MutableList`; `TileBasedLabelStore`
  keeps read-only values.
- K44 Package-private becomes `internal` (K6): the `MapDataStoreLabelStore` fields, mangled for
  Java.
- K45 Classes stay open or abstract, public members are final (K13); `equals`, `hashCode`,
  `toString`, `compareTo`, `clashesWith`, `getClashRect`, `isNotVisible` (the last three final in
  `PointTextContainer`), the protected boundary and position hooks and `removeEldestEntry` stay
  open. `toString` is unchanged, as the soak reads the way text from it (D31).

## Code deviations

| ID | Where | What | Why |
|---|---|---|---|
| C1 | reader `IndexCache` | the LRU map is read and written under its lock | upstream races on it from concurrent reads (M5.1) |
| C2 | reader `IndexCache`, `ReadBuffer` | positional reads until full, no seek under a lock | concurrent block reads no longer queue in the reader; on Windows the JDK still serialises them (M5.1) |
| C3 | reader `ReadBuffer`, `MapFile`, `IndexCache` | overflow-safe string length and index block size; a missing debug signature handled like a wrong one | an overflowing string or index size no longer throws; other malformed data still fails the tile (M5.1) |
| C4 | `DisplayModel`, theme `RenderTheme`, `RenderThemeBuilder`, `RenderInstruction` | device, line, symbol and text scales per model; the theme reads them from its model; the statics seed new models | a static set by one host (`AndroidGraphicFactory` sets the density) no longer changes other renderers (M5.2, D28) |
| C5 | `layer/hills` (15 files), `HillshadingContainer`, `Hillshading`; `CanvasRasterer`, `ShapeType`, `RenderTheme`, `RenderThemeHandler`, `DatabaseRenderer`, `StandardRenderer` | the 17 files removed; `DatabaseRenderer`, `StandardRenderer` take the hills config as an ignored `Object` (`Any?` in Kotlin); `ShapeType.HILLSHADING`, `CanvasRasterer.drawHillshading`, `RenderTheme.matchHillShadings`, `StandardRenderer.hillsRenderConfig` and the `RenderThemeHandler.HILLSHADING_*` statics removed; `interruptAndDestroy()` is a no-op; `RenderThemeHandler` still validates `<hillshading>` and gives it its level, then drops it | hillshading is out of scope and unused by any consumer; the constructor keeps the upstream arity for the golden harness shared with upstream (M5.4a, D30) |
| C6 | `DisplayModel`, `StandardRenderer`, `CanvasRasterer`, theme `Area`; graphics `GraphicContext`, `GraphicFactory`, `HillshadingBitmap`, the Android and AWT canvases, factories and hillshading bitmaps | `NUMBER_OF_THREADS`, `POLYGON_ZOOM_MIN`, `POLYGON_EXCEPTIONS` are per model (`numberOfThreads`, `polygonZoomMin`, `polygonExceptions`, the last two in `equals`/`hashCode`), seeded from `Parameters`; `shadeBitmap`, `createMonoBitmap`, `HillshadingBitmap` and their implementations removed (the 3 files and their state), superseding their mentions in K23, K27 and K29; `Area` reads the thread count from the job's model, as `CanvasRasterer` does; AWT `drawBitmap` still clears palette images | a static set by one host no longer changes other renderers (D28); hillshading is drawn by the host at the `HILLSHADE` anchor (D13, M6.2), not by Mapsforge (M5.4 fixes) |

## Build deviations

The Java sources are identical except the C-rows; how they are built is not. `map-reader` (M5.1),
`core` (M5.2a, M5.3a, M5.5b), `map` (M5.2b to M5.5b) and `map-awt` (M5.3b) are Kotlin, `map` with
its remaining Java (the theme), and keep the Java 8 target and UTF-8 (B6); `map-android` is Kotlin
from M5.3b.

| ID | What | Upstream | Here |
|---|---|---|---|
| B1 | Bytecode level, `map-android` | `--release 8` | Java 17, Kotlin from M5.3b; D8 at min-api 24 rewrites `ByteBuffer.clear()` (verified) |
| B2 | `map-android` packaging | plain JAR against `android.jar` | Android library (AAR), minSdk 24 in its manifest |
| B3 | Kotlin stdlib | none | `kotlin-stdlib` on every legacy module: through `core` (B6) and AGP's built-in Kotlin in `map-android` |
| B4 | Source encoding | `UTF-8` set explicitly | resolved: JVM modules set UTF-8, `map-android` gets it as AGP's default |
| B5 | Line endings | CRLF in the upstream tree | LF via `.gitattributes`; the sync routine ignores CR |
| B6 | `map-reader`, `core`, `map` and `map-awt` language | Java, `--release 8` | Kotlin, `jvmTarget` 1.8 and `-Xjdk-release=1.8`, the remaining Java with `--release 8`; depends on `kotlin-stdlib` |
