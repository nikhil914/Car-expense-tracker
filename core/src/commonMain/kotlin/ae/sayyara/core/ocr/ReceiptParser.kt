package ae.sayyara.core.ocr

import ae.sayyara.core.model.FuelGrade
import kotlin.math.abs
import kotlin.math.round

data class ReceiptFields(
    val litres: Double?,
    val unitPriceAed: Double?,
    val totalAed: Double?,
    val grade: FuelGrade?,
    val stationBrand: String?,
    val timestampText: String?,
    val vatAed: Double?,
    val confidence: Double,
)

/** Shared OCR text normalization. */
object OcrText {
    /** Arabic-Indic (U+0660..) and Eastern Arabic-Indic (U+06F0..) digits -> ASCII; ٫ -> '.'; ٬ removed. */
    fun normalizeDigits(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            when (c) {
                in '٠'..'٩' -> sb.append('0' + (c - '٠'))
                in '۰'..'۹' -> sb.append('0' + (c - '۰'))
                '٫' -> sb.append('.') // Arabic decimal separator
                '٬' -> {} // Arabic thousands separator
                '،' -> sb.append(',') // Arabic comma
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    /** Lowercase, Arabic letter variants folded (أ إ آ -> ا, ة -> ه, ى -> ي), tatweel removed. */
    fun fold(s: String): String = normalizeDigits(s).lowercase()
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        .replace('ة', 'ه').replace('ى', 'ي').replace("ـ", "")
}

/**
 * Heuristic parser for UAE fuel receipts (ADNOC / ENOC / EPPCO / Emarat), English and Arabic.
 *
 * Strategy: label-driven extraction per line (value on the same line or the next one), then a
 * cross-check litres x unit price ~= total. Missing fields are recovered by searching all decimal
 * numbers for a consistent triple. [ReceiptFields.confidence] is 0..1.
 */
object ReceiptParser {

    private val NUM = Regex("(?<![\\d.])(\\d{1,3}(?:,\\d{3})+|\\d+)(?:[.,](\\d{1,3}))?(?![\\d]|\\s*%)")

    private data class Num(val value: Double, val decimals: Int)

    private fun numbers(line: String): List<Num> = NUM.findAll(line).mapNotNull { m ->
        // Skip parts of dates/times like 12/05/2026 or 14:32.
        val before = line.getOrNull(m.range.first - 1)
        val after = line.getOrNull(m.range.last + 1)
        if (before == '/' || before == ':' || after == '/' || after == ':' || (after == '-' || before == '-') && m.groupValues[2].isEmpty()) return@mapNotNull null
        val intPart = m.groupValues[1].replace(",", "")
        val dec = m.groupValues[2]
        val v = (if (dec.isEmpty()) intPart else "$intPart.$dec").toDoubleOrNull() ?: return@mapNotNull null
        Num(v, dec.length)
    }.toList()

    private val LITRE_LABELS = listOf("litre", "liter", "ltr", "lts", "volume", "qty", "quantity", "لتر", "الكميه", "كميه")
    private val PRICE_LABELS = listOf("unit price", "price/l", "price / l", "price per", "per litre", "per liter", "rate",
        "aed/l", "aed / l", "dhs/l", "/ltr", "/l ", "سعر اللتر", "سعر الوحده", "السعر")
    private val TOTAL_LABELS = listOf("total", "amount", "net", "paid", "sale", "grand", "card", "cash",
        "الاجمالي", "المجموع", "المبلغ", "الصافي", "الإجمالي")
    private val VAT_LABELS = listOf("vat", "tax", "ضريبه", "القيمه المضافه")
    private val VAT_EXCLUDE = listOf("trn", "reg", "no.", "number", "رقم", "%")

    private fun has(line: String, labels: List<String>) = labels.any { line.contains(it) }

    private val LITRE_INLINE = Regex("(\\d+(?:[.,]\\d{1,3})?)\\s*(?:l|ltr|ltrs|lts|litres?|liters?|لتر)(?![a-z])")
    private val PRICE_INLINE = Regex("(\\d+[.,]\\d{2,3})\\s*(?:aed|dhs?|درهم)?\\s*/\\s*(?:l|ltr|litre|liter|لتر)(?![a-z])")

    fun parse(ocrText: String): ReceiptFields {
        val lines = ocrText.lines().map { OcrText.fold(it).trim() }.filter { it.isNotEmpty() }
        val all = lines.joinToString("\n")

        fun valueAt(i: Int, pick: (List<Num>) -> Num?): Double? {
            pick(numbers(lines[i]))?.let { return it.value }
            val next = lines.getOrNull(i + 1) ?: return null
            // Only borrow from the next line when it is mostly a number (label/value split layout).
            if (next.count { it.isLetter() } > 6) return null
            return pick(numbers(next))?.value
        }

        var vat: Double? = null
        var litres: Double? = null
        var price: Double? = null
        val totals = ArrayList<Double>()

        for ((i, l) in lines.withIndex()) {
            val incl = l.contains("incl") || l.contains("شامل") || l.contains("inc.")
            val excl = l.contains("excl") || l.contains("before") || l.contains("غير شامل") || l.contains("قبل")
            if (has(l, VAT_LABELS) && !incl && !excl) {
                if (vat == null && !(has(l, VAT_EXCLUDE) && numbers(l).none { it.decimals == 2 })) {
                    vat = valueAt(i) { ns -> ns.lastOrNull { it.decimals == 2 } }
                }
                continue
            }
            if (price == null) {
                PRICE_INLINE.find(l)?.let { price = parseNum(it.groupValues[1]) }
                if (price == null && has(l, PRICE_LABELS)) price = valueAt(i) { ns -> ns.firstOrNull { it.value in 1.0..10.0 && it.decimals >= 2 } }
            }
            if (litres == null) {
                val inline = LITRE_INLINE.findAll(l).map { parseNum(it.groupValues[1]) }
                    .firstOrNull { it != null && it in 0.5..300.0 }
                if (inline != null) litres = inline
                else if (has(l, LITRE_LABELS) && !has(l, PRICE_LABELS)) {
                    litres = valueAt(i) { ns -> ns.firstOrNull { it.value in 0.5..300.0 && it.decimals >= 1 } }
                }
            }
            if (has(l, TOTAL_LABELS) && !l.contains("sub") && !excl) {
                valueAt(i) { ns -> ns.lastOrNull { it.decimals == 2 && it.value > 0 } }?.let { totals.add(it) }
            }
        }
        var total: Double? = totals.maxOrNull()

        val grade = detectGrade(all)
        val brand = detectBrand(all)
        val date = DATE.find(all)?.value?.trim()

        // Cross-check and recovery.
        var foundDirect = listOf(litres, price, total).count { it != null }
        var consistency = 0.0 // -1 inconsistent, 0 unknown, 0.5..1 consistent
        fun check(l: Double, p: Double, t: Double): Double {
            val err = abs(l * p - t) / t
            return when { err <= 0.01 -> 1.0; err <= 0.03 -> 0.75; err <= 0.06 -> 0.5; else -> -1.0 }
        }
        if (litres != null && price != null && total != null) {
            consistency = check(litres!!, price!!, total!!)
        }
        if (consistency <= 0.0) {
            // Search every decimal number for a consistent triple, preferring labelled values.
            val pool = lines.flatMap { numbers(it) }.filter { it.decimals >= 2 }.map { it.value }.distinct().take(40)
            var best: Triple<Double, Double, Double>? = null
            var bestScore = 0.0
            for (p in pool.filter { it in 1.0..10.0 }) for (l in pool.filter { it in 0.5..300.0 && it != p }) for (t in pool) {
                if (t == l || t == p || t < 1.0) continue
                val c = check(l, p, t)
                if (c <= 0) continue
                val bonus = (if (l == litres) 0.3 else 0.0) + (if (p == price) 0.3 else 0.0) + (if (t == total) 0.3 else 0.0)
                if (c + bonus > bestScore) { bestScore = c + bonus; best = Triple(l, p, t) }
            }
            best?.let { (l, p, t) ->
                if (litres != null && litres != l || price != null && price != p || total != null && total != t) foundDirect--
                litres = l; price = p; total = t; consistency = check(l, p, t)
            }
        }
        var derived = 0
        if (consistency == 0.0) {
            if (total == null && litres != null && price != null) { total = r2(litres!! * price!!); derived++ }
            else if (litres == null && total != null && price != null && price!! > 0) { litres = r2(total!! / price!!); derived++ }
            else if (price == null && total != null && litres != null && litres!! > 0) { price = r2(total!! / litres!!); derived++ }
        }

        var conf = 0.15 * foundDirect.coerceAtLeast(0) + 0.05 * derived
        if (grade != null) conf += 0.1
        if (brand != null) conf += 0.05
        if (date != null) conf += 0.05
        conf += when {
            consistency > 0 -> 0.35 * consistency
            consistency < 0 -> -0.25
            else -> 0.0
        }
        val p = price
        if (p != null && p !in 1.5..6.0) conf -= 0.1 // outside any plausible UAE AED/L
        if (vat != null && total != null && vat!! > total!! * 0.2) vat = null
        return ReceiptFields(litres, price, total, grade, brand, date, vat, conf.coerceIn(0.0, 1.0))
    }

    private fun parseNum(s: String): Double? = s.replace(',', '.').toDoubleOrNull()
    private fun r2(x: Double) = round(x * 100) / 100

    private val DATE = Regex(
        "\\b(\\d{1,2}[/.\\-]\\d{1,2}[/.\\-]\\d{2,4}|\\d{4}[/.\\-]\\d{1,2}[/.\\-]\\d{1,2}|\\d{1,2}[ \\-](?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*[ \\-]\\d{2,4})" +
            "(?:[ ,t]+\\d{1,2}:\\d{2}(?::\\d{2})?(?:\\s*[ap]m)?)?",
    )

    fun detectGrade(folded: String): FuelGrade? {
        val t = OcrText.fold(folded)
        val rules = listOf(
            Regex("super\\s*-?\\s*98|سوبر\\s*98|سوبر|\\bsuper\\b|\\bm?98\\s*(?:ron|octane)|(?:unleaded|petrol|gasoline|ron|octane|بنزين)\\s*98") to FuelGrade.SUPER_98,
            Regex("special\\s*-?\\s*95|خصوصي\\s*95|خصوصي|\\bspecial\\b|\\b95\\s*(?:ron|octane)|(?:unleaded|petrol|gasoline|ron|octane|بنزين)\\s*95") to FuelGrade.SPECIAL_95,
            Regex("e\\s*-?\\s*plus\\s*-?\\s*91|اي\\s*بلس\\s*91|اي\\s*بلس|\\be\\s*-?\\s*plus\\b|eplus|\\b91\\s*(?:ron|octane)|(?:unleaded|petrol|gasoline|ron|octane|بنزين)\\s*91") to FuelGrade.E_PLUS_91,
            Regex("diesel|ديزل") to FuelGrade.DIESEL,
        )
        var best: Pair<Int, FuelGrade>? = null
        for ((re, g) in rules) {
            val m = re.find(t) ?: continue
            if (best == null || m.range.first < best.first) best = m.range.first to g
        }
        return best?.second
    }

    fun detectBrand(folded: String): String? {
        val t = OcrText.fold(folded)
        return when {
            t.contains("adnoc") || t.contains("ادنوك") -> "ADNOC"
            t.contains("eppco") || t.contains("ايبكو") -> "EPPCO"
            t.contains("enoc") || t.contains("اينوك") || t.contains("اينوك") -> "ENOC"
            t.contains("emarat") || t.contains("امارات للبترول") || t.contains("بترول الامارات") -> "Emarat"
            else -> null
        }
    }
}
