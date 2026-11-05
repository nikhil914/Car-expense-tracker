package ae.sayyara.app.ui.format

import ae.sayyara.core.model.YearMonth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.os.ConfigurationCompat
import java.text.NumberFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/** Locale-aware formatting: AED via NumberFormat for the current locale, dates via java.time. */
class UiFormatter(val locale: Locale) {
    private val zone: ZoneId = ZoneId.systemDefault()

    private val currency: NumberFormat = NumberFormat.getCurrencyInstance(locale).apply {
        currency = Currency.getInstance("AED")
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    private val numbers = HashMap<Int, NumberFormat>()

    private val dateFmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    private val dateTimeFmt =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale)
    private val timeFmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
    private val monthFmt = DateTimeFormatter.ofPattern("LLL yy", locale)
    private val monthLongFmt = DateTimeFormatter.ofPattern("LLLL yyyy", locale)

    /** e.g. "AED 123.45" / "‏123.45 د.إ.‏" */
    fun aed(value: Double): String = synchronized(currency) { currency.format(value) }

    fun number(value: Double, decimals: Int = 1): String {
        val f = synchronized(numbers) {
            numbers.getOrPut(decimals) {
                NumberFormat.getNumberInstance(locale).apply {
                    minimumFractionDigits = decimals
                    maximumFractionDigits = decimals
                }
            }
        }
        return synchronized(f) { f.format(value) }
    }

    fun integer(value: Double): String = number(value, 0)

    fun integer(value: Int): String = number(value.toDouble(), 0)

    fun date(instant: Instant): String = dateFmt.format(instant.toZoned())

    fun dateTime(instant: Instant): String = dateTimeFmt.format(instant.toZoned())

    fun time(hour: Int, minute: Int): String = timeFmt.format(java.time.LocalTime.of(hour, minute))

    fun date(date: LocalDate): String = dateFmt.format(date.toJava())

    fun monthShort(ym: YearMonth): String = monthFmt.format(java.time.YearMonth.of(ym.year, ym.month))

    fun monthLong(ym: YearMonth): String = monthLongFmt.format(java.time.YearMonth.of(ym.year, ym.month))

    private fun Instant.toZoned() =
        java.time.Instant.ofEpochMilli(toEpochMilliseconds()).atZone(zone)

    private fun LocalDate.toJava(): java.time.LocalDate = java.time.LocalDate.ofEpochDay(toEpochDays().toLong())
}

@Composable
fun rememberUiFormatter(): UiFormatter {
    val configuration = LocalConfiguration.current
    val locale = ConfigurationCompat.getLocales(configuration)[0] ?: Locale.getDefault()
    return remember(locale) { UiFormatter(locale) }
}
