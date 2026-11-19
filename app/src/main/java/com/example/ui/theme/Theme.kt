package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val LocalLiquidGlassPalette = staticCompositionLocalOf {
    LiquidGlassPalette(GlassThemePreset.FROST_LIGHT)
}

val MajlishShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(18.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(30.dp),
    extraLarge = RoundedCornerShape(34.dp)
)

@Composable
fun MajlishTheme(
    themePreset: GlassThemePreset = GlassThemePreset.FROST_LIGHT,
    content: @Composable () -> Unit
) {
    val palette = remember(themePreset) { LiquidGlassPalette(themePreset) }

    val colorScheme = remember(palette) {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = Color.White,
            primaryContainer = palette.accent.copy(alpha = 0.14f),
            onPrimaryContainer = palette.textPrimary,
            secondary = palette.accentSecondary,
            onSecondary = Color.White,
            secondaryContainer = palette.accentSecondary.copy(alpha = 0.14f),
            onSecondaryContainer = palette.textPrimary,
            background = palette.preset.bgTop,
            onBackground = palette.textPrimary,
            surface = Color(0xF0FFFFFF),
            onSurface = palette.textPrimary,
            surfaceVariant = Color(0xCCEAF2FF),
            onSurfaceVariant = palette.textSecondary,
            outline = Color(0xBBFFFFFF),
            outlineVariant = Color(0x88CBD5E1),
            error = palette.error,
            onError = Color.White
        )
    }

    CompositionLocalProvider(LocalLiquidGlassPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MajlishTypography,
            shapes = MajlishShapes,
            content = content
        )
    }
}
