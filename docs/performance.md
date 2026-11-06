# Performance

Benchmark baselines and how M7 measures against them; M7's steps and status are in
[ROADMAP.md](ROADMAP.md).

**Author:** Menion · **Started:** 2026-11-06, split from the roadmap · **Baselines:** M2.7, M2.7b
(Mapsforge, Java), M4.3c (Garmin)

**Status:** no measurement since M5 and M6 changed the code (Q20). **Next action:** M7.0, the
same benchmarks on `main` against the tables below.

## How to measure

| Run | Command | Setup |
|---|---|---|
| JVM | `./gradlew :dev:bench:jmh` | Ryzen AI 9 HX PRO 375 (24 threads), JDK 17, p50 ms |
| JVM, Czech map | `-Pbench.mapFile=<path> -Pbench.center=50.0875,14.4214` | `czech_republic.osm.map`, LoMaps of 2026-10-08, local |
| JVM, Garmin | `-Pbench.includes=GarminBench` | Monaco fixture at the harbour |
| JVM, Garmin Czech | `-Pbench.garmin.mapFile=<path> -Pbench.garmin.center=50.0875,14.4214` | `CzechRep_by_Dave_Luv.img` at Prague, local |
| A54 | `./gradlew :dev:bench:android:connectedReleaseAndroidTest` | SM-A546B, Android 16, clocks locked |

- Mapsforge: 512 px tiles, Locus' primary mode (labels to the store), LoMaps day theme, no menu.
- Garmin: 512 px tiles, the map's own TYP; without `bench.garmin.center` the map's bounds' centre,
  which is Monaco's harbour (43.735, 7.423) but mid-country for the Czech map.
- A54: the same properties as `-Pandroid.testInstrumentationRunnerArguments.<property>`, e.g.
  `...bench.garmin.center=50.0875,14.4214`.
- Theme parse varies ~20 % between JVM runs: compare deltas below that with `fork = 3`.
- A54 setup and the run's arguments: `dev/bench/android/build.gradle.kts`.
- Since M6.1d Garmin tiles carry no labels: the M4.3c render numbers include label work the tiles
  no longer do.

## Baselines

**M2.7 — Mapsforge, JVM, 2026-10-23** (Java code before M5)

| Benchmark | Monaco | Czech map at Prague |
|---|---|---|
| theme parse | 63 | 52 |
| read tile z12 / z15 / z17 | 3.2 / 0.11 / 0.09 | 8.4 / 1.1 / 1.1 |
| render tile z12 / z15 / z17 | 64 / 30 / 12 | 105 / 85 / 25 |
| 4×4 block at z16, 1 / 2 / 4 / 8 threads | 364 / 188 / 115 / 90 | 595 / 318 / 194 / 135 |
| allocated per tile render at z12 | 12.7 MB | 31.5 MB |

**M2.7b — Mapsforge, A54, 2026-10-23** (`androidx.benchmark` 1.5.0). Rendering takes up to ~2× the
JVM time (z12); the block stops scaling above 4 threads (4 big cores). Theme parse is XML only:
symbols rasterise lazily on first render, which no number isolates.

| Benchmark | Monaco | Czech map at Prague |
|---|---|---|
| theme parse | 83 | 80 |
| read tile z12 / z15 / z17 | 11.3 / 0.64 / 0.52 | 37.6 / 5.2 / 5.7 |
| render tile z12 / z15 / z17 | 126 / 39 / 15 | 259 / 131 / 36 |
| 4×4 block at z16, 1 / 2 / 4 / 8 threads | 433 / 260 / 171 / 168 | 881 / 502 / 324 / 305 |

**M4.3c — Garmin, before → after M4.3c, 2026-11-03** (JVM JDK 24 on a busy desktop, ±20 %; A54
clocks locked, one run each). Czech: 191,807,488 B, 38 maps; Europe gmapsupp (3.7 GB, 746 maps) on
the JVM: open 2,729 → 198 ms, dataset heap after open 665 → 81 MB.

| Benchmark | JVM Monaco | JVM Czech | A54 Monaco | A54 Czech |
|---|---|---|---|---|
| open | 8.8 → 2.6 | 87 → 34 | 17 → 3.7 | 321 → 50 |
| read tile z12 / z15 / z17 | 2.4 / 3.9 / 14.6 → 1.4 / 2.3 / 3.0 | 26 / 28 / 40 → 6.2 / 6.4 / 7.8 | 9.3 / 11 / 30 → 7.4 / 9.1 / 11 | 115 / 113 / 187 → 45 / 39 / 45 |
| render tile z12 / z15 / z17 | 18 / 15 / 20 → 18 / 11 / 5.8 | 64 / 43 / 54 → 48 / 21 / 22 | 33 / 30 / 42 → 29 / 27 / 20 | 181 / 141 / 210 → 106 / 62 / 65 |
| 4×4 block at z16, 1 / 2 / 4 / 8 threads | 493 / 291 / 172 / 107 → 302 / 166 / 88 / 53 | 940 / 493 / 292 / 261 → 409 / 205 / 106 / 69 | 1007 / 546 / 310 / 286 → 560 / 309 / 180 / 191 | 3354 / 1798 / 1005 / 857 → 1123 / 617 / 358 / 313 |
| allocated per Czech render at z12 | | 8.4 → 6.3 MB | | |

## Known costs to measure

From M0–M6, each measured before it is changed (ROADMAP P-rows):

| ID | Cost | Seen in |
|---|---|---|
| P2 | label pass `collect` describes every stored label on each prepare | M6.1b |
| P3 | `FeatureQuery.point` builds a full `RenderContext`, ~0.7 MB garbage for LoMaps | M6.3 |
| P5 | Garmin on the A54: reads ~6× the JVM, an allocation per pattern, dash, label and icon draw | M4.3c |
| P15 | Garmin label halos draw each text nine times | M4.3c |
| P6 | Garmin: boxed label and coordinate buffers; sub-division trees kept after add | M4.3c |
| P9 | `PaintCostTest` never run on the A54; F1's native growth mostly unexplained (Q18) | M5.3 |
