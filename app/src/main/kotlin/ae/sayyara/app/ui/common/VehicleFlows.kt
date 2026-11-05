package ae.sayyara.app.ui.common

import ae.sayyara.app.data.AppContainer
import ae.sayyara.core.model.EnergyEvent
import ae.sayyara.core.model.EnergyStore
import ae.sayyara.core.model.EnergyStoreKind
import ae.sayyara.core.model.Expense
import ae.sayyara.core.model.FuelGrade
import ae.sayyara.core.model.Id
import ae.sayyara.core.model.Reminder
import ae.sayyara.core.model.Vehicle
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

fun newId(): Id = UUID.randomUUID().toString()

/** All live data for one vehicle; soft-deleted rows are already filtered out. */
data class VehicleData(
    val vehicle: Vehicle,
    val stores: List<EnergyStore>,
    val events: List<EnergyEvent>,
    val expenses: List<Expense>,
    val reminders: List<Reminder>,
) {
    /** The tank for ICE/PHEV, the battery for an EV. */
    val primaryStore: EnergyStore?
        get() = stores.firstOrNull { it.kind == EnergyStoreKind.TANK } ?: stores.firstOrNull()

    /** Events of [store]; falls back to every event when store ids do not match (e.g. imports). */
    fun eventsFor(store: EnergyStore?): List<EnergyEvent> {
        if (store == null) return events
        val own = events.filter { it.storeId == store.id }
        return if (own.isEmpty() && stores.size <= 1) events else own
    }

    fun lastOdometerKm(): Double? {
        val fromFills = events.maxOfOrNull { it.odometerKm }
        val fromExpenses = expenses.mapNotNull { it.odometerKm }.maxOrNull()
        return listOfNotNull(fromFills, fromExpenses).maxOrNull()
    }

    /** Average km driven per day across the logged history, or null when the span is too short. */
    fun kmPerDay(): Double? {
        if (events.size < 2) return null
        val first = events.minBy { it.timestamp }
        val last = events.maxBy { it.timestamp }
        val days = (last.timestamp - first.timestamp).inWholeHours / 24.0
        val km = last.odometerKm - first.odometerKm
        return if (days >= 7 && km > 0) km / days else null
    }
}

fun AppContainer.activeVehicles(): Flow<List<Vehicle>> =
    vehicles.observeVehicles().map { list -> list.filter { it.deletedAt == null } }

fun AppContainer.selectedVehicle(): Flow<Vehicle?> =
    combine(activeVehicles(), vehicles.observeSelectedVehicleId()) { list, id ->
        list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }.distinctUntilChanged()

@OptIn(ExperimentalCoroutinesApi::class)
fun AppContainer.selectedVehicleData(): Flow<VehicleData?> =
    selectedVehicle().flatMapLatest { v ->
        if (v == null) {
            flowOf(null)
        } else {
            combine(
                vehicles.observeStores(v.id),
                events.observeEvents(v.id),
                expenses.observeExpenses(v.id),
                reminders.observeReminders(v.id),
            ) { stores, events, expenses, reminders ->
                VehicleData(
                    vehicle = v,
                    stores = stores,
                    events = events.filter { it.deletedAt == null },
                    expenses = expenses.filter { it.deletedAt == null },
                    reminders = reminders,
                )
            }
        }
    }

/** Fuel grades a user can pick for a liquid-fuel tank (UAE first). */
val LiquidGrades = listOf(FuelGrade.SUPER_98, FuelGrade.SPECIAL_95, FuelGrade.E_PLUS_91, FuelGrade.DIESEL)
