# Performance

Benchmark baselines and how M7 measures against them; M7's steps and status are in
[ROADMAP.md](ROADMAP.md).

**Author:** Menion · **Started:** 2026-11-06, split from the roadmap · **Baselines:** M2.7, M2.7b
(Mapsforge, Java), M4.3c (Garmin)

**Status:** M7.0 measured `main` after M6 (2026-11-08): no regression against the baselines, the
JVM's Mapsforge renders up to 27 % faster. M7.2b's lock changes speed up the parallel block by up
to 32 % on the JVM and 29 % on the A54. **Next action:** M7.3, allocations; M7.1 ⏸ (Q15).

## How to measure

| Run | Command | Setup |
|---|---|---|
| JVM | `./gradlew :dev:bench:jmh` | Ryzen AI 9 HX PRO 375 (24 threads), JDK 17, p50 ms |
| JVM, Czech map | `-Pbench.mapFile=<path> -Pbench.center=50.0875,14.4214` | `czech_republic.osm.map`, LoMaps of 2026-10-08, local |
| JVM, Garmin | `-Pbench.includes=GarminBench` | Monaco fixture at the harbour |
| JVM, Garmin Czech | `-Pbench.garmin.mapFile=<path> -Pbench.garmin.center=50.0875,14.4214` | `CzechRep_by_Dave_Luv.img` at Prague, local |
| A54 | `./gradlew :dev:bench:android:connectedReleaseAndroidTest` | SM-A546B, Android 16, clocks locked |
| A54, map in shared storage | install, `appops set … MANAGE_EXTERNAL_STORAGE allow`, `cmd package compile -m speed -f …`, `am instrument -w -e bench.mapFile …` | the connected run reinstalls the test package and drops the grant |

- Mapsforge: 512 px tiles, Locus' primary mode (labels to the store), LoMaps day theme, no menu.
- Garmin: 512 px tiles, the map's own TYP; without `bench.garmin.center` the fixture's harbour
  (43.735, 7.423), on the JVM and the A54, as its bounds' centre is a nearly empty tile; another map
  its bounds' centre, mid-country for the Czech map.
- A54: the same properties as `-Pandroid.testInstrumentationRunnerArguments.<property>`, e.g.
  `...bench.garmin.center=50.0875:14.4214`: a centre takes `:` on the A54, as Gradle cuts the
  argument at a comma; the JVM takes both.
- Theme parse varies ~20 % between runs, on the A54 too: compare deltas below that with `fork = 3`.
- The Mapsforge 4×4 block varies up to 2× on a busy desktop: take it on an idle one, from the jar
  on JDK 17 (`jdk-17/bin/java -jar dev/bench/build/libs/bench-jmh.jar MapsforgeBench.ParallelBlock
  -jvm jdk-17/bin/java …`; the PATH's `java` may be newer), a before and an after build
  interleaved, median of 3 rounds.
- A54: nothing else may hold UiAutomation (the mobile-mcp `DeviceServer`), or the benchmark runner
  dies at its third test; Gradle reports success for a run whose instrumentation never started.
- A54 setup and the run's arguments: `dev/bench/android/build.gradle.kts`.
- Since M6.1d Garmin tiles carry no labels: the M4.3c render numbers include label work the tiles
  no longer do.

## M7.0 — `main` after M6, 2026-11-08

Same setup as the baselines; p50 ms, the baseline in brackets; Garmin Monaco at the harbour. JVM
Mapsforge blocks on JDK 17 on an idle desktop, median of 3 runs (taken with M7.2b's comparison on
2026-11-09; the first `-f 3` run here was JDK 24). A54 Czech the second of two runs, which agree
within 5 % except Garmin render z12 (11 %) and theme parse (18 %); A54 Monaco one run, as the
baselines.

**JVM**

| Benchmark | Mapsforge Monaco | Mapsforge Czech | Garmin Monaco | Garmin Czech |
|---|---|---|---|---|
| theme parse | 57 (63) | 59 (52) | | |
| open | | | 2.9 (2.6) | 14 (34) |
| read z12 / z15 / z17 | 2.6 / 0.07 / 0.06 (3.2 / 0.11 / 0.09) | 8.6 / 1.0 / 1.0 (8.4 / 1.1 / 1.1) | 1.8 / 2.3 / 3.4 (1.4 / 2.3 / 3.0) | 6.2 / 6.4 / 7.8 (6.2 / 6.4 / 7.8) |
| render z12 / z15 / z17 | 51 / 22 / 9.1 (64 / 30 / 12) | 100 / 75 / 21 (105 / 85 / 25) | 17 / 12 / 8.2 (18 / 11 / 5.8) | 38 / 15 / 15 (48 / 21 / 22) |
| 4×4 block, 1 / 2 / 4 / 8 threads | 222 / 149 / 94 / 78 (364 / 188 / 115 / 90) | 433 / 284 / 175 / 137 (595 / 318 / 194 / 135) | 280 / 178 / 99 / 58 (302 / 166 / 88 / 53) | 286 / 177 / 101 / 60 (409 / 205 / 106 / 69) |
| allocated per render at z12 | 12.6 MB (12.7) | 31.1 MB (31.5) | | 6.1 MB (6.3) |

**A54**

| Benchmark | Mapsforge Monaco | Mapsforge Czech | Garmin Monaco | Garmin Czech |
|---|---|---|---|---|
| theme parse | 98 (83) | 99 (80) | | |
| open | | | 3.7 (3.7) | 50 (50) |
| read z12 / z15 / z17 | 11.4 / 0.65 / 0.53 (11.3 / 0.64 / 0.52) | 38 / 5.1 / 5.1 (38 / 5.2 / 5.7) | 7.9 / 9.6 / 11 (7.4 / 9.1 / 11) | 45 / 39 / 46 (45 / 39 / 45) |
| render z12 / z15 / z17 | 126 / 39 / 15 (126 / 39 / 15) | 271 / 134 / 37 (259 / 131 / 36) | 32 / 27 / 19 (29 / 27 / 20) | 112 / 61 / 62 (106 / 62 / 65) |
| 4×4 block, 1 / 2 / 4 / 8 threads | 429 / 250 / 168 / 170 (433 / 260 / 171 / 168) | 897 / 511 / 320 / 309 (881 / 502 / 324 / 305) | 553 / 302 / 179 / 191 (560 / 309 / 180 / 191) | 1112 / 609 / 349 / 314 (1123 / 617 / 358 / 313) |

- No regression from M5 and M6 on the A54 (Q20): every number is within the run-to-run spread above,
  Garmin Monaco render z12 (+11 %, one run) at its edge. On the JVM, Mapsforge renders 5–27 % faster
  (z12–z17) and its single-thread block 27–39 %, though the baselines were not taken idle.
- One JVM outlier: Garmin Monaco renders z17 in 8.2 ms against M4.3c's 5.8, and its block is 7–13 %
  slower from 2 threads; the A54 shows neither, and M4.3c's JVM numbers carry ±20 %.
- Theme parse on the A54 is 83–99 ms across runs and maps; the 80–83 baselines are the low end.
- Garmin renders no labels since M6.1d (P4), yet the A54 times stay level: label work was a small
  share of a Garmin tile on the device. The JVM's Czech Garmin gain is mostly JDK 17 against the
  baseline's JDK 24 on a busy desktop (±20 %).
- Same desktop and device as the baselines; the JVM baselines ran on JDK 24 (Garmin) and 17
  (Mapsforge).

## M7.2b — lock contention, 2026-11-09

The 8-thread Czech block spent 18.8 s blocked on monitors in a ~10 s JFR recording: the per-draw
Paint lock 67 %, the `Rule` subtree lock 19 %, `PathText`'s lock 10 %. After C14: 2.3 s, none of the
three in the top. Mapsforge 4×4 block p50 ms, before → after:

| Threads | JVM Monaco | JVM Czech | A54 Monaco | A54 Czech |
|---|---|---|---|---|
| 1 | 222 → 234 | 433 → 440 | 430 → 430 | 897 → 895 |
| 2 | 149 → 137 | 284 → 270 | 253 → 231 | 506 → 486 |
| 4 | 94 → 84 | 175 → 155 | 165 → 132 | 323 → 274 |
| 8 | 78 → 53 | 137 → 100 | 171 → 122 | 309 → 262 |

- JVM: JDK 17, idle desktop, median of 3 interleaved before/after runs; the 8-thread gain held in
  every run (26–33 %). A54: one run each. Single tiles, theme parse and Garmin are not touched:
  level within run-to-run spread (A54 Garmin Monaco render, one run, +11–14 %).
- The A54 soak (`SoakTest`, 5 min) first differed in 2 tiles' labels: `AndroidPaint` measured text
  into one shared `Rect`, which `PathText`'s lock had kept to one thread. With a `Rect` per call
  two soaks passed, as the code before M7.2b does. No JVM test covers it; the soak is the guard.

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
