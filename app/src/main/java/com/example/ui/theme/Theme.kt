package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CaisseLightColorScheme = lightColorScheme(
    primary = ActionBlue,
    onPrimary = Color.White,
    primaryContainer = ActionBlueSoft,
    onPrimaryContainer = ActionBlueDark,
    secondary = TextSecondary,
    onSecondary = Color.White,
    secondaryContainer = AppSurfaceVariant,
    onSecondaryContainer = TextPrimary,
    tertiary = DisponibleText,
    onTertiary = Color.White,
    tertiaryContainer = DisponibleBackground,
    onTertiaryContainer = DisponibleText,
    error = RuptureText,
    onError = Color.White,
    errorContainer = RuptureBackground,
    onErrorContainer = RuptureText,
    background = AppBackground,
    onBackground = TextPrimary,
    surface = AppSurface,
    onSurface = TextPrimary,
    surfaceVariant = AppSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = AppOutline,
    outlineVariant = AppOutlineSubtle
)

@Composable
fun CaisseStockTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CaisseLightColorScheme,
        typography = AppTypography,
        content = content
    )
}
