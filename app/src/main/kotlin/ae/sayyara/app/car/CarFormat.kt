package ae.sayyara.app.car

import ae.sayyara.app.R
import ae.sayyara.core.model.FuelGrade
import ae.sayyara.core.model.YearMonth
import android.content.Context
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Small formatting helpers shared by the car screens. All prices are UAE-wide, so dates use Asia/Dubai. */
internal object CarFormat {
    val UAE_ZONE: TimeZone = TimeZone.of("Asia/Dubai")

    fun monthOf(at: Instant): YearMonth = YearMonth.of(at.toLocalDateTime(UAE_ZONE).date)

    fun currentMonth(): YearMonth = monthOf(Clock.System.now())

    fun locale(context: Context): Locale = context.resources.configuration.locales[0] ?: Locale.getDefault()

    /** "Nov" / "نوفمبر" for the month of [ym]. */
    fun shortMonth(context: Context, ym: YearMonth): String =
        Month.of(ym.month).getDisplayName(TextStyle.SHORT, locale(context))

    fun gradeLabel(context: Context, grade: FuelGrade): String = context.getString(
        when (grade) {
            FuelGrade.SUPER_98 -> R.string.car_grade_super_98
            FuelGrade.SPECIAL_95 -> R.string.car_grade_special_95
            FuelGrade.E_PLUS_91 -> R.string.car_grade_e_plus_91
            FuelGrade.DIESEL -> R.string.car_grade_diesel
            FuelGrade.ELECTRIC -> R.string.car_grade_electric
            FuelGrade.LPG, FuelGrade.CNG, FuelGrade.OTHER -> R.string.car_grade_other
        },
    )

    /** Litres rounded to whole litres for the "~32 L" summary (the gauge estimate is not more precise). */
    fun litresApprox(litres: Double): String = litres.roundToInt().toString()

    /** Whole dirhams for summaries ("AED 129"). */
    fun aedWhole(aed: Double): String = aed.roundToInt().toString()

    /** Two decimals for per-litre prices ("2.58"). */
    fun aedPerLitre(context: Context, aed: Double): String = String.format(locale(context), "%.2f", aed)

    fun km(context: Context, km: Double): String = String.format(locale(context), "%,.0f", km)

    fun percent(value: Double): String = value.roundToInt().toString()

    fun round1(v: Double): Double = (v * 10.0).roundToInt() / 10.0
    fun round2(v: Double): Double = (v * 100.0).roundToInt() / 100.0
}
