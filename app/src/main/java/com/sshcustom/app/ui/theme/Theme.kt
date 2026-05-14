package com.sshcustom.app.ui.theme

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

/**
 * Material 3 theme. On API 31+ we pull the dynamic Material You palette from
 * the system; below that we use a fixed dark palette tuned to mirror the
 * WebUI's `--primary` / `--surface` so the app and the dashboard look like
 * the same product.
 *
 * The fallback colors are deliberately sampled from the WebUI's existing
 * `:root` CSS variables (see src/module/webroot/index.html) — that file is
 * the source of truth for the brand color, since it predates the app and is
 * what users see in browser/KSU-Next first.
 */

private val FallbackDark = darkColorScheme(
    primary = Color(0xFFADC6FF),
    onPrimary = Color(0xFF10203A),
    primaryContainer = Color(0xFF284777),
    onPrimaryContainer = Color(0xFFDBE5FF),
    secondary = Color(0xFFBDC7DC),
    onSecondary = Color(0xFF273141),
    secondaryContainer = Color(0xFF3F4759),
    onSecondaryContainer = Color(0xFFDCE5F9),
    tertiary = Color(0xFFDCC1DD),
    onTertiary = Color(0xFF3F2A41),
    tertiaryContainer = Color(0xFF573E5B),
    onTertiaryContainer = Color(0xFFF8DAFB),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE3E2E8),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE3E2E8),
    surfaceVariant = Color(0xFF44464F),
    onSurfaceVariant = Color(0xFFC5C6D0),
    surfaceContainer = Color(0xFF1D2025),
    surfaceContainerHigh = Color(0xFF282A30),
    surfaceContainerHighest = Color(0xFF33353B),
    outline = Color(0xFF8F919A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val FallbackLight = lightColorScheme(
    primary = Color(0xFF415F91),
    primaryContainer = Color(0xFFD7E3FF),
)

@Composable
fun SshCustomTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> FallbackDark
        else -> FallbackLight
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content,
    )
}
