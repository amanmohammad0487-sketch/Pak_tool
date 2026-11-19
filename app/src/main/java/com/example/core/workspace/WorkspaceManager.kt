package com.example.core.workspace

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import java.io.File

data class WorkspaceFolderInfo(
    val name: String,
    val relativePath: String,
    val file: File,
    val fileCount: Int,
    val sizeBytes: Long,
    val isProtectedSystemDir: Boolean
)

data class WorkspaceFileItem(
    val name: String,
    val relativePath: String,
    val file: File,
    val sizeBytes: Long,
    val lastModified: Long
)

/**
 * Manages the shared Internal Storage folder:
 *   /storage/emulated/0/MAJLISH
 * Automatically creates every directory from APP.py on startup and auto-detects
 * .pak files, unpacked folders, edited files, generated results, and .lua/.luac scripts.
 */
class WorkspaceManager(private val context: Context) {

    companion object {
        const val ROOT_FOLDER_NAME = "MAJLISH"

        fun formatSize(bytes: Long): String {
            var value = bytes.toDouble()
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            var idx = 0
            while (value >= 1024.0 && idx < units.lastIndex) {
                value /= 1024.0
                idx++
            }
            return if (idx == 0) "$bytes B" else "%.2f %s".format(value, units[idx])
        }
    }

    fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Points directly to /storage/emulated/0/MAJLISH when storage permission is granted,
     * falling back to app external files dir only before permission is granted.
     */
    val rootDir: File
        get() {
            return if (hasAllFilesAccess()) {
                File(Environment.getExternalStorageDirectory(), ROOT_FOLDER_NAME)
            } else {
                val fallback = context.getExternalFilesDir(null) ?: context.filesDir
                File(fallback, ROOT_FOLDER_NAME)
            }
        }

    val displayRootPath: String
        get() = if (hasAllFilesAccess()) {
            "Internal Storage/$ROOT_FOLDER_NAME"
        } else {
            "Grant storage access for /$ROOT_FOLDER_NAME"
        }

    val pakDir: File get() = File(rootDir, "PAK")
    val unpackDir: File get() = File(rootDir, "UNPACK")
    val repackDir: File get() = File(rootDir, "REPACK")
    val resultDir: File get() = File(rootDir, "RESULT")
    val exeDir: File get() = File(rootDir, "EXE")

    val pakToolPakDir: File get() = File(rootDir, "PAK TOOL/PAK")
    val pakToolEditDir: File get() = File(rootDir, "PAK TOOL/EDIT")
    val pakToolUnpackDir: File get() = File(rootDir, "PAK TOOL/UNPACK")
    val pakToolResultDir: File get() = File(rootDir, "PAK TOOL/RESULT")

    val luaBaseDir: File get() = File(rootDir, "LUA TOOL")
    val luaOrgDir: File get() = File(rootDir, "LUA TOOL/Lua_org")
    val luaDecriptDir: File get() = File(rootDir, "LUA TOOL/Lua_Decript")
    val luaEditDir: File get() = File(rootDir, "LUA TOOL/EDIT_LUA")
    val luaResultDir: File get() = File(rootDir, "LUA TOOL/Lua_Rueslt")

    private val protectedRootNames = setOf(
        "PAK", "UNPACK", "REPACK", "RESULT", "PAK TOOL", "EXE", "LUA TOOL"
    )

    /**
     * Automatically creates Internal Storage/MAJLISH and every subfolder defined in APP.py.
     */
    fun ensureDirectories(): Boolean {
        val dirs = listOf(
            rootDir,
            pakDir, unpackDir, repackDir, resultDir, exeDir,
            pakToolPakDir, pakToolEditDir, pakToolUnpackDir, pakToolResultDir,
            luaOrgDir, luaDecriptDir, luaEditDir, luaResultDir
        )
        var allOk = true
        for (d in dirs) {
            if (!d.exists()) {
                val created = d.mkdirs()
                if (!created && !d.exists()) allOk = false
            }
        }
        return allOk
    }

    fun listPakFiles(dir: File): List<WorkspaceFileItem> =
        listFilesIn(dir, recursive = false)
            .filter { it.name.endsWith(".pak", ignoreCase = true) }
            .sortedBy { it.name.lowercase() }

    /**
     * Auto-detects .pak files in MAJLISH/PAK first, plus MAJLISH/PAK TOOL/PAK and root MAJLISH/.
     */
    fun listUnpackCandidatePaks(): List<WorkspaceFileItem> {
        val primary = listPakFiles(pakDir)
        val secondary = listPakFiles(pakToolPakDir)
        val rootPaks = listPakFiles(rootDir)
        return (primary + secondary + rootPaks).distinctBy { it.file.absolutePath }
    }

    /**
     * Auto-detects .pak files in MAJLISH/PAK TOOL/PAK first, plus MAJLISH/PAK and MAJLISH/RESULT.
     */
    fun listPakToolAndMainPaks(): List<WorkspaceFileItem> {
        val primary = listPakFiles(pakToolPakDir)
        val secondary = listPakFiles(pakDir)
        val results = listPakFiles(resultDir)
        return (primary + secondary + results).distinctBy { it.file.absolutePath }
    }

    fun listUnpackedPaks(): List<WorkspaceFolderInfo> {
        if (!unpackDir.exists()) return emptyList()
        return unpackDir.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedBy { it.name.lowercase() }
            ?.map { folder ->
                val (count, size) = measureDir(folder)
                WorkspaceFolderInfo(
                    name = folder.name,
                    relativePath = "UNPACK/${folder.name}",
                    file = folder,
                    fileCount = count,
                    sizeBytes = size,
                    isProtectedSystemDir = false
                )
            } ?: emptyList()
    }

    fun listEditedFilesForUnpack(unpackFolder: File): List<WorkspaceFileItem> {
        val editedDir = File(unpackFolder, "edited").apply { if (!exists()) mkdirs() }
        return listFilesIn(editedDir, recursive = true)
            .filter {
                val lower = it.name.lowercase()
                lower !in setOf("thumbs.db", ".ds_store", "desktop.ini") && !it.name.startsWith("Debug_")
            }
    }

    fun listFilesIn(dir: File, recursive: Boolean = true): List<WorkspaceFileItem> {
        if (!dir.exists()) return emptyList()
        val seq = if (recursive) {
            dir.walkTopDown().filter { it.isFile }
        } else {
            dir.listFiles()?.asSequence()?.filter { it.isFile } ?: emptySequence()
        }
        return seq
            .sortedBy { it.name.lowercase() }
            .map { f ->
                WorkspaceFileItem(
                    name = f.name,
                    relativePath = runCatching { f.relativeTo(dir).path.replace('\\', '/') }.getOrDefault(f.name),
                    file = f,
                    sizeBytes = f.length(),
                    lastModified = f.lastModified()
                )
            }
            .toList()
    }

    fun listAllOutputPaks(): List<WorkspaceFileItem> =
        (listPakFiles(resultDir) + listPakFiles(pakToolResultDir))
            .distinctBy { it.file.absolutePath }
            .sortedByDescending { it.lastModified }

    /**
     * Option 07 EXPORT RESULT: saves a generated .pak directly to phone Download/MAJLISH_EXPORT/.
     */
    fun exportPakToDownloads(sourcePak: File): File {
        require(sourcePak.exists() && sourcePak.isFile) { "PAK file not found: ${sourcePak.name}" }
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val exportFolder = File(downloadsDir, "MAJLISH_EXPORT").apply { mkdirs() }
        val target = File(exportFolder, sourcePak.name)
        sourcePak.copyTo(target, overwrite = true)
        return target
    }

    fun listWorkspaceFolders(): List<WorkspaceFolderInfo> {
        ensureDirectories()
        val dirs = listOf(
            pakDir, unpackDir, repackDir, resultDir, exeDir,
            pakToolPakDir, pakToolEditDir, pakToolUnpackDir, pakToolResultDir,
            luaOrgDir, luaDecriptDir, luaEditDir, luaResultDir
        )
        return dirs.map { d ->
            val (count, size) = measureDir(d)
            val rel = runCatching { d.relativeTo(rootDir).path.replace('\\', '/') }.getOrDefault(d.name)
            WorkspaceFolderInfo(
                name = rel,
                relativePath = rel,
                file = d,
                fileCount = count,
                sizeBytes = size,
                isProtectedSystemDir = true
            )
        }
    }

    fun listDeletableFolders(): List<WorkspaceFolderInfo> {
        ensureDirectories()
        val result = mutableListOf<WorkspaceFolderInfo>()
        rootDir.listFiles()
            ?.filter { it.isDirectory && it.name !in protectedRootNames && !it.name.startsWith(".") }
            ?.forEach { f ->
                val (count, size) = measureDir(f)
                result.add(WorkspaceFolderInfo(f.name, f.name, f, count, size, false))
            }
        result.addAll(listUnpackedPaks())
        return result.sortedBy { it.relativePath.lowercase() }
    }

    fun deleteFolder(folder: File): Boolean {
        if (!folder.exists()) return false
        val canonicalRoot = rootDir.canonicalPath
        val canonicalTarget = folder.canonicalPath
        if (!canonicalTarget.startsWith(canonicalRoot) || canonicalTarget == canonicalRoot) return false
        return folder.deleteRecursively()
    }

    private fun measureDir(dir: File): Pair<Int, Long> {
        var count = 0
        var size = 0L
        dir.walkTopDown().forEach {
            if (it.isFile) {
                count++
                size += it.length()
            }
        }
        return count to size
    }
}
