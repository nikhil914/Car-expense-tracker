package ae.sayyara.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/*
 * DAOs. "Active" means deletedAt IS NULL. Observers return Flow; one-shot reads are suspend.
 * dumpAll()/deleteAllRows() exist for BackupManager (they include tombstones).
 */

@Dao
interface VehicleDao {
    @Query("SELECT * FROM vehicles WHERE deletedAt IS NULL ORDER BY active DESC, name COLLATE NOCASE")
    fun observeActive(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles WHERE deletedAt IS NULL ORDER BY active DESC, name COLLATE NOCASE")
    suspend fun getActive(): List<VehicleEntity>

    @Query("SELECT * FROM vehicles WHERE id = :id")
    suspend fun get(id: String): VehicleEntity?

    @Upsert
    suspend fun upsert(entity: VehicleEntity)

    @Upsert
    suspend fun upsertAll(entities: List<VehicleEntity>)

    @Query("UPDATE vehicles SET deletedAt = :now, updatedAt = :now WHERE id = :id AND deletedAt IS NULL")
    suspend fun softDelete(id: String, now: Long): Int

    @Query("UPDATE vehicles SET deletedAt = :now, updatedAt = :now WHERE importBatchId = :batchId AND deletedAt IS NULL")
    suspend fun softDeleteBatch(batchId: String, now: Long): Int

    @Query("SELECT * FROM vehicles")
    suspend fun dumpAll(): List<VehicleEntity>

    @Query("DELETE FROM vehicles")
    suspend fun deleteAllRows()
}

@Dao
interface EnergyStoreDao {
    @Query("SELECT * FROM energy_stores WHERE vehicleId = :vehicleId AND deletedAt IS NULL ORDER BY kind, id")
    fun observeForVehicle(vehicleId: String): Flow<List<EnergyStoreEntity>>

    @Query("SELECT * FROM energy_stores WHERE vehicleId = :vehicleId AND deletedAt IS NULL ORDER BY kind, id")
    suspend fun getForVehicle(vehicleId: String): List<EnergyStoreEntity>

    @Query("SELECT * FROM energy_stores WHERE deletedAt IS NULL")
    suspend fun getActive(): List<EnergyStoreEntity>

    @Query("SELECT * FROM energy_stores WHERE id = :id")
    suspend fun get(id: String): EnergyStoreEntity?

    @Upsert
    suspend fun upsertAll(entities: List<EnergyStoreEntity>)

    @Query(
        "UPDATE energy_stores SET deletedAt = :now, updatedAt = :now " +
            "WHERE vehicleId = :vehicleId AND deletedAt IS NULL AND id NOT IN (:keepIds)",
    )
    suspend fun softDeleteMissing(vehicleId: String, keepIds: List<String>, now: Long): Int

    @Query("UPDATE energy_stores SET deletedAt = :now, updatedAt = :now WHERE vehicleId = :vehicleId AND deletedAt IS NULL")
    suspend fun softDeleteForVehicle(vehicleId: String, now: Long): Int

    @Query("UPDATE energy_stores SET deletedAt = :now, updatedAt = :now WHERE importBatchId = :batchId AND deletedAt IS NULL")
    suspend fun softDeleteBatch(batchId: String, now: Long): Int

    @Query("SELECT * FROM energy_stores")
    suspend fun dumpAll(): List<EnergyStoreEntity>

    @Query("DELETE FROM energy_stores")
    suspend fun deleteAllRows()
}

@Dao
interface EnergyEventDao {
    @Query("SELECT * FROM energy_events WHERE vehicleId = :vehicleId AND deletedAt IS NULL ORDER BY timestamp, odometerKm")
    fun observeForVehicle(vehicleId: String): Flow<List<EnergyEventEntity>>

    @Query("SELECT * FROM energy_events WHERE vehicleId = :vehicleId AND deletedAt IS NULL ORDER BY timestamp, odometerKm")
    suspend fun getForVehicle(vehicleId: String): List<EnergyEventEntity>

    @Query("SELECT * FROM energy_events WHERE deletedAt IS NULL ORDER BY vehicleId, timestamp, odometerKm")
    suspend fun getActive(): List<EnergyEventEntity>

    @Query(
        "SELECT * FROM energy_events WHERE vehicleId = :vehicleId AND deletedAt IS NULL " +
            "ORDER BY timestamp DESC, odometerKm DESC LIMIT 1",
    )
    suspend fun last(vehicleId: String): EnergyEventEntity?

    @Query("SELECT * FROM energy_events WHERE id = :id")
    suspend fun get(id: String): EnergyEventEntity?

    @Upsert
    suspend fun upsert(entity: EnergyEventEntity)

    @Upsert
    suspend fun upsertAll(entities: List<EnergyEventEntity>)

    @Query("UPDATE energy_events SET deletedAt = :now, updatedAt = :now WHERE id = :id AND deletedAt IS NULL")
    suspend fun softDelete(id: String, now: Long): Int

    @Query("UPDATE energy_events SET deletedAt = :now, updatedAt = :now WHERE vehicleId = :vehicleId AND deletedAt IS NULL")
    suspend fun softDeleteForVehicle(vehicleId: String, now: Long): Int

    @Query("UPDATE energy_events SET deletedAt = :now, updatedAt = :now WHERE importBatchId = :batchId AND deletedAt IS NULL")
    suspend fun softDeleteBatch(batchId: String, now: Long): Int

    @Query("SELECT * FROM energy_events")
    suspend fun dumpAll(): List<EnergyEventEntity>

    @Query("DELETE FROM energy_events")
    suspend fun deleteAllRows()
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE vehicleId = :vehicleId AND deletedAt IS NULL ORDER BY timestamp")
    fun observeForVehicle(vehicleId: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE vehicleId = :vehicleId AND deletedAt IS NULL ORDER BY timestamp")
    suspend fun getForVehicle(vehicleId: String): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE deletedAt IS NULL ORDER BY vehicleId, timestamp")
    suspend fun getActive(): List<ExpenseEntity>

    @Upsert
    suspend fun upsert(entity: ExpenseEntity)

    @Upsert
    suspend fun upsertAll(entities: List<ExpenseEntity>)

    @Query("UPDATE expenses SET deletedAt = :now, updatedAt = :now WHERE id = :id AND deletedAt IS NULL")
    suspend fun softDelete(id: String, now: Long): Int

    @Query("UPDATE expenses SET deletedAt = :now, updatedAt = :now WHERE vehicleId = :vehicleId AND deletedAt IS NULL")
    suspend fun softDeleteForVehicle(vehicleId: String, now: Long): Int

    @Query("UPDATE expenses SET deletedAt = :now, updatedAt = :now WHERE importBatchId = :batchId AND deletedAt IS NULL")
    suspend fun softDeleteBatch(batchId: String, now: Long): Int

    @Query("SELECT * FROM expenses")
    suspend fun dumpAll(): List<ExpenseEntity>

    @Query("DELETE FROM expenses")
    suspend fun deleteAllRows()
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE vehicleId = :vehicleId AND deletedAt IS NULL ORDER BY done, dueDate, dueOdometerKm")
    fun observeForVehicle(vehicleId: String): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE vehicleId = :vehicleId AND deletedAt IS NULL ORDER BY done, dueDate, dueOdometerKm")
    suspend fun getForVehicle(vehicleId: String): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun get(id: String): ReminderEntity?

    @Upsert
    suspend fun upsert(entity: ReminderEntity)

    @Upsert
    suspend fun upsertAll(entities: List<ReminderEntity>)

    @Query("UPDATE reminders SET deletedAt = :now, updatedAt = :now WHERE id = :id AND deletedAt IS NULL")
    suspend fun softDelete(id: String, now: Long): Int

    @Query("UPDATE reminders SET deletedAt = :now, updatedAt = :now WHERE vehicleId = :vehicleId AND deletedAt IS NULL")
    suspend fun softDeleteForVehicle(vehicleId: String, now: Long): Int

    @Query("UPDATE reminders SET deletedAt = :now, updatedAt = :now WHERE importBatchId = :batchId AND deletedAt IS NULL")
    suspend fun softDeleteBatch(batchId: String, now: Long): Int

    @Query("SELECT * FROM reminders")
    suspend fun dumpAll(): List<ReminderEntity>

    @Query("DELETE FROM reminders")
    suspend fun deleteAllRows()
}

@Dao
interface TripDao {
    @Query("SELECT * FROM trips WHERE vehicleId = :vehicleId AND deletedAt IS NULL ORDER BY startedAt DESC")
    fun observeForVehicle(vehicleId: String): Flow<List<TripEntity>>

    @Upsert
    suspend fun upsert(entity: TripEntity)

    @Upsert
    suspend fun upsertAll(entities: List<TripEntity>)

    @Query("UPDATE trips SET deletedAt = :now, updatedAt = :now WHERE vehicleId = :vehicleId AND deletedAt IS NULL")
    suspend fun softDeleteForVehicle(vehicleId: String, now: Long): Int

    @Query("SELECT * FROM trips")
    suspend fun dumpAll(): List<TripEntity>

    @Query("DELETE FROM trips")
    suspend fun deleteAllRows()
}

@Dao
interface FuelPriceDao {
    @Query("SELECT * FROM fuel_prices WHERE deletedAt IS NULL ORDER BY month, grade")
    fun observeActive(): Flow<List<FuelPriceEntity>>

    @Query("SELECT * FROM fuel_prices WHERE month = :month AND grade = :grade AND deletedAt IS NULL")
    suspend fun get(month: String, grade: String): FuelPriceEntity?

    @Query("SELECT * FROM fuel_prices WHERE grade = :grade AND deletedAt IS NULL ORDER BY month DESC LIMIT 1")
    suspend fun latest(grade: String): FuelPriceEntity?

    @Query("SELECT MAX(month) FROM fuel_prices WHERE deletedAt IS NULL")
    suspend fun maxMonth(): String?

    /** Seed insert: never overwrites rows that a refresh already wrote. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entities: List<FuelPriceEntity>)

    @Upsert
    suspend fun upsertAll(entities: List<FuelPriceEntity>)

    @Query("SELECT * FROM fuel_prices")
    suspend fun dumpAll(): List<FuelPriceEntity>

    @Query("DELETE FROM fuel_prices")
    suspend fun deleteAllRows()
}

@Dao
interface ImportBatchDao {
    @Query("SELECT * FROM import_batches WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeActive(): Flow<List<ImportBatchEntity>>

    @Query("SELECT * FROM import_batches WHERE id = :id")
    suspend fun get(id: String): ImportBatchEntity?

    @Upsert
    suspend fun upsert(entity: ImportBatchEntity)

    @Upsert
    suspend fun upsertAll(entities: List<ImportBatchEntity>)

    @Query("UPDATE import_batches SET undoneAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun markUndone(id: String, now: Long): Int

    @Query("SELECT * FROM import_batches")
    suspend fun dumpAll(): List<ImportBatchEntity>

    @Query("DELETE FROM import_batches")
    suspend fun deleteAllRows()
}
