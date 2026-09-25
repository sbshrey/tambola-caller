package io.github.sbshrey.tambola.game.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF121D2B)
val Ivory = Color(0xFFFFF5E6)
// Printed ticket/ball artwork keeps its own ink and paper; screen accents follow the theme.
val Panel: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainer
val Saffron: Color @Composable get() = MaterialTheme.colorScheme.primary
val Jade: Color @Composable get() = MaterialTheme.colorScheme.secondary
val Muted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val Coral: Color @Composable get() = MaterialTheme.colorScheme.error

internal val NightColors = darkColorScheme(
    primary = Color(0xFFFFC078), onPrimary = Ink, secondary = Color(0xFF8CDBBB), onSecondary = Ink,
    background = Ink, onBackground = Ivory, surface = Color(0xFF1E2C3C), onSurface = Ivory,
    surfaceVariant = Color(0xFF2B3B4D), onSurfaceVariant = Color(0xFFBBC8D4),
    error = Color(0xFFFFB4AC), onError = Color(0xFF540C12), errorContainer = Color(0xFF74232A), onErrorContainer = Color(0xFFFFDAD6),
    primaryContainer = Color(0xFF60441F), onPrimaryContainer = Color(0xFFFFDEAD),
    secondaryContainer = Color(0xFF214E42), onSecondaryContainer = Color(0xFFB6EFD5),
    tertiary = Color(0xFFFFAFAB), onTertiary = Ink, tertiaryContainer = Color(0xFF633F40), onTertiaryContainer = Color(0xFFFFDAD7),
    surfaceContainerLowest = Color(0xFF0D1723), surfaceContainerLow = Color(0xFF182636),
    surfaceContainer = Color(0xFF1E2C3C), surfaceContainerHigh = Color(0xFF293C4F), surfaceContainerHighest = Color(0xFF31465B),
    surfaceBright = Color(0xFF34475D), surfaceDim = Ink, surfaceTint = Color(0xFFFFC078),
    outline = Color(0xFF9AAABA), outlineVariant = Color(0xFF425467),
    inverseSurface = Ivory, inverseOnSurface = Ink, inversePrimary = Color(0xFF824500),
)

internal val DayColors = lightColorScheme(
    primary = Color(0xFF824500), onPrimary = Color.White, secondary = Color(0xFF21634D), onSecondary = Color.White,
    background = Color(0xFFFFF9F0), onBackground = Ink, surface = Color(0xFFFFFDF8), onSurface = Ink,
    surfaceVariant = Color(0xFFEDE5D8), onSurfaceVariant = Color(0xFF4E5C63),
    error = Color(0xFFA32931), onError = Color.White, errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF540C12),
    primaryContainer = Color(0xFFFFDEAD), onPrimaryContainer = Color(0xFF513000),
    secondaryContainer = Color(0xFFD3EEDD), onSecondaryContainer = Color(0xFF143F31),
    tertiary = Color(0xFF97413F), onTertiary = Color.White, tertiaryContainer = Color(0xFFFFDAD7), onTertiaryContainer = Color(0xFF592320),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFFF4E5),
    surfaceContainer = Color(0xFFF7EDDF), surfaceContainerHigh = Color(0xFFF0E6D8), surfaceContainerHighest = Color(0xFFE8DED0),
    surfaceBright = Color(0xFFFFFDF8), surfaceDim = Color(0xFFE5DBCE), surfaceTint = Color(0xFF824500),
    outline = Color(0xFF6F797B), outlineVariant = Color(0xFFC8C1B7),
    inverseSurface = Ink, inverseOnSurface = Ivory, inversePrimary = Color(0xFFFFC078),
)

@Composable
fun TambolaTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) NightColors else DayColors,
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
