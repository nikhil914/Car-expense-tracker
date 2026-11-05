package ae.sayyara.core.consumption

import ae.sayyara.core.model.EnergyEvent
import ae.sayyara.core.model.EnergyStore
import ae.sayyara.core.model.FuelGrade
import kotlin.math.abs
import kotlin.math.round

/** Checks for the add/edit-fill form. Warnings prompt the user; they never block saving. */
object FillValidation {
    /** Plausible pump price for liquid fuel in the UAE, AED per litre. */
    val FUEL_PRICE_RANGE = 1.0..6.0
    /** Plausible charging price, AED per kWh (0 = free charging). */
    val CHARGE_PRICE_RANGE = 0.0..3.0
    /** Round AED step that suggests a prepaid partial fill. */
    const val ROUND_AED_STEP = 50.0
    /** A fill below this fraction of the median full fill suggests a partial. */
    const val SMALL_FILL_FRACTION = 0.5
    const val MIN_HISTORY_FOR_MEDIAN = 3

    fun check(candidate: EnergyEvent, history: List<EnergyEvent>, store: EnergyStore?): List<ValidationWarning> {
        val others = relevant(candidate, history, store)
        val battery = ConsumptionEngine.isBattery(others + candidate, store)
        val capacity = store?.let { it.usableCapacity ?: it.capacity }?.takeIf { it > 0 }
        val out = mutableListOf<ValidationWarning>()
        val id = candidate.id

        if (candidate.amount <= 0.0) {
            out += ValidationWarning(id, WarningCodes.AMOUNT_NOT_POSITIVE, "Amount must be greater than zero.")
        }
        if (capacity != null) {
            // Wall kWh can exceed battery capacity because of charging losses.
            val limit = capacity * if (battery) 1.3 else 1.05
            if (candidate.amount > limit) {
                out += ValidationWarning(id, WarningCodes.AMOUNT_EXCEEDS_CAPACITY,
                    "${Units.formatNumber(candidate.amount, 2)} is more than the ${Units.formatNumber(capacity, 1)} capacity.")
            }
        }

        // Odometer order against neighbours by date.
        val prev = others.filter { it.timestamp <= candidate.timestamp }.maxWithOrNull(compareBy({ it.timestamp }, { it.odometerKm }))
        val next = others.filter { it.timestamp > candidate.timestamp }.minWithOrNull(compareBy({ it.timestamp }, { it.odometerKm }))
        if (prev != null && candidate.odometerKm <= prev.odometerKm) {
            out += ValidationWarning(id, WarningCodes.ODO_NOT_INCREASING,
                "Odometer ${Units.formatNumber(candidate.odometerKm, 0)} km is not above the previous fill (${Units.formatNumber(prev.odometerKm, 0)} km).")
        } else if (next != null && candidate.odometerKm >= next.odometerKm) {
            out += ValidationWarning(id, WarningCodes.ODO_NOT_INCREASING,
                "Odometer ${Units.formatNumber(candidate.odometerKm, 0)} km is not below the next fill (${Units.formatNumber(next.odometerKm, 0)} km).")
        }

        // Prices and totals.
        candidate.unitPriceAed?.let { p ->
            val range = if (battery || candidate.grade == FuelGrade.ELECTRIC) CHARGE_PRICE_RANGE else FUEL_PRICE_RANGE
            if (p !in range) {
                out += ValidationWarning(id, WarningCodes.IMPLAUSIBLE_PRICE,
                    "Unit price AED ${Units.formatNumber(p, 2)} looks wrong.")
            }
            candidate.totalAed?.let { t ->
                val expected = candidate.amount * p + candidate.serviceFeeAed
                if (abs(t - expected) > maxOf(0.5, 0.02 * t)) {
                    out += ValidationWarning(id, WarningCodes.TOTAL_MISMATCH,
                        "Total AED ${Units.formatNumber(t, 2)} does not match amount x price (AED ${Units.formatNumber(expected, 2)}).")
                }
            }
        }

        // Gauge / SoC fractions.
        fun badPair(before: Double?, after: Double?): Boolean =
            listOfNotNull(before, after).any { it < 0.0 || it > 1.0 } ||
                (before != null && after != null && after < before)
        if (badPair(candidate.levelBefore, candidate.levelAfter) || badPair(candidate.socBefore, candidate.socAfter)) {
            out += ValidationWarning(id, WarningCodes.LEVEL_OUT_OF_RANGE,
                "Level must be between 0 and 100%, and the level after must not be below the level before.")
        }

        // Consumption-level checks: run the engine with the candidate in place.
        val engineCodes = setOf(WarningCodes.IMPLAUSIBLE_CONSUMPTION, WarningCodes.POSSIBLE_MISSED_FILL, WarningCodes.GAUGE_RECALIBRATE)
        out += ConsumptionEngine.compute(others + candidate.copy(deletedAt = null), store).warnings
            .filter { it.eventId == id && it.code in engineCodes }

        return out.distinctBy { it.code }
    }

    /**
     * UAE heuristic for the "Partial fill?" prompt on a fill marked full. True when the total is a round
     * AED amount (multiple of 50: 50, 100, 150 ...), when the gauge-after is clearly below full, or when the
     * volume is far below the usual full fill (median of past full fills). False if already marked partial.
     */
    fun suggestPartial(candidate: EnergyEvent, history: List<EnergyEvent>, store: EnergyStore?): Boolean {
        if (!candidate.isFull) return false
        val total = candidate.totalAed ?: candidate.unitPriceAed?.let { candidate.amount * it }
        if (total != null && total > 0 && isRound(total, ROUND_AED_STEP)) return true
        candidate.levelAfter?.let { if (it < 0.9) return true }
        candidate.socAfter?.let { if (it < 0.9) return true }
        val fulls = relevant(candidate, history, store)
            .filter { it.isFull && !it.missedPrevious && it.amount > 0 }
            .map { it.amount }
            .sorted()
        if (fulls.size >= MIN_HISTORY_FOR_MEDIAN && candidate.amount > 0) {
            val median = if (fulls.size % 2 == 1) fulls[fulls.size / 2]
            else (fulls[fulls.size / 2 - 1] + fulls[fulls.size / 2]) / 2
            if (candidate.amount < SMALL_FILL_FRACTION * median) return true
        }
        return false
    }

    private fun relevant(candidate: EnergyEvent, history: List<EnergyEvent>, store: EnergyStore?): List<EnergyEvent> {
        val storeId = store?.id ?: candidate.storeId
        return history.filter { it.deletedAt == null && it.id != candidate.id && it.storeId == storeId }
    }

    private fun isRound(v: Double, step: Double): Boolean {
        val q = v / step
        return abs(q - round(q)) * step < 0.005
    }
}
