package com.fieldops.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Dark scheme — every token used across the app needs to be filled in here
// so screens that reference MaterialTheme.colorScheme.* land on sensible
// dark values. `outline` and `outlineVariant` drive border/divider colors;
// `surfaceVariant` backs "elevated-inside-a-card" regions like timeline
// stripes or muted chips.
private val DarkColorScheme = darkColorScheme(
    primary = PrimaryLightColor,
    onPrimary = TextOnPrimary,
    primaryContainer = PrimaryDarkColor,
    onPrimaryContainer = TextOnPrimary,
    secondary = SecondaryLightColor,
    onSecondary = TextOnPrimary,
    secondaryContainer = SecondaryDarkColor,
    onSecondaryContainer = TextOnPrimary,
    tertiary = AccentTealLight,
    onTertiary = TextOnPrimary,
    error = ErrorLightColor,
    onError = TextOnPrimary,
    background = Color(0xFF0F1115),        // almost-black page bg
    onBackground = Color(0xFFE5E7EB),      // near-white foreground
    surface = Color(0xFF1C1F26),           // cards / sheets
    onSurface = Color(0xFFE5E7EB),
    surfaceVariant = Color(0xFF2A2F3A),    // muted rows, chips
    onSurfaceVariant = Color(0xFF9CA3AF),  // muted text
    outline = Color(0xFF3F4754),
    outlineVariant = Color(0xFF2A2F3A),
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryColor,
    onPrimary = TextOnPrimary,
    primaryContainer = PrimaryLightColor,
    onPrimaryContainer = TextPrimary,
    secondary = SecondaryColor,
    onSecondary = TextOnPrimary,
    secondaryContainer = SecondaryLightColor,
    onSecondaryContainer = TextPrimary,
    tertiary = AccentTeal,
    onTertiary = TextOnPrimary,
    error = ErrorColor,
    onError = TextOnPrimary,
    background = BackgroundColor,          // slate-100 page bg
    onBackground = TextPrimary,            // slate-900
    surface = SurfaceColor,                // pure white cards
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFFF1F5F9),    // slate-100 muted rows
    onSurfaceVariant = TextSecondary,      // slate-500 muted text
    outline = Color(0xFFD1D5DB),
    outlineVariant = Color(0xFFE2E8F0),
)

@Composable
fun FieldOpsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+; off by default so the brand
    // indigo stays recognisable across devices.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Status bar picks up the brand primary in both themes. Icons are
            // always white-on-indigo so they stay visible — don't flip based
            // on darkTheme.
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
