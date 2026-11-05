package ae.sayyara.core.route

import ae.sayyara.core.model.Emirate
import ae.sayyara.core.model.FuelGrade
import ae.sayyara.core.model.FuelPrice
import ae.sayyara.core.model.LatLon
import ae.sayyara.core.model.YearMonth
import ae.sayyara.core.uae.FuelPriceBook
import ae.sayyara.core.uae.QuoteStatus
import ae.sayyara.core.uae.TollTariffs
import ae.sayyara.core.uae.UaeFuelPrices
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class RouteCostEstimatorTest {
    private val now = Instant.parse("2026-11-05T10:00:00Z")
    private val book = FuelPriceBook(UaeFuelPrices.seed, now = { now })
    private val estimator = RouteCostEstimator(book)
    private fun at(local: String) = Instant.parse("$local+04:00")

    /** Desert road far from any gate. */
    private val noTollRoute = RouteResult(100.0, 60.0, listOf(LatLon(23.80, 55.00), LatLon(23.80, 55.98)))

    private val garhoud = TollTariffs.salikGates.first { it.id == "salik-al-garhoud" }
    private val garhoudRoute = RouteResult(
        distanceKm = 1.5, durationMin = 1.5,
        polyline = listOf(LatLon(garhoud.lat - 0.005, garhoud.lon - 0.005), LatLon(garhoud.lat + 0.005, garhoud.lon + 0.005)),
    )

    @Test
    fun oneWayFinalPrice() {
        val e = estimator.estimate(RouteCostInput(noTollRoute, at("2026-11-02T12:00:00"), 8.0, FuelGrade.SPECIAL_95))
        assertEquals(8.0, e.litres)
        assertEquals(QuoteStatus.FINAL, e.priceQuote!!.status)
        assertEquals(32.32, e.fuelAed) // 8 L x 4.04
        assertEquals(0.0, e.tollAed)
        assertEquals(32.32, e.totalAed)
        assertEquals(30.70, e.lowAed) // 8 x 0.95 x 4.04
        assertEquals(33.94, e.highAed) // 8 x 1.05 x 4.04
    }

    @Test
    fun roundTripWithParkingAndPassengers() {
        val e = estimator.estimate(
            RouteCostInput(
                noTollRoute, at("2026-11-02T09:00:00"), 8.0, FuelGrade.SPECIAL_95, roundTrip = true,
                parkingEmirate = Emirate.DUBAI, parkingZone = "365B", parkingHours = 2.0, passengers = 2,
            ),
        )
        assertEquals(16.0, e.litres)
        assertEquals(64.64, e.fuelAed)
        assertEquals(6.0, e.parkingAed) // arrive 10:00 Mon, zone B off-peak 3 + 3
        assertEquals(70.64, e.totalAed)
        assertEquals(35.32, e.perPersonAed)
    }

    @Test
    fun roundTripTollsUseReversedPolylineAndReturnTime() {
        // Out at 08:00 Mon (peak 6.30), park 3 h, return ~11:01 (off-peak 4.20).
        val e = estimator.estimate(
            RouteCostInput(garhoudRoute, at("2026-11-02T08:00:00"), 10.0, FuelGrade.SUPER_98, roundTrip = true, parkingHours = 3.0),
        )
        assertEquals(listOf(6.30, 4.20), e.tolls.map { it.aed })
        assertEquals(10.50, e.tollAed)
        assertEquals(0.3, e.litres)
        assertEquals(1.25, e.fuelAed) // 0.3 x 4.16 = 1.248
        assertEquals(11.75, e.totalAed)
    }

    @Test
    fun provisionalAddsLargestMonthlyMoveToBand() {
        val e = estimator.estimate(RouteCostInput(noTollRoute, at("2026-12-10T12:00:00"), 8.0, FuelGrade.SPECIAL_95))
        assertEquals(QuoteStatus.PROVISIONAL, e.priceQuote!!.status)
        assertEquals(32.32, e.totalAed)
        assertEquals(24.62, e.lowAed) // 8 x 0.95 x (4.04 - 0.80) = 24.624
        assertEquals(40.66, e.highAed) // 8 x 1.05 x (4.04 + 0.80) = 40.656
        val f = estimator.estimate(RouteCostInput(noTollRoute, at("2027-03-10T12:00:00"), 8.0, FuelGrade.DIESEL))
        assertEquals(QuoteStatus.FORECAST, f.priceQuote!!.status)
        assertEquals(38.0, f.totalAed) // 8 x 4.75
        assertTrue(f.highAed - f.totalAed > 8 * 1.9) // diesel move 1.97
    }

    @Test
    fun repriceWhenNewMonthAnnounced() {
        val saved = estimator.estimate(RouteCostInput(noTollRoute, at("2026-12-10T12:00:00"), 8.0, FuelGrade.SPECIAL_95, passengers = 2))
        val newBook = book.plus(listOf(FuelPrice(YearMonth(2026, 12), FuelGrade.SPECIAL_95, 3.90)))
        val newEstimator = RouteCostEstimator(newBook)
        assertTrue(newEstimator.needsReprice(saved))
        val re = newEstimator.repriceForMonth(saved)
        assertEquals(QuoteStatus.FINAL, re.priceQuote!!.status)
        assertEquals(31.20, re.totalAed)
        assertEquals(15.60, re.perPersonAed)
        assertEquals(re.totalAed, re.lowAed + 31.20 * 0.05, 0.01)
        assertTrue(!newEstimator.needsReprice(re))
    }

    @Test
    fun noPriceForElectricGivesZeroFuel() {
        val e = estimator.estimate(RouteCostInput(noTollRoute, at("2026-11-02T12:00:00"), 15.0, FuelGrade.ELECTRIC))
        assertEquals(null, e.priceQuote)
        assertEquals(0.0, e.fuelAed)
    }
}
