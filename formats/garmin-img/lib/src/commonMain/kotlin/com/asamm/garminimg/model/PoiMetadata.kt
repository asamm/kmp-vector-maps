/*
 * kmp-garmin-img — Kotlin Multiplatform parser & renderer for unencrypted Garmin IMG.
 *
 * Derived from JGarminImgParser and the Asamm fork libGarminImg. Distributed under
 * the GNU Lesser General Public License v2.1.
 */
package com.asamm.garminimg.model

/**
 * Optional address-style metadata attached to a POI ([MapObject.Point]
 * with `isPoi = true`). The IMG carries any subset of these fields per
 * POI; null means "not present in this record."
 *
 * Each field is decoded from the LBL POI section at parse time — the
 * renderer / consumer reads, doesn't compute. Indexed lookups
 * ([cityIndex] / [zipIndex]) are resolved into the matching
 * [cityName] / [zipCode] strings during the same parse step so
 * downstream code never has to touch the LBL again.
 *
 * @property name primary POI name (same string as [MapObject.label])
 * @property streetNumber base-11-encoded house number, or label-pointer string
 * @property street decoded street label
 * @property cityIndex 1-based city table index (0 = no city)
 * @property cityName decoded city name (resolved from [cityIndex])
 * @property zipIndex 1-based ZIP table index (0 = no ZIP)
 * @property zipCode decoded ZIP string (resolved from [zipIndex])
 * @property phone phone number (base-11-encoded) or label-pointer string
 */
data class PoiMetadata(
    val name: String?,
    val streetNumber: String? = null,
    val street: String? = null,
    val cityIndex: Int = 0,
    val cityName: String? = null,
    val zipIndex: Int = 0,
    val zipCode: String? = null,
    val phone: String? = null,
)
