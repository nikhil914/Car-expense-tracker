package ae.sayyara.core.model

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/*
 * Shared domain model. Pure data, no platform types.
 * All money is AED, all distance km, all fuel litres, all energy kWh internally.
 * Unit conversion happens only at the edges (import, display).
 * Derived values (consumption) are never stored here; see ae.sayyara.core.consumption.
 */

typealias Id = String

@Serializable
enum class FuelGrade { SUPER_98, SPECIAL_95, E_PLUS_91, DIESEL, ELECTRIC, LPG, CNG, OTHER }

@Serializable
enum class EnergyStoreKind { TANK, BATTERY }

@Serializable
enum class EnergyUnit { LITRE, KWH, KG, M3 }

@Serializable
enum class Emirate { DUBAI, ABU_DHABI, SHARJAH, AJMAN, UMM_AL_QUWAIN, RAS_AL_KHAIMAH, FUJAIRAH }

@Serializable
data class Vehicle(
    val id: Id,
    val name: String,
    val make: String? = null,
    val model: String? = null,
    val year: Int? = null,
    val plate: String? = null,
    val emirate: Emirate? = null,
    val vin: String? = null,
    val defaultGrade: FuelGrade = FuelGrade.SPECIAL_95,
    val purchaseDate: LocalDate? = null,
    val purchasePriceAed: Double? = null,
    val active: Boolean = true,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
)

/** A tank or a battery. Dual-fuel and PHEV vehicles have two. */
@Serializable
data class EnergyStore(
    val id: Id,
    val vehicleId: Id,
    val kind: EnergyStoreKind,
    val unit: EnergyUnit,
    /** Litres or kWh. */
    val capacity: Double?,
    val usableCapacity: Double? = null,
    val defaultGrade: FuelGrade,
)

@Serializable
enum class LocationType { STATION, HOME, WORK, PUBLIC_AC, PUBLIC_DC, FREE, DELIVERY }

@Serializable
enum class PriceSource { OFFICIAL_MONTH, USER, RECEIPT_OCR, IMPORT }

@Serializable
enum class MeterSource { WALL, CAR }

/** One fill-up or charge. Raw input only. */
@Serializable
data class EnergyEvent(
    val id: Id,
    val vehicleId: Id,
    val storeId: Id,
    val timestamp: Instant,
    val odometerKm: Double,
    /** Litres or kWh added (wall kWh for charging when meterSource = WALL). */
    val amount: Double,
    val unitPriceAed: Double? = null,
    val totalAed: Double? = null,
    val isFull: Boolean = true,
    /** True if the user skipped logging one or more fills before this one. */
    val missedPrevious: Boolean = false,
    /** Gauge fraction 0..1 before/after fill (optional, tank-level entry). */
    val levelBefore: Double? = null,
    val levelAfter: Double? = null,
    /** State of charge 0..1 before/after (EV/PHEV). */
    val socBefore: Double? = null,
    val socAfter: Double? = null,
    val grade: FuelGrade,
    val locationType: LocationType = LocationType.STATION,
    val stationName: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val meterSource: MeterSource = MeterSource.WALL,
    val serviceFeeAed: Double = 0.0,
    val priceSource: PriceSource = PriceSource.USER,
    val note: String? = null,
    val importBatchId: Id? = null,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
)

@Serializable
enum class ExpenseCategoryKey { FUEL, SALIK, DARB, PARKING, MULKIYA, INSPECTION, INSURANCE, SERVICE, TYRES, FINE, WASH, OTHER }

@Serializable
enum class ExpenseSource { MANUAL, SMS, EMAIL, OCR, IMPORT, COMPOSER }

@Serializable
data class Expense(
    val id: Id,
    val vehicleId: Id,
    val timestamp: Instant,
    val odometerKm: Double? = null,
    val category: ExpenseCategoryKey,
    /** Custom category name when category = OTHER or from import. */
    val customCategory: String? = null,
    val amountAed: Double,
    val vatAed: Double? = null,
    val isIncome: Boolean = false,
    val source: ExpenseSource = ExpenseSource.MANUAL,
    val title: String? = null,
    val note: String? = null,
    val importBatchId: Id? = null,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
)

@Serializable
enum class ReminderKind { MULKIYA, INSPECTION, INSURANCE, SERVICE, OIL, TYRES, CUSTOM }

@Serializable
data class Reminder(
    val id: Id,
    val vehicleId: Id,
    val kind: ReminderKind,
    val title: String,
    val dueDate: LocalDate? = null,
    val dueOdometerKm: Double? = null,
    val repeatMonths: Int? = null,
    val repeatKm: Double? = null,
    val leadDays: Int = 14,
    /** e.g. INSPECTION -> INSURANCE -> MULKIYA chain. */
    val chainedToId: Id? = null,
    val done: Boolean = false,
)

@Serializable
enum class TripPurpose { PRIVATE, WORK }

@Serializable
data class LatLon(val lat: Double, val lon: Double)

@Serializable
data class Trip(
    val id: Id,
    val vehicleId: Id,
    val start: Instant,
    val end: Instant,
    val km: Double,
    val purpose: TripPurpose = TripPurpose.PRIVATE,
    val polyline: List<LatLon> = emptyList(),
    val fuelCostAed: Double? = null,
    val tollAed: Double? = null,
    val parkingAed: Double? = null,
)

/** Calendar month, e.g. 2026-11. */
@Serializable
data class YearMonth(val year: Int, val month: Int) : Comparable<YearMonth> {
    init { require(month in 1..12) }
    override fun compareTo(other: YearMonth) = compareValuesBy(this, other, { it.year }, { it.month })
    fun next() = if (month == 12) YearMonth(year + 1, 1) else YearMonth(year, month + 1)
    fun previous() = if (month == 1) YearMonth(year - 1, 12) else YearMonth(year, month - 1)
    override fun toString() = "$year-${month.toString().padStart(2, '0')}"
    companion object {
        fun parse(s: String): YearMonth = s.split("-").let { YearMonth(it[0].toInt(), it[1].toInt()) }
        fun of(date: LocalDate) = YearMonth(date.year, date.month.ordinal + 1)
    }
}

@Serializable
enum class PriceStatus { VERIFIED, PROVISIONAL }

/** UAE national monthly fuel price for one grade. Effective from the 1st of [month]. */
@Serializable
data class FuelPrice(
    val month: YearMonth,
    val grade: FuelGrade,
    val aedPerLitre: Double,
    val status: PriceStatus = PriceStatus.VERIFIED,
    val sourceUrl: String? = null,
)
