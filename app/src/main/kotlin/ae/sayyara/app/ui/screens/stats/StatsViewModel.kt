package ae.sayyara.app.ui.screens.stats

import ae.sayyara.app.data.AppContainer
import ae.sayyara.app.ui.common.selectedVehicleData
import ae.sayyara.app.ui.settings.UiSettings
import ae.sayyara.app.ui.settings.UiSettingsStore
import ae.sayyara.core.consumption.ConsumptionEngine
import ae.sayyara.core.consumption.MonthlyStat
import ae.sayyara.core.consumption.Stats
import ae.sayyara.core.consumption.averageSegments
import ae.sayyara.core.model.EnergyStoreKind
import ae.sayyara.core.model.FuelGrade
import ae.sayyara.core.model.YearMonth
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

enum class StatsPeriod(val months: Int?) { M3(3), M6(6), M12(12), ALL(null) }

/** x = epoch day (consumption, fills) or month index year*12+month-1 (monthly series). */
data class XY(val x: Double, val y: Double)

data class StatsState(
    val loading: Boolean = true,
    val hasVehicle: Boolean = false,
    val period: StatsPeriod = StatsPeriod.M6,
    val settings: UiSettings = UiSettings(),
    val storeKind: EnergyStoreKind = EnergyStoreKind.TANK,
    val grade: FuelGrade = FuelGrade.SPECIAL_95,
    /** per 100 km values, converted to the display unit in the UI. */
    val consumption: List<XY> = emptyList(),
    val months: List<MonthlyStat> = emptyList(),
    val costPerKm: List<XY> = emptyList(),
    val officialPrices: List<XY> = emptyList(),
    val userPrices: List<XY> = emptyList(),
    val totalAed: Double = 0.0,
    val totalKm: Double = 0.0,
    val averagePer100km: Double? = null,
    val costPerKmAed: Double? = null,
)

fun monthIndex(ym: YearMonth): Double = (ym.year * 12 + ym.month - 1).toDouble()

fun monthOfIndex(x: Double): YearMonth {
    val i = Math.round(x).toInt()
    return YearMonth(Math.floorDiv(i, 12), Math.floorMod(i, 12) + 1)
}

class StatsViewModel(container: AppContainer, settings: UiSettingsStore) : ViewModel() {
    private val period = MutableStateFlow(StatsPeriod.M6)

    val state: StateFlow<StatsState> = combine(
        container.selectedVehicleData(),
        container.fuelPrices.observeAll(),
        settings.state,
        period,
    ) { data, prices, ui, p ->
        if (data == null) return@combine StatsState(loading = false, period = p, settings = ui)
        val zone = TimeZone.currentSystemDefault()
        val today = Clock.System.todayIn(zone)
        val nowMonth = YearMonth.of(today)
        val fromMonth = p.months?.let { n -> (1 until n).fold(nowMonth) { m, _ -> m.previous() } }
        fun inPeriod(i: Instant): Boolean =
            fromMonth == null || YearMonth.of(i.toLocalDateTime(zone).date) >= fromMonth

        val store = data.primaryStore
        val events = data.eventsFor(store)
        val result = runCatching { ConsumptionEngine.compute(events, store) }.getOrNull()
        val byId = events.associateBy { it.id }
        val consumption = result?.segments.orEmpty()
            .filter { it.valid && it.per100km > 0 }
            .mapNotNull { seg ->
                val end = byId[seg.endEventId] ?: return@mapNotNull null
                if (!inPeriod(end.timestamp)) return@mapNotNull null
                XY(end.timestamp.toLocalDateTime(zone).date.toEpochDays().toDouble(), seg.per100km)
            }

        val monthly = runCatching { Stats.monthly(data.events, data.expenses, zone) }.getOrDefault(emptyList())
            .filter { fromMonth == null || it.month >= fromMonth }
            .sortedBy { it.month }
        val costPerKm = monthly.mapNotNull { m -> m.costPerKmAed?.let { XY(monthIndex(m.month), it) } }

        val grade = store?.defaultGrade ?: data.vehicle.defaultGrade
        val official = prices.filter { it.grade == grade && (fromMonth == null || it.month >= fromMonth) && it.month <= nowMonth.next() }
            .sortedBy { it.month }
            .map { XY(monthIndex(it.month), it.aedPerLitre) }
        val user = events.filter { it.grade == grade && inPeriod(it.timestamp) }.mapNotNull { e ->
            val price = e.unitPriceAed ?: e.totalAed?.let { t -> if (e.amount > 0) t / e.amount else null }
            price?.let {
                val date = e.timestamp.toLocalDateTime(zone).date
                XY(monthIndex(YearMonth.of(date)) + (date.day - 1) / 31.0, it)
            }
        }

        val totalKm = monthly.sumOf { it.km }
        val totalAed = monthly.sumOf { it.totalAed }
        val segs = result?.averageSegments().orEmpty()
            .filter { s -> byId[s.endEventId]?.let { inPeriod(it.timestamp) } == true }
        val segKm = segs.sumOf { it.km }
        val avg = if (segKm > 0) segs.sumOf { it.amount } / segKm * 100.0 else null

        StatsState(
            loading = false,
            hasVehicle = true,
            period = p,
            settings = ui,
            storeKind = store?.kind ?: EnergyStoreKind.TANK,
            grade = grade,
            consumption = consumption,
            months = monthly,
            costPerKm = costPerKm,
            officialPrices = official,
            userPrices = user,
            totalAed = totalAed,
            totalKm = totalKm,
            averagePer100km = avg,
            costPerKmAed = if (totalKm > 0) totalAed / totalKm else null,
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsState())

    fun setPeriod(p: StatsPeriod) {
        period.value = p
    }
}
