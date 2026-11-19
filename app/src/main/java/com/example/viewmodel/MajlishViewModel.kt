package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.auth.AuthResult
import com.example.core.auth.LicenseAuthService
import com.example.core.auth.SessionStore
import com.example.core.lua.LuaBytecodeEngine
import com.example.core.pak.BlockResult
import com.example.core.pak.RepackResolveMode
import com.example.core.pak.RepackSummary
import com.example.core.pak.TencentPakEngine
import com.example.core.pak.UnpackSummary
import com.example.core.workspace.WorkspaceFileItem
import com.example.core.workspace.WorkspaceFolderInfo
import com.example.core.workspace.WorkspaceManager
import com.example.ui.components.ToolActionFlow
import com.example.ui.theme.GlassThemePreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object Verifying : AuthUiState
    data class Granted(val message: String) : AuthUiState
    data class Denied(val reason: String) : AuthUiState
}

data class MajlishUiState(
    val isAuthenticated: Boolean = false,
    val authUiState: AuthUiState = AuthUiState.Idle,
    val keyInput: String = "",
    val activeKey: String = "",
    val licenseExpiry: String = "N/A",
    val deviceId: String = "",
    val deviceName: String = "",
    val themePreset: GlassThemePreset = GlassThemePreset.FROST_LIGHT,

    // Shared Internal Storage/MAJLISH state
    val hasStorageAccess: Boolean = false,
    val storagePathLabel: String = "Internal Storage/MAJLISH",
    val showStoragePrompt: Boolean = false,

    // Active Liquid Glass modal
    val actionFlow: ToolActionFlow = ToolActionFlow.None,

    // Auto-detected workspace snapshot inside /storage/emulated/0/MAJLISH
    val pakFiles: List<WorkspaceFileItem> = emptyList(),
    val pakToolPakFiles: List<WorkspaceFileItem> = emptyList(),
    val pakToolEditFiles: List<WorkspaceFileItem> = emptyList(),
    val unpackedFolders: List<WorkspaceFolderInfo> = emptyList(),
    val outputPaks: List<WorkspaceFileItem> = emptyList(),
    val workspaceFolders: List<WorkspaceFolderInfo> = emptyList(),
    val deletableFolders: List<WorkspaceFolderInfo> = emptyList(),
    val luaOrgFiles: List<WorkspaceFileItem> = emptyList(),
    val luaDecriptFiles: List<WorkspaceFileItem> = emptyList(),
    val luaEditFiles: List<WorkspaceFileItem> = emptyList(),
    val luaResultFiles: List<WorkspaceFileItem> = emptyList(),

    // Options inside confirmation dialogs
    val repackResolveMode: RepackResolveMode = RepackResolveMode.FOLDER_WISE,
    val targetPathInput: String = "Content/Lua/GameLua/Mod/BRMod/Gameplay/Core",
    val injectEncryptSm4: Boolean = true,
    val customLuaXorHex: String = "",

    // Live operation progress & console output
    val isBusy: Boolean = false,
    val busyTitle: String = "",
    val busyDetail: String = "",
    val busyProgress: Float = 0f,
    val logs: List<String> = emptyList(),
    val blockResults: List<BlockResult> = emptyList(),
    val lastUnpackSummary: UnpackSummary? = null,
    val lastRepackSummary: RepackSummary? = null,
    val snackbarMessage: String? = null
)

class MajlishViewModel(application: Application) : AndroidViewModel(application) {

    private val authService = LicenseAuthService(application)
    private val sessionStore = SessionStore(application)
    val workspace = WorkspaceManager(application)

    private val _uiState = MutableStateFlow(
        MajlishUiState(
            deviceId = authService.getDeviceId(),
            deviceName = authService.getDeviceName(),
            hasStorageAccess = workspace.hasAllFilesAccess(),
            storagePathLabel = workspace.displayRootPath
        )
    )
    val uiState: StateFlow<MajlishUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null
    private var storagePromptDismissedOnce = false

    init {
        checkAndInitializeStorage(showPromptIfMissing = false)
        observeSession()
    }

    fun checkAndInitializeStorage(showPromptIfMissing: Boolean = false) {
        val granted = workspace.hasAllFilesAccess()
        _uiState.update {
            it.copy(
                hasStorageAccess = granted,
                storagePathLabel = workspace.displayRootPath,
                showStoragePrompt = if (granted) false else (showPromptIfMissing && !storagePromptDismissedOnce)
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            workspace.ensureDirectories()
            refreshWorkspaceSync()
        }
    }

    fun dismissStoragePrompt() {
        storagePromptDismissedOnce = true
        _uiState.update { it.copy(showStoragePrompt = false) }
    }

    private fun observeSession() {
        viewModelScope.launch {
            sessionStore.sessionFlow.collect { stored ->
                _uiState.update { state ->
                    state.copy(
                        themePreset = stored.themePreset,
                        customLuaXorHex = stored.customLuaXorHex,
                        keyInput = if (state.keyInput.isEmpty() && !state.isAuthenticated) stored.lastKey else state.keyInput
                    )
                }
            }
        }
    }

    // ==================== AUTH ====================

    fun updateKeyInput(value: String) {
        _uiState.update {
            it.copy(
                keyInput = value,
                authUiState = if (it.authUiState is AuthUiState.Denied) AuthUiState.Idle else it.authUiState
            )
        }
    }

    fun verifyLicenseKey() {
        val key = _uiState.value.keyInput
        if (_uiState.value.authUiState is AuthUiState.Verifying) return
        viewModelScope.launch {
            _uiState.update { it.copy(authUiState = AuthUiState.Verifying) }
            when (val res = authService.verifyKey(key)) {
                is AuthResult.Success -> {
                    sessionStore.saveLastKey(res.key)
                    _uiState.update {
                        it.copy(
                            authUiState = AuthUiState.Granted(res.message),
                            activeKey = res.key,
                            licenseExpiry = res.expiry,
                            deviceId = res.deviceId,
                            deviceName = res.deviceName
                        )
                    }
                    delay(500L)
                    _uiState.update { it.copy(isAuthenticated = true) }
                    checkAndInitializeStorage(showPromptIfMissing = true)
                }
                is AuthResult.Denied -> {
                    _uiState.update { it.copy(authUiState = AuthUiState.Denied(res.reason)) }
                }
            }
        }
    }

    fun signOutAndClearKey() {
        activeJob?.cancel()
        viewModelScope.launch { sessionStore.clearLastKey() }
        _uiState.update {
            it.copy(
                isAuthenticated = false,
                authUiState = AuthUiState.Idle,
                keyInput = "",
                activeKey = "",
                actionFlow = ToolActionFlow.None,
                isBusy = false
            )
        }
    }

    // ==================== MODALS & SETTINGS ====================

    fun openSettings() {
        _uiState.update { it.copy(actionFlow = ToolActionFlow.SettingsMenu) }
    }

    fun openChangeUi() {
        _uiState.update { it.copy(actionFlow = ToolActionFlow.ChangeUiMenu) }
    }

    fun openActionFlow(flow: ToolActionFlow) {
        val needsStorage = flow !is ToolActionFlow.SettingsMenu && flow !is ToolActionFlow.ChangeUiMenu
        if (needsStorage && !workspace.hasAllFilesAccess()) {
            _uiState.update { it.copy(showStoragePrompt = true) }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            workspace.ensureDirectories()
            refreshWorkspaceSync()
            _uiState.update { it.copy(actionFlow = flow) }
        }
    }

    fun closeActionFlow() {
        _uiState.update { it.copy(actionFlow = ToolActionFlow.None) }
    }

    fun onUnpackPakPicked(pakFile: File) {
        _uiState.update { it.copy(actionFlow = ToolActionFlow.ConfirmUnpackPak(pakFile)) }
    }

    fun onRepackFolderPicked(folder: File, forToPath: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val editedFiles = workspace.listEditedFilesForUnpack(folder)
            _uiState.update {
                it.copy(
                    actionFlow = if (forToPath) {
                        ToolActionFlow.ConfirmToPath(folder, editedFiles)
                    } else {
                        ToolActionFlow.ConfirmRepackFolder(folder, editedFiles)
                    }
                )
            }
        }
    }

    fun onInjectPakPicked(pakFile: File) {
        viewModelScope.launch(Dispatchers.IO) {
            val editFiles = workspace.listFilesIn(workspace.pakToolEditDir, recursive = true)
            _uiState.update { it.copy(actionFlow = ToolActionFlow.ConfirmInject(pakFile, editFiles)) }
        }
    }

    fun onProtectPakPicked(pakFile: File) {
        _uiState.update { it.copy(actionFlow = ToolActionFlow.ConfirmProtect(pakFile)) }
    }

    fun onDeleteFolderPicked(folder: WorkspaceFolderInfo) {
        _uiState.update { it.copy(actionFlow = ToolActionFlow.ConfirmDeleteFolder(folder)) }
    }

    fun onExportResultPicked(pakFile: File) {
        _uiState.update { it.copy(actionFlow = ToolActionFlow.ConfirmExportResult(pakFile)) }
    }

    fun onLuaDecompilePicked(files: List<File>) {
        _uiState.update { it.copy(actionFlow = ToolActionFlow.ConfirmLuaDecompile(files)) }
    }

    fun onLuaRecompilePicked(files: List<File>) {
        _uiState.update { it.copy(actionFlow = ToolActionFlow.ConfirmLuaRecompile(files)) }
    }

    fun selectRepackResolveMode(mode: RepackResolveMode) =
        _uiState.update { it.copy(repackResolveMode = mode) }

    fun updateTargetPath(path: String) = _uiState.update { it.copy(targetPathInput = path) }

    fun toggleInjectEncryptSm4(enabled: Boolean) = _uiState.update { it.copy(injectEncryptSm4 = enabled) }

    fun selectTheme(preset: GlassThemePreset) {
        viewModelScope.launch { sessionStore.saveTheme(preset) }
        _uiState.update { it.copy(themePreset = preset, actionFlow = ToolActionFlow.None) }
        showMessage("UI switched to ${preset.displayName}")
    }

    fun clearLogs() = _uiState.update {
        it.copy(logs = emptyList(), blockResults = emptyList(), lastUnpackSummary = null, lastRepackSummary = null)
    }

    fun consumeSnackbar() = _uiState.update { it.copy(snackbarMessage = null) }

    private fun showMessage(msg: String) = _uiState.update { it.copy(snackbarMessage = msg) }

    private fun appendLog(line: String) {
        _uiState.update { state ->
            state.copy(logs = (state.logs + line).takeLast(160))
        }
    }

    fun refreshWorkspace() {
        viewModelScope.launch(Dispatchers.IO) {
            workspace.ensureDirectories()
            refreshWorkspaceSync()
        }
    }

    private fun refreshWorkspaceSync() {
        val granted = workspace.hasAllFilesAccess()
        val unpackPaks = workspace.listUnpackCandidatePaks()
        val pakToolPaks = workspace.listPakToolAndMainPaks()
        val pakToolEdits = workspace.listFilesIn(workspace.pakToolEditDir, recursive = true)
        val unpacked = workspace.listUnpackedPaks()
        val outputs = workspace.listAllOutputPaks()
        val folders = workspace.listWorkspaceFolders()
        val deletable = workspace.listDeletableFolders()
        val luaOrg = workspace.listFilesIn(workspace.luaOrgDir, recursive = false)
            .filter { it.name.endsWith(".lua", true) || it.name.endsWith(".luac", true) }
        val luaDec = workspace.listFilesIn(workspace.luaDecriptDir, recursive = false)
        val luaEdit = workspace.listFilesIn(workspace.luaEditDir, recursive = false)
            .filter { it.name.endsWith(".lua", true) || it.name.endsWith(".luac", true) }
        val luaRes = workspace.listFilesIn(workspace.luaResultDir, recursive = false)

        _uiState.update { cur ->
            cur.copy(
                hasStorageAccess = granted,
                storagePathLabel = workspace.displayRootPath,
                pakFiles = unpackPaks,
                pakToolPakFiles = pakToolPaks,
                pakToolEditFiles = pakToolEdits,
                unpackedFolders = unpacked,
                outputPaks = outputs,
                workspaceFolders = folders,
                deletableFolders = deletable,
                luaOrgFiles = luaOrg,
                luaDecriptFiles = luaDec,
                luaEditFiles = luaEdit,
                luaResultFiles = luaRes
            )
        }
    }

    // ==================== PAK EXECUTION ====================

    fun executeUnpackPak(pakFile: File) {
        _uiState.update { it.copy(actionFlow = ToolActionFlow.None) }
        launchBusyTask("Unpacking ${pakFile.name}") {
            val summary = TencentPakEngine.unpackPak(
                pakFile = pakFile,
                workspaceRoot = workspace.rootDir,
                onProgress = { p, detail ->
                    _uiState.update { it.copy(busyProgress = p, busyDetail = detail) }
                },
                onLog = { appendLog(it) }
            )
            _uiState.update { it.copy(lastUnpackSummary = summary) }
            showMessage("Extracted ${summary.extractedCount} files → MAJLISH/UNPACK/${pakFile.nameWithoutExtension}")
        }
    }

    fun executeRepackPak(unpackFolder: File, toManualPath: Boolean) {
        val mode = _uiState.value.repackResolveMode
        val manualPath = if (toManualPath) _uiState.value.targetPathInput.trim().trim('/') else null
        if (toManualPath && manualPath.isNullOrBlank()) {
            showMessage("Enter a valid target path inside the PAK")
            return
        }
        _uiState.update { it.copy(actionFlow = ToolActionFlow.None) }
        launchBusyTask(if (toManualPath) "Repacking to $manualPath" else "Repacking ${unpackFolder.name}") {
            _uiState.update { it.copy(blockResults = emptyList()) }
            val summary = TencentPakEngine.repackFromUnpack(
                workspaceRoot = workspace.rootDir,
                unpackDir = unpackFolder,
                resolveMode = if (toManualPath) RepackResolveMode.FOLDER_WISE else mode,
                manualTargetPath = manualPath,
                onProgress = { p, detail ->
                    _uiState.update { it.copy(busyProgress = p, busyDetail = detail) }
                },
                onBlockResult = { br ->
                    _uiState.update { s -> s.copy(blockResults = (s.blockResults + br).takeLast(120)) }
                },
                onLog = { appendLog(it) }
            )
            _uiState.update { it.copy(lastRepackSummary = summary) }
            showMessage("Saved → MAJLISH/RESULT/${summary.outputFileName}")
        }
    }

    fun executeInjectFiles(pakFile: File) {
        val protectNew = _uiState.value.injectEncryptSm4
        _uiState.update { it.copy(actionFlow = ToolActionFlow.None) }
        launchBusyTask("Injecting into ${pakFile.name}") {
            val loaded = TencentPakEngine.loadPak(pakFile, onLog = { appendLog(it) })
            val outPak = File(workspace.pakToolResultDir, pakFile.name)
            val (edited, added) = TencentPakEngine.injectFiles(
                pak = loaded,
                editRoot = workspace.pakToolEditDir,
                outputPak = outPak,
                protectNew = protectNew,
                sm4Type = 47,
                onProgress = { p, detail ->
                    _uiState.update { it.copy(busyProgress = p, busyDetail = detail) }
                },
                onLog = { appendLog(it) }
            )
            _uiState.update {
                it.copy(
                    lastRepackSummary = RepackSummary(
                        outputFileName = outPak.name,
                        outputAbsolutePath = outPak.absolutePath,
                        processedFiles = edited,
                        addedFiles = added,
                        modeLabel = if (protectNew) "INJECT + SM4-47" else "INJECT"
                    )
                )
            }
            appendLog("🎮 Game-ready output: MAJLISH/PAK TOOL/RESULT/${outPak.name}")
            showMessage("Injected ($edited replaced, $added added) → PAK TOOL/RESULT/")
        }
    }

    fun executeProtectPak(pakFile: File) {
        _uiState.update { it.copy(actionFlow = ToolActionFlow.None) }
        launchBusyTask("Protecting ${pakFile.name} (SM4-47)") {
            val outPak = File(workspace.pakToolResultDir, pakFile.name)
            val (protectedCount, skippedCount) = TencentPakEngine.protectPak(
                pakFile = pakFile,
                outputPak = outPak,
                sm4Type = 47,
                onProgress = { p, detail ->
                    _uiState.update { it.copy(busyProgress = p, busyDetail = detail) }
                },
                onLog = { appendLog(it) }
            )
            _uiState.update {
                it.copy(
                    lastRepackSummary = RepackSummary(
                        outputFileName = outPak.name,
                        outputAbsolutePath = outPak.absolutePath,
                        processedFiles = protectedCount,
                        skippedFiles = if (skippedCount > 0) listOf("$skippedCount marker/undecryptable entries kept") else emptyList(),
                        modeLabel = "SM4-47 PROTECTED"
                    )
                )
            }
            appendLog("🔒 Protected $protectedCount files (skipped $skippedCount) → PAK TOOL/RESULT/${outPak.name}")
            showMessage("Protected $protectedCount files → PAK TOOL/RESULT/${outPak.name}")
        }
    }

    fun executeDeleteFolder(folder: File) {
        _uiState.update { it.copy(actionFlow = ToolActionFlow.None) }
        viewModelScope.launch(Dispatchers.IO) {
            val ok = workspace.deleteFolder(folder)
            if (ok) {
                appendLog("🗑 Deleted folder: ${folder.name}")
                showMessage("Deleted ${folder.name}")
            } else {
                showMessage("Could not delete ${folder.name}")
            }
            refreshWorkspaceSync()
        }
    }

    fun executeExportResult(pakFile: File) {
        _uiState.update { it.copy(actionFlow = ToolActionFlow.None) }
        launchBusyTask("Exporting ${pakFile.name}") {
            val dest = workspace.exportPakToDownloads(pakFile)
            appendLog("📤 Exported ${pakFile.name} → Download/MAJLISH_EXPORT/${dest.name}")
            _uiState.update { it.copy(busyProgress = 1f, busyDetail = "Exported to Downloads") }
            showMessage("Saved → Download/MAJLISH_EXPORT/${dest.name}")
        }
    }

    // ==================== LUA EXECUTION ====================

    fun executeDecompileLua(files: List<File>) {
        _uiState.update { it.copy(actionFlow = ToolActionFlow.None) }
        launchBusyTask("Decompiling ${files.size} Lua file(s)") {
            val xorKey = LuaBytecodeEngine.resolveXorKey(workspace.luaBaseDir, _uiState.value.customLuaXorHex)
            var ok = 0
            files.forEachIndexed { idx, f ->
                _uiState.update {
                    it.copy(
                        busyProgress = idx.toFloat() / files.size.coerceAtLeast(1).toFloat(),
                        busyDetail = "Decompiling ${f.name}"
                    )
                }
                runCatching {
                    val (_, msg) = LuaBytecodeEngine.decompileFile(f, workspace.luaDecriptDir, xorKey)
                    ok++
                    appendLog("⚡ $msg")
                }.onFailure { e ->
                    appendLog("⚠ Failed ${f.name}: ${e.message}")
                }
            }
            _uiState.update { it.copy(busyProgress = 1f, busyDetail = "Done") }
            showMessage("Decompiled $ok/${files.size} → MAJLISH/LUA TOOL/Lua_Decript")
        }
    }

    fun executeRecompileLua(files: List<File>) {
        _uiState.update { it.copy(actionFlow = ToolActionFlow.None) }
        launchBusyTask("Recompiling ${files.size} Lua file(s)") {
            val xorKey = LuaBytecodeEngine.resolveXorKey(workspace.luaBaseDir, _uiState.value.customLuaXorHex)
            var ok = 0
            files.forEachIndexed { idx, f ->
                _uiState.update {
                    it.copy(
                        busyProgress = idx.toFloat() / files.size.coerceAtLeast(1).toFloat(),
                        busyDetail = "Encrypting ${f.name}"
                    )
                }
                runCatching {
                    val (_, msg) = LuaBytecodeEngine.recompileFile(f, workspace.luaResultDir, xorKey)
                    ok++
                    appendLog("⚡ $msg")
                }.onFailure { e ->
                    appendLog("❌ Recompile error (${f.name}): ${e.message}")
                }
            }
            _uiState.update { it.copy(busyProgress = 1f, busyDetail = "Done") }
            showMessage("Recompiled $ok/${files.size} → MAJLISH/LUA TOOL/Lua_Rueslt")
        }
    }

    fun saveCustomLuaXorHex(hex: String) {
        val cleaned = hex.trim()
        if (cleaned.isNotEmpty() && (cleaned.length % 2 != 0 || !cleaned.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' })) {
            showMessage("XOR key must be valid even-length hexadecimal")
            return
        }
        viewModelScope.launch {
            sessionStore.saveCustomLuaXorHex(cleaned)
            val keyFile = File(workspace.luaBaseDir, "lua_xor_key.txt")
            withContext(Dispatchers.IO) {
                if (cleaned.isEmpty()) keyFile.delete() else keyFile.writeText(cleaned, Charsets.UTF_8)
            }
            _uiState.update { it.copy(customLuaXorHex = cleaned, actionFlow = ToolActionFlow.None) }
            showMessage(if (cleaned.isEmpty()) "Reset Lua XOR key to default" else "Custom Lua XOR key saved")
        }
    }

    private fun launchBusyTask(title: String, block: suspend () -> Unit) {
        if (_uiState.value.isBusy) {
            showMessage("An operation is already running")
            return
        }
        activeJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    isBusy = true,
                    busyTitle = title,
                    busyDetail = "Initializing…",
                    busyProgress = 0.02f
                )
            }
            runCatching {
                block()
            }.onFailure { e ->
                appendLog("❌ Error: ${e.message ?: e.javaClass.simpleName}")
                showMessage("Failed: ${e.message ?: "Unexpected error"}")
            }
            _uiState.update { it.copy(isBusy = false, busyProgress = 0f, busyDetail = "") }
            refreshWorkspaceSync()
        }
    }
}
