package ae.sayyara.app.ui.screens.fill

import ae.sayyara.app.data.AppContainer
import ae.sayyara.app.ui.common.VehicleData
import ae.sayyara.app.ui.common.newId
import ae.sayyara.app.ui.common.selectedVehicleData
import ae.sayyara.app.ui.format.parseDecimal
import ae.sayyara.app.ui.format.toInputText
import ae.sayyara.app.ui.navigation.FillDraft
import ae.sayyara.core.consumption.FillValidation
import ae.sayyara.core.consumption.ValidationWarning
import ae.sayyara.core.model.EnergyEvent
import ae.sayyara.core.model.EnergyStore
import ae.sayyara.core.model.EnergyStoreKind
import ae.sayyara.core.model.EnergyUnit
import ae.sayyara.core.model.FuelGrade
import ae.sayyara.core.model.Id
import ae.sayyara.core.model.PriceSource
import ae.sayyara.core.model.YearMonth
import ae.sayyara.core.ocr.ReceiptFields
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.math.roundToLong
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

enum class AmountMode { LITRES, AED }

/** Station brand chips; stored as EnergyEvent.stationName. */
val StationBrands = listOf("ADNOC", "ENOC", "Emarat")

data class FillUiState(
    val loading: Boolean = true,
    val noVehicle: Boolean = false,
    val vehicleName: String = "",
    val isEdit: Boolean = false,
    val isElectric: Boolean = false,
    val fromCar: Boolean = false,
    val timestamp: Instant = Clock.System.now(),
    val odometerText: String = "",
    val odometerEstimated: Boolean = false,
    val lastOdometerKm: Double? = null,
    val amountMode: AmountMode = AmountMode.AED,
    val litresText: String = "",
    val totalText: String = "",
    val unitPriceText: String = "",
    val priceSource: PriceSource = PriceSource.OFFICIAL_MONTH,
    val officialPrice: Double? = null,
    val priceMonth: YearMonth? = null,
    val grades: List<FuelGrade> = emptyList(),
    val grade: FuelGrade = FuelGrade.SPECIAL_95,
    val isFull: Boolean = true,
    val missedPrevious: Boolean = false,
    val showLevels: Boolean = false,
    val levelBefore: Float = 0.25f,
    val levelAfter: Float = 1f,
    val stationBrand: String? = null,
    val warnings: List<ValidationWarning> = emptyList(),
    val suggestPartial: Boolean = false,
    val scanFailed: Boolean = false,
    val saving: Boolean = false,
    val done: Boolean = false,
) {
    val odometer: Double? get() = parseDecimal(odometerText)
    val unitPrice: Double? get() = parseDecimal(unitPriceText)?.takeIf { it > 0 }

    /** Litres (or kWh): typed, or derived from the AED total. */
    val amount: Double?
        get() = when (amountMode) {
            AmountMode.LITRES -> parseDecimal(litresText)
            AmountMode.AED -> {
                val total = parseDecimal(totalText)
                val p = unitPrice
                if (total != null && p != null) total / p else null
            }
        }?.takeIf { it > 0 }

    /** AED total: typed, or derived from litres x price. */
    val total: Double?
        get() = when (amountMode) {
            AmountMode.AED -> parseDecimal(totalText)
            AmountMode.LITRES -> {
                val l = parseDecimal(litresText)
                val p = unitPrice
                if (l != null && p != null) l * p else null
            }
        }?.takeIf { it > 0 }

    val canSave: Boolean get() = !saving && !noVehicle && odometer != null && amount != null
}

class FillViewModel(
    private val container: AppContainer,
    private val editEventId: Id?,
    private val draft: FillDraft?,
) : ViewModel() {

    private val _state = MutableStateFlow(FillUiState())
    val state: StateFlow<FillUiState> = _state.asStateFlow()

    private var data: VehicleData? = null
    private var store: EnergyStore? = null
    private var original: EnergyEvent? = null

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val d = container.selectedVehicleData().first()
        if (d == null) {
            _state.update { it.copy(loading = false, noVehicle = true) }
            return
        }
        data = d
        original = editEventId?.let { id -> d.events.firstOrNull { it.id == id } }
        val orig = original
        store = orig?.let { o -> d.stores.firstOrNull { it.id == o.storeId } } ?: d.primaryStore
        val electric = store?.kind == EnergyStoreKind.BATTERY
        val grade = orig?.grade ?: store?.defaultGrade ?: d.vehicle.defaultGrade
        val grades = if (electric) listOf(FuelGrade.ELECTRIC) else ae.sayyara.app.ui.common.LiquidGrades

        val now = Clock.System.now()
        val timestamp = orig?.timestamp ?: now
        val month = monthOf(timestamp)
        val official = if (electric) null else officialPrice(month, grade)

        if (orig != null) {
            _state.value = FillUiState(
                loading = false,
                vehicleName = d.vehicle.name,
                isEdit = true,
                isElectric = electric,
                timestamp = orig.timestamp,
                odometerText = orig.odometerKm.toInputText(1),
                lastOdometerKm = d.events.filter { it.id != orig.id && it.timestamp < orig.timestamp }
                    .maxOfOrNull { it.odometerKm },
                amountMode = AmountMode.LITRES,
                litresText = orig.amount.toInputText(2),
                unitPriceText = (orig.unitPriceAed ?: official)?.toInputText(2) ?: "",
                priceSource = orig.priceSource,
                officialPrice = official,
                priceMonth = month,
                grades = grades,
                grade = orig.grade,
                isFull = orig.isFull,
                missedPrevious = orig.missedPrevious,
                showLevels = (orig.levelBefore ?: orig.socBefore) != null,
                levelBefore = (orig.levelBefore ?: orig.socBefore ?: 0.25).toFloat(),
                levelAfter = (orig.levelAfter ?: orig.socAfter ?: 1.0).toFloat(),
                stationBrand = orig.stationName,
            )
        } else {
            val last = d.lastOdometerKm()
            val (odoText, estimated) = when {
                draft?.odometerKm != null -> draft.odometerKm.toInputText(0) to false
                last != null -> {
                    val perDay = d.kmPerDay()
                    val lastAt = d.events.maxByOrNull { it.timestamp }?.timestamp
                    val days = lastAt?.let { (now - it).inWholeHours / 24.0 } ?: 0.0
                    val est = if (perDay != null && days > 0.5) last + perDay * days else last
                    est.roundToLong().toDouble().toInputText(0) to true
                }
                else -> "" to false
            }
            val levelBefore = draft?.levelBefore
            val levelAfter = draft?.levelAfter
            _state.value = FillUiState(
                loading = false,
                vehicleName = d.vehicle.name,
                isElectric = electric,
                fromCar = draft?.fromCar == true,
                timestamp = now,
                odometerText = odoText,
                odometerEstimated = estimated,
                lastOdometerKm = last,
                litresText = draft?.litres?.toInputText(2) ?: "",
                unitPriceText = official?.toInputText(2) ?: "",
                priceSource = if (official != null) PriceSource.OFFICIAL_MONTH else PriceSource.USER,
                officialPrice = official,
                priceMonth = month,
                grades = grades,
                grade = grade,
                isFull = levelAfter?.let { it >= 0.97 } ?: true,
                showLevels = levelBefore != null || levelAfter != null,
                levelBefore = (levelBefore ?: 0.25).toFloat(),
                levelAfter = (levelAfter ?: 1.0).toFloat(),
            )
        }
        revalidate()
    }

    private suspend fun officialPrice(month: YearMonth, grade: FuelGrade): Double? =
        runCatching {
            (container.fuelPrices.priceFor(month, grade) ?: container.fuelPrices.latest(grade))?.aedPerLitre
        }.getOrNull()

    private fun monthOf(instant: Instant): YearMonth =
        YearMonth.of(instant.toLocalDateTime(TimeZone.currentSystemDefault()).date)

    fun onOdometer(text: String) = edit { it.copy(odometerText = text, odometerEstimated = false) }

    fun onAmountMode(mode: AmountMode) = edit { s ->
        if (s.amountMode == mode) return@edit s
        // Carry the computed value across so switching never loses input.
        when (mode) {
            AmountMode.AED -> s.copy(amountMode = mode, totalText = s.total?.toInputText(2) ?: s.totalText)
            AmountMode.LITRES -> s.copy(amountMode = mode, litresText = s.amount?.toInputText(2) ?: s.litresText)
        }
    }

    fun onLitres(text: String) = edit { it.copy(litresText = text) }

    fun onTotal(text: String) = edit { it.copy(totalText = text) }

    fun onUnitPrice(text: String) = edit {
        val p = parseDecimal(text)
        it.copy(
            unitPriceText = text,
            priceSource = if (p != null && p == it.officialPrice) PriceSource.OFFICIAL_MONTH else PriceSource.USER,
        )
    }

    fun useOfficialPrice() = edit { s ->
        s.officialPrice?.let { s.copy(unitPriceText = it.toInputText(2), priceSource = PriceSource.OFFICIAL_MONTH) } ?: s
    }

    fun onGrade(grade: FuelGrade) {
        val s = _state.value
        edit { it.copy(grade = grade) }
        viewModelScope.launch {
            val month = s.priceMonth ?: monthOf(s.timestamp)
            val official = if (s.isElectric) null else officialPrice(month, grade)
            edit { cur ->
                val replacePrice = cur.priceSource == PriceSource.OFFICIAL_MONTH || cur.unitPriceText.isBlank()
                cur.copy(
                    officialPrice = official,
                    unitPriceText = if (replacePrice && official != null) official.toInputText(2) else cur.unitPriceText,
                    priceSource = if (replacePrice && official != null) PriceSource.OFFICIAL_MONTH else cur.priceSource,
                )
            }
        }
    }

    fun onFull(full: Boolean) = edit { it.copy(isFull = full) }

    fun onMissedPrevious(missed: Boolean) = edit { it.copy(missedPrevious = missed) }

    fun onShowLevels(show: Boolean) = edit { it.copy(showLevels = show) }

    fun onLevelBefore(v: Float) = edit { it.copy(levelBefore = v.coerceIn(0f, it.levelAfter)) }

    fun onLevelAfter(v: Float) = edit {
        it.copy(levelAfter = v.coerceIn(it.levelBefore, 1f), isFull = if (v >= 0.97f) true else it.isFull)
    }

    fun onStation(brand: String) = edit { it.copy(stationBrand = if (it.stationBrand == brand) null else brand) }

    fun acceptPartial() = edit { it.copy(isFull = false, suggestPartial = false) }

    fun onReceipt(fields: ReceiptFields?) {
        if (fields == null) {
            edit { it.copy(scanFailed = true) }
            return
        }
        // Locals: properties of a core-module class cannot be smart-cast here.
        val litres = fields.litres?.takeIf { it > 0 }
        val unitPrice = fields.unitPriceAed?.takeIf { it > 0 }
        val total = fields.totalAed?.takeIf { it > 0 }
        val scannedGrade = fields.grade
        val brand = fields.stationBrand
        edit { s ->
            val grade = scannedGrade?.takeIf { it in s.grades } ?: s.grade
            var next = s.copy(scanFailed = false, grade = grade, stationBrand = brand ?: s.stationBrand)
            val price = unitPrice ?: if (litres != null && total != null) total / litres else null
            if (price != null) {
                next = next.copy(unitPriceText = price.toInputText(3), priceSource = PriceSource.RECEIPT_OCR)
            }
            when {
                litres != null -> next.copy(amountMode = AmountMode.LITRES, litresText = litres.toInputText(2))
                total != null -> next.copy(amountMode = AmountMode.AED, totalText = total.toInputText(2))
                else -> next
            }
        }
    }

    fun dismissScanError() = edit { it.copy(scanFailed = false) }

    private inline fun edit(crossinline transform: (FillUiState) -> FillUiState) {
        _state.update { transform(it) }
        revalidate()
    }

    private fun candidate(s: FillUiState): EnergyEvent? {
        val d = data ?: return null
        val odo = s.odometer ?: return null
        val amount = s.amount ?: return null
        val st = store
        val now = Clock.System.now()
        val electric = s.isElectric
        val base = original ?: EnergyEvent(
            id = newId(),
            vehicleId = d.vehicle.id,
            storeId = st?.id ?: "${d.vehicle.id}-tank",
            timestamp = s.timestamp,
            odometerKm = odo,
            amount = amount,
            grade = s.grade,
            updatedAt = now,
        )
        val before = if (s.showLevels) s.levelBefore.toDouble() else null
        val after = if (s.showLevels) s.levelAfter.toDouble() else null
        return base.copy(
            odometerKm = odo,
            amount = amount,
            unitPriceAed = s.unitPrice,
            totalAed = s.total,
            isFull = s.isFull,
            missedPrevious = s.missedPrevious,
            levelBefore = if (electric) null else before,
            levelAfter = if (electric) null else after,
            socBefore = if (electric) before else null,
            socAfter = if (electric) after else null,
            grade = s.grade,
            stationName = s.stationBrand,
            priceSource = s.priceSource,
            updatedAt = now,
        )
    }

    private fun revalidate() {
        val s = _state.value
        val c = candidate(s)
        val d = data
        if (c == null || d == null) {
            _state.update { it.copy(warnings = emptyList(), suggestPartial = false) }
            return
        }
        val history = d.eventsFor(store).filter { it.id != c.id }
        val warnings = runCatching { FillValidation.check(c, history, store) }.getOrDefault(emptyList())
        val partial = s.isFull && runCatching { FillValidation.suggestPartial(c, history, store) }.getOrDefault(false)
        _state.update { it.copy(warnings = warnings, suggestPartial = partial) }
    }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        val d = data ?: return
        val event = candidate(s) ?: return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            if (d.stores.isEmpty()) {
                // Vehicle without a store (should not happen after onboarding): create the default tank.
                container.vehicles.upsertVehicle(
                    d.vehicle,
                    listOf(
                        EnergyStore(
                            id = event.storeId,
                            vehicleId = d.vehicle.id,
                            kind = EnergyStoreKind.TANK,
                            unit = EnergyUnit.LITRE,
                            capacity = null,
                            defaultGrade = event.grade,
                        ),
                    ),
                )
            }
            container.events.upsert(event)
            _state.update { it.copy(saving = false, done = true) }
        }
    }

    fun delete() {
        val id = original?.id ?: return
        viewModelScope.launch {
            container.events.delete(id)
            _state.update { it.copy(done = true) }
        }
    }
}
