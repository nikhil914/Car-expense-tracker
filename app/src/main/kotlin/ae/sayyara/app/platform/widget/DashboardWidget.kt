package ae.sayyara.app.platform.widget

import ae.sayyara.app.R
import ae.sayyara.app.appContainer
import ae.sayyara.app.platform.DeepLinks
import ae.sayyara.app.platform.WorkScheduler
import ae.sayyara.core.consumption.ConsumptionEngine
import ae.sayyara.core.consumption.EconomyUnit
import ae.sayyara.core.consumption.Stats
import ae.sayyara.core.consumption.Units
import ae.sayyara.core.model.EnergyStoreKind
import ae.sayyara.core.model.FuelGrade
import ae.sayyara.core.model.YearMonth
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import java.util.Locale
import kotlinx.coroutines.flow.first

/** Everything the widget renders, already localised. */
data class DashboardWidgetState(
    val vehicleName: String?,
    val consumptionValue: String,
    val consumptionLabel: String,
    val spendValue: String,
    val spendLabel: String,
    val priceValue: String,
    val priceLabel: String,
)

/**
 * Home-screen widget: latest consumption, this month's spend (AED) and the current Special 95 price,
 * plus an "Add fill" button (deep link `sayyara://add-fill`). Data is read from the repositories each
 * time Glance asks; [DashboardWidgetUpdater] calls updateAll() when that data changes.
 */
class DashboardWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = loadState(context)
        val addFill = DeepLinks.intent(context, DeepLinks.ADD_FILL)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val dashboard = DeepLinks.intent(context, DeepLinks.DASHBOARD)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val addFillLabel = context.getString(R.string.widget_add_fill)
        val title = context.getString(R.string.widget_title)
        provideContent {
            GlanceTheme {
                WidgetContent(state, title, addFillLabel, addFill, dashboard)
            }
        }
    }

    @Composable
    private fun WidgetContent(
        state: DashboardWidgetState,
        title: String,
        addFillLabel: String,
        addFill: Intent,
        dashboard: Intent,
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(16.dp)
                .padding(12.dp)
                .clickable(actionStartActivity(dashboard)),
            verticalAlignment = Alignment.Top,
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = state.vehicleName ?: title,
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            )
            Spacer(GlanceModifier.height(6.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Metric(state.consumptionValue, state.consumptionLabel, GlanceModifier.defaultWeight())
                Metric(state.spendValue, state.spendLabel, GlanceModifier.defaultWeight())
                Metric(state.priceValue, state.priceLabel, GlanceModifier.defaultWeight())
            }
            Spacer(GlanceModifier.height(8.dp))
            Button(
                text = addFillLabel,
                onClick = actionStartActivity(addFill),
                modifier = GlanceModifier.fillMaxWidth(),
            )
        }
    }

    @Composable
    private fun Metric(value: String, label: String, modifier: GlanceModifier) {
        Column(modifier = modifier) {
            Text(
                text = value,
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold),
            )
            Text(
                text = label,
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
            )
        }
    }

    companion object {
        private const val DASH = "–"

        suspend fun loadState(context: Context): DashboardWidgetState {
            val c = context.appContainer
            val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
            val today = WorkScheduler.todayInUae()
            val month = YearMonth.of(today)

            val price = c.fuelPrices.priceFor(month, FuelGrade.SPECIAL_95) ?: c.fuelPrices.latest(FuelGrade.SPECIAL_95)
            val priceValue = price?.let { String.format(locale, "%.2f", it.aedPerLitre) } ?: DASH
            val priceLabel = context.getString(R.string.widget_price_label)

            val vehicleId = c.vehicles.observeSelectedVehicleId().first()
            if (vehicleId == null) {
                return DashboardWidgetState(
                    vehicleName = context.getString(R.string.widget_no_vehicle),
                    consumptionValue = DASH, consumptionLabel = context.getString(R.string.widget_consumption_label_kmpl),
                    spendValue = DASH, spendLabel = context.getString(R.string.widget_spend_label),
                    priceValue = priceValue, priceLabel = priceLabel,
                )
            }

            val vehicle = c.vehicles.observeVehicles().first().firstOrNull { it.id == vehicleId }
            val stores = c.vehicles.observeStores(vehicleId).first()
            val events = c.events.observeEvents(vehicleId).first()
            val expenses = c.expenses.observeExpenses(vehicleId).first()

            // Primary store: the tank if there is one (PHEV / bi-fuel), else the first store.
            val store = stores.firstOrNull { it.kind == EnergyStoreKind.TANK } ?: stores.firstOrNull()
            val storeEvents = if (store == null) events else events.filter { it.storeId == store.id }
            val isBattery = store?.kind == EnergyStoreKind.BATTERY
            val unit = if (isBattery) EconomyUnit.KM_PER_KWH else EconomyUnit.KM_PER_L
            val per100 = if (storeEvents.isEmpty()) null else ConsumptionEngine.compute(storeEvents, store).lastPer100km
            val consumptionValue = per100?.takeIf { it > 0 }
                ?.let { String.format(locale, "%.1f", Units.fromPer100km(it, unit)) } ?: DASH

            val monthStat = Stats.monthly(events, expenses, WorkScheduler.UAE_ZONE).firstOrNull { it.month == month }
            val spendValue = monthStat?.let { String.format(locale, "%,.0f", it.totalAed) } ?: String.format(locale, "%d", 0)

            return DashboardWidgetState(
                vehicleName = vehicle?.name,
                consumptionValue = consumptionValue,
                consumptionLabel = context.getString(
                    if (isBattery) R.string.widget_consumption_label_kmkwh else R.string.widget_consumption_label_kmpl,
                ),
                spendValue = spendValue,
                spendLabel = context.getString(R.string.widget_spend_label),
                priceValue = priceValue,
                priceLabel = priceLabel,
            )
        }
    }
}
