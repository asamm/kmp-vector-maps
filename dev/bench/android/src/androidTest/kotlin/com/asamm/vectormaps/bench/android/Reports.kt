/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.bench.android

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/**
 * Writes `text` to `name` in the test's additional output dir, which Gradle copies to
 * build/outputs/connected_android_test_additional_output; no-op without that dir.
 */
internal fun writeReport(name: String, text: String) {
    val dir = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: return
    File(dir, name).writeText(text + "\n")
}
