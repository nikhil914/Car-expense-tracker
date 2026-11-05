package ae.sayyara.core.uae

import ae.sayyara.core.model.FuelGrade
import ae.sayyara.core.model.FuelPrice
import ae.sayyara.core.model.PriceStatus
import ae.sayyara.core.model.YearMonth
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * UAE national monthly fuel prices (one price per grade, UAE-wide, effective the 1st of the month).
 *
 * Announced by the UAE Fuel Price Committee on the last day of the previous month; the primary
 * release is carried by WAM (https://www.wam.ae), then republished by Gulf News, Khaleej Times,
 * The National, Emirates 24|7, Aletihad.
 */
object UaeFuelPrices {

    private const val DUBICARS = "https://www.dubicars.com/news/petrol-price-in-uae.html"
    private const val GN_HISTORY = "https://gulfnews.com/gold-forex/historical-fuel-rates"
    private const val GN_JAN26 = "https://gulfnews.com/business/energy/uae-petrol-diesel-prices-for-january-2026-announced-1.500393811"
    private const val GT_FEB26 = "https://www.gulftoday.ae/news/2026/01/31/fuel-prices-from-february-2026-in-uae-announced"
    private const val WAM_MAR26 = "https://en.aletihad.ae/amp/news/business/4648017/uae-fuel-price-committee-announces-prices-for-march"
    private const val GN_APR26 = "https://gulfnews.com/business/energy/uae-announces-petrol-and-diesel-prices-for-april-2026-1.500491487"
    private const val DFL_JUN26 = "https://dubaifastliving.com/uae-fuel-prices-june-2026/"
    private const val YM_OCT26 = "https://www.yallamotor.com/news/uae-fuel-prices-october-2026-analysis-55857"
    private const val E247_OCT26 = "https://www.emirates247.com/uae/uae-fuel-prices-for-october-2026-announced/6171"
    private const val KT_NOV26 = "https://www.khaleejtimes.com/business/energy/uae-petrol-diesel-prices-november-2026-announced"

    /*
     * SEED PRICE TABLE, AED per litre, Nov 2025 .. Nov 2026.
     *
     * Source: research notes "uae_fuel_prices_route_cost.md" (5 Nov 2026), section 2. Every figure
     * there was taken from WebSearch result snippets of news/aggregator sites (sourceUrl below);
     * no WAM primary release page was read.
     *
     * !!! THESE FIGURES MUST BE CHECKED AGAINST THE WAM RELEASES ("UAE Fuel Price Committee
     * !!! announces prices for <Month>") BEFORE THEY ARE TREATED AS PRODUCTION DATA.
     *
     * Status policy:
     *  - Nov 2025 .. Mar 2026: VERIFIED in the sense that several independent outlets agree (Mar 2026
     *    carries a WAM dateline via Aletihad). Still to be cross-checked against WAM.
     *  - Apr 2026 .. Nov 2026: PROVISIONAL. The notes flag these as not solid: Apr diesel conflicts
     *    (4.69 vs EZHire 4.62), May-Jul have missing grades, and the Nov WAM release was not seen.
     *  - Missing grades are left ABSENT (May SP95; Jun SP95/EP91; Jul SP95/EP91). Never invent them.
     */
    val seed: List<FuelPrice> = buildList {
        fun row(y: Int, m: Int, s98: Double?, sp95: Double?, ep91: Double?, diesel: Double?, status: PriceStatus, src: String) {
            val ym = YearMonth(y, m)
            s98?.let { add(FuelPrice(ym, FuelGrade.SUPER_98, it, status, src)) }
            sp95?.let { add(FuelPrice(ym, FuelGrade.SPECIAL_95, it, status, src)) }
            ep91?.let { add(FuelPrice(ym, FuelGrade.E_PLUS_91, it, status, src)) }
            diesel?.let { add(FuelPrice(ym, FuelGrade.DIESEL, it, status, src)) }
        }
        val v = PriceStatus.VERIFIED
        val p = PriceStatus.PROVISIONAL
        row(2025, 11, 2.63, 2.51, 2.44, 2.67, v, DUBICARS)
        row(2025, 12, 2.70, 2.58, 2.51, 2.85, v, GN_HISTORY)
        row(2026, 1, 2.53, 2.42, 2.34, 2.55, v, GN_JAN26)
        row(2026, 2, 2.45, 2.33, 2.26, 2.52, v, GT_FEB26)
        row(2026, 3, 2.59, 2.48, 2.40, 2.72, v, WAM_MAR26)
        row(2026, 4, 3.39, 3.28, 3.20, 4.69, p, GN_APR26) // diesel: EZHire says 4.62
        row(2026, 5, 3.66, null, 3.48, 4.69, p, GN_HISTORY) // SP95 not found
        row(2026, 6, 3.95, null, null, 4.33, p, DFL_JUN26) // SP95, EP91 not found
        row(2026, 7, 3.40, null, null, 3.60, p, GN_HISTORY) // SP95, EP91 not found
        row(2026, 8, 3.60, 3.49, 3.41, 3.80, p, YM_OCT26)
        row(2026, 9, 3.80, 3.69, 3.61, 4.30, p, YM_OCT26)
        row(2026, 10, 4.40, 4.28, 4.21, 4.80, p, E247_OCT26)
        row(2026, 11, 4.16, 4.04, 3.97, 4.75, p, KT_NOV26)
    }

    /** Plausible retail band, AED/litre. */
    const val MIN_PLAUSIBLE_AED = 0.5
    const val MAX_PLAUSIBLE_AED = 10.0

    // ---------------------------------------------------------------- announcement parsing

    private val ENGLISH_GRADES: List<Pair<FuelGrade, Regex>> = listOf(
        FuelGrade.SUPER_98 to Regex("""super\s*-?\s*98""", RegexOption.IGNORE_CASE),
        FuelGrade.SPECIAL_95 to Regex("""special\s*-?\s*95""", RegexOption.IGNORE_CASE),
        FuelGrade.E_PLUS_91 to Regex("""e\s*-?\s*plus\s*-?\s*91""", RegexOption.IGNORE_CASE),
        FuelGrade.DIESEL to Regex("""\bdiesel\b""", RegexOption.IGNORE_CASE),
    )

    // Arabic grade names as used by WAM Arabic / Aletihad / Al Khaleej (spelling varies).
    private val ARABIC_GRADES: List<Pair<FuelGrade, Regex>> = listOf(
        FuelGrade.SUPER_98 to Regex("""سوبر\s*98"""),
        FuelGrade.SPECIAL_95 to Regex("""[اإ]?سبيشال\s*95|سبيشل\s*95|سبشل\s*95"""),
        FuelGrade.E_PLUS_91 to Regex("""[إا]ي\s*-?\s*بلس\s*91|اي\s*بلاس\s*91"""),
        FuelGrade.DIESEL to Regex("""(?:ال)?ديزل"""),
    )

    /** A decimal price, optionally preceded/followed by a currency token. Decimal point is required. */
    private val PRICE = Regex("""(\d{1,2}\.\d{1,2})""")
    private val FROM_TO = Regex("""from\s+(?:dhs?|aed)?\s*(\d{1,2}\.\d{1,2}).{0,40}?\bto\s+(?:dhs?|aed)?\s*(\d{1,2}\.\d{1,2})""", RegexOption.IGNORE_CASE)
    private val AR_FROM_TO = Regex("""من\s*(\d{1,2}\.\d{1,2}).{0,40}?إلى\s*(\d{1,2}\.\d{1,2})""")

    /** Converts Arabic-Indic and Extended Arabic-Indic digits to ASCII, and the Arabic decimal separator to '.'. */
    fun normalizeDigits(text: String): String = buildString(text.length) {
        for (c in text) {
            when (c) {
                in '٠'..'٩' -> append('0' + (c - '٠'))
                in '۰'..'۹' -> append('0' + (c - '۰'))
                '٫' -> append('.') // Arabic decimal separator
                else -> append(c)
            }
        }
    }

    /**
     * Extracts grade prices from WAM / news text in English or Arabic, e.g.
     * "Super 98 petrol will cost Dh4.16 per litre, compared to Dh4.40 in October".
     *
     * For each grade the first price after the grade name (and before the next grade name) is taken;
     * "from X to Y" phrasing yields Y. Returns null when no grade is found or when a sanity check fails:
     * any price outside [MIN_PLAUSIBLE_AED, MAX_PLAUSIBLE_AED], or petrol ordering not
     * Super 98 > Special 95 > E-Plus 91 (checked among the grades present).
     * Parsed prices are returned as [PriceStatus.PROVISIONAL] pending admin/second-source approval.
     */
    fun parseAnnouncement(text: String, month: YearMonth): List<FuelPrice>? {
        val t = normalizeDigits(text)
            .replace(' ', ' ')
            .replace(Regex("""(\d),(\d{2})(?!\d)"""), "$1.$2") // "4,16" -> "4.16" (decimal comma)
        val found = extract(t, ENGLISH_GRADES, FROM_TO)
        val ar = extract(t, ARABIC_GRADES, AR_FROM_TO)
        for ((g, v) in ar) if (g !in found) found[g] = v
        if (found.isEmpty()) return null
        if (found.values.any { it < MIN_PLAUSIBLE_AED || it > MAX_PLAUSIBLE_AED }) return null
        val order = listOf(FuelGrade.SUPER_98, FuelGrade.SPECIAL_95, FuelGrade.E_PLUS_91).mapNotNull { found[it] }
        if (order.zipWithNext().any { (hi, lo) -> hi <= lo }) return null
        return listOf(FuelGrade.SUPER_98, FuelGrade.SPECIAL_95, FuelGrade.E_PLUS_91, FuelGrade.DIESEL)
            .mapNotNull { g -> found[g]?.let { FuelPrice(month, g, it, PriceStatus.PROVISIONAL) } }
    }

    private fun extract(t: String, grades: List<Pair<FuelGrade, Regex>>, fromTo: Regex): MutableMap<FuelGrade, Double> {
        data class Hit(val grade: FuelGrade, val start: Int, val end: Int)
        val hits = grades.flatMap { (g, re) -> re.findAll(t).map { Hit(g, it.range.first, it.range.last + 1) } }
            .sortedBy { it.start }
        val out = LinkedHashMap<FuelGrade, Double>()
        for ((i, h) in hits.withIndex()) {
            if (h.grade in out) continue
            val windowEnd = minOf(hits.getOrNull(i + 1)?.start ?: t.length, h.end + 160)
            if (windowEnd <= h.end) continue
            val window = t.substring(h.end, windowEnd)
            val ft = fromTo.find(window)
            val first = PRICE.find(window)
            val value = when {
                ft != null && (first == null || ft.range.first <= first.range.first + 12) -> ft.groupValues[2]
                first != null -> first.groupValues[1]
                else -> null
            }?.toDoubleOrNull() ?: continue
            out[h.grade] = value
        }
        return out
    }

    // ---------------------------------------------------------------- remote feed JSON

    /** Current version of the remote price-feed schema. */
    const val SCHEMA_VERSION = 1

    /**
     * Remote price feed, schema v1:
     * ```
     * {
     *   "schemaVersion": 1,
     *   "currency": "AED",
     *   "unit": "litre",
     *   "prices": [
     *     {"month": "2026-11", "grade": "SUPER_98", "aedPerLitre": 4.16,
     *      "status": "VERIFIED", "sourceUrl": "https://www.wam.ae/..."}
     *   ]
     * }
     * ```
     * `grade` is a [FuelGrade] name, `status` a [PriceStatus] name (default VERIFIED). Unknown keys
     * are ignored so the feed can grow; a higher schemaVersion is rejected.
     */
    @Serializable
    data class PriceFeed(
        val schemaVersion: Int = SCHEMA_VERSION,
        val currency: String = "AED",
        val unit: String = "litre",
        val prices: List<PriceFeedEntry>,
    )

    @Serializable
    data class PriceFeedEntry(
        val month: String,
        val grade: FuelGrade,
        @SerialName("aedPerLitre") val aedPerLitre: Double,
        val status: PriceStatus = PriceStatus.VERIFIED,
        val sourceUrl: String? = null,
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }

    fun toJson(prices: List<FuelPrice>): String = json.encodeToString(
        PriceFeed.serializer(),
        PriceFeed(prices = prices.map { PriceFeedEntry(it.month.toString(), it.grade, it.aedPerLitre, it.status, it.sourceUrl) }),
    )

    /** @throws IllegalArgumentException on unsupported schema version, bad currency, or implausible values. */
    fun fromJson(json: String): List<FuelPrice> {
        val feed = this.json.decodeFromString(PriceFeed.serializer(), json)
        require(feed.schemaVersion in 1..SCHEMA_VERSION) { "Unsupported price feed schemaVersion ${feed.schemaVersion}" }
        require(feed.currency == "AED") { "Unsupported currency ${feed.currency}" }
        return feed.prices.map {
            require(it.aedPerLitre in MIN_PLAUSIBLE_AED..MAX_PLAUSIBLE_AED) { "Implausible price ${it.aedPerLitre} for ${it.month} ${it.grade}" }
            FuelPrice(YearMonth.parse(it.month), it.grade, it.aedPerLitre, it.status, it.sourceUrl)
        }
    }
}
