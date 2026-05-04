# Changelog

All notable changes to this project will be documented in this file.
This project adheres to [Semantic Versioning](https://semver.org/).

## [0.1.0] — unreleased

Phase A bootstrap. Sets up the Kotlin Multiplatform skeleton for Android + JVM,
ports the two seed utilities (`CoordUtils`, `BinaryReader`) from the Java
predecessor `libGarminImg`, and wires GitHub Packages publishing.

### Added — Phase A
- Gradle KMP skeleton (Kotlin 2.3.20, AGP 9.1.1, Gradle 9.3.1, JDK 17).
- `:lib` module with Android + JVM targets.
- `com.asamm.garminimg.coord.CoordUtils` — fixed-point ↔ WGS84 conversions and
  bounding-box helpers, ported from `org.free.garminimg.CoordUtils`.
- `com.asamm.garminimg.io.BinaryReader` — endian-aware sequential reader over
  `kotlinx.io.Source`, the seed of the binary IO layer.
- GitHub Actions: CI build, manual snapshot publish, tag-driven release publish.
- LGPL-2.1 LICENSE copied from the libGarminImg fork (derivative work).
