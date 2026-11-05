package ae.sayyara.core.refuel

import kotlin.math.round
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Signal shape for a simulated source. */
enum class Source { ANDROID_AUTO, AAOS, OBD }

/**
 * Deterministic simulated vehicle trace. [level] is the true fuel level in percent; samples add sender noise
 * (stationary) or slosh (moving) and the source's quantization.
 */
class TraceSim(
    private val source: Source,
    private val capacityL: Double,
    startLevel: Double,
    seed: Long = 42,
    var stationaryNoise: Double = 1.0,
    var slosh: Double = 8.0,
) {
    val samples = ArrayList<VehicleSample>()
    var t: Instant = Instant.fromEpochSeconds(1_780_000_000)
    var odo = 12_000.0
    var level = startLevel
    var ignition: Boolean? = if (source == Source.AAOS) true else null
    private var state = seed

    fun rnd(): Double {
        state = (state * 6364136223846793005L + 1442695040888963407L)
        return ((state ushr 11).toDouble() / (1L shl 53).toDouble()) * 2.0 - 1.0
    }

    private fun sample(noise: Double, speed: Double, parked: Boolean, door: Boolean? = null): VehicleSample {
        val reading = (level + noise * rnd()).coerceIn(0.0, 100.0)
        return when (source) {
            Source.ANDROID_AUTO -> VehicleSample(t, fuelLevelPercent = round(reading), odometerKm = round(odo * 10) / 10, speedKmh = speed)
            Source.OBD -> {
                val byte = round(reading * 255.0 / 100.0)
                VehicleSample(t, fuelLevelPercent = byte * 100.0 / 255.0, odometerKm = round(odo * 10) / 10, speedKmh = speed)
            }
            Source.AAOS -> VehicleSample(
                t, fuelLevelLitres = round(reading / 100.0 * capacityL * 10) / 10, odometerKm = odo,
                speedKmh = speed, parked = parked, ignitionOn = ignition, fuelDoorOpen = door,
            )
        }
    }

    fun drive(duration: Duration, speedKmh: Double = 70.0, every: Duration = 5.seconds, litresPer100: Double = 9.0) {
        var e = Duration.ZERO
        while (e < duration) {
            t += every; e += every
            val km = speedKmh * every.inWholeMilliseconds / 3_600_000.0
            odo += km
            level -= km * litresPer100 / 100.0 / capacityL * 100.0
            samples += sample(slosh, speedKmh + 10 * rnd(), parked = false)
        }
    }

    fun idle(duration: Duration, every: Duration = 5.seconds, parked: Boolean = true) {
        var e = Duration.ZERO
        while (e < duration) {
            t += every; e += every
            samples += sample(stationaryNoise, 0.0, parked)
        }
    }

    /** Pump fuel to [to] percent over [duration] while sampling (door state as given; null for no door signal). */
    fun pump(to: Double, duration: Duration = 2.minutes, every: Duration = 5.seconds, door: Boolean? = null) {
        val steps = (duration / every).toInt()
        val from = level
        for (i in 1..steps) {
            t += every
            level = from + (to - from) * i / steps
            samples += sample(stationaryNoise, 0.0, parked = true, door = door)
        }
    }

    /** No samples (session disconnected / ignition off with no data); fuel may change meanwhile. */
    fun gap(duration: Duration, levelAfter: Double = level) {
        t += duration
        level = levelAfter
    }

    fun event(door: Boolean? = null, ignitionOn: Boolean? = null) {
        t += 1.seconds
        if (ignitionOn != null) ignition = ignitionOn
        samples += VehicleSample(t, fuelDoorOpen = door, ignitionOn = ignitionOn, parked = true, speedKmh = 0.0)
    }
}

fun runDetector(d: RefuelDetector, samples: List<VehicleSample>): List<DetectedRefuel> = samples.mapNotNull { d.onSample(it) }
