// Copyright 2026 Asamm Software
// SPDX-License-Identifier: Apache-2.0
//
// Renders the MVT reference tiles with MapLibre GL JS in headless Chromium, into
// dev/fixtures/mvt/reference/maplibre/<variant>/<z>/<x>/<y>.png, one variant per layer set.
// Usage, from this folder: node render.mjs [z/x/y ...]  (default: every server reference tile)
import { spawn } from 'node:child_process';
import { mkdirSync, readdirSync, statSync } from 'node:fs';
import { dirname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright';

const here = dirname(fileURLToPath(import.meta.url));
// the server sees dev/ only, never the rest of the repository
const served = resolve(here, '../..');
const fixtures = join(served, 'fixtures/mvt');
const port = 8765;
const python = process.env.PYTHON || (process.platform === 'win32' ? 'python' : 'python3');

const VARIANTS = {
  // what the Kotlin renderer draws so far: no symbols, no elevation layers
  base: 'symbol,hillshade,color-relief',
  full: '',
};

function referenceTiles() {
  const dir = join(fixtures, 'reference/server/hike_bike');
  const out = [];
  const walk = d => {
    for (const name of readdirSync(d)) {
      const p = join(d, name);
      if (statSync(p).isDirectory()) {
        walk(p);
      } else if (name.endsWith('.png')) {
        out.push(relative(dir, p).replaceAll('\\', '/').replace('.png', ''));
      }
    }
  };
  walk(dir);
  return out;
}

const tiles = process.argv.length > 2 ? process.argv.slice(2) : referenceTiles();
async function waitForServer(server) {
  let exited = null;
  server.on('exit', code => { exited = code; });
  for (let i = 0; i < 50; i++) {
    if (exited !== null) {
      throw new Error(`${python} http.server exited with ${exited}; is port ${port} taken?`);
    }
    try {
      const response = await fetch(`http://127.0.0.1:${port}/mvt/reference/index.html`);
      if (response.ok) {
        return;
      }
    } catch {
      // not listening yet
    }
    await new Promise(r => setTimeout(r, 200));
  }
  throw new Error(`no server on port ${port}`);
}

const server = spawn(python, ['-m', 'http.server', String(port), '--bind', '127.0.0.1', '--directory', served], {
  stdio: 'ignore',
});
server.on('error', e => {
  console.error(`cannot start ${python}: ${e.message}; set PYTHON`);
  process.exit(1);
});
let browser;
try {
  await waitForServer(server);
  // the system Edge, so nothing needs downloading; PW_CHANNEL=chromium for Playwright's own build
  const channel = process.env.PW_CHANNEL || 'msedge';
  browser = await chromium.launch({
    channel: channel === 'chromium' ? undefined : channel,
    args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'],
  });
  const page = await browser.newPage({ viewport: { width: 512, height: 512 }, deviceScaleFactor: 1 });
  // the tile server allows only its web app's origin; fetch its fonts and elevation outside the page
  await page.route('https://tiles.locusmap.app/**', async route => {
    const response = await route.fetch();
    await route.fulfill({ response, headers: { ...response.headers(), 'access-control-allow-origin': '*' } });
  });
  page.on('console', m => { if (m.type() === 'error') console.error('  page:', m.text()); });
  for (const [variant, hide] of Object.entries(VARIANTS)) {
    for (const tile of tiles) {
      const url = `http://127.0.0.1:${port}/mvt/reference/index.html?tile=${tile}&style=hike_bike&tiles=local&hide=${hide}`;
      await page.goto(url);
      await page.waitForFunction(() => window.referenceReady === true, null, { timeout: 60000 });
      const file = join(fixtures, 'reference/maplibre', variant, `${tile}.png`);
      mkdirSync(dirname(file), { recursive: true });
      await page.locator('#map').screenshot({ path: file });
      console.log(`${variant} ${tile}`);
    }
  }
} finally {
  await browser?.close();
  server.kill();
}
