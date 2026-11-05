package ae.sayyara.core.consumption

import ae.sayyara.core.model.EnergyEvent
import ae.sayyara.core.model.Expense
import ae.sayyara.core.model.ExpenseCategoryKey
import ae.sayyara.core.model.FuelGrade
import ae.sayyara.core.model.YearMonth
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/**
 * One calendar month.
 * - [km]: distance driven in the month. Distance between consecutive events is spread over time.
 * - [fuelAmount]: litres (or kWh for EV-only vehicles) burned in the month. Each valid segment's amount is
 *   prorated by the fraction of its distance that falls inside the month.
 * - [fuelAed]: fuel/charging spend dated in the month.
 * - [expensesAed]: other expenses by category (income counts negative).
 * - [per100km]: 100 x [fuelAmount] / km covered by valid segments in the month.
 */
data class MonthlyStat(
    val month: YearMonth,
    val km: Double,
    val fuelAmount: Double,
    val fuelAed: Double,
    val expensesAed: Map<ExpenseCategoryKey, Double>,
    val totalAed: Double,
    val per100km: Double?,
    val costPerKmAed: Double?,
)

object Stats {
    /**
     * Monthly stats over a continuous month range (gaps included as empty months), ascending.
     * Events may span several vehicles and stores; consumption is computed per store. For dual-fuel or
     * PHEV vehicles, [MonthlyStat.fuelAmount] counts liquid-fuel stores only.
     */
    fun monthly(events: List<EnergyEvent>, expenses: List<Expense>, zone: TimeZone): List<MonthlyStat> {
        val live = events.filter { it.deletedAt == null }
        val liveExpenses = expenses.filter { it.deletedAt == null }

        val km = mutableMapOf<YearMonth, Double>()
        val fuel = mutableMapOf<YearMonth, Double>()
        val validKm = mutableMapOf<YearMonth, Double>()
        val fuelAed = mutableMapOf<YearMonth, Double>()
        val exp = mutableMapOf<YearMonth, MutableMap<ExpenseCategoryKey, Double>>()
        val months = mutableSetOf<YearMonth>()

        for ((_, vEvents) in live.groupBy { it.vehicleId }) {
            val sorted = ConsumptionEngine.prepare(vEvents)
            // Pair distance spread by time into months.
            val pairMonths = ArrayList<Map<YearMonth, Double>>(maxOf(sorted.size - 1, 0))
            for (k in 0 until sorted.size - 1) {
                val d = sorted[k + 1].odometerKm - sorted[k].odometerKm
                val split = if (d > 0) spread(sorted[k].timestamp, sorted[k + 1].timestamp, d, zone) else emptyMap()
                pairMonths += split
                split.forEach { (m, v) -> km.add(m, v) }
            }

            val byStore = vEvents.groupBy { it.storeId }
            val liquid = byStore.filterValues { list -> list.any { it.grade != FuelGrade.ELECTRIC } }
            val counted = liquid.ifEmpty { byStore }
            for ((_, sEvents) in counted) {
                val result = ConsumptionEngine.compute(sEvents)
                for (seg in result.averageSegments()) {
                    val start = sorted.first { it.id == seg.startEventId }.odometerKm
                    val end = sorted.first { it.id == seg.endEventId }.odometerKm
                    val inMonth = mutableMapOf<YearMonth, Double>()
                    for (k in 0 until sorted.size - 1) {
                        if (sorted[k].odometerKm >= start && sorted[k + 1].odometerKm <= end) {
                            pairMonths[k].forEach { (m, v) -> inMonth.add(m, v) }
                        }
                    }
                    for ((m, dm) in inMonth) {
                        fuel.add(m, seg.amount * dm / seg.km)
                        validKm.add(m, dm)
                    }
                }
            }
        }

        for (e in live) {
            val m = monthOf(e.timestamp, zone)
            months += m
            fuelAed.add(m, e.costAed())
        }
        for (x in liveExpenses) {
            val m = monthOf(x.timestamp, zone)
            months += m
            exp.getOrPut(m) { mutableMapOf() }.add(x.category, if (x.isIncome) -x.amountAed else x.amountAed)
        }
        months += km.keys
        if (months.isEmpty()) return emptyList()

        val out = mutableListOf<MonthlyStat>()
        var m = months.min()
        val last = months.max()
        while (m <= last) {
            val k = km[m] ?: 0.0
            val f = fuel[m] ?: 0.0
            val vk = validKm[m] ?: 0.0
            val fa = fuelAed[m] ?: 0.0
            val e = exp[m]?.toMap() ?: emptyMap()
            val total = fa + e.values.sum()
            out += MonthlyStat(
                month = m,
                km = k,
                fuelAmount = f,
                fuelAed = fa,
                expensesAed = e,
                totalAed = total,
                per100km = if (vk > 0) 100.0 * f / vk else null,
                costPerKmAed = if (k > 0) total / k else null,
            )
            m = m.next()
        }
        return out
    }

    fun monthOf(t: Instant, zone: TimeZone): YearMonth = YearMonth.of(t.toLocalDateTime(zone).date)

    fun monthStart(m: YearMonth, zone: TimeZone): Instant = LocalDate(m.year, m.month, 1).atStartOfDayIn(zone)

    /** Spreads distance [d] driven between [t0] and [t1] over months in proportion to elapsed time. */
    fun spread(t0: Instant, t1: Instant, d: Double, zone: TimeZone): Map<YearMonth, Double> {
        if (t1 <= t0) return mapOf(monthOf(t1, zone) to d)
        val total = (t1 - t0).inWholeMilliseconds.toDouble()
        val out = mutableMapOf<YearMonth, Double>()
        var m = monthOf(t0, zone)
        val last = monthOf(t1, zone)
        while (m <= last) {
            val from = maxOf(t0, monthStart(m, zone))
            val to = minOf(t1, monthStart(m.next(), zone))
            if (to > from) out.add(m, d * (to - from).inWholeMilliseconds.toDouble() / total)
            m = m.next()
        }
        return out
    }

    private fun <K> MutableMap<K, Double>.add(k: K, v: Double) { this[k] = (this[k] ?: 0.0) + v }
}
