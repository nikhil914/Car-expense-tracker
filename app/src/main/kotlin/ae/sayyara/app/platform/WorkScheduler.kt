package ae.sayyara.app.platform

import ae.sayyara.core.model.YearMonth
import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * All WorkManager scheduling in one place. Safe to call repeatedly (app start, boot, app update):
 * periodic work uses KEEP so the existing schedule is not reset.
 */
object WorkScheduler {
    const val PRICE_PERIODIC = "price-refresh-daily"
    const val PRICE_BURST = "price-refresh-burst"
    const val REMINDER_DAILY = "reminder-check-daily"

    /** Interval of the month-end burst chain. */
    private const val BURST_INTERVAL_HOURS = 3L

    val UAE_ZONE: TimeZone = TimeZone.of("Asia/Dubai")
    private val UAE_JAVA_ZONE: ZoneId = ZoneId.of("Asia/Dubai")

    private val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun scheduleAll(context: Context) {
        val wm = WorkManager.getInstance(context)

        val price = PeriodicWorkRequestBuilder<PriceRefreshWorker>(1, TimeUnit.DAYS)
            .setConstraints(networkConstraints)
            .setInitialDelay(delayUntilUaeTime(hour = 6, minute = 17).toMinutes(), TimeUnit.MINUTES)
            .addTag(PRICE_PERIODIC)
            .build()
        wm.enqueueUniquePeriodicWork(PRICE_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, price)

        val reminders = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayUntilUaeTime(hour = 9, minute = 3).toMinutes(), TimeUnit.MINUTES)
            .addTag(REMINDER_DAILY)
            .build()
        wm.enqueueUniquePeriodicWork(REMINDER_DAILY, ExistingPeriodicWorkPolicy.KEEP, reminders)

        // If the app starts inside the month-end window, start the burst chain now.
        if (inPriceWindow(todayInUae())) enqueuePriceBurst(context, fromBurst = false, immediate = true)
    }

    /**
     * One-time, chained follow-up refreshes around the monthly announcement (last 2 days of a month
     * and the 1st). A burst worker appends its successor to the same unique chain, so only one
     * chain exists at a time. The daily periodic worker only starts a chain when none is pending.
     */
    fun enqueuePriceBurst(context: Context, fromBurst: Boolean, immediate: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<PriceRefreshWorker>()
            .setConstraints(networkConstraints)
            .setInitialDelay(if (immediate) 0L else BURST_INTERVAL_HOURS, TimeUnit.HOURS)
            .setInputData(workDataOf(PriceRefreshWorker.KEY_BURST to true))
            .addTag(PRICE_BURST)
            .build()
        val policy = if (fromBurst) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP
        WorkManager.getInstance(context).enqueueUniqueWork(PRICE_BURST, policy, request)
    }

    fun todayInUae(): LocalDate = Clock.System.now().toLocalDateTime(UAE_ZONE).date

    /** True on the last 2 days of a month and on the 1st (UAE prices are announced on the last day). */
    fun inPriceWindow(date: LocalDate): Boolean {
        val inTwoDays = date.plus(2, DateTimeUnit.DAY)
        val yesterday = date.minus(1, DateTimeUnit.DAY)
        return inTwoDays.month != date.month || yesterday.month != date.month
    }

    /** The month whose prices the burst is waiting for: next month before the 1st, this month on the 1st. */
    fun targetMonth(date: LocalDate): YearMonth {
        val current = YearMonth.of(date)
        val yesterday = date.minus(1, DateTimeUnit.DAY)
        return if (yesterday.month != date.month) current else current.next()
    }

    private fun delayUntilUaeTime(hour: Int, minute: Int): Duration {
        val now = ZonedDateTime.now(UAE_JAVA_ZONE)
        var next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next)
    }
}
