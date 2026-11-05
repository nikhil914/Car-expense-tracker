package ae.sayyara.core.reminders

import ae.sayyara.core.model.Id
import ae.sayyara.core.model.Reminder
import ae.sayyara.core.model.ReminderKind
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.math.ceil

/**
 * Reminder math: when is a reminder due (date or km, whichever comes first), what is the next occurrence of a
 * repeating one, and the UAE registration chain (inspection -> insurance -> Mulkiya renewal).
 */
object ReminderPlanner {
    /**
     * [dueDate] is the effective due date: the reminder's date or, if earlier, the date the km target is projected
     * to be reached at [kmPerDay]. [daysLeft] counts to that effective date (negative when past).
     */
    data class Due(val reminderId: Id, val dueDate: LocalDate?, val daysLeft: Int?, val kmLeft: Double?, val overdue: Boolean)

    /** Days before Mulkiya expiry that the inspection step is due (cars older than [INSPECTION_EXEMPT_YEARS]). */
    const val INSPECTION_DAYS_BEFORE = 30
    /** Days before Mulkiya expiry that the insurance renewal step is due (renewal needs 12 months' cover, 13 in Sharjah). */
    const val INSURANCE_DAYS_BEFORE = 21
    /** New cars are exempt from the renewal inspection for their first 3 years. */
    const val INSPECTION_EXEMPT_YEARS = 3

    const val INSPECTION_LEAD_DAYS = 14
    const val INSURANCE_LEAD_DAYS = 14
    const val MULKIYA_LEAD_DAYS = 30

    const val TITLE_INSPECTION = "Vehicle inspection before Mulkiya renewal (Tasjeel, Shamil or ADNOC centre)"
    const val TITLE_INSURANCE = "Renew car insurance (12 months' cover, 13 in Sharjah)"
    const val TITLE_MULKIYA = "Renew Mulkiya (vehicle registration); clear unpaid fines first"

    fun due(reminder: Reminder, today: LocalDate, odometerKm: Double?, kmPerDay: Double?): Due {
        val kmLeft = if (reminder.dueOdometerKm != null && odometerKm != null) reminder.dueOdometerKm - odometerKm else null
        val kmDate: LocalDate? = if (kmLeft != null && kmPerDay != null && kmPerDay > 0.0) {
            val days = if (kmLeft <= 0.0) 0 else ceil(kmLeft / kmPerDay).toInt()
            today.plus(DatePeriod(days = days))
        } else null
        val effective = listOfNotNull(reminder.dueDate, kmDate).minOrNull()
        val daysLeft = effective?.let { today.daysUntil(it) }
        val overdue = !reminder.done && ((reminder.dueDate != null && today > reminder.dueDate) || (kmLeft != null && kmLeft < 0.0))
        return Due(reminder.id, effective, daysLeft, kmLeft, overdue)
    }

    /** Date from which the reminder should notify: effective due date minus [Reminder.leadDays]. */
    fun notifyOn(reminder: Reminder, due: Due): LocalDate? = due.dueDate?.minus(DatePeriod(days = reminder.leadDays))

    /**
     * Next occurrence after completing a repeating reminder: same id, not done, dates and km moved on.
     * Returns null for a one-off reminder. Months are added with month-end clamping (31 Jan + 1 month = 28/29 Feb).
     * Registration-type reminders (Mulkiya, insurance, inspection) renew from their anniversary when done on time
     * or early; service-type reminders, and anything done late, repeat from the completion date.
     */
    fun next(reminder: Reminder, completedOn: LocalDate, completedOdoKm: Double?): Reminder? {
        val months = reminder.repeatMonths?.takeIf { it > 0 }
        val km = reminder.repeatKm?.takeIf { it > 0.0 }
        if (months == null && km == null) return null
        val anniversary = reminder.kind == ReminderKind.MULKIYA || reminder.kind == ReminderKind.INSURANCE ||
            reminder.kind == ReminderKind.INSPECTION
        val base = if (anniversary && reminder.dueDate != null && completedOn <= reminder.dueDate) reminder.dueDate else completedOn
        val nextDate = months?.let { addMonthsClamped(base, it) }
        val nextKm = km?.let { k -> (completedOdoKm ?: reminder.dueOdometerKm)?.let { it + k } }
        return reminder.copy(
            dueDate = nextDate,
            dueOdometerKm = nextKm,
            done = false,
        )
    }

    /**
     * UAE chain anchored on the Mulkiya expiry: inspection at expiry−30 days (only when the car is more than 3 years
     * old at expiry, or its year is unknown), insurance renewal at expiry−21 days, Mulkiya renewal at expiry.
     * Each step's [Reminder.chainedToId] points to the next step; the Mulkiya step ends the chain. All repeat yearly.
     * Steps whose date already passed (expiry is near) are due today, never after the expiry itself.
     * Sorted by due date.
     */
    fun mulkiyaChain(vehicleId: Id, registrationExpiry: LocalDate, vehicleYear: Int?, today: LocalDate, newId: () -> Id): List<Reminder> {
        val needsInspection = vehicleYear == null || registrationExpiry.year - vehicleYear > INSPECTION_EXEMPT_YEARS
        val mulkiyaId = newId()
        val insuranceId = newId()
        val inspectionId = if (needsInspection) newId() else null
        fun stepDate(daysBefore: Int): LocalDate {
            val target = registrationExpiry.minus(DatePeriod(days = daysBefore))
            val floor = minOf(today, registrationExpiry)
            return maxOf(target, floor)
        }
        val mulkiya = Reminder(
            id = mulkiyaId, vehicleId = vehicleId, kind = ReminderKind.MULKIYA, title = TITLE_MULKIYA,
            dueDate = registrationExpiry, repeatMonths = 12, leadDays = MULKIYA_LEAD_DAYS, chainedToId = null,
        )
        val insurance = Reminder(
            id = insuranceId, vehicleId = vehicleId, kind = ReminderKind.INSURANCE, title = TITLE_INSURANCE,
            dueDate = stepDate(INSURANCE_DAYS_BEFORE), repeatMonths = 12, leadDays = INSURANCE_LEAD_DAYS, chainedToId = mulkiyaId,
        )
        val inspection = inspectionId?.let {
            Reminder(
                id = it, vehicleId = vehicleId, kind = ReminderKind.INSPECTION, title = TITLE_INSPECTION,
                dueDate = stepDate(INSPECTION_DAYS_BEFORE), repeatMonths = 12, leadDays = INSPECTION_LEAD_DAYS, chainedToId = insuranceId,
            )
        }
        return listOfNotNull(inspection, insurance, mulkiya)
    }

    /** Add months keeping the day, clamped to the target month's length. */
    fun addMonthsClamped(date: LocalDate, months: Int): LocalDate {
        val total = date.year * 12 + (date.month.ordinal) + months
        val y = total.floorDiv(12)
        val m = total.mod(12) + 1
        return LocalDate(y, m, minOf(date.day, lengthOfMonth(y, m)))
    }

    private fun lengthOfMonth(year: Int, month: Int): Int = when (month) {
        2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }
}
