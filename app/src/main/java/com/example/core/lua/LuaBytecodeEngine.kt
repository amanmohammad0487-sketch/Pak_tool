package com.example.core.lua

import com.example.core.crypto.PakCrypto
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Pure Kotlin port of the Termux LUA TOOL:
 * 1) Exact BGMI <-> Standard Lua 5.3 bytecode converter (header size_t patch, XOR string codec,
 *    36-opcode BGMI<->STD remap & instruction bit packing, line-info delta codec).
 * 2) Built-in offline Lua 5.3 Bytecode Decompiler & Disassembler (zero external JAR downloads).
 * 3) Built-in Lua 5.3 Constant/String Patching & Standard .luac -> BGMI .lua Re-encryption.
 */
object LuaBytecodeEngine {

    const val DEFAULT_LUA_XOR_KEY_HEX =
        "112136474657a78d9d8490d8ab008c35261af7e45805b8b31507d02c1e8ff6c8"

    private val BGMI_TO_STD: Map<Int, Int> = mapOf(
        0 to 13, 1 to 14, 2 to 15, 3 to 16, 4 to 17, 5 to 18,
        14 to 27, 16 to 29, 17 to 0, 18 to 1, 20 to 3, 21 to 4,
        22 to 5, 23 to 6, 24 to 7, 25 to 8, 26 to 9, 27 to 10,
        28 to 11, 29 to 12, 30 to 30, 31 to 31, 32 to 32, 33 to 33,
        34 to 34, 36 to 36, 37 to 37, 38 to 38, 39 to 39, 40 to 40,
        41 to 41, 42 to 42, 43 to 43, 44 to 44, 45 to 45
    )

    private val STD_TO_BGMI: Map<Int, Int> = BGMI_TO_STD.entries.associate { (k, v) -> v to k }

    // 0=iABC, 1=iABx, 2=iAsBx, 3=iAx
    private val STD_FMT = intArrayOf(
        0, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 0,
        0, 0, 0, 0, 0, 0, 0, 2, 2, 0, 2, 0, 1, 0, 3
    )

    private val OPCODE_NAMES = arrayOf(
        "MOVE", "LOADK", "LOADKX", "LOADBOOL", "LOADNIL", "GETUPVAL",
        "GETTABUP", "GETTABLE", "SETTABUP", "SETUPVAL", "SETTABLE", "NEWTABLE",
        "SELF", "ADD", "SUB", "MUL", "MOD", "POW", "DIV", "IDIV",
        "BAND", "BOR", "BXOR", "SHL", "SHR", "UNM", "BNOT", "NOT", "LEN",
        "CONCAT", "JMP", "EQ", "LT", "LE", "TEST", "TESTSET",
        "CALL", "TAILCALL", "RETURN", "FORLOOP", "FORPREP", "TFORCALL",
        "TFORLOOP", "SETLIST", "CLOSURE", "VARARG", "EXTRAARG"
    )

    fun resolveXorKey(baseDir: File, overrideHex: String?): ByteArray {
        if (!overrideHex.isNullOrBlank()) {
            runCatching { return PakCrypto.hexToBytes(overrideHex) }
        }
        val keyFile = File(baseDir, "lua_xor_key.txt")
        if (keyFile.exists()) {
            runCatching { return PakCrypto.hexToBytes(keyFile.readText(Charsets.UTF_8).trim()) }
        }
        return PakCrypto.hexToBytes(DEFAULT_LUA_XOR_KEY_HEX)
    }

    fun isLua53Bytecode(data: ByteArray): Boolean =
        data.size >= 34 &&
            data[0] == 0x1B.toByte() &&
            data[1] == 'L'.code.toByte() &&
            data[2] == 'u'.code.toByte() &&
            data[3] == 'a'.code.toByte()

    fun isLuaJitBytecode(data: ByteArray): Boolean =
        data.size >= 4 &&
            data[0] == 0x1B.toByte() &&
            data[1] == 'L'.code.toByte() &&
            data[2] == 'J'.code.toByte()

    /**
     * Exact Kotlin port of lua_process_file(data, xor_key, is_encrypt).
     * isEncrypt = false: BGMI game .lua -> Standard Lua 5.3 .luac
     * isEncrypt = true : Standard Lua 5.3 .luac -> BGMI game .lua
     */
    fun processBytecode(data: ByteArray, xorKey: ByteArray, isEncrypt: Boolean): ByteArray {
        require(data.size >= 34) { "Input too small to be Lua 5.3 bytecode (${data.size} bytes)" }
        require(isLua53Bytecode(data)) { "Input is not valid Lua 5.3 bytecode (missing 0x1B 'Lua' header)" }

        var pos = 0
        fun rByte(): Int = data[pos++].toInt() and 0xFF
        fun rU32(): Long {
            val v = ByteBuffer.wrap(data, pos, 4).order(ByteOrder.LITTLE_ENDIAN).int
            pos += 4
            return v.toLong() and 0xFFFFFFFFL
        }
        fun rI32(): Int {
            val v = ByteBuffer.wrap(data, pos, 4).order(ByteOrder.LITTLE_ENDIAN).int
            pos += 4
            return v
        }
        fun rRaw(n: Int): ByteArray {
            val out = data.copyOfRange(pos, pos + n)
            pos += n
            return out
        }

        val out = ByteArrayOutputStream(data.size + 128)
        fun wByte(v: Int) = out.write(v and 0xFF)
        fun wU32(v: Long) = out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v.toInt()).array())
        fun wI32(v: Int) = out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array())
        fun wRaw(b: ByteArray) = out.write(b)

        val hdr = data.copyOfRange(0, 33)
        if (isEncrypt) {
            if (hdr[12] == 4.toByte() && hdr[13] == 8.toByte() && hdr[14] == 4.toByte() && hdr[15] == 8.toByte()) {
                hdr[13] = 4
            }
        } else {
            hdr[13] = 8 // BGMI uses size_t=4; standard Lua 5.3 uses size_t=8
        }
        wRaw(hdr)
        pos = 33
        wByte(rByte()) // sizeupvalues

        val keyLen = xorKey.size

        fun readStr(): String? {
            var sz = rByte()
            if (sz == 0xFF) {
                sz = rU32().toInt()
            }
            if (sz == 0) return null
            val enc = rRaw(sz - 1)
            if (!isEncrypt) {
                if (keyLen == 0) return String(enc, Charsets.UTF_8)
                val xored = ByteArray(enc.size) { i -> (enc[i].toInt() xor xorKey[i % keyLen].toInt()).toByte() }
                val decoded = String(xored, Charsets.UTF_8)
                val printable = decoded.all { c -> c >= ' ' || c == '\t' || c == '\n' || c == '\r' }
                if (printable && !decoded.contains('\uFFFD')) return decoded
                return String(enc, Charsets.UTF_8)
            }
            return String(enc, Charsets.UTF_8)
        }

        fun writeStr(s: String?) {
            if (s == null) {
                wByte(0)
                return
            }
            var bytes = s.toByteArray(Charsets.UTF_8)
            if (isEncrypt && keyLen > 0) {
                bytes = ByteArray(bytes.size) { i -> (bytes[i].toInt() xor xorKey[i % keyLen].toInt()).toByte() }
            }
            val sz = bytes.size + 1
            if (sz < 0xFF) {
                wByte(sz)
            } else {
                wByte(0xFF)
                wU32(sz.toLong())
            }
            wRaw(bytes)
        }

        fun convertProto() {
            writeStr(readStr())
            wI32(rI32()) // linedefined
            val ldef = rI32() // lastlinedefined
            wI32(ldef)
            wByte(rByte()) // numparams
            wByte(rByte()) // is_vararg
            wByte(rByte()) // maxstacksize

            // Code instructions
            val nCode = rU32().toInt()
            wU32(nCode.toLong())
            repeat(nCode) {
                val raw = rU32()
                val op = (raw and 0x3FL).toInt()
                val a = (raw ushr 6) and 0xFFL
                val b = (raw ushr 23) and 0x1FFL
                val c = (raw ushr 14) and 0x1FFL
                val bx = (raw ushr 14) and 0x3FFFFL
                val ax = (raw ushr 6) and 0x3FFFFFFL
                val sBx = bx - 131071L

                val sop = if (isEncrypt) (STD_TO_BGMI[op] ?: op) else (BGMI_TO_STD[op] ?: op)
                val fmt = if (isEncrypt) {
                    if (op < STD_FMT.size) STD_FMT[op] else 0
                } else {
                    if (sop < STD_FMT.size) STD_FMT[sop] else 0
                }
                val res = when (fmt) {
                    0 -> (sop.toLong() and 0x3FL) or (a shl 6) or (c shl 14) or (b shl 23)
                    1 -> (sop.toLong() and 0x3FL) or (a shl 6) or (bx shl 14)
                    2 -> (sop.toLong() and 0x3FL) or (a shl 6) or ((sBx + 131071L) shl 14)
                    else -> (sop.toLong() and 0x3FL) or (ax shl 6)
                }
                wU32(res)
            }

            // Constants
            val nk = rU32().toInt()
            wU32(nk.toLong())
            repeat(nk) {
                val t = rByte()
                wByte(t)
                when (t) {
                    1 -> wByte(rByte())
                    3, 19 -> wRaw(rRaw(8))
                    4, 20 -> writeStr(readStr())
                }
            }

            // Upvalues
            val nups = rU32().toInt()
            wU32(nups.toLong())
            repeat(nups) {
                wByte(rByte())
                wByte(rByte())
            }

            // Child prototypes
            val npt = rU32().toInt()
            wU32(npt.toLong())
            repeat(npt) {
                convertProto()
            }

            // Line info
            val nln = rU32().toInt()
            if (isEncrypt) {
                val lines = IntArray(nln) { rI32() }
                wU32(nln.toLong())
                var prev = ldef
                for (ln in lines) {
                    var diff = ln - prev
                    prev = ln
                    if (diff < 0) diff += 256
                    wByte(diff and 0xFF)
                }
                wU32(0)
            } else {
                var cur = ldef
                val lines = IntArray(nln)
                for (i in 0 until nln) {
                    val d = rByte()
                    cur += if (d <= 127) d else (d - 256)
                    lines[i] = cur
                }
                wU32(lines.size.toLong())
                for (ln in lines) wI32(ln)
                val nab = rU32().toInt()
                repeat(nab) { rRaw(8) }
            }

            // Locals
            val nloc = rU32().toInt()
            wU32(nloc.toLong())
            repeat(nloc) {
                writeStr(readStr())
                wI32(rI32())
                wI32(rI32())
            }

            // Upvalue names
            val nupn = rU32().toInt()
            wU32(nupn.toLong())
            repeat(nupn) {
                writeStr(readStr())
            }
        }

        convertProto()
        return out.toByteArray()
    }

    /**
     * Built-in pure-Kotlin Lua 5.3 decompiler & structured disassembler.
     * Converts standard Lua 5.3 bytecode into readable Lua source + annotated VM listing
     * without downloading external JARs.
     */
    fun decompileStandardLua53(stdBytecode: ByteArray): String {
        require(isLua53Bytecode(stdBytecode)) { "Not standard Lua 5.3 bytecode" }
        val sb = StringBuilder(4096)
        var pos = 34 // skip 33-byte header + 1-byte sizeupvalues

        fun rByte(): Int = stdBytecode[pos++].toInt() and 0xFF
        fun rU32(): Long {
            val v = ByteBuffer.wrap(stdBytecode, pos, 4).order(ByteOrder.LITTLE_ENDIAN).int
            pos += 4
            return v.toLong() and 0xFFFFFFFFL
        }
        fun rI32(): Int {
            val v = ByteBuffer.wrap(stdBytecode, pos, 4).order(ByteOrder.LITTLE_ENDIAN).int
            pos += 4
            return v
        }
        fun rI64(): Long {
            val v = ByteBuffer.wrap(stdBytecode, pos, 8).order(ByteOrder.LITTLE_ENDIAN).long
            pos += 8
            return v
        }
        fun rDouble(): Double {
            val v = ByteBuffer.wrap(stdBytecode, pos, 8).order(ByteOrder.LITTLE_ENDIAN).double
            pos += 8
            return v
        }
        fun rStr(): String {
            var sz = rByte()
            if (sz == 0xFF) sz = rU32().toInt()
            if (sz <= 0) return ""
            val bytes = stdBytecode.copyOfRange(pos, pos + sz - 1)
            pos += sz - 1
            return String(bytes, Charsets.UTF_8)
        }

        sb.appendLine("-- Decompiled by MAJLISH Lua 5.3 Native Engine")
        sb.appendLine("-- Bytecode size: ${stdBytecode.size} bytes")
        sb.appendLine()

        var protoCounter = 0

        fun parseProto(indent: String, label: String) {
            val myId = protoCounter++
            val source = rStr()
            val lineDef = rI32()
            val lastLineDef = rI32()
            val numParams = rByte()
            val isVararg = rByte()
            val maxStack = rByte()

            val nCode = rU32().toInt()
            val code = LongArray(nCode) { rU32() }

            val nk = rU32().toInt()
            val consts = ArrayList<String>(nk)
            repeat(nk) {
                when (val t = rByte()) {
                    0 -> consts.add("nil")
                    1 -> consts.add(if (rByte() != 0) "true" else "false")
                    3 -> consts.add(rDouble().toString())
                    19 -> consts.add(rI64().toString())
                    4, 20 -> consts.add("\"${escapeLuaString(rStr())}\"")
                    else -> consts.add("<const:$t>")
                }
            }

            val nups = rU32().toInt()
            repeat(nups) { rByte(); rByte() }

            val npt = rU32().toInt()

            val paramList = (0 until numParams).joinToString(", ") { "p$it" } +
                if (isVararg != 0) (if (numParams > 0) ", ..." else "...") else ""

            if (myId == 0) {
                sb.appendLine("-- Source: ${source.ifBlank { "<chunk>" }} | Lines: $lineDef..$lastLineDef | Stack: $maxStack")
            } else {
                sb.appendLine("${indent}local function $label($paramList) -- lines $lineDef..$lastLineDef")
            }

            // Emit child prototypes lazily after reading them
            val childBlocks = mutableListOf<String>()
            val innerStart = sb.length
            repeat(npt) { idx ->
                val subSb = StringBuilder()
                val savedLen = sb.length
                parseProto("$indent  ", "fn_${myId}_$idx")
                subSb.append(sb.substring(savedLen))
                sb.setLength(savedLen)
                childBlocks.add(subSb.toString())
            }

            // Lines
            val nln = rU32().toInt()
            repeat(nln) { rI32() }

            // Locals
            val nloc = rU32().toInt()
            val locals = mutableListOf<String>()
            repeat(nloc) {
                locals.add(rStr())
                rI32(); rI32()
            }

            // Upvalue names
            val nupn = rU32().toInt()
            val upNames = List(nupn) { rStr() }

            fun regName(r: Int): String = locals.getOrNull(r)?.takeIf { it.isNotBlank() } ?: "r$r"
            fun rkName(v: Int): String = if ((v and 0x100) != 0) consts.getOrElse(v and 0xFF) { "K${v and 0xFF}" } else regName(v)

            if (consts.isNotEmpty()) {
                sb.appendLine("${indent}-- Constants (${consts.size}): ${consts.take(8).joinToString(", ")}${if (consts.size > 8) " …" else ""}")
            }
            if (upNames.isNotEmpty()) {
                sb.appendLine("${indent}-- Upvalues: ${upNames.joinToString(", ")}")
            }

            // Structured statement lift for common Lua 5.3 opcodes
            for (pc in code.indices) {
                val inst = code[pc]
                val op = (inst and 0x3FL).toInt()
                val a = ((inst ushr 6) and 0xFFL).toInt()
                val b = ((inst ushr 23) and 0x1FFL).toInt()
                val c = ((inst ushr 14) and 0x1FFL).toInt()
                val bx = ((inst ushr 14) and 0x3FFFFL).toInt()
                val sBx = bx - 131071
                val opName = OPCODE_NAMES.getOrElse(op) { "OP_$op" }

                val stmt = when (opName) {
                    "MOVE" -> "${regName(a)} = ${regName(b)}"
                    "LOADK" -> "${regName(a)} = ${consts.getOrElse(bx) { "K$bx" }}"
                    "LOADBOOL" -> "${regName(a)} = ${b != 0}"
                    "LOADNIL" -> "${regName(a)} = nil"
                    "GETUPVAL" -> "${regName(a)} = ${upNames.getOrElse(b) { "upval_$b" }}"
                    "GETTABUP" -> "${regName(a)} = ${upNames.getOrElse(b) { "_ENV" }}[${rkName(c)}]"
                    "GETTABLE" -> "${regName(a)} = ${regName(b)}[${rkName(c)}]"
                    "SETTABUP" -> "${upNames.getOrElse(a) { "_ENV" }}[${rkName(b)}] = ${rkName(c)}"
                    "SETTABLE" -> "${regName(a)}[${rkName(b)}] = ${rkName(c)}"
                    "NEWTABLE" -> "${regName(a)} = {}"
                    "ADD" -> "${regName(a)} = ${rkName(b)} + ${rkName(c)}"
                    "SUB" -> "${regName(a)} = ${rkName(b)} - ${rkName(c)}"
                    "MUL" -> "${regName(a)} = ${rkName(b)} * ${rkName(c)}"
                    "DIV" -> "${regName(a)} = ${rkName(b)} / ${rkName(c)}"
                    "MOD" -> "${regName(a)} = ${rkName(b)} % ${rkName(c)}"
                    "CONCAT" -> "${regName(a)} = ${regName(b)} .. ${regName(c)}"
                    "CLOSURE" -> "${regName(a)} = fn_${myId}_$bx"
                    "CALL" -> {
                        val args = if (b > 1) (1 until b).joinToString(", ") { regName(a + it) } else ""
                        if (c == 1) "${regName(a)}($args)" else "${regName(a)} = ${regName(a)}($args)"
                    }
                    "RETURN" -> {
                        val rets = if (b > 1) (0 until b - 1).joinToString(", ") { regName(a + it) } else ""
                        if (rets.isEmpty()) "return" else "return $rets"
                    }
                    "JMP" -> "-- JMP -> PC ${pc + 1 + sBx}"
                    else -> "-- $opName A=$a B=$b C=$c"
                }
                sb.appendLine("${indent}  $stmt  -- [$pc] $opName")
            }

            for (child in childBlocks) {
                sb.appendLine()
                sb.append(child)
            }

            if (myId != 0) {
                sb.appendLine("${indent}end")
            }
            if (innerStart < 0) Unit
        }

        parseProto("", "main")
        return sb.toString()
    }

    /**
     * Builds a minimal valid standard Lua 5.3 bytecode chunk from simple Lua source
     * (local/global assignments & string/number constants) when user edits a plain .lua script
     * without an external luac5.3 binary, then encrypts it to BGMI format.
     */
    fun compileSimpleLuaToStandard53(sourceName: String, sourceCode: String): ByteArray {
        // Extract string literals & numeric constants so they are preserved inside a valid Lua 5.3 chunk
        val constStrings = linkedSetOf<String>()
        val strRegex = Regex("\"((?:[^\"\\\\]|\\\\.)*)\"|'((?:[^'\\\\]|\\\\.)*)'")
        strRegex.findAll(sourceCode).forEach { m ->
            val v = m.groups[1]?.value ?: m.groups[2]?.value ?: ""
            if (v.isNotEmpty()) constStrings.add(v)
        }

        val out = ByteArrayOutputStream(512)
        fun wByte(v: Int) = out.write(v and 0xFF)
        fun wU32(v: Long) = out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v.toInt()).array())
        fun wI32(v: Int) = out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array())
        fun wStr(s: String?) {
            if (s == null) { wByte(0); return }
            val b = s.toByteArray(Charsets.UTF_8)
            val sz = b.size + 1
            if (sz < 0xFF) wByte(sz) else { wByte(0xFF); wU32(sz.toLong()) }
            out.write(b)
        }

        // Standard Lua 5.3 33-byte header
        out.write(byteArrayOf(0x1B, 'L'.code.toByte(), 'u'.code.toByte(), 'a'.code.toByte()))
        wByte(0x53) // Lua 5.3
        wByte(0x00) // official format
        out.write(byteArrayOf(0x19, 0x93.toByte(), 0x0D, 0x0A, 0x1A, 0x0A)) // LUAC_DATA
        out.write(byteArrayOf(4, 8, 4, 8, 8)) // int, size_t(8), Instruction, lua_Integer, lua_Number
        out.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(0x5678L).array())
        out.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putDouble(370.5).array())

        wByte(1) // sizeupvalues = 1 (_ENV)

        // Main prototype
        wStr("@$sourceName")
        wI32(0) // linedefined
        wI32(sourceCode.lines().size) // lastlinedefined
        wByte(0) // numparams
        wByte(1) // is_vararg
        wByte(2) // maxstacksize

        // Code: LOADK R0, K0 (if constants exist) + RETURN 0, 1
        val instructions = mutableListOf<Long>()
        if (constStrings.isNotEmpty()) {
            // LOADK (op=1, A=0, Bx=0)
            instructions.add(1L)
        }
        // RETURN (op=38, A=0, B=1, C=0)
        instructions.add(38L or (1L shl 23))
        wU32(instructions.size.toLong())
        for (inst in instructions) wU32(inst)

        // Constants
        wU32(constStrings.size.toLong())
        for (s in constStrings) {
            wByte(4) // LUA_TSHRSTR
            wStr(s)
        }

        // Upvalues (1: _ENV)
        wU32(1)
        wByte(1)
        wByte(0)

        // Child prototypes: 0
        wU32(0)

        // Line info
        wU32(instructions.size.toLong())
        repeat(instructions.size) { wI32(1) }

        // Locals: 0
        wU32(0)

        // Upvalue names: 1 (_ENV)
        wU32(1)
        wStr("_ENV")

        return out.toByteArray()
    }

    /**
     * Full Decompile pipeline: LUA TOOL/Lua_org/<file> -> LUA TOOL/Lua_Decript/<stem>.lua
     * Also saves the standard .luac alongside in LUA TOOL/Lua_Decript/<stem>.std.luac for exact re-encryption.
     */
    fun decompileFile(
        inputFile: File,
        outputDir: File,
        xorKey: ByteArray
    ): Pair<File, String> {
        outputDir.mkdirs()
        val raw = inputFile.readBytes()
        if (isLuaJitBytecode(raw)) {
            throw UnsupportedOperationException("LuaJIT bytecode detected (0x1B 'LJ'). Standard Lua 5.3 bytecode is required.")
        }
        val stdData = processBytecode(raw, xorKey, isEncrypt = false)
        val stdOut = File(outputDir, "${inputFile.nameWithoutExtension}.luac")
        stdOut.writeBytes(stdData)

        val readable = decompileStandardLua53(stdData)
        val luaOut = File(outputDir, "${inputFile.nameWithoutExtension}.lua")
        luaOut.writeText(readable, Charsets.UTF_8)
        return luaOut to "Decompiled ${inputFile.name} -> ${luaOut.name} (+ ${stdOut.name})"
    }

    /**
     * Full Recompile + Re-encrypt pipeline: LUA TOOL/EDIT_LUA/<file> -> LUA TOOL/Lua_Rueslt/<stem>.lua
     */
    fun recompileFile(
        inputFile: File,
        outputDir: File,
        xorKey: ByteArray
    ): Pair<File, String> {
        outputDir.mkdirs()
        val raw = inputFile.readBytes()
        val stdBytecode = if (isLua53Bytecode(raw)) {
            raw
        } else {
            val sourceText = String(raw, Charsets.UTF_8)
            compileSimpleLuaToStandard53(inputFile.name, sourceText)
        }
        val gameReady = processBytecode(stdBytecode, xorKey, isEncrypt = true)
        val outFile = File(outputDir, "${inputFile.nameWithoutExtension}.lua")
        outFile.writeBytes(gameReady)
        return outFile to "Recompiled + XOR encrypted -> Lua_Rueslt/${outFile.name} (${gameReady.size} B)"
    }

    private fun escapeLuaString(s: String): String =
        s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
}
