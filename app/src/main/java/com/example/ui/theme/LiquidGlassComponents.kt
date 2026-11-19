package com.example.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Luminous iOS 26 pastel Liquid Glass mesh backdrop with gently drifting translucent color clouds.
 */
@Composable
fun LiquidSpatialBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val palette = LocalLiquidGlassPalette.current
    val infinite = rememberInfiniteTransition(label = "liquid_mesh")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2.0).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "mesh_phase"
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mesh_pulse"
    )

    val bgTop by animateColorAsState(palette.preset.bgTop, tween(450), label = "bgTop")
    val bgMid by animateColorAsState(palette.preset.bgMid, tween(450), label = "bgMid")
    val bgBottom by animateColorAsState(palette.preset.bgBottom, tween(450), label = "bgBottom")
    val orb1 by animateColorAsState(palette.preset.orbTopLeft, tween(450), label = "orb1")
    val orb2 by animateColorAsState(palette.preset.orbMidRight, tween(450), label = "orb2")
    val orb3 by animateColorAsState(palette.preset.orbBottomLeft, tween(450), label = "orb3")
    val orb4 by animateColorAsState(palette.preset.orbBottomRight, tween(450), label = "orb4")

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(bgTop, bgMid, bgBottom)
                )
            )
            val w = size.width
            val h = size.height

            val c1 = Offset(w * (0.12f + 0.05f * cos(phase)), h * (0.10f + 0.04f * sin(phase)))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(orb1.copy(alpha = 0.85f), orb1.copy(alpha = 0.25f), Color.Transparent),
                    center = c1,
                    radius = w * 0.85f * pulse
                ),
                center = c1,
                radius = w * 0.85f * pulse
            )

            val c2 = Offset(w * (0.88f + 0.05f * sin(phase * 1.1f)), h * (0.42f + 0.05f * cos(phase * 0.9f)))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(orb2.copy(alpha = 0.88f), orb2.copy(alpha = 0.28f), Color.Transparent),
                    center = c2,
                    radius = w * 0.88f
                ),
                center = c2,
                radius = w * 0.88f
            )

            val c3 = Offset(w * (0.14f + 0.07f * sin(phase * 0.8f)), h * (0.82f + 0.04f * cos(phase * 1.1f)))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(orb3.copy(alpha = 0.88f), orb3.copy(alpha = 0.30f), Color.Transparent),
                    center = c3,
                    radius = w * 0.92f * pulse
                ),
                center = c3,
                radius = w * 0.92f * pulse
            )

            val c4 = Offset(w * (0.86f + 0.04f * cos(phase * 0.7f)), h * (0.90f + 0.03f * sin(phase)))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(orb4.copy(alpha = 0.72f), orb4.copy(alpha = 0.18f), Color.Transparent),
                    center = c4,
                    radius = w * 0.76f
                ),
                center = c4,
                radius = w * 0.76f
            )
        }
        content()
    }
}

/**
 * Bright frosted iOS 26 Liquid Glass surface card with crisp white specular rim
 * and gentle spring press interaction.
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 32.dp,
    elevated: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val palette = LocalLiquidGlassPalette.current
    val shape: Shape = RoundedCornerShape(cornerRadius)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) 0.982f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "glass_card_scale"
    )

    val fillBrush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = if (elevated) 0.78f else 0.62f),
            Color.White.copy(alpha = if (elevated) 0.66f else 0.44f)
        )
    )

    val rimBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.96f),
            Color.White.copy(alpha = 0.72f),
            Color.White.copy(alpha = 0.88f)
        ),
        start = Offset(0f, 0f),
        end = Offset(700f, 700f)
    )

    val clickMod = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = ripple(color = palette.accent.copy(alpha = 0.18f)),
            onClick = onClick
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .scale(pressScale)
            .shadow(
                elevation = if (elevated) 14.dp else 6.dp,
                shape = shape,
                ambientColor = palette.accent.copy(alpha = 0.14f),
                spotColor = Color(0x220F172A)
            )
            .clip(shape)
            .background(fillBrush)
            .drawWithContent {
                drawContent()
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.White.copy(alpha = 0.34f), Color.Transparent),
                        startY = 0f,
                        endY = size.height * 0.30f
                    ),
                    cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx())
                )
            }
            .border(width = 1.25.dp, brush = rimBrush, shape = shape)
            .then(clickMod)
            .padding(contentPadding),
        content = content
    )
}

/**
 * Full-width pill gradient primary CTA button (matching the UNLOCK / CHECKING... pill).
 */
@Composable
fun LiquidGlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    primary: Boolean = true,
    accentOverride: Color? = null
) {
    val palette = LocalLiquidGlassPalette.current
    val accent = accentOverride ?: palette.accent
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && !loading) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "btn_scale"
    )

    val shape = RoundedCornerShape(100.dp)
    val bgBrush = if (primary) {
        val alphaTop = if (enabled) 0.88f else 0.45f
        val alphaBottom = if (enabled) 1f else 0.55f
        Brush.verticalGradient(
            colors = listOf(
                accent.copy(alpha = alphaTop).let { Color(
                    red = (it.red * 0.72f + 0.28f).coerceAtMost(1f),
                    green = (it.green * 0.72f + 0.28f).coerceAtMost(1f),
                    blue = (it.blue * 0.72f + 0.28f).coerceAtMost(1f),
                    alpha = alphaTop
                ) },
                accent.copy(alpha = alphaBottom)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.86f),
                Color.White.copy(alpha = 0.68f)
            )
        )
    }

    val borderBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = if (primary) 0.65f else 0.95f),
            if (primary) accent.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.85f)
        )
    )

    Box(
        modifier = modifier
            .scale(scale)
            .heightIn(min = 54.dp)
            .shadow(
                elevation = if (primary && enabled) 10.dp else 3.dp,
                shape = shape,
                spotColor = accent.copy(alpha = 0.35f),
                ambientColor = accent.copy(alpha = 0.16f)
            )
            .clip(shape)
            .background(bgBrush)
            .border(1.2.dp, borderBrush, shape)
            .clickable(
                enabled = enabled && !loading,
                interactionSource = interactionSource,
                indication = ripple(color = if (primary) Color.White.copy(alpha = 0.35f) else accent.copy(alpha = 0.18f)),
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.2.dp,
                    color = if (primary) Color.White else accent
                )
                Spacer(Modifier.width(10.dp))
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(19.dp),
                    tint = if (primary) Color.White else accent
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp,
                    fontSize = 15.sp
                ),
                color = if (primary) Color.White else accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Light frosted pill text field matching the License Login key box and path inputs.
 */
@Composable
fun LiquidGlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    singleLine: Boolean = true,
    monospace: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    val palette = LocalLiquidGlassPalette.current
    val shape = RoundedCornerShape(28.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.66f))
            .border(1.2.dp, Color.White.copy(alpha = 0.92f), shape)
            .padding(start = 20.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
            }
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = if (monospace) {
                            TextStyle(fontFamily = MonoFontFamily, fontSize = 15.sp)
                        } else {
                            MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp)
                        },
                        color = palette.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = singleLine,
                    textStyle = if (monospace) {
                        TextStyle(
                            fontFamily = MonoFontFamily,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = palette.textPrimary
                        )
                    } else {
                        MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = palette.textPrimary
                        )
                    },
                    cursorBrush = SolidColor(palette.accent),
                    visualTransformation = visualTransformation,
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (trailingContent != null) {
                Spacer(Modifier.width(8.dp))
                trailingContent()
            }
        }
    }
}
