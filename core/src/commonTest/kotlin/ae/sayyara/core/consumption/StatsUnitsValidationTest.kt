package ae.sayyara.core.consumption

import ae.sayyara.core.model.Expense
import ae.sayyara.core.model.ExpenseCategoryKey
import ae.sayyara.core.model.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

class StatsTest {
    private fun expense(id: String, at: String, cat: ExpenseCategoryKey, aed: Double, income: Boolean = false) =
        Expense(id, "car", Instant.parse(at), category = cat, amountAed = aed, isIncome = income, updatedAt = Instant.parse(at))

    @Test
    fun monthlyProratesSegmentFuelByDistanceInsideMonth() {
        val events = listOf(
            fill("e1", 0.0, 50.0, at = Instant.parse("2026-01-21T00:00:00Z"), totalAed = 130.0),
            fill("e2", 2000.0, 140.0, at = Instant.parse("2026-02-10T00:00:00Z"), totalAed = 364.0),
        )
        val expenses = listOf(
            expense("x1", "2026-01-25T10:00:00Z", ExpenseCategoryKey.PARKING, 10.0),
            expense("x2", "2026-02-02T10:00:00Z", ExpenseCategoryKey.SALIK, 4.0),
            expense("x3", "2026-02-03T10:00:00Z", ExpenseCategoryKey.SALIK, 4.0),
            expense("x4", "2026-04-03T10:00:00Z", ExpenseCategoryKey.SERVICE, 500.0),
            expense("x5", "2026-04-04T10:00:00Z", ExpenseCategoryKey.OTHER, 100.0, income = true),
            expense("gone", "2026-02-03T10:00:00Z", ExpenseCategoryKey.FINE, 400.0).let { it.copy(deletedAt = it.updatedAt) },
        )
        val stats = Stats.monthly(events, expenses, TimeZone.UTC)
        assertEquals(listOf(YearMonth(2026, 1), YearMonth(2026, 2), YearMonth(2026, 3), YearMonth(2026, 4)), stats.map { it.month })

        val jan = stats[0]
        val feb = stats[1]
        // 11 of 20 days in January.
        assertNear(1100.0, jan.km)
        assertNear(900.0, feb.km)
        assertNear(77.0, jan.fuelAmount)
        assertNear(63.0, feb.fuelAmount)
        assertNear(7.0, jan.per100km)
        assertNear(7.0, feb.per100km)
        assertNear(130.0, jan.fuelAed)
        assertNear(364.0, feb.fuelAed)
        assertEquals(mapOf(ExpenseCategoryKey.PARKING to 10.0), jan.expensesAed)
        assertEquals(mapOf(ExpenseCategoryKey.SALIK to 8.0), feb.expensesAed)
        assertNear(140.0, jan.totalAed)
        assertNear(140.0 / 1100.0, jan.costPerKmAed)
        assertNear(372.0, feb.totalAed)

        val mar = stats[2]
        assertEquals(0.0, mar.km)
        assertNull(mar.per100km)
        assertNull(mar.costPerKmAed)
        val apr = stats[3]
        assertNear(400.0, apr.totalAed)
        assertNear(-100.0, apr.expensesAed[ExpenseCategoryKey.OTHER])
    }

    @Test
    fun brokenChainDistanceCountsButNotConsumption() {
        val events = listOf(
            fill("a", 0.0, 50.0, at = Instant.parse("2026-03-01T10:00:00Z")),
            fill("b", 500.0, 40.0, at = Instant.parse("2026-03-10T10:00:00Z")),
            fill("c", 1000.0, 30.0, missed = true, at = Instant.parse("2026-04-10T10:00:00Z")),
            fill("d", 1500.0, 35.0, at = Instant.parse("2026-04-20T10:00:00Z")),
        )
        val stats = Stats.monthly(events, emptyList(), TimeZone.UTC)
        assertNear(1500.0, stats.sumOf { it.km })
        assertNear(75.0, stats.sumOf { it.fuelAmount })
        assertNear(8.0, stats[0].per100km)
        assertNear(7.0, stats[1].per100km)
    }

    @Test
    fun monthBoundariesUseZone() {
        val dubai = TimeZone.of("Asia/Dubai")
        // 2026-01-31T22:00Z is 2026-02-01 02:00 in Dubai.
        val e = fill("a", 0.0, 40.0, at = Instant.parse("2026-01-31T22:00:00Z"), totalAed = 100.0)
        assertEquals(YearMonth(2026, 2), Stats.monthly(listOf(e), emptyList(), dubai).single().month)
        assertEquals(YearMonth(2026, 1), Stats.monthly(listOf(e), emptyList(), TimeZone.UTC).single().month)
        assertTrue(Stats.monthly(emptyList(), emptyList(), dubai).isEmpty())
    }
}

class UnitsTest {
    @Test
    fun conversions() {
        assertNear(7.0, Units.fromPer100km(7.0, EconomyUnit.L_PER_100KM))
        assertNear(100.0 / 7.0, Units.fromPer100km(7.0, EconomyUnit.KM_PER_L))
        assertNear(33.6021, Units.fromPer100km(7.0, EconomyUnit.MPG_US), 1e-3)
        assertNear(40.3544, Units.fromPer100km(7.0, EconomyUnit.MPG_UK), 1e-3)
        assertNear(15.0, Units.fromPer100km(15.0, EconomyUnit.KWH_PER_100KM))
        assertNear(100.0 / 15.0, Units.fromPer100km(15.0, EconomyUnit.KM_PER_KWH))
        assertNear(7.0, Units.toPer100km(Units.fromPer100km(7.0, EconomyUnit.MPG_US), EconomyUnit.MPG_US))
        assertTrue(Units.fromPer100km(0.0, EconomyUnit.KM_PER_L).isNaN())
    }

    @Test
    fun formatting() {
        assertEquals("7.5 L/100km", Units.format(7.46, EconomyUnit.L_PER_100KM))
        assertEquals("14.3 km/L", Units.format(7.0, EconomyUnit.KM_PER_L))
        assertEquals("33.6 mpg (US)", Units.format(7.0, EconomyUnit.MPG_US))
        assertEquals("40.4 mpg (UK)", Units.format(7.0, EconomyUnit.MPG_UK))
        assertEquals("15.0 kWh/100km", Units.format(15.0, EconomyUnit.KWH_PER_100KM))
        assertEquals("6.67 km/kWh", Units.format(15.0, EconomyUnit.KM_PER_KWH))
        assertEquals("— km/L", Units.format(0.0, EconomyUnit.KM_PER_L))
        assertEquals("-1.5", Units.formatNumber(-1.46, 1))
        assertEquals("0.0", Units.formatNumber(-0.01, 1))
        assertEquals("1235", Units.formatNumber(1234.5, 0))
    }
}

class FillValidationTest {
    private val history = listOf(
        fill("h1", 1000.0, 50.0),
        fill("h2", 1500.0, 40.0),
        fill("h3", 2000.0, 42.0),
        fill("h4", 2500.0, 45.0),
    )

    @Test
    fun cleanFillHasNoWarnings() {
        val c = fill("c", 3000.0, 40.0, unitPrice = 2.58, totalAed = 103.2)
        assertTrue(FillValidation.check(c, history, TANK_60).isEmpty())
    }

    @Test
    fun odometerNotIncreasing() {
        val c = fill("c", 2400.0, 40.0, day = 40)
        assertTrue(FillValidation.check(c, history, TANK_60).any { it.code == WarningCodes.ODO_NOT_INCREASING })
        // Back-dated entry above the next one by date.
        val back = fill("c", 1600.0, 40.0, day = 12)
        assertTrue(FillValidation.check(back, history, TANK_60).any { it.code == WarningCodes.ODO_NOT_INCREASING })
        // Editing an existing entry does not compare with its old self.
        val edit = history[3].copy(amount = 44.0)
        assertTrue(FillValidation.check(edit, history, TANK_60).isEmpty())
    }

    @Test
    fun didYouMissAFill() {
        val far = fill("c", 6000.0, 55.0, day = 60)
        val w = FillValidation.check(far, history, TANK_60)
        assertTrue(w.any { it.code == WarningCodes.POSSIBLE_MISSED_FILL }, w.toString())
        // Marking it as missed silences the prompt.
        assertTrue(FillValidation.check(far.copy(missedPrevious = true), history, TANK_60).isEmpty())
    }

    @Test
    fun implausibleValues() {
        val w = FillValidation.check(fill("c", 2600.0, 59.0, unitPrice = 25.8, totalAed = 50.0), history, TANK_60)
        val codes = w.map { it.code }.toSet()
        assertTrue(WarningCodes.IMPLAUSIBLE_CONSUMPTION in codes, codes.toString())
        assertTrue(WarningCodes.IMPLAUSIBLE_PRICE in codes)
        assertTrue(WarningCodes.TOTAL_MISMATCH in codes)
        val big = FillValidation.check(fill("c", 3000.0, 70.0), history, TANK_60).map { it.code }
        assertTrue(WarningCodes.AMOUNT_EXCEEDS_CAPACITY in big)
        val zero = FillValidation.check(fill("c", 3000.0, 0.0), history, TANK_60).map { it.code }
        assertTrue(WarningCodes.AMOUNT_NOT_POSITIVE in zero)
        val lvl = FillValidation.check(fill("c", 3000.0, 30.0, isFull = false, levelBefore = 0.8, levelAfter = 0.3), history, TANK_60)
        assertTrue(lvl.any { it.code == WarningCodes.LEVEL_OUT_OF_RANGE })
    }

    @Test
    fun suggestPartialOnRoundAedAmounts() {
        assertTrue(FillValidation.suggestPartial(fill("c", 3000.0, 19.38, totalAed = 50.0), history, TANK_60))
        assertTrue(FillValidation.suggestPartial(fill("c", 3000.0, 38.76, totalAed = 100.0), history, TANK_60))
        assertTrue(FillValidation.suggestPartial(fill("c", 3000.0, 58.14, totalAed = 150.0), history, TANK_60))
        // Derived from litres x price: 38.76 x 2.58 = 100.0008 -> rounds to AED 100.
        assertTrue(FillValidation.suggestPartial(fill("c", 3000.0, 38.76, unitPrice = 2.58), history, TANK_60))
        assertFalse(FillValidation.suggestPartial(fill("c", 3000.0, 41.2, totalAed = 106.3), history, TANK_60))
        assertFalse(FillValidation.suggestPartial(fill("c", 3000.0, 40.0, totalAed = 120.0), history, TANK_60))
        // Already partial: nothing to suggest.
        assertFalse(FillValidation.suggestPartial(fill("c", 3000.0, 19.38, totalAed = 50.0, isFull = false), history, TANK_60))
    }

    @Test
    fun suggestPartialOnSmallVolumeOrLowGauge() {
        // Median full fill = 43.5 L; 15 L is far below.
        assertTrue(FillValidation.suggestPartial(fill("c", 3000.0, 15.0, totalAed = 38.7), history, TANK_60))
        assertTrue(FillValidation.suggestPartial(fill("c", 3000.0, 30.0, totalAed = 77.4, levelAfter = 0.6), history, TANK_60))
        // Not enough history for a median.
        assertFalse(FillValidation.suggestPartial(fill("c", 3000.0, 15.0, totalAed = 38.7), history.take(2), TANK_60))
    }
}
