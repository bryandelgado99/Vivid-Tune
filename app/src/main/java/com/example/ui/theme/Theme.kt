package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val OndaDarkColorScheme = darkColorScheme(
    primary = CoralPrimary,
    onPrimary = CoralOnPrimary,
    primaryContainer = CoralPrimaryContainer,
    onPrimaryContainer = CoralOnPrimaryContainer,
    secondary = SandSecondary,
    onSecondary = Color(0xFF3F261F),
    secondaryContainer = SandSecondaryContainer,
    onSecondaryContainer = Color(0xFFFFDAD0),
    background = ObsidianSurface,
    onBackground = CreamOnSurface,
    surface = ObsidianSurface,
    onSurface = CreamOnSurface,
    surfaceContainerLowest = Color(0xFF0B0A0D),
    surfaceContainerLow = ObsidianSurfaceContainer,
    surfaceContainer = ObsidianSurfaceContainer,
    surfaceContainerHigh = ObsidianSurfaceHigh,
    surfaceContainerHighest = Color(0xFF312B3B),
    onSurfaceVariant = MutedOnSurfaceVariant,
    outline = Color(0xFF534B57),
    outlineVariant = Color(0xFF332E38)
)

private val OndaLightColorScheme = lightColorScheme(
    primary = Color(0xFFB93F21),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD1),
    onPrimaryContainer = Color(0xFF3D0900),
    secondary = Color(0xFF77574E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDAD1),
    onSecondaryContainer = Color(0xFF2C150F),
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightBackground,
    onSurface = LightOnSurface,
    surfaceContainerLow = LightSurfaceContainer,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = Color(0xFFEBE2E5),
    onSurfaceVariant = LightOnSurfaceVariant
)

val OndaShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(32.dp),
    extraLarge = RoundedCornerShape(40.dp)
)

@Composable
fun OndaTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val baseScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> OndaDarkColorScheme
        else -> OndaLightColorScheme
    }

    MaterialTheme(
        colorScheme = baseScheme,
        typography = OndaTypography,
        shapes = OndaShapes,
        content = content
    )
}
