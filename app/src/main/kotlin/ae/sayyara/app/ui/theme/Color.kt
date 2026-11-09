package ae.sayyara.app.ui.theme

import ae.sayyara.core.model.ExpenseCategoryKey
import ae.sayyara.core.model.FuelGrade
import androidx.compose.ui.graphics.Color

// "Oasis & Asphalt" palette. Light = "Warm Desert Alabaster" (sunlight at the pump),
// Dark = "Midnight Asphalt" (night driving).

// Light
internal val CanvasLight = Color(0xFFF7F5F0)
internal val CardLight = Color(0xFFFFFFFF)
internal val StoneLight = Color(0xFFF0ECE1)
internal val StoneHighLight = Color(0xFFE9E4D7)
internal val PrimaryLight = Color(0xFF0A5C5A)
internal val OnPrimaryLight = Color(0xFFFFFFFF)
internal val PrimaryContainerLight = Color(0xFFD4F0EE)
internal val OnPrimaryContainerLight = Color(0xFF043332)
internal val AccentLight = Color(0xFFC87D32)
internal val OnAccentLight = Color(0xFFFFFFFF)
internal val AccentContainerLight = Color(0xFFF8E3CC)
internal val OnAccentContainerLight = Color(0xFF4A2A08)
internal val InkLight = Color(0xFF111618)
internal val SlateLight = Color(0xFF5A6568)
internal val HairlineLight = Color(0xFFE5E0D5)
internal val OutlineLight = Color(0xFFB9B2A4)

// Dark
internal val CanvasDark = Color(0xFF0B0F11)
internal val CardDark = Color(0xFF141A1D)
internal val GraphiteDark = Color(0xFF1D2529)
internal val GraphiteHighDark = Color(0xFF253035)
internal val PrimaryDark = Color(0xFF2DD4BF)
internal val OnPrimaryDark = Color(0xFF042F2E)
internal val PrimaryContainerDark = Color(0xFF113D3B)
internal val OnPrimaryContainerDark = Color(0xFF99F6E4)
internal val AccentDark = Color(0xFFF59E0B)
internal val OnAccentDark = Color(0xFF2B1A00)
internal val AccentContainerDark = Color(0xFF3D2A08)
internal val OnAccentContainerDark = Color(0xFFFCD9A0)
internal val InkDark = Color(0xFFF3F6F8)
internal val SlateDark = Color(0xFF8E9B9F)
internal val HairlineDark = Color(0xFF232D32)
internal val OutlineDark = Color(0xFF3A474D)

// Semantic
val AlertRed = Color(0xFFEF4444)
val TrendDownGreen = Color(0xFF10B981)

/** UAE pump colour coding for fuel grades. */
fun FuelGrade.badgeColor(): Color = when (this) {
    FuelGrade.SUPER_98 -> Color(0xFF2563EB)
    FuelGrade.SPECIAL_95 -> Color(0xFF10B981)
    FuelGrade.E_PLUS_91 -> Color(0xFFF59E0B)
    FuelGrade.DIESEL -> Color(0xFF64748B)
    FuelGrade.ELECTRIC -> Color(0xFF06B6D4)
    else -> Color(0xFF94A3B8)
}

/** Fixed, theme-independent series colours for expense categories. */
fun ExpenseCategoryKey.color(): Color = when (this) {
    ExpenseCategoryKey.FUEL -> Color(0xFF0D9488)
    ExpenseCategoryKey.SALIK -> Color(0xFF2563EB)
    ExpenseCategoryKey.DARB -> Color(0xFF7C3AED)
    ExpenseCategoryKey.PARKING -> Color(0xFFF59E0B)
    ExpenseCategoryKey.MULKIYA -> Color(0xFFC87D32)
    ExpenseCategoryKey.INSPECTION -> Color(0xFF0891B2)
    ExpenseCategoryKey.INSURANCE -> Color(0xFF4F46E5)
    ExpenseCategoryKey.SERVICE -> Color(0xFF16A34A)
    ExpenseCategoryKey.TYRES -> Color(0xFF475569)
    ExpenseCategoryKey.FINE -> Color(0xFFEF4444)
    ExpenseCategoryKey.WASH -> Color(0xFF06B6D4)
    ExpenseCategoryKey.OTHER -> Color(0xFF94A3B8)
}

/** Kept for existing callers (indexed by category ordinal). */
val CategoryPalette: List<Color> = ExpenseCategoryKey.entries.map { it.color() }
