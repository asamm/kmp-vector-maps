/*
 * kmp-garmin-img — Kotlin Multiplatform parser & renderer for unencrypted Garmin IMG.
 *
 * Derived from JGarminImgParser and the Asamm fork libGarminImg. Distributed under
 * the GNU Lesser General Public License v2.1.
 */
package com.asamm.garminimg.parser

/**
 * One entry in an IMG container's File Allocation Table.
 *
 * The IMG container is structured like a tiny FAT filesystem: the FAT lists every
 * embedded sub-file (TRE, RGN, LBL, NET, TYP, GMP, …) by name, type, size, and the
 * index of its first block in the IMG. Higher layers translate logical sub-file
 * offsets into physical IMG byte offsets through this entry plus the container
 * `blockSize`.
 *
 * @property fileName 8-character mkgmap-style map identifier (e.g. `00000001`).
 *   Right-trimmed of the ASCII space padding the on-disk record carries.
 * @property fileType 3-character sub-file type (uppercase: `TRE`, `RGN`, `LBL`,
 *   `NET`, `TYP`, `GMP`, `MDR`, …).
 * @property fileSize Logical size of the sub-file in bytes, as recorded in the FAT.
 * @property firstBlock Index of the sub-file's first block within the IMG. Multiply
 *   by the container `blockSize` to get the physical byte offset.
 * @property fatOffset Byte offset of this FAT entry inside the IMG. Useful for
 *   diagnostics and for re-parsing the part-number / continuation chain.
 */
data class SubFileEntry(
    val fileName: String,
    val fileType: String,
    val fileSize: Int,
    val firstBlock: Int,
    val fatOffset: Long,
)
