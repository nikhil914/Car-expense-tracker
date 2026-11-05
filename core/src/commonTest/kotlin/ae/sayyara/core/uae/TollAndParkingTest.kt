package ae.sayyara.core.uae

import ae.sayyara.core.model.Emirate
import ae.sayyara.core.model.LatLon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class TollEngineTest {
    private val engine = TollEngine()
    private val garhoud = TollTariffs.salikGates.first { it.id == "salik-al-garhoud" }

    /** Synthetic ~1.5 km segment SW -> NE straight through the Al Garhoud gate point, densely sampled. */
    private val overGarhoud: List<LatLon> = (0..20).map { i ->
        val f = -0.005 + i * 0.0005
        LatLon(garhoud.lat + f, garhoud.lon + f)
    }

    private fun at(local: String) = Instant.parse("$local+04:00")

    @Test
    fun detectsGarhoudCrossingOnceRegardlessOfDirection() {
        val fwd = engine.crossings(overGarhoud)
        assertEquals(listOf("salik-al-garhoud"), fwd.map { it.gate.id })
        val back = engine.crossings(overGarhoud.reversed())
        assertEquals(listOf("salik-al-garhoud"), back.map { it.gate.id })
        assertTrue(fwd.single().distanceKm in 0.6..0.9)
    }

    @Test
    fun parallelRoad200mAwayIsNotACrossing() {
        val shifted = overGarhoud.map { LatLon(it.lat + 0.0013, it.lon - 0.0013) } // ~190 m perpendicular
        assertTrue(engine.crossings(shifted).isEmpty())
    }

    @Test
    fun sparsePolylineSegmentStillDetected() {
        val two = listOf(overGarhoud.first(), overGarhoud.last())
        assertEquals(1, engine.crossings(two).size)
    }

    @Test
    fun salikPeakOffPeakSundayNightVatInclusive() {
        fun aed(local: String) = engine.charges(overGarhoud, at(local)).single().aed
        assertEquals(6.30, aed("2026-11-02T08:00:00")) // Monday peak
        assertEquals(6.30, aed("2026-11-07T17:30:00")) // Saturday evening peak
        assertEquals(4.20, aed("2026-11-02T12:00:00")) // Monday off-peak
        assertEquals(4.20, aed("2026-11-02T22:00:00")) // Monday late evening off-peak
        assertEquals(4.20, aed("2026-11-08T08:00:00")) // Sunday flat
        assertEquals(0.0, aed("2026-11-02T03:00:00")) // free 01-06
        assertEquals(0.0, aed("2026-11-08T01:30:00")) // free 01-06 on Sunday
    }

    @Test
    fun salikPreVatFlag() {
        val noVat = TollEngine(TollTariffs.default2026(salikVatInclusive = false))
        assertEquals(6.0, noVat.charges(overGarhoud, at("2026-11-02T08:00:00")).single().aed)
        assertEquals(4.0, noVat.charges(overGarhoud, at("2026-11-02T12:00:00")).single().aed)
    }

    @Test
    fun crossingTimeEstimatedFromDistance() {
        // Departing 09:59:30 Monday; gate ~0.75 km in. At 60 km/h it is reached ~10:00:15 -> off-peak.
        val c = engine.charges(overGarhoud, at("2026-11-02T09:59:30"), avgSpeedKmh = 60.0).single()
        assertEquals(4.20, c.aed)
        assertTrue(c.at > at("2026-11-02T10:00:00"))
        // At 120 km/h it is reached at ~09:59:52 -> still peak.
        assertEquals(6.30, engine.charges(overGarhoud, at("2026-11-02T09:59:30"), avgSpeedKmh = 120.0).single().aed)
    }

    @Test
    fun alSafaNorthAndSouthSameDirectionWithinHourChargedOnce() {
        val n = TollTariffs.salikGates.first { it.id == "salik-al-safa-north" }
        val s = TollTariffs.salikGates.first { it.id == "salik-al-safa-south" }
        val szr = listOf(LatLon(n.lat + 0.003, n.lon + 0.003), LatLon(n.lat, n.lon), LatLon(s.lat, s.lon), LatLon(s.lat - 0.003, s.lon - 0.003))
        val charges = engine.charges(szr, at("2026-11-02T12:00:00"))
        assertEquals(listOf("salik-al-safa-north"), charges.map { it.gateId })
        // Out and back: opposite direction is charged again.
        val outAndBack = szr + szr.reversed().drop(1)
        assertEquals(2, engine.charges(outAndBack, at("2026-11-02T12:00:00")).size)
    }

    @Test
    fun darbPeakOnlyMonSat() {
        val g = TollTariffs.darbGates.first { it.id == "darb-sheikh-zayed-bridge" }
        val line = listOf(LatLon(g.lat - 0.004, g.lon), LatLon(g.lat + 0.004, g.lon))
        fun aed(local: String) = engine.charges(line, at(local)).single().also { assertEquals("Darb", it.operator) }.aed
        assertEquals(4.0, aed("2026-11-02T07:30:00"))
        assertEquals(4.0, aed("2026-11-02T18:00:00"))
        assertEquals(0.0, aed("2026-11-02T12:00:00"))
        assertEquals(0.0, aed("2026-11-08T08:00:00")) // Sunday
        assertEquals(0.0, aed("2026-11-02T21:00:00"))
    }

    @Test
    fun tariffsJsonRoundTrip() {
        val t = TollTariffs.default2026()
        assertEquals(t, TollTariffs.fromJson(t.toJson()))
        assertEquals(10, t.gates.count { it.operator == "Salik" })
        assertTrue(t.gates.all { it.coordinatesApproximate })
    }
}

class ParkingTest {
    private fun at(local: String) = Instant.parse("$local+04:00")

    @Test
    fun smsDrafts() {
        assertEquals(SmsDraft("7275", "A12345 365C 2"), Parking.smsDraft(Emirate.DUBAI, "A 12345", "365c", 2))
        assertEquals(SmsDraft("3009", "12345 STANDARD 1"), Parking.smsDraft(Emirate.ABU_DHABI, "12345", "standard", 1))
        assertEquals("5566", Parking.smsDraft(Emirate.SHARJAH, "3 12345", "Z1", 3).number)
        assertEquals("sms:7275?body=A12345%20365C%202", Parking.smsUri(Parking.smsDraft(Emirate.DUBAI, "A12345", "365C", 2)))
        assertFailsWith<IllegalArgumentException> { Parking.smsDraft(Emirate.DUBAI, "A1", "365C", 0) }
        assertFailsWith<IllegalArgumentException> { Parking.smsDraft(Emirate.FUJAIRAH, "A1", "1", 1) }
    }

    @Test
    fun dubaiRates() {
        assertEquals(6.0, Parking.hourlyRate(Emirate.DUBAI, "PREMIUM", at("2026-11-02T09:00:00")))
        assertEquals(4.0, Parking.hourlyRate(Emirate.DUBAI, "365B", at("2026-11-02T09:00:00")))
        assertEquals(3.0, Parking.hourlyRate(Emirate.DUBAI, "365B", at("2026-11-02T12:00:00")))
        assertEquals(2.0, Parking.hourlyRate(Emirate.DUBAI, "D", at("2026-11-02T12:00:00")))
        assertEquals(0.0, Parking.hourlyRate(Emirate.DUBAI, "365B", at("2026-11-08T12:00:00"))) // Sunday
        assertEquals(0.0, Parking.hourlyRate(Emirate.DUBAI, "365B", at("2026-11-02T23:00:00")))
        assertNull(Parking.hourlyRate(Emirate.DUBAI, "365Z", at("2026-11-02T12:00:00")))
    }

    @Test
    fun abuDhabiAndSharjah() {
        assertEquals(2.0, Parking.hourlyRate(Emirate.ABU_DHABI, "STANDARD", at("2026-11-02T12:00:00")))
        assertEquals(3.0, Parking.hourlyRate(Emirate.ABU_DHABI, "P", at("2026-11-02T12:00:00")))
        assertEquals(0.0, Parking.hourlyRate(Emirate.ABU_DHABI, "STANDARD", at("2026-11-08T12:00:00")))
        assertEquals(2.38, Parking.cost(Emirate.SHARJAH, "STANDARD", at("2026-11-02T12:00:00"), 1.0))
    }

    @Test
    fun dubaiCostAcrossPeakBoundary() {
        // 09:00-11:00 zone B: 1 h peak (4) + 1 h off-peak (3); 1.5 h billed as 2 h.
        assertEquals(7.0, Parking.cost(Emirate.DUBAI, "B", at("2026-11-02T09:00:00"), 1.5))
    }
}
