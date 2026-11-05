package ae.sayyara.app.data

import ae.sayyara.core.importer.FuelioImport
import ae.sayyara.core.model.EnergyEvent
import ae.sayyara.core.model.EnergyStore
import ae.sayyara.core.model.Expense
import ae.sayyara.core.model.Id
import ae.sayyara.core.model.Reminder
import ae.sayyara.core.model.Vehicle
import kotlin.math.abs
import kotlin.time.Duration.Companion.hours

/** Existing (active) data for one vehicle, used to dedupe an import against. */
data class ExistingVehicleData(
    val vehicle: Vehicle,
    val stores: List<EnergyStore>,
    val events: List<EnergyEvent>,
    val expenses: List<Expense>,
    val reminders: List<Reminder>,
)

/** What a commit will write, after vehicle matching and duplicate removal. */
data class ImportPlan(
    val newVehicles: List<Vehicle>,
    val newStores: List<EnergyStore>,
    val events: List<EnergyEvent>,
    val expenses: List<Expense>,
    val reminders: List<Reminder>,
    /** Imported vehicle name -> existing vehicle it was merged into. */
    val mergedInto: List<Pair<String, Vehicle>>,
    val skippedEvents: Int,
    val skippedExpenses: Int,
    val skippedReminders: Int,
    val vehiclesInFile: Int,
    val warnings: List<String>,
)

/**
 * Pure dedupe logic (no Android types).
 *
 * Vehicle match: same VIN, else same plate, else same name (case/space-insensitive). Re-importing the
 * same Fuelio backup therefore merges into the existing vehicle instead of duplicating it.
 * Fill duplicate: same vehicle, odometer within 0.5 km, time within 12 h, amount within 0.05.
 * Expense duplicate: same vehicle and category, amount within 0.01 AED, time within 24 h.
 * Reminder duplicate: same vehicle, kind, title and due date/odometer.
 */
object ImportDedupe {
    private const val ODO_TOLERANCE_KM = 0.5
    private const val AMOUNT_TOLERANCE = 0.05
    private const val AED_TOLERANCE = 0.01
    private val FILL_WINDOW = 12.hours
    private val EXPENSE_WINDOW = 24.hours

    fun plan(imports: List<FuelioImport>, existing: List<ExistingVehicleData>): ImportPlan {
        val newVehicles = mutableListOf<Vehicle>()
        val newStores = mutableListOf<EnergyStore>()
        val events = mutableListOf<EnergyEvent>()
        val expenses = mutableListOf<Expense>()
        val reminders = mutableListOf<Reminder>()
        val merged = mutableListOf<Pair<String, Vehicle>>()
        val warnings = mutableListOf<String>()
        var skippedEvents = 0
        var skippedExpenses = 0
        var skippedReminders = 0

        for (imp in imports) {
            warnings += imp.warnings
            val match = matchVehicle(imp.vehicle, existing)
            val vehicleId: Id
            val storeMap = mutableMapOf<Id, Id>()
            if (match == null) {
                vehicleId = imp.vehicle.id
                newVehicles += imp.vehicle
                imp.stores.forEach { s ->
                    newStores += s.copy(vehicleId = vehicleId)
                    storeMap[s.id] = s.id
                }
            } else {
                vehicleId = match.vehicle.id
                merged += imp.vehicle.name to match.vehicle
                imp.stores.forEach { s ->
                    val target = match.stores.firstOrNull { it.kind == s.kind && it.defaultGrade == s.defaultGrade }
                        ?: match.stores.firstOrNull { it.kind == s.kind }
                    if (target != null) {
                        storeMap[s.id] = target.id
                    } else {
                        newStores += s.copy(vehicleId = vehicleId)
                        storeMap[s.id] = s.id
                    }
                }
            }

            val priorEvents = match?.events.orEmpty()
            for (e in imp.events) {
                val mapped = e.copy(vehicleId = vehicleId, storeId = storeMap[e.storeId] ?: e.storeId)
                if (priorEvents.any { isDuplicateFill(it, mapped) }) skippedEvents++ else events += mapped
            }
            val priorExpenses = match?.expenses.orEmpty()
            for (x in imp.expenses) {
                val mapped = x.copy(vehicleId = vehicleId)
                if (priorExpenses.any { isDuplicateExpense(it, mapped) }) skippedExpenses++ else expenses += mapped
            }
            val priorReminders = match?.reminders.orEmpty()
            for (r in imp.reminders) {
                val mapped = r.copy(vehicleId = vehicleId)
                if (priorReminders.any { isDuplicateReminder(it, mapped) }) skippedReminders++ else reminders += mapped
            }
        }

        return ImportPlan(
            newVehicles = newVehicles, newStores = newStores, events = events, expenses = expenses,
            reminders = reminders, mergedInto = merged, skippedEvents = skippedEvents,
            skippedExpenses = skippedExpenses, skippedReminders = skippedReminders,
            vehiclesInFile = imports.size, warnings = warnings,
        )
    }

    fun matchVehicle(candidate: Vehicle, existing: List<ExistingVehicleData>): ExistingVehicleData? {
        candidate.vin.normalized()?.let { vin ->
            existing.firstOrNull { it.vehicle.vin.normalized() == vin }?.let { return it }
        }
        candidate.plate.normalized()?.let { plate ->
            existing.firstOrNull { it.vehicle.plate.normalized() == plate }?.let { return it }
        }
        val name = candidate.name.normalized() ?: return null
        return existing.firstOrNull { it.vehicle.name.normalized() == name }
    }

    fun isDuplicateFill(a: EnergyEvent, b: EnergyEvent): Boolean =
        a.vehicleId == b.vehicleId &&
            abs(a.odometerKm - b.odometerKm) <= ODO_TOLERANCE_KM &&
            (a.timestamp - b.timestamp).absoluteValue <= FILL_WINDOW &&
            abs(a.amount - b.amount) <= AMOUNT_TOLERANCE

    fun isDuplicateExpense(a: Expense, b: Expense): Boolean =
        a.vehicleId == b.vehicleId && a.category == b.category && a.isIncome == b.isIncome &&
            abs(a.amountAed - b.amountAed) <= AED_TOLERANCE &&
            (a.timestamp - b.timestamp).absoluteValue <= EXPENSE_WINDOW

    fun isDuplicateReminder(a: Reminder, b: Reminder): Boolean =
        a.vehicleId == b.vehicleId && a.kind == b.kind &&
            a.title.trim().equals(b.title.trim(), ignoreCase = true) &&
            a.dueDate == b.dueDate && a.dueOdometerKm == b.dueOdometerKm

    private fun String?.normalized(): String? =
        this?.filter { it.isLetterOrDigit() }?.uppercase()?.takeIf { it.isNotEmpty() }
}
