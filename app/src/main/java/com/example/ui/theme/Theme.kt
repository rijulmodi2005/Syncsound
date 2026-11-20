package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

private val SyncSoundDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = Color(0xFF002026),
    primaryContainer = Color(0xFF003B47),
    onPrimaryContainer = Color(0xFF9DF2FF),
    secondary = SignalEmerald,
    onSecondary = Color(0xFF002110),
    secondaryContainer = Color(0xFF00391E),
    onSecondaryContainer = Color(0xFFA7F5C5),
    tertiary = AdminAzure,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF182C54),
    onTertiaryContainer = Color(0xFFD6E2FF),
    background = ObsidianBg,
    onBackground = TextPrimaryDark,
    surface = CharcoalSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ElevatedCardSurface,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = CharcoalSurface,
    surfaceContainer = ElevatedCardSurface,
    surfaceContainerHigh = SurfaceContainerHigh,
    outline = SubtleOutline,
    outlineVariant = Color(0xFF232C40),
    error = AlertCoral,
    onError = Color(0xFF2F0004)
)

private val SyncSoundLightColorScheme = lightColorScheme(
    primary = CyanPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8F3FA),
    onPrimaryContainer = Color(0xFF002026),
    secondary = EmeraldAccentLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD2F7DF),
    onSecondaryContainer = Color(0xFF002110),
    tertiary = AzureSecondaryLight,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDCE5FF),
    onTertiaryContainer = Color(0xFF091E4E),
    background = AlabasterBg,
    onBackground = TextPrimaryLight,
    surface = PureWhiteSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = SoftSlateSurface,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLowest = PureWhiteSurface,
    surfaceContainerLow = AlabasterBg,
    surfaceContainer = SoftSlateSurface,
    surfaceContainerHigh = Color(0xFFDCE4F2),
    outline = SubtleOutlineLight,
    outlineVariant = Color(0xFFE3EAF4),
    error = Color(0xFFD32F2F),
    onError = Color.White
)

val SyncSoundShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun SyncSoundTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) SyncSoundDarkColorScheme else SyncSoundLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SyncSoundTypography,
        shapes = SyncSoundShapes,
        content = content
    )
}
