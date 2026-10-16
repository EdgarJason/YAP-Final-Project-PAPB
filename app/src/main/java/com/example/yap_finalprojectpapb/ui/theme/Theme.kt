package com.example.yap_finalprojectpapb.ui.theme

import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF9ECAFF),
    onPrimary = Color(0xFF00315E),
    primaryContainer = BlueDark,
    onPrimaryContainer = BlueLight,
    secondary = RedNormal,
    onSecondary = NeutralWhite,
    background = Color(0xFF101923),
    surface = Color(0xFF162330),
    onSurfaceVariant = Color(0xFFBAC9DA),
    surfaceVariant = Color(0xFF243446),
    onBackground = NeutralWhite,
    onSurface = NeutralWhite,
    error = RedNormal
)

private val LightColorScheme = lightColorScheme(
    primary = BlueDark,
    onPrimary = NeutralWhite,
    primaryContainer = BlueLight,
    onPrimaryContainer = BlueDarker,
    secondary = RedNormal,
    onSecondary = NeutralWhite,
    background = Color(0xFFF6F8FC),
    surfaceVariant = BlueLight,
    onSurfaceVariant = Neutral600,
    surface = NeutralWhite,
    onBackground = Neutral900,
    onSurface = Neutral900,
    error = RedNormal,
    outline = BlueLightActive
)

@Composable
fun YAPFinalProjectPAPBTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
