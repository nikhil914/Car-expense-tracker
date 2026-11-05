package ae.sayyara.core.refuel

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ChargeDetectorTest {
    private var t = Instant.fromEpochSeconds(1_780_000_000)
    private val samples = ArrayList<VehicleSample>()

    private fun run(soc: (Int) -> Double, count: Int, every: Duration, speed: Double = 0.0, odo: Double = 5000.0) {
        repeat(count) {
            t += every
            samples += VehicleSample(t, evBatteryPercent = soc(it), speedKmh = speed, odometerKm = odo)
        }
    }

    @Test
    fun overnightChargeAfterGap() {
        run({ 42.0 - it * 0.1 }, 20, 30.seconds, speed = 60.0)
        run({ 40.0 }, 10, 10.seconds)
        t += 8.hours
        run({ 80.0 }, 10, 10.seconds)
        val d = ChargeDetector(ChargeConfig(batteryCapacityKwh = 75.0))
        val det = samples.mapNotNull { d.onSample(it) }
        assertEquals(1, det.size)
        val c = det.single()
        assertEquals("gap", c.trigger)
        assertTrue(abs(c.kwhAddedToBattery!! - 30.0) < 0.5)
        assertTrue(c.confidence > 0.7)
        assertEquals("sayyara://add-fill?kwh=30&soc_before=40&soc_after=80&odo=5000&source=car", c.toDraftDeepLink())
    }

    @Test
    fun continuousChargeEmitsOnceWhenSocStops() {
        run({ 30.0 }, 10, 1.minutes)
        run({ 30.0 + it * 0.5 }, 100, 1.minutes) // 30 % per hour DC-ish charging, sampled every minute
        run({ 79.5 }, 10, 1.minutes)
        val d = ChargeDetector()
        val det = samples.mapNotNull { d.onSample(it) }
        assertEquals(1, det.size, "detections: $det")
        assertTrue(det.single().socAfterPercent > 79.0)
        assertEquals(null, det.single().kwhAddedToBattery)
    }

    @Test
    fun drivingDrainNeverCounts() {
        run({ 90.0 - it * 0.05 }, 600, 5.seconds, speed = 80.0)
        run({ 60.0 }, 20, 5.seconds)
        val d = ChargeDetector(ChargeConfig(batteryCapacityKwh = 60.0))
        assertTrue(samples.mapNotNull { d.onSample(it) }.isEmpty())
        assertNotNull(d)
    }
}
