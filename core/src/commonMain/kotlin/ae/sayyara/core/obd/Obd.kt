package ae.sayyara.core.obd

import ae.sayyara.core.refuel.VehicleSample
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** A decoded OBD-II mode 01 reading. */
sealed class ObdReading {
    abstract val pid: Int

    /** PID 0x2F fuel tank level input, A*100/255 %. */
    data class FuelLevel(val percent: Double) : ObdReading() { override val pid get() = Obd.PID_FUEL_LEVEL }

    /** PID 0x0D vehicle speed, A km/h. */
    data class Speed(val kmh: Double) : ObdReading() { override val pid get() = Obd.PID_SPEED }

    /** PID 0x5E engine fuel rate, (256A+B)/20 L/h. */
    data class FuelRate(val litresPerHour: Double) : ObdReading() { override val pid get() = Obd.PID_FUEL_RATE }

    /** PID 0x10 mass air flow, (256A+B)/100 g/s. */
    data class Maf(val gramsPerSecond: Double) : ObdReading() { override val pid get() = Obd.PID_MAF }

    /** PID 0xA6 odometer, (A·2²⁴+B·2¹⁶+C·2⁸+D)/10 km. */
    data class Odometer(val km: Double) : ObdReading() { override val pid get() = Obd.PID_ODOMETER }

    /** PIDs 0x00, 0x20, ... 0xC0: bitmap of the next 32 supported PIDs. */
    data class SupportedPids(override val pid: Int, val supported: Set<Int>) : ObdReading()
}

/** ELM327 command building and response parsing for the PIDs refuel detection needs. */
object Obd {
    const val PID_SPEED = 0x0D
    const val PID_MAF = 0x10
    const val PID_FUEL_LEVEL = 0x2F
    const val PID_FUEL_RATE = 0x5E
    const val PID_ODOMETER = 0xA6

    /** Reset, echo off, linefeeds off, spaces off, headers off, automatic protocol. Send in order, wait for ">" each. */
    val initCommands: List<String> = listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0")

    /** PIDs polled by the refuel/consumption pipeline, in a sensible poll order. */
    val pollPids: List<Int> = listOf(PID_SPEED, PID_FUEL_LEVEL, PID_FUEL_RATE, PID_MAF, PID_ODOMETER)

    private val supportedPidRequests = setOf(0x00, 0x20, 0x40, 0x60, 0x80, 0xA0, 0xC0)

    /** Mode 01 request for [pid], e.g. `command(0x2F) == "012F"`. */
    fun command(pid: Int): String {
        require(pid in 0..0xFF) { "pid out of range: $pid" }
        return "01" + hex2(pid)
    }

    /** Same, with the ELM327 "expected responses" suffix (e.g. "012F1") so the adapter returns after the first ECU. */
    fun command(pid: Int, expectedResponses: Int): String {
        require(expectedResponses in 1..15)
        return command(pid) + expectedResponses.toString(16).uppercase()
    }

    /**
     * Parse a raw ELM327 reply. Tolerates the ">" prompt, CR/LF, spaces on or off, headers on (CAN 11-bit, CAN 29-bit,
     * J1850/ISO 9141 with checksum), echoed commands, "SEARCHING...", "BUS INIT: ...OK", ISO-TP multi-frame lines
     * ("0: ..", "1: ..") and replies from several ECUs (the first decodable one wins).
     * Returns null for "NO DATA", "?", errors, unknown PIDs or truncated data.
     */
    fun parse(response: String): ObdReading? {
        val lines = response.replace(">", "\n").split('\r', '\n').map { it.trim().uppercase() }.filter { it.isNotEmpty() }
        val frames = ArrayList<List<Int>>()
        val multi = ArrayList<Int>()
        var inMulti = false
        for (raw in lines) {
            if (isNoise(raw)) continue
            val frameMatch = Regex("^([0-9A-F]):\\s*(.*)$").find(raw)
            if (frameMatch != null) {
                inMulti = true
                multi += bytesOf(frameMatch.groupValues[2]) ?: continue
                continue
            }
            val bytes = bytesOf(raw) ?: continue
            // ISO-TP length line ("00A") before multi-frame data carries no payload.
            if (raw.replace(" ", "").length == 3 && bytes.size <= 1) continue
            frames += bytes
        }
        if (inMulti && multi.isNotEmpty()) decode(multi)?.let { return it }
        for (f in frames) decode(f)?.let { return it }
        return null
    }

    /** Fuel flow from MAF for petrol at stoichiometric AFR. For diesel pass afr ≈ 14.5 and density ≈ 832. */
    fun mafToLitresPerHour(mafGs: Double, afr: Double = 14.7, densityGPerL: Double = 745.0): Double =
        mafGs / afr / densityGPerL * 3600.0

    private fun isNoise(line: String): Boolean {
        val compact = line.replace(" ", "")
        return compact.startsWith("SEARCHING") || compact.startsWith("BUSINIT") || compact.contains("NODATA") ||
            compact == "?" || compact == "OK" || compact.startsWith("ELM") || compact.contains("ERROR") ||
            compact.startsWith("UNABLE") || compact.startsWith("STOPPED") || compact.startsWith("BUFFERFULL") ||
            compact.startsWith("CANERROR") || compact.startsWith("BUSBUSY") || compact.startsWith("FBERROR") ||
            compact.startsWith("DATAERROR") || compact.startsWith("<") || compact.startsWith("AT") ||
            // Echoed mode 01 request, e.g. "012F" or "01 2F 1".
            Regex("^01[0-9A-F]{2}[0-9A-F]?$").matches(compact)
    }

    /** Line to bytes. Drops an 11-bit CAN header ("7E8") whether spaces are on or off. Null if not hex. */
    private fun bytesOf(line: String): List<Int>? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null
        val tokens = trimmed.split(Regex("\\s+"))
        val hex: String = if (tokens.size > 1) {
            tokens.filter { it.length != 3 }.joinToString("") // drop 3-char CAN header token
        } else {
            val t = tokens[0]
            if (t.length % 2 == 1) t.substring(3) else t
        }
        if (hex.isEmpty() || hex.length % 2 == 1 || !hex.all { it in '0'..'9' || it in 'A'..'F' }) return null
        return hex.chunked(2).map { it.toInt(16) }
    }

    private fun decode(bytes: List<Int>): ObdReading? {
        for (i in 0 until bytes.size - 1) {
            if (bytes[i] != 0x41) continue
            val pid = bytes[i + 1]
            val d = bytes.subList(i + 2, bytes.size)
            decodePid(pid, d)?.let { return it }
        }
        return null
    }

    private fun decodePid(pid: Int, d: List<Int>): ObdReading? = when (pid) {
        PID_FUEL_LEVEL -> if (d.isNotEmpty()) ObdReading.FuelLevel(d[0] * 100.0 / 255.0) else null
        PID_SPEED -> if (d.isNotEmpty()) ObdReading.Speed(d[0].toDouble()) else null
        PID_FUEL_RATE -> if (d.size >= 2) ObdReading.FuelRate((256 * d[0] + d[1]) / 20.0) else null
        PID_MAF -> if (d.size >= 2) ObdReading.Maf((256 * d[0] + d[1]) / 100.0) else null
        PID_ODOMETER -> if (d.size >= 4) {
            val raw = (d[0].toLong() shl 24) or (d[1].toLong() shl 16) or (d[2].toLong() shl 8) or d[3].toLong()
            ObdReading.Odometer(raw / 10.0)
        } else null
        in supportedPidRequests -> if (d.size >= 4) {
            val set = HashSet<Int>()
            for (b in 0 until 4) for (bit in 0 until 8) {
                if ((d[b] shr (7 - bit)) and 1 == 1) set += pid + b * 8 + bit + 1
            }
            ObdReading.SupportedPids(pid, set)
        } else null
        else -> null
    }

    private fun hex2(v: Int) = v.toString(16).uppercase().padStart(2, '0')
}

/** One reading as a sample. Readings with no [VehicleSample] field (fuel rate, MAF, bitmaps) give a bare sample. */
fun ObdReading.toSample(at: Instant): VehicleSample = when (this) {
    is ObdReading.FuelLevel -> VehicleSample(at, fuelLevelPercent = percent)
    is ObdReading.Speed -> VehicleSample(at, speedKmh = kmh)
    is ObdReading.Odometer -> VehicleSample(at, odometerKm = km)
    else -> VehicleSample(at)
}

/** Merge one poll cycle of readings into a single sample (later readings win). */
fun List<ObdReading>.toSample(at: Instant): VehicleSample = fold(VehicleSample(at)) { s, r ->
    when (r) {
        is ObdReading.FuelLevel -> s.copy(fuelLevelPercent = r.percent)
        is ObdReading.Speed -> s.copy(speedKmh = r.kmh)
        is ObdReading.Odometer -> s.copy(odometerKm = r.km)
        else -> s
    }
}

/**
 * Integrates fuel flow over time (trapezoid rule). Prefers PID 0x5E; falls back to MAF only while no 0x5E reading
 * has been seen. Intervals longer than [maxGap] are skipped (connection drop) rather than extrapolated.
 */
class FuelUsedIntegrator(
    private val maxGap: Duration = 10.seconds,
    private val afr: Double = 14.7,
    private val densityGPerL: Double = 745.0,
) {
    var litres: Double = 0.0
        private set
    private var lastAt: Instant? = null
    private var lastRate: Double? = null
    private var sawFuelRate = false

    /** Add a flow sample in L/h. Out-of-order samples are ignored. */
    fun add(at: Instant, litresPerHour: Double) {
        val prevAt = lastAt
        val prevRate = lastRate
        if (prevAt != null && at < prevAt) return
        if (prevAt != null && prevRate != null) {
            val dt = at - prevAt
            if (dt <= maxGap) litres += (prevRate + litresPerHour) / 2.0 * (dt.inWholeMilliseconds / 3_600_000.0)
        }
        lastAt = at
        lastRate = litresPerHour.coerceAtLeast(0.0)
    }

    /** Feed any reading; only fuel rate and MAF contribute. */
    fun add(at: Instant, reading: ObdReading) {
        when (reading) {
            is ObdReading.FuelRate -> {
                if (!sawFuelRate) { sawFuelRate = true; lastAt = null; lastRate = null }
                add(at, reading.litresPerHour)
            }
            is ObdReading.Maf -> if (!sawFuelRate) add(at, Obd.mafToLitresPerHour(reading.gramsPerSecond, afr, densityGPerL))
            else -> Unit
        }
    }

    fun reset() {
        litres = 0.0; lastAt = null; lastRate = null; sawFuelRate = false
    }
}
