/*
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: Apache-2.0
 */
package com.asamm.vectormaps.viewer

import com.asamm.vectormaps.core.MapFormatDriver
import java.io.File

/**
 * True when `file` has one of the driver's style extensions.
 */
fun MapFormatDriver.readsStyle(file: File): Boolean {
    return file.extension.lowercase() in styleExtensions
}
