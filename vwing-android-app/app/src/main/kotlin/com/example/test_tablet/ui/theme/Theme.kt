package com.example.test_tablet.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val FarmColorScheme = lightColorScheme(
    primary = FarmGreen,
    onPrimary = Color.White,
    primaryContainer = FarmGreenContainer,
    onPrimaryContainer = FarmGreenDark,
    secondary = FarmAmber,
    onSecondary = Color.White,
    secondaryContainer = FarmAmberContainer,
    onSecondaryContainer = FarmAmberOnContainer,
    background = FarmBackground,
    onBackground = FarmInk,
    surface = Color.White,
    onSurface = FarmInk,
    surfaceVariant = FarmSurfaceVariant,
    onSurfaceVariant = FarmInkMuted,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerLow = Color.White,
    outline = FarmOutline,
    outlineVariant = FarmOutline,
    error = HealthBad,
)

private val OpsColorScheme = darkColorScheme(
    primary = OpsPrimary,
    onPrimary = OpsOnPrimary,
    primaryContainer = Color(0xFF1F3A27),
    onPrimaryContainer = OpsPrimary,
    secondary = OpsAmber,
    onSecondary = Color(0xFF2C1F00),
    secondaryContainer = OpsAmberContainer,
    onSecondaryContainer = OpsAmber,
    background = OpsBackground,
    onBackground = OpsInk,
    surface = OpsSurface,
    onSurface = OpsInk,
    surfaceVariant = OpsSurfaceHigh,
    onSurfaceVariant = OpsInkMuted,
    surfaceContainer = OpsSurface,
    surfaceContainerHigh = OpsSurfaceHigh,
    surfaceContainerLow = OpsSurface,
    outline = OpsOutline,
    outlineVariant = OpsOutline,
    error = Color(0xFFFF8A80),
)

@Composable
fun FarmTheme(content: @Composable () -> Unit) = AppTheme(dark = false, content = content)

@Composable
fun OpsTheme(content: @Composable () -> Unit) = AppTheme(dark = true, content = content)

@Composable
private fun AppTheme(dark: Boolean, content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(
        colorScheme = if (dark) OpsColorScheme else FarmColorScheme,
        typography = Typography,
        content = content,
    )
}
