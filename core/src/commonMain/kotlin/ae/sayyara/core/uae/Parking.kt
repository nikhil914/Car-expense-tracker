package ae.sayyara.core.uae

import ae.sayyara.core.model.Emirate
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class SmsDraft(val number: String, val body: String)

/** Local-time window, ISO days (1 = Mon .. 7 = Sun), minutes [from, to). */
@Serializable
data class TimeWindow(val days: List<Int>, val fromMinute: Int, val toMinute: Int) {
    fun contains(isoDay: Int, minute: Int) = isoDay in days && minute >= fromMinute && minute < toMinute
}

/**
 * Public parking tariff for one zone class. Outside [chargeable] parking is free (0.0). Inside it,
 * [peakAed] applies during [peak], otherwise [offPeakAed].
 */
@Serializable
data class ParkingTariff(
    val emirate: Emirate,
    val zone: String,
    val peakAed: Double,
    val offPeakAed: Double,
    val chargeable: List<TimeWindow>,
    val peak: List<TimeWindow> = emptyList(),
    val maxHours: Int? = null,
    val smsSurchargeAed: Double = 0.0,
    val note: String? = null,
)

@Serializable
data class ParkingTariffs(val version: Int = 1, val tariffs: List<ParkingTariff>) {
    fun toJson(): String = json.encodeToString(serializer(), this)
    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        fun fromJson(s: String): ParkingTariffs = json.decodeFromString(serializer(), s)
    }
}

/**
 * UAE public parking: Dubai (Parkin/RTA), Abu Dhabi (Mawaqif), Sharjah. All figures are from secondary
 * sources (research notes, Nov 2026) and must be remote-configurable; verify before relying on them.
 * Public holidays, Ramadan hours and multistorey/Parkonic sites are not modelled.
 */
object Parking {
    private val MON_SAT = listOf(1, 2, 3, 4, 5, 6)
    private fun h(x: Int) = x * 60

    // Dubai: 14 chargeable hours 08-22 Mon-Sat (Sunday free); peak 08-10 and 16-20 (Parkin variable
    // tariff, effective 4 Apr 2025, WAM/DFM filing). Dubai reportedly raised charges again ~Jun 2026
    // (ZigWheels) - figures below may be stale.
    private val dxbHours = listOf(TimeWindow(MON_SAT, h(8), h(22)))
    private val dxbPeak = listOf(TimeWindow(MON_SAT, h(8), h(10)), TimeWindow(MON_SAT, h(16), h(20)))

    val DEFAULT = ParkingTariffs(
        tariffs = listOf(
            // Premium spaces: AED 6/h peak. Off-peak 4 is UNVERIFIED (notes only give "other paid spaces AED 4").
            ParkingTariff(Emirate.DUBAI, "PREMIUM", 6.0, 4.0, dxbHours, dxbPeak, note = "off-peak unverified"),
            // Standard zones: AED 4/h at peak. Off-peak per Yallamotor for B and D off-street (4/3, 4/2);
            // A and C off-peak 4 is an assumption from "other paid spaces cost AED 4" - UNVERIFIED.
            ParkingTariff(Emirate.DUBAI, "A", 4.0, 4.0, dxbHours, dxbPeak, note = "off-peak unverified"),
            ParkingTariff(Emirate.DUBAI, "B", 4.0, 3.0, dxbHours, dxbPeak),
            ParkingTariff(Emirate.DUBAI, "C", 4.0, 4.0, dxbHours, dxbPeak, note = "off-peak unverified"),
            ParkingTariff(Emirate.DUBAI, "D", 4.0, 2.0, dxbHours, dxbPeak),
            // Event congestion pricing near venues (DWTC from Feb 2025): AED 25/h; hours assumed = standard.
            ParkingTariff(Emirate.DUBAI, "EVENT", 25.0, 25.0, dxbHours),
            // Abu Dhabi Mawaqif: standard AED 2/h (AED 15 per 24 h), premium AED 3/h (max 4 h); free Sundays.
            // Chargeable hours 08-24 Mon-Sat are from prior knowledge - UNVERIFIED.
            ParkingTariff(Emirate.ABU_DHABI, "STANDARD", 2.0, 2.0, listOf(TimeWindow(MON_SAT, h(8), h(24))), note = "AED 15 per 24h"),
            ParkingTariff(Emirate.ABU_DHABI, "PREMIUM", 3.0, 3.0, listOf(TimeWindow(MON_SAT, h(8), h(24))), maxHours = 4),
            // Sharjah: AED 2/h + 38 fils per SMS (AED 2.38 for 1 h via 5566). Chargeable hours 08-22 and
            // Friday free (Sat-Thu charged) are from prior knowledge - UNVERIFIED; 7-day blue zones not modelled.
            ParkingTariff(
                Emirate.SHARJAH, "STANDARD", 2.0, 2.0,
                listOf(TimeWindow(listOf(1, 2, 3, 4, 6, 7), h(8), h(22))),
                smsSurchargeAed = 0.38, note = "hours unverified",
            ),
        ),
    )

    /** Replace from a remote feed. */
    var tariffs: ParkingTariffs = DEFAULT

    private fun zoneKey(emirate: Emirate, zone: String): String {
        val z = zone.trim().uppercase()
        return when (emirate) {
            Emirate.DUBAI -> when {
                z == "P" || z == "PREMIUM" -> "PREMIUM"
                z == "EVENT" || z == "E" -> "EVENT"
                // Parkin zone codes look like "365C": numeric area + zone letter.
                else -> z.trimStart { it.isDigit() }.ifEmpty { z }
            }
            else -> when (z) { "P" -> "PREMIUM"; "S", "" -> "STANDARD"; else -> z }
        }
    }

    fun tariff(emirate: Emirate, zone: String, table: ParkingTariffs = tariffs): ParkingTariff? {
        val key = zoneKey(emirate, zone)
        return table.tariffs.firstOrNull { it.emirate == emirate && it.zone.equals(key, ignoreCase = true) }
    }

    /** AED per hour at [at] (Asia/Dubai local time); 0.0 when free at that time; null for unknown emirate/zone. */
    fun hourlyRate(emirate: Emirate, zone: String, at: Instant): Double? = hourlyRate(emirate, zone, at, tariffs)

    fun hourlyRate(emirate: Emirate, zone: String, at: Instant, table: ParkingTariffs, tz: TimeZone = UAE_ZONE): Double? {
        val t = tariff(emirate, zone, table) ?: return null
        val local = at.toLocalDateTime(tz)
        val day = local.dayOfWeek.isoDayNumber
        val minute = local.hour * 60 + local.minute
        if (t.chargeable.none { it.contains(day, minute) }) return 0.0
        return if (t.peak.any { it.contains(day, minute) }) t.peakAed else t.offPeakAed
    }

    /**
     * Estimated cost of parking [hours] from [start], billed hour by hour at each hour's rate
     * (a trailing fraction is billed as a full hour, as SMS parking is sold in whole hours).
     * Includes the per-SMS surcharge once. Null when the zone is unknown.
     */
    fun cost(emirate: Emirate, zone: String, start: Instant, hours: Double, table: ParkingTariffs = tariffs): Double? {
        val t = tariff(emirate, zone, table) ?: return null
        if (hours <= 0.0) return 0.0
        val whole = kotlin.math.ceil(hours - 1e-9).toInt()
        var sum = 0.0
        for (i in 0 until whole) {
            sum += hourlyRate(emirate, zone, start + i.hours, table) ?: 0.0
        }
        if (sum > 0.0) sum += t.smsSurchargeAed
        return kotlin.math.round(sum * 100) / 100.0
    }

    /**
     * SMS parking draft.
     * - Dubai: 7275, "<plate> <zone> <hours>" (Parkin/RTA format per Gulf News/Dubizzle). Reply "Y" extends.
     * - Abu Dhabi: 3009. Same "<plate> <zone> <hours>" layout is ASSUMED - format UNVERIFIED
     *   (Mawaqif may require plate source/category); confirm with real samples.
     * - Sharjah: 5566 (not 5155). "<plate> <zone> <hours>" layout ASSUMED - format UNVERIFIED.
     * Spaces inside the plate are removed so the body stays three tokens.
     */
    fun smsDraft(emirate: Emirate, plate: String, zone: String, hours: Int): SmsDraft {
        require(hours >= 1) { "hours must be >= 1" }
        val number = when (emirate) {
            Emirate.DUBAI -> "7275"
            Emirate.ABU_DHABI -> "3009"
            Emirate.SHARJAH -> "5566"
            else -> throw IllegalArgumentException("SMS parking not supported for $emirate")
        }
        val p = plate.filterNot { it.isWhitespace() }.uppercase()
        val z = zone.trim().uppercase()
        require(p.isNotEmpty() && z.isNotEmpty()) { "plate and zone required" }
        return SmsDraft(number, "$p $z $hours")
    }

    /** `sms:` URI for an intent, e.g. "sms:7275?body=A12345%20365C%202". */
    fun smsUri(draft: SmsDraft): String = "sms:${draft.number}?body=" + draft.body.replace(" ", "%20")
}
