package ae.sayyara.core.refuel

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Tuning knobs shared by [RefuelDetector] and [ChargeDetector]. Defaults follow the head-unit research note
 * (median of stationary samples, 2 min gap trigger, 30-120 s cluster re-sample after ignition on).
 */
data class DetectorTuning(
    /** Samples in the stationary median used for the baseline and the "sustained rise" check. */
    val stableSamples: Int = 5,
    /** Stationary samples needed after an event trigger (door close, ignition on, gap) to read the new level. */
    val eventSamples: Int = 3,
    /** At or below this speed a sample counts as stationary. */
    val stationarySpeedKmh: Double = 2.0,
    /** A data gap longer than this is treated as a session restart (Android Auto reconnect, OBD reconnect). */
    val gap: Duration = 2.minutes,
    /** Ignore level samples for this long after ignition on or a gap: clusters re-sample the sender at startup. */
    val settleAfterIgnition: Duration = 30.seconds,
    /** Ignore level samples for this long after the fuel door closes (sender damping). */
    val settleAfterDoor: Duration = 10.seconds,
    /** A sustained rise is accepted only once two consecutive medians differ by at most this (level stopped rising). */
    val settleTolerancePercent: Double = 1.5,
    /** Stationary medians within this band above the baseline are treated as noise and tracked. */
    val noiseBandPercent: Double = 1.0,
    /** No second detection within this time unless the car drove more than maxOdoMoveKm in between. */
    val debounce: Duration = 15.minutes,
    /** Moving samples kept (head and tail) for the low-confidence fallback when no stationary window exists. */
    val movingSamples: Int = 7,
)

/** One normalized level reading. [pct] is 0..100; [abs] is litres or kWh, only when the car measured it directly. */
internal data class LevelPoint(val pct: Double, val abs: Double?, val odo: Double?, val at: Instant)

/** A filtered level. [noise] is half the spread (percent points) of the samples behind the median. */
internal data class LevelSnapshot(
    val pct: Double,
    val abs: Double?,
    val odo: Double?,
    val at: Instant,
    val noise: Double,
    val fromMoving: Boolean,
    val samples: Int,
)

internal data class Rise(val before: LevelSnapshot, val after: LevelSnapshot, val trigger: String, val addedAbs: Double?)

internal object Triggers {
    const val FUEL_DOOR = "fuel_door"
    const val IGNITION = "ignition"
    const val GAP = "gap"
    const val LEVEL_RISE = "level_rise"

    fun priority(t: String) = when (t) {
        FUEL_DOOR -> 3
        IGNITION -> 2
        GAP -> 1
        else -> 0
    }
}

internal fun median(values: List<Double>): Double {
    val s = values.sorted()
    val n = s.size
    return if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2.0
}

/**
 * Signal-agnostic "level went up while the car was not driving" state machine.
 * [capacity] is in the same unit as [LevelPoint.abs] (litres or kWh) and is used to turn percent into an amount.
 */
internal class LevelRiseEngine(
    private val capacity: Double?,
    private val minAbs: Double?,
    private val minPct: Double,
    private val maxOdoMoveKm: Double,
    private val tuning: DetectorTuning,
    private val extract: (VehicleSample) -> LevelPoint?,
) {
    init {
        require(tuning.stableSamples >= 1 && tuning.eventSamples >= 1 && tuning.movingSamples >= 1)
    }

    /** Smallest rise (percent points) that can be accepted; below it a change is tracked as noise/consumption. */
    private val thresholdPct: Double = run {
        val absPct = if (minAbs != null && capacity != null && capacity > 0) minAbs / capacity * 100.0 else Double.POSITIVE_INFINITY
        min(minPct, absPct)
    }

    var ref: LevelSnapshot? = null
        private set
    private val window = ArrayList<LevelPoint>()
    private val movingHead = ArrayList<LevelPoint>()
    private val movingTail = ArrayDeque<LevelPoint>()
    private var armed: String? = null
    private var settleUntil: Instant? = null
    private var lastAt: Instant? = null
    private var lastIgnition: Boolean? = null
    private var lastDoor: Boolean? = null
    private var doorOpen = false
    private var lastEmit: LevelSnapshot? = null
    private var out: Rise? = null

    fun reset() {
        ref = null; window.clear(); movingHead.clear(); movingTail.clear(); armed = null; settleUntil = null
        lastAt = null; lastIgnition = null; lastDoor = null; doorOpen = false; lastEmit = null; out = null
    }

    fun onSample(s: VehicleSample): Rise? {
        out = null
        val prev = lastAt
        if (prev != null && s.at < prev) return null // out-of-order sample: ignore
        if (prev != null && s.at - prev > tuning.gap) onEvent(Triggers.GAP, s.at, tuning.settleAfterIgnition)
        lastAt = s.at

        s.ignitionOn?.let { on ->
            if (on && lastIgnition == false) onEvent(Triggers.IGNITION, s.at, tuning.settleAfterIgnition)
            lastIgnition = on
        }
        s.fuelDoorOpen?.let { open ->
            if (open && lastDoor != true) {
                leaveStop()
                doorOpen = true
            } else if (!open && lastDoor == true) {
                doorOpen = false
                onEvent(Triggers.FUEL_DOOR, s.at, tuning.settleAfterDoor)
            }
            lastDoor = open
        }
        if (doorOpen) return out // pumping: the level is changing, ignore it

        val p = extract(s) ?: return out
        if (!isStationary(s)) {
            if (window.isNotEmpty()) leaveStop()
            if (movingHead.size < tuning.movingSamples) movingHead.add(p)
            movingTail.addLast(p)
            while (movingTail.size > tuning.movingSamples) movingTail.removeFirst()
            return out
        }

        if (movingTail.isNotEmpty()) flushMoving()
        val until = settleUntil
        if (until != null && s.at < until) return out
        settleUntil = null

        window.add(p)
        val keep = max(2 * tuning.stableSamples, tuning.eventSamples)
        while (window.size > keep) window.removeAt(0)

        val trig = armed
        if (trig != null) {
            if (window.size >= tuning.eventSamples) emit(evaluate(snapshot(window.takeLast(tuning.eventSamples), false), trig))
            return out
        }
        val n = tuning.stableSamples
        if (window.size < n) return out
        val m1 = snapshot(window.takeLast(n), false)
        val r = ref
        if (r == null) {
            ref = m1
            return out
        }
        val rise = m1.pct - r.pct
        if (rise <= tuning.noiseBandPercent) {
            ref = m1 // consumption or noise: keep tracking the stationary baseline
        } else if (window.size >= 2 * n) {
            val m0 = snapshot(window.subList(window.size - 2 * n, window.size - n), false)
            if (abs(m1.pct - m0.pct) <= tuning.settleTolerancePercent) {
                if (rise >= thresholdPct) emit(evaluate(m1, Triggers.LEVEL_RISE)) else ref = m1 // small settled top-up
            }
        }
        return out
    }

    private fun isStationary(s: VehicleSample): Boolean = when {
        s.parked == true -> true
        s.speedKmh != null -> s.speedKmh <= tuning.stationarySpeedKmh
        s.parked == false -> false
        else -> true // no motion info at all: assume stationary
    }

    private fun emit(r: Rise?) {
        if (r != null && out == null) out = r
    }

    private fun arm(label: String) {
        val a = armed
        if (a == null || Triggers.priority(label) >= Triggers.priority(a)) armed = label
    }

    private fun onEvent(label: String, at: Instant, settle: Duration) {
        leaveStop()
        flushMoving()
        arm(label)
        settleUntil = at + settle
    }

    /** The car leaves the current stop (or an event splits it): use what the stationary window holds. */
    private fun leaveStop() {
        if (window.isEmpty()) return
        val trig = armed
        if (trig != null) {
            emit(evaluate(snapshot(window.takeLast(tuning.eventSamples), false), trig))
        } else if (window.size >= tuning.stableSamples) {
            val m1 = snapshot(window.takeLast(tuning.stableSamples), false)
            val r = ref
            if (r != null && m1.pct - r.pct >= thresholdPct) emit(evaluate(m1, Triggers.LEVEL_RISE))
        }
        window.clear()
    }

    /** Moving samples are slosh-noisy: only a fallback when no stationary reading exists around an event. */
    private fun flushMoving() {
        if (movingTail.isEmpty()) return
        if (movingTail.size >= 3) {
            val trig = armed
            if (trig != null) {
                emit(evaluate(snapshot(movingHead, true), trig))
            }
            ref = snapshot(movingTail.toList(), true)
        }
        movingHead.clear()
        movingTail.clear()
    }

    private fun snapshot(points: List<LevelPoint>, fromMoving: Boolean): LevelSnapshot {
        val pcts = points.map { it.pct }
        val absValues = points.mapNotNull { it.abs }
        return LevelSnapshot(
            pct = median(pcts),
            abs = if (absValues.size == points.size) median(absValues) else null,
            odo = points.lastOrNull { it.odo != null }?.odo,
            at = points.last().at,
            noise = (pcts.max() - pcts.min()) / 2.0,
            fromMoving = fromMoving,
            samples = points.size,
        )
    }

    private fun evaluate(after: LevelSnapshot, trigger: String): Rise? {
        armed = null
        val before = ref
        ref = after
        if (before == null) return null
        val risePct = after.pct - before.pct
        if (risePct <= 0) return null
        var added: Double? = if (before.abs != null && after.abs != null) after.abs - before.abs
        else capacity?.let { risePct / 100.0 * it }
        if (added != null && capacity != null) {
            val beforeAbs = before.abs ?: (before.pct / 100.0 * capacity)
            added = added.coerceIn(0.0, max(0.0, capacity - beforeAbs))
        }
        val bigEnough = risePct >= minPct || (minAbs != null && added != null && added >= minAbs)
        if (!bigEnough) return null
        if (before.odo != null && after.odo != null && abs(after.odo - before.odo) > maxOdoMoveKm) return null
        val last = lastEmit
        if (last != null && after.at - last.at < tuning.debounce &&
            (last.odo == null || after.odo == null || abs(after.odo - last.odo) <= maxOdoMoveKm)
        ) return null
        lastEmit = after
        return Rise(before, after, trigger, added)
    }
}
