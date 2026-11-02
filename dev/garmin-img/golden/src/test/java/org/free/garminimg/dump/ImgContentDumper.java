/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
/*
 * Reference dumper.
 *
 * Walks an IMG via the legacy libGarminImg parser (the "ground truth" for
 * the Kotlin rewrite) and emits a deterministic textual record of every
 * parsed map object. Sister utility: the Kotlin `ImgContentDumper` in
 * `com.asamm.vectormaps.garminimg.dump`.
 *
 * Diffing the two outputs surfaces parser-level discrepancies — anything
 * that doesn't match between Java and Kotlin is a regression in the
 * rewrite that needs investigating before any rendering work.
 *
 * Output format (one record per line, sorted before diffing):
 *   PT level=L type=0xNN/0xNN lon=I lat=I label="…"
 *   LN level=L type=0xNN verts=K bbox=W,S,E,N first=I,I last=I,I label="…"
 *   PG level=L type=0xNN verts=K bbox=W,S,E,N first=I,I last=I,I label="…"
 * Coordinates are raw Garmin-fixed-point ints (no WGS84 conversion) so
 * decoder-level discrepancies surface bit-for-bit.
 */
package org.free.garminimg.dump;

import org.free.garminimg.ImgContext;
import org.free.garminimg.ImgFileBag;
import org.free.garminimg.ImgFilesBag;
import org.free.garminimg.ImgSubFile;
import org.free.garminimg.Label;
import org.free.garminimg.MapListener;
import org.free.garminimg.ObjectKind;
import org.free.garminimg.POILabel;
import org.free.garminimg.SubDivision;
import org.free.garminimg.SubFileReader;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ImgContentDumper {

    private ImgContentDumper() {
    }

    /**
     * Dumps an IMG to a sorted text file. Returns the output path.
     *
     * @param imgPath absolute path to the .img file
     * @param outPath absolute path where the dump should be written
     */
    public static String dump(String imgPath, String outPath) throws IOException {
        File img = new File(imgPath);
        if (!img.exists()) {
            throw new IOException("IMG file not found: " + imgPath);
        }

        ImgFilesBag bag = new ImgFilesBag(new ImgContext(1.0));
        // SubFileReader is the legacy parser's pluggable factory for
        // mapping FAT entries to typed sub-file readers; the default
        // delegates to ImgSubFile.create. Inline as an anonymous
        // subclass so we don't need a one-line file in `locus/`.
        bag.addFile(img, true, new SubFileReader() {
            @Override
            public ImgSubFile parse(String filename, String filetype, int fileSize,
                                    int blocSize, int offset, ImgFileBag imgFileBag) {
                return ImgSubFile.create(filename, filetype, fileSize, blocSize, offset, imgFileBag);
            }
        });

        // Read everything in the IMG's TRE bounding box at full detail
        // (resolution = -1 = pull every level the IMG ships).
        int north = bag.getMaxLatitude();
        int south = bag.getMinLatitude();
        int east = bag.getMaxLongitude();
        int west = bag.getMinLongitude();

        DumpListener listener = new DumpListener();
        bag.readMapForDrawing(west, east, south, north, -1,
                ObjectKind.ALL, listener);

        // Sort lines so byte-for-byte diff with the Kotlin dumper isn't
        // sensitive to traversal order between the two parsers.
        Collections.sort(listener.lines);

        File out = new File(outPath);
        File parent = out.getParentFile();
        if (parent != null) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
        try (BufferedWriter w = Files.newBufferedWriter(
                Paths.get(outPath), StandardCharsets.UTF_8)) {
            w.write("# img=" + img.getName());
            w.newLine();
            w.write("# size=" + img.length());
            w.newLine();
            w.write("# tre_bbox lon=" + west + ".." + east + " lat=" + south + ".." + north);
            w.newLine();
            w.write("# total_records=" + listener.lines.size());
            w.newLine();
            for (String line : listener.lines) {
                w.write(line);
                w.newLine();
            }
        }
        return outPath;
    }

    /**
     * Convenience entry-point for `gradle run` or a direct
     * `java -cp …  ImgContentDumper <imgPath> [outPath]`.
     */
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("usage: ImgContentDumper <img-path> [out-path]");
            System.exit(1);
        }
        String imgPath = args[0];
        String outPath = args.length > 1
                ? args[1]
                : imgPath + ".dump.txt";
        String written = dump(imgPath, outPath);
        PrintStream out = System.out;
        out.println("[dump-old] wrote " + written);
    }

    private static final class DumpListener implements MapListener {

        final List<String> lines = new ArrayList<>(50_000);
        // Captured from the latest startSubDivision callback — every
        // subsequent addPoint / addPoly belongs to this sub-division.
        int currentLevel = -1;

        @Override
        public void startSubDivision(SubDivision subDivision) {
            currentLevel = subDivision.getLevel();
        }

        @Override
        public void addPoint(int type, int subType, int longitude, int latitude,
                             Label label, boolean indexed) {
            StringBuilder sb = new StringBuilder(96);
            sb.append("PT");
            sb.append(" L=").append(currentLevel);
            sb.append(" type=0x").append(hex(type));
            sb.append("/0x").append(hex(subType));
            sb.append(" lon=").append(longitude);
            sb.append(" lat=").append(latitude);
            sb.append(" indexed=").append(indexed ? 1 : 0);
            appendLabel(sb, label);
            // POI metadata block — same fields the Kotlin dumper emits
            // so the two outputs can diff.
            if (label instanceof POILabel) {
                POILabel poi = (POILabel) label;
                try {
                    appendKv(sb, "street_num", poi.getStreetNumber());
                    appendKv(sb, "street", poi.getStreet());
                    appendKv(sb, "city", poi.getCity());
                    appendKv(sb, "zip", poi.getZip());
                    appendKv(sb, "phone", poi.getPhone());
                } catch (IOException ignored) {
                    // POI block decode failure — still emit the rest of
                    // the record so a single bad POI doesn't desync the
                    // diff.
                }
            }
            lines.add(sb.toString());
        }

        private static void appendKv(StringBuilder sb, String key, String value) {
            if (value == null || value.isEmpty()) {
                return;
            }
            sb.append(' ').append(key).append("=\"");
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c == '"' || c == '\\') {
                    sb.append('\\').append(c);
                } else if (c < 0x20) {
                    sb.append(String.format("\\u%04X", (int) c));
                } else {
                    sb.append(c);
                }
            }
            sb.append('"');
        }

        @Override
        public void addPoly(int type, int[] longitudes, int[] latitudes,
                            int nbPoints, Label label, boolean line) {
            StringBuilder sb = new StringBuilder(128);
            sb.append(line ? "LN" : "PG");
            sb.append(" L=").append(currentLevel);
            sb.append(" type=0x").append(hex(type));
            sb.append(" verts=").append(nbPoints);
            int minLon = Integer.MAX_VALUE;
            int maxLon = Integer.MIN_VALUE;
            int minLat = Integer.MAX_VALUE;
            int maxLat = Integer.MIN_VALUE;
            for (int i = 0; i < nbPoints; i++) {
                int x = longitudes[i];
                int y = latitudes[i];
                if (x < minLon) minLon = x;
                if (x > maxLon) maxLon = x;
                if (y < minLat) minLat = y;
                if (y > maxLat) maxLat = y;
            }
            sb.append(" bbox=").append(minLon).append(',').append(minLat)
                    .append(',').append(maxLon).append(',').append(maxLat);
            sb.append(" first=").append(longitudes[0]).append(',').append(latitudes[0]);
            sb.append(" last=").append(longitudes[nbPoints - 1]).append(',')
                    .append(latitudes[nbPoints - 1]);
            appendLabel(sb, label);
            lines.add(sb.toString());
        }

        @Override
        public void startMap(ImgFileBag file) {
        }

        @Override
        public void finishPainting() {
        }

        private static void appendLabel(StringBuilder sb, Label label) {
            sb.append(" label=\"");
            if (label != null) {
                String text = label.getNameNoException();
                if (text != null) {
                    for (int i = 0; i < text.length(); i++) {
                        char c = text.charAt(i);
                        if (c == '"' || c == '\\') {
                            sb.append('\\').append(c);
                        } else if (c < 0x20) {
                            sb.append(String.format("\\u%04X", (int) c));
                        } else {
                            sb.append(c);
                        }
                    }
                }
            }
            sb.append('"');
        }

        private static String hex(int v) {
            return Integer.toHexString(v).toUpperCase();
        }
    }
}
