package ae.sayyara.app.ui.screens.home

import ae.sayyara.app.ui.components.CategoryIconPill
import ae.sayyara.app.ui.components.FuelGradeBadge
import ae.sayyara.app.ui.components.TabularMetricText
import ae.sayyara.app.ui.theme.MetricSize
import ae.sayyara.app.R
import ae.sayyara.app.ui.appViewModel
import ae.sayyara.app.ui.common.economyValue
import ae.sayyara.app.ui.common.forStore
import ae.sayyara.app.ui.common.labelRes
import ae.sayyara.app.ui.components.CategoryIcon
import ae.sayyara.app.ui.components.EmptyState
import ae.sayyara.app.ui.components.Overline
import ae.sayyara.app.ui.components.PillBadge
import ae.sayyara.app.ui.components.SayyaraCard
import ae.sayyara.app.ui.components.TintedIcon
import ae.sayyara.app.ui.format.UiFormatter
import ae.sayyara.app.ui.format.rememberUiFormatter
import ae.sayyara.app.ui.settings.UiSettings
import ae.sayyara.app.ui.theme.HeroNumberStyle
import ae.sayyara.app.ui.theme.SayyaraTheme
import ae.sayyara.app.ui.theme.badgeColor
import ae.sayyara.app.ui.theme.color
import ae.sayyara.app.ui.settings.ThemeMode
import ae.sayyara.core.consumption.EconomyUnit
import ae.sayyara.core.model.Emirate
import ae.sayyara.core.model.EnergyStoreKind
import ae.sayyara.core.model.ExpenseCategoryKey
import ae.sayyara.core.model.FuelGrade
import ae.sayyara.core.model.Reminder
import ae.sayyara.core.model.ReminderKind
import ae.sayyara.core.model.Vehicle
import ae.sayyara.core.model.YearMonth
import ae.sayyara.core.reminders.ReminderPlanner
import ae.sayyara.core.uae.PriceQuote
import ae.sayyara.core.uae.QuoteStatus
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.abs
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

@Composable
fun HomeScreen(
    onAddFill: () -> Unit,
    onAddVehicle: () -> Unit,
    onOpenReminders: () -> Unit,
    onMulkiyaWizard: () -> Unit,
    onOpenStats: () -> Unit,
) {
    val vm = appViewModel { HomeViewModel(it.container, it.settings) }
    val state by vm.state.collectAsStateWithLifecycle()
    HomeContent(state, vm::selectVehicle, onAddFill, onAddVehicle, onOpenReminders, onMulkiyaWizard, onOpenStats)
}

/** Stateless dashboard; previewable. */
@Composable
fun HomeContent(
    state: HomeUiState,
    onSelectVehicle: (String) -> Unit,
    onAddFill: () -> Unit,
    onAddVehicle: () -> Unit,
    onOpenReminders: () -> Unit,
    onMulkiyaWizard: () -> Unit,
    onOpenStats: () -> Unit,
) {
    val fmt = rememberUiFormatter()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { VehiclePill(state, onSelectVehicle) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            if (state.vehicle != null) {
                ExtendedFloatingActionButton(
                    onClick = onAddFill,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.dashboard_add_fill)) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.heightIn(min = 56.dp),
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
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { EconomyHeroCard(state, fmt, onOpenStats) }
                item { MonthSpendCard(state, fmt) }
                item { PriceTickerCard(state, fmt) }
                item { RemindersCard(state, fmt, onOpenReminders, onMulkiyaWizard) }
            }
        }
    }
}

// ---------------------------------------------------------------- top bar

private fun Emirate.code(): String = when (this) {
    Emirate.DUBAI -> "DXB"
    Emirate.ABU_DHABI -> "AUH"
    Emirate.SHARJAH -> "SHJ"
    Emirate.AJMAN -> "AJM"
    Emirate.UMM_AL_QUWAIN -> "UAQ"
    Emirate.RAS_AL_KHAIMAH -> "RAK"
    Emirate.FUJAIRAH -> "FUJ"
}

@Composable
private fun VehiclePill(state: HomeUiState, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val v = state.vehicle
    val multi = state.vehicles.size > 1
    Box {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.heightIn(min = 48.dp).then(if (multi) Modifier.clip(CircleShape).clickable { open = true } else Modifier),
        ) {
            Row(
                Modifier.padding(start = 12.dp, end = if (multi) 8.dp else 14.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.DirectionsCar, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(
                    v?.name ?: stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                val plate = listOfNotNull(v?.emirate?.code(), v?.plate).joinToString(" ")
                if (plate.isNotBlank()) {
                    Text(
                        plate,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                if (multi) Icon(Icons.Filled.ExpandMore, contentDescription = stringResource(R.string.dashboard_switch_vehicle))
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            state.vehicles.forEach { item ->
                DropdownMenuItem(text = { Text(item.name) }, onClick = { open = false; onSelect(item.id) })
            }
        }
    }
}

// ---------------------------------------------------------------- card 1: economy hero

@Composable
private fun EconomyHeroCard(state: HomeUiState, fmt: UiFormatter, onOpenStats: () -> Unit) {
    val unit = state.settings.economyUnit.forStore(state.storeKind)
    SayyaraCard(onClick = onOpenStats) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            HeroFigure(stringResource(R.string.dashboard_last), state.lastPer100km, unit, fmt, Modifier.weight(1f))
            Column(
                Modifier.fillMaxHeight().padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                VerticalDivider(Modifier.height(20.dp), color = MaterialTheme.colorScheme.outlineVariant)
                PillBadge(stringResource(unit.labelRes()), MaterialTheme.colorScheme.primary, Modifier.padding(vertical = 6.dp))
                VerticalDivider(Modifier.height(20.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
            HeroFigure(stringResource(R.string.dashboard_average), state.averagePer100km, unit, fmt, Modifier.weight(1f), alignEnd = true)
        }

        // Context pill: what the figures are based on.
        val explanation = when {
            state.basedOnFills > 0 && state.basedSince != null ->
                stringResource(R.string.dashboard_based_on, fmt.integer(state.basedOnFills), fmt.date(state.basedSince))
            state.fillCount == 0 -> stringResource(R.string.dashboard_explain_no_fills)
            else -> stringResource(R.string.dashboard_explain_need_two_full)
        }
        Column(
            Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Text(explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.pendingPartials > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.padding(horizontal = 4.dp).size(8.dp).background(MaterialTheme.colorScheme.secondary, CircleShape))
                    Text(
                        stringResource(R.string.dashboard_pending_partials, fmt.integer(state.pendingPartials)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        val perKm = state.totalCostPerKmAed ?: state.fuelCostPerKmAed
        if (perKm != null) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.dashboard_running_cost),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                Text(
                    stringResource(R.string.dashboard_per_km_value, fmt.aed(perKm)),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun HeroFigure(
    label: String,
    per100km: Double?,
    unit: EconomyUnit,
    fmt: UiFormatter,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false,
) {
    Column(modifier, horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Overline(label)
        val text = if (per100km == null || per100km <= 0.0) stringResource(R.string.value_missing)
        else fmt.number(economyValue(per100km, unit), 1)
        TabularMetricText(text, size = MetricSize.Hero, horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start)
    }
}

// ---------------------------------------------------------------- card 2: month spend

@Composable
private fun MonthSpendCard(state: HomeUiState, fmt: UiFormatter) {
    SayyaraCard {
        Overline(
            state.month?.let { stringResource(R.string.dashboard_month_spend, fmt.monthLong(it)) }
                ?: stringResource(R.string.dashboard_month_spend_plain),
        )
        TabularMetricText(fmt.aed(state.monthTotalAed), size = MetricSize.Large)
        val positive = state.monthBreakdown.filter { it.second > 0 }
        if (positive.isEmpty()) {
            Text(
                stringResource(R.string.dashboard_month_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SayyaraCard
        }
        // Proportional segmented bar.
        Row(
            Modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            positive.forEach { (cat, aed) ->
                Box(Modifier.weight(aed.toFloat()).fillMaxHeight().background(cat.color()))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            positive.forEach { (cat, aed) ->
                CategoryIconPill(cat, Modifier.fillMaxWidth(), amount = fmt.aed(aed))
            }
        }
    }
}

// ---------------------------------------------------------------- card 3: price ticker

@Composable
private fun PriceTickerCard(state: HomeUiState, fmt: UiFormatter) {
    val grade = state.priceGrade ?: return
    val now = state.priceNow ?: return
    SayyaraCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Overline(stringResource(R.string.dashboard_price_official), Modifier.weight(1f))
            PillBadge(fmt.monthShort(now.month), MaterialTheme.colorScheme.onSurfaceVariant)
            when (now.status) {
                QuoteStatus.PROVISIONAL -> PillBadge(stringResource(R.string.badge_provisional), MaterialTheme.colorScheme.secondary)
                QuoteStatus.FORECAST -> PillBadge(stringResource(R.string.badge_forecast), MaterialTheme.colorScheme.secondary)
                QuoteStatus.FINAL -> Unit
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FuelGradeBadge(grade)
                TabularMetricText(
                    fmt.aed(now.aedPerLitre),
                    unit = "/ " + stringResource(R.string.unit_litre),
                    size = MetricSize.Large,
                )
            }
            state.pricePrevious?.let { prev -> TrendPill(now.aedPerLitre - prev.aedPerLitre, fmt.monthShort(prev.month), fmt) }
        }
        if (now.status != QuoteStatus.FINAL) {
            Text(stringResource(R.string.price_provisional_explain), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TrendPill(delta: Double, prevMonth: String, fmt: UiFormatter) {
    val fils = fmt.integer(abs(delta) * 100)
    val (icon, color, text) = when {
        delta > 0.0005 -> Triple(Icons.AutoMirrored.Filled.TrendingUp, SayyaraTheme.colors.alert, stringResource(R.string.dashboard_price_delta_up, fils, prevMonth))
        delta < -0.0005 -> Triple(Icons.AutoMirrored.Filled.TrendingDown, SayyaraTheme.colors.success, stringResource(R.string.dashboard_price_delta_down, fils, prevMonth))
        else -> Triple(Icons.AutoMirrored.Filled.TrendingFlat, MaterialTheme.colorScheme.onSurfaceVariant, stringResource(R.string.dashboard_price_same, prevMonth))
    }
    Surface(shape = CircleShape, color = color.copy(alpha = 0.14f), contentColor = color) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------- card 4: reminders

@Composable
private fun RemindersCard(state: HomeUiState, fmt: UiFormatter, onOpenReminders: () -> Unit, onMulkiyaWizard: () -> Unit) {
    SayyaraCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Overline(stringResource(R.string.dashboard_upcoming), Modifier.weight(1f))
            TextButton(onClick = onOpenReminders) { Text(stringResource(R.string.dashboard_all_reminders)) }
        }
        if (!state.hasMulkiyaReminder) {
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable(onClick = onMulkiyaWizard)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TintedIcon(Icons.Filled.Badge, MaterialTheme.colorScheme.primary, size = 40.dp)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.mulkiya_setup_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(stringResource(R.string.mulkiya_setup_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        if (state.reminders.isEmpty() && state.hasMulkiyaReminder) {
            Text(stringResource(R.string.reminders_none_due), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.reminders.forEach { ReminderDueRow(it, fmt) }
    }
}

/** Urgency row: overdue items get a crimson tint; upcoming items show time/distance progress. */
@Composable
fun ReminderDueRow(item: ReminderDue, fmt: UiFormatter, modifier: Modifier = Modifier) {
    val r = item.reminder
    val due = item.due
    val tint = if (due.overdue) SayyaraTheme.colors.alert else MaterialTheme.colorScheme.primary
    Row(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (due.overdue) SayyaraTheme.colors.alertContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TintedIcon(if (r.kind == ReminderKind.MULKIYA) Icons.Filled.Badge else Icons.Filled.Notifications, tint)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                r.title.ifBlank { stringResource(r.kind.labelRes()) },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(dueText(item, fmt), style = MaterialTheme.typography.bodySmall, color = if (due.overdue) SayyaraTheme.colors.alert else MaterialTheme.colorScheme.onSurfaceVariant)
            if (!due.overdue) {
                val window = r.leadDays.coerceAtLeast(14) * 2
                due.daysLeft?.let { d ->
                    LinearProgressIndicator(
                        progress = { (1f - d.toFloat() / window).coerceIn(0.05f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                        color = tint,
                        trackColor = MaterialTheme.colorScheme.outlineVariant,
                        drawStopIndicator = {},
                    )
                }
            }
        }
    }
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

// ---------------------------------------------------------------- previews

@Composable
private fun HomePreview(dark: Boolean) {
    SayyaraTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
        HomeContent(HomePreviewData.state(), {}, {}, {}, {}, {}, {})
    }
}

@Preview(name = "Light - EN", showBackground = true, heightDp = 1500)
@Composable
private fun HomeLightPreview() = HomePreview(false)

@Preview(name = "Dark - EN", showBackground = true, heightDp = 1500)
@Composable
private fun HomeDarkPreview() = HomePreview(true)

@Preview(name = "Light - AR (RTL)", locale = "ar", showBackground = true, heightDp = 1500)
@Composable
private fun HomeArabicPreview() = HomePreview(false)
