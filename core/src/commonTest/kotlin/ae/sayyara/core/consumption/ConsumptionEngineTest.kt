package ae.sayyara.core.consumption

import ae.sayyara.core.model.FuelGrade
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

class ConsumptionEngineTest {

    @Test
    fun fullToFullIsDistanceWeightedAndExcludesFirstFill() {
        val r = ConsumptionEngine.compute(listOf(
            fill("a", 1000.0, 55.0),
            fill("b", 1500.0, 40.0),
            fill("c", 2100.0, 42.0),
        ))
        assertEquals(2, r.segments.size)
        assertNear(8.0, r.segments[0].per100km)
        assertNear(7.0, r.segments[1].per100km)
        assertTrue(r.segments.all { it.method == SegmentMethod.FULL_TO_FULL && it.valid })
        // 100 * (40 + 42) / 1100, not the mean of 8.0 and 7.0.
        assertNear(100.0 * 82.0 / 1100.0, r.averagePer100km)
        assertNear(7.0, r.lastPer100km)
        assertNear(1100.0, r.totalKm)
        assertNear(137.0, r.totalAmount)
        assertTrue(r.pendingPartialEventIds.isEmpty())
        assertTrue(r.warnings.isEmpty(), r.warnings.toString())
    }

    @Test
    fun partialsAccumulateIntoNextFullSegment() {
        val events = listOf(
            fill("f1", 1000.0, 50.0),
            fill("p1", 1200.0, 10.0, isFull = false),
            fill("p2", 1400.0, 15.0, isFull = false),
            fill("f2", 1600.0, 20.0),
        )
        val r = ConsumptionEngine.compute(events)
        assertEquals(1, r.segments.size)
        val s = r.segments.single()
        assertEquals("f1", s.startEventId)
        assertEquals("f2", s.endEventId)
        assertNear(600.0, s.km)
        assertNear(45.0, s.amount)
        assertNear(7.5, s.per100km)
        assertNear(7.5, r.averagePer100km)
        assertTrue(r.pendingPartialEventIds.isEmpty())
    }

    @Test
    fun partialsAfterLastFullArePending() {
        val r = ConsumptionEngine.compute(listOf(
            fill("f1", 1000.0, 50.0),
            fill("p1", 1200.0, 10.0, isFull = false),
            fill("p2", 1400.0, 15.0, isFull = false),
        ))
        assertTrue(r.segments.isEmpty())
        assertNull(r.averagePer100km)
        assertNull(r.lastPer100km)
        assertEquals(setOf("p1", "p2"), r.pendingPartialEventIds)
    }

    @Test
    fun partialsBeforeFirstFullAreNotCountedOrPending() {
        val r = ConsumptionEngine.compute(listOf(
            fill("p0", 800.0, 20.0, isFull = false),
            fill("f1", 1000.0, 50.0),
            fill("f2", 1500.0, 35.0),
        ))
        assertEquals(1, r.segments.size)
        assertNear(7.0, r.averagePer100km)
        assertTrue(r.pendingPartialEventIds.isEmpty())
        assertNear(700.0, r.totalKm)
    }

    @Test
    fun missedFillBreaksChainButDistanceStillCounts() {
        val r = ConsumptionEngine.compute(listOf(
            fill("a", 0.0, 50.0, totalAed = 100.0),
            fill("b", 500.0, 40.0, totalAed = 100.0),
            fill("c", 1000.0, 30.0, missed = true, totalAed = 100.0),
            fill("d", 1500.0, 35.0, totalAed = 100.0),
        ))
        assertEquals(listOf("a" to "b", "c" to "d"), r.segments.map { it.startEventId to it.endEventId })
        assertNear(100.0 * 75.0 / 1000.0, r.averagePer100km)
        assertNear(1500.0, r.totalKm)
        // Cost per km uses all distance; first fill's cost excluded.
        assertNear(300.0 / 1500.0, r.fuelCostPerKmAed)
        assertNear(400.0, r.totalFuelCostAed)
    }

    @Test
    fun missedFlagOnPartialRestartsAtNextFull() {
        val r = ConsumptionEngine.compute(listOf(
            fill("a", 0.0, 50.0),
            fill("p", 300.0, 10.0, isFull = false, missed = true),
            fill("b", 600.0, 30.0),
            fill("c", 1000.0, 32.0),
        ))
        assertEquals(listOf("b" to "c"), r.segments.map { it.startEventId to it.endEventId })
        assertNear(8.0, r.averagePer100km)
        assertTrue(r.pendingPartialEventIds.isEmpty())
    }

    @Test
    fun missedFlagDropsPendingPartials() {
        val r = ConsumptionEngine.compute(listOf(
            fill("a", 0.0, 50.0),
            fill("p", 300.0, 10.0, isFull = false),
            fill("b", 600.0, 30.0, missed = true),
        ))
        assertTrue(r.segments.isEmpty())
        assertTrue(r.pendingPartialEventIds.isEmpty())
    }

    @Test
    fun singleFillAndEmpty() {
        val one = ConsumptionEngine.compute(listOf(fill("a", 1000.0, 40.0, totalAed = 90.0)))
        assertTrue(one.segments.isEmpty())
        assertNull(one.averagePer100km)
        assertEquals(0.0, one.totalKm)
        assertNull(one.fuelCostPerKmAed)
        assertNear(90.0, one.totalFuelCostAed)
        assertNear(40.0, one.totalAmount)

        val none = ConsumptionEngine.compute(emptyList())
        assertTrue(none.segments.isEmpty())
        assertNull(none.averagePer100km)
        assertEquals(0.0, none.totalAmount)
    }

    @Test
    fun inputOrderDoesNotMatterAndSoftDeletesAreIgnored() {
        val events = listOf(
            fill("f1", 1000.0, 50.0),
            fill("p1", 1200.0, 10.0, isFull = false),
            fill("f2", 1600.0, 35.0),
            fill("f3", 2000.0, 30.0),
        )
        val base = ConsumptionEngine.compute(events)
        assertEquals(base, ConsumptionEngine.compute(events.reversed()))
        assertEquals(base, ConsumptionEngine.compute(listOf(events[2], events[0], events[3], events[1])))

        // Deleting the partial changes the first segment (edit = recompute from raw data).
        val edited = events.map { if (it.id == "p1") it.copy(deletedAt = it.updatedAt) else it }
        val r = ConsumptionEngine.compute(edited)
        assertNear(35.0, r.segments[0].amount)
        assertNear(45.0, base.segments[0].amount)

        // Back-dated insert lands in the right place by odometer.
        val inserted = ConsumptionEngine.compute(events + fill("f15", 1400.0, 20.0, day = 1))
        assertEquals(listOf("f1" to "f15", "f15" to "f2", "f2" to "f3"),
            inserted.segments.map { it.startEventId to it.endEventId })
        assertTrue(inserted.warnings.any { it.code == WarningCodes.ODO_DATE_MISMATCH && it.eventId == "f15" })
    }

    @Test
    fun storeFilterIgnoresOtherStores() {
        val r = ConsumptionEngine.compute(listOf(
            fill("a", 0.0, 50.0),
            fill("x", 200.0, 99.0, storeId = "other"),
            fill("b", 500.0, 40.0),
        ), TANK_60)
        assertEquals(1, r.segments.size)
        assertNear(8.0, r.averagePer100km)
    }

    @Test
    fun equalOdometerIsFlaggedAndExcluded() {
        val r = ConsumptionEngine.compute(listOf(
            fill("a", 1000.0, 50.0, at = T0),
            fill("b", 1000.0, 5.0, at = T0 + 1.hours),
            fill("c", 1500.0, 40.0, at = T0 + 48.hours),
        ))
        assertTrue(r.warnings.any { it.code == WarningCodes.ODO_NOT_INCREASING && it.eventId == "b" })
        assertFalse(r.segments.first { it.endEventId == "b" }.valid)
        assertNear(8.0, r.averagePer100km)
    }

    @Test
    fun implausibleAndMissedFillWarnings() {
        // 60 L over 100 km = 60 L/100km: implausible, excluded.
        val high = ConsumptionEngine.compute(listOf(fill("a", 0.0, 40.0), fill("b", 100.0, 60.0), fill("c", 600.0, 40.0)))
        assertFalse(high.segments[0].valid)
        assertTrue(high.warnings.any { it.code == WarningCodes.IMPLAUSIBLE_CONSUMPTION && it.eventId == "b" })
        assertNear(8.0, high.averagePer100km)

        // 20 L over 2000 km = 1 L/100km: likely missed fill.
        val low = ConsumptionEngine.compute(listOf(fill("a", 0.0, 40.0), fill("b", 2000.0, 20.0)))
        assertFalse(low.segments[0].valid)
        assertTrue(low.warnings.any { it.code == WarningCodes.POSSIBLE_MISSED_FILL })
        assertNull(low.averagePer100km)

        // 60 L tank cannot cover 3500 km (max 3000 km at 2 L/100km), even with plausible litres.
        val range = ConsumptionEngine.compute(listOf(fill("a", 0.0, 40.0), fill("b", 3500.0, 59.0, isFull = false),
            fill("c", 4000.0, 45.0)), TANK_60)
        assertTrue(range.warnings.any { it.code == WarningCodes.POSSIBLE_MISSED_FILL && it.eventId == "b" })
        assertFalse(range.segments.single { it.method == SegmentMethod.FULL_TO_FULL }.valid)

        // An explicitly flagged missed fill does not warn again.
        val flagged = ConsumptionEngine.compute(listOf(fill("a", 0.0, 40.0), fill("b", 3500.0, 50.0, missed = true)), TANK_60)
        assertTrue(flagged.warnings.isEmpty(), flagged.warnings.toString())
    }

    @Test
    fun gaugeEstimatesWhenNoFullToFull() {
        val r = ConsumptionEngine.compute(listOf(
            fill("e1", 0.0, 30.0, isFull = false, levelBefore = 0.25, levelAfter = 0.75),
            fill("e2", 400.0, 24.0, isFull = false, levelBefore = 0.25, levelAfter = 0.65),
            fill("e3", 800.0, 30.0, isFull = false, levelBefore = 0.20, levelAfter = 0.70),
        ), TANK_60)
        assertEquals(2, r.segments.size)
        assertTrue(r.segments.all { it.method == SegmentMethod.GAUGE && it.valid })
        assertNear(30.0, r.segments[0].amount)
        assertNear(7.5, r.segments[0].per100km)
        assertNear(6.75, r.segments[1].per100km)
        assertNear(100.0 * 57.0 / 800.0, r.averagePer100km)
        assertEquals(SegmentMethod.GAUGE, r.averageMethod)
        assertTrue(r.warnings.isEmpty(), r.warnings.toString())
    }

    @Test
    fun gaugeSpansFillsWithoutReadingsAndNeedsCapacity() {
        val events = listOf(
            fill("e1", 0.0, 30.0, isFull = false, levelBefore = 0.25, levelAfter = 0.75),
            fill("e2", 300.0, 10.0, isFull = false),
            fill("e3", 600.0, 30.0, isFull = false, levelBefore = 0.30),
        )
        val r = ConsumptionEngine.compute(events, TANK_60)
        // used = 60 * (0.75 - 0.30) + 10 = 37 L over 600 km.
        val s = r.segments.single()
        assertEquals("e1" to "e3", s.startEventId to s.endEventId)
        assertNear(37.0, s.amount)
        // No store, no capacity: no gauge estimate.
        assertTrue(ConsumptionEngine.compute(events).segments.isEmpty())
    }

    @Test
    fun fullToFullWinsOverGaugeAndGaugeFillsPendingTail() {
        val r = ConsumptionEngine.compute(listOf(
            fill("a", 0.0, 50.0, levelAfter = 1.0),
            fill("b", 500.0, 40.0, levelBefore = 0.33, levelAfter = 1.0),
            fill("p", 900.0, 20.0, isFull = false, levelBefore = 0.30, levelAfter = 0.63),
        ), TANK_60)
        val ftf = r.segments.filter { it.method == SegmentMethod.FULL_TO_FULL }
        val gauge = r.segments.filter { it.method == SegmentMethod.GAUGE }
        assertEquals(1, ftf.size)
        assertEquals(listOf("b" to "p"), gauge.map { it.startEventId to it.endEventId })
        assertNear(42.0, gauge.single().amount)
        assertNear(8.0, r.averagePer100km)
        assertEquals(SegmentMethod.FULL_TO_FULL, r.averageMethod)
        assertEquals(setOf("p"), r.pendingPartialEventIds)
    }

    @Test
    fun gaugeResidualAsksToRecalibrate() {
        val r = ConsumptionEngine.compute(listOf(
            fill("a", 0.0, 40.0),
            fill("b", 400.0, 10.0, isFull = false, levelBefore = 0.2, levelAfter = 0.8),
        ), TANK_60)
        assertTrue(r.warnings.any { it.code == WarningCodes.GAUGE_RECALIBRATE && it.eventId == "b" })
    }

    @Test
    fun evStateOfCharge() {
        val events = listOf(
            fill("c1", 0.0, 50.0, socBefore = 0.2, socAfter = 0.8, grade = FuelGrade.ELECTRIC, storeId = "batt", totalAed = 0.0),
            fill("c2", 150.0, 33.0, socBefore = 0.5, socAfter = 0.9, grade = FuelGrade.ELECTRIC, storeId = "batt", unitPrice = 0.7),
            fill("c3", 270.0, 20.0, socBefore = 0.6, grade = FuelGrade.ELECTRIC, storeId = "batt", unitPrice = 0.7),
        )
        val r = ConsumptionEngine.compute(events, BATTERY_75)
        assertTrue(r.segments.all { it.method == SegmentMethod.SOC && it.valid })
        assertNear(22.5, r.segments[0].amount)
        assertNear(15.0, r.segments[0].per100km)
        assertNear(18.75, r.segments[1].per100km)
        assertNear(100.0 * 45.0 / 270.0, r.averagePer100km)
        assertNear(0.7 * 53.0, r.totalFuelCostAed)
        assertNear(75.0 / 83.0, ConsumptionEngine.learnedEfficiency(events, BATTERY_75))
        assertNear(20.0, ConsumptionEngine.wallToWheelPer100km(18.0, 0.9))
    }

    @Test
    fun evWithoutSocFallsBackToFullToFullOnWallKwh() {
        val r = ConsumptionEngine.compute(listOf(
            fill("c1", 0.0, 50.0, grade = FuelGrade.ELECTRIC),
            fill("c2", 200.0, 36.0, grade = FuelGrade.ELECTRIC),
            fill("c3", 400.0, 3.0, grade = FuelGrade.ELECTRIC), // 1.5 kWh/100km: below EV range
        ))
        assertNear(18.0, r.segments[0].per100km)
        assertTrue(r.segments[0].valid)
        assertFalse(r.segments[1].valid)
        assertNear(18.0, r.averagePer100km)
    }

    @Test
    fun costPrefersTotalThenAmountTimesPrice() {
        assertNear(100.0, fill("a", 0.0, 40.0, totalAed = 100.0, unitPrice = 2.0).costAed())
        assertNear(80.0, fill("a", 0.0, 40.0, unitPrice = 2.0).costAed())
        assertNear(0.0, fill("a", 0.0, 40.0).costAed())
        val r = ConsumptionEngine.compute(listOf(
            fill("a", 0.0, 40.0, unitPrice = 2.5),
            fill("b", 500.0, 40.0, totalAed = 110.0),
        ))
        assertNear(210.0, r.totalFuelCostAed)
        assertNear(110.0 / 500.0, r.fuelCostPerKmAed)
    }
}
