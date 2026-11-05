package ae.sayyara.app.data.db

import ae.sayyara.core.model.EnergyEvent
import ae.sayyara.core.model.EnergyStore
import ae.sayyara.core.model.Expense
import ae.sayyara.core.model.FuelPrice
import ae.sayyara.core.model.Reminder
import ae.sayyara.core.model.Trip
import ae.sayyara.core.model.Vehicle
import ae.sayyara.core.model.YearMonth
import kotlin.time.Instant

/*
 * Entity <-> core model mappers.
 * Core models without sync columns (EnergyStore, Reminder, Trip) get them supplied by the caller.
 * importBatchId on vehicles/stores/reminders is not part of the core model, so callers pass it
 * (repositories preserve the stored value on edit so that "undo import" still works).
 */

internal fun Long.toInstant(): Instant = Instant.fromEpochMilliseconds(this)
internal fun Instant.toMillis(): Long = toEpochMilliseconds()

// ---- Vehicle
fun VehicleEntity.toModel() = Vehicle(
    id = id, name = name, make = make, model = model, year = year, plate = plate, emirate = emirate,
    vin = vin, defaultGrade = defaultGrade, purchaseDate = purchaseDate, purchasePriceAed = purchasePriceAed,
    active = active, updatedAt = updatedAt.toInstant(), deletedAt = deletedAt?.toInstant(),
)

fun Vehicle.toEntity(importBatchId: String? = null) = VehicleEntity(
    id = id, name = name, make = make, model = model, year = year, plate = plate, emirate = emirate,
    vin = vin, defaultGrade = defaultGrade, purchaseDate = purchaseDate, purchasePriceAed = purchasePriceAed,
    active = active, importBatchId = importBatchId, updatedAt = updatedAt.toMillis(), deletedAt = deletedAt?.toMillis(),
)

// ---- EnergyStore
fun EnergyStoreEntity.toModel() = EnergyStore(
    id = id, vehicleId = vehicleId, kind = kind, unit = unit, capacity = capacity,
    usableCapacity = usableCapacity, defaultGrade = defaultGrade,
)

fun EnergyStore.toEntity(updatedAt: Long, importBatchId: String? = null, deletedAt: Long? = null) = EnergyStoreEntity(
    id = id, vehicleId = vehicleId, kind = kind, unit = unit, capacity = capacity,
    usableCapacity = usableCapacity, defaultGrade = defaultGrade, importBatchId = importBatchId,
    updatedAt = updatedAt, deletedAt = deletedAt,
)

// ---- EnergyEvent
fun EnergyEventEntity.toModel() = EnergyEvent(
    id = id, vehicleId = vehicleId, storeId = storeId, timestamp = timestamp.toInstant(), odometerKm = odometerKm,
    amount = amount, unitPriceAed = unitPriceAed, totalAed = totalAed, isFull = isFull, missedPrevious = missedPrevious,
    levelBefore = levelBefore, levelAfter = levelAfter, socBefore = socBefore, socAfter = socAfter, grade = grade,
    locationType = locationType, stationName = stationName, latitude = latitude, longitude = longitude,
    meterSource = meterSource, serviceFeeAed = serviceFeeAed, priceSource = priceSource, note = note,
    importBatchId = importBatchId, updatedAt = updatedAt.toInstant(), deletedAt = deletedAt?.toInstant(),
)

fun EnergyEvent.toEntity() = EnergyEventEntity(
    id = id, vehicleId = vehicleId, storeId = storeId, timestamp = timestamp.toMillis(), odometerKm = odometerKm,
    amount = amount, unitPriceAed = unitPriceAed, totalAed = totalAed, isFull = isFull, missedPrevious = missedPrevious,
    levelBefore = levelBefore, levelAfter = levelAfter, socBefore = socBefore, socAfter = socAfter, grade = grade,
    locationType = locationType, stationName = stationName, latitude = latitude, longitude = longitude,
    meterSource = meterSource, serviceFeeAed = serviceFeeAed, priceSource = priceSource, note = note,
    importBatchId = importBatchId, updatedAt = updatedAt.toMillis(), deletedAt = deletedAt?.toMillis(),
)

// ---- Expense
fun ExpenseEntity.toModel() = Expense(
    id = id, vehicleId = vehicleId, timestamp = timestamp.toInstant(), odometerKm = odometerKm, category = category,
    customCategory = customCategory, amountAed = amountAed, vatAed = vatAed, isIncome = isIncome, source = source,
    title = title, note = note, importBatchId = importBatchId, updatedAt = updatedAt.toInstant(),
    deletedAt = deletedAt?.toInstant(),
)

fun Expense.toEntity() = ExpenseEntity(
    id = id, vehicleId = vehicleId, timestamp = timestamp.toMillis(), odometerKm = odometerKm, category = category,
    customCategory = customCategory, amountAed = amountAed, vatAed = vatAed, isIncome = isIncome, source = source,
    title = title, note = note, importBatchId = importBatchId, updatedAt = updatedAt.toMillis(),
    deletedAt = deletedAt?.toMillis(),
)

// ---- Reminder
fun ReminderEntity.toModel() = Reminder(
    id = id, vehicleId = vehicleId, kind = kind, title = title, dueDate = dueDate, dueOdometerKm = dueOdometerKm,
    repeatMonths = repeatMonths, repeatKm = repeatKm, leadDays = leadDays, chainedToId = chainedToId, done = done,
)

fun Reminder.toEntity(updatedAt: Long, importBatchId: String? = null, deletedAt: Long? = null) = ReminderEntity(
    id = id, vehicleId = vehicleId, kind = kind, title = title, dueDate = dueDate, dueOdometerKm = dueOdometerKm,
    repeatMonths = repeatMonths, repeatKm = repeatKm, leadDays = leadDays, chainedToId = chainedToId, done = done,
    importBatchId = importBatchId, updatedAt = updatedAt, deletedAt = deletedAt,
)

// ---- Trip
fun TripEntity.toModel() = Trip(
    id = id, vehicleId = vehicleId, start = startedAt.toInstant(), end = endedAt.toInstant(), km = km,
    purpose = purpose, polyline = polyline, fuelCostAed = fuelCostAed, tollAed = tollAed, parkingAed = parkingAed,
)

fun Trip.toEntity(updatedAt: Long, deletedAt: Long? = null) = TripEntity(
    id = id, vehicleId = vehicleId, startedAt = start.toMillis(), endedAt = end.toMillis(), km = km,
    purpose = purpose, polyline = polyline, fuelCostAed = fuelCostAed, tollAed = tollAed, parkingAed = parkingAed,
    updatedAt = updatedAt, deletedAt = deletedAt,
)

// ---- FuelPrice
fun FuelPriceEntity.toModel() = FuelPrice(
    month = YearMonth.parse(month), grade = grade, aedPerLitre = aedPerLitre, status = status, sourceUrl = sourceUrl,
)

fun FuelPrice.toEntity(updatedAt: Long) = FuelPriceEntity(
    month = month.toString(), grade = grade, aedPerLitre = aedPerLitre, status = status, sourceUrl = sourceUrl,
    updatedAt = updatedAt, deletedAt = null,
)
