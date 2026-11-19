package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.ToolActionFlow
import com.example.ui.theme.LiquidGlassCard
import com.example.ui.theme.LocalLiquidGlassPalette
import com.example.ui.theme.MonoFontFamily
import com.example.viewmodel.MajlishUiState

@Composable
fun HomeScreen(
    uiState: MajlishUiState,
    onOpenSettings: () -> Unit,
    onOpenActionFlow: (ToolActionFlow) -> Unit,
    onGrantStorageClick: () -> Unit,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalLiquidGlassPalette.current
    val infinite = rememberInfiniteTransition(label = "ready_dot")
    val dotAlpha by infinite.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "ready_dot_alpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(
                start = 18.dp,
                end = 18.dp,
                top = 12.dp,
                bottom = 28.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Top Brand Control Panel Header Pill (Screenshots 5 & 6)
            item {
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 34.dp,
                    elevated = true,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(62.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .border(1.2.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(18.dp))
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.majlish_logo_1795056643980),
                                contentDescription = stringResource(R.string.cd_app_logo),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "MAJLISH",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.8.sp
                                ),
                                color = palette.accent,
                                maxLines = 1
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "GAMING TOOL CONTROL PANEL",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.6.sp
                                ),
                                color = palette.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.56f))
                                .border(1.2.dp, Color.White.copy(alpha = 0.94f), CircleShape)
                                .clickable(onClick = onOpenSettings)
                                .testTag("open_settings_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = "Open settings",
                                tint = palette.textPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            // 2. SYSTEM READY Status Card (Screenshots 5 & 6)
            item {
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 30.dp,
                    onClick = if (!uiState.hasStorageAccess) onGrantStorageClick else null,
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 18.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(
                                        (if (uiState.hasStorageAccess) palette.accent else palette.warning)
                                            .copy(alpha = dotAlpha)
                                    )
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = if (uiState.hasStorageAccess) "SYSTEM READY" else "ENABLE /MAJLISH STORAGE",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.5.sp
                                ),
                                color = if (uiState.hasStorageAccess) palette.accent else palette.warning
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (uiState.hasStorageAccess) {
                                "License panel connected  •  Android edition"
                            } else {
                                "Tap to grant storage access so /MAJLISH folders auto-create"
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.5.sp),
                            color = palette.textSecondary
                        )
                    }
                }
            }

            // 3. Dynamic Live Progress & Console Output Card (appears during/after tool runs)
            item {
                AnimatedVisibility(
                    visible = uiState.isBusy || uiState.logs.isNotEmpty(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    LiquidGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 28.dp,
                        elevated = true,
                        contentPadding = PaddingValues(18.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (uiState.isBusy) uiState.busyTitle.uppercase() else "LAST OPERATION LOG",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.6.sp
                                    ),
                                    color = palette.accent,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!uiState.isBusy) {
                                    IconButton(
                                        onClick = onClearLogs,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Dismiss log",
                                            tint = palette.textSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            if (uiState.isBusy) {
                                LinearProgressIndicator(
                                    progress = { uiState.busyProgress.coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(7.dp)
                                        .clip(RoundedCornerShape(100.dp)),
                                    color = palette.accent,
                                    trackColor = Color.White.copy(alpha = 0.7f),
                                    strokeCap = StrokeCap.Round
                                )
                                if (uiState.busyDetail.isNotBlank()) {
                                    Text(
                                        text = uiState.busyDetail,
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                        color = palette.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (uiState.logs.isNotEmpty()) {
                                val logBoxShape = RoundedCornerShape(18.dp)
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(logBoxShape)
                                        .background(Color.White.copy(alpha = 0.56f))
                                        .border(1.dp, Color.White.copy(alpha = 0.9f), logBoxShape)
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    uiState.logs.takeLast(6).forEach { line ->
                                        Text(
                                            text = line,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = MonoFontFamily,
                                                fontSize = 11.5.sp
                                            ),
                                            color = palette.textPrimary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. PAK OPERATIONS Section Header
            item {
                Text(
                    text = "PAK OPERATIONS",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp
                    ),
                    color = palette.accent,
                    modifier = Modifier.padding(start = 6.dp, top = 6.dp, bottom = 2.dp)
                )
            }

            // 01 - UNPACK PAK
            item {
                OperationActionCard(
                    number = "01",
                    title = "UNPACK PAK",
                    subtitle = "Extract files from a selected .pak",
                    onClick = { onOpenActionFlow(ToolActionFlow.PickUnpackPak) },
                    testTag = "op_01_unpack"
                )
            }

            // 02 - REPACK PAK
            item {
                OperationActionCard(
                    number = "02",
                    title = "REPACK PAK",
                    subtitle = "Rebuild an unpacked PAK from its edited folder",
                    onClick = { onOpenActionFlow(ToolActionFlow.PickRepackFolder) },
                    testTag = "op_02_repack"
                )
            }

            // 03 - REPACK TO PATH
            item {
                OperationActionCard(
                    number = "03",
                    title = "REPACK TO PATH",
                    subtitle = "Repack edited files into a chosen PAK path",
                    onClick = { onOpenActionFlow(ToolActionFlow.PickToPathFolder) },
                    testTag = "op_03_repack_to_path"
                )
            }

            // 04 - CUSTOM FILE / INJECT
            item {
                OperationActionCard(
                    number = "04",
                    title = "CUSTOM FILE / INJECT",
                    subtitle = "Add or replace files using a selected folder",
                    onClick = { onOpenActionFlow(ToolActionFlow.PickInjectPak) },
                    testTag = "op_04_inject"
                )
            }

            // 05 - PROTECT PAK
            item {
                OperationActionCard(
                    number = "05",
                    title = "PROTECT PAK",
                    subtitle = "Run the existing PAK protection workflow",
                    onClick = { onOpenActionFlow(ToolActionFlow.PickProtectPak) },
                    testTag = "op_05_protect"
                )
            }

            // 06 - DELETE FOLDER
            item {
                OperationActionCard(
                    number = "06",
                    title = "DELETE FOLDER",
                    subtitle = "Remove a workspace folder",
                    onClick = { onOpenActionFlow(ToolActionFlow.PickDeleteFolder) },
                    testTag = "op_06_delete_folder"
                )
            }

            // 07 - EXPORT RESULT
            item {
                OperationActionCard(
                    number = "07",
                    title = "EXPORT RESULT",
                    subtitle = "Save a generated PAK to phone storage",
                    onClick = { onOpenActionFlow(ToolActionFlow.PickExportResult) },
                    testTag = "op_07_export_result"
                )
            }

            // 5. LUA BYTECODE CONVERSION Section Header (Screenshot 3)
            item {
                Text(
                    text = "LUA BYTECODE CONVERSION",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp
                    ),
                    color = palette.accent,
                    modifier = Modifier.padding(start = 6.dp, top = 10.dp, bottom = 2.dp)
                )
            }

            // 08 - LUA UTILITIES
            item {
                OperationActionCard(
                    number = "08",
                    title = "LUA UTILITIES",
                    subtitle = "Convert game Lua bytecode both ways",
                    onClick = { onOpenActionFlow(ToolActionFlow.LuaUtilitiesMenu) },
                    testTag = "op_08_lua_utilities"
                )
            }
        }
    }
}

@Composable
private fun OperationActionCard(
    number: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String
) {
    val palette = LocalLiquidGlassPalette.current
    val badgeShape = RoundedCornerShape(22.dp)

    LiquidGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        cornerRadius = 34.dp,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(badgeShape)
                    .background(Color.White.copy(alpha = 0.56f))
                    .border(1.2.dp, Color.White.copy(alpha = 0.94f), badgeShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = number,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black
                    ),
                    color = palette.accent
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.3.sp
                    ),
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        lineHeight = 19.sp
                    ),
                    color = palette.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(8.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
