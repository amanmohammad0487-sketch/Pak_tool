package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

enum class GlassThemePreset(
    val id: String,
    val displayName: String,
    val accent: Color,
    val accentSecondary: Color,
    val swatchStart: Color,
    val swatchEnd: Color,
    val bgTop: Color,
    val bgMid: Color,
    val bgBottom: Color,
    val orbTopLeft: Color,
    val orbMidRight: Color,
    val orbBottomLeft: Color,
    val orbBottomRight: Color
) {
    LIQUID_GREEN(
        id = "liquid_green",
        displayName = "Liquid Green",
        accent = Color(0xFF0E9F4E),
        accentSecondary = Color(0xFF059669),
        swatchStart = Color(0xFF16C978),
        swatchEnd = Color(0xFF05B5A2),
        bgTop = Color(0xFFF1FFF8),
        bgMid = Color(0xFFD5FAEA),
        bgBottom = Color(0xFFBDF7E2),
        orbTopLeft = Color(0xFF9DF7D2),
        orbMidRight = Color(0xFF7BEFB3),
        orbBottomLeft = Color(0xFF66E6DC),
        orbBottomRight = Color(0xFF96F2C4)
    ),
    ICE_BLUE(
        id = "ice_blue",
        displayName = "Ice Blue",
        accent = Color(0xFF0284C7),
        accentSecondary = Color(0xFF0EA5E9),
        swatchStart = Color(0xFF1A91F0),
        swatchEnd = Color(0xFF0BB6E8),
        bgTop = Color(0xFFF2FBFF),
        bgMid = Color(0xFFD8F2FF),
        bgBottom = Color(0xFFBDE7FF),
        orbTopLeft = Color(0xFF9EE3FF),
        orbMidRight = Color(0xFF81CFFF),
        orbBottomLeft = Color(0xFF67D7F8),
        orbBottomRight = Color(0xFFA2D8FF)
    ),
    AURORA_VIOLET(
        id = "aurora_violet",
        displayName = "Aurora Violet",
        accent = Color(0xFF7C3AED),
        accentSecondary = Color(0xFFC026D3),
        swatchStart = Color(0xFFA855F7),
        swatchEnd = Color(0xFFD946EF),
        bgTop = Color(0xFFFCF7FF),
        bgMid = Color(0xFFEDE0FF),
        bgBottom = Color(0xFFE3D5FF),
        orbTopLeft = Color(0xFFE9C4FF),
        orbMidRight = Color(0xFFD3A6FF),
        orbBottomLeft = Color(0xFFF5B4F8),
        orbBottomRight = Color(0xFFC2B2FF)
    ),
    SUNSET_AMBER(
        id = "sunset_amber",
        displayName = "Sunset Amber",
        accent = Color(0xFFEA580C),
        accentSecondary = Color(0xFFF97316),
        swatchStart = Color(0xFFFF6B1A),
        swatchEnd = Color(0xFFF94C2B),
        bgTop = Color(0xFFFFFAF4),
        bgMid = Color(0xFFFFE8D6),
        bgBottom = Color(0xFFFFDFC4),
        orbTopLeft = Color(0xFFFFD8A8),
        orbMidRight = Color(0xFFFFB588),
        orbBottomLeft = Color(0xFFFFB4A2),
        orbBottomRight = Color(0xFFFFD29D)
    ),
    FROST_LIGHT(
        id = "frost_light",
        displayName = "Frost Light",
        accent = Color(0xFF1877F2),
        accentSecondary = Color(0xFF4F7DF3),
        swatchStart = Color(0xFF6495FF),
        swatchEnd = Color(0xFF8B7CFF),
        bgTop = Color(0xFFF3FAFF),
        bgMid = Color(0xFFDFE8FF),
        bgBottom = Color(0xFFBCE6FF),
        orbTopLeft = Color(0xFFD9F3FF),
        orbMidRight = Color(0xFFB7B6FF),
        orbBottomLeft = Color(0xFF75D5FF),
        orbBottomRight = Color(0xFFAEC7FF)
    );

    companion object {
        fun fromId(id: String?): GlassThemePreset =
            entries.firstOrNull { it.id == id } ?: FROST_LIGHT
    }
}

@Immutable
data class LiquidGlassPalette(
    val preset: GlassThemePreset,
    val glassSurfaceTop: Color = Color(0xB8FFFFFF),
    val glassSurfaceBottom: Color = Color(0x78FFFFFF),
    val glassModalSurface: Color = Color(0xEBFFFFFF),
    val glassPillSurface: Color = Color(0xA6FFFFFF),
    val glassRimBright: Color = Color(0xEEFFFFFF),
    val glassRimSoft: Color = Color(0x99FFFFFF),
    val textPrimary: Color = Color(0xFF0F172A),
    val textSecondary: Color = Color(0xFF475569),
    val textMuted: Color = Color(0xFF64748B),
    val success: Color = Color(0xFF0E9F4E),
    val warning: Color = Color(0xFFD97706),
    val error: Color = Color(0xFFDC2626),
    val accent: Color = preset.accent,
    val accentSecondary: Color = preset.accentSecondary
)
