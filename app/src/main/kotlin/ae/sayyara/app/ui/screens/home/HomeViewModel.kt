package ae.sayyara.app.ui.screens.home

import ae.sayyara.app.data.AppContainer
import ae.sayyara.app.ui.common.VehicleData
import ae.sayyara.app.ui.common.activeVehicles
import ae.sayyara.app.ui.common.selectedVehicleData
import ae.sayyara.app.ui.settings.UiSettings
import ae.sayyara.app.ui.settings.UiSettingsStore
import ae.sayyara.core.consumption.ConsumptionEngine
import ae.sayyara.core.consumption.Stats
import ae.sayyara.core.consumption.averageSegments
import ae.sayyara.core.model.EnergyStoreKind
import ae.sayyara.core.model.ExpenseCategoryKey
import ae.sayyara.core.model.FuelGrade
import ae.sayyara.core.model.FuelPrice
import ae.sayyara.core.model.Id
import ae.sayyara.core.model.Reminder
import ae.sayyara.core.model.ReminderKind
import ae.sayyara.core.model.Vehicle
import ae.sayyara.core.model.YearMonth
import ae.sayyara.core.reminders.ReminderPlanner
import ae.sayyara.core.uae.FuelPriceBook
import ae.sayyara.core.uae.PriceQuote
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

data class ReminderDue(val reminder: Reminder, val due: ReminderPlanner.Due)

data class HomeUiState(
    val loading: Boolean = true,
    val vehicles: List<Vehicle> = emptyList(),
    val vehicle: Vehicle? = null,
    val settings: UiSettings = UiSettings(),
    val storeKind: EnergyStoreKind = EnergyStoreKind.TANK,
    val lastPer100km: Double? = null,
    val averagePer100km: Double? = null,
    /** Valid full-to-full segments behind the average, and when the first one started. */
    val basedOnFills: Int = 0,
    val basedSince: Instant? = null,
    val pendingPartials: Int = 0,
    val fuelCostPerKmAed: Double? = null,
    val totalCostPerKmAed: Double? = null,
    val fillCount: Int = 0,
    val month: YearMonth? = null,
    val monthTotalAed: Double = 0.0,
    val monthBreakdown: List<Pair<ExpenseCategoryKey, Double>> = emptyList(),
    val priceGrade: FuelGrade? = null,
    val priceNow: PriceQuote? = null,
    val pricePrevious: PriceQuote? = null,
    val reminders: List<ReminderDue> = emptyList(),
    val hasMulkiyaReminder: Boolean = false,
)

class HomeViewModel(
    private val container: AppContainer,
    settings: UiSettingsStore,
) : ViewModel() {

    val state: StateFlow<HomeUiState> = combine(
        container.activeVehicles(),
        container.selectedVehicleData(),
        container.fuelPrices.observeAll(),
        settings.state,
    ) { vehicles, data, prices, ui -> build(vehicles, data, prices, ui) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        // Pull the latest official monthly prices in the background; failures are silent.
        viewModelScope.launch { runCatching { container.fuelPrices.refresh() } }
    }

    fun selectVehicle(id: Id) {
        viewModelScope.launch { container.vehicles.selectVehicle(id) }
    }

    private fun build(
        vehicles: List<Vehicle>,
        data: VehicleData?,
        prices: List<FuelPrice>,
        ui: UiSettings,
    ): HomeUiState {
        if (data == null) {
            return HomeUiState(loading = false, vehicles = vehicles, settings = ui)
        }
        val zone = TimeZone.currentSystemDefault()
        val today = Clock.System.todayIn(zone)
        val month = YearMonth.of(today)
        val store = data.primaryStore
        val events = data.eventsFor(store)

        val consumption = runCatching { ConsumptionEngine.compute(events, store) }.getOrNull()
        val valid = consumption?.averageSegments().orEmpty()
        val byId = events.associateBy { it.id }
        val since = valid.mapNotNull { byId[it.startEventId]?.timestamp }.minOrNull()

        val monthly = runCatching { Stats.monthly(data.events, data.expenses, zone) }.getOrDefault(emptyList())
        val thisMonth = monthly.firstOrNull { it.month == month }
        val breakdown = buildList {
            if (thisMonth != null) {
                if (thisMonth.fuelAed > 0) add(ExpenseCategoryKey.FUEL to thisMonth.fuelAed)
                thisMonth.expensesAed
                    .filter { (k, v) -> k != ExpenseCategoryKey.FUEL && v > 0 }
                    .forEach { (k, v) -> add(k to v) }
            }
        }.sortedByDescending { it.second }

        // Total cost per km across all logged months (fuel + tolls + parking + ownership).
        val totalKm = monthly.sumOf { it.km }
        val totalAed = monthly.sumOf { it.totalAed }
        val totalPerKm = if (totalKm > 0) totalAed / totalKm else null

        val grade = store?.defaultGrade ?: data.vehicle.defaultGrade
        val book = runCatching { FuelPriceBook(prices) }.getOrNull()
        val priceNow = if (grade == FuelGrade.ELECTRIC) null else book?.quote(month, grade)
        val pricePrev = if (grade == FuelGrade.ELECTRIC) null else book?.quote(month.previous(), grade)

        val odo = data.lastOdometerKm()
        val kmPerDay = data.kmPerDay()
        val dues = data.reminders.filter { !it.done }.mapNotNull { r ->
            runCatching { ReminderDue(r, ReminderPlanner.due(r, today, odo, kmPerDay)) }.getOrNull()
        }.sortedWith(compareBy<ReminderDue>({ !it.due.overdue }, { it.due.daysLeft ?: Int.MAX_VALUE }))

        return HomeUiState(
            loading = false,
            vehicles = vehicles,
            vehicle = data.vehicle,
            settings = ui,
            storeKind = store?.kind ?: EnergyStoreKind.TANK,
            lastPer100km = consumption?.lastPer100km,
            averagePer100km = consumption?.averagePer100km,
            basedOnFills = valid.size,
            basedSince = since,
            pendingPartials = consumption?.pendingPartialEventIds?.size ?: 0,
            fuelCostPerKmAed = consumption?.fuelCostPerKmAed,
            totalCostPerKmAed = totalPerKm,
            fillCount = events.size,
            month = month,
            monthTotalAed = thisMonth?.totalAed ?: 0.0,
            monthBreakdown = breakdown,
            priceGrade = grade,
            priceNow = priceNow,
            pricePrevious = pricePrev?.takeIf { it.month != priceNow?.month },
            reminders = dues.take(4),
            hasMulkiyaReminder = data.reminders.any { it.kind == ReminderKind.MULKIYA && !it.done },
        )
    }
}
