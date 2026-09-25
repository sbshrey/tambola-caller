package io.github.sbshrey.tambola.game.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF121D2B)
val Panel = Color(0xFF1E2C3C)
val Saffron = Color(0xFFFFC078)
val Jade = Color(0xFF8CDBBB)
val Ivory = Color(0xFFFFF5E6)
val Muted = Color(0xFFAEBCCA)
val Coral = Color(0xFFFF9F9B)

@Composable
fun TambolaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(primary = Saffron, onPrimary = Ink, secondary = Jade, onSecondary = Ink,
            background = Ink, onBackground = Ivory, surface = Panel, onSurface = Ivory,
            surfaceVariant = Color(0xFF2B3B4D), onSurfaceVariant = Muted, error = Coral,
            primaryContainer = Color(0xFF60441F), onPrimaryContainer = Color(0xFFFFDEAD),
            secondaryContainer = Color(0xFF214E42), onSecondaryContainer = Color(0xFFB6EFD5),
            tertiary = Coral, onTertiary = Ink, tertiaryContainer = Color(0xFF633F40), onTertiaryContainer = Color(0xFFFFDAD7),
            surfaceContainerLowest = Color(0xFF0D1723), surfaceContainerLow = Color(0xFF182636),
            surfaceContainer = Panel, surfaceContainerHigh = Color(0xFF293C4F), surfaceContainerHighest = Color(0xFF31465B),
            surfaceBright = Color(0xFF34475D), surfaceDim = Ink, surfaceTint = Saffron,
            outline = Color(0xFF7F92A6), outlineVariant = Color(0xFF425467)),
        typography = Typography(
            headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 42.sp),
            headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
            titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
            titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp),
            bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
            labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
        ), content = content,
    )
}
