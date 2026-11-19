package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.pak.RepackResolveMode
import com.example.core.workspace.WorkspaceFileItem
import com.example.core.workspace.WorkspaceFolderInfo
import com.example.core.workspace.WorkspaceManager
import com.example.ui.theme.GlassThemePreset
import com.example.ui.theme.LiquidGlassButton
import com.example.ui.theme.LiquidGlassTextField
import com.example.ui.theme.LocalLiquidGlassPalette
import com.example.ui.theme.MonoFontFamily
import java.io.File

data class PickerEntry(
    val title: String,
    val subtitle: String,
    val file: File,
    val isFolder: Boolean = false
)

/**
 * Complete Liquid Glass modal workflow state machine matching all screenshots
 * (Settings, Change UI, Auto-Detect PAK/Folder Pickers, Confirmation & Options, Lua Utilities).
 */
sealed interface ToolActionFlow {
    data object None : ToolActionFlow

    // Top-right Settings & Change UI modals
    data object SettingsMenu : ToolActionFlow
    data object ChangeUiMenu : ToolActionFlow

    // 01. UNPACK PAK
    data object PickUnpackPak : ToolActionFlow
    data class ConfirmUnpackPak(val pakFile: File) : ToolActionFlow

    // 02. REPACK PAK
    data object PickRepackFolder : ToolActionFlow
    data class ConfirmRepackFolder(val folder: File, val editedFiles: List<WorkspaceFileItem>) : ToolActionFlow

    // 03. REPACK TO PATH
    data object PickToPathFolder : ToolActionFlow
    data class ConfirmToPath(val folder: File, val editedFiles: List<WorkspaceFileItem>) : ToolActionFlow

    // 04. CUSTOM FILE / INJECT
    data object PickInjectPak : ToolActionFlow
    data class ConfirmInject(val pakFile: File, val editFiles: List<WorkspaceFileItem>) : ToolActionFlow

    // 05. PROTECT PAK
    data object PickProtectPak : ToolActionFlow
    data class ConfirmProtect(val pakFile: File) : ToolActionFlow

    // 06. DELETE FOLDER
    data object PickDeleteFolder : ToolActionFlow
    data class ConfirmDeleteFolder(val folder: WorkspaceFolderInfo) : ToolActionFlow

    // 07. EXPORT RESULT
    data object PickExportResult : ToolActionFlow
    data class ConfirmExportResult(val pakFile: File) : ToolActionFlow

    // 08. LUA UTILITIES
    data object LuaUtilitiesMenu : ToolActionFlow
    data object PickLuaDecompile : ToolActionFlow
    data class ConfirmLuaDecompile(val files: List<File>) : ToolActionFlow
    data object PickLuaRecompile : ToolActionFlow
    data class ConfirmLuaRecompile(val files: List<File>) : ToolActionFlow
    data object ConfigureLuaXorKey : ToolActionFlow
}

@Composable
fun LiquidGlassModalCard(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(34.dp)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .shadow(
                    elevation = 24.dp,
                    shape = shape,
                    ambientColor = Color(0x330F172A),
                    spotColor = Color(0x280F172A)
                )
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.94f),
                            Color(0xF0EDF4FF)
                        )
                    )
                )
                .border(1.4.dp, Color.White.copy(alpha = 0.98f), shape)
                .padding(horizontal = 22.dp, vertical = 24.dp)
        ) {
            content()
        }
    }
}

@Composable
fun LiquidGlassCancelPill(
    text: String = "CANCEL",
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalLiquidGlassPalette.current
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.72f))
            .border(1.2.dp, Color.White.copy(alpha = 0.96f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.8.sp,
                fontSize = 15.sp
            ),
            color = palette.accent
        )
    }
}

@Composable
private fun ModalPillOption(
    title: String,
    subtitle: String? = null,
    titleColor: Color = LocalLiquidGlassPalette.current.textPrimary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalLiquidGlassPalette.current
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.70f))
            .border(1.2.dp, Color.White.copy(alpha = 0.96f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp
            ),
            color = titleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.5.sp),
                color = palette.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * SETTINGS modal (matching screenshot 2: CHANGE UI, LOG OUT, EXIT TOOL, CANCEL).
 */
@Composable
fun SettingsModalDialog(
    currentTheme: GlassThemePreset,
    onChangeUiClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onExitToolClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalLiquidGlassPalette.current
    LiquidGlassModalCard(onDismiss = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "SETTINGS",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                ),
                color = palette.accent
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "MAJLISH control panel",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                color = palette.textSecondary
            )

            Spacer(Modifier.height(18.dp))

            ModalPillOption(
                title = "CHANGE UI",
                subtitle = "Current: ${currentTheme.displayName}",
                onClick = onChangeUiClick,
                modifier = Modifier.testTag("settings_change_ui_btn")
            )
            Spacer(Modifier.height(10.dp))
            ModalPillOption(
                title = "LOG OUT",
                subtitle = "Remove the saved key",
                onClick = onLogoutClick,
                modifier = Modifier.testTag("settings_logout_btn")
            )
            Spacer(Modifier.height(10.dp))
            ModalPillOption(
                title = "EXIT TOOL",
                subtitle = "Close MAJLISH",
                titleColor = palette.error,
                onClick = onExitToolClick,
                modifier = Modifier.testTag("settings_exit_btn")
            )
            Spacer(Modifier.height(12.dp))
            LiquidGlassCancelPill(
                onClick = onDismiss,
                modifier = Modifier.testTag("settings_cancel_btn")
            )
        }
    }
}

/**
 * CHANGE UI modal (matching screenshot 1: 5 gradient circles + checkmark + CANCEL).
 */
@Composable
fun ChangeUiModalDialog(
    selectedTheme: GlassThemePreset,
    onSelectTheme: (GlassThemePreset) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalLiquidGlassPalette.current
    LiquidGlassModalCard(onDismiss = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "CHANGE UI",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                ),
                color = palette.accent
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Choose a liquid glass style",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                color = palette.textSecondary
            )

            Spacer(Modifier.height(18.dp))

            GlassThemePreset.entries.forEach { preset ->
                val isSelected = preset == selectedTheme
                val rowShape = RoundedCornerShape(24.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 62.dp)
                        .clip(rowShape)
                        .background(Color.White.copy(alpha = if (isSelected) 0.85f else 0.66f))
                        .border(
                            width = if (isSelected) 1.5.dp else 1.2.dp,
                            color = if (isSelected) palette.accent.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.96f),
                            shape = rowShape
                        )
                        .clickable { onSelectTheme(preset) }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .testTag("theme_option_${preset.id}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(preset.swatchStart, preset.swatchEnd)
                                )
                            )
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = preset.displayName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.5.sp
                        ),
                        color = palette.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Selected theme",
                            tint = palette.accent,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(2.dp))
            LiquidGlassCancelPill(
                onClick = onDismiss,
                modifier = Modifier.testTag("change_ui_cancel_btn")
            )
        }
    }
}

/**
 * Auto-detected single-choice Liquid Glass Picker Modal (matching screenshot 4: CUSTOM FILE / INJECT).
 */
@Composable
fun LiquidAutoPickerDialog(
    title: String,
    subtitle: String,
    folderHint: String,
    entries: List<PickerEntry>,
    onSelect: (PickerEntry) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalLiquidGlassPalette.current

    LiquidGlassModalCard(onDismiss = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.4.sp,
                            fontSize = 22.sp
                        ),
                        color = palette.accent
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                        color = palette.textSecondary
                    )
                }
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.size(44.dp).testTag("dialog_refresh_btn")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Rescan folder",
                        tint = palette.accent,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            if (entries.isEmpty()) {
                val emptyShape = RoundedCornerShape(22.dp)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(emptyShape)
                        .background(Color.White.copy(alpha = 0.72f))
                        .border(1.2.dp, Color.White.copy(alpha = 0.96f), emptyShape)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "No files detected yet",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = palette.warning
                    )
                    Text(
                        text = "Put your files inside:\n$folderHint\nThen tap the refresh icon.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 310.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(entries, key = { it.file.absolutePath }) { item ->
                        ModalPillOption(
                            title = item.title,
                            subtitle = item.subtitle,
                            onClick = { onSelect(item) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            LiquidGlassCancelPill(
                onClick = onDismiss,
                modifier = Modifier.testTag("picker_cancel_btn")
            )
        }
    }
}

/**
 * Option 08 LUA UTILITIES menu modal (Decompile, Recompile, XOR Key).
 */
@Composable
fun LuaUtilitiesMenuDialog(
    orgCount: Int,
    editCount: Int,
    onDecompileClick: () -> Unit,
    onRecompileClick: () -> Unit,
    onXorKeyClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalLiquidGlassPalette.current
    LiquidGlassModalCard(onDismiss = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "LUA UTILITIES",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.4.sp
                ),
                color = palette.accent
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Convert game Lua bytecode both ways",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                color = palette.textSecondary
            )

            Spacer(Modifier.height(18.dp))

            ModalPillOption(
                title = "DECOMPILE LUA",
                subtitle = "Lua_org ($orgCount detected) → Lua_Decript (.lua)",
                onClick = onDecompileClick,
                modifier = Modifier.testTag("lua_menu_decompile_btn")
            )
            Spacer(Modifier.height(10.dp))
            ModalPillOption(
                title = "RECOMPILE LUA",
                subtitle = "EDIT_LUA ($editCount detected) → Lua_Rueslt (game ready)",
                onClick = onRecompileClick,
                modifier = Modifier.testTag("lua_menu_recompile_btn")
            )
            Spacer(Modifier.height(10.dp))
            ModalPillOption(
                title = "LUA XOR KEY",
                subtitle = "Override default 32-byte BGMI XOR hex key",
                onClick = onXorKeyClick,
                modifier = Modifier.testTag("lua_menu_xorkey_btn")
            )
            Spacer(Modifier.height(12.dp))
            LiquidGlassCancelPill(onClick = onDismiss)
        }
    }
}

/**
 * Multi-file auto-detected picker for Lua Decompile / Recompile.
 */
@Composable
fun LiquidMultiFilePickerDialog(
    title: String,
    subtitle: String,
    folderHint: String,
    files: List<WorkspaceFileItem>,
    confirmLabel: String,
    onConfirmSelection: (List<File>) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalLiquidGlassPalette.current
    var selected by remember(files) { mutableStateOf(files.map { it.file.absolutePath }.toSet()) }

    LiquidGlassModalCard(onDismiss = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 21.sp
                        ),
                        color = palette.accent
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                }
                IconButton(onClick = onRefresh, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Rounded.Refresh, "Rescan folder", tint = palette.accent)
                }
            }

            Spacer(Modifier.height(12.dp))

            if (files.isEmpty()) {
                val emptyShape = RoundedCornerShape(22.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(emptyShape)
                        .background(Color.White.copy(alpha = 0.72f))
                        .border(1.2.dp, Color.White.copy(alpha = 0.96f), emptyShape)
                        .padding(16.dp)
                ) {
                    Text(
                        text = "No .lua/.luac files found in:\n$folderHint\nCopy files there and tap refresh.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${selected.size} of ${files.size} selected",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.textMuted
                    )
                    TextButton(
                        onClick = {
                            selected = if (selected.size == files.size) emptySet()
                            else files.map { it.file.absolutePath }.toSet()
                        }
                    ) {
                        Text(
                            if (selected.size == files.size) "Clear All" else "Select All",
                            color = palette.accent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 250.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(files, key = { it.file.absolutePath }) { item ->
                        val checked = item.file.absolutePath in selected
                        val rowShape = RoundedCornerShape(20.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(rowShape)
                                .background(
                                    if (checked) palette.accent.copy(alpha = 0.12f)
                                    else Color.White.copy(alpha = 0.70f)
                                )
                                .border(
                                    1.2.dp,
                                    if (checked) palette.accent.copy(alpha = 0.50f) else Color.White.copy(alpha = 0.95f),
                                    rowShape
                                )
                                .clickable {
                                    selected = if (checked) selected - item.file.absolutePath else selected + item.file.absolutePath
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    selected = if (checked) selected - item.file.absolutePath else selected + item.file.absolutePath
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = palette.accent,
                                    checkmarkColor = Color.White
                                )
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    item.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = palette.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    WorkspaceManager.formatSize(item.sizeBytes),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.textSecondary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            if (files.isNotEmpty()) {
                LiquidGlassButton(
                    text = confirmLabel.uppercase(),
                    onClick = {
                        val chosen = files.filter { it.file.absolutePath in selected }.map { it.file }
                        onConfirmSelection(chosen)
                    },
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().testTag("multi_picker_confirm_btn")
                )
                Spacer(Modifier.height(10.dp))
            }
            LiquidGlassCancelPill(onClick = onDismiss)
        }
    }
}

/**
 * Confirmation & Options modal ("Do you want to proceed?") in bright frosted Liquid Glass.
 */
@Composable
fun LiquidConfirmActionDialog(
    title: String,
    message: String,
    confirmText: String,
    accentOverride: Color? = null,
    canConfirm: Boolean = true,
    extraContent: (@Composable () -> Unit)? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalLiquidGlassPalette.current
    val headingColor = accentOverride ?: palette.accent

    LiquidGlassModalCard(onDismiss = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.4.sp,
                    fontSize = 21.sp
                ),
                color = headingColor
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                color = palette.textSecondary
            )

            if (extraContent != null) {
                Spacer(Modifier.height(14.dp))
                extraContent()
            }

            Spacer(Modifier.height(18.dp))

            LiquidGlassButton(
                text = confirmText.uppercase(),
                onClick = onConfirm,
                enabled = canConfirm,
                accentOverride = accentOverride,
                modifier = Modifier.fillMaxWidth().testTag("confirm_yes_btn")
            )
            Spacer(Modifier.height(10.dp))
            LiquidGlassCancelPill(
                text = "CANCEL",
                onClick = onDismiss,
                modifier = Modifier.testTag("confirm_cancel_btn")
            )
        }
    }
}

/**
 * First-open storage access prompt to create /storage/emulated/0/MAJLISH automatically.
 */
@Composable
fun StoragePermissionDialog(
    onGrantClick: () -> Unit,
    onLaterClick: () -> Unit
) {
    val palette = LocalLiquidGlassPalette.current
    LiquidGlassModalCard(onDismiss = onLaterClick) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "AUTO-CREATE MAJLISH FOLDER",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp
                ),
                color = palette.accent
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Allow storage access so MAJLISH automatically creates Internal Storage/MAJLISH with PAK, UNPACK, REPACK, RESULT, EXE, PAK TOOL, and LUA TOOL folders and auto-detects your .pak & .lua files.",
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                color = palette.textSecondary
            )
            Spacer(Modifier.height(18.dp))
            LiquidGlassButton(
                text = "ALLOW STORAGE ACCESS",
                onClick = onGrantClick,
                modifier = Modifier.fillMaxWidth().testTag("grant_storage_access_btn")
            )
            Spacer(Modifier.height(10.dp))
            LiquidGlassCancelPill(text = "NOT NOW", onClick = onLaterClick)
        }
    }
}

@Composable
fun RepackModeSelectorBlock(
    selectedMode: RepackResolveMode,
    onSelectMode: (RepackResolveMode) -> Unit
) {
    val palette = LocalLiquidGlassPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "REPACK METHOD",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            ),
            color = palette.accent
        )
        listOf(
            Triple(RepackResolveMode.FOLDER_WISE, "1. Folder-wise Path", "Match full path inside edited/"),
            Triple(RepackResolveMode.INDEX_CSV, "2. index.csv Auto", "Auto-match filenames via EXE/index.csv"),
            Triple(RepackResolveMode.IN_PLACE_BLOCK, "3. In-Place Block", "MINI_OBB / OBBZSDIC / GAMEPATCH")
        ).forEach { (mode, label, desc) ->
            val active = selectedMode == mode
            val shape = RoundedCornerShape(18.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(if (active) palette.accent.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.72f))
                    .border(
                        1.2.dp,
                        if (active) palette.accent else Color.White.copy(alpha = 0.96f),
                        shape
                    )
                    .clickable { onSelectMode(mode) }
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = if (active) palette.accent else palette.textPrimary
                    )
                    Text(desc, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                }
                if (active) {
                    Icon(Icons.Rounded.Check, null, tint = palette.accent, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
fun TargetPathInputBlock(
    targetPath: String,
    onUpdatePath: (String) -> Unit
) {
    val palette = LocalLiquidGlassPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "TARGET IN-PAK PATH",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
            color = palette.accent
        )
        LiquidGlassTextField(
            value = targetPath,
            onValueChange = onUpdatePath,
            placeholder = "Content/Lua/GameLua/Mod/BRMod/Gameplay/Core",
            leadingIcon = Icons.Rounded.Route,
            monospace = true,
            modifier = Modifier.testTag("dialog_target_path_input")
        )
    }
}

@Composable
fun Sm4EncryptOptionBlock(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val palette = LocalLiquidGlassPalette.current
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.72f))
            .border(1.2.dp, palette.accent.copy(alpha = 0.35f), shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(Icons.Rounded.Security, null, tint = palette.accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    "Encrypt with SM4 (Type 47)",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = palette.textPrimary
                )
                Text(
                    "Game-native encryption (recommended)",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textSecondary
                )
            }
        }
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = palette.accent
            )
        )
    }
}

@Composable
fun LuaXorKeyDialog(
    currentHex: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalLiquidGlassPalette.current
    var hexInput by remember(currentHex) { mutableStateOf(currentHex) }

    LiquidGlassModalCard(onDismiss = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "LUA XOR KEY",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = palette.accent
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Leave blank to use the built-in BGMI Lua 5.3 XOR key, or paste a custom hex string.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary
            )
            Spacer(Modifier.height(14.dp))
            LiquidGlassTextField(
                value = hexInput,
                onValueChange = { hexInput = it },
                placeholder = "112136474657a78d… (optional)",
                leadingIcon = Icons.Rounded.Key,
                monospace = true,
                modifier = Modifier.testTag("lua_xor_key_input")
            )
            Spacer(Modifier.height(16.dp))
            LiquidGlassButton(
                text = "SAVE KEY",
                onClick = { onSave(hexInput) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            LiquidGlassCancelPill(onClick = onDismiss)
        }
    }
}
