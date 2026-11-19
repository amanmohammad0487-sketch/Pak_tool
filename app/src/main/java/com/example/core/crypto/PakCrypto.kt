package com.example.core.crypto

import com.example.core.pak.TencentPakInfo
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object PakCrypto {

    const val EM_NONE = 0
    const val EM_SIMPLE1 = 1
    const val EM_SM4_2 = 2
    const val EM_SM4_4 = 4
    const val EM_SIMPLE2 = 16
    const val EM_UNKNOWN_17 = 17
    const val EM_SM4_NEW_BASE = 31
    const val EM_SM4_NEW_MASK = 31.inv()

    const val SIMPLE1_DECRYPT_KEY = 121
    const val SIMPLE2_BLOCK_SIZE = 16
    private val SIMPLE2_KEY_LE = hexToBytes("E55B4ED1")

    private val ZUC_KEY = hexToBytes("01010101010101010101010101010101")
    private val ZUC_IV = hexToBytes("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF")

    val RSA_MOD_1 = hexToBytes(
        "CBE8B9F2504050EF9831B719E9A6249A6D238505ADE909BDE78C180DED6072A0" +
            "C3347B8AF4780E1F212D952D82D4BF7F233C1ECA499E1F9D9A85B4FAD759F54B" +
            "ABC1666C5DE411EA9E4B2374425DD6C6F54333BBC8F2610FE6063E4D0D6C21A6" +
            "71A8F7C3740555E5DC06D4E1691C456DB4116C0C012BF7B206E8311AAAEC6899" +
            "52BF804EF638F09D5822B4117B114208F14DEB459E80CB770E5B0D7978E21F5E" +
            "6CED4999D3583108221A7AB28B960277ADB5690A332784019D9C195BE4EA9EA0" +
            "A09459010F236465DE0D59C3EF7324E954E1118D93EE19F299760C2CDB963CE8" +
            "7973EA5ECC9BBE81C27D4C7C8572AC07E9BCEAC9BD72AB7A56A3C0AD736ABCE4"
    )

    val RSA_MOD_2 = hexToBytes(
        "7F58E8A39A4DA4E87357DDD650EAA16D3B5CE95B213D1030A662566444796A78A" +
            "84AE9AC3DBFFDE7F41094896696835DAF13B89E6EC2B84963B1B1BAF7151DA24" +
            "5C3FBFAE2A6AE18B2684D03F9229DE2C91440F2A3A3BCDE1E5680C16722A8803" +
            "9C73560D5D43F4B6562C2EEA5B1D926D86B51108A2643C70FB74D6442CE3A083" +
            "39B8FD8F660AE88129B7AB8C46F2FA58124485CCCB1E987B05A6DA65A01858ED" +
            "3F89905449AE42BB07290FCB9994BF22E26610BCABB9804783A3B9587917F3D9" +
            "7316EDDA15C5E13F79066407B55A93B291B68A4AC42A98D6E35FED84B14A792D" +
            "154E62028DDAD20FC301951E5924BE9AD62FB719DD94CC30CAB871BEC4377A8"
    )

    private const val SM4_SECRET_4 = "eb691efea914241317a8"
    private const val SM4_SECRET_2 = "Q0hVTKey\$as*1ZFlQCiA"
    private val SM4_SECRET_NEW = listOf(
        "xG2qW5lP7lV2iN5fN5pG",
        "xT1cJ6dL5wC0kK1rB4dK",
        "qC4jS5bZ6fL5xE6nD4zA",
        "gD4jQ2aL3bS3lC3xT0iW",
        "xU1yQ8wE9zY3gZ3bT5aE",
        "uQ3cO2dX7xY4xU7gH7iS",
        "gW1fR0jK6wQ4oN0oK1kZ",
        "aJ4pV7iZ7pU4wP2aC2cZ",
        "cX6jT3cM2oT3vK0kJ1qN",
        "iT2vS0cS6yT6cZ1sE1lO",
        "hM1pH9iY8wM9hT4lN5uJ",
        "kG6bC8jK0fL0dE4sH4mL",
        "dB6lB3vE0eZ8wM8rI0aC",
        "tP7sP7nI9rA2vQ4cV5yQ",
        "aT0cL1yN4pT3sZ7eM2vY",
        "uV6fU8fC9zN3mP5dH8mN",
        "rT6aQ6oZ1yM0gO5tO1aN",
        "jU5bH7lQ0fM9hK2kI0oF",
        "iQ0eM0mJ7uT0kV6kL5zY",
        "rG0lR2rZ5vM6mW5lM1rR",
        "wD2rP3lP9xF4mE1eC5jS",
        "fO3kW1fE6eD0pU1kY7xK",
        "nE1jQ6qK5tW7rM8iD5vV"
    )

    private val sm4Cache = ConcurrentHashMap<String, CustomSm4>()
    private val blockPermCache = ConcurrentHashMap<Long, IntArray>()
    private val cachedZucKeystream: LongArray by lazy {
        Zuc128.generateKeystreamWords(ZUC_KEY, ZUC_IV, 16)
    }

    fun zucKeystream(): LongArray = cachedZucKeystream

    fun sha1(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-1").digest(data)

    fun hexToBytes(hex: String): ByteArray {
        val clean = hex.trim()
        require(clean.length % 2 == 0) { "Hex string must have even length" }
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    fun alignUp(x: Int, n: Int): Int = ((x + n - 1) / n) * n
    fun alignUpLong(x: Long, n: Long): Long = ((x + n - 1L) / n) * n

    fun padToN(data: ByteArray, n: Int): ByteArray {
        val padding = n - (data.size % n)
        if (padding == n) return data
        return data + ByteArray(padding)
    }

    private fun xorXor(buffer: ByteArray, x: ByteArray): ByteArray =
        ByteArray(buffer.size) { i -> (buffer[i].toInt() xor x[i % x.size].toInt()).toByte() }

    private fun hashHash(buffer: ByteArray, n: Int): ByteArray {
        val digest = sha1(buffer)
        val out = ByteArray(n)
        var off = 0
        while (off < n) {
            val len = minOf(digest.size, n - off)
            System.arraycopy(digest, 0, out, off, len)
            off += len
        }
        return out
    }

    private fun meowMeow(buffer: ByteArray): ByteArray {
        if (buffer.size < 43) return ByteArray(0)
        val digestSize = 20
        var x1 = buffer.copyOfRange(1, 1 + digestSize)
        var x2 = buffer.copyOfRange(1 + digestSize, buffer.size)
        x1 = xorXor(x1, hashHash(x2, x1.size))
        x2 = xorXor(x2, hashHash(x1, x2.size))
        val part1 = x2.copyOfRange(0, digestSize)
        val m = x2.copyOfRange(digestSize, x2.size)
        val expectedZeroHash = sha1(ByteArray(digestSize))
        if (!part1.contentEquals(expectedZeroHash)) return ByteArray(0)
        val firstNonZero = m.indexOfFirst { it.toInt() != 0 }
        if (firstNonZero < 0 || firstNonZero + 1 > m.size) return ByteArray(0)
        return m.copyOfRange(firstNonZero + 1, m.size)
    }

    fun rsaExtract(signature: ByteArray, modulus: ByteArray): ByteArray {
        val c = BigInteger(1, signature.reversedArray())
        val n = BigInteger(1, modulus.reversedArray())
        val e = BigInteger.valueOf(65537L)
        val mBig = c.modPow(e, n)
        val beBytes = mBig.toByteArray()
        val le256 = ByteArray(256)
        // Copy big-endian magnitude into little-endian 256-byte array
        var srcIdx = beBytes.size - 1
        var dstIdx = 0
        while (srcIdx >= 0 && dstIdx < 256) {
            le256[dstIdx++] = beBytes[srcIdx--]
        }
        // rstrip 0x00
        var end = le256.size
        while (end > 0 && le256[end - 1].toInt() == 0) {
            end--
        }
        val stripped = le256.copyOfRange(0, end)
        return meowMeow(padToN(stripped, 4))
    }

    fun isSimple1Method(method: Int): Boolean = method == EM_SIMPLE1
    fun isSimple2Method(method: Int): Boolean = method == EM_SIMPLE2
    fun isSm4Method(method: Int): Boolean =
        method == EM_SM4_2 || method == EM_SM4_4 || method == EM_UNKNOWN_17 ||
            (method and EM_SM4_NEW_MASK) != 0

    fun alignEncryptedContentSize(n: Int, encryptionMethod: Int): Int = when {
        isSimple2Method(encryptionMethod) -> alignUp(n, SIMPLE2_BLOCK_SIZE)
        isSm4Method(encryptionMethod) -> alignUp(n, CustomSm4.BLOCK_LENGTH)
        else -> n
    }

    fun decryptSimple1(data: ByteArray): ByteArray =
        ByteArray(data.size) { i -> (data[i].toInt() xor SIMPLE1_DECRYPT_KEY).toByte() }

    fun decryptSimple2(ciphertext: ByteArray): ByteArray {
        require(ciphertext.size % SIMPLE2_BLOCK_SIZE == 0) {
            "SIMPLE2 ciphertext must be 16-byte aligned"
        }
        val initialKey = ByteBuffer.wrap(SIMPLE2_KEY_LE).order(ByteOrder.LITTLE_ENDIAN).int
        var rolling = initialKey
        val inBuf = ByteBuffer.wrap(ciphertext).order(ByteOrder.LITTLE_ENDIAN)
        val out = ByteArray(ciphertext.size)
        val outBuf = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
        repeat(ciphertext.size / 4) {
            val x = inBuf.int
            rolling = rolling xor x
            outBuf.putInt(rolling)
        }
        return out
    }

    fun encryptSimple2(plaintext: ByteArray): ByteArray {
        val padded = padToN(plaintext, SIMPLE2_BLOCK_SIZE)
        var rolling = ByteBuffer.wrap(SIMPLE2_KEY_LE).order(ByteOrder.LITTLE_ENDIAN).int
        val inBuf = ByteBuffer.wrap(padded).order(ByteOrder.LITTLE_ENDIAN)
        val out = ByteArray(padded.size)
        val outBuf = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
        repeat(padded.size / 4) {
            val x = inBuf.int
            val c = rolling xor x
            outBuf.putInt(c)
            rolling = rolling xor c
        }
        return out
    }

    fun stemOfPath(path: String): String {
        val normalized = path.replace('\\', '/')
        val fileName = normalized.substringAfterLast('/')
        val dot = fileName.lastIndexOf('.')
        return if (dot > 0) fileName.substring(0, dot) else fileName
    }

    fun deriveSm4Key(filePath: String, encryptionMethod: Int): ByteArray {
        val part1 = stemOfPath(filePath).lowercase()
        val secret = when (encryptionMethod) {
            EM_SM4_2 -> SM4_SECRET_2
            EM_SM4_4 -> SM4_SECRET_4
            EM_UNKNOWN_17 -> {
                val idx = Math.floorMod(encryptionMethod - EM_SM4_NEW_BASE, SM4_SECRET_NEW.size)
                SM4_SECRET_NEW[idx]
            }
            else -> {
                val idx = Math.floorMod(encryptionMethod - EM_SM4_NEW_BASE, SM4_SECRET_NEW.size)
                "${SM4_SECRET_NEW[idx]}$encryptionMethod"
            }
        }
        val digest = sha1((part1 + secret).toByteArray(Charsets.UTF_8))
        return digest.copyOfRange(0, CustomSm4.KEY_LENGTH)
    }

    private fun sm4For(filePath: String, encryptionMethod: Int): CustomSm4 {
        val cacheKey = "${stemOfPath(filePath).lowercase()}|$encryptionMethod"
        return sm4Cache.getOrPut(cacheKey) {
            CustomSm4(deriveSm4Key(filePath, encryptionMethod))
        }
    }

    fun decryptBlock(ciphertext: ByteArray, filePath: String, encryptionMethod: Int): ByteArray = when {
        isSimple1Method(encryptionMethod) -> decryptSimple1(ciphertext)
        isSimple2Method(encryptionMethod) -> decryptSimple2(ciphertext)
        isSm4Method(encryptionMethod) -> sm4For(filePath, encryptionMethod).decryptBulk(ciphertext)
        else -> throw IllegalArgumentException("Unknown encryption method: $encryptionMethod")
    }

    fun encryptPlaintext(plaintext: ByteArray, filePath: String, encryptionMethod: Int): ByteArray = when {
        isSimple1Method(encryptionMethod) -> decryptSimple1(plaintext)
        isSimple2Method(encryptionMethod) -> encryptSimple2(plaintext)
        isSm4Method(encryptionMethod) -> {
            val padded = padToN(plaintext, CustomSm4.BLOCK_LENGTH)
            sm4For(filePath, encryptionMethod).encryptBulk(padded)
        }
        else -> plaintext
    }

    fun decryptIndex(ciphertext: ByteArray, pakInfo: TencentPakInfo): ByteArray {
        return if (pakInfo.version > 7) {
            val key = rsaExtract(pakInfo.packedKey, RSA_MOD_1)
            val iv = rsaExtract(pakInfo.packedIv, RSA_MOD_1)
            require(key.size == 32 && iv.size == 32) {
                "RSA extracted AES key/iv invalid sizes: key=${key.size}, iv=${iv.size}"
            }
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                IvParameterSpec(iv.copyOfRange(0, 16))
            )
            cipher.doFinal(ciphertext)
        } else {
            decryptSimple1(ciphertext)
        }
    }

    fun encryptIndex(plaintext: ByteArray, pakInfo: TencentPakInfo): ByteArray {
        return if (pakInfo.version > 7) {
            val key = rsaExtract(pakInfo.packedKey, RSA_MOD_1)
            val iv = rsaExtract(pakInfo.packedIv, RSA_MOD_1)
            require(key.size == 32 && iv.size == 32) {
                "RSA extracted AES key/iv invalid sizes"
            }
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(key, "AES"),
                IvParameterSpec(iv.copyOfRange(0, 16))
            )
            cipher.doFinal(plaintext)
        } else {
            decryptSimple1(plaintext)
        }
    }

    private class Lcg(seed: Int) {
        private var state: Long = seed.toLong()

        private fun wrap(x: Long): Long {
            val masked = x and 0xFFFFFFFFL
            return if ((masked and 0x80000000L) == 0L) {
                masked
            } else {
                ((masked + 0x80000000L) and 0xFFFFFFFFL) - 0x80000000L
            }
        }

        fun next(): Int {
            val x1 = wrap(1103515245L * state)
            state = wrap(x1 + 12345L)
            val x2 = if (state < 0L) wrap(x1 + 77880L) else state
            val shifted = (x2 shr 16) and 0xFFFFFFFFL
            return (shifted % 32767L).toInt()
        }
    }

    fun generateBlockIndices(n: Int, encryptionMethod: Int): IntArray {
        if (!isSm4Method(encryptionMethod)) {
            return IntArray(n) { it }
        }
        val cacheKey = (n.toLong() shl 32) or 1L
        return blockPermCache.getOrPut(cacheKey) {
            val permutation = IntArray(n)
            val seen = BooleanArray(n)
            var count = 0
            val lcg = Lcg(n)
            while (count != n) {
                val x = Math.floorMod(lcg.next(), n)
                if (!seen[x]) {
                    seen[x] = true
                    permutation[count++] = x
                }
            }
            val inverse = IntArray(n)
            for (i in 0 until n) {
                inverse[permutation[i]] = i
            }
            inverse
        }
    }

    fun methodName(method: Int, isEncryption: Boolean): String {
        return if (isEncryption) {
            when {
                method == EM_UNKNOWN_17 -> "ENC17?"
                isSimple1Method(method) -> "SIMPLE1"
                isSimple2Method(method) -> "SIMPLE2"
                isSm4Method(method) -> "SM4 (Type $method)"
                method == 0 -> "NONE"
                else -> "UNKNOWN"
            }
        } else {
            when (method) {
                PakCompression.CM_NONE -> "NONE"
                PakCompression.CM_ZLIB -> "ZLIB"
                PakCompression.CM_ZSTD -> "ZSTD"
                PakCompression.CM_ZSTD_DICT -> "ZSTD_DICT"
                else -> "UNKNOWN"
            }
        }
    }
}
