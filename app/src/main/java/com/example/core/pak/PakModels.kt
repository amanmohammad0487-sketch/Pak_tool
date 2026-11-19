package com.example.core.pak

import com.example.core.crypto.PakCompression
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BinaryReader(private val data: ByteArray, initialCursor: Int = 0) {
    var cursor: Int = initialCursor
        private set

    fun u1(): Int = data[cursor++].toInt() and 0xFF

    fun u4(): Long {
        val v = ByteBuffer.wrap(data, cursor, 4).order(ByteOrder.LITTLE_ENDIAN).int
        cursor += 4
        return v.toLong() and 0xFFFFFFFFL
    }

    fun i4(): Int {
        val v = ByteBuffer.wrap(data, cursor, 4).order(ByteOrder.LITTLE_ENDIAN).int
        cursor += 4
        return v
    }

    fun u8(): Long {
        val v = ByteBuffer.wrap(data, cursor, 8).order(ByteOrder.LITTLE_ENDIAN).long
        cursor += 8
        return v
    }

    fun bytes(n: Int): ByteArray {
        val out = data.copyOfRange(cursor, cursor + n)
        cursor += n
        return out
    }

    fun pakString(): String {
        val length = i4()
        if (length == 0) return ""
        require(length > 0) { "Negative/UTF-16 PAK string length not supported: $length" }
        val raw = bytes(length)
        var end = raw.size
        while (end > 0 && raw[end - 1].toInt() == 0) end--
        return String(raw, 0, end, Charsets.UTF_8)
    }
}

class BinaryWriter(initialCapacity: Int = 4096) {
    private val out = ByteArrayOutputStream(initialCapacity)

    fun u1(v: Int) {
        out.write(v and 0xFF)
    }

    fun u4(v: Long) {
        val b = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v.toInt()).array()
        out.write(b)
    }

    fun i4(v: Int) {
        val b = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()
        out.write(b)
    }

    fun u8(v: Long) {
        val b = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(v).array()
        out.write(b)
    }

    fun bytes(b: ByteArray) {
        out.write(b)
    }

    fun pakString(s: String) {
        if (s.isEmpty()) {
            i4(0)
            return
        }
        val b = s.toByteArray(Charsets.UTF_8)
        i4(b.size + 1)
        out.write(b)
        out.write(0)
    }

    fun toByteArray(): ByteArray = out.toByteArray()
}

data class PakCompressedBlock(
    var start: Long,
    var end: Long
) {
    fun copyBlock(): PakCompressedBlock = PakCompressedBlock(start, end)
}

class TencentPakEntry(
    var contentHash: ByteArray,
    var offset: Long,
    var uncompressedSize: Long,
    var compressionMethod: Int,
    var size: Long,
    var unk1: Int,
    var unk2: ByteArray,
    var compressedBlocks: MutableList<PakCompressedBlock>,
    var compressionBlockSize: Int,
    var encrypted: Boolean,
    var encryptionMethod: Int,
    var indexNewSep: Int
) {
    fun deepCopy(): TencentPakEntry = TencentPakEntry(
        contentHash = contentHash.copyOf(),
        offset = offset,
        uncompressedSize = uncompressedSize,
        compressionMethod = compressionMethod,
        size = size,
        unk1 = unk1,
        unk2 = unk2.copyOf(),
        compressedBlocks = compressedBlocks.map { it.copyBlock() }.toMutableList(),
        compressionBlockSize = compressionBlockSize,
        encrypted = encrypted,
        encryptionMethod = encryptionMethod,
        indexNewSep = indexNewSep
    )

    fun serialize(version: Int, writer: BinaryWriter) {
        writer.bytes(contentHash)
        writer.u8(offset)
        writer.u8(uncompressedSize)
        writer.u4(compressionMethod.toLong())
        writer.u8(size)
        if (version >= 5) {
            writer.u1(unk1)
            writer.bytes(unk2)
        }
        if (compressionMethod != PakCompression.CM_NONE && version >= 3) {
            writer.u4(compressedBlocks.size.toLong())
            for (blk in compressedBlocks) {
                writer.u8(blk.start)
                writer.u8(blk.end)
            }
        }
        if (version >= 4) {
            writer.u4(compressionBlockSize.toLong())
            writer.u1(if (encrypted) 1 else 0)
        }
        if (version >= 12) {
            writer.u4(encryptionMethod.toLong())
            writer.u4(indexNewSep.toLong())
        }
    }

    companion object {
        fun parse(reader: BinaryReader, version: Int): TencentPakEntry {
            val contentHash = reader.bytes(20)
            if (version <= 1) {
                reader.u8()
            }
            val offset = reader.u8()
            val uncompressedSize = reader.u8()
            val compressionMethod = (reader.u4().toInt()) and PakCompression.CM_MASK
            val size = reader.u8()
            val unk1 = if (version >= 5) reader.u1() else 0
            val unk2 = if (version >= 5) reader.bytes(20) else ByteArray(20)
            val blocks = if (compressionMethod != 0 && version >= 3) {
                val count = reader.u4().toInt()
                MutableList(count) {
                    PakCompressedBlock(reader.u8(), reader.u8())
                }
            } else {
                mutableListOf()
            }
            val compBlockSize = if (version >= 4) reader.u4().toInt() else 0
            val encrypted = if (version >= 4) reader.u1() == 1 else false
            val encMethod = if (version >= 12) reader.u4().toInt() else 0
            val indexNewSep = if (version >= 12) reader.u4().toInt() else 0
            return TencentPakEntry(
                contentHash = contentHash,
                offset = offset,
                uncompressedSize = uncompressedSize,
                compressionMethod = compressionMethod,
                size = size,
                unk1 = unk1,
                unk2 = unk2,
                compressedBlocks = blocks,
                compressionBlockSize = compBlockSize,
                encrypted = encrypted,
                encryptionMethod = encMethod,
                indexNewSep = indexNewSep
            )
        }
    }
}

class TencentPakInfo(
    val indexEncrypted: Boolean,
    val magic: Long,
    val version: Int,
    val indexHash: ByteArray,
    val indexSize: Long,
    val indexOffset: Long,
    val unk1: ByteArray,
    val packedKey: ByteArray,
    val packedIv: ByteArray,
    val packedIndexHash: ByteArray,
    val stemHash: Long,
    val unk2: Long,
    val contentOrgHash: ByteArray,
    val rawFooter: ByteArray
) {
    companion object {
        const val BASE_PAK_INFO_SIZE = 45

        fun memSize(version: Int): Int {
            val s7 = if (version >= 7) 32 else 0
            val s8 = if (version >= 8) 768 else 0
            val s9 = if (version >= 9) 8 else 0
            val s12 = if (version >= 12) 20 else 0
            return BASE_PAK_INFO_SIZE + s7 + s8 + s9 + s12
        }

        fun parse(tailBytes: ByteArray, keystream: LongArray): TencentPakInfo {
            require(tailBytes.size >= BASE_PAK_INFO_SIZE) { "PAK file too small for footer" }
            val baseStart = tailBytes.size - BASE_PAK_INFO_SIZE
            val baseReader = BinaryReader(tailBytes, baseStart)

            var indexEncrypted = ((baseReader.u1() xor keystream[3].toInt()) and 0xFF) == 1
            val magic = baseReader.u4() xor keystream[2]
            val version = baseReader.u4().toInt()
            val rawIndexHash = baseReader.bytes(20)
            val indexHash = if (version >= 6) {
                val keyBuf = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN).apply {
                    for (i in 4 until 9) putInt(keystream[i].toInt())
                }.array()
                ByteArray(20) { i -> (rawIndexHash[i].toInt() xor keyBuf[i].toInt()).toByte() }
            } else {
                ByteArray(0)
            }
            val indexSize = baseReader.u8() xor ((keystream[10] shl 32) or keystream[11])
            val indexOffset = baseReader.u8() xor ((keystream[0] shl 32) or keystream[1])
            if (version <= 3) indexEncrypted = false

            val fullSize = memSize(version)
            require(tailBytes.size >= fullSize) { "PAK file too small for v$version footer ($fullSize bytes)" }
            val fullStart = tailBytes.size - fullSize
            val rawFooter = tailBytes.copyOfRange(fullStart, tailBytes.size)
            val r = BinaryReader(rawFooter)

            val unk1 = if (version >= 7) {
                val encUnk = r.bytes(32)
                val keyBuf = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN).apply {
                    for (i in 7 until 15) putInt(keystream[i].toInt())
                }.array()
                ByteArray(32) { i -> (encUnk[i].toInt() xor keyBuf[i].toInt()).toByte() }
            } else ByteArray(0)

            val packedKey = if (version >= 8) r.bytes(256) else ByteArray(0)
            val packedIv = if (version >= 8) r.bytes(256) else ByteArray(0)
            val packedIndexHash = if (version >= 8) r.bytes(256) else ByteArray(0)
            val stemHash = if (version >= 9) r.u4() xor keystream[8] else 0L
            val unk2 = if (version >= 9) r.u4() xor keystream[9] else 0L
            val contentOrgHash = if (version >= 12) r.bytes(20) else ByteArray(0)

            return TencentPakInfo(
                indexEncrypted = indexEncrypted,
                magic = magic,
                version = version,
                indexHash = indexHash,
                indexSize = indexSize,
                indexOffset = indexOffset,
                unk1 = unk1,
                packedKey = packedKey,
                packedIv = packedIv,
                packedIndexHash = packedIndexHash,
                stemHash = stemHash,
                unk2 = unk2,
                contentOrgHash = contentOrgHash,
                rawFooter = rawFooter
            )
        }
    }
}

data class BlockResult(
    val fileName: String,
    val blockIndex: Int,
    val blockSizeBytes: Long,
    val fitted: Boolean,
    val compressionRatio: Float? = null
)

data class RepackSummary(
    val outputFileName: String,
    val outputAbsolutePath: String,
    val processedFiles: Int,
    val addedFiles: Int = 0,
    val skippedFiles: List<String> = emptyList(),
    val fittedBlocks: Int = 0,
    val skippedBlocks: Int = 0,
    val modeLabel: String = ""
)

data class UnpackSummary(
    val pakName: String,
    val version: Int,
    val mountPoint: String,
    val extractedCount: Int,
    val rawDumpedCount: Int,
    val outputDirPath: String,
    val indexCsvPath: String,
    val debugLogPath: String
)

enum class RepackResolveMode {
    FOLDER_WISE,
    INDEX_CSV,
    IN_PLACE_BLOCK
}
