package com.nexarq.app.ui.theme

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

// NEXARQ palette — deep navy + teal accents
val Navy950 = Color(0xFF0B1220)
val Navy900 = Color(0xFF101B2E)
val Navy800 = Color(0xFF16243C)
val Navy700 = Color(0xFF1E304E)
val Teal400 = Color(0xFF3EC8DC)
val Teal500 = Color(0xFF2BB3CC)
val Blue400 = Color(0xFF608CFF)
val Accent400 = Color(0xFF7C5CFF)

private val DarkColors = darkColorScheme(
    primary = Teal400,
    onPrimary = Navy950,
    secondary = Blue400,
    onSecondary = Navy950,
    tertiary = Accent400,
    background = Navy950,
    onBackground = Color(0xFFE6EDF5),
    surface = Navy900,
    onSurface = Color(0xFFE6EDF5),
    surfaceVariant = Navy800,
    onSurfaceVariant = Color(0xFFA9B7C8),
    error = Color(0xFFFF6B6B),
    outline = Color(0xFF39475C),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF007C92),
    onPrimary = Color.White,
    secondary = Color(0xFF3D5FD9),
    onSecondary = Color.White,
    tertiary = Color(0xFF6A4BD8),
    background = Color(0xFFF5F7FB),
    onBackground = Color(0xFF111827),
    surface = Color.White,
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFE8EDF5),
    onSurfaceVariant = Color(0xFF4B5563),
    error = Color(0xFFD0342C),
)

@Composable
fun NexarqTheme(
    themeOverride: String = "system",
    dynamicColors: Boolean = true,
    content: @Composable () -> Unit,
) {
    val darkSystem = isSystemInDarkTheme()
    val darkTheme = when (themeOverride) {
        "dark" -> true
        "light" -> false
        else -> darkSystem
    }

    val colorScheme = when {
        dynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
