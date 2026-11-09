package ae.sayyara.app.ui.theme

import ae.sayyara.app.ui.settings.ThemeMode
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = PrimaryLight, onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight, onPrimaryContainer = OnPrimaryContainerLight,
    secondary = AccentLight, onSecondary = OnAccentLight,
    secondaryContainer = AccentContainerLight, onSecondaryContainer = OnAccentContainerLight,
    tertiary = AccentLight, onTertiary = OnAccentLight,
    tertiaryContainer = AccentContainerLight, onTertiaryContainer = OnAccentContainerLight,
    background = CanvasLight, onBackground = InkLight,
    surface = CanvasLight, onSurface = InkLight,
    surfaceVariant = StoneLight, onSurfaceVariant = SlateLight,
    surfaceContainerLowest = CardLight, surfaceContainerLow = CardLight,
    surfaceContainer = StoneLight, surfaceContainerHigh = StoneLight, surfaceContainerHighest = StoneHighLight,
    surfaceBright = CardLight, surfaceDim = StoneHighLight,
    outline = OutlineLight, outlineVariant = HairlineLight,
    error = Color(0xFFB91C1C), onError = Color.White,
    errorContainer = Color(0xFFFDE2E2), onErrorContainer = Color(0xFF5F0B0B),
)

private val DarkColors = darkColorScheme(
    primary = PrimaryDark, onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark, onPrimaryContainer = OnPrimaryContainerDark,
    secondary = AccentDark, onSecondary = OnAccentDark,
    secondaryContainer = AccentContainerDark, onSecondaryContainer = OnAccentContainerDark,
    tertiary = AccentDark, onTertiary = OnAccentDark,
    tertiaryContainer = AccentContainerDark, onTertiaryContainer = OnAccentContainerDark,
    background = CanvasDark, onBackground = InkDark,
    surface = CanvasDark, onSurface = InkDark,
    surfaceVariant = GraphiteDark, onSurfaceVariant = SlateDark,
    surfaceContainerLowest = CanvasDark, surfaceContainerLow = CardDark,
    surfaceContainer = CardDark, surfaceContainerHigh = GraphiteDark, surfaceContainerHighest = GraphiteHighDark,
    surfaceBright = GraphiteDark, surfaceDim = CanvasDark,
    outline = OutlineDark, outlineVariant = HairlineDark,
    error = Color(0xFFF87171), onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF3B1212), onErrorContainer = Color(0xFFFECACA),
)

@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * Brand palette by default. Dynamic (wallpaper) colour is opt-in from Settings because it
 * replaces the brand look.
 */
@Composable
fun SayyaraTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = isDarkTheme(themeMode)
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(LocalSayyaraColors provides if (dark) DarkExtendedColors else LightExtendedColors) {
        MaterialTheme(colorScheme = colors, typography = SayyaraTypography, shapes = SayyaraShapes, content = content)
    }
}

/**
 * Brand tokens Material 3 has no slot for. Read with `SayyaraTheme.colors`.
 * Grade colours are the same in both themes (familiar pump coding).
 */
@Immutable
data class SayyaraExtendedColors(
    val hairline: Color,
    val nested: Color,
    val success: Color,
    val warning: Color,
    val alert: Color,
    val alertContainer: Color,
)

internal val LightExtendedColors = SayyaraExtendedColors(
    hairline = HairlineLight,
    nested = StoneLight,
    success = Color(0xFF0F9D6E),
    warning = AccentLight,
    alert = AlertRed,
    alertContainer = Color(0xFFFDE8E8),
)

internal val DarkExtendedColors = SayyaraExtendedColors(
    hairline = HairlineDark,
    nested = GraphiteDark,
    success = Color(0xFF34D399),
    warning = AccentDark,
    alert = Color(0xFFF87171),
    alertContainer = Color(0xFF3B1212),
)

val LocalSayyaraColors = staticCompositionLocalOf { LightExtendedColors }

object SayyaraTheme {
    val colors: SayyaraExtendedColors
        @Composable @ReadOnlyComposable get() = LocalSayyaraColors.current
}
