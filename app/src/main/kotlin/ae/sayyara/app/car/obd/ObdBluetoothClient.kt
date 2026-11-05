package ae.sayyara.app.car.obd

import ae.sayyara.core.obd.FuelUsedIntegrator
import ae.sayyara.core.obd.Obd
import ae.sayyara.core.obd.ObdReading
import ae.sayyara.core.obd.toSample
import ae.sayyara.core.refuel.DetectedRefuel
import ae.sayyara.core.refuel.RefuelConfig
import ae.sayyara.core.refuel.RefuelDetector
import ae.sayyara.core.refuel.VehicleSample
import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Marks the OBD-II Bluetooth transport as experimental: not wired into any screen, not field-tested. */
@RequiresOptIn(level = RequiresOptIn.Level.WARNING, message = "OBD-II Bluetooth transport is experimental.")
@Retention(AnnotationRetention.BINARY)
annotation class ExperimentalObd

/**
 * EXPERIMENTAL sketch: OBD-II over a Bluetooth Classic (SPP / RFCOMM) ELM327-compatible dongle.
 *
 * Flow: pick a paired adapter -> [run] connects with the SPP UUID, sends [Obd.initCommands], probes the supported
 * PID bitmaps, then polls [Obd.pollPids] every [pollInterval]. Each poll cycle becomes one [VehicleSample]
 * (via core `List<ObdReading>.toSample`) that feeds core [RefuelDetector]. Litres come from
 * fuel % x [tankCapacityL], so accuracy is that of the gauge sender (about +/-5-10 % of the tank).
 *
 * Limits:
 *  - BLE-only dongles (many cheap "OBD BLE" units, Vgate iCar Pro BLE) are not handled; they need a GATT transport.
 *  - Many cars do not answer PID 0x2F (fuel level) or 0xA6 (odometer); then no refuel can be detected.
 *  - Polling runs only while [run] is active (a foreground screen or a future foreground service). No background
 *    service is declared for it yet.
 *
 * Permissions (the caller requests them at runtime; see [requiredPermissions]):
 *  - API 31+: BLUETOOTH_CONNECT (paired devices, connect) and BLUETOOTH_SCAN (only for [startDiscovery]).
 *  - API <= 30: BLUETOOTH and BLUETOOTH_ADMIN (install-time), plus ACCESS_FINE_LOCATION for discovery.
 *
 * Keep one client per vehicle across reconnects: the detector lives here, and a reconnect after a gap is itself a
 * refuel trigger (the level before and after the gap are compared).
 */
@ExperimentalObd
class ObdBluetoothClient(
    context: Context,
    tankCapacityL: Double,
    private val pollInterval: Duration = 2.seconds,
) {
    sealed interface State {
        data object Idle : State
        data object Connecting : State
        data object Initialising : State
        data class Polling(val supportedPids: Set<Int>) : State
        data class Failed(val reason: String) : State
    }

    private val appContext = context.applicationContext
    private val adapter: BluetoothAdapter? = appContext.getSystemService(BluetoothManager::class.java)?.adapter
    private val detector = RefuelDetector(RefuelConfig(tankCapacityL = tankCapacityL))
    private val fuelUsed = FuelUsedIntegrator()

    @Volatile private var socket: BluetoothSocket? = null

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _samples = MutableSharedFlow<VehicleSample>(extraBufferCapacity = 16)
    val samples: SharedFlow<VehicleSample> = _samples.asSharedFlow()

    private val _refuels = MutableSharedFlow<DetectedRefuel>(replay = 1, extraBufferCapacity = 1)
    val refuels: SharedFlow<DetectedRefuel> = _refuels.asSharedFlow()

    /** Fuel burned since this client was created, integrated from PID 0x5E (or MAF). For trip cost and sanity checks. */
    val fuelUsedLitres: Double get() = fuelUsed.litres

    val isBluetoothOn: Boolean get() = adapter?.isEnabled == true

    /** Paired devices that look like OBD adapters first, then the rest. Empty without BLUETOOTH_CONNECT. */
    @SuppressLint("MissingPermission") // checked by canConnect()
    fun pairedDevices(): List<BluetoothDevice> {
        val a = adapter ?: return emptyList()
        if (!canConnect(appContext)) return emptyList()
        return try {
            a.bondedDevices.orEmpty().sortedByDescending { d -> looksLikeObd(d.name) }
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    /**
     * Starts classic discovery so the user can pair a new dongle (results arrive via the
     * BluetoothDevice.ACTION_FOUND broadcast, which the caller registers). Returns false without permission.
     */
    @SuppressLint("MissingPermission") // checked by canScan()
    fun startDiscovery(): Boolean {
        val a = adapter ?: return false
        if (!canScan(appContext)) return false
        return try { a.startDiscovery() } catch (e: SecurityException) { false }
    }

    /**
     * Connects to [device] and polls until the coroutine is cancelled or the link drops.
     * Returns normally after a link drop (state = Failed); the caller decides whether to reconnect.
     */
    suspend fun run(device: BluetoothDevice) = withContext(Dispatchers.IO) {
        if (!canConnect(appContext)) {
            _state.value = State.Failed("BLUETOOTH_CONNECT not granted")
            return@withContext
        }
        _state.value = State.Connecting
        val s = try {
            connect(device)
        } catch (e: IOException) {
            _state.value = State.Failed("connect: ${e.message}")
            return@withContext
        } catch (e: SecurityException) {
            _state.value = State.Failed("permission: ${e.message}")
            return@withContext
        }
        socket = s
        try {
            val input = s.inputStream
            val output = s.outputStream
            _state.value = State.Initialising
            for (cmd in Obd.initCommands) {
                exchange(input, output, cmd, if (cmd == "ATZ") 4.seconds else 2.seconds)
            }
            val supported = probeSupportedPids(input, output)
            val pids = if (supported.isEmpty()) Obd.pollPids else Obd.pollPids.filter { it in supported }
            _state.value = State.Polling(supported)
            pollLoop(input, output, pids)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.i(TAG, "OBD link closed: ${e.message}")
            _state.value = State.Failed("link: ${e.message}")
        } finally {
            close()
        }
    }

    fun close() {
        runCatching { socket?.close() }
        socket = null
        if (_state.value !is State.Failed) _state.value = State.Idle
    }

    // --- internals ----------------------------------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    private fun connect(device: BluetoothDevice): BluetoothSocket {
        // Discovery slows RFCOMM connects down a lot; cancel it (needs BLUETOOTH_SCAN on API 31+).
        if (canScan(appContext)) runCatching { adapter?.cancelDiscovery() }
        val secure = device.createRfcommSocketToServiceRecord(SPP_UUID)
        return try {
            secure.connect()
            secure
        } catch (e: IOException) {
            runCatching { secure.close() }
            // Many ELM327 clones only accept insecure (unauthenticated) RFCOMM.
            val insecure = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
            insecure.connect()
            insecure
        }
    }

    /** Asks 0100, 0120, ... while each bitmap says the next range exists. Empty set = probe failed. */
    private suspend fun probeSupportedPids(input: InputStream, output: OutputStream): Set<Int> {
        val all = HashSet<Int>()
        var base = 0x00
        while (base <= 0xC0) {
            // The first request may trigger "SEARCHING..." while the ELM327 detects the protocol: allow longer.
            val reply = exchange(input, output, Obd.command(base), if (base == 0) 10.seconds else 2.seconds)
            val reading = Obd.parse(reply) as? ObdReading.SupportedPids ?: break
            all += reading.supported
            if ((base + 0x20) !in reading.supported) break
            base += 0x20
        }
        return all
    }

    private suspend fun pollLoop(input: InputStream, output: OutputStream, pids: List<Int>) {
        while (true) {
            currentCoroutineContext().ensureActive()
            val started = TimeSource.Monotonic.markNow()
            val readings = ArrayList<ObdReading>(pids.size)
            for (pid in pids) {
                val reply = exchange(input, output, Obd.command(pid), 1500.milliseconds)
                Obd.parse(reply)?.let { readings += it }
            }
            val now = Clock.System.now()
            readings.forEach { fuelUsed.add(now, it) }
            // An answering ECU means the ignition is on. Parked is left to the speed reading.
            val sample = readings.toSample(now).copy(ignitionOn = true)
            _samples.tryEmit(sample)
            detector.onSample(sample)?.let { _refuels.tryEmit(it) }
            val wait = pollInterval - started.elapsedNow()
            if (wait.isPositive()) delay(wait)
        }
    }

    /**
     * Sends [command] + CR and reads until the ELM327 ">" prompt or [timeout].
     * Uses available()+delay instead of a blocking read so cancellation and timeouts work on RFCOMM streams.
     */
    private suspend fun exchange(input: InputStream, output: OutputStream, command: String, timeout: Duration): String {
        while (input.available() > 0) input.read() // drop stale bytes from a previous timed-out reply
        output.write((command + "\r").toByteArray(Charsets.US_ASCII))
        output.flush()
        val sb = StringBuilder()
        val mark = TimeSource.Monotonic.markNow()
        while (mark.elapsedNow() < timeout) {
            currentCoroutineContext().ensureActive()
            if (input.available() > 0) {
                val b = input.read()
                if (b < 0) throw IOException("stream closed")
                val c = b.toChar()
                if (c == '>') return sb.toString()
                sb.append(c)
            } else {
                delay(15)
            }
        }
        return sb.toString() // timed out: Obd.parse returns null for partial replies
    }

    companion object {
        private const val TAG = "ObdBluetoothClient"

        /** Bluetooth Classic Serial Port Profile. */
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        /** Runtime permissions to request before [pairedDevices] / [run] / [startDiscovery]. */
        fun requiredPermissions(includeScan: Boolean): Array<String> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (includeScan) arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
                else arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                // BLUETOOTH / BLUETOOTH_ADMIN are install-time; discovery needs location before API 31.
                if (includeScan) arrayOf(Manifest.permission.ACCESS_FINE_LOCATION) else emptyArray()
            }

        fun canConnect(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S || granted(context, Manifest.permission.BLUETOOTH_CONNECT)

        fun canScan(context: Context): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) granted(context, Manifest.permission.BLUETOOTH_SCAN)
            else granted(context, Manifest.permission.ACCESS_FINE_LOCATION)

        private fun granted(context: Context, permission: String) =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

        private fun looksLikeObd(name: String?): Boolean {
            val n = name?.uppercase() ?: return false
            return listOf("OBD", "ELM", "VLINK", "V-LINK", "VGATE", "OBDLINK", "KONNWEI", "ICAR").any { it in n }
        }
    }
}
