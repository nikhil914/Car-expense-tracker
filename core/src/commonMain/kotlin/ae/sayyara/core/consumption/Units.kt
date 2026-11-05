package ae.sayyara.core.consumption

import kotlin.math.abs
import kotlin.math.floor

enum class EconomyUnit { L_PER_100KM, KM_PER_L, MPG_US, MPG_UK, KWH_PER_100KM, KM_PER_KWH }

/** Converts the internal per-100-km figure (L/100km or kWh/100km) to display units. */
object Units {
    /** 100 km / US gallon in litres x miles conversion: mpg(US) = 235.214583 / (L/100km). */
    const val MPG_US_FACTOR = 235.214583
    /** mpg(UK) = 282.480936 / (L/100km). */
    const val MPG_UK_FACTOR = 282.480936

    /** Converts [v] (L/100km or kWh/100km) to [unit]. Inverse units return NaN for v <= 0. */
    fun fromPer100km(v: Double, unit: EconomyUnit): Double = when (unit) {
        EconomyUnit.L_PER_100KM, EconomyUnit.KWH_PER_100KM -> v
        EconomyUnit.KM_PER_L, EconomyUnit.KM_PER_KWH -> inverse(100.0, v)
        EconomyUnit.MPG_US -> inverse(MPG_US_FACTOR, v)
        EconomyUnit.MPG_UK -> inverse(MPG_UK_FACTOR, v)
    }

    /** Inverse of [fromPer100km]. */
    fun toPer100km(v: Double, unit: EconomyUnit): Double = fromPer100km(v, unit) // every conversion is self-inverse

    fun label(unit: EconomyUnit): String = when (unit) {
        EconomyUnit.L_PER_100KM -> "L/100km"
        EconomyUnit.KM_PER_L -> "km/L"
        EconomyUnit.MPG_US -> "mpg (US)"
        EconomyUnit.MPG_UK -> "mpg (UK)"
        EconomyUnit.KWH_PER_100KM -> "kWh/100km"
        EconomyUnit.KM_PER_KWH -> "km/kWh"
    }

    fun decimals(unit: EconomyUnit): Int = if (unit == EconomyUnit.KM_PER_KWH) 2 else 1

    /** Formats a per-100-km value in [unit], e.g. "7.5 L/100km", "13.3 km/L". Invalid values show "—". */
    fun format(v: Double, unit: EconomyUnit): String {
        val x = fromPer100km(v, unit)
        if (x.isNaN() || x.isInfinite()) return "— ${label(unit)}"
        return "${formatNumber(x, decimals(unit))} ${label(unit)}"
    }

    /** Locale-independent fixed-decimal formatting (no java.* in common code). */
    fun formatNumber(v: Double, decimals: Int): String {
        if (v.isNaN() || v.isInfinite()) return "—"
        var factor = 1L
        repeat(decimals) { factor *= 10 }
        val r = floor(abs(v) * factor + 0.5).toLong()
        val sign = if (v < 0 && r != 0L) "-" else ""
        val whole = r / factor
        if (decimals == 0) return "$sign$whole"
        val frac = (r % factor).toString().padStart(decimals, '0')
        return "$sign$whole.$frac"
    }

    private fun inverse(k: Double, v: Double): Double = if (v > 0 && !v.isInfinite()) k / v else Double.NaN
}
