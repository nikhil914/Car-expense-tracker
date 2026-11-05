package ae.sayyara.core.importer

import ae.sayyara.core.model.EnergyEvent
import ae.sayyara.core.model.EnergyStore
import ae.sayyara.core.model.EnergyStoreKind
import ae.sayyara.core.model.EnergyUnit
import ae.sayyara.core.model.Expense
import ae.sayyara.core.model.ExpenseCategoryKey
import ae.sayyara.core.model.ExpenseSource
import ae.sayyara.core.model.FuelGrade
import ae.sayyara.core.model.Id
import ae.sayyara.core.model.LocationType
import ae.sayyara.core.model.PriceSource
import ae.sayyara.core.model.Reminder
import ae.sayyara.core.model.ReminderKind
import ae.sayyara.core.model.Vehicle
import kotlin.math.abs
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

data class FuelioImport(
    val vehicle: Vehicle,
    val stores: List<EnergyStore>,
    val events: List<EnergyEvent>,
    val expenses: List<Expense>,
    val reminders: List<Reminder>,
    val warnings: List<String>,
)

/**
 * Parser for Fuelio's sectioned backup CSV (`vehicle-<N>-sync.csv`), one vehicle per file.
 *
 * - Sections start at a marker row whose first non-empty cell is `## Name` (comma padding tolerated).
 * - Columns are mapped by normalized header name (see [normalizeHeader]), never by position.
 * - Everything is converted to km / litres / AED. Currency is not in the file: AED is assumed.
 * - Fuelio's consumption column is ignored; consumption is recomputed by the consumption engine.
 * - No de-duplication happens here; use [dedupeKey] against existing events.
 */
object FuelioImporter {

    const val KM_PER_MILE = 1.609344
    const val L_PER_US_GAL = 3.785411784
    const val L_PER_UK_GAL = 4.54609

    /** Fuelio stores naive local times. The UAE has no DST, so Asia/Dubai is a safe default. */
    val DEFAULT_ZONE: TimeZone = TimeZone.of("Asia/Dubai")

    fun parse(csvText: String, fileName: String?, batchId: Id, now: Instant, newId: () -> Id): FuelioImport =
        parse(csvText, fileName, batchId, now, newId, DEFAULT_ZONE)

    /** Same as the agreed API, with an explicit zone for the file's naive timestamps. */
    fun parse(
        csvText: String, fileName: String?, batchId: Id, now: Instant, newId: () -> Id, zone: TimeZone,
    ): FuelioImport = Run(csvText, fileName, batchId, now, newId, zone).execute()

    /** De-duplication key: minute timestamp + odometer (0.1 km) + amount (0.01). */
    fun dedupeKey(e: EnergyEvent): String {
        val minute = e.timestamp.epochSeconds / 60
        return "$minute|${ImportText.fixed(e.odometerKm, 1)}|${ImportText.fixed(e.amount, 2)}"
    }

    // ---- headers & sections ----

    /** Header key + unit: `Odo (mi)` -> ("odo","mi"); `Price(optional)` -> ("price", null). */
    data class HeaderKey(val key: String, val unit: String?)

    fun normalizeHeader(raw: String): HeaderKey {
        var s = Csv.stripBom(raw).trim().lowercase()
        s = s.replace(Regex("\\(\\s*optional\\s*\\)"), "")
        var unit: String? = null
        val m = Regex("\\(([^)]*)\\)").find(s)
        if (m != null) {
            unit = m.groupValues[1].trim().ifEmpty { null }
            s = s.removeRange(m.range)
        }
        val key = s.filter { it.isLetterOrDigit() || it == '/' }
        return HeaderKey(key, unit)
    }

    class Section(val name: String, header: List<String>, val rows: List<List<String>>) {
        val keys: List<HeaderKey> = header.map { normalizeHeader(it) }
        private val index: Map<String, Int> = buildMap {
            keys.forEachIndexed { i, k -> if (k.key.isNotEmpty() && k.key !in this) put(k.key, i) }
        }

        fun has(key: String) = key in index
        fun unitOf(key: String): String? = index[key]?.let { keys[it].unit }
        fun get(row: List<String>, vararg key: String): String? {
            for (k in key) {
                val i = index[k] ?: continue
                return row.getOrNull(i)?.trim()?.takeIf { it.isNotEmpty() }
            }
            return null
        }
    }

    /** Split rows into sections keyed by lowercase name without spaces (e.g. "costcategories"). */
    fun splitSections(csvText: String): Map<String, Section> {
        val text = Csv.stripBom(csvText)
        val rows = Csv.parse(text, Csv.sniffDelimiter(text))
        val out = LinkedHashMap<String, Section>()
        var name: String? = null
        var header: List<String>? = null
        var data = ArrayList<List<String>>()
        fun flush() {
            val n = name ?: return
            val h = header ?: return
            if (n !in out) out[n] = Section(n, h, data)
        }
        for (row in rows) {
            val nonEmpty = row.filter { it.isNotBlank() }
            if (nonEmpty.isEmpty()) continue
            val first = nonEmpty.first().trim()
            if (first.startsWith("##") && nonEmpty.size == 1) {
                flush()
                name = first.removePrefix("##").trim().lowercase().filter { it.isLetterOrDigit() }
                header = null
                data = ArrayList()
                continue
            }
            if (name == null) continue
            if (header == null) header = row else data.add(row)
        }
        flush()
        return out
    }

    // ---- code tables ----

    /** Fuelio FuelType code -> UAE grade. 0 / -1 / unknown -> null (caller falls back). */
    fun gradeFor(code: Int?): FuelGrade? {
        if (code == null || code <= 0) return null
        return when (code) {
            112, 113, 114, 115 -> FuelGrade.SUPER_98 // 98, Ultimate, 100, racing
            106 -> FuelGrade.E_PLUS_91 // "91"
            in 100..199 -> FuelGrade.SPECIAL_95 // gasoline default (95, 95+, E10, unset child)
            in 200..299 -> FuelGrade.DIESEL
            302 -> FuelGrade.SPECIAL_95 // E10
            in 300..399 -> FuelGrade.OTHER // ethanol E85 etc.
            in 400..499 -> FuelGrade.LPG
            in 500..599 -> FuelGrade.CNG
            in 600..699 -> FuelGrade.ELECTRIC
            else -> FuelGrade.OTHER // 700 flex and future codes
        }
    }

    fun isElectric(code: Int?) = code != null && code in 600..699

    /** Litres per Fuelio FuelUnit code (0 litres, 1 US gal, 2 UK gal). */
    fun litresPerUnit(code: Int?): Double = when (code) {
        1 -> L_PER_US_GAL
        2 -> L_PER_UK_GAL
        else -> 1.0
    }

    /** Heuristic Fuelio cost category name -> Sayyara key (English + Arabic keywords). */
    fun categoryFor(name: String?): ExpenseCategoryKey {
        val n = name?.lowercase()?.trim() ?: return ExpenseCategoryKey.OTHER
        fun any(vararg k: String) = k.any { n.contains(it) }
        return when {
            any("salik", "toll", "سالك", "رسوم الطريق") -> ExpenseCategoryKey.SALIK
            any("darb", "درب") -> ExpenseCategoryKey.DARB
            any("park", "مواقف", "موقف") -> ExpenseCategoryKey.PARKING
            any("insur", "تأمين", "تامين") -> ExpenseCategoryKey.INSURANCE
            any("inspect", "mot", "tasjeel", "shamil", "فحص") -> ExpenseCategoryKey.INSPECTION
            any("regist", "mulkiya", "licen", "road tax", "تسجيل", "ملكية", "ترخيص") -> ExpenseCategoryKey.MULKIYA
            n == "tax" -> ExpenseCategoryKey.MULKIYA
            any("tyre", "tire", "wheel", "إطار", "اطار", "كفر") -> ExpenseCategoryKey.TYRES
            any("fine", "ticket", "penalt", "مخالف", "غرام") -> ExpenseCategoryKey.FINE
            any("wash", "clean", "detail", "غسيل", "غسل") -> ExpenseCategoryKey.WASH
            any("service", "maint", "repair", "oil", "tuning", "garage", "صيانة", "خدمة", "زيت") -> ExpenseCategoryKey.SERVICE
            any("fuel", "petrol", "وقود", "بنزين") -> ExpenseCategoryKey.FUEL
            else -> ExpenseCategoryKey.OTHER
        }
    }

    fun reminderKindFor(cat: ExpenseCategoryKey, title: String?): ReminderKind {
        val t = title?.lowercase() ?: ""
        return when {
            t.contains("oil") || t.contains("زيت") -> ReminderKind.OIL
            cat == ExpenseCategoryKey.MULKIYA -> ReminderKind.MULKIYA
            cat == ExpenseCategoryKey.INSPECTION -> ReminderKind.INSPECTION
            cat == ExpenseCategoryKey.INSURANCE -> ReminderKind.INSURANCE
            cat == ExpenseCategoryKey.SERVICE -> ReminderKind.SERVICE
            cat == ExpenseCategoryKey.TYRES -> ReminderKind.TYRES
            else -> ReminderKind.CUSTOM
        }
    }

    // ---- one import run ----

    private class Run(
        val text: String, val fileName: String?, val batchId: Id, val now: Instant,
        val newId: () -> Id, val zone: TimeZone,
    ) {
        val warnings = ArrayList<String>()
        var clampedDates = 0

        fun execute(): FuelioImport {
            val sections = splitSections(text)
            if (sections.isEmpty()) warnings.add("No Fuelio sections (\"## Vehicle\", \"## Log\") found in file.")
            val veh = sections["vehicle"]
            val vrow = veh?.rows?.firstOrNull()
            if (veh == null || vrow == null) warnings.add("No ## Vehicle section; using defaults (km, litres).")
            if ((veh?.rows?.size ?: 0) > 1) warnings.add("Vehicle section has ${veh!!.rows.size} rows; only the first is imported.")
            fun v(vararg k: String) = if (veh != null && vrow != null) veh.get(vrow, *k) else null

            val dateReader = ImportText.DateReader(v("importcsvdateformat"))
            val distCode = ImportText.number(v("distunit"))?.toInt() ?: 0
            val fuelCode1 = ImportText.number(v("fuelunit"))?.toInt() ?: 0
            val fuelCode2 = ImportText.number(v("fuelunittank2"))?.toInt() ?: fuelCode1
            val tankCount = ImportText.number(v("tankcount"))?.toInt() ?: 1
            val tank1Type = ImportText.number(v("tank1type"))?.toInt()
            val tank2Type = ImportText.number(v("tank2type"))?.toInt()

            val log = sections["log"]
            // Odometer unit: header unit wins over DistUnit when explicit.
            val odoHeaderUnit = log?.unitOf("odo")?.lowercase()
            var kmPerDist = if (distCode == 1) KM_PER_MILE else 1.0
            if (odoHeaderUnit != null) {
                val headerMiles = odoHeaderUnit.startsWith("mi")
                val headerKm = odoHeaderUnit.startsWith("km")
                if (headerMiles && distCode != 1 || headerKm && distCode == 1) {
                    warnings.add("Odometer header says '$odoHeaderUnit' but DistUnit=$distCode; using the header unit.")
                }
                if (headerMiles) kmPerDist = KM_PER_MILE else if (headerKm) kmPerDist = 1.0
            }
            if (kmPerDist != 1.0) warnings.add("Distances converted from miles to km.")
            // Fuel unit: a gallon header overrides the code; a "litres" header is trusted only when the code agrees.
            val fuelHeaderUnit = log?.unitOf("fuel")?.lowercase()
            var lPerUnit1 = litresPerUnit(fuelCode1)
            if (fuelHeaderUnit != null && fuelHeaderUnit.contains("gal")) {
                val uk = fuelHeaderUnit.contains("uk") || fuelHeaderUnit.contains("imp")
                lPerUnit1 = if (uk) L_PER_UK_GAL else if (fuelCode1 == 2) L_PER_UK_GAL else L_PER_US_GAL
            } else if (fuelHeaderUnit != null && fuelHeaderUnit.startsWith("lit") && fuelCode1 != 0) {
                warnings.add("Fuel header says '$fuelHeaderUnit' but FuelUnit=$fuelCode1; using FuelUnit (gallons).")
            }
            if (lPerUnit1 != 1.0) warnings.add("Volumes converted from ${if (lPerUnit1 == L_PER_UK_GAL) "UK" else "US"} gallons to litres.")
            val lPerUnit2 = litresPerUnit(fuelCode2)

            val vehicleId = newId()
            val name = v("name") ?: listOfNotNull(v("make"), v("model")).joinToString(" ").ifBlank { null }
                ?: fileName?.substringAfterLast('/')?.substringBefore('.')?.ifBlank { null } ?: "Imported vehicle"

            // Stores
            val logRows = log?.rows.orEmpty()
            val usesTank2 = logRows.any { (ImportText.number(log!!.get(it, "tanknumber"))?.toInt() ?: 1) == 2 }
            fun makeStore(type: Int?, capRaw: String?, lPerUnit: Double, fallbackGrade: FuelGrade): EnergyStore {
                val electric = isElectric(type)
                val grade = gradeFor(type) ?: fallbackGrade
                val cap = ImportText.number(capRaw)?.takeIf { it > 0 }
                val unit = when {
                    electric -> EnergyUnit.KWH
                    grade == FuelGrade.CNG -> EnergyUnit.KG
                    else -> EnergyUnit.LITRE
                }
                return EnergyStore(
                    id = newId(), vehicleId = vehicleId,
                    kind = if (electric) EnergyStoreKind.BATTERY else EnergyStoreKind.TANK,
                    unit = unit,
                    capacity = cap?.let { if (unit == EnergyUnit.LITRE) it * lPerUnit else it },
                    defaultGrade = grade,
                )
            }
            val store1 = makeStore(tank1Type, v("tank1capacity"), lPerUnit1, FuelGrade.SPECIAL_95)
            val store2 = if (tankCount >= 2 || usesTank2) {
                if (tankCount < 2) warnings.add("Log has TankNumber=2 rows but TankCount=$tankCount; created a second store.")
                makeStore(tank2Type, v("tank2capacity"), lPerUnit2, FuelGrade.LPG)
            } else null
            val stores = listOfNotNull(store1, store2)

            val vehicle = Vehicle(
                id = vehicleId,
                name = name,
                make = v("make"),
                model = v("model"),
                year = ImportText.number(v("year"))?.toInt()?.takeIf { it in 1900..2100 },
                plate = v("plate"),
                vin = v("vin"),
                defaultGrade = store1.defaultGrade,
                active = v("active")?.let { ImportText.flag(it) } ?: true,
                updatedAt = now,
            )

            // Stations joined on StationID
            val stations = HashMap<String, String>()
            sections["favstations"]?.let { fs ->
                for (r in fs.rows) {
                    val sid = fs.get(r, "stationid") ?: continue
                    val label = listOfNotNull(fs.get(r, "namebrand"), fs.get(r, "description")).joinToString(" - ")
                    if (label.isNotBlank()) stations[sid] = label
                }
            }

            val events = if (log != null) mapLog(log, dateReader, vehicleId, store1, store2, kmPerDist, lPerUnit1, lPerUnit2, stations) else {
                warnings.add("No ## Log section; no fill-ups imported."); emptyList()
            }

            val (expenses, reminders) = mapCosts(sections, dateReader, vehicleId, kmPerDist)

            if (clampedDates > 0) warnings.add("$clampedDates invalid date(s) (e.g. 31 September) clamped to month end.")
            warnings.add("Currency is not stored in Fuelio files; amounts assumed to be AED.")
            return FuelioImport(vehicle, stores, events, expenses, reminders, warnings.toList())
        }

        fun readDate(r: ImportText.DateReader, raw: String?): Instant? {
            val p = r.read(raw) ?: return null
            if (p.clamped) clampedDates++
            return p.dateTime.toInstant(zone)
        }

        fun mapLog(
            log: Section, dates: ImportText.DateReader, vehicleId: Id, store1: EnergyStore, store2: EnergyStore?,
            kmPerDist: Double, lPerUnit1: Double, lPerUnit2: Double, stations: Map<String, String>,
        ): List<EnergyEvent> {
            if (!log.has("data") && !log.has("date")) warnings.add("Log header has no date column ('Data').")
            var skipped = 0
            var unknownGrade = 0
            var derived = 0
            val out = ArrayList<EnergyEvent>()
            for (r in log.rows) {
                val ts = readDate(dates, log.get(r, "data", "date"))
                val odoRaw = ImportText.number(log.get(r, "odo", "odometer"))
                val vol = ImportText.number(log.get(r, "fuel", "volume"))
                if (ts == null || odoRaw == null || vol == null || vol <= 0) { skipped++; continue }
                val tank = ImportText.number(log.get(r, "tanknumber"))?.toInt() ?: 1
                val store = if (tank == 2 && store2 != null) store2 else store1
                val lPerUnit = if (store === store2) lPerUnit2 else lPerUnit1
                val conv = if (store.unit == EnergyUnit.LITRE) lPerUnit else 1.0
                val amount = vol * conv

                var total = ImportText.number(log.get(r, "price"))?.takeIf { it > 0 }
                var unit = ImportText.number(log.get(r, "volumeprice"))?.takeIf { it > 0 }?.let { it / conv }
                if (total == null && unit != null) { total = round2(unit * amount); derived++ }
                if (unit == null && total != null) { unit = round3(total / amount); derived++ }

                val code = ImportText.number(log.get(r, "fueltype"))?.toInt()
                val grade = gradeFor(code) ?: store.defaultGrade.also { if (code != null && code < 0) unknownGrade++ }
                val station = log.get(r, "city")
                    ?: log.get(r, "stationid")?.let { stations[it] }
                val lat = ImportText.number(log.get(r, "latitude"))
                val lon = ImportText.number(log.get(r, "longitude"))
                val hasGeo = lat != null && lon != null && !(lat == 0.0 && lon == 0.0)
                val locType = when {
                    store.kind != EnergyStoreKind.BATTERY -> LocationType.STATION
                    code == 602 -> LocationType.PUBLIC_DC
                    else -> LocationType.PUBLIC_AC
                }
                out.add(
                    EnergyEvent(
                        id = newId(), vehicleId = vehicleId, storeId = store.id,
                        timestamp = ts, odometerKm = odoRaw * kmPerDist, amount = amount,
                        unitPriceAed = unit, totalAed = total,
                        isFull = log.get(r, "full")?.let { ImportText.flag(it) } ?: true,
                        missedPrevious = ImportText.flag(log.get(r, "missed")),
                        grade = grade, locationType = locType, stationName = station,
                        latitude = if (hasGeo) lat else null, longitude = if (hasGeo) lon else null,
                        priceSource = PriceSource.IMPORT,
                        note = log.get(r, "notes", "note"),
                        importBatchId = batchId, updatedAt = now,
                    ),
                )
            }
            if (skipped > 0) warnings.add("$skipped log row(s) skipped: missing/invalid date, odometer or volume.")
            if (unknownGrade > 0) warnings.add("$unknownGrade fill(s) had an unknown fuel type; set to the tank's default grade.")
            if (derived > 0) warnings.add("$derived fill(s) had total or unit price derived from the other.")
            val sorted = out.sortedWith(compareBy<EnergyEvent>({ it.timestamp }, { it.odometerKm }))
            var backwards = 0
            val lastByStore = HashMap<Id, Double>()
            for (e in sorted) {
                val prev = lastByStore[e.storeId]
                if (prev != null && e.odometerKm < prev) backwards++
                lastByStore[e.storeId] = e.odometerKm
            }
            if (backwards > 0) warnings.add("$backwards fill(s) have an odometer lower than the previous fill; please review.")
            return sorted
        }

        fun mapCosts(
            sections: Map<String, Section>, dates: ImportText.DateReader, vehicleId: Id, kmPerDist: Double,
        ): Pair<List<Expense>, List<Reminder>> {
            val costs = sections["costs"] ?: return emptyList<Expense>() to emptyList()
            val catNames = HashMap<String, String>()
            sections["costcategories"]?.let { cc ->
                for (r in cc.rows) {
                    val id = cc.get(r, "costtypeid", "id") ?: continue
                    val n = cc.get(r, "name") ?: continue
                    catNames[normId(id)] = n
                }
            }
            val expenses = ArrayList<Expense>()
            val reminders = ArrayList<Reminder>()
            var templates = 0
            var skipped = 0
            for (r in costs.rows) {
                if (ImportText.flag(costs.get(r, "istemplate"))) { templates++; continue }
                val title = costs.get(r, "costtitle", "title")
                val catName = costs.get(r, "costtypeid")?.let { catNames[normId(it)] }
                var cat = categoryFor(catName)
                if (cat == ExpenseCategoryKey.OTHER) cat = categoryFor(title)
                val odo = ImportText.number(costs.get(r, "odo"))?.takeIf { it > 0 }?.let { it * kmPerDist }
                val cost = ImportText.number(costs.get(r, "cost")) ?: 0.0
                val ts = readDate(dates, costs.get(r, "date"))

                val remindDate = dates.read(costs.get(r, "reminddate"))?.also { if (it.clamped) clampedDates++ }?.dateTime?.date
                val remindOdo = ImportText.number(costs.get(r, "remindodo"))?.takeIf { it > 0 }?.let { it * kmPerDist }
                val repeatMonths = ImportText.number(costs.get(r, "repeatmonths"))?.toInt()?.takeIf { it > 0 }
                val repeatKm = ImportText.number(costs.get(r, "repeatodo"))?.takeIf { it > 0 }?.let { it * kmPerDist }

                if (cost != 0.0) {
                    if (ts == null) { skipped++ } else {
                        expenses.add(
                            Expense(
                                id = newId(), vehicleId = vehicleId, timestamp = ts, odometerKm = odo,
                                category = cat, customCategory = catName ?: title,
                                amountAed = abs(cost), isIncome = ImportText.flag(costs.get(r, "isincome")),
                                source = ExpenseSource.IMPORT, title = title, note = costs.get(r, "notes", "note"),
                                importBatchId = batchId, updatedAt = now,
                            ),
                        )
                    }
                }
                if (remindDate != null || remindOdo != null) {
                    reminders.add(
                        Reminder(
                            id = newId(), vehicleId = vehicleId, kind = reminderKindFor(cat, title),
                            title = title ?: catName ?: "Reminder",
                            dueDate = remindDate, dueOdometerKm = remindOdo,
                            repeatMonths = repeatMonths, repeatKm = repeatKm,
                        ),
                    )
                }
            }
            if (templates > 0) warnings.add("$templates cost template(s) skipped.")
            if (skipped > 0) warnings.add("$skipped cost row(s) skipped: invalid date.")
            return expenses to reminders
        }

        fun normId(s: String) = ImportText.number(s)?.toLong()?.toString() ?: s
        fun round2(x: Double) = kotlin.math.round(x * 100) / 100
        fun round3(x: Double) = kotlin.math.round(x * 1000) / 1000
    }
}
