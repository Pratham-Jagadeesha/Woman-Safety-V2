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
    primary = ImmersiveSosRed,
    secondary = ImmersiveDarkAccentPurple,
    tertiary = ImmersiveDarkDeepPurple,
    background = ImmersiveDarkBg,
    surface = ImmersiveDarkContainer,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = ImmersiveDarkTextPrimary,
    onSurface = ImmersiveDarkTextPrimary,
    surfaceVariant = ImmersiveDarkContainerAccent,
    onSurfaceVariant = ImmersiveDarkTextSecondary
)

private val LightColorScheme = lightColorScheme(
    primary = ImmersiveSosRed,
    secondary = ImmersiveAccentPurple,
    tertiary = ImmersiveDeepPurple,
    background = ImmersiveBg,
    surface = ImmersiveContainer,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = ImmersiveTextPrimary,
    onSurface = ImmersiveTextPrimary,
    surfaceVariant = ImmersiveContainerAccent,
    onSurfaceVariant = ImmersiveTextSecondary
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Keep dynamic color toggle if they want system matching on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
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
