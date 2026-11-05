package ae.sayyara.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** One DataStore per process. File: files/datastore/sayyara_prefs.preferences_pb (backed up). */
private val Context.sayyaraDataStore: DataStore<Preferences> by preferencesDataStore(name = "sayyara_prefs")

/** Small key/value settings owned by the data + platform layer. */
class AppPreferences(context: Context) {
    private val store = context.applicationContext.sayyaraDataStore

    private val data: Flow<Preferences> = store.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    val selectedVehicleId: Flow<String?> = data.map { it[SELECTED_VEHICLE] }

    suspend fun setSelectedVehicleId(id: String?) {
        store.edit { prefs -> if (id == null) prefs.remove(SELECTED_VEHICLE) else prefs[SELECTED_VEHICLE] = id }
    }

    /** Last month ("YYYY-MM") for which the "new fuel prices" notification was posted. */
    suspend fun lastPriceMonthNotified(): String? = data.first()[LAST_PRICE_MONTH_NOTIFIED]

    suspend fun setLastPriceMonthNotified(month: String) {
        store.edit { it[LAST_PRICE_MONTH_NOTIFIED] = month }
    }

    /** SAF tree Uri chosen for encrypted backups. */
    val backupFolderUri: Flow<String?> = data.map { it[BACKUP_TREE_URI] }

    suspend fun setBackupFolderUri(uri: String?) {
        store.edit { prefs -> if (uri == null) prefs.remove(BACKUP_TREE_URI) else prefs[BACKUP_TREE_URI] = uri }
    }

    /** Epoch day of the last notification for a reminder, used to throttle repeats. */
    suspend fun reminderNotifiedEpochDay(reminderId: String): Long? = data.first()[reminderKey(reminderId)]

    suspend fun setReminderNotifiedEpochDay(reminderId: String, epochDay: Long) {
        store.edit { it[reminderKey(reminderId)] = epochDay }
    }

    private fun reminderKey(id: String) = longPreferencesKey("reminder_notified_$id")

    private companion object {
        val SELECTED_VEHICLE = stringPreferencesKey("selected_vehicle_id")
        val LAST_PRICE_MONTH_NOTIFIED = stringPreferencesKey("last_price_month_notified")
        val BACKUP_TREE_URI = stringPreferencesKey("backup_tree_uri")
    }
}
