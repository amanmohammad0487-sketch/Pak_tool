package com.example

import com.example.core.crypto.CustomSm4
import com.example.core.crypto.PakCompression
import com.example.core.crypto.PakCrypto
import com.example.core.crypto.Zuc128
import com.example.core.lua.LuaBytecodeEngine
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CryptoAndLuaEngineTest {

    @Test
    fun zuc128_matchesOfficial3gppTestVector1() {
        // 3GPP TS 35.221 Test Set 1: key = 0x00*16, iv = 0x00*16
        // Expected Z[0] = 0x27bede74, Z[1] = 0x018082da
        val words = Zuc128.generateKeystreamWords(ByteArray(16), ByteArray(16), 2)
        assertEquals(0x27bede74L, words[0])
        assertEquals(0x018082daL, words[1])
    }

    @Test
    fun zuc128_matchesOfficial3gppTestVector2() {
        // 3GPP TS 35.221 Test Set 2: key = 0xFF*16, iv = 0xFF*16
        // Expected Z[0] = 0x0657cfa0, Z[1] = 0x7096398b
        val ff = ByteArray(16) { 0xFF.toByte() }
        val words = Zuc128.generateKeystreamWords(ff, ff, 2)
        assertEquals(0x0657cfa0L, words[0])
        assertEquals(0x7096398bL, words[1])
    }

    @Test
    fun customSm4_encryptDecryptRoundTrip() {
        val key = PakCrypto.deriveSm4Key("Content/Lua/BRPlayerCharacterBase.lua", 47)
        assertEquals(16, key.size)
        val sm4 = CustomSm4(key)
        val plaintext = ByteArray(64) { (it * 7 + 3).toByte() }
        val cipher = sm4.encryptBulk(plaintext)
        val restored = sm4.decryptBulk(cipher)
        assertArrayEquals(plaintext, restored)
    }

    @Test
    fun simple1AndSimple2_roundTrip() {
        val payload = ByteArray(48) { (it * 13 + 5).toByte() }
        val c1 = PakCrypto.encryptPlaintext(payload, "test.uasset", PakCrypto.EM_SIMPLE1)
        assertArrayEquals(payload, PakCrypto.decryptBlock(c1, "test.uasset", PakCrypto.EM_SIMPLE1))

        val c2 = PakCrypto.encryptPlaintext(payload, "test.uasset", PakCrypto.EM_SIMPLE2)
        assertArrayEquals(payload, PakCrypto.decryptBlock(c2, "test.uasset", PakCrypto.EM_SIMPLE2))
    }

    @Test
    fun lcgBlockPermutation_isValidBijection() {
        val indices = PakCrypto.generateBlockIndices(17, 47)
        assertEquals(17, indices.size)
        assertEquals((0 until 17).toSet(), indices.toSet())
    }

    @Test
    fun zlibAndZstd_roundTrip() {
        val sample = "MAJLISH Liquid Glass PAK Engine test payload ".repeat(120).toByteArray()
        val zlibComp = PakCompression.bestCompress(sample, PakCompression.CM_ZLIB)
        assertArrayEquals(sample, PakCompression.decompressBlock(zlibComp, null, PakCompression.CM_ZLIB))

        val zstdComp = PakCompression.bestCompress(sample, PakCompression.CM_ZSTD, fast = true)
        assertArrayEquals(sample, PakCompression.decompressBlock(zstdComp, null, PakCompression.CM_ZSTD))
    }

    @Test
    fun lua53_bgmiRoundTripAndDecompile() {
        val xorKey = PakCrypto.hexToBytes(LuaBytecodeEngine.DEFAULT_LUA_XOR_KEY_HEX)
        val stdChunk = LuaBytecodeEngine.compileSimpleLuaToStandard53(
            "test.lua",
            "local title = \"MAJLISH_READY\"\nreturn title"
        )
        val bgmiEncrypted = LuaBytecodeEngine.processBytecode(stdChunk, xorKey, isEncrypt = true)
        val decryptedStd = LuaBytecodeEngine.processBytecode(bgmiEncrypted, xorKey, isEncrypt = false)
        val decompiled = LuaBytecodeEngine.decompileStandardLua53(decryptedStd)
        assertTrue(decompiled.contains("MAJLISH_READY"))
    }
}
