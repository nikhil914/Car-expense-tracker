package ae.sayyara.app.ui.format

/**
 * Parses user-typed decimals. Accepts Eastern Arabic digits (٠-٩), Persian digits (۰-۹),
 * the Arabic decimal separator (٫) and a comma as decimal separator.
 */
fun parseDecimal(text: String): Double? {
    if (text.isBlank()) return null
    val sb = StringBuilder(text.length)
    for (c in text.trim()) {
        when (c) {
            in '٠'..'٩' -> sb.append('0' + (c - '٠'))
            in '۰'..'۹' -> sb.append('0' + (c - '۰'))
            '٫', ',' -> sb.append('.')
            '٬', ' ', ' ' -> Unit // thousands separators
            else -> sb.append(c)
        }
    }
    return sb.toString().toDoubleOrNull()?.takeIf { it.isFinite() }
}

fun parseInt(text: String): Int? = parseDecimal(text)?.takeIf { it == Math.floor(it) }?.toInt()

/** Plain ASCII representation for pre-filling text fields (always parseable by [parseDecimal]). */
fun Double.toInputText(decimals: Int = 2): String {
    if (this == Math.floor(this) && decimals >= 0 && kotlin.math.abs(this) < 1e12) return toLong().toString()
    return "%.${decimals}f".format(java.util.Locale.ROOT, this).trimEnd('0').trimEnd('.')
}
