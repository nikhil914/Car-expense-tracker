package ae.sayyara.automotive

import ae.sayyara.core.refuel.CarSignals
import ae.sayyara.core.refuel.ChargeConfig
import ae.sayyara.core.refuel.ChargeDetector
import ae.sayyara.core.refuel.RefuelConfig
import ae.sayyara.core.refuel.RefuelDetector
import ae.sayyara.core.refuel.VehicleSample
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.property.CarPropertyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Lightweight background detection for refuels done while Sayyara was not on screen.
 *
 * At a UAE pump the engine is usually off, so the head unit is asleep or our session is gone. Instead of a
 * long-running service we take short snapshots:
 *  - a periodic JobScheduler job (15 min, the platform minimum) while the head unit is awake, and
 *  - a one-shot job right after BOOT_COMPLETED (many head units cold-boot when the car starts).
 * Each run reads FUEL_LEVEL (and EV_BATTERY_LEVEL) a few times over about 40 s, replays the last stored level as
 * the "before" window, and lets core RefuelDetector / ChargeDetector decide.
 *
 * Limits, so nobody over-promises this:
 *  - Head units that suspend to RAM do not send BOOT_COMPLETED on wake. Then the first periodic job after wake
 *    (up to ~15 min, or later under the job quota) catches the refuel, or the live monitor does if the user
 *    opens Sayyara first.
 *  - JobScheduler may defer jobs (standby buckets, quotas). A rarely used app gets fewer runs.
 *  - No foreground service, no exact alarms, no wake locks: this matches AAOS background rules and Play policy
 *    for a POI-category car app. Detection is best-effort and the driver always confirms.
 *  - The job needs CAR_ENERGY granted (runtime) beforehand; without it, it does nothing.
 */
object RefuelSnapshot {
    private const val TAG = "RefuelSnapshot"
    private const val JOB_PERIODIC = 4201
    private const val JOB_BOOT = 4202
    const val SOURCE_SNAPSHOT = "snapshot"

    /** Settle after the first reading must exceed DetectorTuning.settleAfterIgnition (30 s). */
    private const val SETTLE_MS = 32_000L
    private const val SAMPLE_GAP_MS = 2_000L
    private const val AFTER_SAMPLES = 4
    private const val REPLAY_COPIES = 6

    fun schedulePeriodic(context: Context) {
        val js = context.getSystemService(JobScheduler::class.java) ?: return
        if (js.getPendingJob(JOB_PERIODIC) != null) return
        val job = JobInfo.Builder(JOB_PERIODIC, ComponentName(context, RefuelSnapshotJobService::class.java))
            .setPeriodic(15 * 60 * 1000L)
            .setPersisted(true) // needs RECEIVE_BOOT_COMPLETED
            .build()
        runCatching { js.schedule(job) }.onFailure { Log.w(TAG, "schedule periodic failed", it) }
    }

    fun scheduleNow(context: Context) {
        val js = context.getSystemService(JobScheduler::class.java) ?: return
        val job = JobInfo.Builder(JOB_BOOT, ComponentName(context, RefuelSnapshotJobService::class.java))
            .setMinimumLatency(0)
            .setOverrideDeadline(60_000L)
            .build()
        runCatching { js.schedule(job) }.onFailure { Log.w(TAG, "schedule one-shot failed", it) }
    }

    /**
     * Feeds a stored level as a stationary, ignition-off window so the detector has a baseline. It is repeated
     * because the detector needs DetectorTuning.stableSamples readings before it forms one.
     */
    fun replay(refuel: RefuelDetector?, charge: ChargeDetector?, s: StoredLevel, at: Instant) {
        repeat(REPLAY_COPIES) { i ->
            val sample = VehicleSample(
                at = at + i.seconds,
                fuelLevelLitres = s.fuelLitres,
                fuelLevelPercent = s.fuelPercent,
                odometerKm = s.odometerKm,
                speedKmh = 0.0,
                ignitionOn = false,
                parked = true,
                fuelDoorOpen = false,
            )
            if (refuel != null && (s.fuelLitres != null || s.fuelPercent != null)) refuel.onSample(sample)
            if (charge != null && s.socPercent != null) charge.onSample(sample.copy(evBatteryPercent = s.socPercent))
        }
    }

    /** One snapshot run. Blocking (about 40 s); call from a worker thread. */
    fun run(context: Context) {
        if (AaosRefuelMonitor.liveRunning) return // the live monitor already watches
        if (!AaosPermissions.canDetect(context)) return
        val prefs = AaosServices.prefs(context)
        val car = try {
            Car.createCar(context)
        } catch (e: Exception) {
            null
        } ?: return
        try {
            val pm = car.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager ?: return
            val tankMl = AaosCarReader.readFloat(pm, VehiclePropertyIds.INFO_FUEL_CAPACITY)?.takeIf { it > 0f }
            val tankL = tankMl?.let(CarSignals::litresFromMillilitres)
            val evCapKwh = AaosCarReader.readFloat(pm, VehiclePropertyIds.INFO_EV_BATTERY_CAPACITY)?.takeIf { it > 0f }?.let { it / 1000.0 }
            val mileage = AaosPermissions.granted(context, AaosPermissions.CAR_MILEAGE_3P)

            fun read(): StoredLevel {
                val l = AaosCarReader.readFloat(pm, VehiclePropertyIds.FUEL_LEVEL)?.takeIf { it >= 0f }?.let(CarSignals::litresFromMillilitres)
                val wh = AaosCarReader.readFloat(pm, VehiclePropertyIds.EV_BATTERY_LEVEL)?.takeIf { it >= 0f }
                return StoredLevel(
                    atEpochMs = System.currentTimeMillis(),
                    fuelLitres = l,
                    fuelPercent = if (l != null && tankL != null) (l / tankL * 100.0).coerceIn(0.0, 100.0) else null,
                    odometerKm = if (mileage) AaosCarReader.readFloat(pm, VehiclePropertyIds.PERF_ODOMETER)?.toDouble() else null,
                    socPercent = if (wh != null && evCapKwh != null) (wh / 1000.0 / evCapKwh * 100.0).coerceIn(0.0, 100.0) else null,
                )
            }

            val first = read()
            if (first.fuelLitres == null && first.socPercent == null) return // nothing readable on this car
            val before = prefs.snapshot()
            val doorOpen = AaosCarReader.readBoolean(pm, VehiclePropertyIds.FUEL_DOOR_OPEN)
            val portConnected = AaosCarReader.readBoolean(pm, VehiclePropertyIds.EV_CHARGE_PORT_CONNECTED)
            if (doorOpen == true || portConnected == true) return // still filling / charging: try next run

            if (before == null || before.atEpochMs >= first.atEpochMs) {
                prefs.saveSnapshot(first)
                return
            }
            val refuel = if (first.fuelLitres != null) RefuelDetector(RefuelConfig(tankCapacityL = tankL ?: AaosRefuelMonitor.FALLBACK_TANK_L)) else null
            val charge = if (first.socPercent != null) ChargeDetector(ChargeConfig(batteryCapacityKwh = evCapKwh)) else null
            replay(refuel, charge, before, Instant.fromEpochMilliseconds(before.atEpochMs))

            val ignition = AaosCarReader.readInt(pm, VehiclePropertyIds.IGNITION_STATE)?.let(CarSignals::ignitionOnFromAaos)
            val store = AaosServices.store(context)
            var last = first
            // The first reading opens the event (gap / ignition); the detector ignores levels during its settle time,
            // so the following readings are taken after SETTLE_MS.
            val readings = sequence {
                yield(first)
                Thread.sleep(SETTLE_MS)
                repeat(AFTER_SAMPLES) {
                    yield(read())
                    Thread.sleep(SAMPLE_GAP_MS)
                }
            }
            for (r in readings) {
                last = r
                val sample = VehicleSample(
                    at = Instant.fromEpochMilliseconds(r.atEpochMs),
                    fuelLevelLitres = r.fuelLitres,
                    fuelLevelPercent = r.fuelPercent,
                    odometerKm = r.odometerKm,
                    // No speed subscription here: the job runs mostly parked; ignition tells the detector the rest.
                    ignitionOn = ignition ?: true,
                    fuelDoorOpen = false,
                )
                if (refuel != null && r.fuelLitres != null) refuel.onSample(sample)?.let { d ->
                    val item = PendingRefuel.from(d, SOURCE_SNAPSHOT)
                    if (store.add(item)) RefuelNotifier.notifyDetected(context, item)
                }
                if (charge != null && r.socPercent != null) charge.onSample(sample.copy(evBatteryPercent = r.socPercent))?.let { d ->
                    val item = PendingRefuel.from(d, SOURCE_SNAPSHOT)
                    if (store.add(item)) RefuelNotifier.notifyDetected(context, item)
                }
            }
            prefs.saveSnapshot(last)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (e: Exception) {
            Log.w(TAG, "snapshot failed", e)
        } finally {
            runCatching { car.disconnect() }
        }
    }
}

/** Runs one [RefuelSnapshot.run] on a worker thread. */
class RefuelSnapshotJobService : JobService() {
    @Volatile private var worker: Thread? = null

    override fun onStartJob(params: JobParameters): Boolean {
        worker = thread(name = "sayyara-refuel-snapshot") {
            RefuelSnapshot.run(applicationContext)
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        worker?.interrupt()
        return false // periodic job runs again on schedule; no retry needed
    }
}

/** Re-arms the periodic job after boot and takes one snapshot right away (head unit cold-booted with the car). */
class SnapshotBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        RefuelSnapshot.schedulePeriodic(context)
        RefuelSnapshot.scheduleNow(context)
    }
}
