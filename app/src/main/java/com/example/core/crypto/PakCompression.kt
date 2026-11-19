package com.example.core.crypto

import com.github.luben.zstd.Zstd
import com.github.luben.zstd.ZstdDictCompress
import com.github.luben.zstd.ZstdDictDecompress
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

object PakCompression {

    const val CM_NONE = 0
    const val CM_ZLIB = 1
    const val CM_ZSTD = 6
    const val CM_ZSTD_DICT = 8
    const val CM_MASK = 15

    fun decompressBlock(
        block: ByteArray,
        zstdDictBytes: ByteArray?,
        compressionMethod: Int
    ): ByteArray {
        return when (compressionMethod) {
            CM_NONE -> block
            CM_ZLIB -> decompressZlib(block)
            CM_ZSTD -> decompressZstd(block, null)
            CM_ZSTD_DICT -> decompressZstd(block, zstdDictBytes)
            else -> throw IllegalArgumentException("Unknown compression method: $compressionMethod")
        }
    }

    fun bestCompress(
        chunk: ByteArray,
        compressionMethod: Int,
        zstdDictBytes: ByteArray? = null,
        fast: Boolean = false
    ): ByteArray {
        return when (compressionMethod) {
            CM_ZLIB -> compressZlib(chunk, if (fast) 1 else 9)
            CM_ZSTD, CM_ZSTD_DICT -> {
                val dict = if (compressionMethod == CM_ZSTD_DICT) zstdDictBytes else null
                val levels = if (fast) intArrayOf(6, 3, 1) else intArrayOf(19, 16, 13, 10, 7, 4, 1)
                for (lvl in levels) {
                    try {
                        return compressZstd(chunk, lvl, dict)
                    } catch (_: Throwable) {
                        continue
                    }
                }
                chunk
            }
            else -> chunk
        }
    }

    fun compressZlib(data: ByteArray, level: Int = Deflater.BEST_COMPRESSION): ByteArray {
        val deflater = Deflater(level)
        try {
            deflater.setInput(data)
            deflater.finish()
            val out = ByteArrayOutputStream(data.size)
            val buf = ByteArray(16384)
            while (!deflater.finished()) {
                val count = deflater.deflate(buf)
                if (count <= 0) break
                out.write(buf, 0, count)
            }
            return out.toByteArray()
        } finally {
            deflater.end()
        }
    }

    fun decompressZlib(data: ByteArray): ByteArray {
        val inflater = Inflater()
        return try {
            inflater.setInput(data)
            val out = ByteArrayOutputStream(data.size * 2)
            val buf = ByteArray(16384)
            while (!inflater.finished()) {
                val count = inflater.inflate(buf)
                if (count == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) break
                } else {
                    out.write(buf, 0, count)
                }
            }
            out.toByteArray()
        } catch (_: Exception) {
            // Exact Python behavior: return raw block on zlib.error
            data
        } finally {
            inflater.end()
        }
    }

    fun compressZstd(data: ByteArray, level: Int, dictBytes: ByteArray? = null): ByteArray {
        return if (dictBytes != null && dictBytes.isNotEmpty()) {
            ZstdDictCompress(dictBytes, level).use { dict ->
                Zstd.compress(data, dict)
            }
        } else {
            Zstd.compress(data, level)
        }
    }

    fun decompressZstd(data: ByteArray, dictBytes: ByteArray? = null): ByteArray {
        val rawSize = Zstd.decompressedSize(data)
        val targetSize = if (rawSize > 0 && rawSize < 256L * 1024L * 1024L) {
            rawSize.toInt()
        } else {
            maxOf(data.size * 6, 65536)
        }
        return if (dictBytes != null && dictBytes.isNotEmpty()) {
            val dst = ByteArray(targetSize)
            val written = Zstd.decompress(dst, data, dictBytes)
            if (Zstd.isError(written)) {
                throw IllegalStateException("ZSTD dict decompress error: ${Zstd.getErrorName(written)}")
            }
            dst.copyOfRange(0, written.toInt())
        } else {
            val dst = ByteArray(targetSize)
            val written = Zstd.decompress(dst, data)
            if (Zstd.isError(written)) {
                throw IllegalStateException("ZSTD decompress error: ${Zstd.getErrorName(written)}")
            }
            dst.copyOfRange(0, written.toInt())
        }
    }

    fun decryptedLooksValid(data: ByteArray, compressionMethod: Int): Boolean {
        if (compressionMethod == CM_ZLIB) {
            return data.size >= 2 &&
                (data[0].toInt() and 0xFF) == 0x78 &&
                (data[1].toInt() and 0xFF) in setOf(0x01, 0x9C, 0xDA)
        }
        if (compressionMethod == CM_ZSTD || compressionMethod == CM_ZSTD_DICT) {
            return data.size >= 4 &&
                data[0] == 0x28.toByte() &&
                data[1] == 0xB5.toByte() &&
                data[2] == 0x2F.toByte() &&
                data[3] == 0xFD.toByte()
        }
        return true
    }
}
