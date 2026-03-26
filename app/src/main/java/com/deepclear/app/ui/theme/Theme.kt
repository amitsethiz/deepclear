package com.deepclear.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = DeepBlue,
    onPrimary = White,
    primaryContainer = DeepBlueLight,
    onPrimaryContainer = White,
    secondary = Teal,
    onSecondary = White,
    secondaryContainer = TealLight,
    onSecondaryContainer = TextPrimary,
    tertiary = Cyan,
    onTertiary = TextPrimary,
    tertiaryContainer = CyanLight,
    onTertiaryContainer = TextPrimary,
    background = LightGray,
    onBackground = TextPrimary,
    surface = White,
    onSurface = TextPrimary,
    surfaceVariant = MediumGray,
    onSurfaceVariant = TextSecondary,
    error = ErrorRed,
    onError = White,
    outline = MediumGray,
    outlineVariant = LightGray
)

private val DarkColorScheme = darkColorScheme(
    primary = TealLight,
    onPrimary = Charcoal,
    primaryContainer = TealDark,
    onPrimaryContainer = DarkTextPrimary,
    secondary = Cyan,
    onSecondary = Charcoal,
    secondaryContainer = DeepBlueDark,
    onSecondaryContainer = DarkTextPrimary,
    tertiary = CyanLight,
    onTertiary = Charcoal,
    tertiaryContainer = DeepBlue,
    onTertiaryContainer = DarkTextPrimary,
    background = Charcoal,
    onBackground = DarkTextPrimary,
    surface = CharcoalSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = CharcoalLight,
    onSurfaceVariant = DarkTextSecondary,
    error = ErrorRedDark,
    onError = Charcoal,
    outline = CharcoalLight,
    outlineVariant = CharcoalSurface
)

@Composable
fun DeepClearTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Explicitly disabled: we enforce "Ocean Fresh" on ALL devices.
    // Dynamic color (Android 12+) would override our brand palette.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalView.current.context
            if (darkTheme) {
                androidx.compose.material3.dynamicDarkColorScheme(context)
            } else {
                androidx.compose.material3.dynamicLightColorScheme(context)
            }
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = DeepClearTypography,
        content = content
    )
}
