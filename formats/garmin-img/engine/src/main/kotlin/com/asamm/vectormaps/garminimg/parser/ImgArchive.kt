/*
 * Derived from JGarminImgParser, Copyright (C) 2006 Patrick Valsecchi, and its Asamm fork
 * libGarminImg.
 * Copyright 2026 Asamm Software
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */
package com.asamm.vectormaps.garminimg.parser

import com.asamm.vectormaps.garminimg.utils.io.ImgRandomAccessReader

/**
 * Top-level reader for an IMG container.
 *
 * Replaces the Java `ImgFileBagExtractor` head — opens an IMG, validates the
 * `DSKIMG` signature, computes the block size from the header exponents, and
 * enumerates the FAT directory. Sub-file content parsing (TRE, RGN, LBL, NET, TYP)
 * lives in dedicated readers built on top of this entry point.
 *
 * Immutable after [open], so threads may share it; the readers passed to [open] and
 * [openSubFile] are single-threaded.
 *
 * # On-disk layout (unencrypted IMG)
 * - `0x00..0x07` — eight bytes; first byte is `0x00` for unencrypted maps. The Java
 *   predecessor used the first byte as an XOR key for "encrypted" maps; that code
 *   path is dropped (out of scope, see the CLAUDE.md scope table).
 * - `0x10` — six-byte ASCII signature `DSKIMG`.
 * - `0x49..0x5C` — 20-byte ASCII map description (space-padded).
 * - `0x61, 0x62` — two block-size exponents: `blockSize = 1 shl (e1 + e2)`. Standard
 *   values yield 512, 2048, or 8192 bytes.
 * - `0x1FE` — UInt16 LE; must equal `0xAA55` (end-of-partition-table marker, in MBR
 *   tradition).
 * - `0x200..` — FAT region. Scan in [FAT_BLOCK_SIZE]-byte steps until a block whose
 *   first byte is `0x01` appears; that block's offset+0xC carries an absolute byte
 *   offset to the end of the FAT, from which we derive the FAT length. The block
 *   itself is a FAT header; subsequent blocks are entries.
 * - Each FAT entry is exactly [FAT_BLOCK_SIZE] bytes:
 *   - byte `0x00`: marker (`0x01` = entry present, anything else = skip);
 *   - bytes `0x01..0x08`: 8-character filename (ASCII, space-padded);
 *   - bytes `0x09..0x0B`: 3-character filetype (ASCII, uppercase);
 *   - bytes `0x0C..0x0F`: file size, Int32 LE;
 *   - bytes `0x10..0x11`: part number, UInt16 LE — only `0` is the primary entry;
 *     non-zero records are continuation entries we skip;
 *   - bytes `0x20..0x21`: index of first block, UInt16 LE.
 */
class ImgArchive private constructor(
    val description: String,
    val blockSize: Int,
    val subFiles: List<SubFileEntry>,
) {

    /**
     * Returns the first sub-file entry matching `fileType` (case-insensitive), or
     * `null` if no such sub-file is present. Useful for the common pattern of
     * looking up exactly one TRE/RGN/LBL/NET per logical map.
     */
    fun findFirst(fileType: String): SubFileEntry? {
        return subFiles.firstOrNull { it.fileType.equals(fileType, ignoreCase = true) }
    }

    /**
     * Returns every sub-file entry whose type matches `fileType` (case-insensitive).
     */
    fun findAll(fileType: String): List<SubFileEntry> {
        return subFiles.filter { it.fileType.equals(fileType, ignoreCase = true) }
    }

    /**
     * Opens a logical reader over the sub-file described by `entry`, using `reader`
     * as the underlying IMG byte source. The returned stream addresses bytes as
     * logical offsets into the sub-file (0..[SubFileEntry.fileSize] − 1) and
     * translates to physical IMG offsets via the container [blockSize].
     *
     * The same `reader` may back multiple [ImgSubFileStream]s; each tracks its own
     * logical position and re-seeks the container before every read.
     */
    fun openSubFile(entry: SubFileEntry, reader: ImgRandomAccessReader): ImgSubFileStream {
        return ImgSubFileStream(
            container = reader,
            firstBlock = entry.firstBlock,
            blockSize = blockSize,
            fileSize = entry.fileSize.toLong() and 0xFFFFFFFFL,
        )
    }

    companion object {

        /**
         * IMG signature at offset 0x10. Six ASCII bytes spelling `DSKIMG`.
         */
        const val DSKIMG_SIGNATURE: String = "DSKIMG"

        /**
         * End-of-partition-table marker at offset 0x1FE. UInt16 little-endian.
         */
        const val PARTITION_TABLE_END: Int = 0xAA55

        /**
         * Each FAT entry occupies exactly this many bytes; the FAT region is also
         * scanned in chunks of this size when locating its start.
         */
        const val FAT_BLOCK_SIZE: Int = 512

        /**
         * First byte of a real FAT entry / FAT header block. Anything else means
         * "padding, skip".
         */
        const val FAT_ENTRY_MARKER: Int = 0x01

        /**
         * Most sub-files read from one FAT; real maps, gmapsupp included, hold a few thousand.
         */
        private const val MAX_FAT_ENTRIES: Int = 100_000

        /**
         * Reads the IMG header and FAT from `reader`, returning a fully-populated
         * archive description. Does not retain `reader`; callers may close it
         * after this call returns.
         *
         * @throws IllegalArgumentException on a malformed header (bad signature,
         *   wrong partition-table marker) or a structurally invalid FAT.
         */
        fun open(reader: ImgRandomAccessReader): ImgArchive {
            requireUnencryptedHeader(reader)
            val description = readDescription(reader)
            val blockSize = readBlockSize(reader)
            requirePartitionTableEnd(reader)

            val (firstEntryOffset, fatEndOffset) = findFatBounds(reader)
            val subFiles = readFatEntries(reader, firstEntryOffset, fatEndOffset)
            return ImgArchive(description, blockSize, subFiles)
        }

        private fun requireUnencryptedHeader(reader: ImgRandomAccessReader) {
            reader.seek(0L)
            val firstByte = reader.readByte()
            require(firstByte == 0) {
                "First byte of IMG is 0x${firstByte.toString(16)} — expected 0x00. " +
                    "Encrypted/locked IMGs are not supported."
            }
            reader.seek(0x10L)
            val signature = reader.readString(DSKIMG_SIGNATURE.length)
            require(signature == DSKIMG_SIGNATURE) {
                "Bad IMG signature at 0x10: '$signature' (expected '$DSKIMG_SIGNATURE')"
            }
        }

        private fun readDescription(reader: ImgRandomAccessReader): String {
            reader.seek(0x49L)
            return reader.readString(20).trimEnd(' ', '\u0000')
        }

        private fun readBlockSize(reader: ImgRandomAccessReader): Int {
            reader.seek(0x61L)
            val e1 = reader.readByte()
            val e2 = reader.readByte()
            val exponent = e1 + e2
            require(exponent in 0..30) {
                "Bad block-size exponent: $e1 + $e2 = $exponent"
            }
            return 1 shl exponent
        }

        private fun requirePartitionTableEnd(reader: ImgRandomAccessReader) {
            reader.seek(0x1FEL)
            val marker = reader.readUInt16()
            require(marker == PARTITION_TABLE_END) {
                "Bad partition-table end marker at 0x1FE: 0x${marker.toString(16)} " +
                    "(expected 0x${PARTITION_TABLE_END.toString(16)})"
            }
        }

        /**
         * Locates the first FAT entry block by scanning past padding from `0x200`
         * upward, then derives where the FAT ends by reading the absolute end
         * offset stored at `headerBlock + 0xC`.
         *
         * Returns `(firstEntryOffset, fatEndOffset)`. The FAT header block itself
         * lives at `firstEntryOffset - FAT_BLOCK_SIZE`.
         */
        private fun findFatBounds(reader: ImgRandomAccessReader): Pair<Long, Long> {
            var scan = 0x200L
            while (scan + FAT_BLOCK_SIZE <= reader.size) {
                reader.seek(scan)
                val firstByte = reader.readByte()
                if (firstByte == FAT_ENTRY_MARKER) {
                    break
                }
                scan += FAT_BLOCK_SIZE
            }
            require(scan + FAT_BLOCK_SIZE <= reader.size) {
                "Reached EOF scanning for FAT start (last scan=0x${scan.toString(16)})"
            }

            // The FAT header block carries the absolute byte-offset of the FAT end at
            // its 0x0C field. Subtract `scan` to get the FAT length in bytes; the
            // first entry sits one FAT_BLOCK_SIZE past `scan`.
            reader.seek(scan + 0x0CL)
            val fatEndOffset = reader.readInt32().toLong() and 0xFFFFFFFFL
            require(fatEndOffset > scan + FAT_BLOCK_SIZE) {
                "Invalid FAT end offset: 0x${fatEndOffset.toString(16)} <= " +
                    "header-block end 0x${(scan + FAT_BLOCK_SIZE).toString(16)}"
            }
            // Refuse a fatEndOffset past EOF — a corrupt header carrying
            // 0xFFFFFFFF would otherwise drive ~8 M seek/read syscalls
            // before the entry-walk reads finally fail.
            require(fatEndOffset <= reader.size) {
                "FAT end offset 0x${fatEndOffset.toString(16)} past EOF (size=${reader.size})"
            }
            return (scan + FAT_BLOCK_SIZE) to fatEndOffset
        }

        private fun readFatEntries(
            reader: ImgRandomAccessReader,
            firstEntryOffset: Long,
            fatEndOffset: Long,
        ): List<SubFileEntry> {
            val entries = mutableListOf<SubFileEntry>()
            var entryOffset = firstEntryOffset
            while (entryOffset + FAT_BLOCK_SIZE <= fatEndOffset) {
                val parsed = readOneFatEntry(reader, entryOffset)
                if (parsed != null) {
                    // a crafted FAT spanning a huge file would otherwise fill the heap
                    require(entries.size < MAX_FAT_ENTRIES) {
                        "IMG FAT has more than $MAX_FAT_ENTRIES sub-files"
                    }
                    entries.add(parsed)
                }
                entryOffset += FAT_BLOCK_SIZE
            }
            return entries
        }

        /**
         * Reads one FAT entry at `offset`. Returns `null` for non-primary records
         * (continuation parts, padding, or anything not flagged with the entry
         * marker) — callers ignore those.
         */
        private fun readOneFatEntry(
            reader: ImgRandomAccessReader,
            offset: Long,
        ): SubFileEntry? {
            reader.seek(offset)
            val marker = reader.readByte()
            if (marker != FAT_ENTRY_MARKER) {
                return null
            }
            val fileName = reader.readString(8).trimEnd(' ', '\u0000')
            val fileType = reader.readString(3).trimEnd(' ', '\u0000').uppercase()
            val fileSize = reader.readInt32()
            val partNumber = reader.readUInt16()
            // Only the primary record (partNumber == 0) describes a sub-file.
            // Continuation parts repeat (filename, filetype) but list further
            // block addresses elsewhere in the entry; we skip them.
            if (partNumber != 0) {
                return null
            }
            reader.seek(offset + 0x20L)
            val firstBlock = reader.readUInt16()
            return SubFileEntry(
                fileName = fileName,
                fileType = fileType,
                fileSize = fileSize,
                firstBlock = firstBlock,
                fatOffset = offset,
            )
        }
    }
}
