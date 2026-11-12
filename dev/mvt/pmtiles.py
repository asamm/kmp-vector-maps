# Copyright 2026 Asamm Software
# SPDX-License-Identifier: Apache-2.0
"""Writes a PMTiles v3 file from a z/x/y.pbf folder, as the MVT driver's fixture.

Written from the PMTiles v3 spec (github.com/protomaps/PMTiles, spec/v3), apart from the Kotlin
reader it tests: gzip tiles, a gzip root directory holding every entry, no leaf directories.

    python dev/mvt/pmtiles.py dev/fixtures/mvt/tiles/planet dev/fixtures/mvt/tiles/fixtures.pmtiles
"""
import gzip
import io
import math
import os
import struct
import sys


def tile_id(z, x, y):
    # the tiles of every lower zoom, then the position on the zoom's Hilbert curve
    acc = sum(4 ** i for i in range(z))
    n = 2 ** z
    d = 0
    s = n // 2
    while s > 0:
        rx = 1 if (x & s) > 0 else 0
        ry = 1 if (y & s) > 0 else 0
        d += s * s * ((3 * rx) ^ ry)
        if ry == 0:
            if rx == 1:
                x = s - 1 - x
                y = s - 1 - y
            x, y = y, x
        s //= 2
    return acc + d


def varint(value):
    out = bytearray()
    while True:
        byte = value & 0x7f
        value >>= 7
        if value:
            out.append(byte | 0x80)
        else:
            out.append(byte)
            return bytes(out)


def gzipped(data):
    buffer = io.BytesIO()
    # a fixed time and level keep the file the same on every run of one Python and zlib build
    with gzip.GzipFile(fileobj=buffer, mode='wb', compresslevel=9, mtime=0) as f:
        f.write(data)
    return buffer.getvalue()


def lon_lat(z, x, y):
    n = 2 ** z
    lon = x / n * 360 - 180
    lat = math.degrees(math.atan(math.sinh(math.pi * (1 - 2 * y / n))))
    return lon, lat


def main(folder, target):
    tiles = []
    for z in os.listdir(folder):
        for x in os.listdir(os.path.join(folder, z)):
            for name in os.listdir(os.path.join(folder, z, x)):
                if name.endswith('.pbf'):
                    with open(os.path.join(folder, z, x, name), 'rb') as f:
                        data = gzipped(f.read())
                    tiles.append((tile_id(int(z), int(x), int(name[:-4])), int(z), int(x), int(name[:-4]), data))
    tiles.sort()
    blob = bytearray()
    entries = []
    for tid, z, x, y, data in tiles:
        entries.append((tid, len(blob), len(data), 1))
        blob += data
    # columns: count, id deltas, run lengths, lengths, offsets + 1 (0 would mean "continues")
    directory = varint(len(entries))
    last = 0
    for tid, _, _, _ in entries:
        directory += varint(tid - last)
        last = tid
    for _, _, _, run in entries:
        directory += varint(run)
    for _, _, length, _ in entries:
        directory += varint(length)
    for _, offset, _, _ in entries:
        directory += varint(offset + 1)
    root = gzipped(bytes(directory))
    metadata = gzipped(b'{"name":"kmp-vector-maps fixtures"}')
    root_offset = 127
    metadata_offset = root_offset + len(root)
    data_offset = metadata_offset + len(metadata)
    corners = [lon_lat(z, x, y) for _, z, x, y, _ in tiles] + [lon_lat(z, x + 1, y + 1) for _, z, x, y, _ in tiles]
    min_lon = min(c[0] for c in corners)
    max_lon = max(c[0] for c in corners)
    min_lat = min(c[1] for c in corners)
    max_lat = max(c[1] for c in corners)
    zooms = [z for _, z, _, _, _ in tiles]
    header = b'PMTiles' + bytes([3])
    header += struct.pack('<QQQQQQQQ', root_offset, len(root), metadata_offset, len(metadata),
                          data_offset + len(blob), 0, data_offset, len(blob))
    header += struct.pack('<QQQ', len(tiles), len(entries), len(tiles))
    # clustered, gzip directories, gzip tiles, vector tiles
    header += bytes([1, 2, 2, 1, min(zooms), max(zooms)])
    header += struct.pack('<iiii', round(min_lon * 1e7), round(min_lat * 1e7), round(max_lon * 1e7), round(max_lat * 1e7))
    header += bytes([min(zooms)]) + struct.pack('<ii', round((min_lon + max_lon) / 2 * 1e7), round((min_lat + max_lat) / 2 * 1e7))
    assert len(header) == 127, len(header)
    with open(target, 'wb') as f:
        f.write(header + root + metadata + bytes(blob))
    print(target, len(tiles), 'tiles')


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
