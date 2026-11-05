@file:OptIn(ExperimentalMaterial3Api::class)

package ae.sayyara.app.ui.screens.stats

import ae.sayyara.app.R
import ae.sayyara.app.ui.appViewModel
import ae.sayyara.app.ui.common.economyText
import ae.sayyara.app.ui.common.economyValue
import ae.sayyara.app.ui.common.forStore
import ae.sayyara.app.ui.common.labelRes
import ae.sayyara.app.ui.components.ChartLegend
import ae.sayyara.app.ui.components.ChartPoint
import ae.sayyara.app.ui.components.ChoiceChips
import ae.sayyara.app.ui.components.EmptyState
import ae.sayyara.app.ui.components.LineChart
import ae.sayyara.app.ui.components.LineSeries
import ae.sayyara.app.ui.components.SectionCard
import ae.sayyara.app.ui.components.StackedBar
import ae.sayyara.app.ui.components.StackedBarChart
import ae.sayyara.app.ui.format.UiFormatter
import ae.sayyara.app.ui.format.rememberUiFormatter
import ae.sayyara.app.ui.theme.CategoryPalette
import ae.sayyara.core.model.ExpenseCategoryKey
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.datetime.LocalDate

@Composable
fun StatsScreen() {
    val vm = appViewModel { StatsViewModel(it.container, it.settings) }
    val state by vm.state.collectAsStateWithLifecycle()
    val fmt = rememberUiFormatter()

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_stats)) }) }) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            !state.hasVehicle -> EmptyState(
                title = stringResource(R.string.dashboard_no_vehicle_title),
                body = stringResource(R.string.dashboard_no_vehicle_body),
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    ChoiceChips(
                        options = StatsPeriod.entries,
                        selected = state.period,
                        onSelect = vm::setPeriod,
                        label = { stringResource(it.labelRes()) },
                    )
                }
                item { SummaryCard(state, fmt) }
                item { ConsumptionCard(state, fmt) }
                item { SpendCard(state, fmt) }
                item { CostPerKmCard(state, fmt) }
                item { PriceCard(state, fmt) }
            }
        }
    }
}

private fun StatsPeriod.labelRes(): Int = when (this) {
    StatsPeriod.M3 -> R.string.period_3m
    StatsPeriod.M6 -> R.string.period_6m
    StatsPeriod.M12 -> R.string.period_12m
    StatsPeriod.ALL -> R.string.period_all
}

@Composable
private fun SummaryCard(state: StatsState, fmt: UiFormatter) {
    val unit = state.settings.economyUnit.forStore(state.storeKind)
    SectionCard {
        Row(Modifier.fillMaxWidth()) {
            Metric(stringResource(R.string.stats_total_spend), fmt.aed(state.totalAed), Modifier.weight(1f))
            Metric(
                stringResource(R.string.stats_distance),
                stringResource(R.string.km_value, fmt.integer(state.totalKm)),
                Modifier.weight(1f),
            )
        }
        Row(Modifier.fillMaxWidth()) {
            Metric(stringResource(R.string.dashboard_average), economyText(state.averagePer100km, unit, fmt), Modifier.weight(1f))
            Metric(
                stringResource(R.string.stats_cost_per_km),
                state.costPerKmAed?.let { fmt.aed(it) } ?: stringResource(R.string.value_missing),
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge)
    }
}

private fun epochDayLabel(x: Double, fmt: UiFormatter): String =
    fmt.date(LocalDate.fromEpochDays(Math.round(x)))

@Composable
private fun ConsumptionCard(state: StatsState, fmt: UiFormatter) {
    val unit = state.settings.economyUnit.forStore(state.storeKind)
    val unitLabel = stringResource(unit.labelRes())
    val title = stringResource(R.string.stats_consumption_trend, unitLabel)
    SectionCard(title = title) {
        LineChart(
            series = listOf(
                LineSeries(
                    points = state.consumption.map { ChartPoint(it.x, economyValue(it.y, unit)) },
                    color = MaterialTheme.colorScheme.primary,
                ),
            ),
            yLabel = { fmt.number(it, 1) },
            xLabel = { epochDayLabel(it, fmt) },
            contentDescription = title,
        )
        Text(
            stringResource(R.string.stats_consumption_explain),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun categoryColor(c: ExpenseCategoryKey): Color = CategoryPalette[c.ordinal % CategoryPalette.size]

@Composable
private fun SpendCard(state: StatsState, fmt: UiFormatter) {
    val title = stringResource(R.string.stats_monthly_spend)
    val used = linkedSetOf<ExpenseCategoryKey>()
    val bars = state.months.map { m ->
        val segs = buildList {
            if (m.fuelAed > 0) { add(categoryColor(ExpenseCategoryKey.FUEL) to m.fuelAed); used += ExpenseCategoryKey.FUEL }
            m.expensesAed.entries
                .filter { it.key != ExpenseCategoryKey.FUEL && it.value > 0 }
                .sortedBy { it.key.ordinal }
                .forEach { add(categoryColor(it.key) to it.value); used += it.key }
        }
        StackedBar(fmt.monthShort(m.month), segs)
    }
    SectionCard(title = title) {
        StackedBarChart(bars = bars, yLabel = { fmt.integer(it) }, contentDescription = title)
        val legend = used.sortedBy { it.ordinal }.map { categoryColor(it) to stringResource(it.labelRes()) }
        ChartLegend(legend)
    }
}

@Composable
private fun CostPerKmCard(state: StatsState, fmt: UiFormatter) {
    val title = stringResource(R.string.stats_cost_per_km_trend)
    SectionCard(title = title) {
        LineChart(
            series = listOf(
                LineSeries(state.costPerKm.map { ChartPoint(it.x, it.y) }, MaterialTheme.colorScheme.tertiary),
            ),
            yLabel = { fmt.number(it, 2) },
            xLabel = { fmt.monthShort(monthOfIndex(it)) },
            contentDescription = title,
        )
        Text(
            stringResource(R.string.stats_cost_per_km_explain),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PriceCard(state: StatsState, fmt: UiFormatter) {
    val title = stringResource(R.string.stats_price_history, stringResource(state.grade.labelRes()))
    val official = MaterialTheme.colorScheme.primary
    val mine = MaterialTheme.colorScheme.tertiary
    SectionCard(title = title) {
        LineChart(
            series = listOf(
                LineSeries(state.officialPrices.map { ChartPoint(it.x, it.y) }, official),
                LineSeries(state.userPrices.map { ChartPoint(it.x, it.y) }, mine, drawLine = false, dotRadius = 4.dp),
            ),
            yLabel = { fmt.number(it, 2) },
            xLabel = { fmt.monthShort(monthOfIndex(it)) },
            contentDescription = title,
        )
        ChartLegend(
            listOf(
                official to stringResource(R.string.stats_legend_official),
                mine to stringResource(R.string.stats_legend_your_fills),
            ),
        )
    }
}
