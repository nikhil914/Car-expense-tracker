package ae.sayyara.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Base = Typography()

/** Tabular figures so AED, km and km/L columns align and do not jitter as values change. */
const val TABULAR = "tnum"

/**
 * System font (Roboto for Latin, Noto Sans Arabic for Arabic). Body line heights are ~15% taller
 * than M3 defaults so Arabic ascenders and descenders are not clipped.
 */
val SayyaraTypography = Typography(
    displaySmall = Base.displaySmall.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = TABULAR),
    headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = TABULAR),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = TABULAR),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR),
    bodyLarge = Base.bodyLarge.copy(lineHeight = 27.sp, fontFeatureSettings = TABULAR),
    bodyMedium = Base.bodyMedium.copy(lineHeight = 23.sp, fontFeatureSettings = TABULAR),
    bodySmall = Base.bodySmall.copy(lineHeight = 19.sp, fontFeatureSettings = TABULAR),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = Base.labelMedium.copy(fontWeight = FontWeight.SemiBold),
    labelSmall = Base.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp),
)

/** Large figure style for the dashboard hero numbers. */
val HeroNumberStyle = TextStyle(
    fontSize = 44.sp, fontWeight = FontWeight.Bold, lineHeight = 50.sp, fontFeatureSettings = TABULAR,
)

/** Adds tabular figures to any style (AED, km, km/L, L must never jitter or misalign). */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = TABULAR)

/** Metric sizes for [ae.sayyara.app.ui.components.TabularMetricText]. */
enum class MetricSize(val style: TextStyle, val unitStyle: TextStyle) {
    Hero(HeroNumberStyle, TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR)),
    Large(TextStyle(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TABULAR),
        TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR)),
    Medium(TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR),
        TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR)),
    Small(TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR),
        TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR)),
}
