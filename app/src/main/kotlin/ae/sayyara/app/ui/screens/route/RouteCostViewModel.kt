package ae.sayyara.app.ui.screens.route

import ae.sayyara.app.data.AppContainer
import ae.sayyara.app.ui.common.selectedVehicleData
import ae.sayyara.app.ui.format.parseDecimal
import ae.sayyara.app.ui.format.toInputText
import ae.sayyara.core.consumption.ConsumptionEngine
import ae.sayyara.core.model.Emirate
import ae.sayyara.core.model.FuelGrade
import ae.sayyara.core.model.LatLon
import ae.sayyara.core.route.RouteCostEstimate
import ae.sayyara.core.route.RouteCostEstimator
import ae.sayyara.core.route.RouteCostInput
import ae.sayyara.core.route.RouteProvider
import ae.sayyara.core.route.RouteResult
import ae.sayyara.core.uae.FuelPriceBook
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

enum class DistanceMode { COORDINATES, MANUAL }

enum class RouteError { BAD_COORDINATES, BAD_DISTANCE, BAD_CONSUMPTION, NO_ROUTE, FAILED }

/** The estimator is UAE-only, so departure times are always Gulf Standard Time. */
val UaeZone: TimeZone = TimeZone.of("Asia/Dubai")

data class RouteUiState(
    val mode: DistanceMode = DistanceMode.COORDINATES,
    val fromLat: String = "",
    val fromLon: String = "",
    val toLat: String = "",
    val toLon: String = "",
    val distanceText: String = "",
    val durationText: String = "",
    val date: LocalDate? = null,
    val hour: Int = 8,
    val minute: Int = 0,
    val roundTrip: Boolean = false,
    val passengers: Int = 1,
    val parking: Boolean = false,
    val parkingEmirate: Emirate = Emirate.DUBAI,
    val parkingZone: String = "",
    val parkingHoursText: String = "2",
    /** Always L/100km (or kWh/100km) internally; prefilled from the vehicle's measured average. */
    val per100kmText: String = "",
    val per100kmFromHistory: Boolean = false,
    val grade: FuelGrade = FuelGrade.SPECIAL_95,
    val busy: Boolean = false,
    val error: RouteError? = null,
    val route: RouteResult? = null,
    val estimate: RouteCostEstimate? = null,
)

class RouteCostViewModel(
    private val container: AppContainer,
    private val routeProvider: RouteProvider,
) : ViewModel() {
    private val _state = MutableStateFlow(RouteUiState())
    val state: StateFlow<RouteUiState> = _state.asStateFlow()

    init {
        val now = Clock.System.now().toLocalDateTime(UaeZone)
        _state.update { it.copy(date = now.date, hour = (now.hour + 1) % 24, minute = 0) }
        viewModelScope.launch {
            val d = container.selectedVehicleData().first() ?: run {
                _state.update { it.copy(per100kmText = DEFAULT_PER_100KM.toInputText(1)) }
                return@launch
            }
            val store = d.primaryStore
            val avg = runCatching { ConsumptionEngine.compute(d.eventsFor(store), store).averagePer100km }.getOrNull()
            _state.update {
                it.copy(
                    grade = store?.defaultGrade ?: d.vehicle.defaultGrade,
                    per100kmText = (avg ?: DEFAULT_PER_100KM).toInputText(1),
                    per100kmFromHistory = avg != null,
                    parkingEmirate = d.vehicle.emirate ?: Emirate.DUBAI,
                )
            }
        }
    }

    fun update(transform: (RouteUiState) -> RouteUiState) =
        _state.update { transform(it).copy(error = null) }

    fun swap() = update { it.copy(fromLat = it.toLat, fromLon = it.toLon, toLat = it.fromLat, toLon = it.fromLon, route = null) }

    fun estimate() {
        val s = _state.value
        val per100 = parseDecimal(s.per100kmText)?.takeIf { it > 0 && it < 100 }
            ?: return fail(RouteError.BAD_CONSUMPTION)
        val date = s.date ?: Clock.System.now().toLocalDateTime(UaeZone).date
        val departure = LocalDateTime(date, LocalTime(s.hour, s.minute)).toInstant(UaeZone)
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val route: RouteResult = when (s.mode) {
                DistanceMode.MANUAL -> {
                    val km = parseDecimal(s.distanceText)?.takeIf { it > 0 } ?: return@launch fail(RouteError.BAD_DISTANCE)
                    val minutes = parseDecimal(s.durationText)?.takeIf { it > 0 } ?: (km / 80.0 * 60.0)
                    RouteResult(distanceKm = km, durationMin = minutes, polyline = emptyList())
                }
                DistanceMode.COORDINATES -> {
                    val from = latLon(s.fromLat, s.fromLon)
                    val to = latLon(s.toLat, s.toLon)
                    if (from == null || to == null) return@launch fail(RouteError.BAD_COORDINATES)
                    val r = runCatching { routeProvider.route(from, to) }.getOrNull()
                        ?: return@launch fail(RouteError.NO_ROUTE)
                    r
                }
            }
            val prices = runCatching { container.fuelPrices.observeAll().first() }.getOrDefault(emptyList())
            val parkingHours = if (s.parking) parseDecimal(s.parkingHoursText)?.coerceIn(0.0, 72.0) ?: 0.0 else 0.0
            val estimate = runCatching {
                RouteCostEstimator(FuelPriceBook(prices)).estimate(
                    RouteCostInput(
                        route = route,
                        departure = departure,
                        per100km = per100,
                        grade = s.grade,
                        roundTrip = s.roundTrip,
                        parkingEmirate = if (s.parking) s.parkingEmirate else null,
                        parkingZone = if (s.parking) s.parkingZone.trim().ifBlank { null } else null,
                        parkingHours = parkingHours,
                        passengers = s.passengers.coerceAtLeast(1),
                    ),
                )
            }.getOrNull() ?: return@launch fail(RouteError.FAILED)
            _state.update { it.copy(busy = false, route = route, estimate = estimate) }
        }
    }

    private fun fail(e: RouteError) {
        _state.update { it.copy(busy = false, error = e, estimate = null) }
    }

    private fun latLon(lat: String, lon: String): LatLon? {
        val la = parseDecimal(lat) ?: return null
        val lo = parseDecimal(lon) ?: return null
        if (la !in -90.0..90.0 || lo !in -180.0..180.0) return null
        return LatLon(la, lo)
    }

    companion object {
        /** Typical UAE sedan when no history exists yet. */
        const val DEFAULT_PER_100KM = 8.0
    }
}
