@file:OptIn(ExperimentalMaterial3Api::class)

package ae.sayyara.app.ui.screens.dashboard

import ae.sayyara.app.ui.common.economyValue
import ae.sayyara.core.consumption.EconomyUnit
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import ae.sayyara.app.R
import ae.sayyara.app.ui.appViewModel
import ae.sayyara.app.ui.common.economyText
import ae.sayyara.app.ui.common.forStore
import ae.sayyara.app.ui.common.labelRes
import ae.sayyara.app.ui.components.StatusBadge
import ae.sayyara.app.ui.components.EmptyState
import ae.sayyara.app.ui.components.SectionCard
import ae.sayyara.app.ui.format.UiFormatter
import ae.sayyara.app.ui.format.rememberUiFormatter
import ae.sayyara.app.ui.theme.CategoryPalette
import ae.sayyara.app.ui.theme.HeroNumberStyle
import ae.sayyara.core.model.ReminderKind
import ae.sayyara.core.uae.QuoteStatus
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.abs

@Composable
fun DashboardScreen(
    onAddFill: () -> Unit,
    onAddVehicle: () -> Unit,
    onOpenReminders: () -> Unit,
    onMulkiyaWizard: () -> Unit,
    onOpenStats: () -> Unit,
) {
    val vm = appViewModel { DashboardViewModel(it.container, it.settings) }
    val state by vm.state.collectAsStateWithLifecycle()
    val fmt = rememberUiFormatter()

    Scaffold(
        topBar = {
            TopAppBar(title = {
                VehicleSwitcher(state, onSelect = vm::selectVehicle)
            })
        },
        floatingActionButton = {
            if (state.vehicle != null) {
                ExtendedFloatingActionButton(
                    onClick = onAddFill,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.dashboard_add_fill)) },
                )
            }
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.vehicle == null -> EmptyState(
                title = stringResource(R.string.dashboard_no_vehicle_title),
                body = stringResource(R.string.dashboard_no_vehicle_body),
                actionLabel = stringResource(R.string.action_add_vehicle),
                onAction = onAddVehicle,
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { HeroCard(state, fmt, onOpenStats) }
                item { MonthSpendCard(state, fmt) }
                item { PriceCard(state, fmt) }
                item { RemindersCard(state, fmt, onOpenReminders, onMulkiyaWizard) }
            }
        }
    }
}

@Composable
private fun VehicleSwitcher(state: DashboardState, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val title = state.vehicle?.name ?: stringResource(R.string.app_name)
    if (state.vehicles.size <= 1) {
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        return
    }
    Box {
        Row(
            Modifier.clickable { open = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = stringResource(R.string.dashboard_switch_vehicle))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            state.vehicles.forEach { v ->
                DropdownMenuItem(
                    text = { Text(v.name) },
                    onClick = { open = false; onSelect(v.id) },
                )
            }
        }
    }
}

@Composable
private fun HeroCard(state: DashboardState, fmt: UiFormatter, onOpenStats: () -> Unit) {
    val unit = state.settings.economyUnit.forStore(state.storeKind)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenStats),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.dashboard_last), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    EconomyFigure(state.lastPer100km, unit, fmt, HeroNumberStyle)
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.dashboard_average), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    EconomyFigure(state.averagePer100km, unit, fmt, MaterialTheme.typography.headlineMedium)
                }
            }
            // Transparency: always say what the figure is based on.
            val explanation = when {
                state.basedOnFills > 0 && state.basedSince != null -> stringResource(
                    R.string.dashboard_based_on,
                    fmt.integer(state.basedOnFills),
                    fmt.date(state.basedSince),
                )
                state.fillCount == 0 -> stringResource(R.string.dashboard_explain_no_fills)
                else -> stringResource(R.string.dashboard_explain_need_two_full)
            }
            Text(explanation, style = MaterialTheme.typography.bodyMedium)
            if (state.pendingPartials > 0) {
                Text(
                    stringResource(R.string.dashboard_pending_partials, fmt.integer(state.pendingPartials)),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            val perKm = state.totalCostPerKmAed ?: state.fuelCostPerKmAed
            if (perKm != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                Text(
                    stringResource(
                        if (state.totalCostPerKmAed != null) R.string.dashboard_cost_per_km_total else R.string.dashboard_cost_per_km_fuel,
                        fmt.aed(perKm),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun MonthSpendCard(state: DashboardState, fmt: UiFormatter) {
    val month = state.month
    SectionCard(
        title = month?.let { stringResource(R.string.dashboard_month_spend, fmt.monthLong(it)) }
            ?: stringResource(R.string.dashboard_month_spend_plain),
    ) {
        FitText(fmt.aed(state.monthTotalAed), MaterialTheme.typography.headlineMedium)
        if (state.monthBreakdown.isEmpty()) {
            Text(
                stringResource(R.string.dashboard_month_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.monthBreakdown.forEach { (cat, aed) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(CategoryPalette[cat.ordinal % CategoryPalette.size], CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(cat.labelRes()), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(fmt.aed(aed), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun PriceCard(state: DashboardState, fmt: UiFormatter) {
    val grade = state.priceGrade ?: return
    val now = state.priceNow ?: return
    SectionCard(title = stringResource(R.string.dashboard_price_title, stringResource(grade.labelRes()))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.price_per_litre_value, fmt.aed(now.aedPerLitre)),
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(8.dp))
            when (now.status) {
                QuoteStatus.PROVISIONAL -> StatusBadge(stringResource(R.string.badge_provisional), warning = true)
                QuoteStatus.FORECAST -> StatusBadge(stringResource(R.string.badge_forecast), warning = true)
                QuoteStatus.FINAL -> Unit
            }
        }
        Text(
            stringResource(R.string.dashboard_price_month, fmt.monthLong(now.month)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val prev = state.pricePrevious
        if (prev != null) {
            val delta = now.aedPerLitre - prev.aedPerLitre
            val fils = abs(delta) * 100
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when {
                        delta > 0.0005 -> Icons.AutoMirrored.Filled.TrendingUp
                        delta < -0.0005 -> Icons.AutoMirrored.Filled.TrendingDown
                        else -> Icons.AutoMirrored.Filled.TrendingFlat
                    },
                    contentDescription = null,
                    tint = when {
                        delta > 0.0005 -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    },
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        delta > 0.0005 -> stringResource(R.string.dashboard_price_up, fmt.integer(fils), fmt.monthLong(prev.month))
                        delta < -0.0005 -> stringResource(R.string.dashboard_price_down, fmt.integer(fils), fmt.monthLong(prev.month))
                        else -> stringResource(R.string.dashboard_price_same, fmt.monthLong(prev.month))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (now.status != QuoteStatus.FINAL) {
            Text(
                stringResource(R.string.price_provisional_explain),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RemindersCard(
    state: DashboardState,
    fmt: UiFormatter,
    onOpenReminders: () -> Unit,
    onMulkiyaWizard: () -> Unit,
) {
    SectionCard(title = stringResource(R.string.dashboard_upcoming)) {
        if (!state.hasMulkiyaReminder) {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onMulkiyaWizard).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Badge, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.mulkiya_setup_title), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.mulkiya_setup_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (state.reminders.isEmpty() && state.hasMulkiyaReminder) {
            Text(stringResource(R.string.reminders_none_due), style = MaterialTheme.typography.bodyMedium)
        }
        state.reminders.forEach { item ->
            ReminderDueRow(item, fmt)
        }
        TextButton(onClick = onOpenReminders) { Text(stringResource(R.string.dashboard_all_reminders)) }
    }
}

@Composable
fun ReminderDueRow(item: ReminderDue, fmt: UiFormatter, modifier: Modifier = Modifier) {
    val r = item.reminder
    val due = item.due
    Row(modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (r.kind == ReminderKind.MULKIYA) Icons.Filled.Badge else Icons.Filled.Notifications,
            contentDescription = null,
            tint = if (due.overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                r.title.ifBlank { stringResource(r.kind.labelRes()) },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                dueText(item, fmt),
                style = MaterialTheme.typography.bodySmall,
                color = if (due.overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Spacer(Modifier.height(2.dp))
}

@Composable
fun dueText(item: ReminderDue, fmt: UiFormatter): String {
    val due = item.due
    val days = due.daysLeft
    val km = due.kmLeft
    val dateText = due.dueDate?.let { fmt.date(it) }
    return when {
        due.overdue && days != null && days < 0 -> stringResource(R.string.reminder_overdue_days, fmt.integer(-days))
        due.overdue -> stringResource(R.string.reminder_overdue)
        days != null && km != null -> stringResource(R.string.reminder_due_days_km, fmt.integer(days), fmt.integer(km))
        days != null && dateText != null -> stringResource(R.string.reminder_due_days_date, fmt.integer(days), dateText)
        days != null -> stringResource(R.string.reminder_due_days, fmt.integer(days))
        km != null -> stringResource(R.string.reminder_due_km, fmt.integer(km))
        else -> stringResource(R.string.reminder_no_due)
    }
}

/**
 * Consumption figure on one line: the number shrinks to fit (never wraps) and the unit is set
 * smaller beside it, so "12.3 km/L" fits half a card even at large font scales.
 */
@Composable
private fun EconomyFigure(per100km: Double?, unit: EconomyUnit, fmt: UiFormatter, style: TextStyle) {
    if (per100km == null || per100km <= 0.0) {
        Text(stringResource(R.string.value_missing), style = style, maxLines = 1)
        return
    }
    Row(verticalAlignment = Alignment.Bottom) {
        FitText(fmt.number(economyValue(per100km, unit), 1), style, Modifier.weight(1f, fill = false))
        Spacer(Modifier.width(4.dp))
        Text(
            stringResource(unit.labelRes()),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(bottom = 6.dp),
        )
    }
}

/** Single-line text that steps its font size down (to 60%) instead of wrapping. */
@Composable
private fun FitText(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    BasicText(
        text = text,
        modifier = modifier,
        style = style.merge(TextStyle(color = color)),
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(
            minFontSize = style.fontSize * 0.6f,
            maxFontSize = style.fontSize,
            stepSize = 1.sp,
        ),
    )
}
