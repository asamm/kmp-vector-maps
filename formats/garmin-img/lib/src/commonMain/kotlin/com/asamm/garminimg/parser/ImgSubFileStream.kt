/*
 * kmp-garmin-img — Kotlin Multiplatform parser & renderer for unencrypted Garmin IMG.
 *
 * Derived from JGarminImgParser and the Asamm fork libGarminImg. Distributed under
 * the GNU Lesser General Public License v2.1.
 */
package com.asamm.garminimg.parser

import com.asamm.garminimg.io.ImgRandomAccessReader

/**
 * Logical random-access view over a single sub-file inside an IMG container.
 *
 * Each sub-file occupies a contiguous run of fixed-size blocks starting at
 * [SubFileEntry.firstBlock] within the IMG. This stream presents that run as a
 * flat byte stream addressed from logical offset 0 to [SubFileEntry.fileSize] — 1.
 * Translation to the physical IMG offset is `firstBlock * blockSize + logicalPos`.
 *
 * # Block contiguity assumption
 *
 * mkgmap (and Garmin's own tooling) allocate sub-file blocks contiguously, then
 * record continuation FAT entries that just chain additional block-table chunks
 * for the same logical file. The Java predecessor `ImgSubFile.getAbsolutePosition`
 * relies on contiguity (it maps `(firstBloc + bloc) * blocSize`) and ignores
 * continuation records entirely. We mirror that assumption here. If a fragmented
 * IMG ever turns up in the wild, this class would silently produce garbage —
 * the fix would be to read the per-entry block table at FAT offset `0x20..0x1FF`
 * and consult it inside [seek]. Flagged here so the failure mode is recognisable.
 *
 * # Concurrency
 *
 * The stream tracks its own logical position and re-seeks the underlying
 * [container] before every read. Multiple streams sharing one container are
 * therefore safe for serial use — one stream's reads will not corrupt another
 * stream's logical position. They are *not* safe for concurrent use across
 * threads; the caller serialises.
 *
 * @property container the underlying IMG byte source
 * @property firstBlock the sub-file's first physical block index in the IMG
 * @property blockSize the IMG container's block size, in bytes
 * @property fileSize the sub-file's logical size, in bytes
 */
class ImgSubFileStream(
    private val container: ImgRandomAccessReader,
    val firstBlock: Int,
    val blockSize: Int,
    private val fileSize: Long,
) : ImgRandomAccessReader {

    init {
        require(firstBlock >= 0) { "firstBlock must be >= 0; got $firstBlock" }
        require(blockSize > 0) { "blockSize must be > 0; got $blockSize" }
        require(fileSize >= 0) { "fileSize must be >= 0; got $fileSize" }
    }

    /**
     * Cached logical position within the sub-file. We don't trust
     * `container.position()` because other streams over the same container can
     * move it between our reads.
     */
    private var logicalPos: Long = 0L

    /**
     * Physical offset within the IMG where this sub-file's logical 0 lives.
     */
    private val physicalBase: Long = firstBlock.toLong() * blockSize

    override val size: Long
        get() = fileSize

    override fun position(): Long {
        return logicalPos
    }

    override fun seek(pos: Long) {
        require(pos in 0L..fileSize) {
            "seek($pos) out of bounds for sub-file (firstBlock=$firstBlock, size=$fileSize)"
        }
        logicalPos = pos
    }

    override fun readByte(): Int {
        if (logicalPos >= fileSize) {
            throw IndexOutOfBoundsException(
                "readByte() at sub-file EOF: logicalPos=$logicalPos, size=$fileSize",
            )
        }
        ensureContainerPositioned()
        val b = container.readByte()
        logicalPos++
        return b
    }

    override fun readBlock(buffer: ByteArray) {
        if (logicalPos + buffer.size > fileSize) {
            throw IndexOutOfBoundsException(
                "readBlock(${buffer.size}) past sub-file EOF: " +
                    "logicalPos=$logicalPos, size=$fileSize",
            )
        }
        ensureContainerPositioned()
        container.readBlock(buffer)
        logicalPos += buffer.size
    }

    /**
     * Re-seeks the underlying container if its position is anywhere other than
     * where we expect. The check is cheap; the seek is no-op when we're already
     * positioned correctly (typical sequential read inside one sub-file).
     */
    private fun ensureContainerPositioned() {
        val expected = physicalBase + logicalPos
        if (container.position() != expected) {
            container.seek(expected)
        }
    }
}

/**
 * Common header at the start of every IMG sub-file.
 *
 * The sub-file's first two bytes carry [headerLength] (the byte length of the
 * sub-file-specific header that follows); byte 0xD carries a lock flag that is
 * non-zero only for encrypted/locked sub-files (out of scope — see PLAN.md).
 *
 * Mirrors the Java `ImgSubFile.superInit` read.
 */
data class SubFileCommonHeader(
    /**
     * Total length, in bytes, of the sub-file-specific header (including these
     * common bytes). Sub-file readers use this to know where the body starts.
     */
    val headerLength: Int,
)

/**
 * Reads the standard IMG sub-file common header (header length at offset 0,
 * lock-flag check at offset 0xD). Throws if the lock flag is non-zero — locked
 * sub-files require an unlock code we don't have, and aren't in scope.
 *
 * Leaves the read position at offset `2`, just past the [SubFileCommonHeader.headerLength]
 * field, so callers can immediately continue with sub-file-specific header fields.
 */
fun ImgRandomAccessReader.readSubFileCommonHeader(): SubFileCommonHeader {
    seek(0L)
    val headerLength = readUInt16()
    seek(0x0DL)
    val lockFlag = readByte()
    require(lockFlag == 0) {
        "Locked sub-file detected (lockFlag=$lockFlag at offset 0xD). Encrypted IMGs " +
            "are out of scope (see PLAN.md). Decrypt the source IMG first or use an " +
            "unencrypted variant."
    }
    seek(2L)
    return SubFileCommonHeader(headerLength = headerLength)
}
