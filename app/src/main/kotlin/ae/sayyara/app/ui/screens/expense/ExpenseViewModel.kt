package ae.sayyara.app.ui.screens.expense

import ae.sayyara.app.data.AppContainer
import ae.sayyara.app.ui.common.VehicleData
import ae.sayyara.app.ui.common.newId
import ae.sayyara.app.ui.common.selectedVehicleData
import ae.sayyara.app.ui.format.parseDecimal
import ae.sayyara.app.ui.format.toInputText
import ae.sayyara.core.model.Emirate
import ae.sayyara.core.model.Expense
import ae.sayyara.core.model.ExpenseCategoryKey
import ae.sayyara.core.model.Id
import ae.sayyara.core.uae.Parking
import ae.sayyara.core.uae.SmsDraft
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

data class ExpenseUiState(
    val loading: Boolean = true,
    val noVehicle: Boolean = false,
    val isEdit: Boolean = false,
    val category: ExpenseCategoryKey = ExpenseCategoryKey.SALIK,
    val amountText: String = "",
    val date: LocalDate? = null,
    val odometerText: String = "",
    val title: String = "",
    val note: String = "",
    val isIncome: Boolean = false,
    // Parking helper
    val plate: String = "",
    val emirate: Emirate = Emirate.DUBAI,
    val zone: String = "",
    val hours: Int = 1,
    val parkingRate: Double? = null,
    val saving: Boolean = false,
    val done: Boolean = false,
) {
    val amount: Double? get() = parseDecimal(amountText)?.takeIf { it >= 0 }
    val canSave: Boolean get() = !saving && !noVehicle && (amount ?: 0.0) > 0
}

class ExpenseViewModel(
    private val container: AppContainer,
    private val expenseId: Id?,
    initialCategory: ExpenseCategoryKey?,
) : ViewModel() {
    private val zoneTz = TimeZone.currentSystemDefault()
    private val _state = MutableStateFlow(ExpenseUiState(category = initialCategory ?: ExpenseCategoryKey.SALIK))
    val state: StateFlow<ExpenseUiState> = _state.asStateFlow()

    private var data: VehicleData? = null
    private var original: Expense? = null

    init {
        viewModelScope.launch {
            val d = container.selectedVehicleData().first()
            if (d == null) {
                _state.update { it.copy(loading = false, noVehicle = true) }
                return@launch
            }
            data = d
            val orig = expenseId?.let { id -> d.expenses.firstOrNull { it.id == id } }
            original = orig
            val today = Clock.System.now().toLocalDateTime(zoneTz).date
            _state.update { s ->
                val base = s.copy(
                    loading = false,
                    plate = d.vehicle.plate.orEmpty(),
                    emirate = d.vehicle.emirate ?: Emirate.DUBAI,
                    date = today,
                )
                if (orig == null) {
                    base
                } else {
                    base.copy(
                        isEdit = true,
                        category = orig.category,
                        amountText = orig.amountAed.toInputText(2),
                        date = orig.timestamp.toLocalDateTime(zoneTz).date,
                        odometerText = orig.odometerKm?.toInputText(0).orEmpty(),
                        title = orig.title.orEmpty(),
                        note = orig.note.orEmpty(),
                        isIncome = orig.isIncome,
                    )
                }
            }
            updateParkingRate()
        }
    }

    fun onCategory(c: ExpenseCategoryKey) {
        _state.update { it.copy(category = c) }
        updateParkingRate()
    }

    fun onAmount(t: String) = _state.update { it.copy(amountText = t) }
    fun onDate(d: LocalDate?) = _state.update { it.copy(date = d ?: it.date) }
    fun onOdometer(t: String) = _state.update { it.copy(odometerText = t) }
    fun onTitle(t: String) = _state.update { it.copy(title = t) }
    fun onNote(t: String) = _state.update { it.copy(note = t) }
    fun onIncome(v: Boolean) = _state.update { it.copy(isIncome = v) }
    fun onPlate(t: String) = _state.update { it.copy(plate = t) }

    fun onEmirate(e: Emirate) {
        _state.update { it.copy(emirate = e) }
        updateParkingRate()
    }

    fun onZone(t: String) {
        _state.update { it.copy(zone = t.uppercase()) }
        updateParkingRate()
    }

    fun onHours(h: Int) {
        _state.update { it.copy(hours = h.coerceIn(1, 24)) }
        updateParkingRate()
    }

    private fun updateParkingRate() {
        val s = _state.value
        if (s.category != ExpenseCategoryKey.PARKING || s.zone.isBlank()) {
            _state.update { it.copy(parkingRate = null) }
            return
        }
        val now = Clock.System.now()
        val rate = runCatching { Parking.hourlyRate(s.emirate, s.zone, now) }.getOrNull()
        val suggested = runCatching { Parking.cost(s.emirate, s.zone, now, s.hours.toDouble()) }.getOrNull()
            ?: rate?.let { it * s.hours }
        _state.update { cur ->
            cur.copy(
                parkingRate = rate,
                // Prefill the amount only while the user has not typed one.
                amountText = if (cur.amountText.isBlank() && suggested != null) suggested.toInputText(2) else cur.amountText,
            )
        }
    }

    /** SMS draft for paying parking (e.g. Dubai 7275, Abu Dhabi 3009, Sharjah 5566). */
    fun smsDraft(): SmsDraft? {
        val s = _state.value
        if (s.plate.isBlank() || s.zone.isBlank()) return null
        return runCatching { Parking.smsDraft(s.emirate, s.plate.trim(), s.zone.trim(), s.hours) }.getOrNull()
    }

    fun save() {
        val s = _state.value
        val d = data ?: return
        val amount = s.amount ?: return
        if (!s.canSave) return
        _state.update { it.copy(saving = true) }
        val now = Clock.System.now()
        val date = s.date ?: now.toLocalDateTime(zoneTz).date
        // Keep the original time of day when editing; new entries use "now" when dated today.
        val timestamp = original?.timestamp?.takeIf { it.toLocalDateTime(zoneTz).date == date }
            ?: if (date == now.toLocalDateTime(zoneTz).date) now else date.atTime(12, 0).toInstant(zoneTz)
        val expense = (original ?: Expense(
            id = newId(),
            vehicleId = d.vehicle.id,
            timestamp = timestamp,
            category = s.category,
            amountAed = amount,
            updatedAt = now,
        )).copy(
            timestamp = timestamp,
            category = s.category,
            amountAed = amount,
            odometerKm = parseDecimal(s.odometerText),
            title = s.title.trim().ifBlank { null },
            note = s.note.trim().ifBlank { null },
            customCategory = if (s.category == ExpenseCategoryKey.OTHER) s.title.trim().ifBlank { null } else original?.customCategory,
            isIncome = s.isIncome,
            updatedAt = now,
        )
        viewModelScope.launch {
            container.expenses.upsert(expense)
            _state.update { it.copy(saving = false, done = true) }
        }
    }

    fun delete() {
        val id = original?.id ?: return
        viewModelScope.launch {
            container.expenses.delete(id)
            _state.update { it.copy(done = true) }
        }
    }
}
