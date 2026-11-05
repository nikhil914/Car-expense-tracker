package ae.sayyara.automotive

import ae.sayyara.core.model.FuelGrade
import ae.sayyara.core.model.YearMonth
import ae.sayyara.core.uae.FuelPriceBook
import android.content.Context
import android.content.SharedPreferences
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Manual DI for the automotive APK (no Application subclass needed). */
object AaosServices {
    @Volatile private var store: PendingRefuelStore? = null
    @Volatile private var prefs: AaosPrefs? = null

    /** Bundled UAE prices from core. A remote refresh arrives with the Phase 2 backend. */
    val prices: FuelPriceBook by lazy { FuelPriceBook.seeded() }

    val uaeZone: TimeZone by lazy { TimeZone.of("Asia/Dubai") }

    fun store(context: Context): PendingRefuelStore =
        store ?: synchronized(this) { store ?: PendingRefuelStore(context.applicationContext).also { store = it } }

    fun prefs(context: Context): AaosPrefs =
        prefs ?: synchronized(this) { prefs ?: AaosPrefs(context.applicationContext).also { prefs = it } }

    fun monthOf(epochMs: Long): YearMonth =
        YearMonth.of(Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(uaeZone).date)
}

/** Last known level reading, used to compare across head-unit sleeps (see RefuelSnapshot). */
data class StoredLevel(
    val atEpochMs: Long,
    val fuelLitres: Double?,
    val fuelPercent: Double?,
    val odometerKm: Double?,
    val socPercent: Double?,
)

class AaosPrefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("aaos_refuel", Context.MODE_PRIVATE)

    /** Fuel grade the driver picked on the Prices screen; used to price detected refuels. */
    var grade: FuelGrade
        get() = sp.getString(KEY_GRADE, null)?.let { runCatching { FuelGrade.valueOf(it) }.getOrNull() } ?: FuelGrade.SPECIAL_95
        set(value) = sp.edit().putString(KEY_GRADE, value.name).apply()

    var permissionPromptShown: Boolean
        get() = sp.getBoolean(KEY_PERM_PROMPT, false)
        set(value) = sp.edit().putBoolean(KEY_PERM_PROMPT, value).apply()

    fun snapshot(): StoredLevel? {
        val at = sp.getLong(KEY_AT, 0L)
        if (at <= 0L) return null
        fun d(key: String) = sp.getFloat(key, Float.NaN).toDouble().takeUnless { it.isNaN() }
        return StoredLevel(at, d(KEY_FUEL_L), d(KEY_FUEL_PCT), d(KEY_ODO), d(KEY_SOC))
    }

    fun saveSnapshot(s: StoredLevel) {
        sp.edit()
            .putLong(KEY_AT, s.atEpochMs)
            .putFloat(KEY_FUEL_L, s.fuelLitres?.toFloat() ?: Float.NaN)
            .putFloat(KEY_FUEL_PCT, s.fuelPercent?.toFloat() ?: Float.NaN)
            .putFloat(KEY_ODO, s.odometerKm?.toFloat() ?: Float.NaN)
            .putFloat(KEY_SOC, s.socPercent?.toFloat() ?: Float.NaN)
            .apply()
    }

    private companion object {
        const val KEY_GRADE = "grade"
        const val KEY_PERM_PROMPT = "perm_prompt_shown"
        const val KEY_AT = "snap_at"
        const val KEY_FUEL_L = "snap_fuel_l"
        const val KEY_FUEL_PCT = "snap_fuel_pct"
        const val KEY_ODO = "snap_odo"
        const val KEY_SOC = "snap_soc"
    }
}
