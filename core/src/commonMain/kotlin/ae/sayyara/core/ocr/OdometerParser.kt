package ae.sayyara.core.ocr

/**
 * Picks the odometer reading from OCR text of a dashboard photo.
 *
 * Candidates are integer runs of 3..7 digits (digit groups like "123 456" / "123,456" are joined,
 * common OCR letter confusions O/I/l/S/B/Z inside digit runs are repaired). Numbers attached to
 * trip / speed / time / temperature / range markers are dropped.
 *
 * With [lastOdometerKm]: the closest candidate in [last, last + 20 000] km wins, else null.
 * Without it: the longest candidate (ties: the larger), preferring ones labelled ODO / km.
 */
object OdometerParser {
    const val MAX_JUMP_KM = 20_000.0

    private val TOKEN = Regex("[0-9OoIlSBZ|]{1,7}(?:[ ,.'][0-9]{3}(?![0-9]))*(?:[.,][0-9])?")
    private val EXCLUDE_AFTER = Regex("^\\s*(?:km/h|kmh|mph|rpm|%|°|:|l/100|km/l|mpg|kph)", RegexOption.IGNORE_CASE)
    private val EXCLUDE_BEFORE = Regex("(?:trip\\s*[ab]?|range|rng|dte|avg|:)\\s*$", RegexOption.IGNORE_CASE)
    private val ODO_LABEL = Regex("(?:odo|total|odometer|عداد|المسافه)\\s*[:=]?\\s*$", RegexOption.IGNORE_CASE)

    private data class Cand(val value: Double, val digits: Int, val labelled: Boolean)

    private fun repair(token: String): String? {
        val digits = token.count { it.isDigit() }
        val letters = token.count { it in "OoIlSBZ|" }
        if (letters > 0 && (digits < 3 || letters > 2)) return null
        return token.map {
            when (it) {
                'O', 'o' -> '0'
                'I', 'l', '|' -> '1'
                'S' -> '5'
                'B' -> '8'
                'Z' -> '2'
                else -> it
            }
        }.joinToString("")
    }

    fun parse(ocrText: String, lastOdometerKm: Double?): Double? {
        val text = OcrText.normalizeDigits(ocrText)
        val cands = ArrayList<Cand>()
        for (line in text.lines()) {
            for (m in TOKEN.findAll(line)) {
                val before = line.substring(0, m.range.first)
                val after = line.substring(m.range.last + 1)
                if (before.isNotEmpty() && before.last().isLetter() && before.last() !in "OoIlSBZ") continue
                if (after.isNotEmpty() && after.first().isLetter() && !after.startsWith("km", true) && !after.startsWith("mi", true)) continue
                if (EXCLUDE_AFTER.containsMatchIn(after) || EXCLUDE_BEFORE.containsMatchIn(before)) continue
                var raw = m.value
                // Drop a trailing tenth digit (e.g. "123456.7") - odometers are whole km.
                Regex("[.,][0-9]$").find(raw)?.let { if (it.range.first > raw.length - 3) raw = raw.substring(0, it.range.first) }
                val fixed = repair(raw.filter { it != ' ' && it != ',' && it != '.' && it != '\'' }) ?: continue
                if (fixed.length !in 3..7 || !fixed.all { it.isDigit() }) continue
                val v = fixed.toDoubleOrNull() ?: continue
                val labelled = ODO_LABEL.containsMatchIn(before) || after.trimStart().startsWith("km", true)
                cands.add(Cand(v, fixed.length, labelled))
            }
        }
        if (cands.isEmpty()) return null
        if (lastOdometerKm != null) {
            return cands.filter { it.value >= lastOdometerKm && it.value <= lastOdometerKm + MAX_JUMP_KM }
                .minWithOrNull(compareBy<Cand>({ if (it.labelled) 0 else 1 }, { it.value - lastOdometerKm }))?.value
        }
        val pool = cands.filter { it.labelled }.ifEmpty { cands }
        return pool.maxWithOrNull(compareBy<Cand>({ it.digits }, { it.value }))?.value
    }
}
