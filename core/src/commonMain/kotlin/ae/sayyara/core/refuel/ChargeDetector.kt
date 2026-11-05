package ae.sayyara.core.refuel

import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

data class ChargeConfig(
    /** Usable battery capacity in kWh; null means kWh cannot be estimated (percent only). */
    val batteryCapacityKwh: Double? = null,
    val minPercentJump: Double = 5.0,
    val minKwh: Double? = null,
    val fullThresholdPercent: Double = 98.0,
    val maxOdoMoveKm: Double = 2.0,
)

/** A detected charging session. [kwhAddedToBattery] is battery-side energy (Δ% × capacity), not wall kWh. */
data class DetectedCharge(
    val at: Instant,
    val kwhAddedToBattery: Double?,
    val socBeforePercent: Double,
    val socAfterPercent: Double,
    val likelyFull: Boolean,
    val odometerKm: Double?,
    val confidence: Double,
    val trigger: String,
)

/**
 * EV/PHEV charge detector on [VehicleSample.evBatteryPercent]. Same state machine as [RefuelDetector].
 * Battery SoC has no slosh, but charging is slow, so a "sustained rise" is only accepted once SoC stops rising
 * (tight settle tolerance) or when the session ends (gap, ignition on, driving away).
 */
class ChargeDetector(
    config: ChargeConfig = ChargeConfig(),
    tuning: DetectorTuning = DetectorTuning(stableSamples = 3, eventSamples = 2, settleTolerancePercent = 0.3, noiseBandPercent = 0.5, debounce = 30.minutes),
) {
    private val cfg = config
    private val engine = LevelRiseEngine(
        capacity = config.batteryCapacityKwh,
        minAbs = config.minKwh,
        minPct = config.minPercentJump,
        maxOdoMoveKm = config.maxOdoMoveKm,
        tuning = tuning,
    ) { s ->
        val pct = s.evBatteryPercent
        if (pct == null || pct.isNaN() || pct < 0.0 || pct > 100.5) null
        else LevelPoint(pct.coerceAtMost(100.0), null, s.odometerKm, s.at)
    }

    fun onSample(s: VehicleSample): DetectedCharge? {
        val rise = engine.onSample(s) ?: return null
        return DetectedCharge(
            at = rise.after.at,
            kwhAddedToBattery = rise.addedAbs,
            socBeforePercent = rise.before.pct,
            socAfterPercent = rise.after.pct,
            likelyFull = rise.after.pct >= cfg.fullThresholdPercent,
            odometerKm = rise.after.odo ?: rise.before.odo,
            // SoC comes from the BMS, which is far more precise than a fuel sender: treat it like a measured amount.
            confidence = confidence(rise, absMeasured = true, door = false),
            trigger = rise.trigger,
        )
    }

    fun reset() = engine.reset()
}

/** Draft deep link for a charge, e.g. `sayyara://add-fill?kwh=40.2&soc_before=20&soc_after=80&odo=1234&source=car`. */
fun DetectedCharge.toDraftDeepLink(): String = buildString {
    append("sayyara://add-fill?")
    kwhAddedToBattery?.let { append("kwh=").append(fmt1(it)).append('&') }
    append("soc_before=").append(socBeforePercent.roundToLong())
    append("&soc_after=").append(socAfterPercent.roundToLong())
    odometerKm?.let { append("&odo=").append(it.roundToLong()) }
    append("&source=car")
}
