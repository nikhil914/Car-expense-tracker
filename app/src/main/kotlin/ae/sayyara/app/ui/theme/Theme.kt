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
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = TealPrimaryLight,
    onPrimary = OnTealPrimaryLight,
    primaryContainer = TealContainerLight,
    onPrimaryContainer = OnTealContainerLight,
    secondary = SandSecondaryLight,
    onSecondary = OnSandSecondaryLight,
    secondaryContainer = SandContainerLight,
    onSecondaryContainer = OnSandContainerLight,
    tertiary = DuneTertiaryLight,
    onTertiary = OnDuneTertiaryLight,
    tertiaryContainer = DuneContainerLight,
    onTertiaryContainer = OnDuneContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    error = ErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
)

private val DarkColors = darkColorScheme(
    primary = TealPrimaryDark,
    onPrimary = OnTealPrimaryDark,
    primaryContainer = TealContainerDark,
    onPrimaryContainer = OnTealContainerDark,
    secondary = SandSecondaryDark,
    onSecondary = OnSandSecondaryDark,
    secondaryContainer = SandContainerDark,
    onSecondaryContainer = OnSandContainerDark,
    tertiary = DuneTertiaryDark,
    onTertiary = OnDuneTertiaryDark,
    tertiaryContainer = DuneContainerDark,
    onTertiaryContainer = OnDuneContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    error = ErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
)

@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun SayyaraTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
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
    MaterialTheme(colorScheme = colors, typography = SayyaraTypography, content = content)
}
