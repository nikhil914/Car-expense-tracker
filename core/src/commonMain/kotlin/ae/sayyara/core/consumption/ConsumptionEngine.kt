package ae.sayyara.core.consumption

import ae.sayyara.core.model.EnergyEvent
import ae.sayyara.core.model.EnergyStore
import ae.sayyara.core.model.EnergyStoreKind
import ae.sayyara.core.model.EnergyUnit
import ae.sayyara.core.model.FuelGrade
import ae.sayyara.core.model.Id
import ae.sayyara.core.model.MeterSource

/** How a segment's energy figure was obtained. */
enum class SegmentMethod {
    /** Exact: fuel pumped between two brim fills (partials accumulated). */
    FULL_TO_FULL,
    /** Estimate from tank gauge readings (levelBefore/levelAfter) and tank capacity. Show as "~". */
    GAUGE,
    /** EV/PHEV battery: capacity x state-of-charge drop (battery-to-wheel kWh). */
    SOC,
}

/**
 * One consumption segment between two energy events.
 * [amount] is litres (tank) or kWh (battery) used over [km]. [valid] = false means the segment
 * is shown but excluded from averages (zero distance, implausible value, likely missed fill).
 */
data class Segment(
    val startEventId: Id,
    val endEventId: Id,
    val km: Double,
    val amount: Double,
    val per100km: Double,
    val method: SegmentMethod,
    val valid: Boolean,
)

/** A non-fatal data problem. [code] is one of [WarningCodes]. [eventId] is the event to fix or review. */
data class ValidationWarning(val eventId: Id?, val code: String, val message: String)

data class ConsumptionResult(
    val segments: List<Segment>,
    /** Distance-weighted: 100 * sum(amount) / sum(km) over valid segments of [averageMethod]. */
    val averagePer100km: Double?,
    /** Latest valid segment of [averageMethod]. */
    val lastPer100km: Double?,
    /** Odometer span of all (non-deleted) events, including distance across chain breaks. */
    val totalKm: Double,
    /** Sum of litres/kWh bought over all events (spend view, first fill included). */
    val totalAmount: Double,
    /** Sum of event costs over all events (spend view, first fill included). */
    val totalFuelCostAed: Double,
    /** Cost of fuel burned per km: cost of all events except the first, divided by [totalKm]. */
    val fuelCostPerKmAed: Double?,
    /** Partial fills after the last full fill, waiting for the next full fill to close the segment. */
    val pendingPartialEventIds: Set<Id>,
    val warnings: List<ValidationWarning>,
)

/** Stable warning codes (UI maps them to localized strings; [ValidationWarning.message] is English fallback). */
object WarningCodes {
    const val ODO_NOT_INCREASING = "ODO_NOT_INCREASING"
    const val ODO_DATE_MISMATCH = "ODO_DATE_MISMATCH"
    const val IMPLAUSIBLE_CONSUMPTION = "IMPLAUSIBLE_CONSUMPTION"
    const val POSSIBLE_MISSED_FILL = "POSSIBLE_MISSED_FILL"
    const val GAUGE_RECALIBRATE = "GAUGE_RECALIBRATE"
    const val AMOUNT_NOT_POSITIVE = "AMOUNT_NOT_POSITIVE"
    const val AMOUNT_EXCEEDS_CAPACITY = "AMOUNT_EXCEEDS_CAPACITY"
    const val IMPLAUSIBLE_PRICE = "IMPLAUSIBLE_PRICE"
    const val TOTAL_MISMATCH = "TOTAL_MISMATCH"
    const val LEVEL_OUT_OF_RANGE = "LEVEL_OUT_OF_RANGE"
}

/** Cost of one event: [EnergyEvent.totalAed], else amount x unit price + service fee, else service fee (0 for free). */
fun EnergyEvent.costAed(): Double =
    totalAed ?: ((unitPriceAed?.let { amount * it } ?: 0.0) + serviceFeeAed)

/** The method the result's average is based on: FULL_TO_FULL if any valid exact segment exists, else GAUGE, else SOC. */
val ConsumptionResult.averageMethod: SegmentMethod?
    get() = ConsumptionEngine.primaryMethod(segments)

/** The valid segments that make up [ConsumptionResult.averagePer100km] (for "based on N fills since ..."). */
fun ConsumptionResult.averageSegments(): List<Segment> {
    val m = averageMethod ?: return emptyList()
    return segments.filter { it.valid && it.method == m }
}

/**
 * Deterministic consumption engine. Consumption is derived data: always recompute from raw events.
 *
 * Pass the events of ONE energy store. When [store] is given, events of other stores are ignored and its
 * capacity enables gauge/SoC estimates and range checks. Soft-deleted events are ignored.
 */
object ConsumptionEngine {
    const val MIN_L_PER_100KM = 2.0
    const val MAX_L_PER_100KM = 40.0
    const val MIN_KWH_PER_100KM = 5.0
    const val MAX_KWH_PER_100KM = 60.0
    /** Gauge consistency: residual above this fraction of capacity suggests recalibrating capacity. */
    const val GAUGE_RESIDUAL_FRACTION = 0.10

    /** Default wall-to-battery efficiency by charger type (editable in UI). */
    const val EFFICIENCY_L1 = 0.80
    const val EFFICIENCY_L2 = 0.90
    const val EFFICIENCY_DC = 0.93

    /** Non-deleted events of [store] (if given), sorted by odometer, then timestamp, then id. */
    fun prepare(events: List<EnergyEvent>, store: EnergyStore? = null): List<EnergyEvent> =
        events.asSequence()
            .filter { it.deletedAt == null && (store == null || it.storeId == store.id) }
            .sortedWith(compareBy<EnergyEvent>({ it.odometerKm }, { it.timestamp }, { it.id }))
            .toList()

    fun isBattery(events: List<EnergyEvent>, store: EnergyStore?): Boolean =
        if (store != null) store.kind == EnergyStoreKind.BATTERY || store.unit == EnergyUnit.KWH
        else events.isNotEmpty() && events.all { it.grade == FuelGrade.ELECTRIC }

    fun plausibleRange(battery: Boolean): ClosedFloatingPointRange<Double> =
        if (battery) MIN_KWH_PER_100KM..MAX_KWH_PER_100KM else MIN_L_PER_100KM..MAX_L_PER_100KM

    internal fun primaryMethod(segments: List<Segment>): SegmentMethod? =
        listOf(SegmentMethod.FULL_TO_FULL, SegmentMethod.GAUGE, SegmentMethod.SOC)
            .firstOrNull { m -> segments.any { it.valid && it.method == m } }

    fun compute(events: List<EnergyEvent>, store: EnergyStore? = null): ConsumptionResult {
        val sorted = prepare(events, store)
        val n = sorted.size
        val battery = isBattery(sorted, store)
        val capacity = store?.let { it.usableCapacity ?: it.capacity }?.takeIf { it > 0 }
        val range = plausibleRange(battery)
        val unitLabel = if (battery) "kWh/100km" else "L/100km"
        val warnings = mutableListOf<ValidationWarning>()
        val segments = mutableListOf<Pair<Pair<Int, Int>, Segment>>()

        // --- Pair-level validation ---------------------------------------------------------------
        val suspectPair = BooleanArray(maxOf(n - 1, 0))
        for (k in 0 until n) {
            val e = sorted[k]
            if (e.amount <= 0.0) warnings += ValidationWarning(e.id, WarningCodes.AMOUNT_NOT_POSITIVE,
                "Amount must be greater than zero.")
        }
        for (k in 0 until n - 1) {
            val a = sorted[k]
            val b = sorted[k + 1]
            val d = b.odometerKm - a.odometerKm
            if (d <= 0.0) {
                suspectPair[k] = true
                warnings += ValidationWarning(b.id, WarningCodes.ODO_NOT_INCREASING,
                    "Odometer ${fmtKm(b.odometerKm)} km is not above the previous entry (${fmtKm(a.odometerKm)} km).")
            }
            if (b.timestamp < a.timestamp) {
                warnings += ValidationWarning(b.id, WarningCodes.ODO_DATE_MISMATCH,
                    "Entry at ${fmtKm(b.odometerKm)} km is dated before the entry at ${fmtKm(a.odometerKm)} km. Check the odometer or date.")
            }
            if (capacity != null && !b.missedPrevious) {
                val maxRange = capacity * 100.0 / range.start
                if (d > maxRange) {
                    suspectPair[k] = true
                    warnings += ValidationWarning(b.id, WarningCodes.POSSIBLE_MISSED_FILL,
                        "${fmtKm(d)} km since the previous fill is more than a full ${if (battery) "battery" else "tank"} allows. Did you miss a fill-up?")
                }
            }
        }

        fun evaluate(i: Int, j: Int, amount: Double, method: SegmentMethod): Segment {
            val a = sorted[i]
            val b = sorted[j]
            val d = b.odometerKm - a.odometerKm
            val per100 = if (d > 0) 100.0 * amount / d else 0.0
            var valid = d > 0 && amount > 0 && (i until j).none { suspectPair[it] }
            if (valid && per100 !in range) {
                valid = false
                if (per100 < range.start) {
                    warnings += ValidationWarning(b.id, WarningCodes.POSSIBLE_MISSED_FILL,
                        "${fmt1(per100)} $unitLabel is unusually low. Did you miss a fill-up?")
                } else {
                    warnings += ValidationWarning(b.id, WarningCodes.IMPLAUSIBLE_CONSUMPTION,
                        "${fmt1(per100)} $unitLabel is outside the plausible range (${fmt1(range.start)}-${fmt1(range.endInclusive)}). Check amount, odometer and the full-tank flag.")
                }
            }
            return Segment(a.id, b.id, d, amount, per100, method, valid)
        }

        val socMode = battery && capacity != null && sorted.any { it.socBefore != null || it.socAfter != null }
        val covered = BooleanArray(maxOf(n - 1, 0))
        val pending = linkedSetOf<Id>()

        if (!socMode) {
            // --- A/B: full-to-full with partial accumulation, missed fills break the chain -------
            var anchor: Int? = null
            var acc = 0.0
            for (i in 0 until n) {
                val e = sorted[i]
                if (e.missedPrevious) {
                    pending.clear(); acc = 0.0
                    anchor = if (e.isFull) i else null
                    continue
                }
                val a = anchor
                if (a == null) {
                    if (e.isFull) { anchor = i; acc = 0.0 }
                    continue
                }
                acc += e.amount
                if (!e.isFull) { pending += e.id; continue }
                val seg = evaluate(a, i, acc, SegmentMethod.FULL_TO_FULL)
                segments += (a to i) to seg
                if (seg.valid) for (k in a until i) covered[k] = true
                anchor = i; acc = 0.0; pending.clear()
            }

            // --- D: gauge estimates where no full-to-full segment covers the distance -----------
            if (capacity != null && !battery) {
                for (j in 0 until n) {
                    val e = sorted[j]
                    val before = e.levelBefore
                    val after = e.levelAfter
                    if (before != null && after != null) {
                        val residual = kotlin.math.abs(e.amount - capacity * (after - before))
                        if (residual > GAUGE_RESIDUAL_FRACTION * capacity) {
                            warnings += ValidationWarning(e.id, WarningCodes.GAUGE_RECALIBRATE,
                                "${fmt1(e.amount)} L added does not match the gauge change for a ${fmt1(capacity)} L tank. Recalibrate the tank capacity?")
                        }
                    }
                }
                var i = 0
                while (i < n - 1) {
                    val post = postLevel(sorted[i], capacity)
                    if (post == null) { i++; continue }
                    var j = i + 1
                    var mid = 0.0
                    var ok = true
                    while (j < n) {
                        if (covered[j - 1] || sorted[j].missedPrevious) { ok = false; break }
                        if (sorted[j].levelBefore != null) break
                        mid += sorted[j].amount
                        j++
                    }
                    if (j >= n) break
                    if (!ok) { i = j; continue }
                    val used = capacity * (post - sorted[j].levelBefore!!) + mid
                    segments += (i to j) to evaluate(i, j, used, SegmentMethod.GAUGE)
                    i = j
                }
            }
        } else {
            // --- E: EV state of charge, battery-to-wheel kWh = C x (SoC_after_i - SoC_before_j) --
            val c = capacity!!
            for (i in 0 until n - 1) {
                val a = sorted[i]
                val b = sorted[i + 1]
                if (b.missedPrevious) continue
                val after = a.socAfter ?: continue
                val before = b.socBefore ?: continue
                segments += (i to i + 1) to evaluate(i, i + 1, c * (after - before), SegmentMethod.SOC)
            }
        }

        val ordered = segments.sortedWith(compareBy({ it.first.second }, { it.first.first })).map { it.second }
        val method = primaryMethod(ordered)
        val used = ordered.filter { it.valid && it.method == method }
        val sumKm = used.sumOf { it.km }
        val average = if (method != null && sumKm > 0) 100.0 * used.sumOf { it.amount } / sumKm else null

        val totalKm = if (n >= 2) sorted.last().odometerKm - sorted.first().odometerKm else 0.0
        val totalCost = sorted.sumOf { it.costAed() }
        val burnedCost = if (n >= 2) totalCost - sorted.first().costAed() else 0.0

        return ConsumptionResult(
            segments = ordered,
            averagePer100km = average,
            lastPer100km = used.lastOrNull()?.per100km,
            totalKm = totalKm,
            totalAmount = sorted.sumOf { it.amount },
            totalFuelCostAed = totalCost,
            fuelCostPerKmAed = if (totalKm > 0) burnedCost / totalKm else null,
            pendingPartialEventIds = pending.toSet(),
            warnings = warnings.distinctBy { it.eventId to it.code },
        )
    }

    /** Gauge fraction after event: explicit, 1.0 for a brim fill, or before + amount/C. */
    private fun postLevel(e: EnergyEvent, capacity: Double): Double? =
        e.levelAfter ?: if (e.isFull) 1.0 else e.levelBefore?.let { (it + e.amount / capacity).coerceAtMost(1.0) }

    /**
     * Learned wall-to-battery efficiency: sum(C x deltaSoC) / sum(wall kWh) over charges that record both
     * SoC values and a wall-metered amount. Null when no such charge or no capacity.
     */
    fun learnedEfficiency(events: List<EnergyEvent>, store: EnergyStore): Double? {
        val c = (store.usableCapacity ?: store.capacity)?.takeIf { it > 0 } ?: return null
        var batt = 0.0
        var wall = 0.0
        for (e in prepare(events, store)) {
            val b = e.socBefore ?: continue
            val a = e.socAfter ?: continue
            if (e.meterSource != MeterSource.WALL || e.amount <= 0 || a <= b) continue
            batt += c * (a - b)
            wall += e.amount
        }
        return if (wall > 0) batt / wall else null
    }

    /** Wall-to-wheel (what you pay for) from battery-to-wheel kWh/100km and efficiency. */
    fun wallToWheelPer100km(batteryPer100km: Double, efficiency: Double): Double = batteryPer100km / efficiency

    private fun fmtKm(v: Double): String = Units.formatNumber(v, 0)
    private fun fmt1(v: Double): String = Units.formatNumber(v, 1)
}
