# Sayyara: build brief for parallel builders

Sayyara is a UAE-first, Android-first fuel and vehicle expense tracker. It is meant to beat Fuelio.

- **Research:** `/home/user/reports/UAE fuel tracker app roadmap.md` (read the sections relevant to your task).
- **Raw notes:** `/home/user/research_notes/UAE fuel tracker app roadmap/*.md`. These include the exact Fuelio CSV format, UAE prices, tolls and parking, and head-unit refuel detection.

## Repo layout (root `/home/user/sayyara`)
- `core/` is Kotlin Multiplatform. Only the `jvm()` target is active now; iOS comes later.
  - Pure Kotlin only: `kotlinx-datetime` 0.7.1 (`kotlin.time.Instant`, `kotlinx.datetime.LocalDate`, `TimeZone`) and `kotlinx-serialization-json`.
  - No `java.*` in `commonMain`.
  - Tests go in `core/src/commonTest/kotlin/...` and use `kotlin.test`.
  - Run them with `cd /home/user/sayyara && gradle -q :core:jvmTest --console=plain`. Add `--tests 'ae.sayyara.core.<pkg>.*'` to run only yours. Gradle may wait on a lock while another builder runs; that is fine.
- `core/src/commonMain/kotlin/ae/sayyara/core/model/Model.kt` is the shared domain model. It is owned by the coordinator; do NOT edit it.
  - If you need a model change, put a small extension or a new type in your own package.
  - Note it in your final report.
- `app/` is the Android phone app and Android Auto. It uses Kotlin, Jetpack Compose and Material 3, minSdk 26, compileSdk 36.
  - **It cannot be compiled in this environment, because the Android SDK is blocked.** Write code carefully against real, current AndroidX APIs, and double-check imports and signatures.
  - Dependencies are already declared in `app/build.gradle.kts`. Do not edit build files. If a dependency is missing, report it.
- `automotive/` is the Android Automotive OS (AAOS) build for head units with Google built-in. It cannot be compiled here either.
- `app/src/main/AndroidManifest.xml` is owned by the coordinator. It already declares:
  - `.SayyaraApp`, `.MainActivity`
  - `.platform.QuickLogTileService`
  - `.platform.widget.DashboardWidgetReceiver` (with `@xml/dashboard_widget_info`)
  - `.platform.BootReceiver`
  - `.car.SayyaraCarAppService` (POI category, minCarApiLevel 3)
  - `@xml/shortcuts`, `@xml/backup_rules`, `@xml/data_extraction_rules`, `@xml/locales_config`
  - `@string/app_name`, `@string/tile_label`
- `app/src/main/kotlin/ae/sayyara/app/data/Repositories.kt` is the CONTRACT between the data layer and the UI. Do not change signatures; report needed changes instead.

## Ownership (only write inside your area)
| Builder | Owns |
|---|---|
| A consumption | `core/.../core/consumption/**` and its tests |
| B import/OCR | `core/.../core/importer/**`, `core/.../core/ocr/**` and their tests |
| C UAE | `core/.../core/uae/**`, `core/.../core/route/**` and their tests |
| D refuel/reminders | `core/.../core/refuel/**`, `core/.../core/obd/**`, `core/.../core/reminders/**` and their tests |
| E app data+platform | `app/.../app/SayyaraApp.kt`, `app/.../app/data/**` (except `Repositories.kt`), `app/.../app/platform/**`, `app/src/main/res/xml/{backup_rules,data_extraction_rules,shortcuts,dashboard_widget_info}.xml`, `app/src/main/res/values*/strings_platform.xml` |
| F app UI | `app/.../app/MainActivity.kt`, `app/.../app/ui/**`, `app/src/main/res/values/strings.xml`, `app/src/main/res/values-ar/strings.xml` (must define `app_name`) |
| G car | `app/.../app/car/**`, `app/src/main/res/values*/strings_car.xml`, all of `automotive/**` |

## Core public API (agreed signatures; owners implement, others call)
```kotlin
// A  ae.sayyara.core.consumption
enum class SegmentMethod { FULL_TO_FULL, GAUGE, SOC }
data class Segment(val startEventId: Id, val endEventId: Id, val km: Double, val amount: Double,
                   val per100km: Double, val method: SegmentMethod, val valid: Boolean)
data class ValidationWarning(val eventId: Id?, val code: String, val message: String)
data class ConsumptionResult(val segments: List<Segment>, val averagePer100km: Double?,
    val lastPer100km: Double?, val totalKm: Double, val totalAmount: Double, val totalFuelCostAed: Double,
    val fuelCostPerKmAed: Double?, val pendingPartialEventIds: Set<Id>, val warnings: List<ValidationWarning>)
object ConsumptionEngine { fun compute(events: List<EnergyEvent>, store: EnergyStore? = null): ConsumptionResult }
data class MonthlyStat(val month: YearMonth, val km: Double, val fuelAmount: Double, val fuelAed: Double,
    val expensesAed: Map<ExpenseCategoryKey, Double>, val totalAed: Double, val per100km: Double?, val costPerKmAed: Double?)
object Stats { fun monthly(events: List<EnergyEvent>, expenses: List<Expense>, zone: TimeZone): List<MonthlyStat> }
enum class EconomyUnit { L_PER_100KM, KM_PER_L, MPG_US, MPG_UK, KWH_PER_100KM, KM_PER_KWH }
object Units { fun fromPer100km(v: Double, unit: EconomyUnit): Double; fun format(v: Double, unit: EconomyUnit): String }
object FillValidation {
  fun check(candidate: EnergyEvent, history: List<EnergyEvent>, store: EnergyStore?): List<ValidationWarning>
  /** UAE heuristic: a round AED amount (50/100/...) or a volume far below the usual fill suggests a partial fill. */
  fun suggestPartial(candidate: EnergyEvent, history: List<EnergyEvent>, store: EnergyStore?): Boolean
}

// B  ae.sayyara.core.importer / ae.sayyara.core.ocr
data class FuelioImport(val vehicle: Vehicle, val stores: List<EnergyStore>, val events: List<EnergyEvent>,
    val expenses: List<Expense>, val reminders: List<Reminder>, val warnings: List<String>)
object FuelioImporter { fun parse(csvText: String, fileName: String?, batchId: Id, now: Instant, newId: () -> Id): FuelioImport }
object CsvExporter { fun export(vehicles: List<Vehicle>, stores: List<EnergyStore>, events: List<EnergyEvent>, expenses: List<Expense>): String }
data class ReceiptFields(val litres: Double?, val unitPriceAed: Double?, val totalAed: Double?, val grade: FuelGrade?,
    val stationBrand: String?, val timestampText: String?, val vatAed: Double?, val confidence: Double)
object ReceiptParser { fun parse(ocrText: String): ReceiptFields }   // ADNOC/ENOC/Emarat, English + Arabic digits
object OdometerParser { fun parse(ocrText: String, lastOdometerKm: Double?): Double? }

// C  ae.sayyara.core.uae / ae.sayyara.core.route
enum class QuoteStatus { FINAL, PROVISIONAL, FORECAST }
data class PriceQuote(val month: YearMonth, val grade: FuelGrade, val aedPerLitre: Double, val status: QuoteStatus)
object UaeFuelPrices {
  val seed: List<FuelPrice>
  fun parseAnnouncement(text: String, month: YearMonth): List<FuelPrice>?   // regex over WAM/news text
  fun toJson(prices: List<FuelPrice>): String; fun fromJson(json: String): List<FuelPrice>
}
class FuelPriceBook(prices: List<FuelPrice>) {
  fun quote(month: YearMonth, grade: FuelGrade): PriceQuote?   // falls back to latest known as PROVISIONAL
  fun latest(grade: FuelGrade): FuelPrice?
}
data class TollCharge(val operator: String, val gateId: String, val gateName: String, val at: Instant, val aed: Double)
class TollEngine(/* gates + schedules, defaults = Salik + Darb 2026 */) {
  fun charges(polyline: List<LatLon>, departure: Instant, avgSpeedKmh: Double = 60.0): List<TollCharge>
}
data class SmsDraft(val number: String, val body: String)
object Parking {
  fun hourlyRate(emirate: Emirate, zone: String, at: Instant): Double?
  fun smsDraft(emirate: Emirate, plate: String, zone: String, hours: Int): SmsDraft
}
interface RouteProvider { suspend fun route(from: LatLon, to: LatLon): RouteResult? }
data class RouteResult(val distanceKm: Double, val durationMin: Double, val polyline: List<LatLon>)
data class RouteCostInput(val route: RouteResult, val departure: Instant, val per100km: Double, val grade: FuelGrade,
    val roundTrip: Boolean = false, val parkingEmirate: Emirate? = null, val parkingZone: String? = null,
    val parkingHours: Double = 0.0, val passengers: Int = 1)
data class RouteCostEstimate(val litres: Double, val fuelAed: Double, val tolls: List<TollCharge>, val tollAed: Double,
    val parkingAed: Double, val totalAed: Double, val perPersonAed: Double, val priceQuote: PriceQuote?,
    val lowAed: Double, val highAed: Double)
class RouteCostEstimator(prices: FuelPriceBook, tolls: TollEngine = TollEngine()) { fun estimate(input: RouteCostInput): RouteCostEstimate }

// D  ae.sayyara.core.refuel / obd / reminders
data class VehicleSample(val at: Instant, val fuelLevelPercent: Double? = null, val fuelLevelLitres: Double? = null,
    val odometerKm: Double? = null, val speedKmh: Double? = null, val fuelDoorOpen: Boolean? = null,
    val ignitionOn: Boolean? = null, val parked: Boolean? = null, val evBatteryPercent: Double? = null)
data class DetectedRefuel(val at: Instant, val litresAdded: Double, val levelBeforePercent: Double, val levelAfterPercent: Double,
    val likelyFull: Boolean, val odometerKm: Double?, val confidence: Double, val trigger: String)
data class RefuelConfig(val tankCapacityL: Double, val minLitres: Double = 5.0, val minPercentJump: Double = 8.0,
    val fullThresholdPercent: Double = 97.0, val maxOdoMoveKm: Double = 2.0)
class RefuelDetector(config: RefuelConfig) { fun onSample(s: VehicleSample): DetectedRefuel? ; fun reset() }
object Obd { fun command(pid: Int): String; fun parse(response: String): ObdReading? }  // 0x2F, 0x0D, 0x5E, 0x10, 0xA6
object ReminderPlanner {
  data class Due(val reminderId: Id, val dueDate: LocalDate?, val daysLeft: Int?, val kmLeft: Double?, val overdue: Boolean)
  fun due(reminder: Reminder, today: LocalDate, odometerKm: Double?, kmPerDay: Double?): Due
  fun next(reminder: Reminder, completedOn: LocalDate, completedOdoKm: Double?): Reminder?   // repeating
  /** UAE chain: inspection (cars > 3y) -> insurance -> Mulkiya renewal, all before registration expiry. */
  fun mulkiyaChain(vehicleId: Id, registrationExpiry: LocalDate, vehicleYear: Int?, today: LocalDate, newId: () -> Id): List<Reminder>
}
```
You may add extra public helpers. Keep the signatures above exact so that other builders' code compiles.

## Deep links (MainActivity handles; widget/tile/shortcut/car use)
- `sayyara://add-fill` opens the quick add-fill sheet for the selected vehicle.
- `sayyara://add-fill?litres=32.5&level_before=18&level_after=98&odo=123456&source=car` is a draft pre-filled by car refuel detection.
- `sayyara://dashboard`, `sayyara://expenses/add?category=PARKING`, `sayyara://import`, `sayyara://route`.

## UX principles
- **Fast fill-up:** at most 3 taps. Prefill the odometer estimate, the grade from the vehicle, and the official UAE price for the month. Full tank is the default.
- **Language:** Arabic and English, with RTL working in every layout.
- **Units and currency:** AED, km and litres by default.
- **Look:** dark mode, Material 3, dynamic colour.
- **Transparency:** explain each consumption figure ("based on 3 fills since 2 Nov").
- **No ads.** Never paywall export.
