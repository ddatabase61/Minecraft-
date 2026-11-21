package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val BlockForgeDarkColorScheme = darkColorScheme(
    primary = EmeraldCore,
    onPrimary = Color(0xFF042612),
    primaryContainer = EmeraldContainerDark,
    onPrimaryContainer = EmeraldSoft,
    secondary = DiamondCyan,
    onSecondary = Color(0xFF002A33),
    secondaryContainer = DiamondContainerDark,
    onSecondaryContainer = Color(0xFFB3F3FF),
    tertiary = TorchAmber,
    onTertiary = Color(0xFF331B00),
    tertiaryContainer = TorchContainerDark,
    onTertiaryContainer = Color(0xFFFFE0B2),
    error = RedstoneRuby,
    onError = Color(0xFF3B0806),
    errorContainer = RedstoneContainerDark,
    onErrorContainer = Color(0xFFFFDAD6),
    background = ObsidianBg,
    onBackground = ChalkWhite,
    surface = ObsidianSurface,
    onSurface = ChalkWhite,
    surfaceVariant = ObsidianSurfaceLow,
    onSurfaceVariant = StoneMuted,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceLow,
    surfaceContainerHigh = ObsidianSurfaceHigh,
    surfaceContainerHighest = ObsidianSurfaceHighest,
    outline = ObsidianOutline,
    outlineVariant = ObsidianOutlineVariant
)

private val BlockForgeLightColorScheme = lightColorScheme(
    primary = EmeraldDeep,
    onPrimary = Color.White,
    primaryContainer = EmeraldSoft,
    onPrimaryContainer = Color(0xFF052914),
    secondary = DiamondDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC2F2FF),
    onSecondaryContainer = Color(0xFF002028),
    tertiary = TorchDeep,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE0B2),
    onTertiaryContainer = Color(0xFF2B1700),
    background = DaylightBg,
    onBackground = DaylightOnSurface,
    surface = DaylightSurface,
    onSurface = DaylightOnSurface,
    surfaceVariant = DaylightSurfaceVariant,
    onSurfaceVariant = DaylightMuted,
    outline = Color(0xFF7B9488),
    outlineVariant = Color(0xFFC7D9D0)
)

// Crisp, subtly chamfered voxel-inspired shapes
private val BlockForgeShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun BlockForgeTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) BlockForgeDarkColorScheme else BlockForgeLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = BlockForgeShapes,
        content = content
    )
}
