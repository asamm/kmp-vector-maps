// Copyright 2026 Asamm Software
// SPDX-License-Identifier: Apache-2.0
//
// The MapLibre half of the parity sweep with MapLibre Native (@maplibre/maplibre-gl-native), the
// renderer of Locus Map iOS, in parallel processes and without a browser; much faster than
// sweep.mjs render. Needs Node 22 or 24, the versions the package ships Windows binaries for.
// Usage, from this folder:
//   node sweep-native.mjs <file.pmtiles> <tiles.txt> <runDir> <cacheDir> [processes]
// writes <runDir>/maplibre/<view>/<z>_<x>_<y>.png for the views base (no symbols), labels and
// labels_r30 (the map turned 30 degrees clockwise), and <runDir>/maplibre/failed.txt; glyphs
// downloaded once into <cacheDir>/glyphs.
import { fork } from 'node:child_process';
import { availableParallelism } from 'node:os';
import { existsSync, mkdirSync, readFileSync, renameSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { deflateSync } from 'node:zlib';
import { PmTiles } from './pmtiles.mjs';
import { MODES, tileCenter } from './sweep-views.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const styleDir = resolve(here, '../../fixtures/mvt/styles/hike_bike');

const SIZE = 512;

function style(hide) {
  const s = JSON.parse(readFileSync(join(styleDir, 'style.json'), 'utf8'));
  s.sprite = 'sweep://sprite/sprite';
  for (const source of Object.values(s.sources)) {
    if (source.type === 'vector') {
      delete source.url;
      source.tiles = ['sweep://tiles/{z}/{x}/{y}'];
      source.minzoom = 0;
      source.maxzoom = 14;
    }
  }
  s.layers = s.layers.filter(l => !hide.includes(l.type));
  // a source no layer reads would still be fetched, the elevation ones from the server
  const used = new Set(s.layers.map(l => l.source));
  s.sources = Object.fromEntries(Object.entries(s.sources).filter(([id]) => used.has(id)));
  return s;
}

// --- PNG, as MapLibre Native hands back premultiplied RGBA ---

const CRC = new Int32Array(256).map((_, n) => {
  let c = n;
  for (let k = 0; k < 8; k++) {
    c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  }
  return c;
});

function crc32(buffer) {
  let c = -1;
  for (const b of buffer) {
    c = CRC[(c ^ b) & 0xff] ^ (c >>> 8);
  }
  return (c ^ -1) >>> 0;
}

function chunk(type, data) {
  const out = Buffer.alloc(12 + data.length);
  out.writeUInt32BE(data.length, 0);
  out.write(type, 4, 'ascii');
  data.copy(out, 8);
  out.writeUInt32BE(crc32(out.subarray(4, 8 + data.length)), 8 + data.length);
  return out;
}

function png(rgba, width, height) {
  const raw = Buffer.alloc((width * 4 + 1) * height);
  for (let y = 0; y < height; y++) {
    const row = y * (width * 4 + 1);
    for (let x = 0; x < width; x++) {
      const i = (y * width + x) * 4;
      const a = rgba[i + 3];
      const o = row + 1 + x * 4;
      for (let c = 0; c < 3; c++) {
        raw[o + c] = a === 0 ? 0 : Math.min(255, Math.round(rgba[i + c] * 255 / a));
      }
      raw[o + 3] = a;
    }
  }
  const header = Buffer.alloc(13);
  header.writeUInt32BE(width, 0);
  header.writeUInt32BE(height, 4);
  header[8] = 8;
  header[9] = 6;
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', header),
    chunk('IDAT', deflateSync(raw, { level: 3 })),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

// written whole or not at all, as a killed run's files are kept and other workers read the glyphs
function writeWhole(file, data) {
  const part = `${file}.${process.pid}.part`;
  writeFileSync(part, data);
  try {
    renameSync(part, file);
  } catch (e) {
    // another worker wrote the same glyphs first
    rmSync(part, { force: true });
  }
}

// --- a worker process: its share of the tiles ---

async function work(file, list, runDir, cacheDir, index, count) {
  const mbgl = (await import('@maplibre/maplibre-gl-native')).default;
  const pm = new PmTiles(file);
  const glyphDir = join(cacheDir, 'glyphs');
  const request = (req, callback) => {
    const url = req.url;
    try {
      let m = url.match(/^sweep:\/\/tiles\/(\d+)\/(\d+)\/(\d+)$/);
      if (m) {
        const data = pm.tile(+m[1], +m[2], +m[3]);
        callback(null, data ? { data } : {});
        return;
      }
      m = url.match(/^sweep:\/\/sprite\/(.+)$/);
      if (m) {
        callback(null, { data: readFileSync(join(styleDir, m[1])) });
        return;
      }
      if (url.startsWith('https://tiles.locusmap.app/fonts/')) {
        const name = decodeURIComponent(url.substring('https://tiles.locusmap.app/fonts/'.length));
        const cached = join(glyphDir, name.replaceAll('/', '__'));
        if (existsSync(cached)) {
          callback(null, { data: readFileSync(cached) });
          return;
        }
        fetch(url, { headers: { Origin: 'https://web.locusmap.app' }, signal: AbortSignal.timeout(60000) })
          .then(async r => {
            if (!r.ok) {
              throw new Error(`glyphs ${r.status} ${url}`);
            }
            const data = Buffer.from(await r.arrayBuffer());
            mkdirSync(glyphDir, { recursive: true });
            writeWhole(cached, data);
            callback(null, { data });
          })
          .catch(e => callback(e));
        return;
      }
      callback(new Error(`not served: ${url}`));
    } catch (e) {
      callback(e);
    }
  };
  const maps = MODES.map(mode => {
    const map = new mbgl.Map({ request, ratio: 1, mode: 'static' });
    map.load(style(mode.hide));
    return { mode, map };
  });
  const tiles = readFileSync(list, 'utf8').split(/\r?\n/).filter(Boolean)
    .filter((_, i) => i % count === index)
    .map(t => t.split('/').map(Number));
  const failed = [];
  for (const [z, x, y] of tiles) {
    const [lon, lat] = tileCenter(z, x, y);
    for (const { mode, map } of maps) {
      for (const view of mode.views) {
        const file = join(runDir, 'maplibre', view.name, `${z}_${x}_${y}.png`);
        if (existsSync(file)) {
          continue;
        }
        try {
          const pixels = await new Promise((ok, fail) => {
            map.render({ zoom: z, center: [lon, lat], width: SIZE, height: SIZE, bearing: view.bearing }, (e, b) => {
              if (e) {
                fail(e);
              } else {
                ok(b);
              }
            });
          });
          writeWhole(file, png(pixels, SIZE, SIZE));
        } catch (e) {
          failed.push(`${view.name} ${z}/${x}/${y} ${String(e.message).split('\n')[0]}`);
        }
      }
    }
    process.send({ done: 1 });
  }
  for (const { map } of maps) {
    map.release();
  }
  pm.close();
  process.send({ failed });
}

// --- the parent: forks the workers and collects their failures ---

async function run(file, list, runDir, cacheDir, processes) {
  for (const mode of MODES) {
    for (const view of mode.views) {
      mkdirSync(join(runDir, 'maplibre', view.name), { recursive: true });
    }
  }
  const total = readFileSync(list, 'utf8').split(/\r?\n/).filter(Boolean).length;
  const started = Date.now();
  let done = 0;
  const failed = [];
  await Promise.all(Array.from({ length: processes }, (_, i) => new Promise((ok, fail) => {
    const child = fork(fileURLToPath(import.meta.url), ['work', file, list, runDir, cacheDir, String(i), String(processes)]);
    child.on('message', m => {
      if (m.done) {
        done++;
        if (done % 200 === 0) {
          console.log(`${done} / ${total} tiles, ${(done / ((Date.now() - started) / 1000)).toFixed(1)} tiles/s`);
        }
      }
      if (m.failed) {
        failed.push(...m.failed);
      }
    });
    child.on('exit', code => (code === 0 ? ok() : fail(new Error(`worker ${i} exited with ${code}`))));
  })));
  writeFileSync(join(runDir, 'maplibre', 'failed.txt'), failed.join('\n') + (failed.length ? '\n' : ''));
  console.log(`${done} tiles in ${((Date.now() - started) / 1000).toFixed(0)} s, ${failed.length} views failed`);
}

const [command, ...args] = process.argv.slice(2);
if (command === 'work') {
  const [file, list, runDir, cacheDir, index, count] = args;
  await work(file, list, runDir, cacheDir, +index, +count);
} else {
  const [file, list, runDir, cacheDir, processes = String(Math.max(1, availableParallelism() - 2))] = [command, ...args];
  if (!cacheDir) {
    console.error('usage: node sweep-native.mjs <file.pmtiles> <tiles.txt> <runDir> <cacheDir> [processes]');
    process.exit(2);
  }
  await run(file, list, runDir, cacheDir, +processes);
}
