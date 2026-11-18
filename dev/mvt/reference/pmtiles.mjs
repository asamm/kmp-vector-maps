// Copyright 2026 Asamm Software
// SPDX-License-Identifier: Apache-2.0
//
// A PMTiles v3 reader for the parity sweep (sweep.mjs, sweep-native.mjs), written from the spec
// (github.com/protomaps/PMTiles, spec/v3), apart from the Kotlin reader it checks.
import { closeSync, openSync, readSync } from 'node:fs';
import { gunzipSync } from 'node:zlib';

export class PmTiles {

  constructor(path) {
    this.fd = openSync(path, 'r');
    const h = this.read(0, 127);
    if (h.toString('ascii', 0, 7) !== 'PMTiles' || h[7] !== 3) {
      throw new Error(`${path}: not PMTiles v3`);
    }
    const u64 = o => Number(h.readBigUInt64LE(o));
    this.rootOffset = u64(8);
    this.rootLength = u64(16);
    this.leafOffset = u64(40);
    this.dataOffset = u64(56);
    this.internalGzip = h[97] === 2;
    this.tileGzip = h[98] === 2;
    this.maxZoom = h[101];
    this.directories = new Map();
  }

  read(offset, length) {
    const buffer = Buffer.alloc(length);
    let at = 0;
    while (at < length) {
      const n = readSync(this.fd, buffer, at, length - at, offset + at);
      if (n <= 0) {
        throw new Error(`read past the end at ${offset + at}`);
      }
      at += n;
    }
    return buffer;
  }

  directory(offset, length) {
    let dir = this.directories.get(offset);
    if (!dir) {
      let bytes = this.read(offset, length);
      if (this.internalGzip) {
        bytes = gunzipSync(bytes);
      }
      dir = parseDirectory(bytes);
      if (this.directories.size > 256) {
        this.directories.clear();
      }
      this.directories.set(offset, dir);
    }
    return dir;
  }

  // the tile's bytes, inflated; null when the file has none
  tile(z, x, y) {
    const id = tileId(z, x, y);
    let offset = this.rootOffset;
    let length = this.rootLength;
    for (let depth = 0; depth < 4; depth++) {
      const dir = this.directory(offset, length);
      const i = find(dir, id);
      if (i < 0) {
        return null;
      }
      if (dir.runs[i] > 0) {
        const bytes = this.read(this.dataOffset + dir.offsets[i], dir.lengths[i]);
        return this.tileGzip ? gunzipSync(bytes) : bytes;
      }
      offset = this.leafOffset + dir.offsets[i];
      length = dir.lengths[i];
    }
    return null;
  }

  // every tile entry: its first tile id, run length, stored length and data offset
  *entries() {
    const walk = function* (pm, offset, length) {
      const dir = pm.directory(offset, length);
      for (let i = 0; i < dir.ids.length; i++) {
        if (dir.runs[i] > 0) {
          yield { id: dir.ids[i], run: dir.runs[i], length: dir.lengths[i], offset: dir.offsets[i] };
        } else {
          yield* walk(pm, pm.leafOffset + dir.offsets[i], dir.lengths[i]);
        }
      }
    };
    yield* walk(this, this.rootOffset, this.rootLength);
  }

  close() {
    closeSync(this.fd);
  }
}

function parseDirectory(bytes) {
  let at = 0;
  const varint = () => {
    let value = 0;
    let scale = 1;
    for (;;) {
      const b = bytes[at++];
      value += (b & 0x7f) * scale;
      if (b < 0x80) {
        return value;
      }
      scale *= 128;
    }
  };
  const count = varint();
  const ids = new Array(count);
  let last = 0;
  for (let i = 0; i < count; i++) {
    last += varint();
    ids[i] = last;
  }
  const runs = Array.from({ length: count }, varint);
  const lengths = Array.from({ length: count }, varint);
  const offsets = new Array(count);
  for (let i = 0; i < count; i++) {
    const v = varint();
    offsets[i] = v === 0 && i > 0 ? offsets[i - 1] + lengths[i - 1] : v - 1;
  }
  return { ids, runs, lengths, offsets };
}

function find(dir, id) {
  let low = 0;
  let high = dir.ids.length - 1;
  while (low <= high) {
    const mid = (low + high) >> 1;
    if (dir.ids[mid] === id) {
      return mid;
    }
    if (dir.ids[mid] < id) {
      low = mid + 1;
    } else {
      high = mid - 1;
    }
  }
  if (high < 0) {
    return -1;
  }
  if (dir.runs[high] === 0 || id - dir.ids[high] < dir.runs[high]) {
    return high;
  }
  return -1;
}

function zoomStart(z) {
  return (Math.pow(4, z) - 1) / 3;
}

export function tileId(z, x, y) {
  let d = 0;
  for (let s = Math.pow(2, z) / 2; s >= 1; s /= 2) {
    const rx = (x & s) > 0 ? 1 : 0;
    const ry = (y & s) > 0 ? 1 : 0;
    d += s * s * ((3 * rx) ^ ry);
    if (ry === 0) {
      if (rx === 1) {
        x = s - 1 - x;
        y = s - 1 - y;
      }
      [x, y] = [y, x];
    }
  }
  return zoomStart(z) + d;
}

export function zxy(id) {
  let z = 0;
  while (zoomStart(z + 1) <= id) {
    z++;
  }
  let t = id - zoomStart(z);
  const n = Math.pow(2, z);
  let x = 0;
  let y = 0;
  for (let s = 1; s < n; s *= 2) {
    const rx = 1 & Math.floor(t / 2);
    const ry = 1 & (t ^ rx);
    if (ry === 0) {
      if (rx === 1) {
        x = s - 1 - x;
        y = s - 1 - y;
      }
      [x, y] = [y, x];
    }
    x += s * rx;
    y += s * ry;
    t = Math.floor(t / 4);
  }
  return [z, x, y];
}

