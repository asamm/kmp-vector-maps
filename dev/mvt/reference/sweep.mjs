// Copyright 2026 Asamm Software
// SPDX-License-Identifier: Apache-2.0
//
// The MapLibre half of the parity sweep (dev/mvt/sweep): samples tiles of a large PMTiles file and
// renders them with MapLibre GL JS in headless Edge, the tiles served from the file itself.
// Usage, from this folder:
//   node sweep.mjs sample <file.pmtiles> <tiles.txt> [perZoom=200] [seed=1] [minZoom=1] [maxZoom=18]
//   node sweep.mjs render <file.pmtiles> <tiles.txt> <runDir> [pages=8]
//   node sweep.mjs serve <file.pmtiles>  (render starts it itself)
// render writes <runDir>/maplibre/<mode>/<z>_<x>_<y>.png for the modes base (no symbols), labels
// and labels_r30 (the map turned 30 degrees clockwise), and <runDir>/maplibre/failed.txt.
import { spawn } from 'node:child_process';
import { createServer } from 'node:http';
import { existsSync, mkdirSync, readFileSync, renameSync, statSync, writeFileSync } from 'node:fs';
import { dirname, extname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright';
import { PmTiles, zxy } from './pmtiles.mjs';
import { MODES, tileCenter } from './sweep-views.mjs';

const here = dirname(fileURLToPath(import.meta.url));
// the server sees dev/ only, never the rest of the repository
const served = resolve(here, '../..');

// --- sampling ---

function random(seed) {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

// `count` of `items` without repeats, an item's chance growing with its weight (Efraimidis-Spirakis)
function weighted(items, weight, count, rnd) {
  return items
    .map(item => ({ item, key: Math.pow(rnd(), 1 / Math.max(1, weight(item))) }))
    .sort((a, b) => b.key - a.key)
    .slice(0, count)
    .map(e => e.item);
}

// tiles of each zoom, distinct contents only, the larger (denser) ones likelier; above the data
// zoom children of sampled data tiles
function sample(file, out, perZoom, seed, minZoom, maxZoom) {
  const pm = new PmTiles(file);
  const byZoom = new Map();
  const seen = new Set();
  let entries = 0;
  for (const e of pm.entries()) {
    entries++;
    if (seen.has(e.offset)) {
      continue;
    }
    seen.add(e.offset);
    const [z, x, y] = zxy(e.id);
    if (!byZoom.has(z)) {
      byZoom.set(z, []);
    }
    byZoom.get(z).push({ z, x, y, length: e.length });
  }
  const rnd = random(seed);
  const lines = [];
  for (let z = minZoom; z <= maxZoom; z++) {
    const dataZoom = Math.min(z, pm.maxZoom);
    const pool = byZoom.get(dataZoom) || [];
    const picked = weighted(pool, t => t.length, perZoom, rnd);
    for (const t of picked) {
      const dz = z - dataZoom;
      const span = Math.pow(2, dz);
      const x = t.x * span + Math.floor(rnd() * span);
      const y = t.y * span + Math.floor(rnd() * span);
      lines.push(`${z}/${x}/${y}`);
    }
    console.log(`z${z}: ${picked.length} of ${pool.length} distinct data tiles`);
  }
  pm.close();
  mkdirSync(dirname(resolve(out)), { recursive: true });
  // two picks above the data zoom may land on one child
  const unique = [...new Set(lines)];
  writeFileSync(out, unique.join('\n') + '\n');
  console.log(`${unique.length} tiles from ${entries} entries -> ${out}`);
}

// --- rendering ---

const PAGES_PER_BROWSER = +(process.env.SWEEP_PAGES_PER_BROWSER || 2);

const CACHE_BYTES = 256 * 1024 * 1024;

const TYPES = { '.html': 'text/html', '.json': 'application/json', '.png': 'image/png' };

// the tiles and dev/ over HTTP, in a process of its own, as reading and inflating tiles would stall
// the pages' control
function serve(file) {
  const pm = new PmTiles(file);
  // inflated tiles, the oldest dropped past CACHE_BYTES
  const cache = new Map();
  let cached = 0;
  const stats = { requests: 0, ms: 0 };
  const server = createServer((request, response) => {
    let path;
    try {
      path = decodeURIComponent(new URL(request.url, 'http://x').pathname);
    } catch (e) {
      response.writeHead(400);
      response.end();
      return;
    }
    const match = path.match(/^\/sweep-tiles\/(\d+)\/(\d+)\/(\d+)\.pbf$/);
    if (match) {
      const t = performance.now();
      let bytes = cache.get(path);
      if (bytes === undefined) {
        bytes = pm.tile(+match[1], +match[2], +match[3]);
        while (cached > CACHE_BYTES) {
          const [oldest, data] = cache.entries().next().value;
          cache.delete(oldest);
          cached -= data?.length ?? 0;
        }
        cache.set(path, bytes);
        cached += bytes?.length ?? 0;
      }
      stats.requests++;
      stats.ms += performance.now() - t;
      if (process.env.SWEEP_TRACE && stats.requests % 200 === 0) {
        console.error(`  server: ${stats.requests} tiles, ${(stats.ms / stats.requests).toFixed(1)} ms each`);
      }
      response.writeHead(bytes ? 200 : 204, { 'content-type': 'application/x-protobuf' });
      response.end(bytes || undefined);
      return;
    }
    const file = resolve(served, '.' + path);
    if (!file.startsWith(served + sep)
      || !existsSync(file)
      || !statSync(file).isFile()) {
      response.writeHead(404);
      response.end();
      return;
    }
    response.writeHead(200, { 'content-type': TYPES[extname(file)] || 'application/octet-stream' });
    response.end(readFileSync(file));
  });
  server.listen(0, '127.0.0.1', () => console.log(`listening ${server.address().port}`));
}

function startServer(file) {
  const child = spawn(process.execPath, [fileURLToPath(import.meta.url), 'serve', file], {
    stdio: ['ignore', 'pipe', 'inherit'],
  });
  return new Promise((ok, fail) => {
    // a server that dies later, such as on a corrupt tile, stops the render: its pages would go
    // idle without tiles
    child.on('exit', code => {
      child.dead = true;
      fail(new Error(`tile server exited with ${code}`));
    });
    child.stdout.on('data', data => {
      const match = String(data).match(/listening (\d+)/);
      if (match) {
        ok({ child, port: +match[1] });
      }
    });
  });
}

async function openPage(browser, port, mode) {
  const page = await browser.newPage({ viewport: { width: 512, height: 512 }, deviceScaleFactor: 1 });
  // the tile server allows only its web app's origin; fetch its fonts outside the page
  await page.route('https://tiles.locusmap.app/**', async route => {
    const response = await route.fetch();
    await route.fulfill({ response, headers: { ...response.headers(), 'access-control-allow-origin': '*' } });
  });
  page.on('console', m => { if (m.type() === 'error') console.error(`  page ${mode.name}: ${m.text()}`); });
  await page.goto(`http://127.0.0.1:${port}/mvt/reference/index.html?tile=1/0/0&style=hike_bike&tiles=sweep&hide=${mode.hide.join(',')}`);
  await page.waitForFunction(() => window.referenceReady === true, null, { timeout: 120000 });
  return page;
}

async function renderView(page, z, x, y, bearing, file) {
  const [lon, lat] = tileCenter(z, x, y);
  const started = Date.now();
  await page.evaluate(({ lon, lat, z, bearing }) => new Promise(done => {
    window.map.once('idle', done);
    window.map.jumpTo({ center: [lon, lat], zoom: z, bearing });
    window.map.triggerRepaint();
  }), { lon, lat, z, bearing });
  const idle = Date.now();
  await page.screenshot({ path: file, type: 'png', clip: { x: 0, y: 0, width: 512, height: 512 } });
  if (process.env.SWEEP_TRACE) {
    console.log(`    view ${z}/${x}/${y} b${bearing}: idle ${idle - started} ms, shot ${Date.now() - idle} ms`);
  }
}

async function render(file, list, runDir, pages) {
  const tiles = readFileSync(list, 'utf8').split(/\r?\n/).filter(Boolean).map(t => t.split('/').map(Number));
  const { child, port } = await startServer(file);
  const out = join(runDir, 'maplibre');
  const browsers = [];
  try {
    for (const mode of MODES) {
      for (const view of mode.views) {
        mkdirSync(join(out, view.name), { recursive: true });
      }
    }
    // the system Edge, so nothing needs downloading; PW_CHANNEL=chromium for Playwright's own
    // build
    const channel = process.env.PW_CHANNEL || 'msedge';
    // a browser's pages share its one GPU process, so the pages spread over several browsers
    for (let i = 0; i < Math.ceil(pages / PAGES_PER_BROWSER); i++) {
      browsers.push(await chromium.launch({
        channel: channel === 'chromium' ? undefined : channel,
        // the GPU through ANGLE, or SWEEP_GL=swiftshader for software rendering
        args: process.env.SWEEP_GL === 'swiftshader'
          ? ['--use-angle=swiftshader', '--enable-unsafe-swiftshader']
          : ['--use-angle=d3d11', '--enable-gpu', '--ignore-gpu-blocklist'],
      }));
    }
    await renderJobs(tiles, out, browsers, port, pages, child);
  } finally {
    await Promise.all(browsers.map(b => b.close()));
    child.kill();
  }
}

async function renderJobs(tiles, out, browsers, port, pages, child) {
  const jobs = [];
  for (const [z, x, y] of tiles) {
    for (const mode of MODES) {
      const files = mode.views.map(v => join(out, v.name, `${z}_${x}_${y}.png`));
      jobs.push({ mode, z, x, y, files });
    }
  }
  const todo = jobs.filter(j => !j.files.every(f => existsSync(f)));
  const failed = [];
  let done = 0;
  const started = Date.now();
  // each worker keeps one page per mode, as the modes are different styles
  const worker = async index => {
    const browser = browsers[Math.floor(index / PAGES_PER_BROWSER)];
    const own = {};
    for (;;) {
      // a crashed browser's pages would fail every job left: the job goes back for the others
      if (!browser.isConnected()) {
        console.error(`  worker ${index}: its browser is gone`);
        break;
      }
      const job = todo.shift();
      if (!job
        || child.dead) {
        break;
      }
      try {
        own[job.mode.name] ??= await openPage(browser, port, job.mode);
        const t = Date.now();
        for (const [i, view] of job.mode.views.entries()) {
          // kept under its name only whole and with the server alive, as resuming keeps files
          await renderView(own[job.mode.name], job.z, job.x, job.y, view.bearing, job.files[i] + '.view');
          if (child.dead) {
            throw new Error('tile server died');
          }
          renameSync(job.files[i] + '.view', job.files[i]);
        }
        if (process.env.SWEEP_TRACE) {
          console.log(`  ${job.mode.name} ${job.z}/${job.x}/${job.y} ${Date.now() - t} ms`);
        }
      } catch (e) {
        if (!browser.isConnected()) {
          todo.push(job);
          continue;
        }
        failed.push(`${job.mode.name} ${job.z}/${job.x}/${job.y} ${String(e.message).split('\n')[0]}`);
        await own[job.mode.name]?.close().catch(() => {});
        delete own[job.mode.name];
      }
      done++;
      if (done % 100 === 0) {
        const rate = done / ((Date.now() - started) / 1000);
        console.log(`${done} / ${jobs.length}, ${rate.toFixed(1)} tiles/s, ${failed.length} failed`);
      }
    }
    for (const page of Object.values(own)) {
      await page.close();
    }
  };
  await Promise.all(Array.from({ length: pages }, (_, i) => worker(i)));
  if (child.dead) {
    throw new Error(`tile server died after ${done} jobs; run again to resume`);
  }
  writeFileSync(join(out, 'failed.txt'), failed.join('\n') + (failed.length ? '\n' : ''));
  console.log(`rendered ${done} in ${((Date.now() - started) / 1000).toFixed(0)} s, ${failed.length} failed`);
}

const [command, ...args] = process.argv.slice(2);
if (command === 'sample') {
  const [file, out, perZoom = '200', seed = '1', minZoom = '1', maxZoom = '18'] = args;
  sample(file, out, +perZoom, +seed, +minZoom, +maxZoom);
} else if (command === 'serve') {
  serve(args[0]);
} else if (command === 'render') {
  const [file, list, runDir, pages = '8'] = args;
  await render(file, list, runDir, +pages);
} else {
  console.error('usage: node sweep.mjs sample|render ..., see the header');
  process.exit(2);
}
