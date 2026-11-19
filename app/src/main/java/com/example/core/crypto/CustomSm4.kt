package com.example.core.crypto

/**
 * Exact Kotlin port of the game-specific Custom SM4 cipher from the Termux PAK tool
 * (custom S-Box, FK, CK, and precomputed T0-T3 lookup tables for native-speed ECB bulk ops).
 */
class CustomSm4(key: ByteArray) {

    private val encRoundKeys = IntArray(32)
    private val decRoundKeys = IntArray(32)

    init {
        require(key.size == BLOCK_LENGTH) { "SM4 key must be 16 bytes" }
        expandKey(key, encRoundKeys)
        for (i in 0 until 32) {
            decRoundKeys[i] = encRoundKeys[31 - i]
        }
    }

    fun encryptBulk(data: ByteArray): ByteArray = transformBulk(data, encRoundKeys)

    fun decryptBulk(data: ByteArray): ByteArray = transformBulk(data, decRoundKeys)

    private fun transformBulk(data: ByteArray, rk: IntArray): ByteArray {
        val n = data.size
        require(n % BLOCK_LENGTH == 0) { "SM4 input length must be multiple of 16, got $n" }
        val out = ByteArray(n)
        val t0 = T0
        val t1 = T1
        val t2 = T2
        val t3 = T3
        var idx = 0
        while (idx < n) {
            var x0 = readBeInt(data, idx)
            var x1 = readBeInt(data, idx + 4)
            var x2 = readBeInt(data, idx + 8)
            var x3 = readBeInt(data, idx + 12)
            var i = 0
            while (i < 32) {
                var t = x1 xor x2 xor x3 xor rk[i]
                x0 = x0 xor (t0[(t ushr 24) and 0xFF] xor t1[(t ushr 16) and 0xFF] xor t2[(t ushr 8) and 0xFF] xor t3[t and 0xFF])
                t = x2 xor x3 xor x0 xor rk[i + 1]
                x1 = x1 xor (t0[(t ushr 24) and 0xFF] xor t1[(t ushr 16) and 0xFF] xor t2[(t ushr 8) and 0xFF] xor t3[t and 0xFF])
                t = x3 xor x0 xor x1 xor rk[i + 2]
                x2 = x2 xor (t0[(t ushr 24) and 0xFF] xor t1[(t ushr 16) and 0xFF] xor t2[(t ushr 8) and 0xFF] xor t3[t and 0xFF])
                t = x0 xor x1 xor x2 xor rk[i + 3]
                x3 = x3 xor (t0[(t ushr 24) and 0xFF] xor t1[(t ushr 16) and 0xFF] xor t2[(t ushr 8) and 0xFF] xor t3[t and 0xFF])
                i += 4
            }
            writeBeInt(out, idx, x3)
            writeBeInt(out, idx + 4, x2)
            writeBeInt(out, idx + 8, x1)
            writeBeInt(out, idx + 12, x0)
            idx += 16
        }
        return out
    }

    companion object {
        const val KEY_LENGTH = 16
        const val BLOCK_LENGTH = 16

        private val S_BOX = intArrayOf(
            52, 102, 37, 116, 137, 120, 228, 169, 90, 65, 188, 122, 214, 22, 33, 35,
            77, 97, 218, 148, 155, 223, 19, 60, 105, 58, 49, 10, 95, 215, 153, 149,
            241, 174, 114, 61, 7, 96, 36, 182, 152, 238, 196, 162, 45, 136, 221, 141,
            4, 234, 187, 17, 202, 62, 93, 161, 246, 63, 176, 151, 128, 71, 43, 166,
            230, 247, 217, 177, 89, 192, 124, 190, 84, 40, 183, 126, 79, 248, 67, 110,
            160, 80, 14, 245, 144, 184, 251, 163, 123, 98, 25, 70, 3, 42, 185, 143,
            159, 119, 180, 91, 131, 135, 8, 235, 226, 30, 66, 240, 15, 232, 113, 106,
            117, 173, 85, 31, 181, 171, 51, 250, 127, 21, 189, 133, 216, 6, 104, 179,
            82, 48, 72, 11, 0, 237, 239, 178, 87, 142, 231, 108, 213, 229, 46, 83,
            130, 5, 249, 129, 244, 86, 191, 140, 75, 227, 219, 74, 145, 76, 44, 211,
            64, 41, 78, 32, 20, 54, 121, 9, 111, 209, 55, 224, 57, 12, 138, 146,
            56, 18, 53, 109, 225, 253, 147, 154, 23, 212, 201, 156, 107, 132, 38, 157,
            175, 118, 193, 158, 208, 150, 197, 203, 233, 115, 73, 210, 205, 100, 195, 199,
            1, 125, 243, 172, 252, 222, 164, 68, 50, 27, 194, 186, 28, 2, 198, 39,
            69, 139, 242, 24, 167, 16, 81, 29, 200, 207, 99, 255, 47, 13, 88, 206,
            101, 165, 220, 26, 59, 134, 254, 34, 92, 168, 94, 103, 170, 236, 112, 204
        )

        private val FK = intArrayOf(
            1184304796,
            1270900830,
            1493524870,
            3164752158L.toInt()
        )

        private val CK = intArrayOf(
            964907, 973793155, 2654690407L.toInt(), 2916866751L.toInt(),
            2071233739, 1226140771, 3348805095L.toInt(), 2045549823,
            388349611, 800627875, 612403927, 3721562911L.toInt(),
            1195432523, 3150178931L.toInt(), 612053223, 2445162591L.toInt(),
            67183755, 1174197155, 1393249511, 3331183455L.toInt(),
            3822152747L.toInt(), 1332317203, 1804781383, 1990130463,
            1282653851, 3376591251L.toInt(), 2910902311L.toInt(), 925872959,
            332098219, 735840931, 396665415, 3588844719L.toInt()
        )

        private val T0 = IntArray(256)
        private val T1 = IntArray(256)
        private val T2 = IntArray(256)
        private val T3 = IntArray(256)

        init {
            for (i in 0 until 256) {
                val s = S_BOX[i]
                T0[i] = linear0(s shl 24)
                T1[i] = linear0(s shl 16)
                T2[i] = linear0(s shl 8)
                T3[i] = linear0(s)
            }
        }

        private fun rol32(x: Int, n: Int): Int = (x shl n) or (x ushr (32 - n))

        private fun linear0(y: Int): Int =
            y xor rol32(y, 2) xor rol32(y, 10) xor rol32(y, 18) xor rol32(y, 24)

        private fun sboxWord(x: Int): Int =
            (S_BOX[(x ushr 24) and 0xFF] shl 24) or
                (S_BOX[(x ushr 16) and 0xFF] shl 16) or
                (S_BOX[(x ushr 8) and 0xFF] shl 8) or
                S_BOX[x and 0xFF]

        private fun t1(x: Int): Int {
            val s = sboxWord(x)
            return s xor rol32(s, 13) xor rol32(s, 23)
        }

        private fun expandKey(key: ByteArray, rk: IntArray) {
            var k0 = readBeInt(key, 0) xor FK[0]
            var k1 = readBeInt(key, 4) xor FK[1]
            var k2 = readBeInt(key, 8) xor FK[2]
            var k3 = readBeInt(key, 12) xor FK[3]
            var i = 0
            while (i < 32) {
                k0 = k0 xor t1(k1 xor k2 xor k3 xor CK[i])
                rk[i] = k0
                k1 = k1 xor t1(k2 xor k3 xor k0 xor CK[i + 1])
                rk[i + 1] = k1
                k2 = k2 xor t1(k3 xor k0 xor k1 xor CK[i + 2])
                rk[i + 2] = k2
                k3 = k3 xor t1(k0 xor k1 xor k2 xor CK[i + 3])
                rk[i + 3] = k3
                i += 4
            }
        }

        private fun readBeInt(buf: ByteArray, off: Int): Int =
            ((buf[off].toInt() and 0xFF) shl 24) or
                ((buf[off + 1].toInt() and 0xFF) shl 16) or
                ((buf[off + 2].toInt() and 0xFF) shl 8) or
                (buf[off + 3].toInt() and 0xFF)

        private fun writeBeInt(buf: ByteArray, off: Int, v: Int) {
            buf[off] = (v ushr 24).toByte()
            buf[off + 1] = (v ushr 16).toByte()
            buf[off + 2] = (v ushr 8).toByte()
            buf[off + 3] = v.toByte()
        }
    }
}
