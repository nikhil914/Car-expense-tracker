package ae.sayyara.app.data.db

import ae.sayyara.core.model.Emirate
import ae.sayyara.core.model.EnergyStoreKind
import ae.sayyara.core.model.EnergyUnit
import ae.sayyara.core.model.ExpenseCategoryKey
import ae.sayyara.core.model.ExpenseSource
import ae.sayyara.core.model.FuelGrade
import ae.sayyara.core.model.LatLon
import ae.sayyara.core.model.LocationType
import ae.sayyara.core.model.MeterSource
import ae.sayyara.core.model.PriceSource
import ae.sayyara.core.model.PriceStatus
import ae.sayyara.core.model.ReminderKind
import ae.sayyara.core.model.TripPurpose
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/*
 * Room entities. Every row carries `updatedAt` (epoch ms) and `deletedAt` (epoch ms tombstone)
 * so a future PowerSync / last-writer-wins sync can propagate edits and deletes.
 * Timestamps are stored as epoch milliseconds (UTC) to keep SQL ordering and range queries simple.
 * Entities are also @Serializable: BackupManager snapshots them verbatim (tombstones included).
 */

@Serializable
@Entity(tableName = "vehicles", indices = [Index("importBatchId")])
data class VehicleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val make: String?,
    val model: String?,
    val year: Int?,
    val plate: String?,
    val emirate: Emirate?,
    val vin: String?,
    val defaultGrade: FuelGrade,
    val purchaseDate: LocalDate?,
    val purchasePriceAed: Double?,
    val active: Boolean,
    val importBatchId: String?,
    val updatedAt: Long,
    val deletedAt: Long?,
)

@Serializable
@Entity(tableName = "energy_stores", indices = [Index("vehicleId"), Index("importBatchId")])
data class EnergyStoreEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    val kind: EnergyStoreKind,
    val unit: EnergyUnit,
    val capacity: Double?,
    val usableCapacity: Double?,
    val defaultGrade: FuelGrade,
    val importBatchId: String?,
    val updatedAt: Long,
    val deletedAt: Long?,
)

@Serializable
@Entity(
    tableName = "energy_events",
    indices = [Index("vehicleId", "timestamp"), Index("storeId"), Index("importBatchId")],
)
data class EnergyEventEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    val storeId: String,
    val timestamp: Long,
    val odometerKm: Double,
    val amount: Double,
    val unitPriceAed: Double?,
    val totalAed: Double?,
    val isFull: Boolean,
    val missedPrevious: Boolean,
    val levelBefore: Double?,
    val levelAfter: Double?,
    val socBefore: Double?,
    val socAfter: Double?,
    val grade: FuelGrade,
    val locationType: LocationType,
    val stationName: String?,
    val latitude: Double?,
    val longitude: Double?,
    val meterSource: MeterSource,
    val serviceFeeAed: Double,
    val priceSource: PriceSource,
    val note: String?,
    val importBatchId: String?,
    val updatedAt: Long,
    val deletedAt: Long?,
)

@Serializable
@Entity(tableName = "expenses", indices = [Index("vehicleId", "timestamp"), Index("importBatchId")])
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    val timestamp: Long,
    val odometerKm: Double?,
    val category: ExpenseCategoryKey,
    val customCategory: String?,
    val amountAed: Double,
    val vatAed: Double?,
    val isIncome: Boolean,
    val source: ExpenseSource,
    val title: String?,
    val note: String?,
    val importBatchId: String?,
    val updatedAt: Long,
    val deletedAt: Long?,
)

@Serializable
@Entity(tableName = "reminders", indices = [Index("vehicleId"), Index("importBatchId")])
data class ReminderEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    val kind: ReminderKind,
    val title: String,
    val dueDate: LocalDate?,
    val dueOdometerKm: Double?,
    val repeatMonths: Int?,
    val repeatKm: Double?,
    val leadDays: Int,
    val chainedToId: String?,
    val done: Boolean,
    val importBatchId: String?,
    val updatedAt: Long,
    val deletedAt: Long?,
)

@Serializable
@Entity(tableName = "trips", indices = [Index("vehicleId", "startedAt")])
data class TripEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    val startedAt: Long,
    val endedAt: Long,
    val km: Double,
    val purpose: TripPurpose,
    val polyline: List<LatLon>,
    val fuelCostAed: Double?,
    val tollAed: Double?,
    val parkingAed: Double?,
    val updatedAt: Long,
    val deletedAt: Long?,
)

/** Reference data. [month] is "YYYY-MM" (zero padded) so string order == chronological order. */
@Serializable
@Entity(tableName = "fuel_prices", primaryKeys = ["month", "grade"])
data class FuelPriceEntity(
    val month: String,
    val grade: FuelGrade,
    val aedPerLitre: Double,
    val status: PriceStatus,
    val sourceUrl: String?,
    val updatedAt: Long,
    val deletedAt: Long?,
)

@Serializable
@Entity(tableName = "import_batches")
data class ImportBatchEntity(
    @PrimaryKey val id: String,
    /** "fuelio" | "csv" | ... */
    val source: String,
    val fileName: String?,
    val statsJson: String,
    val createdAt: Long,
    val undoneAt: Long?,
    val updatedAt: Long,
    val deletedAt: Long?,
)
