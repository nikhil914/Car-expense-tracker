package ae.sayyara.core.importer

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlinx.datetime.LocalDateTime

/** Number, flag and date helpers shared by the importers. Pure Kotlin. */
object ImportText {

    /** `.` decimal; a single `,` with no `.` is treated as a decimal comma. Empty -> null. */
    fun number(raw: String?): Double? {
        val t = raw?.trim()?.trim('"')?.replace(" ", "") ?: return null
        if (t.isEmpty()) return null
        val s = if (!t.contains('.') && t.count { it == ',' } == 1) t.replace(',', '.') else t.replace(",", "")
        return s.toDoubleOrNull()?.takeIf { !it.isNaN() && !it.isInfinite() }
    }

    /** "1", "1.0", "true", "yes" -> true. */
    fun flag(raw: String?): Boolean {
        val t = raw?.trim()?.lowercase() ?: return false
        if (t == "true" || t == "yes" || t == "y") return true
        val n = number(t) ?: return false
        return n != 0.0
    }

    /** Fixed decimals without java.* formatting. */
    fun fixed(v: Double, decimals: Int): String {
        val f = 10.0.pow(decimals)
        val scaled = (abs(v) * f).roundToLong()
        val sign = if (v < 0 && scaled != 0L) "-" else ""
        if (decimals == 0) return sign + scaled.toString()
        val str = scaled.toString().padStart(decimals + 1, '0')
        return sign + str.dropLast(decimals) + "." + str.takeLast(decimals)
    }

    fun daysInMonth(year: Int, month: Int): Int = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
        else -> 0
    }

    /** Result of a date parse; [clamped] is true when e.g. 2026-09-31 became 2026-09-30. */
    data class ParsedDate(val dateTime: LocalDateTime, val clamped: Boolean)

    private data class Compiled(val regex: Regex, val fields: List<Char>)

    /**
     * Compile a Java/ICU style pattern (yyyy, yy, MM, M, dd, d, HH, H, mm, ss, literal chars,
     * 'quoted literals'). Returns null for unsupported tokens (e.g. MMM month names).
     */
    private fun compile(pattern: String): Compiled? = run {
        val sb = StringBuilder("^\\s*")
        val fields = ArrayList<Char>()
        var i = 0
        var ok = true
        while (i < pattern.length) {
            val c = pattern[i]
            var j = i
            while (j < pattern.length && pattern[j] == c) j++
            val len = j - i
            when (c) {
                'y' -> { sb.append(if (len == 2) "(\\d{2})" else "(\\d{4})"); fields.add('y') }
                'M' -> { if (len > 2) ok = false; sb.append("(\\d{1,2})"); fields.add('M') }
                'd' -> { sb.append("(\\d{1,2})"); fields.add('d') }
                'H', 'k' -> { sb.append("(\\d{1,2})"); fields.add('H') }
                'm' -> { sb.append("(\\d{1,2})"); fields.add('m') }
                's' -> { sb.append("(\\d{1,2})"); fields.add('s') }
                'S' -> sb.append("\\d+")
                '\'' -> {
                    val end = pattern.indexOf('\'', i + 1)
                    if (end < 0) { ok = false; j = pattern.length } else {
                        sb.append(Regex.escape(pattern.substring(i + 1, end))); j = end + 1
                    }
                }
                else -> if (c.isLetter()) ok = false else repeat(len) { sb.append(Regex.escape(c.toString())) }
            }
            i = j
        }
        sb.append("\\s*$")
        if (ok) Compiled(Regex(sb.toString()), fields) else null
    }

    fun parseWith(pattern: String, raw: String): ParsedDate? = compile(pattern)?.let { parseCompiled(it, raw) }

    private fun parseCompiled(c: Compiled, raw: String): ParsedDate? {
        val m = c.regex.find(raw) ?: return null
        var y = 0; var mo = 0; var d = 0; var h = 0; var mi = 0; var s = 0
        c.fields.forEachIndexed { idx, f ->
            val v = m.groupValues[idx + 1].toInt()
            when (f) {
                'y' -> y = if (m.groupValues[idx + 1].length == 2) 2000 + v else v
                'M' -> mo = v
                'd' -> d = v
                'H' -> h = v
                'm' -> mi = v
                's' -> s = v
            }
        }
        if (y == 0 || mo !in 1..12 || d < 1 || h > 24 || mi > 59 || s > 59) return null
        if (h == 24) h = 0
        val dim = daysInMonth(y, mo)
        val clamped = d > dim
        return ParsedDate(LocalDateTime(y, mo, if (clamped) dim else d, h, mi, s), clamped)
    }

    val FALLBACK_PATTERNS = listOf(
        "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm", "yyyy-MM-dd",
        "dd.MM.yyyy HH:mm:ss", "dd.MM.yyyy HH:mm", "dd.MM.yyyy",
        "dd/MM/yyyy HH:mm", "dd/MM/yyyy", "yyyy/MM/dd HH:mm", "yyyy/MM/dd",
    )

    /** Reusable reader: compiles the hint and fallback patterns once. */
    class DateReader(hint: String? = null) {
        private val compiled: List<Compiled> = buildList {
            if (!hint.isNullOrBlank()) {
                add(hint); add("$hint HH:mm"); add("$hint HH:mm:ss")
            }
            addAll(FALLBACK_PATTERNS)
        }.distinct().mapNotNull { compile(it) }

        fun read(raw: String?): ParsedDate? {
            val t = raw?.trim() ?: return null
            if (t.isEmpty()) return null
            for (c in compiled) parseCompiled(c, t)?.let { return it }
            return null
        }
    }

    /** Try the file's hint (alone and with a time suffix), then the fallback list. */
    fun parseDate(raw: String?, hint: String? = null): ParsedDate? = DateReader(hint).read(raw)
}
