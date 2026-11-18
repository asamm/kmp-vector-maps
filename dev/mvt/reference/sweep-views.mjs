// Copyright 2026 Asamm Software
// SPDX-License-Identifier: Apache-2.0
//
// The views both MapLibre halves of the parity sweep render (sweep.mjs with GL JS, sweep-native.mjs
// with MapLibre Native); dev/mvt/sweep reads them by view name.

// a style per layer set; each view of a tile renders from it
export const MODES = [
  // the tile renderer's layers: no symbols, no elevation layers
  { name: 'base', hide: ['symbol', 'hillshade', 'color-relief'], views: [{ name: 'base', bearing: 0 }] },
  // the label pass over the tiles: no elevation layers, which the host draws; MapLibre's bearing
  // turns the map anticlockwise, the label pass's rotation clockwise
  {
    name: 'labels',
    hide: ['hillshade', 'color-relief'],
    views: [{ name: 'labels', bearing: 0 }, { name: 'labels_r30', bearing: -30 }],
  },
];

export function tileCenter(z, x, y) {
  const n = Math.pow(2, z);
  const lon = (x + 0.5) / n * 360 - 180;
  const lat = Math.atan(Math.sinh(Math.PI * (1 - 2 * (y + 0.5) / n))) * 180 / Math.PI;
  return [lon, lat];
}
