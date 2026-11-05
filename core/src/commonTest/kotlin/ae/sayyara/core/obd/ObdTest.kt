package ae.sayyara.core.obd

import ae.sayyara.core.refuel.RefuelConfig
import ae.sayyara.core.refuel.RefuelDetector
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ObdTest {
    private fun near(a: Double, b: Double, tol: Double = 1e-6) = assertTrue(abs(a - b) <= tol, "expected $a got $b")

    @Test
    fun commands() {
        assertEquals("012F", Obd.command(0x2F))
        assertEquals("010D", Obd.command(0x0D))
        assertEquals("01A6", Obd.command(0xA6))
        assertEquals("012F1", Obd.command(0x2F, 1))
        assertEquals(listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0"), Obd.initCommands)
    }

    @Test
    fun decodesEachPid() {
        near(50.19607843, (Obd.parse("41 2F 80\r\r>") as ObdReading.FuelLevel).percent, 1e-6)
        near(100.0, (Obd.parse("412FFF") as ObdReading.FuelLevel).percent)
        near(88.0, (Obd.parse("41 0D 58") as ObdReading.Speed).kmh)
        near((256 * 0x01 + 0x2C) / 20.0, (Obd.parse("41 5E 01 2C") as ObdReading.FuelRate).litresPerHour)
        near(4.5, (Obd.parse("41 10 01 C2") as ObdReading.Maf).gramsPerSecond)
        near(123456.7, (Obd.parse("41 A6 00 12 D6 87") as ObdReading.Odometer).km)
    }

    @Test
    fun headersSpacesPromptAndSearching() {
        val expected = 0x40 * 100.0 / 255.0
        val variants = listOf(
            "SEARCHING...\r41 2F 40 \r\r>",
            "7E8 03 41 2F 40\r\r>",          // CAN 11-bit, headers on
            "7E803412F40\r>",                // CAN 11-bit, headers on, spaces off
            "18 DA F1 10 03 41 2F 40\r>",    // CAN 29-bit
            "48 6B 10 41 2F 40 A1\r>",       // J1850 / ISO 9141 with checksum
            "012F\r41 2F 40\r\r>",           // echo still on
            "BUS INIT: ...OK\r41 2F 40\r>",
            "\n\n  41 2f 40  \n>",           // lowercase, stray whitespace
        )
        for (v in variants) {
            val r = Obd.parse(v)
            assertIs<ObdReading.FuelLevel>(r, v)
            near(expected, r.percent)
        }
    }

    @Test
    fun noDataAndErrorsAreNull() {
        for (v in listOf("NO DATA\r\r>", "SEARCHING...\rUNABLE TO CONNECT\r>", "?\r>", "CAN ERROR\r>", ">", "", "STOPPED\r>",
            "41 5E 01\r>" /* truncated */, "41 99 01 02\r>" /* unknown pid */, "BUFFER FULL\r>", "ATZ\r\rELM327 v1.5\r\r>")) {
            assertNull(Obd.parse(v), v)
        }
    }

    @Test
    fun multiLineMultipleEcusFirstWins() {
        val r = Obd.parse("7E8 03 41 0D 32\r7E9 03 41 0D 33\r\r>")
        assertIs<ObdReading.Speed>(r)
        near(50.0, r.kmh)
        // first ECU says NO DATA style garbage, second has data
        val r2 = Obd.parse("7E8 03 7F 01 12\r7E9 03 41 0D 33\r>")
        assertIs<ObdReading.Speed>(r2)
        near(51.0, r2.kmh)
    }

    @Test
    fun isoTpMultiFrameAndSupportedPids() {
        val r = Obd.parse("00A\r0: 41 00 BE 3F A8\r1: 13 00 00 00 00 00 00\r\r>")
        assertIs<ObdReading.SupportedPids>(r)
        assertTrue(0x0D in r.supported)
        assertTrue(0x10 in r.supported)
        assertTrue(0x01 in r.supported)
        assertTrue(0x20 in r.supported) // last bit: next range supported
        val r2 = Obd.parse("41 20 00 02 00 01")
        assertIs<ObdReading.SupportedPids>(r2)
        assertEquals(setOf(0x2F, 0x40), r2.supported)
    }

    @Test
    fun mafConversion() {
        // 10 g/s petrol -> 10 / 14.7 / 745 * 3600 ≈ 3.287 L/h
        near(3.2872, Obd.mafToLitresPerHour(10.0), 1e-3)
        near(10.0 / 14.5 / 832 * 3600, Obd.mafToLitresPerHour(10.0, afr = 14.5, densityGPerL = 832.0))
    }

    @Test
    fun toSampleHelpers() {
        val at = Instant.fromEpochSeconds(1000)
        assertEquals(42.0, ObdReading.Speed(42.0).toSample(at).speedKmh)
        assertEquals(null, ObdReading.Maf(3.0).toSample(at).speedKmh)
        val s = listOf(ObdReading.Speed(0.0), ObdReading.FuelLevel(55.0), ObdReading.Odometer(1234.5), ObdReading.FuelRate(1.0)).toSample(at)
        assertEquals(0.0, s.speedKmh); assertEquals(55.0, s.fuelLevelPercent); assertEquals(1234.5, s.odometerKm); assertEquals(at, s.at)
    }

    @Test
    fun fuelIntegrator() {
        val i = FuelUsedIntegrator()
        val t0 = Instant.fromEpochSeconds(0)
        // 3.6 L/h for one hour, sampled every second -> 3.6 L
        for (k in 0..3600) i.add(t0 + k.seconds, ObdReading.FuelRate(3.6))
        near(3.6, i.litres, 1e-9)
        // gap is skipped, not extrapolated
        i.add(t0 + 3600.seconds + 5.minutes, 3.6)
        near(3.6, i.litres, 1e-9)
        // MAF ignored once fuel rate seen
        i.add(t0 + 3600.seconds + 5.minutes + 1.seconds, ObdReading.Maf(100.0))
        near(3.6, i.litres, 1e-9)

        val m = FuelUsedIntegrator()
        for (k in 0..60) m.add(t0 + k.seconds, ObdReading.Maf(10.0))
        near(Obd.mafToLitresPerHour(10.0) / 60.0, m.litres, 1e-9)
        m.reset(); near(0.0, m.litres)
    }

    @Test
    fun endToEndObdRefuel() {
        // Poll 2F + 0D + A6 every 5 s; fuel goes 20 % -> 95 % while parked with the engine on.
        val d = RefuelDetector(RefuelConfig(tankCapacityL = 55.0))
        var t = Instant.fromEpochSeconds(1_780_000_000)
        val levels = List(24) { 20.0 + (it % 3 - 1) * 0.8 } + List(30) { 20.0 + 75.0 * (it + 1) / 30 } + List(30) { 95.0 + (it % 3 - 1) * 0.8 }
        val found = levels.mapNotNull { lvl ->
            t += 5.seconds
            val a = (lvl * 255 / 100).roundToInt()
            val replies = listOf(
                "SEARCHING...\r7E8 03 41 2F ${a.toString(16).padStart(2, '0')}\r>",
                "7E8 03 41 0D 00\r>",
                "7E8 06 41 A6 00 01 E2 40\r>",
            )
            d.onSample(replies.mapNotNull { Obd.parse(it) }.toSample(t))
        }
        assertEquals(1, found.size)
        val r = found.single()
        near(12345.6, r.odometerKm!!, 1e-6)
        assertTrue(abs(r.litresAdded - 0.75 * 55) < 2.0, "litres ${r.litresAdded}")
    }
}
