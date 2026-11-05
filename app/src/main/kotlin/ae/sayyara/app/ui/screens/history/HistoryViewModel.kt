package ae.sayyara.app.ui.screens.history

import ae.sayyara.app.data.AppContainer
import ae.sayyara.app.ui.common.selectedVehicleData
import ae.sayyara.core.model.EnergyEvent
import ae.sayyara.core.model.Expense
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One row of the unified fills + expenses timeline. */
sealed interface TimelineItem {
    val key: String
    val timestamp: Instant

    data class Fill(val event: EnergyEvent, val kmSincePrevious: Double?) : TimelineItem {
        override val key get() = "f-${event.id}"
        override val timestamp get() = event.timestamp
    }

    data class Cost(val expense: Expense) : TimelineItem {
        override val key get() = "e-${expense.id}"
        override val timestamp get() = expense.timestamp
    }
}

data class HistoryState(
    val loading: Boolean = true,
    val hasVehicle: Boolean = false,
    val items: List<TimelineItem> = emptyList(),
)

class HistoryViewModel(private val container: AppContainer) : ViewModel() {

    val state: StateFlow<HistoryState> = container.selectedVehicleData().map { data ->
        if (data == null) {
            HistoryState(loading = false)
        } else {
            val fills = data.events.sortedBy { it.odometerKm }
            val fillItems = fills.mapIndexed { i, e ->
                val prev = fills.getOrNull(i - 1)?.takeIf { it.storeId == e.storeId }
                TimelineItem.Fill(e, prev?.let { e.odometerKm - it.odometerKm })
            }
            val items = (fillItems + data.expenses.map { TimelineItem.Cost(it) })
                .sortedByDescending { it.timestamp }
            HistoryState(loading = false, hasVehicle = true, items = items)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryState())

    /** Soft-deletes the item; [undoDelete] restores it. */
    fun delete(item: TimelineItem) {
        viewModelScope.launch {
            when (item) {
                is TimelineItem.Fill -> container.events.delete(item.event.id)
                is TimelineItem.Cost -> container.expenses.delete(item.expense.id)
            }
        }
    }

    fun undoDelete(item: TimelineItem) {
        val now = Clock.System.now()
        viewModelScope.launch {
            when (item) {
                is TimelineItem.Fill -> container.events.upsert(item.event.copy(deletedAt = null, updatedAt = now))
                is TimelineItem.Cost -> container.expenses.upsert(item.expense.copy(deletedAt = null, updatedAt = now))
            }
        }
    }
}
