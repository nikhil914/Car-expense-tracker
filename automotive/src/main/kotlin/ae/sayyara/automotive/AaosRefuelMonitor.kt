package ae.sayyara.automotive

import ae.sayyara.core.refuel.CarSignals
import ae.sayyara.core.refuel.ChargeConfig
import ae.sayyara.core.refuel.ChargeDetector
import ae.sayyara.core.refuel.RefuelConfig
import ae.sayyara.core.refuel.RefuelDetector
import ae.sayyara.core.refuel.VehicleSample
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyConfig
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.math.abs
import kotlin.math.max
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Live refuel / charge detection on Android Automotive OS through [CarPropertyManager].
 *
 * Signals (each one guarded: carmakers may not implement or may block any of them):
 *  - FUEL_LEVEL (mL, continuous) -> litres. Needs CAR_ENERGY (dangerous).
 *  - FUEL_DOOR_OPEN (on-change): the strongest trigger. While open, levels are ignored; on close the detector reads
 *    the new level after a short settle. Needs CAR_ENERGY_PORTS (normal).
 *  - IGNITION_STATE (on-change): off -> on is a trigger (the cluster re-samples the sender at start). CAR_POWERTRAIN.
 *  - PERF_ODOMETER, only if CAR_MILEAGE_3P is granted: rejects "rises" while driving > 2 km.
 *  - PERF_VEHICLE_SPEED (CAR_SPEED) and PARKING_BRAKE_ON (CAR_POWERTRAIN): stationary check (slosh).
 *  - INFO_FUEL_CAPACITY (static, CAR_INFO): tank size for percent and clamping.
 *  - RANGE_REMAINING (CAR_ENERGY): display only.
 *  - EVs: EV_BATTERY_LEVEL (Wh) / INFO_EV_BATTERY_CAPACITY (Wh) -> SoC % into core [ChargeDetector], with
 *    EV_CHARGE_PORT_CONNECTED mapped onto VehicleSample.fuelDoorOpen. Plugged in = "door open" (levels ignored while
 *    charging), unplugged = "door closed", which triggers the evaluation, exactly as for a fuel door.
 *
 * Lifetime and background limits (see also RefuelSnapshot):
 *  - This live monitor runs only while the car app Session exists, i.e. while the templates host keeps our
 *    CarAppService bound (app on screen or recently used). We deliberately do NOT run a foreground service:
 *    AAOS applies the normal Android background-execution limits; a long-running FGS would need a declared
 *    foregroundServiceType that matches the Play policy for that type, and "fuel logging" fits none of them.
 *    Google Play also only accepts car apps in a supported category (we are POI), and the quality guidelines
 *    do not allow background UI while driving.
 *  - Refuels done while the head unit sleeps (engine off at the pump, the common case in the UAE) are
 *    caught by RefuelSnapshot: a 15-minute periodic JobScheduler job and a boot receiver compare the current level
 *    with the last stored one.
 *
 * Callbacks arrive on the main looper. All detector work happens there; file I/O goes to [Dispatchers.IO].
 */
class AaosRefuelMonitor(context: Context) {

    data class Capabilities(
        val fuelLevel: Boolean,
        val fuelDoor: Boolean,
        val ignition: Boolean,
        val odometer: Boolean,
        val speed: Boolean,
        val parkingBrake: Boolean,
        val range: Boolean,
        val evBattery: Boolean,
        val chargePort: Boolean,
        val tankCapacityL: Double?,
        val evCapacityKwh: Double?,
    )

    sealed interface Status {
        data object Connecting : Status
        data object CarServiceUnavailable : Status
        data class NeedsPermission(val missing: List<String>) : Status
        data object NoEnergyData : Status
        data class Running(
            val caps: Capabilities,
            val fuelLitres: Double?,
            val fuelPercent: Double?,
            val socPercent: Double?,
            val rangeKm: Double?,
        ) : Status
    }

    private val appContext = context.applicationContext
    private val store = AaosServices.store(appContext)
    private val prefs = AaosServices.prefs(appContext)
    private val main = Handler(Looper.getMainLooper())
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _status = MutableStateFlow<Status>(Status.Connecting)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _detections = MutableSharedFlow<PendingRefuel>(extraBufferCapacity = 4)
    val detections: SharedFlow<PendingRefuel> = _detections.asSharedFlow()

    private var car: Car? = null
    private var pm: CarPropertyManager? = null
    private var caps: Capabilities? = null
    private var refuelDetector: RefuelDetector? = null
    private var chargeDetector: ChargeDetector? = null
    private val subscribed = mutableListOf<Int>()
    private var running = false
    private var lastSnapshotSaveMs = 0L

    // Latest values
    private var fuelL: Double? = null
    private var doorOpen: Boolean? = null
    private var ignitionOn: Boolean? = null
    private var odoKm: Double? = null
    private var speedKmh: Double? = null
    private var parkingBrake: Boolean? = null
    private var rangeKm: Double? = null
    private var evWh: Double? = null
    private var portConnected: Boolean? = null

    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) = onValue(value)
        override fun onErrorEvent(propertyId: Int, areaId: Int) {
            Log.w(TAG, "Property error ${AaosCarReader.name(propertyId)} area $areaId")
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            feed()
            main.postDelayed(this, TICK_MS)
        }
    }

    /** Call from the main thread. Safe to call again after [stop], e.g. after permissions were granted. */
    fun start() {
        if (car != null) return
        if (!scope.isActiveScope()) scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        liveRunning = true
        _status.value = Status.Connecting
        car = try {
            Car.createCar(appContext, main, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { c, ready ->
                if (ready) onCarReady(c) else onCarLost()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Car.createCar failed", e)
            _status.value = Status.CarServiceUnavailable
            liveRunning = false
            null
        }
    }

    fun stop() {
        onCarLost()
        runCatching { car?.disconnect() }
        car = null
        liveRunning = false
        scope.cancel()
    }

    /** Re-reads permissions and resubscribes (after the runtime prompt). */
    fun restart() {
        stop()
        start()
    }

    // --- connection -------------------------------------------------------------------------------------------

    private fun onCarReady(c: Car) {
        val manager = try {
            c.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
        } catch (e: Exception) {
            null
        }
        if (manager == null) {
            _status.value = Status.CarServiceUnavailable
            return
        }
        pm = manager
        if (!AaosPermissions.canDetect(appContext)) {
            _status.value = Status.NeedsPermission(AaosPermissions.missingRuntime(appContext))
            // Fuel door and ignition need only normal permissions, but without levels there is nothing to measure.
            return
        }
        val c0 = probe(manager)
        caps = c0
        if (!c0.fuelLevel && !c0.evBattery) {
            _status.value = Status.NoEnergyData
            return
        }
        if (c0.fuelLevel) {
            refuelDetector = RefuelDetector(RefuelConfig(tankCapacityL = c0.tankCapacityL ?: FALLBACK_TANK_L))
        }
        if (c0.evBattery) {
            chargeDetector = ChargeDetector(ChargeConfig(batteryCapacityKwh = c0.evCapacityKwh))
        }
        // Initial values for on-change properties (an on-change subscription may not deliver the current value).
        if (c0.fuelDoor) doorOpen = AaosCarReader.readBoolean(manager, VehiclePropertyIds.FUEL_DOOR_OPEN)
        if (c0.ignition) ignitionOn = AaosCarReader.readInt(manager, VehiclePropertyIds.IGNITION_STATE)?.let(CarSignals::ignitionOnFromAaos)
        if (c0.chargePort) portConnected = AaosCarReader.readBoolean(manager, VehiclePropertyIds.EV_CHARGE_PORT_CONNECTED)

        replayStoredLevel()

        if (c0.fuelLevel) subscribe(manager, VehiclePropertyIds.FUEL_LEVEL, 1f)
        if (c0.fuelDoor) subscribe(manager, VehiclePropertyIds.FUEL_DOOR_OPEN, ON_CHANGE)
        if (c0.ignition) subscribe(manager, VehiclePropertyIds.IGNITION_STATE, ON_CHANGE)
        if (c0.odometer) subscribe(manager, VehiclePropertyIds.PERF_ODOMETER, 0.2f)
        if (c0.speed) subscribe(manager, VehiclePropertyIds.PERF_VEHICLE_SPEED, 1f)
        if (c0.parkingBrake) subscribe(manager, VehiclePropertyIds.PARKING_BRAKE_ON, ON_CHANGE)
        if (c0.range) subscribe(manager, VehiclePropertyIds.RANGE_REMAINING, 0.1f)
        if (c0.evBattery) subscribe(manager, VehiclePropertyIds.EV_BATTERY_LEVEL, 0.2f)
        if (c0.chargePort) subscribe(manager, VehiclePropertyIds.EV_CHARGE_PORT_CONNECTED, ON_CHANGE)

        running = true
        main.removeCallbacks(ticker)
        main.postDelayed(ticker, TICK_MS)
        publish()
    }

    private fun onCarLost() {
        running = false
        main.removeCallbacks(ticker)
        saveStoredLevel(force = true)
        val manager = pm
        if (manager != null && subscribed.isNotEmpty()) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                    manager.unsubscribePropertyEvents(callback)
                } else {
                    @Suppress("DEPRECATION")
                    manager.unregisterCallback(callback)
                }
            } catch (e: Exception) {
                Log.w(TAG, "unsubscribe failed", e)
            }
        }
        subscribed.clear()
        pm = null
    }

    private fun probe(manager: CarPropertyManager): Capabilities {
        fun has(id: Int) = AaosCarReader.isAvailable(manager, id)
        val tankMl = if (has(VehiclePropertyIds.INFO_FUEL_CAPACITY)) AaosCarReader.readFloat(manager, VehiclePropertyIds.INFO_FUEL_CAPACITY) else null
        val evWhCap = if (has(VehiclePropertyIds.INFO_EV_BATTERY_CAPACITY)) AaosCarReader.readFloat(manager, VehiclePropertyIds.INFO_EV_BATTERY_CAPACITY) else null
        return Capabilities(
            fuelLevel = has(VehiclePropertyIds.FUEL_LEVEL),
            fuelDoor = has(VehiclePropertyIds.FUEL_DOOR_OPEN),
            ignition = has(VehiclePropertyIds.IGNITION_STATE),
            odometer = AaosPermissions.granted(appContext, AaosPermissions.CAR_MILEAGE_3P) && has(VehiclePropertyIds.PERF_ODOMETER),
            speed = AaosPermissions.granted(appContext, AaosPermissions.CAR_SPEED) && has(VehiclePropertyIds.PERF_VEHICLE_SPEED),
            parkingBrake = has(VehiclePropertyIds.PARKING_BRAKE_ON),
            range = has(VehiclePropertyIds.RANGE_REMAINING),
            evBattery = has(VehiclePropertyIds.EV_BATTERY_LEVEL) && evWhCap != null && evWhCap > 0f,
            chargePort = has(VehiclePropertyIds.EV_CHARGE_PORT_CONNECTED),
            tankCapacityL = tankMl?.takeIf { it > 0f }?.let(CarSignals::litresFromMillilitres),
            evCapacityKwh = evWhCap?.takeIf { it > 0f }?.let { it / 1000.0 },
        )
    }

    private fun subscribe(manager: CarPropertyManager, propertyId: Int, targetRateHz: Float) {
        val config = AaosCarReader.config(manager, propertyId) ?: return
        val rate = if (config.changeMode == CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS) {
            targetRateHz.coerceIn(config.minSampleRate, max(config.minSampleRate, config.maxSampleRate))
        } else {
            ON_CHANGE
        }
        val ok = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                manager.subscribePropertyEvents(propertyId, rate, callback)
            } else {
                @Suppress("DEPRECATION")
                manager.registerCallback(callback, propertyId, rate)
            }
        } catch (e: Exception) {
            Log.w(TAG, "subscribe ${AaosCarReader.name(propertyId)} failed", e)
            false
        }
        if (ok) subscribed += propertyId
    }

    // --- values -----------------------------------------------------------------------------------------------

    private fun onValue(value: CarPropertyValue<*>) {
        if (value.status != CarPropertyValue.STATUS_AVAILABLE) return
        val v = value.value ?: return
        when (value.propertyId) {
            VehiclePropertyIds.FUEL_LEVEL -> (v as? Float)?.let { if (it >= 0f) fuelL = CarSignals.litresFromMillilitres(it) }
            VehiclePropertyIds.FUEL_DOOR_OPEN -> (v as? Boolean)?.let { open ->
                val changed = open != doorOpen
                doorOpen = open
                if (changed) feed() // edges matter: feed at once, not on the next tick
            }
            VehiclePropertyIds.IGNITION_STATE -> (v as? Int)?.let { st ->
                val on = CarSignals.ignitionOnFromAaos(st)
                val changed = on != ignitionOn
                ignitionOn = on
                if (changed) {
                    if (on == false) saveStoredLevel(force = true) // engine off: remember the level for the snapshot job
                    feed()
                }
            }
            VehiclePropertyIds.PERF_ODOMETER -> (v as? Float)?.let { if (it >= 0f) odoKm = it.toDouble() }
            VehiclePropertyIds.PERF_VEHICLE_SPEED -> (v as? Float)?.let { speedKmh = abs(it) * 3.6 } // m/s, negative in reverse
            VehiclePropertyIds.PARKING_BRAKE_ON -> (v as? Boolean)?.let { parkingBrake = it }
            VehiclePropertyIds.RANGE_REMAINING -> (v as? Float)?.let { rangeKm = it / 1000.0 }
            VehiclePropertyIds.EV_BATTERY_LEVEL -> (v as? Float)?.let { evWh = it.toDouble() }
            VehiclePropertyIds.EV_CHARGE_PORT_CONNECTED -> (v as? Boolean)?.let { connected ->
                val changed = connected != portConnected
                portConnected = connected
                if (changed) feed()
            }
        }
    }

    private fun socPercent(): Double? {
        val wh = evWh ?: return null
        val capKwh = caps?.evCapacityKwh ?: return null
        return (wh / 1000.0 / capKwh * 100.0).coerceIn(0.0, 100.0)
    }

    private fun fuelPercent(): Double? {
        val l = fuelL ?: return null
        val cap = caps?.tankCapacityL ?: return null
        return (l / cap * 100.0).coerceIn(0.0, 100.0)
    }

    /** Builds one sample from the latest values and feeds both detectors. Main thread. */
    private fun feed() {
        if (!running) return
        val now = Clock.System.now()
        val base = VehicleSample(
            at = now,
            fuelLevelLitres = fuelL,
            fuelLevelPercent = fuelPercent(),
            odometerKm = odoKm,
            speedKmh = speedKmh,
            ignitionOn = ignitionOn,
            parked = parkingBrake,
        )
        refuelDetector?.let { d ->
            if (fuelL != null) d.onSample(base.copy(fuelDoorOpen = doorOpen))?.let { r ->
                record(PendingRefuel.from(r, SOURCE_LIVE))
            }
        }
        chargeDetector?.let { d ->
            val soc = socPercent()
            if (soc != null) d.onSample(base.copy(evBatteryPercent = soc, fuelDoorOpen = portConnected))?.let { c ->
                record(PendingRefuel.from(c, SOURCE_LIVE))
            }
        }
        saveStoredLevel(force = false)
        publish()
    }

    private fun record(item: PendingRefuel) {
        scope.launch(Dispatchers.IO) {
            val added = runCatching { store.add(item) }.getOrDefault(false)
            if (added) {
                _detections.tryEmit(item)
                RefuelNotifier.notifyDetected(appContext, item)
            }
        }
    }

    private fun publish() {
        val c = caps ?: return
        _status.value = Status.Running(c, fuelL, fuelPercent(), socPercent(), rangeKm)
    }

    /**
     * Feeds the last stored level (from an earlier session or the snapshot job) as a short stationary,
     * ignition-off window. If fuel was added while we were not running, the first live readings then show up
     * as a rise across the gap.
     */
    private fun replayStoredLevel() {
        val s = prefs.snapshot() ?: return
        val nowMs = System.currentTimeMillis()
        if (nowMs - s.atEpochMs > MAX_REPLAY_AGE_MS || s.atEpochMs > nowMs) return
        val at = kotlin.time.Instant.fromEpochMilliseconds(s.atEpochMs)
        RefuelSnapshot.replay(refuelDetector, chargeDetector, s, at)
    }

    private fun saveStoredLevel(force: Boolean) {
        val nowMs = System.currentTimeMillis()
        if (!force && nowMs - lastSnapshotSaveMs < SNAPSHOT_EVERY_MS) return
        // Never overwrite the stored level with a reading taken while the fuel door or charge port is open.
        if (doorOpen == true || portConnected == true) return
        if (fuelL == null && socPercent() == null) return
        lastSnapshotSaveMs = nowMs
        prefs.saveSnapshot(StoredLevel(nowMs, fuelL, fuelPercent(), odoKm, socPercent()))
    }

    private fun CoroutineScope.isActiveScope(): Boolean = coroutineContext[kotlinx.coroutines.Job]?.isActive == true

    companion object {
        private const val TAG = "AaosRefuelMonitor"
        const val SOURCE_LIVE = "live"
        private const val ON_CHANGE = 0f // CarPropertyManager.SENSOR_RATE_ONCHANGE
        private const val TICK_MS = 2_000L
        private const val SNAPSHOT_EVERY_MS = 30_000L
        private const val MAX_REPLAY_AGE_MS = 14L * 24 * 3600 * 1000

        /** Used only when INFO_FUEL_CAPACITY is missing; FUEL_LEVEL is in mL, so litres stay exact, only % is off. */
        const val FALLBACK_TANK_L = 70.0

        /** True while a live monitor is connected; the snapshot job skips its run then. */
        @Volatile
        var liveRunning: Boolean = false
            private set
    }
}
