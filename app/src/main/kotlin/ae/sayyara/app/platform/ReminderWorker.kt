package ae.sayyara.app.platform

import ae.sayyara.app.R
import ae.sayyara.app.appContainer
import ae.sayyara.core.model.EnergyEvent
import ae.sayyara.core.model.Expense
import ae.sayyara.core.model.Reminder
import ae.sayyara.core.reminders.ReminderPlanner
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.util.Locale
import kotlin.math.roundToLong
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate

/** Daily: evaluates every open reminder with [ReminderPlanner.due] and posts notifications. */
class ReminderWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = applicationContext.appContainer
        val today = WorkScheduler.todayInUae()
        val vehicles = container.vehicles.observeVehicles().first().filter { it.active }
        for (vehicle in vehicles) {
            val reminders = container.reminders.observeReminders(vehicle.id).first().filter { !it.done }
            if (reminders.isEmpty()) continue
            val events = container.events.observeEvents(vehicle.id).first()
            val expenses = container.expenses.observeExpenses(vehicle.id).first()
            val odometer = currentOdometer(events, expenses)
            val kmPerDay = kmPerDay(events)
            for (reminder in reminders) {
                val due = ReminderPlanner.due(reminder, today, odometer, kmPerDay)
                if (shouldNotify(reminder, due, today)) {
                    val title = applicationContext.getString(R.string.notif_reminder_title, reminder.title, vehicle.name)
                    Notifications.postReminder(applicationContext, reminder.id, title, describe(due))
                    container.preferences.setReminderNotifiedEpochDay(reminder.id, today.toEpochDays())
                }
            }
        }
        return Result.success()
    }

    private suspend fun shouldNotify(reminder: Reminder, due: ReminderPlanner.Due, today: LocalDate): Boolean {
        val daysLeft = due.daysLeft
        val kmLeft = due.kmLeft
        val dueSoon = due.overdue ||
            (daysLeft != null && daysLeft <= reminder.leadDays) ||
            (kmLeft != null && kmLeft <= KM_LEAD)
        if (!dueSoon) return false
        // Urgent ones (today/tomorrow) every day; otherwise at most every few days.
        if (daysLeft != null && daysLeft in 0..1) return true
        val last = applicationContext.appContainer.preferences.reminderNotifiedEpochDay(reminder.id) ?: return true
        return today.toEpochDays() - last >= REPEAT_EVERY_DAYS
    }

    private fun describe(due: ReminderPlanner.Due): String {
        val res = applicationContext.resources
        val daysLeft = due.daysLeft
        val kmLeft = due.kmLeft
        return when {
            due.overdue && daysLeft != null && daysLeft < 0 ->
                res.getQuantityString(R.plurals.notif_reminder_overdue_days, -daysLeft, -daysLeft)
            due.overdue -> res.getString(R.string.notif_reminder_overdue)
            daysLeft == 0 -> res.getString(R.string.notif_reminder_due_today)
            daysLeft != null && (kmLeft == null || daysLeft <= DAYS_BEFORE_KM) ->
                res.getQuantityString(R.plurals.notif_reminder_due_in_days, daysLeft, daysLeft)
            kmLeft != null -> {
                val locale = res.configuration.locales[0] ?: Locale.getDefault()
                res.getString(R.string.notif_reminder_due_in_km, String.format(locale, "%,d", kmLeft.roundToLong()))
            }
            else -> res.getString(R.string.notif_reminder_due_soon)
        }
    }

    private fun currentOdometer(events: List<EnergyEvent>, expenses: List<Expense>): Double? =
        (events.map { it.odometerKm } + expenses.mapNotNull { it.odometerKm }).maxOrNull()

    /** Average daily distance over the logged history (needs two weeks of data). */
    private fun kmPerDay(events: List<EnergyEvent>): Double? {
        if (events.size < 2) return null
        val sorted = events.sortedBy { it.timestamp }
        val days = (sorted.last().timestamp - sorted.first().timestamp).inWholeDays
        val km = sorted.last().odometerKm - sorted.first().odometerKm
        return if (days >= 14 && km > 0) km / days else null
    }

    private companion object {
        const val KM_LEAD = 500.0
        const val REPEAT_EVERY_DAYS = 3L
        /** Prefer a "days" message unless the km limit is clearly what will trigger first. */
        const val DAYS_BEFORE_KM = 30
    }
}
