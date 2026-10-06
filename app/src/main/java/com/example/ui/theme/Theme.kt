package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = CyanPrimaryDark,
    onPrimary = NavyBackground,
    primaryContainer = CyanSecondary,
    onPrimaryContainer = Color.White,
    secondary = AccentTeal,
    onSecondary = NavyBackground,
    background = NavyBackground,
    onBackground = OnSurfaceDark,
    surface = NavySurface,
    onSurface = OnSurfaceDark,
    surfaceVariant = NavySurfaceVariant,
    onSurfaceVariant = Color(0xFFBAC7D5),
    outline = OutlineDark,
    error = StatusRed,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = CyanSecondary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCAF0F8),
    onPrimaryContainer = Color(0xFF03045E),
    secondary = CyanPrimary,
    onSecondary = Color.White,
    background = Color(0xFFF4F7FB),
    onBackground = OnSurfaceLight,
    surface = Color.White,
    onSurface = OnSurfaceLight,
    surfaceVariant = Color(0xFFE8EEF5),
    onSurfaceVariant = Color(0xFF475569),
    outline = OutlineLight,
    error = StatusRed,
    onError = Color.White
)

@Composable
fun TeleManageTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Use our handcrafted palette for a distinctive aesthetic
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
