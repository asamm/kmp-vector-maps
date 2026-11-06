# Upstream sync

Which Mapsforge release this repo is based on, and every place where the vendored code or its
build intentionally differs from it.

| Item | Value |
|---|---|
| Upstream | [mapsforge/mapsforge](https://github.com/mapsforge/mapsforge) |
| Last synced tag | `0.31.0` (commit `19518627`); vendored from `0.30.0` (commit `54e8654f`) |
| Synced on | 2026-11-04 |
| Golden reference | `0.30.0` jars from JitPack (`upstreamMapsforge`); later tags from source, see below |
| Scope | 177 files, the closure of locus-core's imports ([ROADMAP](ROADMAP.md) M0); 159 now Kotlin, 18 hillshading files removed (C5, C6; 2 restored reduced in C12) |

## Following upstream

Every vendored file is Kotlin since M5.7a, so upstream changes are ported by hand, one tag at a
time, with reading kept identical to the new tag (C9 aside: a caption placed against a
non-square symbol) and drawing within the accepted differences (D37). The helpers need Python 3, `gh` and, for step 3, the Android SDK in `local.properties`:

1. **List.** `python dev/mapsforge/upstream/upstream_diff.py <lastTag> <newTag>` puts every changed
   upstream file in one group: `VENDORED` (a vendored file's upstream), `BUILD`, `THEME`, `MODULE`
   (other files of the vendored modules) or `OUT`. Read the commits and the upstream
   `docs/Changelog.md` too.
2. **Port.** Each `VENDORED` change goes into its Kotlin file, keeping the C-rows; the upstream
   diff is `gh api repos/mapsforge/mapsforge/compare/<lastTag>...<newTag>` or the commit page.
   `BUILD`: dependency versions of the vendored modules (`androidsvg`, `kxml2`, `svgSalamander` in
   `gradle/libs.versions.toml`). `THEME`: a new element or attribute in `renderTheme.xsd` needs the
   theme parser. `MODULE`: read; layers, overlays and views are not vendored, but a fix there may
   point at a vendored class.
3. **Verify.** `python dev/mapsforge/upstream/verify_upstream.py <newTag>` builds the tag from
   source and runs both Mapsforge goldens against it (JitPack fails to build `mapsforge-map-android`
   since 0.31.0, and Maven Central stops at 0.25.0). Before porting it shows what the new tag
   changes in the render, after porting it must pass; a difference that stays is a new C-row,
   decided with the owner. Label-free tiles pass up to 0.5 % differing pixels without an entry, so
   read `build/golden/diff` even on a pass; an entry reported stale is removed, with its C-row when
   the deviation is gone.
4. **CI.** If JitPack has the tag's jars, raise `upstreamMapsforge` and replace the five
   components' checksums in `gradle/verification-metadata.xml`; otherwise CI keeps the last tag with
   jars, which is sound only while step 3 passes.
5. **Record.** The table above, a sync-log row below, both deviation lists and the K-rows when an
   API changed, all in the sync's commit.

From M6.0 (D37), when the vendored output changes on purpose, step 3 still compares reading exactly;
drawing passes through the committed accepted-differences entries
(`dev/mapsforge/golden/accepted-differences.txt`), labels are compared against our own goldens
([architecture.md](architecture.md), golden strategy): they do not depend on the upstream tag, so
step 3 changes them only when a port changes our labels, and the port then refreshes them
(`updateLabelGoldens`). No golden catches a label change of the tag itself: a `VENDORED` change in
label placement, text measuring or symbol drawing (symbols are stored with the labels) is ported
and reviewed by hand.

The local Mapsforge clone (`54e8654f`) is read-only and stays on 0.30.0; newer tags are read
through `gh` or the scripts.

### Sync log

| Tag | Date | Upstream commits | Result |
|---|---|---|---|
| `0.30.0` | 2026-10-22 | — | vendored (M0, D4) |
| `0.31.0` | 2026-11-04 | 7: `Marker` hit test without a bitmap, `TileRendererLayer` re-add, POI v5 multilingual categories, map-writer tag mapping, XML formatting of `renderTheme.xsd` and themes, changelog, release | no vendored file changed, nothing ported; dependencies unchanged; goldens pass against 0.31.0 from source |

## Converted to Kotlin

M5 converts one area per PR in place (D24); a converted file keeps its upstream licence header.
Upstream `org.mapsforge.X` is `com.asamm.vectormaps.mapsforge.X` since M5.7b (K64). Upstream changes
are ported by hand, as "Following upstream" above describes.

| Area | Step | Upstream path |
|---|---|---|
| reader, 15 files | M5.1 | `mapsforge-map-reader/src/main/java/org/mapsforge/map/reader` |
| core model and util, 17 files | M5.2a | `mapsforge-core/src/main/java/org/mapsforge/core/{model,util}` |
| map model, util and datastore, 12 files | M5.2b | `mapsforge-map/src/main/java/org/mapsforge/map/{model,util,datastore}` |
| core graphics, 27 files (26 after C6) | M5.3a | `mapsforge-core/src/main/java/org/mapsforge/core/graphics` |
| Android and AWT graphics, 25 files (23 after C6) | M5.3b | `mapsforge-map-android/src/main/java/org/mapsforge/map/android/{graphics,util}`, `mapsforge-map-awt/src/main/java/org/mapsforge/map/awt/graphics` |
| renderer, tile cache, job queue, `TilePosition`, 17 files (18 after C12) | M5.4b | `mapsforge-map/src/main/java/org/mapsforge/map/layer/{renderer,cache,queue}`, `mapsforge-map/src/main/java/org/mapsforge/map/layer/TilePosition.java` |
| map elements and label stores, 7 files | M5.5b | `mapsforge-core/src/main/java/org/mapsforge/core/mapelements`, `mapsforge-map/src/main/java/org/mapsforge/map/layer/labels/{LabelStore,MapDataStoreLabelStore,TileBasedLabelStore}.java` |
| theme rules, matchers, `RenderTheme`, `RenderContext`, `RenderCallback`, `ThemeCallback`, 20 files | M5.6a | `mapsforge-map/src/main/java/org/mapsforge/map/rendertheme/rule/` (all but `RenderThemeBuilder`, `RenderThemeFuture`, `RenderThemeHandler`, `RuleBuilder`), `mapsforge-map/src/main/java/org/mapsforge/map/rendertheme/{RenderCallback,RenderContext,ThemeCallback}.java` |
| render instructions, 9 files (10 after C12) | M5.6b | `mapsforge-map/src/main/java/org/mapsforge/map/rendertheme/renderinstruction` |
| XML theme parser, 11 files | M5.7a | `mapsforge-map/src/main/java/org/mapsforge/map/rendertheme/{ExternalRenderTheme,XmlRenderTheme,XmlRenderThemeMenuCallback,XmlRenderThemeStyleLayer,XmlRenderThemeStyleMenu,XmlThemeResourceProvider,XmlUtils}.java`, `mapsforge-map/src/main/java/org/mapsforge/map/rendertheme/rule/{RenderThemeBuilder,RenderThemeFuture,RenderThemeHandler,RuleBuilder}.java` |

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
  `LineSegment.clipToRectangle`; `Utils.deduplicate*` declare `T : Comparable<T>?`, so elements may
  be null.
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
  `getCapacityFirstLevel()` stay functions (the driver's former `DrawnTiles` implemented them);
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
  `MapDataStoreLabelStore` store, theme future, display model or graphic factory throws in the
  constructor, where upstream's every query returned an empty list (the query swallows the
  exception; the graphic factory since K33).
- K43 `getVisibleItems` returns and `storeMapItems` takes a `MutableList`; `TileBasedLabelStore`
  keeps read-only values.
- K44 Package-private becomes `internal` (K6): the `MapDataStoreLabelStore` fields, mangled for
  Java.
- K45 Classes stay open or abstract, public members are final (K13); `equals`, `hashCode`,
  `toString`, `compareTo`, `clashesWith`, `getClashRect`, `isNotVisible` (the last three final in
  `PointTextContainer`), the protected boundary and position hooks and `removeEldestEntry` stay
  open. `toString` is unchanged, as the soak reads the way text from it (D31).

API differences of the theme rules (M5.6a, K46–K51):

- K46 Getters are properties with the same Java names: `RenderTheme` `levels`, `mapBackground`,
  `mapBackgroundOutside`; `hasMapBackgroundOutside()` and `RenderContext.getLabels()` stay
  functions.
- K47 The package-private members the theme parser uses are `internal` (public until M5.7a, for
  the Java parser): the `RenderTheme.levels` setter, `addRule`, `complete`,
  `Rule.addRenderingInstruction`, `addSubRule`, `cat` and the `Rule` matcher caches.
- K48 Package-private becomes `internal` (K6): the matcher and enum classes, `PositiveRule` and
  `NegativeRule` with their matcher fields and `exclusive`; the constructors of these, `Rule` and `RenderTheme`
  (not mangled); `Rule`'s zoom and matcher fields and its matching, scaling and lifecycle
  members, mangled for Java.
- K49 The stateless matchers (`AnyMatcher`, `ClosedWayMatcher`, `LinearWayMatcher`,
  `ElementNodeMatcher`, `ElementWayMatcher`) are objects (K10), Java still uses `INSTANCE`;
  `Rule`, `Closed` and `Element` gain companions, the enums `getEntries()`.
- K50 Classes stay open or abstract, public members are final (K13); `RuleVisitor.apply` stays
  open as the visitor's hook.
- K51 Declared nullability: `RenderCallback` parameters are non-null except the symbol
  `boundary`; a Kotlin implementer must match, so LoPoints' `PointGeneratorV4Theme` drops `?` on
  its overrides when Locus adopts this library (as K16). `ThemeCallback.getColor` takes a nullable
  origin (the theme builder passes null) and `getBitmap` returns `Bitmap?`; its other parameters
  and the `getText` result are non-null, and a Kotlin implementer must match. `RenderTheme`
  and `Rule` check their builder's display model and matchers at construction, where upstream
  failed at first use.

API differences of the render instructions (M5.6b, K52–K57):

- K52 Getters are properties with the same Java names: `RenderInstruction.category` (protected
  setter), `Symbol` `boundary` and `id`; `Symbol.getBitmap()` stays a function, as it creates the
  bitmap lazily under its lock.
- K53 Fields read elsewhere stay `@JvmField`: `RenderInstruction` `displayModel`,
  `graphicFactory`, and the protected `height`, `percent`, `width`. `Caption.DEFAULT_GAP` is a
  public constant; `RenderInstruction` and `Caption` gain public companions (K14).
- K54 Package-private and protected become `internal` (K6): `TextKey`, `RenderInstruction.Scale`,
  `scaleFromValue` (an internal type cannot leave a protected function) and the attribute-name
  constants; in bytecode all are public with plain names (the constants as static fields of
  `RenderInstruction`) except `scaleFromValue`, which is mangled.
- K55 Classes stay open or abstract, members and overrides are final (K13), including the
  protected `computeBoundary`, `createBitmap`, `transformText`, and `Symbol.getBitmap()`.
- K56 Declared nullability: constructor arguments are non-null except `relativePathPrefix` and
  `resourceProvider`, and throw at entry; `createBitmap` takes and returns nullable values,
  `TextKey.getValue` and `Symbol.getBitmap()` return nullable ones. A Java `ThemeCallback.getText`
  returning null fails at `transformText` entry instead of inside it.
- K57 The never-null `stroke` checks in `Area`, `Circle` and `Line` are gone (K7); `Area`,
  `Caption` and `PathText` read `displayModel.themeCallback` once per render instead of at each use
  (in `Area` also once per scaled stroke Paint).

API differences of the XML theme parser (M5.7a, K58–K63):

- K58 Getters are properties with the same Java names: `XmlRenderTheme` `menuCallback`,
  `relativePathPrefix`, `resourceProvider` (`getRenderThemeAsStream()` stays a function, as it
  opens a new stream); `XmlRenderThemeStyleMenu` `id`, `defaultLanguage`, `defaultValue`, `layers`;
  `XmlRenderThemeStyleLayer` `id`, `categories`, `overlays`, `titles`, `isEnabled`, `isVisible`.
  The collections stay mutable, as upstream; the menu classes keep their field names and
  `serialVersionUID`, so their serialized form is unchanged. A Kotlin `XmlRenderTheme` implementer
  overrides properties, and a Kotlin caller assigns them: Locus' `theme.setMenuCallback { … }` in
  `PrefMapVectorV4Theme` becomes `theme.menuCallback = XmlRenderThemeMenuCallback { … }`, and
  LoMaps' `Themes.InternalRenderTheme` (asamm/lomaps-mapsforge) turns its 5 overrides into
  `override var`/`val` when rebuilt against this API; its prebuilt jar linked until K64 renamed the
  packages, now it must be rebuilt.
- K59 `XmlRenderThemeMenuCallback` and `XmlThemeResourceProvider` are `fun interface`s, so Kotlin
  lambdas still convert.
- K60 Statics: `XmlUtils` is an object (K10) and `RenderThemeHandler`'s statics sit in its
  companion, both `@JvmStatic`; `XmlUtils.PREFIX_FILE` is a constant. `RenderThemeHandler`'s
  `processRenderTheme` is private: upstream it was public on a class without a public
  constructor.
- K61 Declared nullability: nullable are the menu's and layers' `id`, the menu's
  `defaultLanguage` and `defaultValue` (a theme may omit them; upstream stored null), `getLayer`'s
  argument and result, `layers`' keys, `getTitle`, `relativePathPrefix`, `menuCallback`,
  `resourceProvider`, the provider's `relativePath` and stream, and the menu callback's result;
  its menu is non-null.
  Layer `categories` and `titles` hold no null: a `<cat>` without `id` and a `<name>` without
  `lang` or `value` are skipped, which changes no render (a null category is never looked up). A
  `<name>` without `value` changes no title either (`getTitle` fell back already); one without
  `lang` is lost, where upstream kept it under a null key (both attributes are required by the
  XSD). `RenderThemeFuture` takes a non-null graphic factory, theme and display model and throws
  at entry, where
  upstream's future returned a null theme; its `get()` is non-null. In Locus,
  `PrefMapVectorV4Theme` (`id`, which no longer smart-casts across modules, `defaultValue`,
  `getLayer(..)`, `overlay.id` as a map key) and `MapsForgeV4ThemesFragment` (`defaultLanguage`,
  `getLayer(key)`, `layer.id`) need `?:`, `!!` or a local `val` at adoption.
- K62 Package-private becomes `internal` (K6): the `RenderThemeBuilder` and `RuleBuilder` fields
  (with C4's `RenderThemeBuilder.displayModel`) and the `XmlRenderThemeStyleLayer` constructor.
  Classes stay open, members and overrides final (K13) except `equals` and `hashCode`;
  `RenderThemeHandler` stays final.
- K63 Dropped as dead (K7, K30): the throws after exhaustive enum switches in `RuleBuilder` and
  `RenderThemeHandler`, the `XmlUtils` constructor that threw, the null-file branches of
  `ExternalRenderTheme.equals` and `hashCode`, the `PREFIX_JAR_V1` prefix and its branch (`jar:`
  matches first) and the commented-out `RuleOptimizer` calls; `RuleBuilder`'s rule stack stays an
  unused, non-null parameter (upstream accepted null).

Packages and modules (M5.7b, K64):

- K64 Every package `org.mapsforge.X` is `com.asamm.vectormaps.mapsforge.X`, and the five upstream
  modules are three: `formats/mapsforge/engine` (`mapsforge-core`, `-map`, `-map-reader`),
  `formats/mapsforge/android` (`-map-android`, namespace
  `com.asamm.vectormaps.mapsforge.map.android`) and `formats/mapsforge/awt` (`-map-awt`);
  `internal` members now reach across the merged engine, and logger names follow the packages.
  Locus swaps its `org.mapsforge.` imports at adoption (locus-core, LoPoints and lomaps-mapsforge's
  `Themes`). The golden harness keeps upstream's imports; `dev/mapsforge/golden` and
  `golden-android` compile a copy with the prefix swapped.

Structure (M5.1):

- K65 Repeated upstream bodies share private helpers: the four `QueryCalculations` second-level
  bitmasks (`secondLevel`), `RequiredFields.readPoiTags` and `readWayTags` (`readTags`), the two
  `ReadBuffer` growth blocks (`ensureCapacity`) and the three `MapFile` constructors (`Opened` and
  the `open` overloads); behaviour unchanged.

## Code deviations

| ID | Where | What | Why |
|---|---|---|---|
| C1 | reader `IndexCache` | the LRU map is read and written under its lock | upstream races on it from concurrent reads (M5.1) |
| C2 | reader `IndexCache`, `ReadBuffer` | positional reads until full, no seek under a lock | concurrent block reads no longer queue in the reader; on Windows the JDK still serialises them (M5.1) |
| C3 | reader `ReadBuffer`, `MapFile`, `IndexCache` | overflow-safe string length and index block size; a missing debug signature handled like a wrong one | an overflowing string or index size no longer throws; other malformed data still fails the tile (M5.1) |
| C4 | `DisplayModel`, theme `RenderTheme`, `RenderThemeBuilder`, `RenderInstruction` | device, line, symbol and text scales per model; the theme reads them from its model; the statics seed new models | a static set by one host (`AndroidGraphicFactory` sets the density) no longer changes other renderers (M5.2, D28) |
| C5 | `layer/hills` (15 files), `HillshadingContainer`, `Hillshading`; `CanvasRasterer`, `ShapeType`, `RenderTheme`, `RenderThemeHandler`, `DatabaseRenderer`, `StandardRenderer` | the 17 files removed; `DatabaseRenderer`, `StandardRenderer` take the hills config as an ignored `Object` (`Any?` in Kotlin); `ShapeType.HILLSHADING`, `CanvasRasterer.drawHillshading`, `RenderTheme.matchHillShadings`, `StandardRenderer.hillsRenderConfig` and the `RenderThemeHandler.HILLSHADING_*` statics removed; `interruptAndDestroy()` is a no-op; `RenderThemeHandler` still validates `<hillshading>` and gives it its level, then drops it (C12 supersedes the removal of `Hillshading`, `HillshadingContainer`, `ShapeType.HILLSHADING`, `CanvasRasterer.drawHillshading`, `RenderTheme.matchHillShadings`, `StandardRenderer.hillsRenderConfig` and the ignored hills parameter) | hillshading is out of scope and unused by any consumer; the constructor keeps the upstream arity for the golden harness shared with upstream (M5.4a, D30) |
| C6 | `DisplayModel`, `StandardRenderer`, `CanvasRasterer`, theme `Area`; graphics `GraphicContext`, `GraphicFactory`, `HillshadingBitmap`, the Android and AWT canvases, factories and hillshading bitmaps | `NUMBER_OF_THREADS`, `POLYGON_ZOOM_MIN`, `POLYGON_EXCEPTIONS` are per model (`numberOfThreads`, `polygonZoomMin`, `polygonExceptions`, the last two in `equals`/`hashCode`), seeded from `Parameters`; `shadeBitmap`, `createMonoBitmap`, `HillshadingBitmap` and their implementations removed (the 3 files and their state), superseding their mentions in K23, K27 and K29; `Area` reads the thread count from the job's model, as `CanvasRasterer` does; AWT `drawBitmap` still clears palette images | a static set by one host no longer changes other renderers (D28); hillshading is drawn by the host at the `HILLSHADE` anchor (D13, M6.2), not by Mapsforge (M5.4 fixes) |
| C7 | theme `RenderTheme`, `Rule`, `RenderContext`, `RenderInstruction` and the 6 instructions with scaled values; `DatabaseRenderer`, `CanvasRasterer`, `DisplayModel`; graphics `Paint`, `AndroidPaint`, `AwtPaint` | the matching caches key on the tags (without `name`), zoom level and closed state compared in full and ordered, not on their hash, and skip keys with more than 256 characters of tags; scaled Paints are kept for the 4 most recently used zoom levels plus those a job holds (`acquireZoom`/`releaseZoom`, called by `DatabaseRenderer`); the theme drops its stroke and text scales and each instruction its scaled values in `evictZoom`, and `Caption` falls back to its unscaled offset as its callers do; `DisplayModel.antiAliasing` and `symbolScaling` (both in `equals`/`hashCode`) seeded from `Parameters`, `RenderInstruction.createBitmap` reads `symbolScaling` from the model, theme Paints take the model's anti-aliasing through the new `Paint.setAntiAlias` (a no-op on AWT), `CanvasRasterer` sets the canvas from it (on Android the canvas' bitmap Paint, which upstream kept on) | tag lists with the same hash shared their instructions; the per-zoom Paint caches never shrank (locus-core F1–F3); a static set by one host no longer changes other renderers (M5.6 fixes, D28, D29) |
| C8 | AWT `AwtGraphicFactory`, `AwtPaint` | `AwtGraphicFactory.withFonts` gives a factory whose paints draw the listed font families in a given font, at the paint's size and style; `INSTANCE` keeps the logical fonts | the label goldens draw text in the bundled DejaVu Sans, so they hold on every system (M6.0b, D37) |
| C9 | theme `RenderInstruction`, `Line` | `computeBoundary` offsets a symbol's top by its height (A8); a `<line>` with `scale="all"` scales its dashes only when it has a `stroke-dasharray` (A9) | upstream offsets by the width, which moves non-square symbols, their clash box and the captions placed against them (`symbol-id`), and throws on such a line (M6.4) |
| C10 | `MapElementContainer`, `WayTextContainer`; new Asamm `map/layer/labels/LabelShapes` | `MapElementContainer.xyInternal` reads the anchor; `WayTextContainer` keeps `textHeight` and makes its line, text, paints and orientation `internal`; `LabelShapes.of` describes a stored container for a label placer (kind, anchor, priority, display, collision boxes, text, path) | the Mapsforge driver's label pass (D39) turns the label store into core `LabelCandidate`s; the engine cannot depend on `:core` (Java 8), and the containers keep their public API (M6.1b) |
| C11 | AWT `AwtCanvas` | `rotate(degrees, px, py)` hands `Graphics2D` radians (A10) | upstream passes the degrees to `Graphics2D.rotate`, which takes radians, so a turned AWT canvas turns by the wrong angle; no tile drawing calls it, the label pass does (M6.1b) |
| C12 | theme `RenderThemeHandler`, `RenderTheme`; `DatabaseRenderer`, `StandardRenderer`, `CanvasRasterer`, `ShapeType`; graphics `Canvas`, the Android and AWT canvases; `Hillshading`, `HillshadingContainer` restored reduced; new Asamm `map/layer/renderer/HillshadingDrawer` | `<hillshading>` places the host's hillshading again: the handler keeps the element's `layer`, `cat` and level (zoom, magnitude and color are only validated), `RenderTheme.matchHillShadings` puts a `HILLSHADING` container there and `CanvasRasterer.drawHillshading` calls the host's `HillshadingDrawer` on the canvas; a theme without the element gets it after all ways; the renderers take a `HillshadingDrawer?` where upstream takes a `HillsRenderConfig`; `Canvas.withPlatformCanvas` runs host code on the Android `Canvas` (saved and restored) or a `Graphics2D` copy; API differences: `matchHillShadings` takes the containers' unused `Paint` (upstream passes null), `drawHillshading(drawer)` is public, `StandardRenderer.hillsRenderConfig` is protected (upstream public), `RenderTheme.declaresHillshading` is new | the host draws hillshading (D13) where the theme puts it, else after all features (D16, M6.2); without a drawer nothing changes, as upstream without a `HillsRenderConfig` |

## Build deviations

No Java source is left (M5.7a). `engine` (upstream `core`, `map`, `map-reader`, merged in M5.7b)
and `awt` are Kotlin and keep the Java 8 target and UTF-8 (B6); `android` is Kotlin from M5.3b.

| ID | What | Upstream | Here |
|---|---|---|---|
| B1 | Bytecode level, `android` (`map-android`) | `--release 8` | Java 17, Kotlin from M5.3b; D8 at min-api 24 rewrites `ByteBuffer.clear()` (verified) |
| B2 | `android` packaging | plain JAR against `android.jar` | Android library (AAR), minSdk 24 in its manifest |
| B3 | Kotlin stdlib | none | `kotlin-stdlib` on every vendored module: through `engine` (B6) and AGP's built-in Kotlin in `android` |
| B4 | Source encoding | `UTF-8` set explicitly | resolved: JVM modules set UTF-8, `android` gets it as AGP's default |
| B5 | Line endings | CRLF in the upstream tree | LF via `.gitattributes` |
| B6 | `engine` and `awt` language | Java, `--release 8` | Kotlin, `jvmTarget` 1.8 and `-Xjdk-release=1.8`; a Java source would build with `--release 8`; depends on `kotlin-stdlib` |
