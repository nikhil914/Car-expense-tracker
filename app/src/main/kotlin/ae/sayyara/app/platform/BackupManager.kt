package ae.sayyara.app.platform

import ae.sayyara.app.BuildConfig
import ae.sayyara.app.data.AppPreferences
import ae.sayyara.app.data.db.EnergyEventEntity
import ae.sayyara.app.data.db.EnergyStoreEntity
import ae.sayyara.app.data.db.ExpenseEntity
import ae.sayyara.app.data.db.FuelPriceEntity
import ae.sayyara.app.data.db.ImportBatchEntity
import ae.sayyara.app.data.db.ReminderEntity
import ae.sayyara.app.data.db.SayyaraDatabase
import ae.sayyara.app.data.db.TripEntity
import ae.sayyara.app.data.db.VehicleEntity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Full logical snapshot of the database (tombstones included, so sync state survives a restore). */
@Serializable
data class BackupSnapshot(
    val formatVersion: Int,
    val schemaVersion: Int,
    val createdAtMs: Long,
    val appVersion: String,
    val selectedVehicleId: String? = null,
    val vehicles: List<VehicleEntity> = emptyList(),
    val stores: List<EnergyStoreEntity> = emptyList(),
    val events: List<EnergyEventEntity> = emptyList(),
    val expenses: List<ExpenseEntity> = emptyList(),
    val reminders: List<ReminderEntity> = emptyList(),
    val trips: List<TripEntity> = emptyList(),
    val fuelPrices: List<FuelPriceEntity> = emptyList(),
    val importBatches: List<ImportBatchEntity> = emptyList(),
)

data class BackupFile(val uri: Uri, val name: String, val sizeBytes: Long, val lastModifiedMs: Long)

data class RestoreSummary(val createdAtMs: Long, val vehicles: Int, val fills: Int, val expenses: Int)

sealed class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class WrongPassphraseOrCorrupt(cause: Throwable?) : BackupException("Wrong passphrase or damaged backup", cause)
    class NotABackup : BackupException("Not a Sayyara backup file")
    class UnsupportedVersion(version: Int) : BackupException("Backup format $version is newer than this app")
    class FolderUnavailable : BackupException("Backup folder is not accessible; pick it again")
    class VerificationFailed : BackupException("Backup written but the test restore did not match")
}

/**
 * Encrypted snapshot backups to a user-chosen Storage Access Framework folder. Google Drive, local
 * storage and other document providers are all reachable through SAF.
 *
 * File format (`sayyara-backup-YYYYMMDD-HHmmss.sayyara`):
 *   MAGIC "SAYYBAK1" | int formatVersion | int pbkdf2Iterations | 16-byte salt | 12-byte IV |
 *   AES-256-GCM( gzip( JSON BackupSnapshot ) ), header bytes authenticated as AAD.
 * The key comes from the user's passphrase (PBKDF2-HMAC-SHA256), so the file restores on a new phone.
 * The passphrase itself is never stored. Each write is verified by decrypting it again.
 */
class BackupManager(
    context: Context,
    private val db: SayyaraDatabase,
    private val prefs: AppPreferences,
) {
    private val appContext = context.applicationContext
    private val random = SecureRandom()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Call with the Uri from ActivityResultContracts.OpenDocumentTree. */
    suspend fun setBackupFolder(treeUri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        appContext.contentResolver.takePersistableUriPermission(treeUri, flags)
        prefs.setBackupFolderUri(treeUri.toString())
    }

    suspend fun backupFolder(): Uri? = prefs.backupFolderUri.first()?.let(Uri::parse)

    /** Writes a new encrypted snapshot, verifies it, and prunes to the newest [keep] files. */
    suspend fun writeSnapshot(passphrase: CharArray, keep: Int = DEFAULT_KEEP): BackupFile = withContext(Dispatchers.IO) {
        val folder = folderOrThrow()
        val snapshot = takeSnapshot()
        val plain = gzip(json.encodeToString(BackupSnapshot.serializer(), snapshot).toByteArray(Charsets.UTF_8))
        val bytes = encrypt(plain, passphrase)

        val name = FILE_PREFIX + stampFormat().format(Date(snapshot.createdAtMs)) + FILE_SUFFIX
        val file = folder.createFile(MIME, name) ?: throw IOException("Could not create $name")
        appContext.contentResolver.openOutputStream(file.uri, "w")?.use { it.write(bytes) }
            ?: throw IOException("Could not open $name for writing")

        // Integrity check by test restore (decrypt + parse, compare counts).
        val check = readSnapshot(file.uri, passphrase)
        if (check.vehicles.size != snapshot.vehicles.size || check.events.size != snapshot.events.size ||
            check.expenses.size != snapshot.expenses.size
        ) {
            throw BackupException.VerificationFailed()
        }

        prune(folder, keep)
        BackupFile(file.uri, name, bytes.size.toLong(), file.lastModified())
    }

    /** Newest first. */
    suspend fun listSnapshots(): List<BackupFile> = withContext(Dispatchers.IO) {
        val folder = folderOrThrow()
        snapshotFiles(folder).map { BackupFile(it.uri, it.name.orEmpty(), it.length(), it.lastModified()) }
    }

    /** Decrypts without writing anything (for a "restore this backup?" preview). */
    suspend fun inspect(fileUri: Uri, passphrase: CharArray): RestoreSummary = withContext(Dispatchers.IO) {
        readSnapshot(fileUri, passphrase).summary()
    }

    /** Replaces all local data with the snapshot in one transaction. */
    suspend fun restore(fileUri: Uri, passphrase: CharArray): RestoreSummary = withContext(Dispatchers.IO) {
        val s = readSnapshot(fileUri, passphrase)
        db.withTransaction {
            db.importBatchDao().deleteAllRows()
            db.tripDao().deleteAllRows()
            db.reminderDao().deleteAllRows()
            db.expenseDao().deleteAllRows()
            db.energyEventDao().deleteAllRows()
            db.energyStoreDao().deleteAllRows()
            db.vehicleDao().deleteAllRows()
            db.vehicleDao().upsertAll(s.vehicles)
            db.energyStoreDao().upsertAll(s.stores)
            db.energyEventDao().upsertAll(s.events)
            db.expenseDao().upsertAll(s.expenses)
            db.reminderDao().upsertAll(s.reminders)
            db.tripDao().upsertAll(s.trips)
            // Prices are reference data: merge rather than wipe, so newer refreshed prices survive.
            db.fuelPriceDao().upsertAll(s.fuelPrices)
            db.importBatchDao().upsertAll(s.importBatches)
        }
        prefs.setSelectedVehicleId(s.selectedVehicleId)
        s.summary()
    }

    // ---- internals

    private suspend fun takeSnapshot(): BackupSnapshot = db.withTransaction {
        BackupSnapshot(
            formatVersion = FORMAT_VERSION,
            schemaVersion = SayyaraDatabase.VERSION,
            createdAtMs = System.currentTimeMillis(),
            appVersion = BuildConfig.VERSION_NAME,
            selectedVehicleId = prefs.selectedVehicleId.first(),
            vehicles = db.vehicleDao().dumpAll(),
            stores = db.energyStoreDao().dumpAll(),
            events = db.energyEventDao().dumpAll(),
            expenses = db.expenseDao().dumpAll(),
            reminders = db.reminderDao().dumpAll(),
            trips = db.tripDao().dumpAll(),
            fuelPrices = db.fuelPriceDao().dumpAll(),
            importBatches = db.importBatchDao().dumpAll(),
        )
    }

    private fun readSnapshot(uri: Uri, passphrase: CharArray): BackupSnapshot {
        val bytes = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("Could not open backup")
        val plain = decrypt(bytes, passphrase)
        return json.decodeFromString(BackupSnapshot.serializer(), String(gunzip(plain), Charsets.UTF_8))
    }

    private suspend fun folderOrThrow(): DocumentFile {
        val uri = backupFolder() ?: throw BackupException.FolderUnavailable()
        val folder = DocumentFile.fromTreeUri(appContext, uri)
        if (folder == null || !folder.canWrite()) throw BackupException.FolderUnavailable()
        return folder
    }

    private fun snapshotFiles(folder: DocumentFile): List<DocumentFile> =
        folder.listFiles()
            .filter { it.isFile && it.name?.startsWith(FILE_PREFIX) == true && it.name?.endsWith(FILE_SUFFIX) == true }
            .sortedByDescending { it.name } // timestamped names sort chronologically

    private fun prune(folder: DocumentFile, keep: Int) {
        snapshotFiles(folder).drop(keep.coerceAtLeast(1)).forEach { it.delete() }
    }

    private fun encrypt(plain: ByteArray, passphrase: CharArray): ByteArray {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val header = ByteArrayOutputStream().also { out ->
            DataOutputStream(out).use { d ->
                d.write(MAGIC)
                d.writeInt(FORMAT_VERSION)
                d.writeInt(PBKDF2_ITERATIONS)
                d.write(salt)
                d.write(iv)
            }
        }.toByteArray()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt, PBKDF2_ITERATIONS), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(header)
        return header + cipher.doFinal(plain)
    }

    private fun decrypt(bytes: ByteArray, passphrase: CharArray): ByteArray {
        val headerLen = MAGIC.size + 4 + 4 + SALT_BYTES + IV_BYTES
        if (bytes.size <= headerLen) throw BackupException.NotABackup()
        val input = DataInputStream(ByteArrayInputStream(bytes))
        val magic = ByteArray(MAGIC.size).also(input::readFully)
        if (!magic.contentEquals(MAGIC)) throw BackupException.NotABackup()
        val version = input.readInt()
        if (version > FORMAT_VERSION) throw BackupException.UnsupportedVersion(version)
        val iterations = input.readInt()
        if (iterations !in 10_000..10_000_000) throw BackupException.NotABackup()
        val salt = ByteArray(SALT_BYTES).also(input::readFully)
        val iv = ByteArray(IV_BYTES).also(input::readFully)
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(bytes, 0, headerLen)
            return cipher.doFinal(bytes, headerLen, bytes.size - headerLen)
        } catch (e: AEADBadTagException) {
            throw BackupException.WrongPassphraseOrCorrupt(e)
        } catch (e: GeneralSecurityException) {
            throw BackupException.WrongPassphraseOrCorrupt(e)
        }
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BITS)
        try {
            val raw = SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded
            return SecretKeySpec(raw, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun gzip(bytes: ByteArray): ByteArray =
        ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(bytes) } }.toByteArray()

    private fun gunzip(bytes: ByteArray): ByteArray = GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }

    private fun BackupSnapshot.summary() = RestoreSummary(
        createdAtMs = createdAtMs,
        vehicles = vehicles.count { it.deletedAt == null },
        fills = events.count { it.deletedAt == null },
        expenses = expenses.count { it.deletedAt == null },
    )

    private fun stampFormat() = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

    companion object {
        const val DEFAULT_KEEP = 7
        private const val FORMAT_VERSION = 1
        private const val FILE_PREFIX = "sayyara-backup-"
        private const val FILE_SUFFIX = ".sayyara"
        private const val MIME = "application/octet-stream"
        private val MAGIC = "SAYYBAK1".toByteArray(Charsets.US_ASCII)
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KDF = "PBKDF2WithHmacSHA256" // available from API 26 (our minSdk)
        private const val PBKDF2_ITERATIONS = 210_000
        private const val KEY_BITS = 256
        private const val TAG_BITS = 128
        private const val SALT_BYTES = 16
        private const val IV_BYTES = 12
    }
}
