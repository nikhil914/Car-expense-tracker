package ae.sayyara.app.platform.widget

import ae.sayyara.app.data.AppContainer
import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps the home-screen widget in step with the repositories while the app process is alive:
 * any change to the selected vehicle, its fills, its expenses or the price table triggers a
 * (debounced) updateAll(). updateAll() is a no-op when no widget is placed.
 */
object DashboardWidgetUpdater {
    private const val DEBOUNCE_MS = 1_500L

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    fun start(context: Context, container: AppContainer, scope: CoroutineScope): Job {
        val appContext = context.applicationContext
        return scope.launch {
            container.vehicles.observeSelectedVehicleId()
                .flatMapLatest { vehicleId ->
                    if (vehicleId == null) {
                        container.fuelPrices.observeAll().map { prices -> listOf<Any>(prices) }
                    } else {
                        combine(
                            container.events.observeEvents(vehicleId),
                            container.expenses.observeExpenses(vehicleId),
                            container.vehicles.observeStores(vehicleId),
                            container.fuelPrices.observeAll(),
                        ) { events, expenses, stores, prices -> listOf<Any>(vehicleId, events, expenses, stores, prices) }
                    }
                }
                .debounce(DEBOUNCE_MS)
                .collectLatest { safeUpdate(appContext) }
        }
    }

    /** One-shot refresh, e.g. after a background price refresh. */
    suspend fun updateNow(context: Context) = safeUpdate(context.applicationContext)

    private suspend fun safeUpdate(context: Context) {
        try {
            DashboardWidget().updateAll(context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A broken widget must never take the app down; the next change retries.
        }
    }
}
