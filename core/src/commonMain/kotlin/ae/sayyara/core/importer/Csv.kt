package ae.sayyara.core.importer

/**
 * Minimal RFC 4180 CSV reader/writer. Pure Kotlin.
 *
 * Reader is tolerant: quoted and unquoted fields may be mixed, CRLF / LF / CR line endings are
 * accepted, a stray quote inside an unquoted field is kept literally, and a leading UTF-8 BOM is
 * stripped. Blank lines come back as a single empty cell row (callers usually skip them).
 */
object Csv {
    const val BOM = '﻿'

    fun stripBom(text: String): String = text.trimStart(BOM)

    /** Guess `,` or `;` from the first non-blank lines (Excel in comma-decimal locales writes `;`). */
    fun sniffDelimiter(text: String): Char {
        val sample = stripBom(text).lineSequence().filter { it.isNotBlank() }.take(5).toList()
        val commas = sample.sumOf { line -> countOutsideQuotes(line, ',') }
        val semis = sample.sumOf { line -> countOutsideQuotes(line, ';') }
        return if (semis > commas) ';' else ','
    }

    private fun countOutsideQuotes(line: String, c: Char): Int {
        var inQ = false
        var n = 0
        for (ch in line) {
            if (ch == '"') inQ = !inQ else if (ch == c && !inQ) n++
        }
        return n
    }

    fun parse(text: String, delimiter: Char = ','): List<List<String>> {
        val s = stripBom(text)
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        var fieldStarted = false // true once any char (or quote) belongs to the current field
        var i = 0
        val n = s.length
        fun endField() {
            row.add(field.toString()); field.clear(); fieldStarted = false
        }
        fun endRow() {
            endField(); rows.add(row); row = ArrayList()
        }
        while (i < n) {
            val c = s[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < n && s[i + 1] == '"') { field.append('"'); i += 2; continue }
                    inQuotes = false; i++; continue
                }
                field.append(c); i++; continue
            }
            when {
                c == '"' && !fieldStarted -> { inQuotes = true; fieldStarted = true; i++ }
                c == delimiter -> { endField(); i++ }
                c == '\r' -> { endRow(); i++; if (i < n && s[i] == '\n') i++ }
                c == '\n' -> { endRow(); i++ }
                else -> { field.append(c); fieldStarted = true; i++ }
            }
        }
        if (fieldStarted || field.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }

    /** Quote a field only when needed (delimiter, quote, CR/LF, or leading/trailing space). */
    fun escape(value: String, delimiter: Char = ','): String {
        val needs = value.any { it == delimiter || it == '"' || it == '\n' || it == '\r' } ||
            (value.isNotEmpty() && (value.first() == ' ' || value.last() == ' '))
        return if (needs) "\"" + value.replace("\"", "\"\"") + "\"" else value
    }

    fun writeRow(cells: List<String>, delimiter: Char = ','): String =
        cells.joinToString(delimiter.toString()) { escape(it, delimiter) }
}
