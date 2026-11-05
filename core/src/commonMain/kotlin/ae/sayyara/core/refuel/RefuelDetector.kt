package ae.sayyara.core.refuel

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.time.Instant

/**
 * One vehicle data point from any source. Fill in only what the source provides:
 * - Android Auto `CarInfo`: [fuelLevelPercent], [odometerKm], [speedKmh] (no door, no litres).
 * - AAOS `CarPropertyManager`: [fuelLevelLitres] (FUEL_LEVEL is mL, divide by 1000), [fuelDoorOpen],
 *   [ignitionOn] (see [CarSignals.ignitionOnFromAaos]), [parked], [speedKmh], [odometerKm], [evBatteryPercent].
 * - OBD-II: [fuelLevelPercent] (PID 0x2F), [speedKmh] (0x0D), [odometerKm] (0xA6); see `ae.sayyara.core.obd`.
 */
data class VehicleSample(
    val at: Instant,
    val fuelLevelPercent: Double? = null,
    val fuelLevelLitres: Double? = null,
    val odometerKm: Double? = null,
    val speedKmh: Double? = null,
    val fuelDoorOpen: Boolean? = null,
    val ignitionOn: Boolean? = null,
    val parked: Boolean? = null,
    val evBatteryPercent: Double? = null,
)

/**
 * A detected refuel. [trigger] is one of "fuel_door", "ignition", "gap", "level_rise".
 * [confidence] is 0..1: litres plus fuel door is best; percent only and noisy/moving readings are lower.
 */
data class DetectedRefuel(
    val at: Instant,
    val litresAdded: Double,
    val levelBeforePercent: Double,
    val levelAfterPercent: Double,
    val likelyFull: Boolean,
    val odometerKm: Double?,
    val confidence: Double,
    val trigger: String,
)

data class RefuelConfig(
    val tankCapacityL: Double,
    val minLitres: Double = 5.0,
    val minPercentJump: Double = 8.0,
    val fullThresholdPercent: Double = 97.0,
    val maxOdoMoveKm: Double = 2.0,
) {
    init {
        require(tankCapacityL > 0) { "tankCapacityL must be > 0" }
    }
}

/**
 * Signal-agnostic refuel detector. Feed every sample in time order; it returns a [DetectedRefuel] at most once
 * per refuel. Samples taken while moving are ignored for levels (slosh); the baseline is the median of the last
 * stationary samples. Triggers: fuel door open->close, ignition off->on, data resuming after a gap, or a
 * sustained rise while stationary. Keep one instance per vehicle and persist nothing else: call [reset] when
 * switching vehicles.
 */
class RefuelDetector(config: RefuelConfig, tuning: DetectorTuning = DetectorTuning()) {
    private val cfg = config
    private val engine = LevelRiseEngine(
        capacity = config.tankCapacityL,
        minAbs = config.minLitres,
        minPct = config.minPercentJump,
        maxOdoMoveKm = config.maxOdoMoveKm,
        tuning = tuning,
    ) { s ->
        val cap = config.tankCapacityL
        val litres = s.fuelLevelLitres
        val pct = s.fuelLevelPercent ?: litres?.let { it / cap * 100.0 }
        if (pct == null || pct.isNaN() || pct < 0.0 || pct > 110.0) null
        else LevelPoint(pct.coerceAtMost(100.0), litres, s.odometerKm, s.at)
    }

    fun onSample(s: VehicleSample): DetectedRefuel? {
        val rise = engine.onSample(s) ?: return null
        val litresMeasured = rise.before.abs != null && rise.after.abs != null
        val door = rise.trigger == Triggers.FUEL_DOOR
        return DetectedRefuel(
            at = rise.after.at,
            litresAdded = rise.addedAbs ?: 0.0,
            levelBeforePercent = rise.before.pct,
            levelAfterPercent = rise.after.pct,
            likelyFull = rise.after.pct >= cfg.fullThresholdPercent,
            odometerKm = rise.after.odo ?: rise.before.odo,
            confidence = confidence(rise, litresMeasured, door),
            trigger = rise.trigger,
        )
    }

    fun reset() = engine.reset()
}

internal fun confidence(rise: Rise, absMeasured: Boolean, door: Boolean): Double {
    var c = when {
        absMeasured && door -> 0.95
        absMeasured -> 0.85
        door -> 0.85
        else -> 0.75
    }
    c -= when (rise.trigger) {
        Triggers.FUEL_DOOR -> 0.0
        Triggers.IGNITION -> 0.03
        Triggers.GAP -> 0.05
        else -> 0.08
    }
    val noise = max(rise.before.noise, rise.after.noise)
    val risePct = max(1e-6, rise.after.pct - rise.before.pct)
    c -= min(0.3, 0.02 * noise + 0.5 * noise / risePct)
    if (rise.before.fromMoving || rise.after.fromMoving) c -= 0.15
    if (rise.before.odo == null || rise.after.odo == null) c -= 0.05
    return c.coerceIn(0.05, 0.99)
}

/** Draft deep link for the quick add-fill sheet, e.g. `sayyara://add-fill?litres=32.5&level_before=18&level_after=98&odo=123456&source=car`. */
fun DetectedRefuel.toDraftDeepLink(): String = buildString {
    append("sayyara://add-fill?litres=").append(fmt1(litresAdded))
    append("&level_before=").append(levelBeforePercent.roundToLong())
    append("&level_after=").append(levelAfterPercent.roundToLong())
    odometerKm?.let { append("&odo=").append(it.roundToLong()) }
    append("&source=car")
}

/** One decimal, no trailing ".0", no exponent notation. */
internal fun fmt1(x: Double): String {
    val t = (x * 10).roundToLong()
    val sign = if (t < 0) "-" else ""
    val a = if (t < 0) -t else t
    return if (a % 10 == 0L) "$sign${a / 10}" else "$sign${a / 10}.${a % 10}"
}

/** Small helpers for mapping car platform values into [VehicleSample]. */
object CarSignals {
    /** AAOS VehicleIgnitionState: UNDEFINED 0, LOCK 1, OFF 2, ACC 3, ON 4, START 5. ON and START count as on. */
    fun ignitionOnFromAaos(state: Int): Boolean? = when (state) {
        1, 2, 3 -> false
        4, 5 -> true
        else -> null
    }

    /** AAOS FUEL_LEVEL is reported in millilitres. */
    fun litresFromMillilitres(ml: Float): Double = ml / 1000.0
}
