package com.asamm.gradle

import org.gradle.api.Project
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Computes the module version from the base version string.
 * When `-PisSnapshot` is set, appends a `-dev.<suffix>` qualifier.
 * The suffix comes from `-PsnapshotSuffix` (e.g. a git SHA) or falls back to a timestamp.
 */
fun Project.moduleVersion(baseVersion: String): String {
    return if (findProperty("isSnapshot") != null) {
        val suffix = (findProperty("snapshotSuffix") as String?)
            ?: SimpleDateFormat("yyyyMMdd'T'HHmmss").format(Date())
        "$baseVersion-dev.$suffix"
    } else {
        baseVersion
    }
}
