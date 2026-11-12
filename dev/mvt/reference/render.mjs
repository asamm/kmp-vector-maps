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
const repo = resolve(here, '../../..');
const fixtures = join(repo, 'dev/fixtures/mvt');
const port = 8765;

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
const server = spawn('python', ['-m', 'http.server', String(port), '--bind', '127.0.0.1', '--directory', repo], { stdio: 'ignore' });
try {
  await new Promise(r => setTimeout(r, 1000));
  // the system Edge, so nothing needs downloading; PW_CHANNEL=chromium for Playwright's own build
  const channel = process.env.PW_CHANNEL || 'msedge';
  const browser = await chromium.launch({
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
      const url = `http://127.0.0.1:${port}/dev/mvt/reference/index.html?tile=${tile}&style=hike_bike&tiles=local&hide=${hide}`;
      await page.goto(url);
      await page.waitForFunction(() => window.referenceReady === true, null, { timeout: 60000 });
      const file = join(fixtures, 'reference/maplibre', variant, `${tile}.png`);
      mkdirSync(dirname(file), { recursive: true });
      await page.locator('#map').screenshot({ path: file });
      console.log(`${variant} ${tile}`);
    }
  }
  await browser.close();
} finally {
  server.kill();
}
