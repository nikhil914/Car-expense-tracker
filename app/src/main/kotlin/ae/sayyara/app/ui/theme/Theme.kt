package ae.sayyara.app.ui.theme

import ae.sayyara.app.ui.settings.ThemeMode
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

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

/** M3 Expressive shape tension: 24 dp outer cards, 14 dp nested blocks, 8 dp small chips. */
val SayyaraShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
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
    MaterialTheme(colorScheme = colors, typography = SayyaraTypography, shapes = SayyaraShapes, content = content)
}
