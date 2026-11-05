package ae.sayyara.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        VehicleEntity::class,
        EnergyStoreEntity::class,
        EnergyEventEntity::class,
        ExpenseEntity::class,
        ReminderEntity::class,
        TripEntity::class,
        FuelPriceEntity::class,
        ImportBatchEntity::class,
    ],
    version = SayyaraDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class SayyaraDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao
    abstract fun energyStoreDao(): EnergyStoreDao
    abstract fun energyEventDao(): EnergyEventDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun reminderDao(): ReminderDao
    abstract fun tripDao(): TripDao
    abstract fun fuelPriceDao(): FuelPriceDao
    abstract fun importBatchDao(): ImportBatchDao

    companion object {
        const val VERSION = 1

        /** File name; referenced by res/xml/backup_rules.xml and data_extraction_rules.xml. */
        const val FILE_NAME = "sayyara.db"

        fun build(context: Context): SayyaraDatabase =
            Room.databaseBuilder(context.applicationContext, SayyaraDatabase::class.java, FILE_NAME)
                // Add Migration objects here when VERSION is bumped; schemas are exported to app/schemas.
                .build()
    }
}
