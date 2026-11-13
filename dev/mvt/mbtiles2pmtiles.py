# Copyright 2026 Asamm Software
# SPDX-License-Identifier: Apache-2.0
"""Converts an MBTiles file of vector tiles to PMTiles v3, for local tests on real data.

Written from the PMTiles v3 spec (github.com/protomaps/PMTiles, spec/v3) and the MBTiles 1.3 spec,
apart from the Kotlin reader it feeds. Tiles keep their bytes (gzip or not); identical tiles share
their data, consecutive ones as runs; entries go to leaf directories once the root outgrows its
16 KB, as the reference writer does. Large files belong in the git-ignored `local/`:

    python dev/mvt/mbtiles2pmtiles.py local/mvt/czech_republic.mbtiles local/mvt/czech_republic.pmtiles
"""
import gzip
import hashlib
import io
import json
import os
import sqlite3
import struct
import sys

from pmtiles import tile_id, varint

HEADER_SIZE = 127

# the spec keeps the header and root directory in the first 16 KB
ROOT_SPACE = 16384


def gzipped(data):
    buffer = io.BytesIO()
    with gzip.GzipFile(fileobj=buffer, mode='wb', compresslevel=6, mtime=0) as f:
        f.write(data)
    return buffer.getvalue()


def directory(entries):
    # columns: count, id deltas, run lengths, lengths, offsets + 1 (0 would mean "continues")
    out = bytearray(varint(len(entries)))
    last = 0
    for tid, _, _, _ in entries:
        out += varint(tid - last)
        last = tid
    for _, _, _, run in entries:
        out += varint(run)
    for _, _, length, _ in entries:
        out += varint(length)
    for _, offset, _, _ in entries:
        out += varint(offset + 1)
    return gzipped(bytes(out))


def directories(entries):
    root = directory(entries)
    if len(root) <= ROOT_SPACE - HEADER_SIZE:
        return root, b''
    leaf_size = 4096
    while True:
        leaves = bytearray()
        root_entries = []
        for start in range(0, len(entries), leaf_size):
            chunk = entries[start:start + leaf_size]
            leaf = directory(chunk)
            # a run length of 0 points to a leaf directory
            root_entries.append((chunk[0][0], len(leaves), len(leaf), 0))
            leaves += leaf
        root = directory(root_entries)
        if len(root) <= ROOT_SPACE - HEADER_SIZE:
            return root, bytes(leaves)
        leaf_size = int(leaf_size * 1.2)


def main(source, target):
    db = sqlite3.connect(f'file:{source}?mode=ro', uri=True)
    meta = dict(db.execute('SELECT name, value FROM metadata'))
    keys = []
    for z, x, row in db.execute('SELECT zoom_level, tile_column, tile_row FROM tiles'):
        # MBTiles counts rows from the south
        y = (1 << z) - 1 - row
        keys.append((tile_id(z, x, y), z, x, row))
    keys.sort()
    print(source, len(keys), 'tiles')

    entries = []
    offsets = {}
    data_path = target + '.data'
    with open(data_path, 'wb') as data:
        length = 0
        # the header names one compression for all tiles
        compressions = set()
        for i, (tid, z, x, row) in enumerate(keys):
            blob = db.execute(
                'SELECT tile_data FROM tiles WHERE zoom_level = ? AND tile_column = ? AND tile_row = ?',
                (z, x, row),
            ).fetchone()[0]
            compressions.add(blob[:2] == b'\x1f\x8b')
            if len(compressions) > 1:
                sys.exit(f'{source}: gzip and plain tiles mixed')
            digest = hashlib.sha1(blob).digest()
            offset = offsets.get(digest)
            if offset is None:
                offset = length
                offsets[digest] = offset
                data.write(blob)
                length += len(blob)
            last = entries[-1] if entries else None
            # the same bytes on the next tile id lengthen the previous entry's run
            if last is not None and last[1] == offset and last[0] + last[3] == tid:
                entries[-1] = (last[0], last[1], last[2], last[3] + 1)
            else:
                entries.append((tid, offset, len(blob), 1))
            if i % 100000 == 0:
                print(' ', i, 'tiles written')
    db.close()

    gzip_tiles = compressions == {True}
    root, leaves = directories(entries)
    # PMTiles keeps vector_layers at the top level, MBTiles inside its `json` row
    meta.update(json.loads(meta.pop('json', '{}')))
    metadata = gzipped(json.dumps(meta).encode())
    root_offset = HEADER_SIZE
    metadata_offset = root_offset + len(root)
    leaf_offset = metadata_offset + len(metadata)
    data_offset = leaf_offset + len(leaves)
    zooms = [z for _, z, _, _ in keys]
    west, south, east, north = (float(v) for v in meta.get('bounds', '-180,-85.0511,180,85.0511').split(','))
    header = b'PMTiles' + bytes([3])
    header += struct.pack('<QQQQQQQQ', root_offset, len(root), metadata_offset, len(metadata),
                          leaf_offset, len(leaves), data_offset, length)
    header += struct.pack('<QQQ', len(keys), len(entries), len(offsets))
    # clustered, gzip directories, tiles as stored, vector tiles
    header += bytes([1, 2, 2 if gzip_tiles else 1, 1, min(zooms), max(zooms)])
    header += struct.pack('<iiii', round(west * 1e7), round(south * 1e7), round(east * 1e7), round(north * 1e7))
    header += bytes([min(max(zooms), 10)]) + struct.pack('<ii', round((west + east) / 2 * 1e7), round((south + north) / 2 * 1e7))
    assert len(header) == HEADER_SIZE, len(header)
    with open(target, 'wb') as out:
        out.write(header + root + metadata + leaves)
        with open(data_path, 'rb') as data:
            while True:
                chunk = data.read(1 << 20)
                if not chunk:
                    break
                out.write(chunk)
    os.remove(data_path)
    print(target, len(entries), 'entries,', len(offsets), 'contents,', len(leaves) // 1024, 'KB of leaves,',
          os.path.getsize(target) // (1 << 20), 'MB')


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
