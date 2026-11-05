package ae.sayyara.core.reminders

import ae.sayyara.core.model.Reminder
import ae.sayyara.core.model.ReminderKind
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReminderPlannerTest {
    private val today = LocalDate(2026, 11, 5)
    private fun r(kind: ReminderKind = ReminderKind.SERVICE, date: LocalDate? = null, km: Double? = null,
                  months: Int? = null, repeatKm: Double? = null, done: Boolean = false) =
        Reminder("r1", "v1", kind, "t", dueDate = date, dueOdometerKm = km, repeatMonths = months, repeatKm = repeatKm, done = done)

    @Test
    fun dueByDateOnly() {
        val d = ReminderPlanner.due(r(date = LocalDate(2026, 12, 5)), today, null, null)
        assertEquals(30, d.daysLeft); assertEquals(LocalDate(2026, 12, 5), d.dueDate); assertNull(d.kmLeft); assertFalse(d.overdue)
        assertEquals("r1", d.reminderId)
    }

    @Test
    fun kmComesFirstWhenProjectedEarlier() {
        // 1,000 km left at 50 km/day -> 20 days, earlier than the date in 30 days
        val d = ReminderPlanner.due(r(date = LocalDate(2026, 12, 5), km = 60_000.0), today, 59_000.0, 50.0)
        assertEquals(1000.0, d.kmLeft); assertEquals(20, d.daysLeft); assertEquals(LocalDate(2026, 11, 25), d.dueDate)
        // partial day rounds up
        assertEquals(21, ReminderPlanner.due(r(km = 60_000.0), today, 59_000.0, 49.0).daysLeft)
        // date first when km is far
        assertEquals(30, ReminderPlanner.due(r(date = LocalDate(2026, 12, 5), km = 70_000.0), today, 59_000.0, 50.0).daysLeft)
        // no kmPerDay: km left only, date unchanged
        val n = ReminderPlanner.due(r(km = 60_000.0), today, 59_000.0, null)
        assertNull(n.dueDate); assertNull(n.daysLeft); assertEquals(1000.0, n.kmLeft)
        // no odometer: nothing about km
        assertNull(ReminderPlanner.due(r(km = 60_000.0), today, null, 50.0).kmLeft)
    }

    @Test
    fun overdue() {
        val byDate = ReminderPlanner.due(r(date = LocalDate(2026, 11, 1)), today, null, null)
        assertTrue(byDate.overdue); assertEquals(-4, byDate.daysLeft)
        assertFalse(ReminderPlanner.due(r(date = today), today, null, null).overdue) // due today is not overdue
        val byKm = ReminderPlanner.due(r(km = 60_000.0), today, 60_250.0, 40.0)
        assertTrue(byKm.overdue); assertEquals(-250.0, byKm.kmLeft); assertEquals(0, byKm.daysLeft)
        assertFalse(ReminderPlanner.due(r(date = LocalDate(2026, 1, 1), done = true), today, null, null).overdue)
    }

    @Test
    fun notifyOnUsesLeadDays() {
        val rem = r(date = LocalDate(2026, 12, 5)).copy(leadDays = 30)
        assertEquals(LocalDate(2026, 11, 5), ReminderPlanner.notifyOn(rem, ReminderPlanner.due(rem, today, null, null)))
    }

    @Test
    fun nextRepeatsWithMonthEndClamping() {
        val oil = r(date = LocalDate(2026, 1, 31), km = 50_000.0, months = 1, repeatKm = 5_000.0, done = true)
        val n = ReminderPlanner.next(oil, LocalDate(2026, 1, 31), 49_800.0)!!
        assertEquals(LocalDate(2026, 2, 28), n.dueDate)
        assertEquals(54_800.0, n.dueOdometerKm)
        assertFalse(n.done); assertEquals("r1", n.id)
        assertEquals(LocalDate(2028, 2, 29), ReminderPlanner.addMonthsClamped(LocalDate(2028, 1, 31), 1))
        assertEquals(LocalDate(2027, 1, 31), ReminderPlanner.addMonthsClamped(LocalDate(2026, 10, 31), 3))
        assertEquals(LocalDate(2025, 11, 30), ReminderPlanner.addMonthsClamped(LocalDate(2026, 5, 30), -6))
        // service: repeats from completion date
        val svc = r(date = LocalDate(2026, 6, 1), months = 6)
        assertEquals(LocalDate(2027, 2, 10), ReminderPlanner.next(svc, LocalDate(2026, 8, 10), null)!!.dueDate)
        // km repeat with no completion odo: from the old target
        assertEquals(15_000.0, ReminderPlanner.next(r(km = 10_000.0, repeatKm = 5_000.0), today, null)!!.dueOdometerKm)
        assertNull(ReminderPlanner.next(r(km = 10_000.0, repeatKm = 5_000.0), today, null)!!.dueDate)
    }

    @Test
    fun nextOneOffIsNull() {
        assertNull(ReminderPlanner.next(r(date = today), today, 1000.0))
    }

    @Test
    fun registrationRenewsFromAnniversaryWhenEarly() {
        val m = r(kind = ReminderKind.MULKIYA, date = LocalDate(2027, 3, 15), months = 12)
        assertEquals(LocalDate(2028, 3, 15), ReminderPlanner.next(m, LocalDate(2027, 3, 1), null)!!.dueDate)
        // renewed late: from the completion date
        assertEquals(LocalDate(2028, 4, 2), ReminderPlanner.next(m, LocalDate(2027, 4, 2), null)!!.dueDate)
    }

    @Test
    fun mulkiyaChainOldCar() {
        var n = 0
        val chain = ReminderPlanner.mulkiyaChain("v1", LocalDate(2027, 3, 15), 2018, today) { "id${n++}" }
        assertEquals(listOf(ReminderKind.INSPECTION, ReminderKind.INSURANCE, ReminderKind.MULKIYA), chain.map { it.kind })
        val (insp, ins, mul) = chain
        assertEquals(LocalDate(2027, 2, 13), insp.dueDate)
        assertEquals(LocalDate(2027, 2, 22), ins.dueDate)
        assertEquals(LocalDate(2027, 3, 15), mul.dueDate)
        assertEquals(ins.id, insp.chainedToId)
        assertEquals(mul.id, ins.chainedToId)
        assertNull(mul.chainedToId)
        assertEquals(3, chain.map { it.id }.toSet().size)
        assertTrue(chain.all { it.vehicleId == "v1" && it.repeatMonths == 12 && !it.done })
        assertTrue(mul.title.contains("Mulkiya")); assertTrue(insp.title.contains("inspection", ignoreCase = true))
        assertEquals(30, mul.leadDays)
    }

    @Test
    fun mulkiyaChainNewCarSkipsInspectionUnknownYearIncludes() {
        val newCar = ReminderPlanner.mulkiyaChain("v1", LocalDate(2027, 3, 15), 2025, today) { "new" }
        assertEquals(listOf(ReminderKind.INSURANCE, ReminderKind.MULKIYA), newCar.map { it.kind })
        var k = 0
        val unknown = ReminderPlanner.mulkiyaChain("v1", LocalDate(2027, 3, 15), null, today) { "u${k++}" }
        assertEquals(3, unknown.size)
    }

    @Test
    fun mulkiyaChainNearExpiryClampsStepsToToday() {
        var k = 0
        val chain = ReminderPlanner.mulkiyaChain("v1", LocalDate(2026, 11, 20), 2015, today) { "c${k++}" }
        assertEquals(today, chain[0].dueDate) // expiry−30 already passed
        assertEquals(today, chain[1].dueDate) // expiry−21 already passed
        assertEquals(LocalDate(2026, 11, 20), chain[2].dueDate)
        assertTrue(chain.none { ReminderPlanner.due(it, today, null, null).overdue })
    }

}
