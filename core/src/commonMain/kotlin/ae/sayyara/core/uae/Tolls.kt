package ae.sayyara.core.uae

import ae.sayyara.core.model.LatLon
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class TollCharge(val operator: String, val gateId: String, val gateName: String, val at: Instant, val aed: Double)

/**
 * A toll gate as a point. Crossing detection is direction-agnostic (polyline passes within the
 * engine's radius of the point); [linkGroup] gates passed in the same direction within
 * [TollTariffs.linkWindowMinutes] are charged once.
 *
 * All default coordinates are APPROXIMATE (hand-placed on the carriageway from map knowledge, not
 * from an official source) and NEED VERIFICATION against OSM / on-road GPS before release.
 */
@Serializable
data class TollGate(
    val id: String,
    val operator: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val scheduleId: String,
    val linkGroup: String? = null,
    val coordinatesApproximate: Boolean = true,
)

/**
 * A charging band. [days] are ISO day numbers (1 = Monday .. 7 = Sunday). Minutes are minutes of
 * local (Asia/Dubai) day, [fromMinute] inclusive, [toMinute] exclusive.
 */
@Serializable
data class TollBand(val days: List<Int>, val fromMinute: Int, val toMinute: Int, val baseAed: Double)

/** First matching band wins; otherwise [defaultBaseAed]. VAT is added when [vatInclusive]. */
@Serializable
data class TollSchedule(
    val id: String,
    val bands: List<TollBand>,
    val defaultBaseAed: Double,
    val vatRate: Double = 0.0,
    val vatInclusive: Boolean = false,
) {
    fun aedAt(isoDay: Int, minuteOfDay: Int): Double {
        val base = bands.firstOrNull { isoDay in it.days && minuteOfDay >= it.fromMinute && minuteOfDay < it.toMinute }?.baseAed
            ?: defaultBaseAed
        val gross = if (vatInclusive) base * (1 + vatRate) else base
        return (gross * 100).roundToLong() / 100.0
    }
}

/** Replaceable (remote-config) toll data. */
@Serializable
data class TollTariffs(
    val version: Int = 1,
    val gates: List<TollGate>,
    val schedules: List<TollSchedule>,
    val linkWindowMinutes: Int = 60,
) {
    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        fun fromJson(s: String): TollTariffs = json.decodeFromString(serializer(), s)

        const val SALIK = "Salik"
        const val DARB = "Darb"
        const val SALIK_SCHEDULE = "salik-2025-variable"
        const val DARB_SCHEDULE = "darb-2025-09"

        private val MON_SAT = listOf(1, 2, 3, 4, 5, 6)
        private val ALL_DAYS = listOf(1, 2, 3, 4, 5, 6, 7)
        private fun h(hour: Int) = hour * 60

        /**
         * Salik variable pricing since 31 Jan 2025: AED 6 peak 06-10 and 16-20 Mon-Sat; AED 4 off-peak
         * and all chargeable hours on Sunday; free 01:00-06:00 every day.
         *
         * VAT: 5% VAT on Salik (AED 6.30 / 4.20) is reported from 1 Jun 2026 by one source and 1 Jul 2026
         * by another; the start date is UNVERIFIED against Salik/FTA. Default is VAT-inclusive since both
         * dates are before today. Ramadan/Eid/public-holiday schedules are NOT modelled.
         */
        fun salikSchedule(vatInclusive: Boolean = true) = TollSchedule(
            id = SALIK_SCHEDULE,
            bands = listOf(
                TollBand(ALL_DAYS, h(1), h(6), 0.0),
                TollBand(MON_SAT, h(6), h(10), 6.0),
                TollBand(MON_SAT, h(16), h(20), 6.0),
            ),
            defaultBaseAed = 4.0,
            vatRate = 0.05,
            vatInclusive = vatInclusive,
        )

        /**
         * Darb (Abu Dhabi) since 1 Sep 2025: AED 4 per crossing at peak 07-09 and 15-19, Mon-Sat; free
         * otherwise and on Sundays. Daily/monthly caps removed (AD Mobility page; the older ITC page still
         * lists caps). Chargeable days are reported as Mon-Sat or Mon-Fri; Mon-Sat used. Holidays not modelled.
         */
        val darbSchedule = TollSchedule(
            id = DARB_SCHEDULE,
            bands = listOf(
                TollBand(MON_SAT, h(7), h(9), 4.0),
                TollBand(MON_SAT, h(15), h(19), 4.0),
            ),
            defaultBaseAed = 0.0,
        )

        /** 10 Salik gates (since 24 Nov 2024). Coordinates APPROXIMATE, need verification. */
        val salikGates = listOf(
            TollGate("salik-al-barsha", SALIK, "Al Barsha", 25.1158, 55.1938, SALIK_SCHEDULE),
            TollGate("salik-al-garhoud", SALIK, "Al Garhoud Bridge", 25.2385, 55.3445, SALIK_SCHEDULE),
            TollGate("salik-al-maktoum", SALIK, "Al Maktoum Bridge", 25.2555, 55.3255, SALIK_SCHEDULE),
            // Al Mamzar North/South historically charged once when passed together; NOT re-verified,
            // so they are not linked here. Add linkGroup = "al-mamzar" remotely once confirmed.
            TollGate("salik-al-mamzar-south", SALIK, "Al Mamzar South", 25.2935, 55.3660, SALIK_SCHEDULE),
            TollGate("salik-al-mamzar-north", SALIK, "Al Mamzar North", 25.3005, 55.3720, SALIK_SCHEDULE),
            TollGate("salik-al-safa-north", SALIK, "Al Safa North", 25.1885, 55.2510, SALIK_SCHEDULE, linkGroup = "al-safa"),
            TollGate("salik-al-safa-south", SALIK, "Al Safa South", 25.1605, 55.2225, SALIK_SCHEDULE, linkGroup = "al-safa"),
            TollGate("salik-airport-tunnel", SALIK, "Airport Tunnel", 25.2575, 55.3770, SALIK_SCHEDULE),
            TollGate("salik-jebel-ali", SALIK, "Jebel Ali", 25.0395, 55.1180, SALIK_SCHEDULE),
            TollGate("salik-business-bay", SALIK, "Business Bay Crossing", 25.1945, 55.3165, SALIK_SCHEDULE),
        )

        /** Original 4 Darb gates. Coordinates APPROXIMATE, need verification. */
        val darbGates = listOf(
            TollGate("darb-sheikh-zayed-bridge", DARB, "Sheikh Zayed Bridge", 24.4555, 54.3795, DARB_SCHEDULE),
            TollGate("darb-sheikh-khalifa-bridge", DARB, "Sheikh Khalifa Bridge", 24.5135, 54.4015, DARB_SCHEDULE),
            TollGate("darb-al-maqta-bridge", DARB, "Al Maqta Bridge", 24.4050, 54.4960, DARB_SCHEDULE),
            TollGate("darb-mussafah-bridge", DARB, "Mussafah Bridge", 24.3850, 54.5090, DARB_SCHEDULE),
        )
        // Not included: the reported 2026 Darb reshaping (6 gates, renames) and the new 24/7 E11 gates
        // at Ghantoot and Al Qurm (live 4 May 2026 per Shory). Both are unverified and no coordinates
        // or 24/7 tariff are sourced; add them through the remote tariff feed once confirmed.

        fun default2026(salikVatInclusive: Boolean = true) = TollTariffs(
            gates = salikGates + darbGates,
            schedules = listOf(salikSchedule(salikVatInclusive), darbSchedule),
        )
    }
}

/**
 * Computes Salik/Darb charges for a route polyline. Crossing times are estimated from cumulative
 * polyline distance at [avgSpeedKmh] from [departure], evaluated in Asia/Dubai local time.
 */
class TollEngine(
    val tariffs: TollTariffs = TollTariffs.default2026(),
    val crossingRadiusM: Double = 60.0,
    private val zone: TimeZone = UAE_ZONE,
) {
    private val schedules = tariffs.schedules.associateBy { it.id }

    data class Crossing(val gate: TollGate, val distanceKm: Double, val bearingDeg: Double)

    /** Ordered gate crossings along [polyline] (direction-agnostic, one per pass). */
    fun crossings(polyline: List<LatLon>): List<Crossing> {
        if (polyline.size < 2) return emptyList()
        val out = mutableListOf<Crossing>()
        for (gate in tariffs.gates) {
            var inside = false
            var cum = 0.0
            for (i in 0 until polyline.size - 1) {
                val a = polyline[i]
                val b = polyline[i + 1]
                val segKm = Geo.haversineKm(a, b)
                val (dM, t) = Geo.pointToSegment(gate.lat, gate.lon, a, b)
                if (dM <= crossingRadiusM) {
                    if (!inside) out += Crossing(gate, cum + segKm * t, Geo.bearingDeg(a, b))
                    inside = true
                } else {
                    inside = false
                }
                cum += segKm
            }
        }
        return out.sortedBy { it.distanceKm }
    }

    fun charges(polyline: List<LatLon>, departure: Instant, avgSpeedKmh: Double = 60.0): List<TollCharge> {
        require(avgSpeedKmh > 0) { "avgSpeedKmh must be > 0" }
        val result = mutableListOf<TollCharge>()
        val lastInGroup = mutableMapOf<String, Pair<Instant, Double>>() // group -> (time, bearing) of last charged
        for (c in crossings(polyline)) {
            val at = departure + (c.distanceKm / avgSpeedKmh * 3600.0).seconds
            val group = c.gate.linkGroup
            if (group != null) {
                val prev = lastInGroup[group]
                if (prev != null && (at - prev.first).inWholeMinutes < tariffs.linkWindowMinutes &&
                    Geo.angleDiff(prev.second, c.bearingDeg) < 90.0
                ) continue // linked gate, same direction, within window: charged once
            }
            val schedule = schedules[c.gate.scheduleId] ?: continue
            val local = at.toLocalDateTime(zone)
            val aed = schedule.aedAt(local.dayOfWeek.isoDayNumber, local.hour * 60 + local.minute)
            if (group != null) lastInGroup[group] = at to c.bearingDeg
            result += TollCharge(c.gate.operator, c.gate.id, c.gate.name, at, aed)
        }
        return result
    }
}

/** Minimal spherical geometry, pure Kotlin. */
object Geo {
    private const val R_KM = 6371.0088
    private fun rad(d: Double) = d * PI / 180.0

    fun haversineKm(a: LatLon, b: LatLon): Double {
        val dLat = rad(b.lat - a.lat)
        val dLon = rad(b.lon - a.lon)
        val h = sin(dLat / 2).let { it * it } + cos(rad(a.lat)) * cos(rad(b.lat)) * sin(dLon / 2).let { it * it }
        return 2 * R_KM * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    fun lengthKm(polyline: List<LatLon>): Double = polyline.zipWithNext().sumOf { (a, b) -> haversineKm(a, b) }

    /** Distance in metres from point (lat, lon) to segment ab, and the projection parameter t in [0, 1]. */
    fun pointToSegment(lat: Double, lon: Double, a: LatLon, b: LatLon): Pair<Double, Double> {
        val k = cos(rad(lat)) * R_KM * 1000 * PI / 180.0
        val m = R_KM * 1000 * PI / 180.0
        val ax = (a.lon - lon) * k; val ay = (a.lat - lat) * m
        val bx = (b.lon - lon) * k; val by = (b.lat - lat) * m
        val dx = bx - ax; val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
        val px = ax + t * dx; val py = ay + t * dy
        return sqrt(px * px + py * py) to t
    }

    fun bearingDeg(a: LatLon, b: LatLon): Double {
        val y = sin(rad(b.lon - a.lon)) * cos(rad(b.lat))
        val x = cos(rad(a.lat)) * sin(rad(b.lat)) - sin(rad(a.lat)) * cos(rad(b.lat)) * cos(rad(b.lon - a.lon))
        return (atan2(y, x) * 180.0 / PI + 360.0) % 360.0
    }

    fun angleDiff(a: Double, b: Double): Double = abs(((a - b) % 360.0 + 540.0) % 360.0 - 180.0)
}
