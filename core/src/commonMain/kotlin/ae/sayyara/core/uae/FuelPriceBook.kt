package ae.sayyara.core.uae

import ae.sayyara.core.model.FuelGrade
import ae.sayyara.core.model.FuelPrice
import ae.sayyara.core.model.YearMonth
import kotlin.math.abs
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** FINAL: the month's price is announced. PROVISIONAL: not yet announced, latest known used. FORECAST: further out than next month. */
enum class QuoteStatus { FINAL, PROVISIONAL, FORECAST }

data class PriceQuote(val month: YearMonth, val grade: FuelGrade, val aedPerLitre: Double, val status: QuoteStatus)

/** Asia/Dubai (UTC+4, no DST). */
internal val UAE_ZONE: TimeZone by lazy { TimeZone.of("Asia/Dubai") }

/**
 * Month-aware price lookup. When the same (month, grade) appears more than once, the LAST entry wins,
 * so a remote feed appended after [UaeFuelPrices.seed] overrides it.
 *
 * @param now clock used to decide PROVISIONAL (up to next month) versus FORECAST (later).
 */
class FuelPriceBook(
    prices: List<FuelPrice>,
    private val now: () -> Instant = { Clock.System.now() },
    private val zone: TimeZone = UAE_ZONE,
) {
    private val byGrade: Map<FuelGrade, List<FuelPrice>> = prices
        .associateBy { it.month to it.grade }.values
        .groupBy { it.grade }
        .mapValues { (_, v) -> v.sortedBy { it.month } }

    val prices: List<FuelPrice> get() = byGrade.values.flatten()

    fun history(grade: FuelGrade): List<FuelPrice> = byGrade[grade].orEmpty()

    fun latest(grade: FuelGrade): FuelPrice? = byGrade[grade]?.lastOrNull()

    fun currentMonth(): YearMonth = YearMonth.of(now().toLocalDateTime(zone).date)

    /**
     * - Known (month, grade): FINAL.
     * - Before the earliest known month for the grade: null.
     * - Otherwise the latest price at or before [month] is used: PROVISIONAL when [month] is at most
     *   next month (relative to [now]), FORECAST when it is later.
     */
    fun quote(month: YearMonth, grade: FuelGrade): PriceQuote? {
        val list = byGrade[grade] ?: return null
        if (list.isEmpty() || month < list.first().month) return null
        list.firstOrNull { it.month == month }?.let { return PriceQuote(month, grade, it.aedPerLitre, QuoteStatus.FINAL) }
        val fallback = list.last { it.month <= month }
        val status = if (month > currentMonth().next()) QuoteStatus.FORECAST else QuoteStatus.PROVISIONAL
        return PriceQuote(month, grade, fallback.aedPerLitre, status)
    }

    /**
     * Largest absolute month-to-month change (AED/litre) for [grade] across consecutive known months,
     * limited to the trailing [trailingMonths] months ending at the latest known month.
     * Months with a gap (missing grade) are not compared. 0.0 when fewer than two consecutive months.
     */
    fun maxMonthlyMove(grade: FuelGrade, trailingMonths: Int = 12): Double {
        val list = byGrade[grade].orEmpty()
        val last = list.lastOrNull()?.month ?: return 0.0
        var start = last
        repeat(trailingMonths) { start = start.previous() }
        return list.filter { it.month >= start }.zipWithNext()
            .filter { (a, b) -> a.month.next() == b.month }
            .maxOfOrNull { (a, b) -> abs(b.aedPerLitre - a.aedPerLitre) } ?: 0.0
    }

    /** New book with [newer] applied on top of this one (newer wins). */
    fun plus(newer: List<FuelPrice>): FuelPriceBook = FuelPriceBook(prices + newer, now, zone)

    companion object {
        fun seeded(now: () -> Instant = { Clock.System.now() }) = FuelPriceBook(UaeFuelPrices.seed, now)
    }
}
