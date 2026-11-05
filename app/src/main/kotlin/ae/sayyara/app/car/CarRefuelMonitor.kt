package ae.sayyara.app.car

import ae.sayyara.app.SayyaraApp
import ae.sayyara.app.data.AppContainer
import ae.sayyara.core.model.EnergyStoreKind
import ae.sayyara.core.model.FuelGrade
import ae.sayyara.core.model.Id
import ae.sayyara.core.refuel.DetectedRefuel
import ae.sayyara.core.refuel.RefuelConfig
import ae.sayyara.core.refuel.RefuelDetector
import ae.sayyara.core.refuel.VehicleSample
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.common.OnCarDataAvailableListener
import androidx.car.app.hardware.info.CarInfo
import androidx.car.app.hardware.info.EnergyLevel
import androidx.car.app.hardware.info.EnergyProfile
import androidx.car.app.hardware.info.Mileage
import androidx.car.app.hardware.info.Speed
import androidx.core.content.ContextCompat
import java.util.concurrent.Executor
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Android Auto (projection) refuel monitor.
 *
 * While the car app session is alive it listens to CarInfo energy level, mileage and speed, turns them into
 * [VehicleSample]s and feeds core [RefuelDetector]. Android Auto has no fuel-door signal and gives fuel only as
 * a percentage, so litres = percent jump x the tank capacity of the selected vehicle's TANK [EnergyStore].
 *
 * Refuels usually happen with the phone disconnected (engine off at the pump). To catch them, the last reading of
 * a session is persisted and replayed at the start of the next one; the time gap between the two is a GAP trigger
 * for the detector, and the level step across it is the refuel.
 *
 * Every CarValue is checked for [CarValue.STATUS_SUCCESS]. Many cars return UNIMPLEMENTED/UNAVAILABLE for fuel;
 * then [state] becomes [State.Unavailable] with [Reason.NO_FUEL_LEVEL] and the UI explains it.
 *
 * Call [start] and [stop] on the main thread (the session lifecycle does).
 */
class CarRefuelMonitor(
    private val carContext: CarContext,
    private val scope: CoroutineScope,
) {
    enum class Reason { NO_VEHICLE, NO_TANK_CAPACITY, ELECTRIC_ONLY, NO_FUEL_LEVEL, NO_PERMISSION, NOT_SUPPORTED }

    sealed interface State {
        data object Starting : State
        data class Active(val fuelPercent: Double?, val odometerKm: Double?, val tankCapacityL: Double) : State
        data class Unavailable(val reason: Reason) : State
    }

    /** A detected refuel plus what the UI needs to save it. [odometerKm] is the best known odometer (may be null). */
    data class Detection(
        val refuel: DetectedRefuel,
        val vehicleId: Id,
        val storeId: Id,
        val grade: FuelGrade,
        val odometerKm: Double?,
    )

    private data class Target(val vehicleId: Id, val storeId: Id, val grade: FuelGrade, val capacityL: Double)

    private val container: AppContainer get() = (carContext.applicationContext as SayyaraApp).container
    private val prefs: SharedPreferences =
        carContext.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val executor: Executor = ContextCompat.getMainExecutor(carContext)

    private val _state = MutableStateFlow<State>(State.Starting)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _detections = MutableSharedFlow<Detection>(replay = 1, extraBufferCapacity = 1)
    /** Replays the latest detection so a screen subscribed slightly late still sees it; consumers dedupe by `refuel.at`. */
    val detections: SharedFlow<Detection> = _detections.asSharedFlow()

    /** Latest fuel percent with STATUS_SUCCESS, for the "Log fill-up" draft (level before). */
    val currentFuelPercent: Double? get() = fuelPct

    private var carInfo: CarInfo? = null
    private var detector: RefuelDetector? = null
    private var target: Target? = null
    private var started = false
    private var listenersAdded = false
    private var gotFuel = false
    private var startJob: Job? = null
    private var tickerJob: Job? = null
    private var timeoutJob: Job? = null

    private var fuelPct: Double? = null
    private var odoKm: Double? = null
    private var speedKmh: Double? = null
    private var lastPersistAt: Instant? = null

    private val energyListener = OnCarDataAvailableListener<EnergyLevel> { data -> onEnergy(data) }
    private val mileageListener = OnCarDataAvailableListener<Mileage> { data -> onMileage(data) }
    private val speedListener = OnCarDataAvailableListener<Speed> { data -> onSpeed(data) }
    private val profileListener = OnCarDataAvailableListener<EnergyProfile> { data -> onProfile(data) }

    fun start() {
        if (started) return
        started = true
        startJob = scope.launch {
            val t = resolveTarget() ?: return@launch
            target = t
            detector = RefuelDetector(RefuelConfig(tankCapacityL = t.capacityL))
            replayPersisted(t)
            _state.value = State.Active(null, null, t.capacityL)
            registerListeners()
        }
    }

    fun stop() {
        if (!started) return
        started = false
        startJob?.cancel()
        tickerJob?.cancel()
        timeoutJob?.cancel()
        persist(force = true)
        val info = carInfo
        if (info != null && listenersAdded) {
            runCatching { info.removeEnergyLevelListener(energyListener) }
            runCatching { info.removeMileageListener(mileageListener) }
            runCatching { info.removeSpeedListener(speedListener) }
        }
        listenersAdded = false
    }

    // --- setup ------------------------------------------------------------------------------------------------

    private suspend fun resolveTarget(): Target? {
        val vehicles = runCatching { container.vehicles.observeVehicles().first() }.getOrDefault(emptyList())
        val selected = runCatching { container.vehicles.observeSelectedVehicleId().first() }.getOrNull()
        val vehicle = vehicles.firstOrNull { it.id == selected && it.deletedAt == null }
            ?: vehicles.firstOrNull { it.active && it.deletedAt == null }
        if (vehicle == null) return unavailable(Reason.NO_VEHICLE)
        val stores = runCatching { container.vehicles.observeStores(vehicle.id).first() }.getOrDefault(emptyList())
        val tank = stores.firstOrNull { it.kind == EnergyStoreKind.TANK }
        if (tank == null) {
            return unavailable(if (stores.any { it.kind == EnergyStoreKind.BATTERY }) Reason.ELECTRIC_ONLY else Reason.NO_TANK_CAPACITY)
        }
        // The gauge percentage refers to the nominal tank, so prefer capacity over usableCapacity.
        val capacity = tank.capacity ?: tank.usableCapacity
        if (capacity == null || capacity <= 0.0) return unavailable(Reason.NO_TANK_CAPACITY)
        return Target(vehicle.id, tank.id, tank.defaultGrade, capacity)
    }

    private fun unavailable(reason: Reason): Nothing? {
        _state.value = State.Unavailable(reason)
        return null
    }

    private fun registerListeners() {
        val info = try {
            carContext.getCarService(CarHardwareManager::class.java).carInfo
        } catch (e: Exception) {
            // Host below Car API 3 or hardware manager missing.
            Log.w(TAG, "CarHardwareManager unavailable", e)
            _state.value = State.Unavailable(Reason.NOT_SUPPORTED)
            return
        }
        carInfo = info
        try {
            info.fetchEnergyProfile(executor, profileListener)
            info.addEnergyLevelListener(executor, energyListener)
            listenersAdded = true
        } catch (e: SecurityException) {
            // CAR_FUEL not granted.
            _state.value = State.Unavailable(Reason.NO_PERMISSION)
            return
        } catch (e: Exception) {
            Log.w(TAG, "addEnergyLevelListener failed", e)
            _state.value = State.Unavailable(Reason.NOT_SUPPORTED)
            return
        }
        // Mileage and speed are optional extras: without them the detector still works (it assumes stationary).
        runCatching { info.addMileageListener(executor, mileageListener) }
            .onFailure { Log.i(TAG, "Mileage not available: ${it.message}") }
        runCatching { info.addSpeedListener(executor, speedListener) }
            .onFailure { Log.i(TAG, "Speed not available: ${it.message}") }

        // STATUS_UNKNOWN can be transient right after connecting. If no fuel value arrives in time, explain.
        timeoutJob = scope.launch {
            delay(FUEL_TIMEOUT)
            if (!gotFuel) _state.value = State.Unavailable(Reason.NO_FUEL_LEVEL)
        }
        // The host may call back only on change. A steady tick gives the detector its stationary sample window.
        tickerJob = scope.launch {
            while (isActive) {
                delay(TICK)
                if (gotFuel) feed()
            }
        }
    }

    /**
     * Replays the last persisted reading of an earlier session so a refuel done while disconnected shows up as a
     * level step across the session gap. It is replayed [REPLAY_COPIES] times, one second apart, because the
     * detector needs a full stationary window (DetectorTuning.stableSamples) to form its baseline.
     */
    private fun replayPersisted(t: Target) {
        if (prefs.getString(KEY_VEHICLE, null) != t.vehicleId) return
        val pct = prefs.getFloat(KEY_PCT, Float.NaN).toDouble()
        val atMs = prefs.getLong(KEY_AT, 0L)
        if (pct.isNaN() || atMs <= 0L) return
        val odo = prefs.getFloat(KEY_ODO, Float.NaN).toDouble().takeUnless { it.isNaN() }
        val at = Instant.fromEpochMilliseconds(atMs)
        val d = detector ?: return
        repeat(REPLAY_COPIES) { i ->
            d.onSample(
                VehicleSample(
                    at = at + i.seconds,
                    fuelLevelPercent = pct,
                    odometerKm = odo,
                    speedKmh = 0.0,
                    ignitionOn = false,
                    parked = true,
                ),
            )
        }
        odoKm = odo
    }

    // --- callbacks (main thread) --------------------------------------------------------------------------------

    private fun onProfile(profile: EnergyProfile) {
        val types = profile.fuelTypes
        if (types.status != CarValue.STATUS_SUCCESS) return
        val list = types.value ?: return
        if (list.isNotEmpty() && list.all { it == EnergyProfile.FUEL_TYPE_ELECTRIC }) {
            // Pure EV: charging auto-detect is not done on Android Auto yet (no kWh, only battery %).
            _state.value = State.Unavailable(Reason.ELECTRIC_ONLY)
            stop()
        }
    }

    private fun onEnergy(data: EnergyLevel) {
        val fuel = data.fuelPercent
        when (fuel.status) {
            CarValue.STATUS_SUCCESS -> {
                val v = fuel.value ?: return
                if (v < 0f || v > 100f) return
                fuelPct = v.toDouble()
                if (!gotFuel) {
                    gotFuel = true
                    timeoutJob?.cancel()
                }
                feed()
            }
            CarValue.STATUS_UNIMPLEMENTED, CarValue.STATUS_UNAVAILABLE ->
                if (!gotFuel) _state.value = State.Unavailable(Reason.NO_FUEL_LEVEL)
            else -> Unit // STATUS_UNKNOWN: wait for the next callback or the timeout
        }
    }

    private fun onMileage(data: Mileage) {
        val odo = data.odometerMeters
        if (odo.status != CarValue.STATUS_SUCCESS) return
        val m = odo.value ?: return
        if (m >= 0f) odoKm = m / 1000.0
    }

    private fun onSpeed(data: Speed) {
        val raw = data.rawSpeedMetersPerSecond
        val value = when {
            raw.status == CarValue.STATUS_SUCCESS -> raw.value
            data.displaySpeedMetersPerSecond.status == CarValue.STATUS_SUCCESS -> data.displaySpeedMetersPerSecond.value
            else -> null
        } ?: return
        val kmh = kotlin.math.abs(value) * 3.6
        val wasParked = speedKmh?.let { it < PARKED_KMH }
        speedKmh = kmh
        // Feed on stop/start edges only; the ticker covers steady state.
        if (gotFuel && wasParked != (kmh < PARKED_KMH)) feed()
    }

    private fun feed() {
        val d = detector ?: return
        val t = target ?: return
        val pct = fuelPct ?: return
        val now = Clock.System.now()
        val sample = VehicleSample(
            at = now,
            fuelLevelPercent = pct,
            odometerKm = odoKm,
            speedKmh = speedKmh,
            ignitionOn = true, // projection only runs with the car on
            parked = speedKmh?.let { it < PARKED_KMH },
        )
        val refuel = d.onSample(sample)
        _state.value = State.Active(pct, odoKm, t.capacityL)
        persist(force = false)
        if (refuel != null && refuel.at.toEpochMilliseconds() != prefs.getLong(KEY_LAST_DETECTED, 0L)) {
            prefs.edit().putLong(KEY_LAST_DETECTED, refuel.at.toEpochMilliseconds()).apply()
            _detections.tryEmit(Detection(refuel, t.vehicleId, t.storeId, t.grade, refuel.odometerKm ?: odoKm))
        }
    }

    private fun persist(force: Boolean) {
        val t = target ?: return
        val pct = fuelPct ?: return
        val now = Clock.System.now()
        val last = lastPersistAt
        if (!force && last != null && now - last < PERSIST_EVERY) return
        lastPersistAt = now
        prefs.edit()
            .putString(KEY_VEHICLE, t.vehicleId)
            .putFloat(KEY_PCT, pct.toFloat())
            .putFloat(KEY_ODO, odoKm?.toFloat() ?: Float.NaN)
            .putLong(KEY_AT, now.toEpochMilliseconds())
            .apply()
    }

    private companion object {
        const val TAG = "CarRefuelMonitor"
        const val PREFS = "car_refuel_monitor"
        const val KEY_VEHICLE = "vehicle_id"
        const val KEY_PCT = "last_fuel_pct"
        const val KEY_ODO = "last_odo_km"
        const val KEY_AT = "last_at_ms"
        const val KEY_LAST_DETECTED = "last_detected_at_ms"
        const val REPLAY_COPIES = 6
        const val PARKED_KMH = 2.0
        val TICK = 5.seconds
        val FUEL_TIMEOUT = 30.seconds
        val PERSIST_EVERY = 30.seconds
    }
}
