package ae.sayyara.app.data

import ae.sayyara.core.model.EnergyEvent
import ae.sayyara.core.model.EnergyStore
import ae.sayyara.core.model.Expense
import ae.sayyara.core.model.FuelGrade
import ae.sayyara.core.model.FuelPrice
import ae.sayyara.core.model.Id
import ae.sayyara.core.model.Reminder
import ae.sayyara.core.model.Trip
import ae.sayyara.core.model.Vehicle
import ae.sayyara.core.model.YearMonth
import kotlinx.coroutines.flow.Flow

/*
 * CONTRACT between the data layer (implemented in ae.sayyara.app.data.*) and the UI layer
 * (ae.sayyara.app.ui.*). UI depends only on these interfaces, obtained from AppContainer.
 * Writes are soft deletes (deletedAt) so sync can propagate them later.
 */

interface VehicleRepository {
    fun observeVehicles(): Flow<List<Vehicle>>
    fun observeStores(vehicleId: Id): Flow<List<EnergyStore>>
    suspend fun upsertVehicle(vehicle: Vehicle, stores: List<EnergyStore>)
    suspend fun deleteVehicle(id: Id)
    /** Currently selected vehicle (persisted in DataStore). */
    fun observeSelectedVehicleId(): Flow<Id?>
    suspend fun selectVehicle(id: Id)
}

interface EnergyEventRepository {
    fun observeEvents(vehicleId: Id): Flow<List<EnergyEvent>>
    suspend fun upsert(event: EnergyEvent)
    suspend fun delete(id: Id)
    suspend fun lastEvent(vehicleId: Id): EnergyEvent?
}

interface ExpenseRepository {
    fun observeExpenses(vehicleId: Id): Flow<List<Expense>>
    suspend fun upsert(expense: Expense)
    suspend fun delete(id: Id)
}

interface ReminderRepository {
    fun observeReminders(vehicleId: Id): Flow<List<Reminder>>
    suspend fun upsert(reminder: Reminder)
    suspend fun delete(id: Id)
}

interface TripRepository {
    fun observeTrips(vehicleId: Id): Flow<List<Trip>>
    suspend fun upsert(trip: Trip)
}

/** UAE monthly fuel prices: bundled seed + remote refresh. */
interface FuelPriceRepository {
    fun observeAll(): Flow<List<FuelPrice>>
    suspend fun priceFor(month: YearMonth, grade: FuelGrade): FuelPrice?
    suspend fun latest(grade: FuelGrade): FuelPrice?
    /** Pull latest published prices; returns true when a new month arrived. */
    suspend fun refresh(): Boolean
}

sealed interface ImportResult {
    data class Preview(val batchId: Id, val vehicles: Int, val fills: Int, val expenses: Int, val warnings: List<String>) : ImportResult
    data class Failed(val message: String) : ImportResult
}

interface ImportRepository {
    /** Parse a Fuelio CSV / zip (bytes) without writing; returns preview. */
    suspend fun preview(fileName: String, bytes: ByteArray): ImportResult
    suspend fun commit(batchId: Id)
    suspend fun undo(batchId: Id)
    /** Full CSV export of all data. */
    suspend fun exportCsv(): ByteArray
}

/** Manual DI root. Created once in SayyaraApp. */
interface AppContainer {
    val vehicles: VehicleRepository
    val events: EnergyEventRepository
    val expenses: ExpenseRepository
    val reminders: ReminderRepository
    val trips: TripRepository
    val fuelPrices: FuelPriceRepository
    val importer: ImportRepository
}
