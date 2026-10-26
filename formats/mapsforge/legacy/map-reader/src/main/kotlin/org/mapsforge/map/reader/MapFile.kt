/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2014-2015 Ludwig M Brinckmann
 * Copyright 2014-2020 devemux86
 * Copyright 2015-2016 lincomatic
 * Copyright 2016 bvgastel
 * Copyright 2017 linuskr
 * Copyright 2017 Gustl22
 * Copyright 2024-2025 Sublimis
 * Copyright 2026 Asamm Software
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.mapsforge.map.reader

import org.mapsforge.core.model.BoundingBox
import org.mapsforge.core.model.LatLong
import org.mapsforge.core.model.Tag
import org.mapsforge.core.model.Tile
import org.mapsforge.core.util.LatLongUtils
import org.mapsforge.core.util.MercatorProjection
import org.mapsforge.core.util.Parameters
import org.mapsforge.map.datastore.MapDataStore
import org.mapsforge.map.datastore.MapReadResult
import org.mapsforge.map.datastore.PoiWayBundle
import org.mapsforge.map.datastore.PointOfInterest
import org.mapsforge.map.datastore.Way
import org.mapsforge.map.reader.header.MapFileException
import org.mapsforge.map.reader.header.MapFileHeader
import org.mapsforge.map.reader.header.MapFileInfo
import org.mapsforge.map.reader.header.SubFileParameter
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.channels.FileChannel
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Reads binary map files. Reads may run concurrently; keep them few, each holds a block in memory.
 * Open, as Locus overrides `wayAsLabelTagFilter` in a subclass.
 *
 * See the [specification](https://github.com/mapsforge/mapsforge/blob/master/docs/Specification-Binary-Map-File.md).
 */
open class MapFile private constructor(opened: Opened, language: String?) : MapDataStore(language) {

    private val databaseIndexCache: IndexCache = opened.databaseIndexCache

    private val fileSize: Long = opened.fileSize

    private val inputChannel: FileChannel = opened.inputChannel

    val mapFileHeader: MapFileHeader = opened.mapFileHeader

    private val timestamp: Long = opened.timestamp

    private var zoomLevelMin: Byte = 0

    private var zoomLevelMax: Byte = Byte.MAX_VALUE

    val mapFileInfo: MapFileInfo
        get() = mapFileHeader.mapFileInfo

    /**
     * The languages the map file supports, null when it names none.
     */
    val mapLanguages: Array<String>?
        get() {
            val languagesPreference = mapFileInfo.languagesPreference
            // String.trim() and String.split() of Java: control characters trim, trailing empties drop
            if (languagesPreference != null
                && languagesPreference.trim { it <= ' ' }.isNotEmpty()
            ) {
                return languagesPreference.split(",").dropLastWhile { it.isEmpty() }.toTypedArray()
            }
            return null
        }

    /**
     * Opens `mapFile` with the default language; throws [MapFileException] when it is null or
     * invalid.
     */
    constructor(mapFile: File?) : this(mapFile, null)

    /**
     * Opens `mapFile`; throws [MapFileException] when it is null or invalid.
     */
    constructor(mapFile: File?, language: String?) : this(open(mapFile), language)

    constructor(mapFileInputStream: FileInputStream?) : this(mapFileInputStream, null)

    constructor(mapFileInputStream: FileInputStream?, language: String?) :
        this(mapFileInputStream, System.currentTimeMillis(), language)

    /**
     * Opens the map file of `mapFileInputStream`; throws [MapFileException] when it is null or
     * invalid.
     */
    constructor(mapFileInputStream: FileInputStream?, lastModified: Long, language: String?) :
        this(open(mapFileInputStream = mapFileInputStream, lastModified = lastModified), language)

    constructor(mapFileChannel: FileChannel?) : this(mapFileChannel, null)

    constructor(mapFileChannel: FileChannel?, language: String?) :
        this(mapFileChannel, System.currentTimeMillis(), language)

    /**
     * Opens the map file of `mapFileChannel`; throws [MapFileException] when it is null or
     * invalid.
     */
    constructor(mapFileChannel: FileChannel?, lastModified: Long, language: String?) :
        this(open(mapFileChannel = mapFileChannel, lastModified = lastModified), language)

    constructor(mapPath: String) : this(mapPath, null)

    constructor(mapPath: String, language: String?) : this(File(mapPath), language)

    override fun boundingBox(): BoundingBox {
        return mapFileInfo.boundingBox
    }

    override fun close() {
        closeFileChannel(indexCache = databaseIndexCache, channel = inputChannel)
    }

    private fun decodeWayNodesDoubleDelta(
        waySegment: Array<LatLong?>,
        tileLatitude: Double,
        tileLongitude: Double,
        readBuffer: ReadBuffer,
    ) {
        // get the first way node latitude offset (VBE-S)
        var wayNodeLatitude = tileLatitude + LatLongUtils.microdegreesToDegrees(readBuffer.readSignedInt())

        // get the first way node longitude offset (VBE-S)
        var wayNodeLongitude = tileLongitude + LatLongUtils.microdegreesToDegrees(readBuffer.readSignedInt())

        // store the first way node
        waySegment[0] = LatLong(wayNodeLatitude, wayNodeLongitude)

        var previousSingleDeltaLatitude = 0.0
        var previousSingleDeltaLongitude = 0.0

        for (wayNodesIndex in 1 until waySegment.size) {
            // get the way node latitude double-delta offset (VBE-S)
            val doubleDeltaLatitude = LatLongUtils.microdegreesToDegrees(readBuffer.readSignedInt())

            // get the way node longitude double-delta offset (VBE-S)
            val doubleDeltaLongitude = LatLongUtils.microdegreesToDegrees(readBuffer.readSignedInt())

            val singleDeltaLatitude = doubleDeltaLatitude + previousSingleDeltaLatitude
            val singleDeltaLongitude = doubleDeltaLongitude + previousSingleDeltaLongitude

            wayNodeLatitude = wayNodeLatitude + singleDeltaLatitude
            wayNodeLongitude = clampLongitude(wayNodeLongitude + singleDeltaLongitude)

            waySegment[wayNodesIndex] = LatLong(wayNodeLatitude, wayNodeLongitude)

            previousSingleDeltaLatitude = singleDeltaLatitude
            previousSingleDeltaLongitude = singleDeltaLongitude
        }
    }

    private fun decodeWayNodesSingleDelta(
        waySegment: Array<LatLong?>,
        tileLatitude: Double,
        tileLongitude: Double,
        readBuffer: ReadBuffer,
    ) {
        // get the first way node latitude single-delta offset (VBE-S)
        var wayNodeLatitude = tileLatitude + LatLongUtils.microdegreesToDegrees(readBuffer.readSignedInt())

        // get the first way node longitude single-delta offset (VBE-S)
        var wayNodeLongitude = tileLongitude + LatLongUtils.microdegreesToDegrees(readBuffer.readSignedInt())

        // store the first way node
        waySegment[0] = LatLong(wayNodeLatitude, wayNodeLongitude)

        for (wayNodesIndex in 1 until waySegment.size) {
            // get the way node latitude offset (VBE-S)
            wayNodeLatitude = wayNodeLatitude + LatLongUtils.microdegreesToDegrees(readBuffer.readSignedInt())

            // get the way node longitude offset (VBE-S)
            wayNodeLongitude = clampLongitude(
                wayNodeLongitude + LatLongUtils.microdegreesToDegrees(readBuffer.readSignedInt()),
            )

            waySegment[wayNodesIndex] = LatLong(wayNodeLatitude, wayNodeLongitude)
        }
    }

    /**
     * The file's modification time, the caller's `lastModified`, or the open time when none is
     * given; the same for every tile.
     */
    override fun getDataTimestamp(tile: Tile?): Long {
        return timestamp
    }

    private fun processBlock(
        queryParameters: QueryParameters,
        subFileParameter: SubFileParameter,
        boundingBox: BoundingBox,
        tileLatitude: Double,
        tileLongitude: Double,
        selector: Selector,
        readBuffer: ReadBuffer,
    ): PoiWayBundle? {
        if (!processBlockSignature(readBuffer)) {
            return null
        }

        val zoomTable = readZoomTable(subFileParameter, readBuffer)
        val zoomTableRow = queryParameters.queryZoomLevel - subFileParameter.zoomLevelMin
        val poisOnQueryZoomLevel = zoomTable[zoomTableRow][0]
        val waysOnQueryZoomLevel = zoomTable[zoomTableRow][1]

        // get the relative offset to the first stored way in the block
        var firstWayOffset = readBuffer.readUnsignedInt()
        if (firstWayOffset < 0) {
            LOGGER.warning(INVALID_FIRST_WAY_OFFSET + firstWayOffset)
            return null
        }

        // add the current buffer position to the relative first way offset
        firstWayOffset += readBuffer.bufferPosition
        if (firstWayOffset > readBuffer.bufferSize) {
            LOGGER.warning(INVALID_FIRST_WAY_OFFSET + firstWayOffset)
            return null
        }

        val filterRequired = queryParameters.queryZoomLevel > subFileParameter.baseZoomLevel

        val pois = processPOIs(
            tileLatitude = tileLatitude,
            tileLongitude = tileLongitude,
            numberOfPois = poisOnQueryZoomLevel,
            boundingBox = boundingBox,
            filterRequired = filterRequired,
            readBuffer = readBuffer,
        ) ?: return null

        val ways: List<Way>
        if (Selector.POIS == selector) {
            ways = emptyList()
        } else {
            // finished reading POIs, check if the current buffer position is valid
            if (readBuffer.bufferPosition > firstWayOffset) {
                LOGGER.warning("invalid buffer position: " + readBuffer.bufferPosition)
                return null
            }

            // move the pointer to the first way
            readBuffer.bufferPosition = firstWayOffset

            ways = processWays(
                queryParameters = queryParameters,
                numberOfWays = waysOnQueryZoomLevel,
                boundingBox = boundingBox,
                filterRequired = filterRequired,
                tileLatitude = tileLatitude,
                tileLongitude = tileLongitude,
                selector = selector,
                readBuffer = readBuffer,
            ) ?: return null
        }

        return PoiWayBundle(pois, ways)
    }

    /**
     * Checks the block signature, if present; false when it is invalid.
     */
    private fun processBlockSignature(readBuffer: ReadBuffer): Boolean {
        if (mapFileHeader.mapFileInfo.debugFile) {
            // get and check the block signature
            val signatureBlock = readBuffer.readUTF8EncodedString(SIGNATURE_LENGTH_BLOCK.toInt())
            if (signatureBlock == null
                || !signatureBlock.startsWith("###TileStart")
            ) {
                LOGGER.warning("invalid block signature: $signatureBlock")
                return false
            }
        }
        return true
    }

    @Throws(IOException::class)
    private fun processBlocks(
        queryParameters: QueryParameters,
        subFileParameter: SubFileParameter,
        boundingBox: BoundingBox,
        selector: Selector,
    ): MapReadResult? {
        val mapFileReadResult = MapReadResult()

        // read and process all blocks from top to bottom and from left to right
        for (row in queryParameters.fromBlockY..queryParameters.toBlockY) {
            for (column in queryParameters.fromBlockX..queryParameters.toBlockX) {
                // calculate the actual block number of the needed block in the file
                val blockNumber = row * subFileParameter.blocksWidth + column

                // get the current index entry
                val currentBlockIndexEntry = databaseIndexCache.getIndexEntry(subFileParameter, blockNumber)

                // get and check the current block pointer
                val currentBlockPointer = currentBlockIndexEntry and BITMASK_INDEX_OFFSET
                if (currentBlockPointer < 1
                    || currentBlockPointer > subFileParameter.subFileSize
                ) {
                    LOGGER.warning("invalid current block pointer: $currentBlockPointer")
                    LOGGER.warning("subFileSize: " + subFileParameter.subFileSize)
                    return null
                }

                val nextBlockPointer: Long
                // check if the current block is the last block in the file
                if (blockNumber + 1 == subFileParameter.numberOfBlocks) {
                    // set the next block pointer to the end of the file
                    nextBlockPointer = subFileParameter.subFileSize
                } else {
                    // get and check the next block pointer
                    nextBlockPointer = databaseIndexCache.getIndexEntry(subFileParameter, blockNumber + 1) and
                        BITMASK_INDEX_OFFSET
                    if (nextBlockPointer > subFileParameter.subFileSize) {
                        LOGGER.warning("invalid next block pointer: $nextBlockPointer")
                        LOGGER.warning("sub-file size: " + subFileParameter.subFileSize)
                        return null
                    }
                }

                // calculate the size of the current block
                val currentBlockSize = (nextBlockPointer - currentBlockPointer).toInt()
                if (currentBlockSize < 0) {
                    LOGGER.warning("current block size must not be negative: $currentBlockSize")
                    return null
                } else if (currentBlockSize == 0) {
                    // the current block is empty, continue with the next block
                    continue
                } else if (currentBlockSize > Parameters.MAXIMUM_BUFFER_SIZE) {
                    // the current block is too large, continue with the next block
                    LOGGER.warning("current block size too large: $currentBlockSize")
                    continue
                } else if (currentBlockPointer + currentBlockSize > fileSize) {
                    LOGGER.warning("current block largher than file size: $currentBlockSize")
                    return null
                }

                // read the current block into the buffer
                val readBuffer = ReadBuffer(inputChannel)
                if (!readBuffer.readFromFile(subFileParameter.startAddress + currentBlockPointer, currentBlockSize)) {
                    // skip the current block
                    LOGGER.warning("reading current block has failed: $currentBlockSize")
                    return null
                }

                // calculate the top-left coordinates of the underlying tile
                val tileLatitude = MercatorProjection.tileYToLatitude(
                    subFileParameter.boundaryTileTop + row,
                    subFileParameter.baseZoomLevel,
                )
                val tileLongitude = MercatorProjection.tileXToLongitude(
                    subFileParameter.boundaryTileLeft + column,
                    subFileParameter.baseZoomLevel,
                )

                try {
                    val poiWayBundle = processBlock(
                        queryParameters = queryParameters,
                        subFileParameter = subFileParameter,
                        boundingBox = boundingBox,
                        tileLatitude = tileLatitude,
                        tileLongitude = tileLongitude,
                        selector = selector,
                        readBuffer = readBuffer,
                    )
                    if (poiWayBundle != null) {
                        mapFileReadResult.add(poiWayBundle)
                    }
                } catch (e: ArrayIndexOutOfBoundsException) {
                    LOGGER.log(Level.SEVERE, e.toString(), e)
                }
            }
        }

        // the index entries' water flags stay unread: water tiles are not rendered
        return mapFileReadResult
    }

    private fun processPOIs(
        tileLatitude: Double,
        tileLongitude: Double,
        numberOfPois: Int,
        boundingBox: BoundingBox,
        filterRequired: Boolean,
        readBuffer: ReadBuffer,
    ): List<PointOfInterest>? {
        val pois = ArrayList<PointOfInterest>()
        val poiTags = mapFileHeader.mapFileInfo.poiTags

        var elementCounter = numberOfPois
        while (elementCounter != 0) {
            --elementCounter
            if (mapFileHeader.mapFileInfo.debugFile) {
                // get and check the POI signature
                val signaturePoi = readBuffer.readUTF8EncodedString(SIGNATURE_LENGTH_POI.toInt())
                if (signaturePoi == null
                    || !signaturePoi.startsWith("***POIStart")
                ) {
                    LOGGER.warning("invalid POI signature: $signaturePoi")
                    return null
                }
            }

            // get the POI latitude offset (VBE-S)
            val latitude = tileLatitude + LatLongUtils.microdegreesToDegrees(readBuffer.readSignedInt())

            // get the POI longitude offset (VBE-S)
            val longitude = tileLongitude + LatLongUtils.microdegreesToDegrees(readBuffer.readSignedInt())

            // get the special byte which encodes multiple flags
            val specialByte = readBuffer.readByte()

            // bit 1-4 represent the layer
            val layer = ((specialByte.toInt() and POI_LAYER_BITMASK) ushr POI_LAYER_SHIFT).toByte()
            // bit 5-8 represent the number of tag IDs
            val numberOfTags = (specialByte.toInt() and POI_NUMBER_OF_TAGS_BITMASK).toByte()

            // get the tags from IDs (VBE-U)
            val tags = readBuffer.readTags(poiTags, numberOfTags) ?: return null

            // get the feature bitmask (1 byte)
            val featureByte = readBuffer.readByte().toInt()

            // bit 1-3 enable optional features
            val featureName = featureByte and POI_FEATURE_NAME != 0
            val featureHouseNumber = featureByte and POI_FEATURE_HOUSE_NUMBER != 0
            val featureElevation = featureByte and POI_FEATURE_ELEVATION != 0

            // check if the POI has a name
            if (featureName) {
                tags.add(Tag(TAG_KEY_NAME, extractLocalized(readBuffer.readUTF8EncodedString())))
            }

            // check if the POI has a house number
            if (featureHouseNumber) {
                tags.add(Tag(TAG_KEY_HOUSE_NUMBER, readBuffer.readUTF8EncodedString()))
            }

            // check if the POI has an elevation
            if (featureElevation) {
                tags.add(Tag(TAG_KEY_ELE, readBuffer.readSignedInt().toString()))
            }

            val position = LatLong(latitude, longitude)
            // depending on the zoom level configuration the poi can lie outside
            // the tile requested, we filter them out here
            if (!filterRequired
                || boundingBox.contains(position)
            ) {
                pois.add(PointOfInterest(layer, tags, position))
            }
        }

        return pois
    }

    private fun processWayDataBlock(
        tileLatitude: Double,
        tileLongitude: Double,
        doubleDeltaEncoding: Boolean,
        readBuffer: ReadBuffer,
    ): Array<Array<LatLong>>? {
        // get and check the number of way coordinate blocks (VBE-U)
        val numberOfWayCoordinateBlocks = readBuffer.readUnsignedInt()
        if (numberOfWayCoordinateBlocks < 1
            || numberOfWayCoordinateBlocks > Short.MAX_VALUE
        ) {
            LOGGER.warning("invalid number of way coordinate blocks: $numberOfWayCoordinateBlocks")
            return null
        }

        // create the array which will store the different way coordinate blocks
        val wayCoordinates = arrayOfNulls<Array<LatLong?>>(numberOfWayCoordinateBlocks)

        // read the way coordinate blocks
        for (coordinateBlock in 0 until numberOfWayCoordinateBlocks) {
            // get and check the number of way nodes (VBE-U)
            val numberOfWayNodes = readBuffer.readUnsignedInt()
            if (numberOfWayNodes < 2
                || numberOfWayNodes > Short.MAX_VALUE
            ) {
                LOGGER.warning("invalid number of way nodes: $numberOfWayNodes")
                // the buffer position is lost: the rest of the block is unreliable, usually dropped
                return null
            }

            // create the array which will store the current way segment
            val waySegment = arrayOfNulls<LatLong>(numberOfWayNodes)

            if (doubleDeltaEncoding) {
                decodeWayNodesDoubleDelta(waySegment, tileLatitude, tileLongitude, readBuffer)
            } else {
                decodeWayNodesSingleDelta(waySegment, tileLatitude, tileLongitude, readBuffer)
            }

            wayCoordinates[coordinateBlock] = waySegment
        }

        // every block and node is set above
        @Suppress("UNCHECKED_CAST")
        return wayCoordinates as Array<Array<LatLong>>
    }

    private fun processWays(
        queryParameters: QueryParameters,
        numberOfWays: Int,
        boundingBox: BoundingBox,
        filterRequired: Boolean,
        tileLatitude: Double,
        tileLongitude: Double,
        selector: Selector,
        readBuffer: ReadBuffer,
    ): List<Way>? {
        val ways = ArrayList<Way>()
        val wayTags = mapFileHeader.mapFileInfo.wayTags

        val wayFilterBbox = boundingBox.extendMeters(wayFilterDistance)

        var elementCounter = numberOfWays
        while (elementCounter != 0) {
            --elementCounter
            if (mapFileHeader.mapFileInfo.debugFile) {
                // get and check the way signature
                val signatureWay = readBuffer.readUTF8EncodedString(SIGNATURE_LENGTH_WAY.toInt())
                if (signatureWay == null
                    || !signatureWay.startsWith("---WayStart")
                ) {
                    LOGGER.warning("invalid way signature: $signatureWay")
                    return null
                }
            }

            // get the size of the way (VBE-U)
            val wayDataSize = readBuffer.readUnsignedInt()
            if (wayDataSize < 0) {
                LOGGER.warning("invalid way data size: $wayDataSize")
                return null
            }

            if (queryParameters.useTileBitmask) {
                // get the way tile bitmask (2 bytes)
                val tileBitmask = readBuffer.readShort()
                // check if the way is inside the requested tile
                if (queryParameters.queryTileBitmask and tileBitmask == 0) {
                    // skip the rest of the way and continue with the next way
                    readBuffer.skipBytes(wayDataSize - 2)
                    continue
                }
            } else {
                // ignore the way tile bitmask (2 bytes)
                readBuffer.skipBytes(2)
            }

            // get the special byte which encodes multiple flags
            val specialByte = readBuffer.readByte()

            // bit 1-4 represent the layer
            val layer = ((specialByte.toInt() and WAY_LAYER_BITMASK) ushr WAY_LAYER_SHIFT).toByte()
            // bit 5-8 represent the number of tag IDs
            val numberOfTags = (specialByte.toInt() and WAY_NUMBER_OF_TAGS_BITMASK).toByte()

            // get the tags from IDs (VBE-U)
            val tags = readBuffer.readTags(wayTags, numberOfTags) ?: return null

            // get the feature bitmask (1 byte)
            val featureByte = readBuffer.readByte().toInt()

            // bit 1-6 enable optional features
            val featureName = featureByte and WAY_FEATURE_NAME != 0
            val featureHouseNumber = featureByte and WAY_FEATURE_HOUSE_NUMBER != 0
            val featureRef = featureByte and WAY_FEATURE_REF != 0
            val featureLabelPosition = featureByte and WAY_FEATURE_LABEL_POSITION != 0
            val featureWayDataBlocksByte = featureByte and WAY_FEATURE_DATA_BLOCKS_BYTE != 0
            val featureWayDoubleDeltaEncoding = featureByte and WAY_FEATURE_DOUBLE_DELTA_ENCODING != 0

            // check if the way has a name
            if (featureName) {
                tags.add(Tag(TAG_KEY_NAME, extractLocalized(readBuffer.readUTF8EncodedString())))
            }

            // check if the way has a house number
            if (featureHouseNumber) {
                tags.add(Tag(TAG_KEY_HOUSE_NUMBER, readBuffer.readUTF8EncodedString()))
            }

            // check if the way has a reference
            if (featureRef) {
                tags.add(Tag(TAG_KEY_REF, readBuffer.readUTF8EncodedString()))
            }

            val labelPosition = if (featureLabelPosition) {
                readOptionalLabelPosition(readBuffer)
            } else {
                null
            }

            val wayDataBlocks = readOptionalWayDataBlocksByte(featureWayDataBlocksByte, readBuffer)
            if (wayDataBlocks < 1) {
                LOGGER.warning("invalid number of way data blocks: $wayDataBlocks")
                return null
            }

            repeat(wayDataBlocks) {
                val wayNodes = processWayDataBlock(
                    tileLatitude = tileLatitude,
                    tileLongitude = tileLongitude,
                    doubleDeltaEncoding = featureWayDoubleDeltaEncoding,
                    readBuffer = readBuffer,
                ) ?: return@repeat
                if (filterRequired
                    && wayFilterEnabled
                    && !wayFilterBbox.intersectsArea(wayNodes)
                ) {
                    return@repeat
                }
                if (Selector.ALL == selector
                    || featureName
                    || featureHouseNumber
                    || featureRef
                    || wayAsLabelTagFilter(tags)
                ) {
                    var labelLatLong: LatLong? = null
                    if (labelPosition != null) {
                        val first = wayNodes[0][0]
                        labelLatLong = LatLong(
                            first.latitude + LatLongUtils.microdegreesToDegrees(labelPosition[1]),
                            first.longitude + LatLongUtils.microdegreesToDegrees(labelPosition[0]),
                        )
                    }
                    ways.add(Way(layer, tags, wayNodes, labelLatLong))
                }
            }
        }

        return ways
    }

    /**
     * Reads only named items for a tile.
     */
    override fun readNamedItems(tile: Tile): MapReadResult? {
        return readMapData(tile, tile, Selector.NAMED)
    }

    /**
     * Reads named items for the area from `upperLeft` to `lowerRight`, which must not lie above or
     * left of `upperLeft`.
     */
    override fun readNamedItems(upperLeft: Tile, lowerRight: Tile): MapReadResult? {
        return readMapData(upperLeft, lowerRight, Selector.NAMED)
    }

    /**
     * Reads all map data of the tile at its zoom level.
     */
    override fun readMapData(tile: Tile): MapReadResult? {
        return readMapData(tile, tile, Selector.ALL)
    }

    /**
     * Reads all map data for the area from `upperLeft` to `lowerRight`, which must not lie above
     * or left of `upperLeft`.
     */
    override fun readMapData(upperLeft: Tile, lowerRight: Tile): MapReadResult? {
        return readMapData(upperLeft, lowerRight, Selector.ALL)
    }

    private fun readMapData(upperLeft: Tile, lowerRight: Tile, selector: Selector): MapReadResult? {
        try {
            val queryParameters = QueryParameters()
            queryParameters.queryZoomLevel = mapFileHeader.getQueryZoomLevel(upperLeft.zoomLevel, mapCallback).toInt()

            // get and check the sub-file for the query zoom level
            val subFileParameter = mapFileHeader.getSubFileParameter(queryParameters.queryZoomLevel)
            if (subFileParameter == null) {
                LOGGER.warning("no sub-file for zoom level: " + queryParameters.queryZoomLevel)
                return null
            }

            queryParameters.calculateBaseTiles(upperLeft, lowerRight, subFileParameter)
            queryParameters.calculateBlocks(subFileParameter)

            return processBlocks(
                queryParameters = queryParameters,
                subFileParameter = subFileParameter,
                boundingBox = Tile.getBoundingBox(upperLeft, lowerRight),
                selector = selector,
            )
        } catch (e: IOException) {
            LOGGER.log(Level.SEVERE, e.toString(), e)
            return null
        }
    }

    // label position offsets: [0] longitude, [1] latitude (VBE-S each, latitude first)
    private fun readOptionalLabelPosition(readBuffer: ReadBuffer): IntArray {
        val labelPosition = IntArray(2)

        // get the label position latitude offset (VBE-S)
        labelPosition[1] = readBuffer.readSignedInt()

        // get the label position longitude offset (VBE-S)
        labelPosition[0] = readBuffer.readSignedInt()

        return labelPosition
    }

    private fun readOptionalWayDataBlocksByte(featureWayDataBlocksByte: Boolean, readBuffer: ReadBuffer): Int {
        if (featureWayDataBlocksByte) {
            // get and check the number of way data blocks (VBE-U)
            return readBuffer.readUnsignedInt()
        }
        // only one way data block exists
        return 1
    }

    /**
     * Reads only POI data for the tile.
     */
    override fun readPoiData(tile: Tile): MapReadResult? {
        return readMapData(tile, tile, Selector.POIS)
    }

    /**
     * Reads POI data for the area from `upperLeft` to `lowerRight`, using the map file's block
     * layout.
     */
    override fun readPoiData(upperLeft: Tile, lowerRight: Tile): MapReadResult? {
        return readMapData(upperLeft, lowerRight, Selector.POIS)
    }

    private fun readZoomTable(subFileParameter: SubFileParameter, readBuffer: ReadBuffer): Array<IntArray> {
        val rows = subFileParameter.zoomLevelMax - subFileParameter.zoomLevelMin + 1
        val zoomTable = Array(rows) { IntArray(2) }

        var cumulatedNumberOfPois = 0
        var cumulatedNumberOfWays = 0

        for (row in 0 until rows) {
            cumulatedNumberOfPois += readBuffer.readUnsignedInt()
            cumulatedNumberOfWays += readBuffer.readUnsignedInt()

            zoomTable[row][0] = cumulatedNumberOfPois
            zoomTable[row][1] = cumulatedNumberOfWays
        }

        return zoomTable
    }

    /**
     * Restricts the data returned to the zoom range, for use in a multi-map data store.
     */
    fun restrictToZoomRange(minZoom: Byte, maxZoom: Byte) {
        zoomLevelMax = maxZoom
        zoomLevelMin = minZoom
    }

    override fun startPosition(): LatLong {
        return mapFileInfo.startPosition ?: mapFileInfo.boundingBox.centerPoint
    }

    override fun startZoomLevel(): Byte {
        return mapFileInfo.startZoomLevel ?: DEFAULT_START_ZOOM_LEVEL
    }

    override fun supportsTile(tile: Tile): Boolean {
        return tile.boundingBox.intersects(mapFileInfo.boundingBox)
            && tile.zoomLevel >= zoomLevelMin
            && tile.zoomLevel <= zoomLevelMax
    }

    override fun supportsFullTile(tile: Tile): Boolean {
        return supportsFullArea(tile.boundingBox, tile.zoomLevel)
    }

    override fun supportsArea(boundingBox: BoundingBox, zoomLevel: Byte): Boolean {
        return boundingBox.intersects(mapFileInfo.boundingBox)
            && zoomLevel >= zoomLevelMin
            && zoomLevel <= zoomLevelMax
    }

    override fun supportsFullArea(boundingBox: BoundingBox, zoomLevel: Byte): Boolean {
        val bbox1 = mapFileInfo.boundingBox
        return bbox1.intersects(boundingBox)
            && zoomLevel >= zoomLevelMin
            && zoomLevel <= zoomLevelMax
            && bbox1.contains(boundingBox.maxLatitude, boundingBox.maxLongitude)
            && bbox1.contains(boundingBox.minLatitude, boundingBox.minLongitude)
            && bbox1.contains(boundingBox.maxLatitude, boundingBox.minLongitude)
            && bbox1.contains(boundingBox.minLatitude, boundingBox.maxLongitude)
    }

    /**
     * The open channel and what its header gave.
     */
    private class Opened(val inputChannel: FileChannel, val timestamp: Long) {

        val fileSize: Long = inputChannel.size()

        val mapFileHeader = MapFileHeader().also {
            it.readHeader(ReadBuffer(inputChannel), fileSize)
        }

        val databaseIndexCache = IndexCache(inputChannel, INDEX_CACHE_SIZE)
    }

    /**
     * The data subset a read returns.
     */
    private enum class Selector {

        ALL,

        POIS,

        /**
         * POI data and ways that have a name.
         */
        NAMED,
    }

    companion object {

        private val LOGGER: Logger = Logger.getLogger(MapFile::class.java.name)

        // extracts the block offset from an index entry
        private const val BITMASK_INDEX_OFFSET = 0x7FFFFFFFFFL

        private const val DEFAULT_START_ZOOM_LEVEL: Byte = 12

        // index blocks the index cache keeps
        private const val INDEX_CACHE_SIZE = 64

        private const val INVALID_FIRST_WAY_OFFSET = "invalid first way offset: "

        private const val POI_FEATURE_ELEVATION = 0x20

        private const val POI_FEATURE_HOUSE_NUMBER = 0x40

        private const val POI_FEATURE_NAME = 0x80

        private const val POI_LAYER_BITMASK = 0xf0

        private const val POI_LAYER_SHIFT = 4

        private const val POI_NUMBER_OF_TAGS_BITMASK = 0x0f

        // lengths of the debug signatures at the start of each block, POI and way
        private const val SIGNATURE_LENGTH_BLOCK: Byte = 32

        private const val SIGNATURE_LENGTH_POI: Byte = 32

        private const val SIGNATURE_LENGTH_WAY: Byte = 32

        private const val TAG_KEY_ELE = "ele"

        private const val TAG_KEY_HOUSE_NUMBER = "addr:housenumber"

        private const val TAG_KEY_NAME = "name"

        private const val TAG_KEY_REF = "ref"

        private const val WAY_FEATURE_DATA_BLOCKS_BYTE = 0x08

        private const val WAY_FEATURE_DOUBLE_DELTA_ENCODING = 0x04

        private const val WAY_FEATURE_HOUSE_NUMBER = 0x40

        private const val WAY_FEATURE_LABEL_POSITION = 0x10

        private const val WAY_FEATURE_NAME = 0x80

        private const val WAY_FEATURE_REF = 0x20

        private const val WAY_LAYER_BITMASK = 0xf0

        private const val WAY_LAYER_SHIFT = 4

        private const val WAY_NUMBER_OF_TAGS_BITMASK = 0x0f

        /**
         * Way filtering returns only the ways relevant to the requested tile: faster, but it can
         * clip lines, mostly at high zoom levels. Turn it off or raise [wayFilterDistance], the
         * margin around the tile in meters, to reduce clipping.
         */
        @JvmField
        var wayFilterEnabled = true

        @JvmField
        var wayFilterDistance = 20

        private fun open(mapFile: File?): Opened {
            if (mapFile == null) {
                throw MapFileException("mapFile must not be null")
            }
            return open(
                channel = {
                    // check if the file exists and is readable
                    if (!mapFile.exists()) {
                        throw MapFileException("file does not exist: $mapFile")
                    } else if (!mapFile.isFile) {
                        throw MapFileException("not a file: $mapFile")
                    } else if (!mapFile.canRead()) {
                        throw MapFileException("cannot read file: $mapFile")
                    }
                    // the stream closes with its channel
                    FileInputStream(mapFile).channel
                },
                timestamp = { mapFile.lastModified() },
            )
        }

        private fun open(mapFileInputStream: FileInputStream?, lastModified: Long): Opened {
            if (mapFileInputStream == null) {
                throw MapFileException("mapFileInputStream must not be null")
            }
            return open(channel = { mapFileInputStream.channel }, timestamp = { lastModified })
        }

        private fun open(mapFileChannel: FileChannel?, lastModified: Long): Opened {
            if (mapFileChannel == null) {
                throw MapFileException("mapFileChannel must not be null")
            }
            return open(channel = { mapFileChannel }, timestamp = { lastModified })
        }

        // opens the channel and reads the header; on any failure closes the channel and throws a
        // MapFileException with the cause's text
        private fun open(channel: () -> FileChannel, timestamp: () -> Long): Opened {
            var inputChannel: FileChannel? = null
            try {
                inputChannel = channel()
                return Opened(inputChannel = inputChannel, timestamp = timestamp())
            } catch (e: Exception) {
                closeFileChannel(indexCache = null, channel = inputChannel)
                throw MapFileException(e.toString())
            }
        }

        // destroys the cache and closes the channel; failures are logged, not thrown
        private fun closeFileChannel(indexCache: IndexCache?, channel: FileChannel?) {
            try {
                indexCache?.destroy()
                channel?.close()
            } catch (e: Exception) {
                LOGGER.log(Level.SEVERE, e.toString(), e)
            }
        }

    }
}

// decoding near the date line can land slightly outside [-180, 180]; top level, so the per-node
// call is a direct static call
private fun clampLongitude(longitude: Double): Double {
    if (longitude < LatLongUtils.LONGITUDE_MIN
        && LatLongUtils.LONGITUDE_MIN - longitude < 0.001
    ) {
        return LatLongUtils.LONGITUDE_MIN
    } else if (longitude > LatLongUtils.LONGITUDE_MAX
        && longitude - LatLongUtils.LONGITUDE_MAX < 0.001
    ) {
        return LatLongUtils.LONGITUDE_MAX
    }
    return longitude
}
