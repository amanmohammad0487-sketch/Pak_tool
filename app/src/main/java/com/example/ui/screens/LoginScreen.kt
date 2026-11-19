package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.LiquidGlassButton
import com.example.ui.theme.LiquidGlassCard
import com.example.ui.theme.LiquidGlassTextField
import com.example.ui.theme.LocalLiquidGlassPalette
import com.example.viewmodel.AuthUiState
import com.example.viewmodel.MajlishUiState

@Composable
fun LoginScreen(
    uiState: MajlishUiState,
    onKeyChange: (String) -> Unit,
    onVerifyClick: () -> Unit
) {
    val palette = LocalLiquidGlassPalette.current
    val clipboard = LocalClipboardManager.current

    val infinite = rememberInfiniteTransition(label = "logo_float")
    val logoScale by infinite.animateFloat(
        initialValue = 0.985f,
        targetValue = 1.015f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logo_scale"
    )

    val isVerifying = uiState.authUiState is AuthUiState.Verifying

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
        ) {
            // Centered floating app emblem with crisp white glass frame
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .scale(logoScale)
                    .size(114.dp)
                    .shadow(
                        elevation = 18.dp,
                        shape = RoundedCornerShape(30.dp),
                        spotColor = palette.accent.copy(alpha = 0.25f),
                        ambientColor = Color(0x260F172A)
                    )
                    .clip(RoundedCornerShape(30.dp))
                    .background(Color.White.copy(alpha = 0.88f))
                    .padding(4.dp)
                    .clip(RoundedCornerShape(26.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.majlish_logo_1795056643980),
                    contentDescription = stringResource(R.string.cd_app_logo),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(Modifier.height(26.dp))

            // Brand Title & Subtitle (left-aligned above the card, matching screenshot 7)
            Text(
                text = "MAJLISH",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp
                ),
                color = palette.accent
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "GAMING TOOL CONTROL PANEL",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.1.sp
                ),
                color = palette.textSecondary
            )

            Spacer(Modifier.height(28.dp))

            // Luminous Frosted Liquid Glass Login Card
            LiquidGlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 34.dp,
                elevated = true,
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 26.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "LICENSE LOGIN",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        ),
                        color = palette.accent
                    )

                    Spacer(Modifier.height(6.dp))

                    Text(
                        text = "Paste your key to unlock the tool",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.5.sp),
                        color = palette.textSecondary
                    )

                    Spacer(Modifier.height(20.dp))

                    // Pill key field with inline PASTE pill button
                    LiquidGlassTextField(
                        value = uiState.keyInput,
                        onValueChange = onKeyChange,
                        placeholder = "XXXX-XXXX-XXXX",
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onVerifyClick() }),
                        modifier = Modifier.testTag("license_key_input"),
                        trailingContent = {
                            val pillShape = RoundedCornerShape(22.dp)
                            Box(
                                modifier = Modifier
                                    .clip(pillShape)
                                    .background(Color.White.copy(alpha = 0.85f))
                                    .border(1.2.dp, Color.White, pillShape)
                                    .clickable {
                                        clipboard.getText()?.text?.let { pasted ->
                                            onKeyChange(pasted.trim())
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                                    .testTag("paste_key_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "PASTE",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.6.sp,
                                        fontSize = 13.5.sp
                                    ),
                                    color = palette.accent
                                )
                            }
                        }
                    )

                    Spacer(Modifier.height(18.dp))

                    // Full-width gradient pill CTA
                    LiquidGlassButton(
                        text = if (isVerifying) "CHECKING..." else "UNLOCK TOOL",
                        onClick = onVerifyClick,
                        enabled = uiState.keyInput.isNotBlank() && !isVerifying,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("unlock_button")
                    )

                    // Live verification progress bar & status text (matching screenshot 7)
                    AnimatedVisibility(
                        visible = uiState.authUiState !is AuthUiState.Idle,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            when (val st = uiState.authUiState) {
                                AuthUiState.Verifying -> {
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(100.dp)),
                                        color = palette.accent.copy(alpha = 0.55f),
                                        trackColor = Color.White.copy(alpha = 0.65f),
                                        strokeCap = StrokeCap.Round
                                    )
                                    Spacer(Modifier.height(14.dp))
                                    Text(
                                        text = "Verifying license...",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        ),
                                        color = palette.textSecondary,
                                        textAlign = TextAlign.Center
                                    )
                                }

                                is AuthUiState.Granted -> {
                                    Text(
                                        text = "Access granted • Unlocking MAJLISH...",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.5.sp
                                        ),
                                        color = palette.success,
                                        textAlign = TextAlign.Center
                                    )
                                }

                                is AuthUiState.Denied -> {
                                    Text(
                                        text = st.reason,
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        ),
                                        color = palette.error,
                                        textAlign = TextAlign.Center
                                    )
                                }

                                AuthUiState.Idle -> Unit
                            }
                        }
                    }
                }
            }
        }
    }
}
