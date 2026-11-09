package ae.sayyara.app.ui.components

import ae.sayyara.app.R
import ae.sayyara.app.ui.common.labelRes
import ae.sayyara.app.ui.theme.ChipShape
import ae.sayyara.app.ui.theme.MetricSize
import ae.sayyara.app.ui.theme.PillShape
import ae.sayyara.app.ui.theme.SayyaraTheme
import ae.sayyara.app.ui.theme.badgeColor
import ae.sayyara.app.ui.theme.color
import ae.sayyara.app.ui.settings.ThemeMode
import ae.sayyara.core.model.ExpenseCategoryKey
import ae.sayyara.core.model.FuelGrade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------- RTL

/**
 * Mirrors a directional drawable (arrow, chevron, timeline connector) in RTL. Prefer
 * Icons.AutoMirrored.* where one exists; use this for custom drawables and Canvas content.
 */
@Composable
fun Modifier.autoMirror(): Modifier =
    if (LocalLayoutDirection.current == LayoutDirection.Rtl) this.scale(scaleX = -1f, scaleY = 1f) else this

// ---------------------------------------------------------------- metric text

enum class UnitPlacement { Beside, Below }

/**
 * A figure (AED, km, km/L, L) that never wraps: tabular digits, single line, steps the font
 * down to 60% before it would overflow. The unit is set smaller and muted, beside or below.
 */
@Composable
fun TabularMetricText(
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    size: MetricSize = MetricSize.Large,
    placement: UnitPlacement = UnitPlacement.Beside,
    color: Color = LocalContentColor.current,
    unitColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
) {
    val number: @Composable (Modifier) -> Unit = { m ->
        BasicText(
            text = value,
            modifier = m,
            style = size.style.merge(TextStyle(color = color)),
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(
                minFontSize = size.style.fontSize * 0.6f,
                maxFontSize = size.style.fontSize,
                stepSize = 1.sp,
            ),
        )
    }
    val unitText: @Composable () -> Unit = {
        if (unit != null) Text(unit, style = size.unitStyle, color = unitColor, maxLines = 1, softWrap = false)
    }
    when (placement) {
        UnitPlacement.Beside -> Row(modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            number(Modifier.weight(1f, fill = false))
            Box(Modifier.padding(bottom = 4.dp)) { unitText() }
        }
        UnitPlacement.Below -> Column(modifier, horizontalAlignment = horizontalAlignment) {
            number(Modifier)
            unitText()
        }
    }
}

// ---------------------------------------------------------------- fuel grade badge

/** Pump-coded fuel grade pill: coloured dot plus grade name (Super 98 blue, Special 95 green, ...). */
@Composable
fun FuelGradeBadge(grade: FuelGrade, modifier: Modifier = Modifier, filled: Boolean = false) {
    val c = grade.badgeColor()
    Surface(
        modifier = modifier,
        shape = PillShape,
        color = if (filled) c else c.copy(alpha = 0.14f),
        contentColor = if (filled) Color.White else c,
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(8.dp).background(if (filled) Color.White else c, PillShape))
            Text(stringResource(grade.labelRes()), style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------- category pill

/**
 * Category row: tinted icon, name, optional tabular amount at the end. RTL-safe (start/end only).
 */
@Composable
fun CategoryIconPill(
    category: ExpenseCategoryKey,
    modifier: Modifier = Modifier,
    amount: String? = null,
    label: String? = null,
) {
    Row(
        modifier.heightIn(min = 48.dp)
            .background(SayyaraTheme.colors.nested, ChipShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CategoryIcon(category, size = 32.dp)
        Text(
            label ?: stringResource(category.labelRes()),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = amount != null),
        )
        if (amount != null) {
            Text(amount, style = MaterialTheme.typography.titleSmall, color = category.color(), maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------- previews

@Composable
private fun ComponentsSample(dark: Boolean) {
    ae.sayyara.app.ui.theme.SayyaraTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SayyaraCard {
                    Overline(stringResource(R.string.dashboard_last))
                    TabularMetricText("11.4", unit = stringResource(R.string.unit_km_per_l), size = MetricSize.Hero)
                    TabularMetricText("4.04", unit = "AED / L", size = MetricSize.Medium, placement = UnitPlacement.Below)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FuelGradeBadge(FuelGrade.SUPER_98)
                    FuelGradeBadge(FuelGrade.SPECIAL_95, filled = true)
                    FuelGradeBadge(FuelGrade.DIESEL)
                }
                CategoryIconPill(ExpenseCategoryKey.SALIK, amount = "AED 126.00")
                CategoryIconPill(ExpenseCategoryKey.PARKING, amount = "AED 96.00", label = "Mawaqif Zone 365C")
            }
        }
    }
}

@Preview(name = "Light - EN") @Composable private fun ComponentsLight() = ComponentsSample(false)
@Preview(name = "Dark - EN") @Composable private fun ComponentsDark() = ComponentsSample(true)
@Preview(name = "Light - AR (RTL)", locale = "ar") @Composable private fun ComponentsArabic() = ComponentsSample(false)
