# Viewer captures

Drop-folder for the desktop viewer's **Save view** button. Each click
writes two files sharing a timestamp prefix:

- `<ts>.png` — the rendered image as currently displayed.
- `<ts>.json` — viewport metadata: bbox in both Garmin fixed-point
  coordinates and WGS84 degrees, panel size in pixels, the picked TRE
  level, and the current render-mode toggles (Anti-alias,
  Areas / Lines / Points).

`<ts>` format: `yyyyMMdd-HHmmss`.

## Example JSON

```json
{
  "timestamp": "20260506-101530",
  "img": "C:\\maps\\otm-monaco.img",
  "panel": { "widthPx": 1200, "heightPx": 800 },
  "viewport_garmin": {
    "minLon": 338207, "maxLon": 339092,
    "minLat": 2036880, "maxLat": 2037450
  },
  "viewport_wgs84": {
    "minLon": 7.259522, "maxLon": 7.278494,
    "minLat": 43.703911, "maxLat": 43.716139
  },
  "picked_level": 1,
  "render": {
    "antiAliased": true,
    "showAreas": true,
    "showLines": true,
    "showPoints": true
  }
}
```

## Use

When reporting a visual issue, the JSON is enough to reproduce the same
view exactly — the bbox plus panel size feed straight into a
`Viewport(...)` constructor, no manual coord math needed.

Captures aren't auto-committed; `git add` them individually when you
want a specific render in version control (e.g. anchoring a known-good
state for later regression).
