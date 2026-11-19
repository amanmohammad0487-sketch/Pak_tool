package com.example.core.pak

import com.example.core.crypto.PakCompression
import com.example.core.crypto.PakCrypto
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.IdentityHashMap
import java.util.zip.CRC32

/**
 * Memory-safe Android Kotlin port of TencentPakFile + Unpack, Repack (Full Rebuild,
 * Folder-wise, index.csv, In-Place Block), Repack-to-Path, Multi-File Inject, and SM4 Protect.
 */
class LoadedTencentPak(
    val file: File,
    val pakInfo: TencentPakInfo,
    val rawMountPoint: String,
    val mountPoint: String,
    val files: List<TencentPakEntry>,
    val rawDirs: LinkedHashMap<String, LinkedHashMap<String, TencentPakEntry>>,
    val indexDirs: LinkedHashMap<String, LinkedHashMap<String, TencentPakEntry>>,
    val isZstdWithDict: Boolean,
    val zstdDictBytes: ByteArray?,
    val zstdDictEntry: TencentPakEntry?
) {
    fun buildFullPathMap(): LinkedHashMap<String, TencentPakEntry> {
        val map = LinkedHashMap<String, TencentPakEntry>()
        for ((dirPath, dirFiles) in indexDirs) {
            for ((name, entry) in dirFiles) {
                val full = joinPakPath(dirPath, name)
                map[full] = entry
            }
        }
        return map
    }

    fun defaultCompressionMethod(): Int {
        val counts = mutableMapOf<Int, Int>()
        for (e in files) {
            val cm = e.compressionMethod
            if (cm == PakCompression.CM_ZLIB || cm == PakCompression.CM_ZSTD || cm == PakCompression.CM_ZSTD_DICT) {
                counts[cm] = (counts[cm] ?: 0) + 1
            }
        }
        if (isZstdWithDict && (counts[PakCompression.CM_ZSTD_DICT] ?: 0) > 0) return PakCompression.CM_ZSTD_DICT
        if ((counts[PakCompression.CM_ZSTD] ?: 0) > 0) return PakCompression.CM_ZSTD
        if ((counts[PakCompression.CM_ZSTD_DICT] ?: 0) > 0) return PakCompression.CM_ZSTD
        return PakCompression.CM_ZLIB
    }

    companion object {
        fun joinPakPath(dir: String, name: String): String {
            val cleanDir = dir.replace('\\', '/').trim('/')
            val full = if (cleanDir.isEmpty()) name else "$cleanDir/$name"
            return full.removePrefix("./").trimStart('/')
        }
    }
}

object TencentPakEngine {

    private val SKIP_NAMES = setOf("thumbs.db", ".ds_store", "desktop.ini")

    fun loadPak(
        pakFile: File,
        isOd: Boolean = false,
        onLog: (String) -> Unit = {}
    ): LoadedTencentPak {
        require(pakFile.exists() && pakFile.isFile) { "PAK file not found: ${pakFile.name}" }
        val fileLength = pakFile.length()
        require(fileLength >= 1024L || fileLength >= TencentPakInfo.BASE_PAK_INFO_SIZE) {
            "Invalid PAK file size: $fileLength bytes"
        }

        val keystream = PakCrypto.zucKeystream()

        RandomAccessFile(pakFile, "r").use { raf ->
            val tailLen = minOf(fileLength, 1024L).toInt()
            val tail = ByteArray(tailLen)
            raf.seek(fileLength - tailLen)
            raf.readFully(tail)

            val pakInfo = TencentPakInfo.parse(tail, keystream)

            // Verify stem hash (v9+)
            if (!isOd && pakInfo.version >= 9) {
                val crc = CRC32()
                val stemUtf32Le = stringToUtf32Le(pakFile.nameWithoutExtension)
                crc.update(stemUtf32Le)
                if (pakInfo.stemHash != (crc.value and 0xFFFFFFFFL)) {
                    onLog("⚠ Filename stem differs from original pak stem hash; keep original filename for game use.")
                }
            }

            if (pakInfo.version <= 10) {
                throw IllegalArgumentException("Unsupported PAK version: ${pakInfo.version} (requires v11+)")
            }

            // Read and decrypt index
            require(pakInfo.indexOffset >= 0 && pakInfo.indexSize > 0 && pakInfo.indexOffset + pakInfo.indexSize <= fileLength) {
                "Corrupt PAK index range: offset=${pakInfo.indexOffset}, size=${pakInfo.indexSize}"
            }
            val encryptedIndex = ByteArray(pakInfo.indexSize.toInt())
            raf.seek(pakInfo.indexOffset)
            raf.readFully(encryptedIndex)

            val indexData = if (pakInfo.indexEncrypted) {
                PakCrypto.decryptIndex(encryptedIndex, pakInfo)
            } else {
                encryptedIndex
            }

            // Verify index SHA-1
            if (!isOd && pakInfo.version >= 8) {
                val rsaHash = PakCrypto.rsaExtract(pakInfo.packedIndexHash, PakCrypto.RSA_MOD_2)
                if (!pakInfo.indexHash.contentEquals(rsaHash)) {
                    onLog("! Rebuilt PAK detected (packed_index_hash mismatch ignored)")
                }
            }
            val actualSha1 = PakCrypto.sha1(indexData)
            if (!pakInfo.indexHash.contentEquals(actualSha1)) {
                throw IllegalStateException("PAK index SHA-1 verification failed — file may be corrupt or unsupported.")
            }

            // Parse index entries and directories
            val reader = BinaryReader(indexData)
            val rawMountPoint = reader.pakString()
            val cleanMountPoint = constructMountPoint(rawMountPoint)
            val entryCount = reader.u4().toInt()
            val entries = List(entryCount) { TencentPakEntry.parse(reader, pakInfo.version) }

            val isZstdWithDict = pakFile.absolutePath.contains("zsdic", ignoreCase = true)
            var zstdDictBytes: ByteArray? = null
            var zstdDictEntry: TencentPakEntry? = null

            val rawDirs = LinkedHashMap<String, LinkedHashMap<String, TencentPakEntry>>()
            val indexDirs = LinkedHashMap<String, LinkedHashMap<String, TencentPakEntry>>()

            val dirCount = reader.u8().toInt()
            repeat(dirCount) {
                val dirPath = reader.pakString()
                val fileCount = reader.u8().toInt()
                val map = LinkedHashMap<String, TencentPakEntry>(fileCount)
                repeat(fileCount) {
                    val fileName = reader.pakString()
                    val encodedIdx = reader.i4()
                    val realIdx = encodedIdx.inv()
                    if (realIdx in entries.indices) {
                        map[fileName] = entries[realIdx]
                    }
                }
                rawDirs[dirPath] = map
                val dirLeaf = dirPath.replace('\\', '/').trim('/').substringAfterLast('/')
                if (isZstdWithDict && dirLeaf == "zstddic" && map.size == 1) {
                    val dictEntry = map.values.first()
                    zstdDictEntry = dictEntry
                    zstdDictBytes = readZstdDict(raf, dictEntry)
                } else {
                    indexDirs[dirPath] = map
                }
            }

            return LoadedTencentPak(
                file = pakFile,
                pakInfo = pakInfo,
                rawMountPoint = rawMountPoint,
                mountPoint = cleanMountPoint,
                files = entries,
                rawDirs = rawDirs,
                indexDirs = indexDirs,
                isZstdWithDict = isZstdWithDict,
                zstdDictBytes = zstdDictBytes,
                zstdDictEntry = zstdDictEntry
            )
        }
    }

    private fun readZstdDict(raf: RandomAccessFile, entry: TencentPakEntry): ByteArray {
        val raw = peekRange(raf, entry.offset, entry.size.toInt())
        val r = BinaryReader(raw)
        val dictSize = r.u8().toInt()
        r.u4()
        val verify = r.u4().toInt()
        require(dictSize == verify) { "Invalid ZSTD dict header" }
        return r.bytes(dictSize)
    }

    private fun peekRange(raf: RandomAccessFile, offset: Long, size: Int): ByteArray {
        val buf = ByteArray(size)
        raf.seek(offset)
        raf.readFully(buf)
        return buf
    }

    private fun peekEntryContent(raf: RandomAccessFile, entry: TencentPakEntry): ByteArray {
        val aligned = PakCrypto.alignEncryptedContentSize(entry.size.toInt(), entry.encryptionMethod)
        return peekRange(raf, entry.offset, aligned)
    }

    private fun peekBlockContent(raf: RandomAccessFile, block: PakCompressedBlock, encMethod: Int): ByteArray {
        val rawLen = (block.end - block.start).toInt()
        val aligned = PakCrypto.alignEncryptedContentSize(rawLen, encMethod)
        return peekRange(raf, block.start, aligned)
    }

    fun unpackPak(
        pakFile: File,
        workspaceRoot: File,
        onProgress: (Float, String) -> Unit,
        onLog: (String) -> Unit
    ): UnpackSummary {
        onLog("🚀 Loading ${pakFile.name}…")
        val pak = loadPak(pakFile, onLog = onLog)
        val stem = pakFile.nameWithoutExtension
        val unpackRoot = File(workspaceRoot, "UNPACK/$stem")
        val repackRoot = File(workspaceRoot, "REPACK/$stem")
        val mountRoot = if (pak.mountPoint.isEmpty()) unpackRoot else File(unpackRoot, pak.mountPoint)
        mountRoot.mkdirs()

        val totalFiles = pak.indexDirs.values.sumOf { it.size }.coerceAtLeast(1)
        var processed = 0
        var rawDumped = 0

        RandomAccessFile(pak.file, "r").use { raf ->
            for ((dirPath, dirFiles) in pak.indexDirs) {
                val cleanDir = dirPath.replace('\\', '/').trim('/')
                val currentOut = if (cleanDir.isEmpty()) mountRoot else File(mountRoot, cleanDir)
                currentOut.mkdirs()

                val repackDir = File(
                    if (pak.mountPoint.isEmpty()) repackRoot else File(repackRoot, pak.mountPoint),
                    cleanDir
                )
                repackDir.mkdirs()

                for ((fileName, entry) in dirFiles) {
                    val outFile = File(currentOut, fileName)
                    val fullRel = LoadedTencentPak.joinPakPath(dirPath, fileName)
                    val dumpedRaw = writeEntryToDisk(raf, pak, outFile, fullRel, entry, onLog)
                    if (dumpedRaw) rawDumped++
                    processed++
                    if (processed <= 25 || processed % 100 == 0 || processed == totalFiles) {
                        val encStr = PakCrypto.methodName(entry.encryptionMethod, true)
                        val compStr = PakCrypto.methodName(entry.compressionMethod, false)
                        onProgress(processed.toFloat() / totalFiles.toFloat(), "$fileName [$compStr/$encStr]")
                    }
                }
            }
        }

        // Write Debug_<pak>.log
        val debugLog = File(unpackRoot, "Debug_$stem.log")
        writeDebugLog(pak, debugLog)

        // Create edited/ folder
        File(unpackRoot, "edited").mkdirs()

        // Write per-pak index.csv and EXE/index.csv
        val perPakCsv = File(unpackRoot, "index.csv")
        val pathsCount = writePackIndexCsv(pak, perPakCsv)
        val exeCsv = File(workspaceRoot, "EXE/index.csv")
        if (!exeCsv.exists() || exeCsv.length() == 0L) {
            writePackIndexCsv(pak, exeCsv)
        }

        onLog("✅ Extracted $processed files ($rawDumped raw-dumped) • $pathsCount paths indexed")
        onProgress(1f, "Unpack complete")

        return UnpackSummary(
            pakName = pakFile.name,
            version = pak.pakInfo.version,
            mountPoint = pak.mountPoint.ifEmpty { "/" },
            extractedCount = processed,
            rawDumpedCount = rawDumped,
            outputDirPath = unpackRoot.absolutePath,
            indexCsvPath = perPakCsv.absolutePath,
            debugLogPath = debugLog.absolutePath
        )
    }

    private fun writeEntryToDisk(
        raf: RandomAccessFile,
        pak: LoadedTencentPak,
        outFile: File,
        fullRelPath: String,
        entry: TencentPakEntry,
        onLog: (String) -> Unit
    ): Boolean {
        val em = entry.encryptionMethod
        val cm = entry.compressionMethod

        // ENC 17 unknown cipher -> raw dump
        if (entry.encrypted && em == PakCrypto.EM_UNKNOWN_17) {
            onLog("⚠ ENC 17 raw dump: ${outFile.name}")
            FileOutputStream(outFile).use { fos ->
                if (cm == PakCompression.CM_NONE) {
                    fos.write(peekEntryContent(raf, entry))
                } else {
                    for (blk in entry.compressedBlocks) {
                        fos.write(peekRange(raf, blk.start, (blk.end - blk.start).toInt()))
                    }
                }
            }
            return true
        }

        // SM4 header probe before decompressing
        if (entry.encrypted && cm != PakCompression.CM_NONE && PakCrypto.isSm4Method(em) && entry.compressedBlocks.isNotEmpty()) {
            try {
                val probeIdx = PakCrypto.generateBlockIndices(entry.compressedBlocks.size, em)[0]
                val probeRaw = peekBlockContent(raf, entry.compressedBlocks[probeIdx], em)
                val probeDec = PakCrypto.decryptBlock(probeRaw, fullRelPath, em)
                if (!PakCompression.decryptedLooksValid(probeDec, cm)) {
                    throw IllegalStateException("SM4 probe mismatch (Type $em)")
                }
            } catch (exc: Exception) {
                FileOutputStream(outFile).use { fos ->
                    for (blk in entry.compressedBlocks) {
                        fos.write(peekBlockContent(raf, blk, em))
                    }
                }
                onLog("! Raw dump (undecryptable): ${outFile.name} (${exc.message})")
                return true
            }
        }

        if (cm == PakCompression.CM_NONE) {
            var data = peekEntryContent(raf, entry)
            if (entry.encrypted) {
                data = PakCrypto.decryptBlock(data, fullRelPath, em)
            }
            val limit = if (entry.uncompressedSize > 0 && data.size > entry.uncompressedSize) {
                entry.uncompressedSize.toInt()
            } else {
                data.size
            }
            FileOutputStream(outFile).use { it.write(data, 0, limit) }
            return false
        }

        return try {
            BufferedOutputStream(FileOutputStream(outFile)).use { bos ->
                val indices = PakCrypto.generateBlockIndices(entry.compressedBlocks.size, em)
                for (x in indices) {
                    var data = peekBlockContent(raf, entry.compressedBlocks[x], em)
                    if (entry.encrypted) {
                        data = PakCrypto.decryptBlock(data, fullRelPath, em)
                    }
                    val decompressed = PakCompression.decompressBlock(data, pak.zstdDictBytes, cm)
                    bos.write(decompressed)
                }
            }
            false
        } catch (exc: Exception) {
            FileOutputStream(outFile).use { fos ->
                for (blk in entry.compressedBlocks) {
                    fos.write(peekBlockContent(raf, blk, em))
                }
            }
            onLog("! Raw dump fallback: ${outFile.name} (${exc.javaClass.simpleName})")
            true
        }
    }

    /**
     * Option 2 & Option 3: Repack from UNPACK/<pakname>/edited/ into RESULT/<pakname>.pak
     */
    fun repackFromUnpack(
        workspaceRoot: File,
        unpackDir: File,
        resolveMode: RepackResolveMode,
        manualTargetPath: String? = null,
        onProgress: (Float, String) -> Unit,
        onBlockResult: (BlockResult) -> Unit = {},
        onLog: (String) -> Unit
    ): RepackSummary {
        val pakName = unpackDir.name
        val editedDir = File(unpackDir, "edited")
        if (!editedDir.exists()) editedDir.mkdirs()

        val editFiles = editedDir.walkTopDown()
            .filter { it.isFile && it.name.lowercase() !in SKIP_NAMES && !it.name.startsWith("Debug_") }
            .toList()
        require(editFiles.isNotEmpty()) {
            "UNPACK/$pakName/edited/ is empty. Import edited files first."
        }

        val origPak = findOriginalPakForUnpack(workspaceRoot, pakName)
            ?: throw IllegalStateException("Original .pak not found in PAK/ or PAK TOOL/PAK/ for '$pakName'")

        onLog("📦 Loading original ${origPak.name}…")
        val pak = loadPak(origPak, onLog = onLog)
        val resultDir = File(workspaceRoot, "RESULT").apply { mkdirs() }
        val outputPak = File(resultDir, origPak.name)

        // In-place block repack mode (MINI_OBB / OBBZSDIC / GAMEPATCH)
        if (resolveMode == RepackResolveMode.IN_PLACE_BLOCK && manualTargetPath.isNullOrBlank()) {
            val detected = detectRepackMode(origPak)
            onLog("🧩 In-Place Block Repack Mode: $detected")
            return repackInPlaceBlocks(pak, editedDir, outputPak, detected, onProgress, onBlockResult, onLog)
        }

        val fmap = pak.buildFullPathMap()
        val lowerMap = HashMap<String, Pair<String, TencentPakEntry>>(fmap.size)
        val nameMap = HashMap<String, MutableList<Pair<String, TencentPakEntry>>>()
        for ((full, entry) in fmap) {
            lowerMap[full.lowercase()] = full to entry
            nameMap.getOrPut(File(full).name.lowercase()) { mutableListOf() }.add(full to entry)
        }

        val indexNameMap = HashMap<String, MutableList<String>>()
        if (resolveMode == RepackResolveMode.INDEX_CSV && manualTargetPath.isNullOrBlank()) {
            val csv = findIndexCsv(workspaceRoot, pakName)
                ?: File(unpackDir, "index.csv").also { writePackIndexCsv(pak, it) }
            val paths = loadIndexCsv(csv)
            onLog("📄 Loaded index.csv (${paths.size} paths)")
            for (p in paths) {
                indexNameMap.getOrPut(File(p).name.lowercase()) { mutableListOf() }.add(p)
            }
        }

        val manualDirKey: String? = manualTargetPath?.takeIf { it.isNotBlank() }?.let { raw ->
            val cleaned = raw.replace('\\', '/').trim('/')
            val matched = pak.rawDirs.keys.firstOrNull { it.trim('/').equals(cleaned, ignoreCase = true) }
            matched ?: "$cleaned/"
        }

        val resolved = LinkedHashMap<String, File>()
        val skipped = mutableListOf<String>()

        for (file in editFiles) {
            var targetFull: String? = null
            var entry: TencentPakEntry? = null

            when {
                manualDirKey != null -> {
                    val want = manualDirKey + file.name
                    val hit = lowerMap[want.lowercase()] ?: lowerMap[stripMountPrefix(pak, want).lowercase()]
                    if (hit != null) {
                        targetFull = hit.first
                        entry = hit.second
                    } else {
                        val tmpl = fmap.entries.firstOrNull {
                            File(it.key).extension.equals(file.extension, ignoreCase = true)
                        }?.value ?: fmap.values.firstOrNull()
                        targetFull = want
                        entry = tmpl
                    }
                }

                resolveMode == RepackResolveMode.FOLDER_WISE -> {
                    val rel = file.relativeTo(editedDir).path.replace('\\', '/').trimStart('/')
                    val hit = lowerMap[rel.lowercase()] ?: lowerMap[stripMountPrefix(pak, rel).lowercase()]
                    if (hit != null) {
                        targetFull = hit.first
                        entry = hit.second
                    } else {
                        skipped.add("${file.name} (no folder-wise match: $rel)")
                        onLog("✗ No match: $rel")
                        continue
                    }
                }

                else -> {
                    val key = file.name.lowercase()
                    val cands = nameMap[key].orEmpty()
                    if (cands.size == 1) {
                        targetFull = cands[0].first
                        entry = cands[0].second
                    } else if (cands.size > 1) {
                        val fpMatch = smartResolveByFingerprint(file, cands)
                        if (fpMatch != null) {
                            targetFull = fpMatch.first
                            entry = fpMatch.second
                        } else {
                            skipped.add("${file.name} (ambiguous matches)")
                            onLog("⚠ Ambiguous matches for ${file.name}")
                            continue
                        }
                    } else {
                        val idxHits = indexNameMap[key].orEmpty()
                        var found: Pair<String, TencentPakEntry>? = null
                        for (ip in idxHits) {
                            found = lowerMap[ip.lowercase()] ?: lowerMap[stripMountPrefix(pak, ip).lowercase()]
                            if (found != null) break
                        }
                        if (found != null) {
                            targetFull = found.first
                            entry = found.second
                        } else {
                            skipped.add("${file.name} (not found in index.csv)")
                            onLog("✗ Not found in index.csv: ${file.name}")
                            continue
                        }
                    }
                }
            }

            if (entry != null && entry.encryptionMethod == PakCrypto.EM_UNKNOWN_17) {
                skipped.add("${file.name} (ENC 17 unsupported)")
                onLog("⚠ Skipped ENC 17 entry: $targetFull")
                continue
            }
            if (targetFull != null) {
                resolved[targetFull] = file
            }
        }

        require(resolved.isNotEmpty()) { "Nothing matched to repack (${skipped.size} skipped)." }

        val tempStage = File(workspaceRoot, ".tmp_repack_${System.currentTimeMillis()}").apply { mkdirs() }
        return try {
            for ((targetFull, diskFile) in resolved) {
                val dest = File(tempStage, targetFull)
                dest.parentFile?.mkdirs()
                diskFile.copyTo(dest, overwrite = true)
            }

            val (editedCount, addedCount) = injectFiles(
                pak = pak,
                editRoot = tempStage,
                outputPak = outputPak,
                protectNew = false,
                sm4Type = 47,
                onProgress = onProgress,
                onLog = onLog
            )

            RepackSummary(
                outputFileName = outputPak.name,
                outputAbsolutePath = outputPak.absolutePath,
                processedFiles = editedCount,
                addedFiles = addedCount,
                skippedFiles = skipped,
                modeLabel = if (manualDirKey != null) "TO-PATH ($manualDirKey)" else resolveMode.name
            )
        } finally {
            tempStage.deleteRecursively()
        }
    }

    /**
     * Option 2 In-Place Block Repack (exact port of repack_pak_file_with_block_display)
     */
    private fun repackInPlaceBlocks(
        pak: LoadedTencentPak,
        editedRoot: File,
        outputPak: File,
        detectedMode: String,
        onProgress: (Float, String) -> Unit,
        onBlockResult: (BlockResult) -> Unit,
        onLog: (String) -> Unit
    ): RepackSummary {
        pak.file.copyTo(outputPak, overwrite = true)
        val effectiveDict = if (detectedMode == "OBBZSDIC") pak.zstdDictBytes else null

        val nameMap = HashMap<String, MutableList<Pair<String, TencentPakEntry>>>()
        val fmap = pak.buildFullPathMap()
        for ((full, entry) in fmap) {
            nameMap.getOrPut(File(full).name.lowercase()) { mutableListOf() }.add(full to entry)
        }

        val editFiles = editedRoot.walkTopDown().filter { it.isFile && it.name.lowercase() !in SKIP_NAMES }.toList()
        val matched = LinkedHashMap<String, Pair<File, TencentPakEntry>>()
        for (p in editFiles) {
            val cands = nameMap[p.name.lowercase()].orEmpty()
            if (cands.size == 1) {
                matched[cands[0].first] = p to cands[0].second
            } else if (cands.size > 1) {
                smartResolveByFingerprint(p, cands)?.let { matched[it.first] = p to it.second }
            }
        }
        require(matched.isNotEmpty()) { "No matching entries found for in-place block repack." }

        var fittedTotal = 0
        var skippedTotal = 0
        var doneFiles = 0

        RandomAccessFile(pak.file, "r").use { srcRaf ->
            RandomAccessFile(outputPak, "rw").use { outRaf ->
                for ((fullPath, pair) in matched) {
                    val (diskFile, entry) = pair
                    var newData = diskFile.readBytes()
                    val em = entry.encryptionMethod
                    val cm = entry.compressionMethod

                    if (cm == PakCompression.CM_NONE) {
                        val encRegion = if (entry.encrypted) {
                            PakCrypto.alignEncryptedContentSize(entry.size.toInt(), em)
                        } else entry.size.toInt()
                        val slice = newData.copyOfRange(0, minOf(newData.size, encRegion))
                        val toWrite = if (entry.encrypted) {
                            val padded = PakCrypto.padToN(slice, PakCrypto.alignEncryptedContentSize(1, em))
                            PakCrypto.encryptPlaintext(padded, fullPath, em)
                        } else slice
                        outRaf.seek(entry.offset)
                        outRaf.write(toWrite)
                        fittedTotal++
                        onBlockResult(BlockResult(diskFile.name, 0, toWrite.size.toLong(), true, 1f))
                    } else {
                        val blocks = entry.compressedBlocks
                        val order = PakCrypto.generateBlockIndices(blocks.size, em)
                        val uncSize = entry.uncompressedSize.toInt()
                        if (newData.size < uncSize) {
                            newData = newData + ByteArray(uncSize - newData.size)
                        } else if (newData.size > uncSize) {
                            newData = newData.copyOfRange(0, uncSize)
                        }

                        val chunkSize = if (entry.compressionBlockSize > 0) {
                            entry.compressionBlockSize
                        } else 65536

                        var ptr = 0
                        for ((logicalIdx, physIdx) in order.withIndex()) {
                            val blk = blocks[physIdx]
                            val targetSize = (blk.end - blk.start).toInt()
                            val chunkLen = minOf(chunkSize, newData.size - ptr)
                            if (chunkLen <= 0) break
                            val chunk = newData.copyOfRange(ptr, ptr + chunkLen)
                            ptr += chunkLen

                            val origBytes = peekRange(srcRaf, blk.start, targetSize)
                            var compBytes: ByteArray? = null

                            if (cm == PakCompression.CM_ZSTD || cm == PakCompression.CM_ZSTD_DICT) {
                                val dict = if (cm == PakCompression.CM_ZSTD_DICT) effectiveDict else null
                                for (lvl in intArrayOf(19, 16, 13, 10, 7, 4, 1)) {
                                    val c = PakCompression.compressZstd(chunk, lvl, dict)
                                    if (c.size <= targetSize) {
                                        compBytes = c
                                        break
                                    }
                                }
                            } else if (cm == PakCompression.CM_ZLIB) {
                                val c = PakCompression.compressZlib(chunk, 9)
                                if (c.size <= targetSize) compBytes = c
                            }

                            var finalBytes = compBytes
                            if (finalBytes != null && entry.encrypted) {
                                finalBytes = PakCrypto.encryptPlaintext(finalBytes, fullPath, em)
                            }

                            if (finalBytes == null || finalBytes.size > targetSize) {
                                outRaf.seek(blk.start)
                                outRaf.write(origBytes)
                                skippedTotal++
                                onBlockResult(BlockResult(diskFile.name, logicalIdx, targetSize.toLong(), false))
                            } else {
                                outRaf.seek(blk.start)
                                outRaf.write(finalBytes)
                                if (finalBytes.size < targetSize) {
                                    outRaf.write(ByteArray(targetSize - finalBytes.size))
                                }
                                fittedTotal++
                                val ratio = finalBytes.size.toFloat() / chunk.size.coerceAtLeast(1).toFloat()
                                onBlockResult(BlockResult(diskFile.name, logicalIdx, targetSize.toLong(), true, ratio))
                            }
                        }
                    }

                    doneFiles++
                    onProgress(doneFiles.toFloat() / matched.size.toFloat(), "Repacked ${diskFile.name}")
                    onLog("✓ Processed ${diskFile.name}")
                }
            }
        }

        return RepackSummary(
            outputFileName = outputPak.name,
            outputAbsolutePath = outputPak.absolutePath,
            processedFiles = doneFiles,
            fittedBlocks = fittedTotal,
            skippedBlocks = skippedTotal,
            modeLabel = "IN-PLACE ($detectedMode)"
        )
    }

    /**
     * Option 4: Multi-File Inject + Full PAK Rebuild (supports replacing existing + adding new + optional SM4 Type 47)
     */
    fun injectFiles(
        pak: LoadedTencentPak,
        editRoot: File,
        outputPak: File,
        protectNew: Boolean = true,
        sm4Type: Int = 47,
        onProgress: (Float, String) -> Unit,
        onLog: (String) -> Unit
    ): Pair<Int, Int> {
        val version = pak.pakInfo.version
        require(version >= 12) { "Unsupported PAK version $version (need >= 12)" }

        val editFiles = editRoot.walkTopDown().filter { it.isFile && it.name.lowercase() !in SKIP_NAMES }.toList()
        require(editFiles.isNotEmpty()) { "No files found in edit folder." }

        val allDirs = LinkedHashMap<String, LinkedHashMap<String, TencentPakEntry>>()
        for ((k, v) in pak.rawDirs) {
            allDirs[k] = LinkedHashMap(v)
        }

        data class InjectionPlan(val file: File, val template: TencentPakEntry?, val isNew: Boolean)
        val injections = LinkedHashMap<String, InjectionPlan>()

        for (p in editFiles) {
            val rel = p.relativeTo(editRoot).path.replace('\\', '/')
            val fileName = p.name
            val rawRelDir = rel.substringBeforeLast('/', "")
            val strippedDir = stripMountPrefix(pak, rawRelDir)
            val targetDir = if (strippedDir.isBlank()) {
                ""
            } else {
                findExistingDir(allDirs, strippedDir) ?: normalizeDirKey(strippedDir)
            }

            val existing = allDirs[targetDir]?.entries?.firstOrNull {
                it.key.equals(fileName, ignoreCase = true)
            }
            if (existing != null) {
                val fullPath = LoadedTencentPak.joinPakPath(targetDir, existing.key)
                injections[fullPath] = InjectionPlan(p, existing.value, false)
                onLog("● Replace: $fullPath")
            } else {
                val fullPath = LoadedTencentPak.joinPakPath(targetDir, fileName)
                val template = pickTemplate(pak, allDirs, targetDir, fileName)
                injections[fullPath] = InjectionPlan(p, template, true)
                onLog("● Add new: $fullPath")
            }
        }

        val newFiles = pak.files.map { it.deepCopy() }.toMutableList()
        val oldToNew = IdentityHashMap<TencentPakEntry, TencentPakEntry>()
        for (i in pak.files.indices) {
            oldToNew[pak.files[i]] = newFiles[i]
        }

        outputPak.parentFile?.mkdirs()
        var currentOffset = 0L
        var editedCount = 0
        var addedCount = 0
        val totalEntries = allDirs.values.sumOf { it.size }.coerceAtLeast(1)
        var cursorIdx = 0

        RandomAccessFile(pak.file, "r").use { srcRaf ->
            BufferedOutputStream(FileOutputStream(outputPak), 256 * 1024).use { bos ->
                for ((dpStr, dirFiles) in allDirs) {
                    for ((name, oldEntry) in dirFiles) {
                        val fullPath = LoadedTencentPak.joinPakPath(dpStr, name)
                        val ne = oldToNew.getOrPut(oldEntry) {
                            oldEntry.deepCopy().also { newFiles.add(it) }
                        }

                        val plan = injections[fullPath]
                        if (plan != null) {
                            val newRaw = plan.file.readBytes()
                            ne.contentHash = PakCrypto.sha1(newRaw)
                            ne.uncompressedSize = newRaw.size.toLong()
                            ne.compressionMethod = oldEntry.compressionMethod
                            if (protectNew) {
                                ne.encryptionMethod = sm4Type
                                ne.encrypted = true
                            } else {
                                ne.encryptionMethod = oldEntry.encryptionMethod
                                ne.encrypted = oldEntry.encrypted
                            }
                            ne.unk1 = oldEntry.unk1
                            ne.compressionBlockSize = if (oldEntry.compressionBlockSize > 0) oldEntry.compressionBlockSize else 65536
                            ne.indexNewSep = oldEntry.indexNewSep
                            ne.unk2 = PakCrypto.sha1((pak.rawMountPoint + fullPath).lowercase().toByteArray(Charsets.UTF_8))

                            currentOffset = writeEntryContentStream(
                                bos, currentOffset, ne, newRaw, fullPath, pak.zstdDictBytes, fast = false
                            )
                            editedCount++
                            onLog("✓ Replaced: $fullPath (${newRaw.size} B)")
                        } else {
                            currentOffset = copyOriginalContentStream(srcRaf, bos, currentOffset, ne, oldEntry)
                        }

                        cursorIdx++
                        if (cursorIdx % 120 == 0 || cursorIdx == totalEntries) {
                            onProgress((cursorIdx.toFloat() / totalEntries.toFloat()) * 0.9f, "Rebuilding PAK ($cursorIdx/$totalEntries)")
                        }
                    }
                }

                // Append brand-new entries
                for ((fullPath, plan) in injections) {
                    if (!plan.isNew) continue
                    val template = plan.template
                    val ne = template?.deepCopy() ?: TencentPakEntry(
                        contentHash = ByteArray(20),
                        offset = 0L,
                        uncompressedSize = 0L,
                        compressionMethod = PakCompression.CM_NONE,
                        size = 0L,
                        unk1 = 0,
                        unk2 = ByteArray(20),
                        compressedBlocks = mutableListOf(),
                        compressionBlockSize = 65536,
                        encrypted = false,
                        encryptionMethod = 0,
                        indexNewSep = 0
                    )
                    val newRaw = plan.file.readBytes()
                    ne.contentHash = PakCrypto.sha1(newRaw)
                    ne.uncompressedSize = newRaw.size.toLong()
                    ne.compressionMethod = pak.defaultCompressionMethod()
                    if (protectNew) {
                        ne.encryptionMethod = sm4Type
                        ne.encrypted = true
                    } else {
                        ne.encryptionMethod = template?.encryptionMethod ?: 0
                        ne.encrypted = template?.encrypted ?: false
                    }
                    ne.unk1 = template?.unk1 ?: 0
                    ne.compressionBlockSize = template?.compressionBlockSize?.takeIf { it > 0 } ?: 65536
                    ne.indexNewSep = template?.indexNewSep ?: 0
                    ne.unk2 = PakCrypto.sha1((pak.rawMountPoint + fullPath).lowercase().toByteArray(Charsets.UTF_8))

                    currentOffset = writeEntryContentStream(
                        bos, currentOffset, ne, newRaw, fullPath, pak.zstdDictBytes, fast = false
                    )
                    newFiles.add(ne)
                    oldToNew[ne] = ne

                    val dpKey = if ('/' in fullPath) fullPath.substringBeforeLast('/') + "/" else ""
                    val leaf = fullPath.substringAfterLast('/')
                    allDirs.getOrPut(dpKey) { LinkedHashMap() }[leaf] = ne
                    addedCount++
                    onLog("✓ Added new: $fullPath (${newRaw.size} B)")
                }

                writePakIndexAndFooter(pak, allDirs, newFiles, oldToNew, bos, currentOffset)
            }
        }

        onProgress(1f, "PAK rebuild complete")
        return editedCount to addedCount
    }

    /**
     * Option 5: Protect PAK — Re-encrypt every decryptable entry with game-native SM4 (Type 47).
     */
    fun protectPak(
        pakFile: File,
        outputPak: File,
        sm4Type: Int = 47,
        onProgress: (Float, String) -> Unit,
        onLog: (String) -> Unit
    ): Pair<Int, Int> {
        onLog("🔒 Loading ${pakFile.name} for SM4 protection…")
        val pak = loadPak(pakFile, onLog = onLog)
        val version = pak.pakInfo.version
        require(version >= 12) { "Unsupported PAK version $version (need >= 12)" }

        val allDirs = LinkedHashMap<String, LinkedHashMap<String, TencentPakEntry>>()
        for ((k, v) in pak.rawDirs) {
            allDirs[k] = LinkedHashMap(v)
        }

        val newFiles = pak.files.map { it.deepCopy() }.toMutableList()
        val oldToNew = IdentityHashMap<TencentPakEntry, TencentPakEntry>()
        for (i in pak.files.indices) {
            oldToNew[pak.files[i]] = newFiles[i]
        }

        outputPak.parentFile?.mkdirs()
        var currentOffset = 0L
        var protectedCount = 0
        var skippedCount = 0
        val total = allDirs.values.sumOf { it.size }.coerceAtLeast(1)
        var done = 0

        RandomAccessFile(pak.file, "r").use { srcRaf ->
            BufferedOutputStream(FileOutputStream(outputPak), 256 * 1024).use { bos ->
                for ((dpStr, dirFiles) in allDirs) {
                    for ((name, oldEntry) in dirFiles) {
                        val fullPath = LoadedTencentPak.joinPakPath(dpStr, name)
                        val ne = oldToNew.getOrPut(oldEntry) {
                            oldEntry.deepCopy().also { newFiles.add(it) }
                        }

                        val isMarker = oldEntry.compressionMethod == PakCompression.CM_NONE && oldEntry.size == 0L
                        val isZdict = pak.zstdDictEntry != null && oldEntry === pak.zstdDictEntry
                        if (isMarker || isZdict) {
                            currentOffset = copyOriginalContentStream(srcRaf, bos, currentOffset, ne, oldEntry)
                            skippedCount++
                        } else {
                            val plaintext = try {
                                extractEntryPlaintext(srcRaf, pak, oldEntry, fullPath)
                            } catch (_: Exception) {
                                null
                            }

                            if (plaintext == null) {
                                currentOffset = copyOriginalContentStream(srcRaf, bos, currentOffset, ne, oldEntry)
                                skippedCount++
                                onLog("! Skipped undecryptable: $fullPath")
                            } else {
                                ne.compressionMethod = if (oldEntry.compressionMethod == PakCompression.CM_NONE) {
                                    PakCompression.CM_ZLIB
                                } else {
                                    oldEntry.compressionMethod
                                }
                                ne.encryptionMethod = sm4Type
                                ne.encrypted = true
                                ne.unk1 = oldEntry.unk1
                                ne.unk2 = oldEntry.unk2
                                ne.indexNewSep = oldEntry.indexNewSep
                                ne.compressionBlockSize = if (oldEntry.compressionBlockSize > 0) oldEntry.compressionBlockSize else 65536
                                currentOffset = writeEntryContentStream(
                                    bos, currentOffset, ne, plaintext, fullPath, pak.zstdDictBytes, fast = true
                                )
                                protectedCount++
                                if (protectedCount <= 12 || protectedCount % 250 == 0) {
                                    onLog("🔒 Re-encrypted SM4-$sm4Type: $fullPath (${plaintext.size} B)")
                                }
                            }
                        }

                        done++
                        if (done % 50 == 0 || done == total) {
                            onProgress((done.toFloat() / total.toFloat()) * 0.95f, "Protecting ($done/$total)")
                        }
                    }
                }

                writePakIndexAndFooter(pak, allDirs, newFiles, oldToNew, bos, currentOffset)
            }
        }

        onProgress(1f, "SM4 Protection complete")
        return protectedCount to skippedCount
    }

    private fun extractEntryPlaintext(
        raf: RandomAccessFile,
        pak: LoadedTencentPak,
        entry: TencentPakEntry,
        fullPath: String
    ): ByteArray {
        val em = entry.encryptionMethod
        val cm = entry.compressionMethod
        if (entry.encrypted && em == PakCrypto.EM_UNKNOWN_17) {
            throw UnsupportedOperationException("ENC 17 unsupported")
        }
        if (cm == PakCompression.CM_NONE) {
            var data = peekEntryContent(raf, entry)
            if (entry.encrypted) {
                data = PakCrypto.decryptBlock(data, fullPath, em)
            }
            val limit = minOf(data.size, entry.uncompressedSize.toInt())
            return data.copyOfRange(0, limit)
        }
        val out = java.io.ByteArrayOutputStream(entry.uncompressedSize.toInt().coerceAtLeast(64))
        val indices = PakCrypto.generateBlockIndices(entry.compressedBlocks.size, em)
        for (x in indices) {
            var data = peekBlockContent(raf, entry.compressedBlocks[x], em)
            if (entry.encrypted) {
                data = PakCrypto.decryptBlock(data, fullPath, em)
            }
            val dec = PakCompression.decompressBlock(data, pak.zstdDictBytes, cm)
            out.write(dec)
        }
        return out.toByteArray()
    }

    private fun writeEntryContentStream(
        bos: BufferedOutputStream,
        startOffset: Long,
        ne: TencentPakEntry,
        plaintext: ByteArray,
        fullPath: String,
        zstdDictBytes: ByteArray?,
        fast: Boolean
    ): Long {
        var offset = startOffset
        if (ne.compressionMethod == PakCompression.CM_NONE) {
            val cipher = if (ne.encrypted) {
                PakCrypto.encryptPlaintext(plaintext, fullPath, ne.encryptionMethod)
            } else plaintext
            ne.offset = offset
            ne.size = plaintext.size.toLong()
            ne.uncompressedSize = plaintext.size.toLong()
            bos.write(cipher)
            return offset + cipher.size
        }

        val cs = if (ne.compressionBlockSize > 0) ne.compressionBlockSize else 65536
        if (plaintext.isEmpty()) {
            ne.compressedBlocks = mutableListOf()
            ne.offset = offset
            ne.size = 0L
            ne.uncompressedSize = 0L
            return offset
        }

        val chunks = mutableListOf<ByteArray>()
        var i = 0
        while (i < plaintext.size) {
            val end = minOf(i + cs, plaintext.size)
            chunks.add(plaintext.copyOfRange(i, end))
            i = end
        }
        val n = chunks.size
        val inv = if (ne.encrypted) {
            PakCrypto.generateBlockIndices(n, ne.encryptionMethod)
        } else IntArray(n) { it }
        val fileOrder = IntArray(n)
        for (j in 0 until n) {
            fileOrder[inv[j]] = j
        }

        val newBlocks = ArrayList<PakCompressedBlock>(n)
        var totalSize = 0L
        for (k in 0 until n) {
            val chunk = chunks[fileOrder[k]]
            val compressed = PakCompression.bestCompress(chunk, ne.compressionMethod, zstdDictBytes, fast)
            val cipher = if (ne.encrypted) {
                PakCrypto.encryptPlaintext(compressed, fullPath, ne.encryptionMethod)
            } else compressed
            val blk = PakCompressedBlock(offset, offset + cipher.size)
            bos.write(cipher)
            offset += cipher.size
            totalSize += cipher.size
            newBlocks.add(blk)
        }

        ne.compressedBlocks = newBlocks
        ne.offset = newBlocks[0].start
        ne.size = totalSize
        ne.uncompressedSize = plaintext.size.toLong()
        return offset
    }

    private fun copyOriginalContentStream(
        srcRaf: RandomAccessFile,
        bos: BufferedOutputStream,
        startOffset: Long,
        ne: TencentPakEntry,
        oldEntry: TencentPakEntry
    ): Long {
        var offset = startOffset
        val em = oldEntry.encryptionMethod
        if (oldEntry.compressionMethod == PakCompression.CM_NONE) {
            val readSz = if (oldEntry.encrypted) {
                PakCrypto.alignEncryptedContentSize(oldEntry.size.toInt(), em)
            } else oldEntry.size.toInt()
            ne.offset = offset
            streamCopy(srcRaf, oldEntry.offset, readSz, bos)
            offset += readSz
        } else if (oldEntry.compressedBlocks.isNotEmpty()) {
            val newBlks = ArrayList<PakCompressedBlock>(oldEntry.compressedBlocks.size)
            for (ob in oldEntry.compressedBlocks) {
                val unc = (ob.end - ob.start).toInt()
                val enc = if (oldEntry.encrypted) PakCrypto.alignEncryptedContentSize(unc, em) else unc
                val nb = PakCompressedBlock(offset, offset + unc)
                streamCopy(srcRaf, ob.start, enc, bos)
                offset += enc
                newBlks.add(nb)
            }
            ne.compressedBlocks = newBlks
            ne.offset = newBlks[0].start
        }
        ne.encryptionMethod = oldEntry.encryptionMethod
        ne.encrypted = oldEntry.encrypted
        return offset
    }

    private fun streamCopy(srcRaf: RandomAccessFile, srcOffset: Long, length: Int, bos: BufferedOutputStream) {
        if (length <= 0) return
        srcRaf.seek(srcOffset)
        val buf = ByteArray(minOf(length, 65536))
        var remaining = length
        while (remaining > 0) {
            val toRead = minOf(remaining, buf.size)
            val r = srcRaf.read(buf, 0, toRead)
            if (r <= 0) break
            bos.write(buf, 0, r)
            remaining -= r
        }
    }

    private fun writePakIndexAndFooter(
        pak: LoadedTencentPak,
        allDirs: LinkedHashMap<String, LinkedHashMap<String, TencentPakEntry>>,
        newFiles: List<TencentPakEntry>,
        oldToNew: IdentityHashMap<TencentPakEntry, TencentPakEntry>,
        bos: BufferedOutputStream,
        indexOffset: Long
    ) {
        val version = pak.pakInfo.version
        val keystream = PakCrypto.zucKeystream()

        val newEntryIndex = IdentityHashMap<TencentPakEntry, Int>()
        newFiles.forEachIndexed { idx, entry -> newEntryIndex[entry] = idx }
        val oldEntryIndex = IdentityHashMap<TencentPakEntry, Int>()
        pak.files.forEachIndexed { idx, entry -> oldEntryIndex[entry] = idx }

        val w = BinaryWriter(64 * 1024)
        w.pakString(pak.rawMountPoint)
        w.u4(newFiles.size.toLong())
        for (ne in newFiles) {
            ne.serialize(version, w)
        }
        w.u8(allDirs.size.toLong())
        for ((dpStr, dirFiles) in allDirs) {
            w.pakString(dpStr)
            w.u8(dirFiles.size.toLong())
            for ((name, entryRef) in dirFiles) {
                w.pakString(name)
                var foundIdx = newEntryIndex[entryRef]
                if (foundIdx == null) foundIdx = oldEntryIndex[entryRef]
                if (foundIdx == null) {
                    val mapped = oldToNew[entryRef]
                    if (mapped != null) foundIdx = newEntryIndex[mapped]
                }
                if (foundIdx == null) {
                    foundIdx = newFiles.indexOfFirst { it.offset == entryRef.offset && it.size == entryRef.size }
                        .takeIf { it >= 0 }
                }
                w.i4(if (foundIdx != null) foundIdx.inv() else -1)
            }
        }

        val indexPlain = w.toByteArray()
        val newSha1 = PakCrypto.sha1(indexPlain)

        val indexBytes = if (pak.pakInfo.indexEncrypted) {
            PakCrypto.encryptIndex(indexPlain, pak.pakInfo)
        } else {
            indexPlain
        }

        bos.write(indexBytes)

        val newFooter = pak.pakInfo.rawFooter.copyOf()
        val footerLen = newFooter.size

        // Update index_hash (offset footerLen - 36 .. footerLen - 16)
        val hKey = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN).apply {
            for (i in 4 until 9) putInt(keystream[i].toInt())
        }.array()
        for (i in 0 until 20) {
            newFooter[footerLen - 36 + i] = (newSha1[i].toInt() xor hKey[i].toInt()).toByte()
        }

        // Update index_size (footerLen - 16 .. footerLen - 8)
        val encSize = indexBytes.size.toLong() xor ((keystream[10] shl 32) or keystream[11])
        ByteBuffer.wrap(newFooter, footerLen - 16, 8).order(ByteOrder.LITTLE_ENDIAN).putLong(encSize)

        // Update index_offset (footerLen - 8 .. footerLen)
        val encOffset = indexOffset xor ((keystream[0] shl 32) or keystream[1])
        ByteBuffer.wrap(newFooter, footerLen - 8, 8).order(ByteOrder.LITTLE_ENDIAN).putLong(encOffset)

        bos.write(newFooter)
        bos.flush()
    }

    fun writeDebugLog(pak: LoadedTencentPak, outFile: File) {
        outFile.bufferedWriter(Charsets.UTF_8).use { w ->
            w.appendLine("================================================================================")
            w.appendLine("MAJLISH PAK UNPACKING DEBUG LOG")
            w.appendLine("================================================================================")
            w.appendLine("PAK File: ${pak.file.name}")
            w.appendLine("PAK Info Version: ${pak.pakInfo.version}")
            w.appendLine("Mount Point: ${pak.mountPoint}")
            w.appendLine("--------------------------------------------------------------------------------")
            var idx = 0
            for ((dir, files) in pak.indexDirs) {
                for ((name, entry) in files) {
                    idx++
                    val full = LoadedTencentPak.joinPakPath(dir, name)
                    w.appendLine("\n[$idx] $full")
                    w.appendLine("  Uncompressed Size: ${entry.uncompressedSize} bytes")
                    w.appendLine("  Compressed Size: ${entry.size} bytes")
                    w.appendLine("  Compression Method: ${entry.compressionMethod} (${PakCrypto.methodName(entry.compressionMethod, false)})")
                    w.appendLine("  Encryption Method: ${entry.encryptionMethod} (${PakCrypto.methodName(entry.encryptionMethod, true)})")
                    w.appendLine("  Compressed Blocks: ${entry.compressedBlocks.size}")
                    entry.compressedBlocks.forEachIndexed { bIdx, blk ->
                        w.appendLine("    Block $bIdx: Offset=${blk.start} Size=${blk.end - blk.start} bytes")
                    }
                }
            }
            w.appendLine("\n================================================================================")
            w.appendLine("END OF LOG")
        }
    }

    fun writePackIndexCsv(pak: LoadedTencentPak, csvFile: File): Int {
        csvFile.parentFile?.mkdirs()
        val paths = pak.buildFullPathMap().keys.sorted()
        csvFile.bufferedWriter(Charsets.UTF_8).use { w ->
            for (p in paths) w.appendLine(p)
        }
        return paths.size
    }

    fun loadIndexCsv(csvFile: File): List<String> {
        if (!csvFile.exists()) return emptyList()
        return csvFile.readLines(Charsets.UTF_8)
            .map { it.trim().replace('\\', '/').trimStart('/') }
            .filter { it.isNotEmpty() }
    }

    private fun findIndexCsv(workspaceRoot: File, pakName: String): File? {
        val candidates = listOf(
            File(workspaceRoot, "EXE/index.csv"),
            File(workspaceRoot, "UNPACK/$pakName/index.csv"),
            File(workspaceRoot, "EXE/index_$pakName.csv")
        )
        return candidates.firstOrNull { it.exists() && it.length() > 0L }
    }

    fun findOriginalPakForUnpack(workspaceRoot: File, pakName: String): File? {
        val dirs = listOf(
            File(workspaceRoot, "PAK"),
            File(workspaceRoot, "PAK TOOL/PAK"),
            File(workspaceRoot, "RESULT")
        )
        for (d in dirs) {
            if (!d.exists()) continue
            val exact = File(d, "$pakName.pak")
            if (exact.exists()) return exact
            val match = d.listFiles()?.firstOrNull {
                it.isFile && it.extension.equals("pak", ignoreCase = true) &&
                    it.nameWithoutExtension.equals(pakName, ignoreCase = true)
            }
            if (match != null) return match
        }
        return null
    }

    fun detectRepackMode(pakFile: File): String {
        val name = pakFile.name.lowercase()
        return when {
            name == "mini_obb.pak" -> "MINI_OBB"
            "zsdic" in name -> "OBBZSDIC"
            "game" in name || "patch" in name -> "GAMEPATCH"
            else -> "OBBZSDIC"
        }
    }

    private fun smartResolveByFingerprint(
        repackFile: File,
        candidates: List<Pair<String, TencentPakEntry>>
    ): Pair<String, TencentPakEntry>? {
        val sz = repackFile.length()
        val sizeMatches = candidates.filter { it.second.uncompressedSize == sz }
        if (sizeMatches.size == 1) return sizeMatches[0]
        if (sizeMatches.isEmpty()) return null
        fun fp(e: TencentPakEntry) = listOf(
            e.uncompressedSize, e.size, e.compressionMethod.toLong(),
            e.compressedBlocks.size.toLong(), e.compressionBlockSize.toLong()
        )
        val base = fp(sizeMatches[0].second)
        val final = sizeMatches.filter { fp(it.second) == base }
        return if (final.size == 1) final[0] else null
    }

    private fun normalizeDirKey(pathStr: String): String {
        val s = pathStr.replace('\\', '/').trim('/')
        return if (s.isEmpty()) "" else "$s/"
    }

    private fun findExistingDir(
        allDirs: Map<String, Map<String, TencentPakEntry>>,
        wantDir: String
    ): String? {
        val want = normalizeDirKey(wantDir).trim('/').lowercase()
        if (want.isEmpty()) return ""
        allDirs.keys.firstOrNull { it.trim('/').equals(want, ignoreCase = true) }?.let { return it }
        var best: String? = null
        for (k in allDirs.keys) {
            val key = k.trim('/').lowercase()
            if (key.isNotEmpty() && want.endsWith("/$key")) {
                if (best == null || key.length > best.length) best = k
            }
        }
        return best
    }

    private fun stripMountPrefix(pak: LoadedTencentPak, relDir: String): String {
        val mp = pak.mountPoint.replace('\\', '/').trim('/')
        val rd = relDir.replace('\\', '/').trim('/')
        return if (mp.isNotEmpty() && (rd.equals(mp, ignoreCase = true) || rd.startsWith("$mp/", ignoreCase = true))) {
            rd.substring(mp.length).trimStart('/')
        } else rd
    }

    private fun pickTemplate(
        pak: LoadedTencentPak,
        allDirs: Map<String, Map<String, TencentPakEntry>>,
        targetDir: String,
        fileName: String
    ): TencentPakEntry? {
        val ext = File(fileName).extension.lowercase()
        allDirs[targetDir]?.entries?.firstOrNull {
            File(it.key).extension.lowercase() == ext
        }?.value?.let { return it }
        for (files in allDirs.values) {
            files.entries.firstOrNull { File(it.key).extension.lowercase() == ext }?.value?.let { return it }
        }
        return allDirs.values.firstOrNull()?.values?.firstOrNull() ?: pak.files.firstOrNull()
    }

    private fun constructMountPoint(mountPoint: String): String {
        return mountPoint.replace('\\', '/')
            .split('/')
            .filter { it.isNotEmpty() && it != ".." && it != "." }
            .joinToString("/")
    }

    private fun stringToUtf32Le(s: String): ByteArray {
        val codePoints = s.codePoints().toArray()
        val buf = ByteBuffer.allocate(codePoints.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (cp in codePoints) buf.putInt(cp)
        return buf.array()
    }
}
