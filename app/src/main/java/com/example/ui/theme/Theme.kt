package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ArenaDarkColorScheme = darkColorScheme(
    primary = NeonPurple,
    onPrimary = TextPrimaryWhite,
    primaryContainer = NeonPurpleDeep,
    onPrimaryContainer = NeonPurpleBright,
    secondary = CyberCyan,
    onSecondary = ArenaObsidian,
    secondaryContainer = CyberCyanDeep,
    onSecondaryContainer = CyberCyanSoft,
    tertiary = TrophyGold,
    onTertiary = ArenaObsidian,
    background = ArenaObsidian,
    onBackground = TextPrimaryWhite,
    surface = ArenaDeepSurface,
    onSurface = TextPrimaryWhite,
    surfaceVariant = ArenaCardSurface,
    onSurfaceVariant = TextSecondarySlate,
    outline = ArenaBorderGlow,
    error = CrimsonDanger,
    onError = TextPrimaryWhite
)

val ArenaShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp)
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ArenaDarkColorScheme,
        typography = Typography,
        shapes = ArenaShapes,
        content = content
    )
}
