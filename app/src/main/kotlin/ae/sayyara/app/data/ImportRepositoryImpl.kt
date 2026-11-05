package ae.sayyara.app.data

import ae.sayyara.app.R
import ae.sayyara.app.data.db.ImportBatchEntity
import ae.sayyara.app.data.db.SayyaraDatabase
import ae.sayyara.app.data.db.toEntity
import ae.sayyara.app.data.db.toModel
import ae.sayyara.core.importer.CsvExporter
import ae.sayyara.core.importer.FuelioImport
import ae.sayyara.core.importer.FuelioImporter
import ae.sayyara.core.model.Id
import android.content.Context
import androidx.room.withTransaction
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Fuelio import: `.csv`, `.csv.zip`, or a zip of a whole `backup-sync` folder
 * (any `vehicle-<N>-sync.csv`, including nested `vehicle-<N>-sync.csv.zip`).
 * Parsed batches stay in memory until committed; nothing is written during preview.
 */
class RoomImportRepository(
    context: Context,
    private val db: SayyaraDatabase,
    private val prefs: AppPreferences,
    private val clock: NowProvider = SystemNow,
    private val newId: () -> Id = { UUID.randomUUID().toString() },
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ImportRepository {

    private val appContext = context.applicationContext
    private val lock = Mutex()
    private val pending = LinkedHashMap<Id, PendingImport>()

    private class PendingImport(val fileName: String, val imports: List<FuelioImport>)

    override suspend fun preview(fileName: String, bytes: ByteArray): ImportResult = withContext(io) {
        val csvs = try {
            extractCsvFiles(fileName, bytes)
        } catch (e: IOException) {
            return@withContext ImportResult.Failed(appContext.getString(R.string.import_error_unreadable, e.message ?: ""))
        } catch (e: IllegalArgumentException) { // malformed zip entry names
            return@withContext ImportResult.Failed(appContext.getString(R.string.import_error_unreadable, e.message ?: ""))
        }
        if (csvs.isEmpty()) return@withContext ImportResult.Failed(appContext.getString(R.string.import_error_no_csv))

        val batchId = newId()
        val now = clock.now()
        val imports = try {
            csvs.map { (name, text) -> FuelioImporter.parse(text, name, batchId, now, newId) }
        } catch (e: Exception) {
            // The parser owns its error types; surface any of them as a readable failure.
            return@withContext ImportResult.Failed(
                appContext.getString(R.string.import_error_unreadable, e.message ?: e.javaClass.simpleName),
            )
        }
        if (imports.all { it.events.isEmpty() && it.expenses.isEmpty() && it.reminders.isEmpty() }) {
            return@withContext ImportResult.Failed(appContext.getString(R.string.import_error_empty))
        }

        val plan = ImportDedupe.plan(imports, loadExisting())
        lock.withLock {
            pending[batchId] = PendingImport(fileName, imports)
            while (pending.size > MAX_PENDING) pending.remove(pending.keys.first())
        }
        ImportResult.Preview(
            batchId = batchId,
            vehicles = plan.vehiclesInFile,
            fills = plan.events.size,
            expenses = plan.expenses.size,
            warnings = previewWarnings(plan),
        )
    }

    override suspend fun commit(batchId: Id) {
        val item = lock.withLock { pending.remove(batchId) }
            ?: throw IllegalStateException("Import batch $batchId is unknown or was already committed")
        val nowMs = clock.now().toEpochMilliseconds()
        var firstVehicleId: Id? = null
        db.withTransaction {
            // Re-run dedupe inside the transaction so data added since preview is respected.
            val plan = ImportDedupe.plan(item.imports, loadExisting())
            db.vehicleDao().upsertAll(plan.newVehicles.map { it.toEntity(importBatchId = batchId) })
            db.energyStoreDao().upsertAll(plan.newStores.map { it.toEntity(updatedAt = nowMs, importBatchId = batchId) })
            db.energyEventDao().upsertAll(plan.events.map { it.copy(importBatchId = batchId).toEntity() })
            db.expenseDao().upsertAll(plan.expenses.map { it.copy(importBatchId = batchId).toEntity() })
            db.reminderDao().upsertAll(plan.reminders.map { it.toEntity(updatedAt = nowMs, importBatchId = batchId) })
            val stats = buildJsonObject {
                put("vehiclesInFile", plan.vehiclesInFile)
                put("newVehicles", plan.newVehicles.size)
                put("mergedVehicles", plan.mergedInto.size)
                put("stores", plan.newStores.size)
                put("fills", plan.events.size)
                put("expenses", plan.expenses.size)
                put("reminders", plan.reminders.size)
                put("skippedFills", plan.skippedEvents)
                put("skippedExpenses", plan.skippedExpenses)
                put("skippedReminders", plan.skippedReminders)
                put("warnings", plan.warnings.size)
            }
            db.importBatchDao().upsert(
                ImportBatchEntity(
                    id = batchId, source = SOURCE_FUELIO, fileName = item.fileName, statsJson = stats.toString(),
                    createdAt = nowMs, undoneAt = null, updatedAt = nowMs, deletedAt = null,
                ),
            )
            firstVehicleId = plan.newVehicles.firstOrNull()?.id ?: plan.mergedInto.firstOrNull()?.second?.id
        }
        val selected = prefs.selectedVehicleId.first()
        if (selected == null || db.vehicleDao().get(selected)?.deletedAt != null) {
            firstVehicleId?.let { prefs.setSelectedVehicleId(it) }
        }
    }

    override suspend fun undo(batchId: Id) {
        val nowMs = clock.now().toEpochMilliseconds()
        db.withTransaction {
            db.energyEventDao().softDeleteBatch(batchId, nowMs)
            db.expenseDao().softDeleteBatch(batchId, nowMs)
            db.reminderDao().softDeleteBatch(batchId, nowMs)
            db.energyStoreDao().softDeleteBatch(batchId, nowMs)
            db.vehicleDao().softDeleteBatch(batchId, nowMs)
            db.importBatchDao().markUndone(batchId, nowMs)
        }
        val selected = prefs.selectedVehicleId.first()
        if (selected != null && db.vehicleDao().get(selected)?.deletedAt != null) {
            prefs.setSelectedVehicleId(db.vehicleDao().getActive().firstOrNull()?.id)
        }
    }

    override suspend fun exportCsv(): ByteArray = withContext(io) {
        val vehicles = db.vehicleDao().getActive().map { it.toModel() }
        val stores = db.energyStoreDao().getActive().map { it.toModel() }
        val events = db.energyEventDao().getActive().map { it.toModel() }
        val expenses = db.expenseDao().getActive().map { it.toModel() }
        CsvExporter.export(vehicles, stores, events, expenses).toByteArray(Charsets.UTF_8)
    }

    private suspend fun loadExisting(): List<ExistingVehicleData> =
        db.vehicleDao().getActive().map { v ->
            ExistingVehicleData(
                vehicle = v.toModel(),
                stores = db.energyStoreDao().getForVehicle(v.id).map { it.toModel() },
                events = db.energyEventDao().getForVehicle(v.id).map { it.toModel() },
                expenses = db.expenseDao().getForVehicle(v.id).map { it.toModel() },
                reminders = db.reminderDao().getForVehicle(v.id).map { it.toModel() },
            )
        }

    private fun previewWarnings(plan: ImportPlan): List<String> = buildList {
        plan.mergedInto.forEach { (_, existing) ->
            add(appContext.getString(R.string.import_warning_merged_vehicle, existing.name))
        }
        if (plan.skippedEvents + plan.skippedExpenses + plan.skippedReminders > 0) {
            add(
                appContext.getString(
                    R.string.import_warning_duplicates,
                    plan.skippedEvents, plan.skippedExpenses, plan.skippedReminders,
                ),
            )
        }
        addAll(plan.warnings)
    }

    companion object {
        const val SOURCE_FUELIO = "fuelio"
        private const val MAX_PENDING = 4
        private const val MAX_ENTRY_BYTES = 32 * 1024 * 1024
        private const val MAX_ZIP_DEPTH = 2
        private val FUELIO_SYNC_CSV = Regex("""(^|/)vehicle-[^/]*-sync\.csv$""", RegexOption.IGNORE_CASE)

        /** Returns (fileName, text) pairs. Visible for tests. */
        @Throws(IOException::class)
        fun extractCsvFiles(fileName: String, bytes: ByteArray): List<Pair<String, String>> {
            if (!isZip(bytes)) return listOf(fileName to decodeText(bytes))
            val all = mutableListOf<Pair<String, String>>()
            readZip(bytes, all, depth = 0)
            val sync = all.filter { FUELIO_SYNC_CSV.containsMatchIn(it.first) }
            return sync.ifEmpty { all }
        }

        private fun readZip(bytes: ByteArray, out: MutableList<Pair<String, String>>, depth: Int) {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val name = entry.name
                    val lower = name.lowercase()
                    // Skip macOS resource forks.
                    if (lower.startsWith("__macosx/") || lower.substringAfterLast('/').startsWith("._")) continue
                    when {
                        lower.endsWith(".csv") -> out += name to decodeText(zis.readNBytesCompat(MAX_ENTRY_BYTES))
                        lower.endsWith(".zip") && depth < MAX_ZIP_DEPTH ->
                            readZip(zis.readNBytesCompat(MAX_ENTRY_BYTES), out, depth + 1)
                    }
                    zis.closeEntry()
                }
            }
        }

        private fun isZip(bytes: ByteArray): Boolean =
            bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() &&
                bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()

        /** UTF-8 with BOM tolerated (Fuelio writes plain UTF-8; Excel round-trips add a BOM). */
        private fun decodeText(bytes: ByteArray): String = String(bytes, Charsets.UTF_8).removePrefix("﻿")
    }
}
