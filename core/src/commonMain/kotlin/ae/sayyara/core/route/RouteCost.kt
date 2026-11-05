package ae.sayyara.core.route

import ae.sayyara.core.model.Emirate
import ae.sayyara.core.model.FuelGrade
import ae.sayyara.core.model.LatLon
import ae.sayyara.core.model.YearMonth
import ae.sayyara.core.uae.FuelPriceBook
import ae.sayyara.core.uae.Geo
import ae.sayyara.core.uae.Parking
import ae.sayyara.core.uae.PriceQuote
import ae.sayyara.core.uae.QuoteStatus
import ae.sayyara.core.uae.TollCharge
import ae.sayyara.core.uae.TollEngine
import kotlin.math.max
import kotlin.math.round
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Any routing backend (Google Routes, Mapbox, HERE, OSRM/Valhalla). */
interface RouteProvider {
    suspend fun route(from: LatLon, to: LatLon): RouteResult?
}

data class RouteResult(val distanceKm: Double, val durationMin: Double, val polyline: List<LatLon>)

data class RouteCostInput(
    val route: RouteResult,
    val departure: Instant,
    val per100km: Double,
    val grade: FuelGrade,
    val roundTrip: Boolean = false,
    val parkingEmirate: Emirate? = null,
    val parkingZone: String? = null,
    val parkingHours: Double = 0.0,
    val passengers: Int = 1,
)

data class RouteCostEstimate(
    val litres: Double,
    val fuelAed: Double,
    val tolls: List<TollCharge>,
    val tollAed: Double,
    val parkingAed: Double,
    val totalAed: Double,
    val perPersonAed: Double,
    val priceQuote: PriceQuote?,
    val lowAed: Double,
    val highAed: Double,
)

/**
 * Trip cost = fuel (km x L/100km / 100 x price for the departure month) + Salik/Darb tolls on the
 * polyline + parking. Round trips double the fuel, and price the return leg's tolls on the reversed
 * polyline, departing after arrival + parking time.
 *
 * Band: consumption +/-[consumptionBand]; for PROVISIONAL/FORECAST quotes the price also moves by the
 * largest month-to-month change in the book's history for that grade.
 */
class RouteCostEstimator(
    private val prices: FuelPriceBook,
    private val tolls: TollEngine = TollEngine(),
    private val consumptionBand: Double = 0.05,
    private val zone: TimeZone = TimeZone.of("Asia/Dubai"),
) {
    fun estimate(input: RouteCostInput): RouteCostEstimate {
        require(input.passengers >= 1) { "passengers must be >= 1" }
        require(input.per100km >= 0) { "per100km must be >= 0" }
        val r = input.route
        val legs = if (input.roundTrip) 2 else 1
        val litres = r.distanceKm * input.per100km / 100.0 * legs

        val month = YearMonth.of(input.departure.toLocalDateTime(zone).date)
        val quote = prices.quote(month, input.grade)

        val polyKm = Geo.lengthKm(r.polyline)
        val speed = if (r.durationMin > 0 && polyKm > 0) polyKm / (r.durationMin / 60.0) else 60.0
        val outbound = tolls.charges(r.polyline, input.departure, speed)
        val arrival = input.departure + r.durationMin.minutes

        val parking = if (input.parkingEmirate != null && input.parkingZone != null && input.parkingHours > 0) {
            Parking.cost(input.parkingEmirate, input.parkingZone, arrival, input.parkingHours) ?: 0.0
        } else 0.0

        val inbound = if (input.roundTrip) {
            tolls.charges(r.polyline.asReversed(), arrival + input.parkingHours.hours, speed)
        } else emptyList()

        val allTolls = outbound + inbound
        return build(litres, quote, allTolls, parking, input.passengers)
    }

    /**
     * Re-prices a saved estimate (e.g. after a new month is announced) with this estimator's book,
     * keeping litres, tolls and parking. [month]/[grade] default to those of the saved quote; pass them
     * when the saved estimate had no quote. Passengers are recovered from total/perPerson.
     */
    fun repriceForMonth(
        saved: RouteCostEstimate,
        month: YearMonth? = saved.priceQuote?.month,
        grade: FuelGrade? = saved.priceQuote?.grade,
    ): RouteCostEstimate {
        val quote = if (month != null && grade != null) prices.quote(month, grade) else null
        val passengers = if (saved.perPersonAed > 0) max(1, round(saved.totalAed / saved.perPersonAed).toInt()) else 1
        return build(saved.litres, quote, saved.tolls, saved.parkingAed, passengers)
    }

    /** True when the book now gives a different status or price than the saved quote. */
    fun needsReprice(saved: RouteCostEstimate): Boolean {
        val q = saved.priceQuote ?: return false
        val now = prices.quote(q.month, q.grade) ?: return false
        return now.status != q.status || now.aedPerLitre != q.aedPerLitre
    }

    private fun build(litres: Double, quote: PriceQuote?, tolls: List<TollCharge>, parking: Double, passengers: Int): RouteCostEstimate {
        val price = quote?.aedPerLitre ?: 0.0
        val fuel = litres * price
        val tollAed = tolls.sumOf { it.aed }
        val fixed = tollAed + parking
        val total = fuel + fixed
        val move = if (quote != null && quote.status != QuoteStatus.FINAL) prices.maxMonthlyMove(quote.grade) else 0.0
        val low = litres * (1 - consumptionBand) * max(0.0, price - move) + fixed
        val high = litres * (1 + consumptionBand) * (price + if (quote != null) move else 0.0) + fixed
        return RouteCostEstimate(
            litres = litres.r2(),
            fuelAed = fuel.r2(),
            tolls = tolls,
            tollAed = tollAed.r2(),
            parkingAed = parking.r2(),
            totalAed = total.r2(),
            perPersonAed = (total / passengers).r2(),
            priceQuote = quote,
            lowAed = low.r2(),
            highAed = high.r2(),
        )
    }

    private fun Double.r2() = round(this * 100) / 100.0
}
