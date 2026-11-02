/*
 * JGarminImgParser - test-only minimal copy.
 *
 * Original ImgConstants (in libGarminImg) bundles parser-side type IDs
 * with a large render-side static block (Android Paints, Bitmap caches,
 * StyleInternal). For the dump-diff regression test we only need the
 * type-ID constants — the rest stays out so the Android SDK and the
 * Locus render package don't have to be on the test classpath.
 */
package org.free.garminimg.utils;

public class ImgConstants {

    // POINTS IDS
    public static final int ELEVATION = 0x6300;
    public static final int ELEVATION1 = 0x6616;

    // LINES IDS
    public static final int ID_LINE_PAVEMENT = 0x13;
    public static final int ID_LINE_RAILROAD = 0x14;
    public static final int ID_LINE_TRAIL = 0x16;

    // contours
    public static final int MINOR_LAND_CONTOUR = 0x20;
    public static final int INTERMEDIATE_LAND_CONTOUR = 0x21;
    public static final int MAJOR_LAND_CONTOUR = 0x22;
    public static final int MINOR_DEPTH_CONTOUR = 0x23;
    public static final int INTERMEDIATE_DEPTH_CONTOUR = 0x24;
    public static final int MAJOR_DEPTH_CONTOUR = 0x25;

    public static final int TUNNEL_SHIFT = 0x100;
    public static final int RUINS = 0x150;

    // POLYGONS IDS
    public static final int AIRPORT = 0x07;
    public static final int SHOPPING_CENTER = 0x08;
    public static final int DEFINITION_AREA = 0x4a;
    public static final int BACKGROUND = 0x4b;
    public static final int FOREST = 0x101;
    public static final int STATION_AREA = 0x102;
    public static final int GRAVEL_AREA = 0x103;
}
