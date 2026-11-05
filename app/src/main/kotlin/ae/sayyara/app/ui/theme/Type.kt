package ae.sayyara.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Base = Typography()

/**
 * System font (Roboto for Latin, Noto Naskh/Sans Arabic for Arabic) with slightly taller line
 * heights on body styles so Arabic diacritics are not clipped.
 */
val SayyaraTypography = Typography(
    displaySmall = Base.displaySmall.copy(fontWeight = FontWeight.SemiBold),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = Base.bodyLarge.copy(lineHeight = 26.sp),
    bodyMedium = Base.bodyMedium.copy(lineHeight = 22.sp),
    bodySmall = Base.bodySmall.copy(lineHeight = 18.sp),
    labelLarge = Base.labelLarge,
)

/** Large figure style for the dashboard hero numbers. */
val HeroNumberStyle = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Bold, lineHeight = 52.sp)
