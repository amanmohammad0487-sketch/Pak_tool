package com.example

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.workspace.WorkspaceManager
import com.example.ui.components.ChangeUiModalDialog
import com.example.ui.components.LiquidAutoPickerDialog
import com.example.ui.components.LiquidConfirmActionDialog
import com.example.ui.components.LiquidMultiFilePickerDialog
import com.example.ui.components.LuaUtilitiesMenuDialog
import com.example.ui.components.LuaXorKeyDialog
import com.example.ui.components.PickerEntry
import com.example.ui.components.RepackModeSelectorBlock
import com.example.ui.components.SettingsModalDialog
import com.example.ui.components.Sm4EncryptOptionBlock
import com.example.ui.components.StoragePermissionDialog
import com.example.ui.components.TargetPathInputBlock
import com.example.ui.components.ToolActionFlow
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.theme.LiquidSpatialBackground
import com.example.ui.theme.LocalLiquidGlassPalette
import com.example.ui.theme.MajlishTheme
import com.example.viewmodel.MajlishUiState
import com.example.viewmodel.MajlishViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        )
        setContent {
            MajlishAppRoot()
        }
    }
}

private fun requestSharedStoragePermission(
    context: Context,
    legacyLauncher: (Array<String>) -> Unit
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        runCatching { context.startActivity(intent) }.onFailure {
            runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
        }
    } else {
        legacyLauncher(
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        )
    }
}

@Composable
fun MajlishAppRoot(viewModel: MajlishViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val legacyStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.checkAndInitializeStorage(showPromptIfMissing = false)
    }

    // Automatically ensure /storage/emulated/0/MAJLISH folders & rescan whenever returning to the app
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkAndInitializeStorage(showPromptIfMissing = false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeSnackbar()
        }
    }

    val isModalVisible = uiState.actionFlow !is ToolActionFlow.None ||
        (uiState.showStoragePrompt && !uiState.hasStorageAccess)

    val blurRadius by animateDpAsState(
        targetValue = if (isModalVisible && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 18.dp else 0.dp,
        animationSpec = tween(220),
        label = "modal_bg_blur"
    )

    MajlishTheme(themePreset = uiState.themePreset) {
        val palette = LocalLiquidGlassPalette.current

        Box(modifier = Modifier.fillMaxSize()) {
            LiquidSpatialBackground(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier)
            ) {
                AnimatedContent(
                    targetState = uiState.isAuthenticated,
                    transitionSpec = {
                        (fadeIn(tween(340)) + scaleIn(initialScale = 0.97f, animationSpec = tween(340))) togetherWith
                            fadeOut(tween(220))
                    },
                    label = "auth_transition"
                ) { authenticated ->
                    if (!authenticated) {
                        LoginScreen(
                            uiState = uiState,
                            onKeyChange = viewModel::updateKeyInput,
                            onVerifyClick = viewModel::verifyLicenseKey
                        )
                    } else {
                        HomeScreen(
                            uiState = uiState,
                            onOpenSettings = viewModel::openSettings,
                            onOpenActionFlow = viewModel::openActionFlow,
                            onGrantStorageClick = {
                                requestSharedStoragePermission(context) { perms ->
                                    legacyStorageLauncher.launch(perms)
                                }
                            },
                            onClearLogs = viewModel::clearLogs
                        )
                    }
                }
            }

            // Modals rendered cleanly over the blurred Liquid Glass backdrop
            if (uiState.isAuthenticated) {
                ActionFlowDialogHost(
                    uiState = uiState,
                    viewModel = viewModel,
                    onExitApp = {
                        (context as? Activity)?.finishAffinity()
                    }
                )
            }

            if (uiState.showStoragePrompt && !uiState.hasStorageAccess) {
                StoragePermissionDialog(
                    onGrantClick = {
                        viewModel.dismissStoragePrompt()
                        requestSharedStoragePermission(context) { perms ->
                            legacyStorageLauncher.launch(perms)
                        }
                    },
                    onLaterClick = viewModel::dismissStoragePrompt
                )
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    shape = RoundedCornerShape(22.dp),
                    containerColor = Color(0xF2FFFFFF),
                    contentColor = palette.textPrimary,
                    actionColor = palette.accent
                )
            }
        }
    }
}

@Composable
private fun ActionFlowDialogHost(
    uiState: MajlishUiState,
    viewModel: MajlishViewModel,
    onExitApp: () -> Unit
) {
    val palette = LocalLiquidGlassPalette.current
    when (val flow = uiState.actionFlow) {
        ToolActionFlow.None -> Unit

        // Top-right SETTINGS modal (Screenshot 2)
        ToolActionFlow.SettingsMenu -> {
            SettingsModalDialog(
                currentTheme = uiState.themePreset,
                onChangeUiClick = viewModel::openChangeUi,
                onLogoutClick = viewModel::signOutAndClearKey,
                onExitToolClick = onExitApp,
                onDismiss = viewModel::closeActionFlow
            )
        }

        // CHANGE UI modal (Screenshot 1)
        ToolActionFlow.ChangeUiMenu -> {
            ChangeUiModalDialog(
                selectedTheme = uiState.themePreset,
                onSelectTheme = viewModel::selectTheme,
                onDismiss = viewModel::closeActionFlow
            )
        }

        // 01. UNPACK PAK
        ToolActionFlow.PickUnpackPak -> {
            LiquidAutoPickerDialog(
                title = "UNPACK PAK",
                subtitle = "Select the .pak to unpack",
                folderHint = "Internal Storage/MAJLISH/PAK/",
                entries = uiState.pakFiles.map {
                    PickerEntry(it.name, WorkspaceManager.formatSize(it.sizeBytes), it.file)
                },
                onSelect = { viewModel.onUnpackPakPicked(it.file) },
                onRefresh = viewModel::refreshWorkspace,
                onDismiss = viewModel::closeActionFlow
            )
        }

        is ToolActionFlow.ConfirmUnpackPak -> {
            LiquidConfirmActionDialog(
                title = "Unpack PAK",
                message = "Selected: ${flow.pakFile.name} (${WorkspaceManager.formatSize(flow.pakFile.length())})\n\nExtract all files, Debug_.log, edited/ & index.csv into:\nMAJLISH/UNPACK/${flow.pakFile.nameWithoutExtension}/",
                confirmText = "Unpack Now",
                onConfirm = { viewModel.executeUnpackPak(flow.pakFile) },
                onDismiss = viewModel::closeActionFlow
            )
        }

        // 02. REPACK PAK
        ToolActionFlow.PickRepackFolder -> {
            LiquidAutoPickerDialog(
                title = "REPACK PAK",
                subtitle = "Select the unpacked PAK folder",
                folderHint = "Internal Storage/MAJLISH/UNPACK/",
                entries = uiState.unpackedFolders.map {
                    PickerEntry(
                        title = it.name,
                        subtitle = "${it.fileCount} files • ${WorkspaceManager.formatSize(it.sizeBytes)}",
                        file = it.file,
                        isFolder = true
                    )
                },
                onSelect = { viewModel.onRepackFolderPicked(it.file, forToPath = false) },
                onRefresh = viewModel::refreshWorkspace,
                onDismiss = viewModel::closeActionFlow
            )
        }

        is ToolActionFlow.ConfirmRepackFolder -> {
            val count = flow.editedFiles.size
            val msg = if (count > 0) {
                "Detected $count edited file(s) in MAJLISH/UNPACK/${flow.folder.name}/edited/.\nOutput saves to MAJLISH/RESULT/${flow.folder.name}.pak."
            } else {
                "No edited files found in MAJLISH/UNPACK/${flow.folder.name}/edited/.\nPlace your edited files there first."
            }
            LiquidConfirmActionDialog(
                title = "Repack ${flow.folder.name}",
                message = msg,
                confirmText = "Start Repack",
                canConfirm = count > 0,
                extraContent = if (count > 0) {
                    {
                        RepackModeSelectorBlock(
                            selectedMode = uiState.repackResolveMode,
                            onSelectMode = viewModel::selectRepackResolveMode
                        )
                    }
                } else null,
                onConfirm = { viewModel.executeRepackPak(flow.folder, toManualPath = false) },
                onDismiss = viewModel::closeActionFlow
            )
        }

        // 03. REPACK TO PATH
        ToolActionFlow.PickToPathFolder -> {
            LiquidAutoPickerDialog(
                title = "REPACK TO PATH",
                subtitle = "Select the unpacked PAK folder",
                folderHint = "Internal Storage/MAJLISH/UNPACK/",
                entries = uiState.unpackedFolders.map {
                    PickerEntry(
                        title = it.name,
                        subtitle = "${it.fileCount} files • ${WorkspaceManager.formatSize(it.sizeBytes)}",
                        file = it.file,
                        isFolder = true
                    )
                },
                onSelect = { viewModel.onRepackFolderPicked(it.file, forToPath = true) },
                onRefresh = viewModel::refreshWorkspace,
                onDismiss = viewModel::closeActionFlow
            )
        }

        is ToolActionFlow.ConfirmToPath -> {
            val count = flow.editedFiles.size
            val msg = if (count > 0) {
                "Detected $count file(s) in MAJLISH/UNPACK/${flow.folder.name}/edited/.\nChoose the internal target directory inside the PAK:"
            } else {
                "No files found in MAJLISH/UNPACK/${flow.folder.name}/edited/.\nPlace files there first."
            }
            LiquidConfirmActionDialog(
                title = "Repack to Path",
                message = msg,
                confirmText = "Repack to Path",
                canConfirm = count > 0 && uiState.targetPathInput.isNotBlank(),
                extraContent = if (count > 0) {
                    {
                        TargetPathInputBlock(
                            targetPath = uiState.targetPathInput,
                            onUpdatePath = viewModel::updateTargetPath
                        )
                    }
                } else null,
                onConfirm = { viewModel.executeRepackPak(flow.folder, toManualPath = true) },
                onDismiss = viewModel::closeActionFlow
            )
        }

        // 04. CUSTOM FILE / INJECT (Screenshot 4!)
        ToolActionFlow.PickInjectPak -> {
            LiquidAutoPickerDialog(
                title = "CUSTOM FILE / INJECT",
                subtitle = "Select the .pak to inject into",
                folderHint = "Internal Storage/MAJLISH/PAK TOOL/PAK/",
                entries = uiState.pakToolPakFiles.map {
                    PickerEntry(it.name, WorkspaceManager.formatSize(it.sizeBytes), it.file)
                },
                onSelect = { viewModel.onInjectPakPicked(it.file) },
                onRefresh = viewModel::refreshWorkspace,
                onDismiss = viewModel::closeActionFlow
            )
        }

        is ToolActionFlow.ConfirmInject -> {
            val count = flow.editFiles.size
            val msg = if (count > 0) {
                "Detected $count file(s) in MAJLISH/PAK TOOL/EDIT/.\nInject into ${flow.pakFile.name} and save to MAJLISH/PAK TOOL/RESULT/${flow.pakFile.name}?"
            } else {
                "No files found in MAJLISH/PAK TOOL/EDIT/.\nPlace files at their in-PAK relative path inside PAK TOOL/EDIT/ first."
            }
            LiquidConfirmActionDialog(
                title = "Inject Custom Files",
                message = msg,
                confirmText = "Inject Now",
                canConfirm = count > 0,
                extraContent = if (count > 0) {
                    {
                        Sm4EncryptOptionBlock(
                            enabled = uiState.injectEncryptSm4,
                            onToggle = viewModel::toggleInjectEncryptSm4
                        )
                    }
                } else null,
                onConfirm = { viewModel.executeInjectFiles(flow.pakFile) },
                onDismiss = viewModel::closeActionFlow
            )
        }

        // 05. PROTECT PAK
        ToolActionFlow.PickProtectPak -> {
            LiquidAutoPickerDialog(
                title = "PROTECT PAK",
                subtitle = "Select the .pak to encrypt with SM4",
                folderHint = "Internal Storage/MAJLISH/PAK TOOL/PAK/",
                entries = uiState.pakToolPakFiles.map {
                    PickerEntry(it.name, WorkspaceManager.formatSize(it.sizeBytes), it.file)
                },
                onSelect = { viewModel.onProtectPakPicked(it.file) },
                onRefresh = viewModel::refreshWorkspace,
                onDismiss = viewModel::closeActionFlow
            )
        }

        is ToolActionFlow.ConfirmProtect -> {
            LiquidConfirmActionDialog(
                title = "Protect PAK (SM4-47)",
                message = "Re-encrypt EVERY entry in ${flow.pakFile.name} using game-native SM4 (Type 47)?\n\nOutput keeps the exact same filename inside:\nMAJLISH/PAK TOOL/RESULT/${flow.pakFile.name}",
                confirmText = "Start Protect",
                onConfirm = { viewModel.executeProtectPak(flow.pakFile) },
                onDismiss = viewModel::closeActionFlow
            )
        }

        // 06. DELETE FOLDER
        ToolActionFlow.PickDeleteFolder -> {
            LiquidAutoPickerDialog(
                title = "DELETE FOLDER",
                subtitle = "Select a workspace folder to remove",
                folderHint = "Internal Storage/MAJLISH/UNPACK/",
                entries = uiState.deletableFolders.map {
                    PickerEntry(
                        title = it.relativePath,
                        subtitle = "${it.fileCount} files • ${WorkspaceManager.formatSize(it.sizeBytes)}",
                        file = it.file,
                        isFolder = true
                    )
                },
                onSelect = { entry ->
                    uiState.deletableFolders.firstOrNull { it.file.absolutePath == entry.file.absolutePath }
                        ?.let(viewModel::onDeleteFolderPicked)
                },
                onRefresh = viewModel::refreshWorkspace,
                onDismiss = viewModel::closeActionFlow
            )
        }

        is ToolActionFlow.ConfirmDeleteFolder -> {
            LiquidConfirmActionDialog(
                title = "Delete Folder",
                message = "Permanently delete ${flow.folder.relativePath} (${flow.folder.fileCount} files, ${WorkspaceManager.formatSize(flow.folder.sizeBytes)})?",
                confirmText = "Delete Folder",
                accentOverride = palette.error,
                onConfirm = { viewModel.executeDeleteFolder(flow.folder.file) },
                onDismiss = viewModel::closeActionFlow
            )
        }

        // 07. EXPORT RESULT
        ToolActionFlow.PickExportResult -> {
            LiquidAutoPickerDialog(
                title = "EXPORT RESULT",
                subtitle = "Select a generated .pak to save to Downloads",
                folderHint = "Internal Storage/MAJLISH/RESULT/ & PAK TOOL/RESULT/",
                entries = uiState.outputPaks.map {
                    PickerEntry(it.name, WorkspaceManager.formatSize(it.sizeBytes), it.file)
                },
                onSelect = { viewModel.onExportResultPicked(it.file) },
                onRefresh = viewModel::refreshWorkspace,
                onDismiss = viewModel::closeActionFlow
            )
        }

        is ToolActionFlow.ConfirmExportResult -> {
            LiquidConfirmActionDialog(
                title = "Export Result PAK",
                message = "Copy ${flow.pakFile.name} (${WorkspaceManager.formatSize(flow.pakFile.length())}) to:\nInternal Storage/Download/MAJLISH_EXPORT/${flow.pakFile.name}?",
                confirmText = "Save to Downloads",
                onConfirm = { viewModel.executeExportResult(flow.pakFile) },
                onDismiss = viewModel::closeActionFlow
            )
        }

        // 08. LUA UTILITIES
        ToolActionFlow.LuaUtilitiesMenu -> {
            LuaUtilitiesMenuDialog(
                orgCount = uiState.luaOrgFiles.size,
                editCount = uiState.luaEditFiles.size,
                onDecompileClick = { viewModel.openActionFlow(ToolActionFlow.PickLuaDecompile) },
                onRecompileClick = { viewModel.openActionFlow(ToolActionFlow.PickLuaRecompile) },
                onXorKeyClick = { viewModel.openActionFlow(ToolActionFlow.ConfigureLuaXorKey) },
                onDismiss = viewModel::closeActionFlow
            )
        }

        ToolActionFlow.PickLuaDecompile -> {
            LiquidMultiFilePickerDialog(
                title = "DECOMPILE LUA",
                subtitle = "Select .lua/.luac files from LUA TOOL/Lua_org",
                folderHint = "Internal Storage/MAJLISH/LUA TOOL/Lua_org/",
                files = uiState.luaOrgFiles,
                confirmLabel = "Continue",
                onConfirmSelection = viewModel::onLuaDecompilePicked,
                onRefresh = viewModel::refreshWorkspace,
                onDismiss = viewModel::closeActionFlow
            )
        }

        is ToolActionFlow.ConfirmLuaDecompile -> {
            LiquidConfirmActionDialog(
                title = "Decompile ${flow.files.size} File(s)",
                message = "Convert & decompile selected game Lua bytecode into:\nMAJLISH/LUA TOOL/Lua_Decript/",
                confirmText = "Decompile Now",
                onConfirm = { viewModel.executeDecompileLua(flow.files) },
                onDismiss = viewModel::closeActionFlow
            )
        }

        ToolActionFlow.PickLuaRecompile -> {
            LiquidMultiFilePickerDialog(
                title = "RECOMPILE LUA",
                subtitle = "Select edited files from LUA TOOL/EDIT_LUA",
                folderHint = "Internal Storage/MAJLISH/LUA TOOL/EDIT_LUA/",
                files = uiState.luaEditFiles,
                confirmLabel = "Continue",
                onConfirmSelection = viewModel::onLuaRecompilePicked,
                onRefresh = viewModel::refreshWorkspace,
                onDismiss = viewModel::closeActionFlow
            )
        }

        is ToolActionFlow.ConfirmLuaRecompile -> {
            LiquidConfirmActionDialog(
                title = "Recompile ${flow.files.size} File(s)",
                message = "Recompile & XOR-encrypt selected file(s) into game-ready Lua inside:\nMAJLISH/LUA TOOL/Lua_Rueslt/",
                confirmText = "Recompile Now",
                onConfirm = { viewModel.executeRecompileLua(flow.files) },
                onDismiss = viewModel::closeActionFlow
            )
        }

        ToolActionFlow.ConfigureLuaXorKey -> {
            LuaXorKeyDialog(
                currentHex = uiState.customLuaXorHex,
                onSave = viewModel::saveCustomLuaXorHex,
                onDismiss = viewModel::closeActionFlow
            )
        }
    }
}
