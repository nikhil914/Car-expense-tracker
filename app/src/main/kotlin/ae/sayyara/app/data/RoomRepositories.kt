package ae.sayyara.app.data

import ae.sayyara.app.data.db.SayyaraDatabase
import ae.sayyara.app.data.db.toEntity
import ae.sayyara.app.data.db.toModel
import ae.sayyara.core.model.EnergyEvent
import ae.sayyara.core.model.EnergyStore
import ae.sayyara.core.model.Expense
import ae.sayyara.core.model.Id
import ae.sayyara.core.model.Reminder
import ae.sayyara.core.model.Trip
import ae.sayyara.core.model.Vehicle
import androidx.room.withTransaction
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Injectable clock so repositories are testable. */
fun interface NowProvider {
    fun now(): Instant
}

val SystemNow = NowProvider { Clock.System.now() }

class RoomVehicleRepository(
    private val db: SayyaraDatabase,
    private val prefs: AppPreferences,
    private val clock: NowProvider = SystemNow,
) : VehicleRepository {
    private val vehicleDao = db.vehicleDao()
    private val storeDao = db.energyStoreDao()

    override fun observeVehicles(): Flow<List<Vehicle>> =
        vehicleDao.observeActive().map { list -> list.map { it.toModel() } }

    override fun observeStores(vehicleId: Id): Flow<List<EnergyStore>> =
        storeDao.observeForVehicle(vehicleId).map { list -> list.map { it.toModel() } }

    override suspend fun upsertVehicle(vehicle: Vehicle, stores: List<EnergyStore>) {
        val now = clock.now()
        val nowMs = now.toEpochMilliseconds()
        db.withTransaction {
            val existing = vehicleDao.get(vehicle.id)
            vehicleDao.upsert(
                vehicle.copy(updatedAt = now, deletedAt = null).toEntity(importBatchId = existing?.importBatchId),
            )
            val storeEntities = stores.map { s ->
                val prior = storeDao.get(s.id)
                s.copy(vehicleId = vehicle.id).toEntity(updatedAt = nowMs, importBatchId = prior?.importBatchId)
            }
            storeDao.upsertAll(storeEntities)
            storeDao.softDeleteMissing(vehicle.id, storeEntities.map { it.id }, nowMs)
        }
    }

    override suspend fun deleteVehicle(id: Id) {
        val nowMs = clock.now().toEpochMilliseconds()
        db.withTransaction {
            vehicleDao.softDelete(id, nowMs)
            storeDao.softDeleteForVehicle(id, nowMs)
            db.energyEventDao().softDeleteForVehicle(id, nowMs)
            db.expenseDao().softDeleteForVehicle(id, nowMs)
            db.reminderDao().softDeleteForVehicle(id, nowMs)
            db.tripDao().softDeleteForVehicle(id, nowMs)
        }
        if (prefs.selectedVehicleId.first() == id) {
            prefs.setSelectedVehicleId(vehicleDao.getActive().firstOrNull()?.id)
        }
    }

    /**
     * The stored selection when it still points at an active vehicle; otherwise the first active
     * vehicle (active first, then by name); null when there are no vehicles.
     */
    override fun observeSelectedVehicleId(): Flow<Id?> =
        combine(prefs.selectedVehicleId, vehicleDao.observeActive()) { selected, vehicles ->
            when {
                vehicles.isEmpty() -> null
                selected != null && vehicles.any { it.id == selected } -> selected
                else -> vehicles.first().id
            }
        }.distinctUntilChanged()

    override suspend fun selectVehicle(id: Id) = prefs.setSelectedVehicleId(id)
}

class RoomEnergyEventRepository(
    db: SayyaraDatabase,
    private val clock: NowProvider = SystemNow,
) : EnergyEventRepository {
    private val dao = db.energyEventDao()

    override fun observeEvents(vehicleId: Id): Flow<List<EnergyEvent>> =
        dao.observeForVehicle(vehicleId).map { list -> list.map { it.toModel() } }

    override suspend fun upsert(event: EnergyEvent) =
        dao.upsert(event.copy(updatedAt = clock.now(), deletedAt = null).toEntity())

    override suspend fun delete(id: Id) {
        dao.softDelete(id, clock.now().toEpochMilliseconds())
    }

    override suspend fun lastEvent(vehicleId: Id): EnergyEvent? = dao.last(vehicleId)?.toModel()

    /** All active events across vehicles (export, backup, widget). */
    suspend fun allActive(): List<EnergyEvent> = dao.getActive().map { it.toModel() }
}

class RoomExpenseRepository(
    db: SayyaraDatabase,
    private val clock: NowProvider = SystemNow,
) : ExpenseRepository {
    private val dao = db.expenseDao()

    override fun observeExpenses(vehicleId: Id): Flow<List<Expense>> =
        dao.observeForVehicle(vehicleId).map { list -> list.map { it.toModel() } }

    override suspend fun upsert(expense: Expense) =
        dao.upsert(expense.copy(updatedAt = clock.now(), deletedAt = null).toEntity())

    override suspend fun delete(id: Id) {
        dao.softDelete(id, clock.now().toEpochMilliseconds())
    }
}

class RoomReminderRepository(
    db: SayyaraDatabase,
    private val clock: NowProvider = SystemNow,
) : ReminderRepository {
    private val dao = db.reminderDao()

    override fun observeReminders(vehicleId: Id): Flow<List<Reminder>> =
        dao.observeForVehicle(vehicleId).map { list -> list.map { it.toModel() } }

    override suspend fun upsert(reminder: Reminder) {
        val prior = dao.get(reminder.id)
        dao.upsert(reminder.toEntity(updatedAt = clock.now().toEpochMilliseconds(), importBatchId = prior?.importBatchId))
    }

    override suspend fun delete(id: Id) {
        dao.softDelete(id, clock.now().toEpochMilliseconds())
    }
}

class RoomTripRepository(
    db: SayyaraDatabase,
    private val clock: NowProvider = SystemNow,
) : TripRepository {
    private val dao = db.tripDao()

    override fun observeTrips(vehicleId: Id): Flow<List<Trip>> =
        dao.observeForVehicle(vehicleId).map { list -> list.map { it.toModel() } }

    override suspend fun upsert(trip: Trip) = dao.upsert(trip.toEntity(updatedAt = clock.now().toEpochMilliseconds()))
}
