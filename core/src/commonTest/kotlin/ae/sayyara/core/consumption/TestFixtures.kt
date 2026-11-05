package ae.sayyara.core.consumption

import ae.sayyara.core.model.EnergyEvent
import ae.sayyara.core.model.EnergyStore
import ae.sayyara.core.model.EnergyStoreKind
import ae.sayyara.core.model.EnergyUnit
import ae.sayyara.core.model.FuelGrade
import kotlin.math.abs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

internal val T0: Instant = Instant.parse("2026-01-01T08:00:00Z")

internal fun fill(
    id: String,
    odo: Double,
    amount: Double,
    day: Int = odo.toInt() / 100,
    isFull: Boolean = true,
    missed: Boolean = false,
    totalAed: Double? = null,
    unitPrice: Double? = null,
    levelBefore: Double? = null,
    levelAfter: Double? = null,
    socBefore: Double? = null,
    socAfter: Double? = null,
    grade: FuelGrade = FuelGrade.SPECIAL_95,
    storeId: String = "tank",
    at: Instant = T0 + day.days,
    deleted: Boolean = false,
) = EnergyEvent(
    id = id, vehicleId = "car", storeId = storeId, timestamp = at, odometerKm = odo, amount = amount,
    unitPriceAed = unitPrice, totalAed = totalAed, isFull = isFull, missedPrevious = missed,
    levelBefore = levelBefore, levelAfter = levelAfter, socBefore = socBefore, socAfter = socAfter,
    grade = grade, updatedAt = at, deletedAt = if (deleted) at else null,
)

internal val TANK_60 = EnergyStore("tank", "car", EnergyStoreKind.TANK, EnergyUnit.LITRE, 60.0, null, FuelGrade.SPECIAL_95)
internal val BATTERY_75 = EnergyStore("batt", "car", EnergyStoreKind.BATTERY, EnergyUnit.KWH, 80.0, 75.0, FuelGrade.ELECTRIC)

internal fun assertNear(expected: Double, actual: Double?, eps: Double = 1e-6, msg: String? = null) {
    assertNotNull(actual, msg ?: "expected $expected but was null")
    assertTrue(abs(expected - actual) <= eps, (msg ?: "") + " expected $expected but was $actual")
}
