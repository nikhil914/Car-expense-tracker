package ae.sayyara.app.platform

import ae.sayyara.app.R
import ae.sayyara.app.appContainer
import ae.sayyara.app.data.PriceRefreshOutcome
import ae.sayyara.core.model.FuelGrade
import ae.sayyara.core.model.YearMonth
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.io.IOException
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/**
 * Pulls the UAE price feed. Runs daily (periodic) and, around the monthly announcement, as a chain
 * of one-time workers every few hours until the target month's prices are in (see [WorkScheduler]).
 * Posts "UAE fuel prices for December: Super 98 AED x" when a new month arrives.
 */
class PriceRefreshWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = applicationContext.appContainer
        val repo = container.fuelPrices
        val isBurst = inputData.getBoolean(KEY_BURST, false)

        val outcome: PriceRefreshOutcome? = try {
            repo.refreshDetailed()
        } catch (e: IOException) {
            null
        } catch (e: IllegalArgumentException) { // malformed feed JSON
            null
        }

        outcome?.newMonth?.let { notifyNewMonth(it) }

        // Keep the burst chain alive until the awaited month is published.
        val today = WorkScheduler.todayInUae()
        if (WorkScheduler.inPriceWindow(today)) {
            val target = WorkScheduler.targetMonth(today)
            if (repo.priceFor(target, FuelGrade.SUPER_98) == null) {
                WorkScheduler.enqueuePriceBurst(applicationContext, fromBurst = isBurst)
            }
        }

        return when {
            outcome != null -> Result.success()
            // A failed burst link must still succeed, or APPEND would cancel its successor.
            isBurst -> Result.success()
            runAttemptCount < MAX_RETRIES -> Result.retry()
            else -> Result.success()
        }
    }

    private suspend fun notifyNewMonth(month: YearMonth) {
        val container = applicationContext.appContainer
        val prefs = container.preferences
        if (prefs.lastPriceMonthNotified() == month.toString()) return
        val repo = container.fuelPrices
        val super98 = repo.priceFor(month, FuelGrade.SUPER_98) ?: return
        val special95 = repo.priceFor(month, FuelGrade.SPECIAL_95)
        val eplus91 = repo.priceFor(month, FuelGrade.E_PLUS_91)
        val diesel = repo.priceFor(month, FuelGrade.DIESEL)

        val ctx = applicationContext
        val locale = ctx.resources.configuration.locales[0] ?: Locale.getDefault()
        val monthName = Month.of(month.month).getDisplayName(TextStyle.FULL_STANDALONE, locale)
        fun aed(v: Double?) = if (v == null) "–" else String.format(locale, "%.2f", v)

        val text = ctx.getString(R.string.notif_price_new_month, monthName, aed(super98.aedPerLitre))
        val big = ctx.getString(
            R.string.notif_price_all_grades,
            aed(super98.aedPerLitre), aed(special95?.aedPerLitre), aed(eplus91?.aedPerLitre), aed(diesel?.aedPerLitre),
        )
        Notifications.postPrices(ctx, ctx.getString(R.string.notif_price_title), text, "$text\n$big")
        prefs.setLastPriceMonthNotified(month.toString())
    }

    companion object {
        const val KEY_BURST = "burst"
        private const val MAX_RETRIES = 3
    }
}
