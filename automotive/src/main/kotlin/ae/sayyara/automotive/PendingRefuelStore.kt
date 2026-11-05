package ae.sayyara.automotive

import ae.sayyara.core.model.FuelGrade
import ae.sayyara.core.refuel.DetectedCharge
import ae.sayyara.core.refuel.DetectedRefuel
import android.content.Context
import android.util.AtomicFile
import android.util.Log
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** A refuel or charge detected on the head unit, waiting for the driver to confirm or dismiss it. */
data class PendingRefuel(
    val id: String,
    val kind: Kind,
    val atEpochMs: Long,
    /** Litres for FUEL, battery-side kWh for CHARGE (null when the battery capacity is unknown). */
    val amount: Double?,
    val levelBeforePercent: Double,
    val levelAfterPercent: Double,
    val likelyFull: Boolean,
    val odometerKm: Double?,
    val confidence: Double,
    /** Core trigger ("fuel_door", "ignition", "gap", "level_rise") or "charge_port" for charges. */
    val trigger: String,
    /** "live" (CarPropertyManager callbacks) or "snapshot" (periodic job). */
    val source: String,
    val status: Status = Status.PENDING,
    val grade: FuelGrade? = null,
    val unitPriceAed: Double? = null,
    val totalAed: Double? = null,
    val decidedAtEpochMs: Long? = null,
) {
    enum class Kind { FUEL, CHARGE }
    enum class Status { PENDING, CONFIRMED, DISMISSED }

    companion object {
        fun from(r: DetectedRefuel, source: String) = PendingRefuel(
            id = UUID.randomUUID().toString(),
            kind = Kind.FUEL,
            atEpochMs = r.at.toEpochMilliseconds(),
            amount = r.litresAdded,
            levelBeforePercent = r.levelBeforePercent,
            levelAfterPercent = r.levelAfterPercent,
            likelyFull = r.likelyFull,
            odometerKm = r.odometerKm,
            confidence = r.confidence,
            trigger = r.trigger,
            source = source,
        )

        fun from(c: DetectedCharge, source: String) = PendingRefuel(
            id = UUID.randomUUID().toString(),
            kind = Kind.CHARGE,
            atEpochMs = c.at.toEpochMilliseconds(),
            amount = c.kwhAddedToBattery,
            levelBeforePercent = c.socBeforePercent,
            levelAfterPercent = c.socAfterPercent,
            likelyFull = c.likelyFull,
            odometerKm = c.odometerKm,
            confidence = c.confidence,
            // The charge port is fed to the detector as the "door" (see AaosRefuelMonitor), so relabel it.
            trigger = if (c.trigger == "fuel_door") "charge_port" else c.trigger,
            source = source,
        )
    }
}

/**
 * The head unit's own small store: ONE JSON file (`files/pending_refuels.json`) of detected refuels and charges.
 *
 * The automotive APK does not depend on `:app` and has no Room database. Confirmed entries stay here, marked
 * CONFIRMED. PHASE 2: they will sync to the phone app through the cloud backend (same Sayyara account), which will
 * turn them into EnergyEvents with priceSource = OFFICIAL_MONTH and note "auto-detected (car)". Until then, the
 * car is the only place they are visible.
 *
 * Thread-safe (all methods synchronized). AtomicFile keeps the file valid if the head unit loses power mid-write.
 * Callers do the I/O off the main thread.
 */
class PendingRefuelStore(context: Context) {
    private val file = AtomicFile(File(context.applicationContext.filesDir, FILE_NAME))
    private val _version = MutableStateFlow(0)
    /** Bumps on every change, so screens can reload. */
    val version: StateFlow<Int> = _version.asStateFlow()

    @Synchronized
    fun all(): List<PendingRefuel> = read()

    @Synchronized
    fun pending(): List<PendingRefuel> = read().filter { it.status == PendingRefuel.Status.PENDING }.sortedByDescending { it.atEpochMs }

    @Synchronized
    fun get(id: String): PendingRefuel? = read().firstOrNull { it.id == id }

    /**
     * Adds [item] unless it duplicates an entry of the same kind within [DEDUPE_WINDOW_MS] with a similar level
     * after. This happens when the live monitor and the snapshot job both see the same refuel.
     * Returns true when the item was added.
     */
    @Synchronized
    fun add(item: PendingRefuel): Boolean {
        val items = read().toMutableList()
        val dup = items.any {
            it.kind == item.kind && abs(it.atEpochMs - item.atEpochMs) < DEDUPE_WINDOW_MS &&
                abs(it.levelAfterPercent - item.levelAfterPercent) < 5.0
        }
        if (dup) return false
        items += item
        write(prune(items))
        return true
    }

    @Synchronized
    fun update(id: String, transform: (PendingRefuel) -> PendingRefuel): PendingRefuel? {
        val items = read().toMutableList()
        val i = items.indexOfFirst { it.id == id }
        if (i < 0) return null
        val updated = transform(items[i])
        items[i] = updated
        write(items)
        return updated
    }

    /** Keep at most [MAX_ITEMS]; dismissed entries are dropped first (confirmed ones wait for Phase 2 sync). */
    private fun prune(items: List<PendingRefuel>): List<PendingRefuel> {
        if (items.size <= MAX_ITEMS) return items
        val dismissed = items.filter { it.status == PendingRefuel.Status.DISMISSED }.sortedBy { it.atEpochMs }
        val drop = dismissed.take(items.size - MAX_ITEMS).map { it.id }.toSet()
        val kept = items.filterNot { it.id in drop }
        return if (kept.size <= MAX_ITEMS) kept else kept.sortedBy { it.atEpochMs }.takeLast(MAX_ITEMS)
    }

    private fun read(): List<PendingRefuel> = try {
        val text = file.readFully().toString(Charsets.UTF_8)
        val root = JSONObject(text)
        val arr = root.optJSONArray("items") ?: JSONArray()
        (0 until arr.length()).mapNotNull { i -> runCatching { fromJson(arr.getJSONObject(i)) }.getOrNull() }
    } catch (e: FileNotFoundException) {
        emptyList()
    } catch (e: Exception) {
        Log.w(TAG, "Unreadable $FILE_NAME, starting empty", e)
        emptyList()
    }

    private fun write(items: List<PendingRefuel>) {
        val root = JSONObject()
            .put("schema", SCHEMA)
            .put("items", JSONArray().apply { items.forEach { put(toJson(it)) } })
        val out = file.startWrite()
        try {
            out.write(root.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            throw e
        }
        _version.value = _version.value + 1
    }

    private fun toJson(p: PendingRefuel) = JSONObject().apply {
        put("id", p.id)
        put("kind", p.kind.name)
        put("at", p.atEpochMs)
        p.amount?.let { put("amount", it) }
        put("before", p.levelBeforePercent)
        put("after", p.levelAfterPercent)
        put("full", p.likelyFull)
        p.odometerKm?.let { put("odo", it) }
        put("confidence", p.confidence)
        put("trigger", p.trigger)
        put("source", p.source)
        put("status", p.status.name)
        p.grade?.let { put("grade", it.name) }
        p.unitPriceAed?.let { put("unitPriceAed", it) }
        p.totalAed?.let { put("totalAed", it) }
        p.decidedAtEpochMs?.let { put("decidedAt", it) }
    }

    private fun fromJson(o: JSONObject) = PendingRefuel(
        id = o.getString("id"),
        kind = PendingRefuel.Kind.valueOf(o.getString("kind")),
        atEpochMs = o.getLong("at"),
        amount = o.optDoubleOrNull("amount"),
        levelBeforePercent = o.getDouble("before"),
        levelAfterPercent = o.getDouble("after"),
        likelyFull = o.optBoolean("full"),
        odometerKm = o.optDoubleOrNull("odo"),
        confidence = o.optDouble("confidence", 0.5),
        trigger = o.optString("trigger"),
        source = o.optString("source"),
        status = runCatching { PendingRefuel.Status.valueOf(o.getString("status")) }.getOrDefault(PendingRefuel.Status.PENDING),
        grade = o.optStringOrNull("grade")?.let { g -> runCatching { FuelGrade.valueOf(g) }.getOrNull() },
        unitPriceAed = o.optDoubleOrNull("unitPriceAed"),
        totalAed = o.optDoubleOrNull("totalAed"),
        decidedAtEpochMs = if (o.has("decidedAt")) o.getLong("decidedAt") else null,
    )

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key).takeUnless { it.isNaN() } else null

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null

    private companion object {
        const val TAG = "PendingRefuelStore"
        const val FILE_NAME = "pending_refuels.json"
        const val SCHEMA = 1
        const val MAX_ITEMS = 200
        const val DEDUPE_WINDOW_MS = 30L * 60 * 1000
    }
}
