package ae.sayyara.core.uae

import ae.sayyara.core.model.FuelGrade
import ae.sayyara.core.model.FuelPrice
import ae.sayyara.core.model.PriceStatus
import ae.sayyara.core.model.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class UaeFuelPricesTest {
    private val nov = YearMonth(2026, 11)

    private fun List<FuelPrice>.of(g: FuelGrade) = first { it.grade == g }.aedPerLitre

    @Test
    fun parsesEnglishNewsText() {
        val text = """
            UAE petrol, diesel prices for November 2026 announced.
            The Super 98 petrol will cost Dh4.16 per litre, compared to Dh4.40 in October.
            The Special 95 petrol will cost Dh4.04 a litre, compared to Dh4.28 last month.
            The E-Plus 91 category will cost Dh3.97 a litre, compared to Dh4.21 in October.
            Diesel will be charged Dh4.75 per litre, compared to the current Dh4.80.
        """.trimIndent()
        val p = UaeFuelPrices.parseAnnouncement(text, nov)!!
        assertEquals(4, p.size)
        assertEquals(4.16, p.of(FuelGrade.SUPER_98))
        assertEquals(4.04, p.of(FuelGrade.SPECIAL_95))
        assertEquals(3.97, p.of(FuelGrade.E_PLUS_91))
        assertEquals(4.75, p.of(FuelGrade.DIESEL))
        assertTrue(p.all { it.month == nov && it.status == PriceStatus.PROVISIONAL })
    }

    @Test
    fun parsesWamStyleAndFromTo() {
        val text = "The price of Super 98 rose from AED 3.80 to AED 4.40 per litre, Special 95 from AED 3.69 to AED 4.28, " +
            "E-Plus 91 AED 4.21 per litre, and diesel AED 4.80."
        val p = UaeFuelPrices.parseAnnouncement(text, YearMonth(2026, 10))!!
        assertEquals(4.40, p.of(FuelGrade.SUPER_98))
        assertEquals(4.28, p.of(FuelGrade.SPECIAL_95))
        assertEquals(4.21, p.of(FuelGrade.E_PLUS_91))
        assertEquals(4.80, p.of(FuelGrade.DIESEL))
    }

    @Test
    fun parsesArabicWithArabicIndicDigits() {
        val text = "حددت لجنة متابعة أسعار الوقود أسعار شهر نوفمبر: سعر لتر بنزين سوبر 98 بسعر ٤٫١٦ درهم، " +
            "وسعر لتر بنزين سبيشال 95 بسعر 4.04 درهم، وسعر لتر بنزين إي بلس 91 بسعر 3.97 درهم، وسعر لتر الديزل 4.75 درهم."
        val p = UaeFuelPrices.parseAnnouncement(text, nov)!!
        assertEquals(4.16, p.of(FuelGrade.SUPER_98))
        assertEquals(4.04, p.of(FuelGrade.SPECIAL_95))
        assertEquals(3.97, p.of(FuelGrade.E_PLUS_91))
        assertEquals(4.75, p.of(FuelGrade.DIESEL))
    }

    @Test
    fun partialAnnouncementKeepsOnlyFoundGrades() {
        val p = UaeFuelPrices.parseAnnouncement("Super 98 fell 24 fils to Dh4.16 while diesel costs Dh4.75 per litre.", nov)!!
        assertEquals(setOf(FuelGrade.SUPER_98, FuelGrade.DIESEL), p.map { it.grade }.toSet())
        assertEquals(4.16, p.of(FuelGrade.SUPER_98))
    }

    @Test
    fun rejectsBadOrderingRangeOrNoMatch() {
        assertNull(UaeFuelPrices.parseAnnouncement("Super 98 Dh3.90, Special 95 Dh4.04, E-Plus 91 Dh3.97", nov))
        assertNull(UaeFuelPrices.parseAnnouncement("Super 98 Dh41.60 per litre", nov))
        assertNull(UaeFuelPrices.parseAnnouncement("Super 98 Dh0.20 per litre", nov))
        assertNull(UaeFuelPrices.parseAnnouncement("Gold prices rose today to Dh480.50", nov))
    }

    @Test
    fun seedShapeAndGaps() {
        val seed = UaeFuelPrices.seed
        assertEquals(YearMonth(2025, 11), seed.minOf { it.month })
        assertEquals(YearMonth(2026, 11), seed.maxOf { it.month })
        assertTrue(seed.none { it.month == YearMonth(2026, 6) && it.grade == FuelGrade.SPECIAL_95 })
        assertTrue(seed.filter { it.month >= YearMonth(2026, 4) }.all { it.status == PriceStatus.PROVISIONAL })
        assertTrue(seed.filter { it.month < YearMonth(2026, 4) }.all { it.status == PriceStatus.VERIFIED })
        assertEquals(13 * 4 - 5, seed.size)
    }

    @Test
    fun jsonRoundTripAndVersionCheck() {
        val json = UaeFuelPrices.toJson(UaeFuelPrices.seed)
        assertTrue(json.contains("\"schemaVersion\":1"))
        assertEquals(UaeFuelPrices.seed, UaeFuelPrices.fromJson(json))
        val minimal = """{"schemaVersion":1,"prices":[{"month":"2026-12","grade":"DIESEL","aedPerLitre":4.5,"extra":1}],"x":true}"""
        assertEquals(listOf(FuelPrice(YearMonth(2026, 12), FuelGrade.DIESEL, 4.5)), UaeFuelPrices.fromJson(minimal))
        assertFailsWith<IllegalArgumentException> { UaeFuelPrices.fromJson("""{"schemaVersion":2,"prices":[]}""") }
        assertFailsWith<IllegalArgumentException> {
            UaeFuelPrices.fromJson("""{"schemaVersion":1,"prices":[{"month":"2026-12","grade":"DIESEL","aedPerLitre":45.0}]}""")
        }
    }
}

class FuelPriceBookTest {
    private val now = Instant.parse("2026-11-05T10:00:00Z")
    private val book = FuelPriceBook(UaeFuelPrices.seed, now = { now })

    @Test
    fun knownMonthIsFinal() {
        val q = book.quote(YearMonth(2026, 11), FuelGrade.SUPER_98)!!
        assertEquals(QuoteStatus.FINAL, q.status)
        assertEquals(4.16, q.aedPerLitre)
    }

    @Test
    fun nextMonthFallsBackToLatestAsProvisional() {
        val q = book.quote(YearMonth(2026, 12), FuelGrade.DIESEL)!!
        assertEquals(QuoteStatus.PROVISIONAL, q.status)
        assertEquals(4.75, q.aedPerLitre)
        assertEquals(YearMonth(2026, 12), q.month)
    }

    @Test
    fun laterThanNextMonthIsForecast() {
        val q = book.quote(YearMonth(2027, 1), FuelGrade.SPECIAL_95)!!
        assertEquals(QuoteStatus.FORECAST, q.status)
        assertEquals(4.04, q.aedPerLitre)
    }

    @Test
    fun beforeEarliestIsNullAndUnknownGradeIsNull() {
        assertNull(book.quote(YearMonth(2025, 10), FuelGrade.SUPER_98))
        assertNull(book.quote(YearMonth(2026, 11), FuelGrade.ELECTRIC))
    }

    @Test
    fun gapMonthUsesPreviousKnownAsProvisional() {
        val q = book.quote(YearMonth(2026, 6), FuelGrade.SPECIAL_95)!!
        assertEquals(QuoteStatus.PROVISIONAL, q.status)
        assertEquals(3.28, q.aedPerLitre) // April, last SP95 before June
    }

    @Test
    fun latestAndOverrideAndMaxMove() {
        assertEquals(YearMonth(2026, 11), book.latest(FuelGrade.E_PLUS_91)!!.month)
        val updated = book.plus(listOf(FuelPrice(YearMonth(2026, 12), FuelGrade.DIESEL, 4.50)))
        assertEquals(QuoteStatus.FINAL, updated.quote(YearMonth(2026, 12), FuelGrade.DIESEL)!!.status)
        assertEquals(1.97, book.maxMonthlyMove(FuelGrade.DIESEL), 1e-9)
        assertEquals(0.80, book.maxMonthlyMove(FuelGrade.SPECIAL_95), 1e-9)
    }
}
