package ae.sayyara.core.refuel

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class RefuelDetectorTest {

    private fun assertNear(expected: Double, actual: Double, tol: Double, msg: String = "") =
        assertTrue(abs(expected - actual) <= tol, "$msg expected $expected ± $tol but was $actual")

    @Test
    fun androidAutoPercentOnlyGapTrigger() {
        val sim = TraceSim(Source.ANDROID_AUTO, capacityL = 60.0, startLevel = 40.0)
        sim.drive(20.minutes)
        sim.idle(1.minutes)
        sim.gap(8.minutes, levelAfter = 99.0) // AA session ends at the pump, reconnects after filling
        sim.idle(1.minutes)
        sim.drive(10.minutes)
        val det = runDetector(RefuelDetector(RefuelConfig(60.0)), sim.samples)
        assertEquals(1, det.size, "detections: $det")
        val r = det.single()
        assertEquals("gap", r.trigger)
        val before = 40.0 - 20 * 70 / 60.0 * 9 / 100 / 60 * 100 // ≈ 36.5 %
        assertNear(before, r.levelBeforePercent, 2.0, "before")
        assertNear(99.0, r.levelAfterPercent, 1.5, "after")
        assertNear((99.0 - before) / 100 * 60, r.litresAdded, 2.0, "litres")
        assertTrue(r.likelyFull)
        assertTrue(r.confidence in 0.5..0.8, "percent-only confidence ${r.confidence}")
        assertNotNull(r.odometerKm)
    }

    @Test
    fun androidAutoReconnectWithoutStationaryBaselineUsesMovingFallback() {
        val sim = TraceSim(Source.ANDROID_AUTO, capacityL = 60.0, startLevel = 30.0)
        sim.drive(15.minutes)
        sim.gap(10.minutes, levelAfter = 80.0) // drove straight in, phone unplugged, no stationary samples
        sim.idle(1.minutes)
        val det = runDetector(RefuelDetector(RefuelConfig(60.0)), sim.samples)
        assertEquals(1, det.size)
        val r = det.single()
        assertNear(80.0 - 28.0, r.levelAfterPercent - r.levelBeforePercent, 5.0, "rise")
        assertFalse(r.likelyFull)
        val stationaryCase = runDetector(RefuelDetector(RefuelConfig(60.0)), TraceSim(Source.ANDROID_AUTO, 60.0, 30.0).apply {
            drive(15.minutes); idle(1.minutes); gap(10.minutes, 80.0); idle(1.minutes)
        }.samples).single()
        assertTrue(r.confidence < stationaryCase.confidence, "moving fallback must lower confidence")
    }

    @Test
    fun aaosLitresAndFuelDoorIsBest() {
        val sim = TraceSim(Source.AAOS, capacityL = 70.0, startLevel = 25.0, stationaryNoise = 0.6)
        sim.drive(10.minutes)
        sim.idle(1.minutes)
        sim.event(door = true)
        sim.pump(to = 99.0, door = true)
        sim.event(door = false)
        sim.idle(1.minutes)
        sim.drive(5.minutes)
        val det = runDetector(RefuelDetector(RefuelConfig(70.0)), sim.samples)
        assertEquals(1, det.size, "detections: $det")
        val r = det.single()
        assertEquals("fuel_door", r.trigger)
        val beforePct = 25.0 - 10 * 70 / 60.0 * 9 / 100 / 70 * 100
        assertNear((99.0 - beforePct) / 100 * 70, r.litresAdded, 1.0, "litres")
        assertTrue(r.likelyFull)
        assertTrue(r.confidence >= 0.9, "confidence ${r.confidence}")
    }

    @Test
    fun aaosIgnitionCycleTrigger() {
        val sim = TraceSim(Source.AAOS, capacityL = 50.0, startLevel = 20.0, stationaryNoise = 0.5)
        sim.drive(5.minutes)
        sim.idle(30.seconds)
        sim.event(ignitionOn = false)
        sim.gap(90.seconds, levelAfter = 70.0) // shorter than the gap trigger: ignition must do it
        sim.event(ignitionOn = true)
        sim.idle(1.minutes)
        val det = runDetector(RefuelDetector(RefuelConfig(50.0)), sim.samples)
        assertEquals(1, det.size)
        assertEquals("ignition", det.single().trigger)
        assertFalse(det.single().likelyFull)
        assertNear(25.0, det.single().litresAdded, 1.5)
    }

    @Test
    fun obdSustainedRiseWhileStationary() {
        val sim = TraceSim(Source.OBD, capacityL = 65.0, startLevel = 15.0, stationaryNoise = 1.2)
        sim.drive(10.minutes)
        sim.idle(1.minutes)
        sim.pump(to = 98.0, duration = 3.minutes) // engine left on, OBD keeps polling
        sim.idle(2.minutes)
        sim.drive(10.minutes)
        val det = runDetector(RefuelDetector(RefuelConfig(65.0)), sim.samples)
        assertEquals(1, det.size, "detections: $det")
        val r = det.single()
        assertEquals("level_rise", r.trigger)
        assertTrue(r.levelAfterPercent > 96.0, "should wait until the level settles, got ${r.levelAfterPercent}")
        assertTrue(r.likelyFull)
        assertTrue(r.confidence in 0.4..0.75, "confidence ${r.confidence}")
    }

    @Test
    fun noFalsePositiveWhileDrivingWithHeavySlosh() {
        for (source in Source.entries) {
            val sim = TraceSim(source, capacityL = 60.0, startLevel = 80.0, seed = 7, slosh = 15.0)
            repeat(12) {
                sim.drive(8.minutes)
                sim.idle(40.seconds, parked = false) // red light
            }
            val det = runDetector(RefuelDetector(RefuelConfig(60.0)), sim.samples)
            assertTrue(det.isEmpty(), "$source false positives: $det")
        }
    }

    @Test
    fun noFalsePositiveOnIgnitionCyclesAndGapsWithoutFuel() {
        val sim = TraceSim(Source.AAOS, capacityL = 60.0, startLevel = 50.0)
        repeat(5) {
            sim.drive(10.minutes)
            sim.idle(1.minutes)
            sim.event(ignitionOn = false)
            sim.gap(3.hours)
            sim.event(ignitionOn = true)
            sim.idle(1.minutes)
        }
        assertTrue(runDetector(RefuelDetector(RefuelConfig(60.0)), sim.samples).isEmpty())
    }

    @Test
    fun debounceNeverEmitsTwiceForOneRefuel() {
        val sim = TraceSim(Source.ANDROID_AUTO, capacityL = 60.0, startLevel = 20.0)
        sim.idle(1.minutes)
        sim.gap(6.minutes, levelAfter = 85.0)
        sim.idle(1.minutes)
        // Gauge lag: cluster creeps up to 97 % a few minutes later, plus a reconnect.
        sim.gap(3.minutes, levelAfter = 97.0)
        sim.idle(2.minutes)
        sim.gap(3.minutes)
        sim.idle(2.minutes)
        repeat(30) { sim.idle(10.seconds) }
        val det = runDetector(RefuelDetector(RefuelConfig(60.0)), sim.samples)
        assertEquals(1, det.size, "detections: $det")
    }

    @Test
    fun secondRealRefuelAfterDrivingIsDetected() {
        val sim = TraceSim(Source.AAOS, capacityL = 60.0, startLevel = 20.0, stationaryNoise = 0.4)
        sim.idle(1.minutes)
        sim.event(door = true); sim.pump(90.0, door = true); sim.event(door = false); sim.idle(1.minutes)
        sim.drive(3.hours, speedKmh = 110.0) // ≈ 330 km, burns ≈ 30 L
        sim.idle(1.minutes)
        sim.event(door = true); sim.pump(95.0, door = true); sim.event(door = false); sim.idle(1.minutes)
        val det = runDetector(RefuelDetector(RefuelConfig(60.0)), sim.samples)
        assertEquals(2, det.size, "detections: $det")
    }

    @Test
    fun partialRefuel() {
        val sim = TraceSim(Source.ANDROID_AUTO, capacityL = 60.0, startLevel = 30.0, stationaryNoise = 0.8)
        sim.idle(2.minutes)
        sim.gap(5.minutes, levelAfter = 55.0) // AED 50-ish top-up, ≈ 15 L
        sim.idle(2.minutes)
        val r = runDetector(RefuelDetector(RefuelConfig(60.0)), sim.samples).single()
        assertFalse(r.likelyFull)
        assertNear(15.0, r.litresAdded, 1.5)
    }

    @Test
    fun tinyTopUpBelowThresholdsIgnored() {
        val sim = TraceSim(Source.ANDROID_AUTO, capacityL = 60.0, startLevel = 30.0, stationaryNoise = 0.5)
        sim.idle(2.minutes)
        sim.gap(5.minutes, levelAfter = 35.0) // 3 L, 5 %
        sim.idle(2.minutes)
        assertTrue(runDetector(RefuelDetector(RefuelConfig(60.0)), sim.samples).isEmpty())
    }

    @Test
    fun odometerMovedTooFarIsRejected() {
        val sim = TraceSim(Source.ANDROID_AUTO, capacityL = 60.0, startLevel = 30.0)
        sim.idle(2.minutes)
        sim.gap(30.minutes, levelAfter = 90.0)
        sim.odo += 25.0 // drove without the phone connected; can't confirm where/when
        sim.idle(2.minutes)
        assertTrue(runDetector(RefuelDetector(RefuelConfig(60.0)), sim.samples).isEmpty())
    }

    @Test
    fun resetClearsState() {
        val d = RefuelDetector(RefuelConfig(60.0))
        val sim = TraceSim(Source.ANDROID_AUTO, 60.0, 30.0).apply { idle(2.minutes); gap(5.minutes, 90.0); idle(2.minutes) }
        assertEquals(1, runDetector(d, sim.samples).size)
        d.reset()
        assertEquals(1, runDetector(d, sim.samples).size)
    }

    @Test
    fun deepLinkFormat() {
        val r = DetectedRefuel(
            at = Instant.fromEpochSeconds(0), litresAdded = 32.46, levelBeforePercent = 18.2, levelAfterPercent = 97.8,
            likelyFull = true, odometerKm = 123456.4, confidence = 0.9, trigger = "fuel_door",
        )
        assertEquals("sayyara://add-fill?litres=32.5&level_before=18&level_after=98&odo=123456&source=car", r.toDraftDeepLink())
        assertEquals("sayyara://add-fill?litres=40&level_before=10&level_after=77&source=car",
            r.copy(litresAdded = 40.0, levelBeforePercent = 10.0, levelAfterPercent = 77.0, odometerKm = null).toDraftDeepLink())
    }

    @Test
    fun aaosIgnitionMapping() {
        assertEquals(true, CarSignals.ignitionOnFromAaos(4))
        assertEquals(false, CarSignals.ignitionOnFromAaos(2))
        assertEquals(null, CarSignals.ignitionOnFromAaos(0))
    }
}
