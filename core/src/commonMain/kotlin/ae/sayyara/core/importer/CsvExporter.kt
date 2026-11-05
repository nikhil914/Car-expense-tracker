package ae.sayyara.core.importer

import ae.sayyara.core.model.Emirate
import ae.sayyara.core.model.EnergyEvent
import ae.sayyara.core.model.EnergyStore
import ae.sayyara.core.model.EnergyStoreKind
import ae.sayyara.core.model.EnergyUnit
import ae.sayyara.core.model.Expense
import ae.sayyara.core.model.ExpenseCategoryKey
import ae.sayyara.core.model.ExpenseSource
import ae.sayyara.core.model.FuelGrade
import ae.sayyara.core.model.LocationType
import ae.sayyara.core.model.MeterSource
import ae.sayyara.core.model.PriceSource
import ae.sayyara.core.model.Reminder
import ae.sayyara.core.model.ReminderKind
import ae.sayyara.core.model.Vehicle
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/**
 * Sayyara's own sectioned CSV (UTF-8, RFC 4180, comma separated).
 *
 * ```
 * ## Sayyara
 * format,version
 * sayyara-csv,1
 * ## Vehicles
 * id,name,...
 * ## EnergyStores
 * ## EnergyEvents
 * ## Expenses
 * ## Reminders        (only from the overload that takes reminders)
 * ```
 * Units are always km / litres or kWh / AED. Instants are ISO-8601 UTC, dates ISO yyyy-MM-dd,
 * enums by name, null as an empty cell. Doubles use Kotlin's shortest round-trip form, so
 * [SayyaraCsv.parse] restores exactly what was exported (empty strings read back as null).
 */
object CsvExporter {
    const val FORMAT = "sayyara-csv"
    const val VERSION = 1

    internal val VEHICLE_COLS = listOf(
        "id", "name", "make", "model", "year", "plate", "emirate", "vin", "defaultGrade",
        "purchaseDate", "purchasePriceAed", "active", "updatedAt", "deletedAt",
    )
    internal val STORE_COLS = listOf("id", "vehicleId", "kind", "unit", "capacity", "usableCapacity", "defaultGrade")
    internal val EVENT_COLS = listOf(
        "id", "vehicleId", "storeId", "timestamp", "odometerKm", "amount", "unitPriceAed", "totalAed", "isFull",
        "missedPrevious", "levelBefore", "levelAfter", "socBefore", "socAfter", "grade", "locationType",
        "stationName", "latitude", "longitude", "meterSource", "serviceFeeAed", "priceSource", "note",
        "importBatchId", "updatedAt", "deletedAt",
    )
    internal val EXPENSE_COLS = listOf(
        "id", "vehicleId", "timestamp", "odometerKm", "category", "customCategory", "amountAed", "vatAed",
        "isIncome", "source", "title", "note", "importBatchId", "updatedAt", "deletedAt",
    )
    internal val REMINDER_COLS = listOf(
        "id", "vehicleId", "kind", "title", "dueDate", "dueOdometerKm", "repeatMonths", "repeatKm", "leadDays",
        "chainedToId", "done",
    )

    fun export(vehicles: List<Vehicle>, stores: List<EnergyStore>, events: List<EnergyEvent>, expenses: List<Expense>): String =
        export(vehicles, stores, events, expenses, null)

    fun export(
        vehicles: List<Vehicle>, stores: List<EnergyStore>, events: List<EnergyEvent>, expenses: List<Expense>,
        reminders: List<Reminder>?,
    ): String {
        val sb = StringBuilder()
        fun line(cells: List<String>) { sb.append(Csv.writeRow(cells)).append("\r\n") }
        fun section(name: String, cols: List<String>, rows: List<List<Any?>>) {
            line(listOf("## $name")); line(cols)
            rows.forEach { r -> line(r.map(::cell)) }
        }
        section("Sayyara", listOf("format", "version"), listOf(listOf(FORMAT, VERSION)))
        section("Vehicles", VEHICLE_COLS, vehicles.map {
            listOf(it.id, it.name, it.make, it.model, it.year, it.plate, it.emirate, it.vin, it.defaultGrade,
                it.purchaseDate, it.purchasePriceAed, it.active, it.updatedAt, it.deletedAt)
        })
        section("EnergyStores", STORE_COLS, stores.map {
            listOf(it.id, it.vehicleId, it.kind, it.unit, it.capacity, it.usableCapacity, it.defaultGrade)
        })
        section("EnergyEvents", EVENT_COLS, events.map {
            listOf(it.id, it.vehicleId, it.storeId, it.timestamp, it.odometerKm, it.amount, it.unitPriceAed, it.totalAed,
                it.isFull, it.missedPrevious, it.levelBefore, it.levelAfter, it.socBefore, it.socAfter, it.grade,
                it.locationType, it.stationName, it.latitude, it.longitude, it.meterSource, it.serviceFeeAed,
                it.priceSource, it.note, it.importBatchId, it.updatedAt, it.deletedAt)
        })
        section("Expenses", EXPENSE_COLS, expenses.map {
            listOf(it.id, it.vehicleId, it.timestamp, it.odometerKm, it.category, it.customCategory, it.amountAed,
                it.vatAed, it.isIncome, it.source, it.title, it.note, it.importBatchId, it.updatedAt, it.deletedAt)
        })
        if (reminders != null) section("Reminders", REMINDER_COLS, reminders.map {
            listOf(it.id, it.vehicleId, it.kind, it.title, it.dueDate, it.dueOdometerKm, it.repeatMonths, it.repeatKm,
                it.leadDays, it.chainedToId, it.done)
        })
        return sb.toString()
    }

    private fun cell(v: Any?): String = when (v) {
        null -> ""
        is Enum<*> -> v.name
        else -> v.toString() // Double, Int, Boolean, Instant (ISO), LocalDate (ISO), String
    }
}

data class SayyaraCsvData(
    val vehicles: List<Vehicle>,
    val stores: List<EnergyStore>,
    val events: List<EnergyEvent>,
    val expenses: List<Expense>,
    val reminders: List<Reminder>,
    val warnings: List<String>,
)

/** Reader for [CsvExporter] output. Columns are matched by name, so added/reordered columns are fine. */
object SayyaraCsv {

    fun isSayyaraCsv(text: String): Boolean =
        Csv.stripBom(text).trimStart().lineSequence().firstOrNull()?.trim()?.trim('"')?.startsWith("## Sayyara") == true

    fun parse(text: String): SayyaraCsvData {
        val warnings = ArrayList<String>()
        val sections = HashMap<String, Pair<Map<String, Int>, List<List<String>>>>()
        var name: String? = null
        var header: Map<String, Int>? = null
        var rows = ArrayList<List<String>>()
        fun flush() {
            val n = name ?: return
            sections[n] = (header ?: emptyMap()) to rows
        }
        for (row in Csv.parse(text)) {
            if (row.all { it.isEmpty() }) continue
            val first = row.first()
            if (first.startsWith("## ") && row.drop(1).all { it.isEmpty() }) {
                flush(); name = first.removePrefix("## ").trim().lowercase(); header = null; rows = ArrayList(); continue
            }
            if (name == null) continue
            if (header == null) header = row.withIndex().associate { it.value.trim() to it.index } else rows.add(row)
        }
        flush()
        val meta = sections["sayyara"]
        if (meta == null) warnings.add("Missing ## Sayyara header section.")
        else {
            val ver = meta.second.firstOrNull()?.let { r -> meta.first["version"]?.let { r.getOrNull(it) } }?.toIntOrNull()
            if (ver != null && ver > CsvExporter.VERSION) warnings.add("File version $ver is newer than supported ${CsvExporter.VERSION}.")
        }

        fun <T> read(section: String, map: (Row) -> T): List<T> {
            val (h, rs) = sections[section] ?: return emptyList()
            val out = ArrayList<T>()
            rs.forEachIndexed { i, r ->
                try { out.add(map(Row(h, r))) } catch (e: Exception) {
                    warnings.add("$section row ${i + 1} skipped: ${e.message}")
                }
            }
            return out
        }

        val vehicles = read("vehicles") { r ->
            Vehicle(
                id = r.req("id"), name = r.req("name"), make = r.str("make"), model = r.str("model"),
                year = r.int("year"), plate = r.str("plate"), emirate = r.enum<Emirate>("emirate"), vin = r.str("vin"),
                defaultGrade = r.enum<FuelGrade>("defaultGrade") ?: FuelGrade.SPECIAL_95,
                purchaseDate = r.date("purchaseDate"), purchasePriceAed = r.dbl("purchasePriceAed"),
                active = r.bool("active") ?: true, updatedAt = r.instant("updatedAt") ?: error("updatedAt"),
                deletedAt = r.instant("deletedAt"),
            )
        }
        val stores = read("energystores") { r ->
            EnergyStore(
                id = r.req("id"), vehicleId = r.req("vehicleId"),
                kind = r.enum<EnergyStoreKind>("kind") ?: EnergyStoreKind.TANK,
                unit = r.enum<EnergyUnit>("unit") ?: EnergyUnit.LITRE,
                capacity = r.dbl("capacity"), usableCapacity = r.dbl("usableCapacity"),
                defaultGrade = r.enum<FuelGrade>("defaultGrade") ?: FuelGrade.SPECIAL_95,
            )
        }
        val events = read("energyevents") { r ->
            EnergyEvent(
                id = r.req("id"), vehicleId = r.req("vehicleId"), storeId = r.req("storeId"),
                timestamp = r.instant("timestamp") ?: error("timestamp"),
                odometerKm = r.dbl("odometerKm") ?: error("odometerKm"), amount = r.dbl("amount") ?: error("amount"),
                unitPriceAed = r.dbl("unitPriceAed"), totalAed = r.dbl("totalAed"),
                isFull = r.bool("isFull") ?: true, missedPrevious = r.bool("missedPrevious") ?: false,
                levelBefore = r.dbl("levelBefore"), levelAfter = r.dbl("levelAfter"),
                socBefore = r.dbl("socBefore"), socAfter = r.dbl("socAfter"),
                grade = r.enum<FuelGrade>("grade") ?: FuelGrade.OTHER,
                locationType = r.enum<LocationType>("locationType") ?: LocationType.STATION,
                stationName = r.str("stationName"), latitude = r.dbl("latitude"), longitude = r.dbl("longitude"),
                meterSource = r.enum<MeterSource>("meterSource") ?: MeterSource.WALL,
                serviceFeeAed = r.dbl("serviceFeeAed") ?: 0.0,
                priceSource = r.enum<PriceSource>("priceSource") ?: PriceSource.IMPORT,
                note = r.str("note"), importBatchId = r.str("importBatchId"),
                updatedAt = r.instant("updatedAt") ?: error("updatedAt"), deletedAt = r.instant("deletedAt"),
            )
        }
        val expenses = read("expenses") { r ->
            Expense(
                id = r.req("id"), vehicleId = r.req("vehicleId"), timestamp = r.instant("timestamp") ?: error("timestamp"),
                odometerKm = r.dbl("odometerKm"), category = r.enum<ExpenseCategoryKey>("category") ?: ExpenseCategoryKey.OTHER,
                customCategory = r.str("customCategory"), amountAed = r.dbl("amountAed") ?: error("amountAed"),
                vatAed = r.dbl("vatAed"), isIncome = r.bool("isIncome") ?: false,
                source = r.enum<ExpenseSource>("source") ?: ExpenseSource.IMPORT,
                title = r.str("title"), note = r.str("note"), importBatchId = r.str("importBatchId"),
                updatedAt = r.instant("updatedAt") ?: error("updatedAt"), deletedAt = r.instant("deletedAt"),
            )
        }
        val reminders = read("reminders") { r ->
            Reminder(
                id = r.req("id"), vehicleId = r.req("vehicleId"), kind = r.enum<ReminderKind>("kind") ?: ReminderKind.CUSTOM,
                title = r.str("title") ?: "", dueDate = r.date("dueDate"), dueOdometerKm = r.dbl("dueOdometerKm"),
                repeatMonths = r.int("repeatMonths"), repeatKm = r.dbl("repeatKm"), leadDays = r.int("leadDays") ?: 14,
                chainedToId = r.str("chainedToId"), done = r.bool("done") ?: false,
            )
        }
        return SayyaraCsvData(vehicles, stores, events, expenses, reminders, warnings)
    }

    class Row(private val h: Map<String, Int>, private val r: List<String>) {
        fun str(k: String): String? = h[k]?.let { r.getOrNull(it) }?.takeIf { it.isNotEmpty() }
        fun req(k: String): String = str(k) ?: error("missing $k")
        fun dbl(k: String): Double? = str(k)?.let { it.toDoubleOrNull() ?: error("bad number $k='$it'") }
        fun int(k: String): Int? = str(k)?.let { it.toIntOrNull() ?: error("bad integer $k='$it'") }
        fun bool(k: String): Boolean? = str(k)?.let { it.equals("true", true) || it == "1" }
        fun instant(k: String): Instant? = str(k)?.let { Instant.parse(it) }
        fun date(k: String): LocalDate? = str(k)?.let { LocalDate.parse(it) }
        inline fun <reified E : Enum<E>> enum(k: String): E? =
            str(k)?.let { v -> enumValues<E>().firstOrNull { it.name == v } ?: error("bad ${E::class.simpleName} '$v'") }
    }
}
