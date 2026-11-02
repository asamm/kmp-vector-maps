package com.asamm.garminimg.parser

/**
 * Builder for tiny synthetic IMG byte streams used by [ImgArchiveTest].
 *
 * Constructs the minimum bytes needed to satisfy [ImgArchive.open]:
 * - DSKIMG signature at 0x10
 * - 20-byte description at 0x49
 * - block-size exponents at 0x61 / 0x62
 * - 0xAA55 partition-table end marker at 0x1FE
 * - one FAT header block at 0x200 with FAT-end offset at 0x20C
 * - N FAT entry blocks following, each 512 bytes
 *
 * Real IMGs are far richer; this builder deliberately stops at "enough for the FAT
 * directory layer to parse cleanly". It does not emit valid sub-file content —
 * every FAT entry's `firstBlock` points into uninitialised tail bytes.
 */
internal class SyntheticImg private constructor(
    private val description: String,
    private val e1: Int,
    private val e2: Int,
    private val leadingPaddingBlocks: Int,
    private val fatEntries: List<FatEntrySpec>,
    private val payloads: List<PayloadSpec>,
) {

    private data class FatEntrySpec(
        val marker: Int,
        val fileName: String,
        val fileType: String,
        val fileSize: Int,
        val partNumber: Int,
        val firstBlock: Int,
    )

    private data class PayloadSpec(
        val blockIndex: Int,
        val bytes: ByteArray,
    )

    fun build(): ByteArray {
        val blockSize = 1 shl (e1 + e2)
        val firstEntryOffset = FAT_HEADER_OFFSET + (1 + leadingPaddingBlocks) * BLOCK
        val fatEndOffset = firstEntryOffset + fatEntries.size * BLOCK

        // Compute total file size large enough to hold every payload at its target
        // block, plus the FAT region. Each payload at blockIndex N needs the file to
        // extend to at least (N+1)*blockSize + bytes.size.
        val payloadEnd = payloads.maxOfOrNull {
            it.blockIndex.toLong() * blockSize + it.bytes.size
        } ?: 0L
        val totalSize = maxOf(fatEndOffset.toInt() + 256, payloadEnd.toInt())
        val img = ByteArray(totalSize)

        // DSKIMG signature at 0x10
        DSKIMG_BYTES.copyInto(img, destinationOffset = 0x10)

        // 20-byte description at 0x49 (space-padded to exactly 20 bytes)
        val descBytes = description.encodeToByteArray()
        require(descBytes.size <= 20) { "description too long: '$description'" }
        descBytes.copyInto(img, destinationOffset = 0x49)
        for (i in descBytes.size until 20) {
            img[0x49 + i] = ' '.code.toByte()
        }

        // Block-size exponents at 0x61
        img[0x61] = e1.toByte()
        img[0x62] = e2.toByte()

        // Partition-table end marker at 0x1FE (UInt16 LE = 0xAA55)
        img[0x1FE] = 0x55
        img[0x1FF] = 0xAA.toByte()

        // Leading padding blocks before the FAT header — start at 0x200, leave
        // first byte == 0 so the scanner skips them.
        // (No-op: ByteArray defaults to 0.)

        // FAT header block: marker 0x01 at offset 0, FAT-end absolute byte offset
        // at offset 0x0C (Int32 LE)
        val fatHeaderOffset = (FAT_HEADER_OFFSET + leadingPaddingBlocks * BLOCK).toInt()
        img[fatHeaderOffset] = 0x01
        writeInt32LE(img, fatHeaderOffset + 0x0C, fatEndOffset.toInt())

        // FAT entry blocks
        var entryOffset = firstEntryOffset.toInt()
        for (entry in fatEntries) {
            img[entryOffset] = entry.marker.toByte()
            writeAsciiPadded(img, entryOffset + 0x01, entry.fileName, 8)
            writeAsciiPadded(img, entryOffset + 0x09, entry.fileType, 3)
            writeInt32LE(img, entryOffset + 0x0C, entry.fileSize)
            writeUInt16LE(img, entryOffset + 0x10, entry.partNumber)
            writeUInt16LE(img, entryOffset + 0x20, entry.firstBlock)
            entryOffset += BLOCK
        }

        // Payload blocks: copy each block's bytes into the IMG at blockIndex*blockSize.
        for (payload in payloads) {
            val target = payload.blockIndex.toLong() * blockSize
            require(target + payload.bytes.size <= img.size) {
                "payload at block ${payload.blockIndex} (${payload.bytes.size} bytes) " +
                    "extends past synthetic IMG size ${img.size}"
            }
            payload.bytes.copyInto(img, destinationOffset = target.toInt())
        }

        return img
    }

    class Builder {
        private var description: String = "synthetic"
        private var e1: Int = 9
        private var e2: Int = 0
        private var leadingPadding: Int = 0
        private val entries = mutableListOf<FatEntrySpec>()
        private val payloads = mutableListOf<PayloadSpec>()

        fun description(value: String) = apply { description = value }

        fun blockSizeExponent(e1: Int, e2: Int) = apply {
            this.e1 = e1
            this.e2 = e2
        }

        fun leadingFatPaddingBlocks(count: Int) = apply { leadingPadding = count }

        fun addFatEntry(
            marker: Int = ImgArchive.FAT_ENTRY_MARKER,
            fileName: String,
            fileType: String,
            fileSize: Int = 0,
            partNumber: Int = 0,
            firstBlock: Int = 0,
        ) = apply {
            entries.add(
                FatEntrySpec(marker, fileName, fileType, fileSize, partNumber, firstBlock),
            )
        }

        /**
         * Inject `bytes` into the synthetic IMG starting at `blockIndex * blockSize`.
         * Used to populate sub-file content so [ImgSubFileStream] tests have something
         * meaningful to read.
         */
        fun payloadAtBlock(blockIndex: Int, bytes: ByteArray) = apply {
            payloads.add(PayloadSpec(blockIndex, bytes))
        }

        fun build(): ByteArray {
            return SyntheticImg(description, e1, e2, leadingPadding, entries, payloads).build()
        }
    }

    companion object {
        const val BLOCK: Int = ImgArchive.FAT_BLOCK_SIZE
        const val FAT_HEADER_OFFSET: Long = 0x200L
        const val FIRST_ENTRY_OFFSET: Long = FAT_HEADER_OFFSET + BLOCK

        private val DSKIMG_BYTES: ByteArray = "DSKIMG".encodeToByteArray()

        fun builder(): Builder {
            return Builder()
        }

        private fun writeAsciiPadded(dest: ByteArray, offset: Int, value: String, len: Int) {
            val bytes = value.encodeToByteArray()
            require(bytes.size <= len) { "field too long: '$value' > $len bytes" }
            bytes.copyInto(dest, destinationOffset = offset)
            for (i in bytes.size until len) {
                dest[offset + i] = ' '.code.toByte()
            }
        }

        private fun writeUInt16LE(dest: ByteArray, offset: Int, value: Int) {
            dest[offset] = (value and 0xFF).toByte()
            dest[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        }

        private fun writeInt32LE(dest: ByteArray, offset: Int, value: Int) {
            dest[offset] = (value and 0xFF).toByte()
            dest[offset + 1] = ((value ushr 8) and 0xFF).toByte()
            dest[offset + 2] = ((value ushr 16) and 0xFF).toByte()
            dest[offset + 3] = ((value ushr 24) and 0xFF).toByte()
        }
    }
}
