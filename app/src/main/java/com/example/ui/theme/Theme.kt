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

private val LightColorScheme = lightColorScheme(
    primary = BabyCoralPrimary,
    onPrimary = BabyCoralOnPrimary,
    primaryContainer = BabyCoralContainer,
    onPrimaryContainer = BabyCoralOnContainer,
    secondary = BabyTealSecondary,
    onSecondary = BabyTealOnSecondary,
    secondaryContainer = BabyTealContainer,
    onSecondaryContainer = BabyTealOnContainer,
    tertiary = BabyAmberTertiary,
    tertiaryContainer = BabyAmberContainer,
    onTertiaryContainer = BabyAmberOnContainer,
    background = WarmBackground,
    surface = WarmSurface,
    surfaceVariant = WarmSurfaceVariant,
    onBackground = Color(0xFF201A18),
    onSurface = Color(0xFF201A18),
    onSurfaceVariant = Color(0xFF52443D)
)

private val DarkColorScheme = darkColorScheme(
    primary = BabyCoralPrimaryDark,
    primaryContainer = Color(0xFF73200B),
    secondary = BabyTealSecondaryDark,
    secondaryContainer = Color(0xFF005047),
    tertiary = BabyAmberTertiaryDark,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onBackground = Color(0xFFEDE0DB),
    onSurface = Color(0xFFEDE0DB),
    onSurfaceVariant = Color(0xFFD7C2B9)
)

@Composable
fun BabyCareTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Default to false to preserve brand warm identity
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
