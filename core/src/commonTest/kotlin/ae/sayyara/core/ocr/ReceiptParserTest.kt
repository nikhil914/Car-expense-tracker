package ae.sayyara.core.ocr

import ae.sayyara.core.model.FuelGrade
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReceiptParserTest {
    private fun near(expected: Double, actual: Double?, eps: Double = 0.011) =
        assertTrue(actual != null && abs(expected - actual) <= eps, "expected $expected but was $actual")

    @Test fun adnocEnglish() {
        val text = """
            ADNOC Distribution
            Station 512 Al Khawaneej
            TRN 100023456700003
            Date: 05/11/2026 08:42
            Pump 04   Nozzle 2
            Product: Super 98
            Volume        40.00 L
            Price/L       AED 2.58
            Amount excl. VAT  98.29
            VAT 5%         4.91
            Total incl. VAT AED 103.20
            Paid by CARD
        """.trimIndent()
        val r = ReceiptParser.parse(text)
        near(40.0, r.litres)
        near(2.58, r.unitPriceAed)
        near(103.20, r.totalAed)
        near(4.91, r.vatAed)
        assertEquals(FuelGrade.SUPER_98, r.grade)
        assertEquals("ADNOC", r.stationBrand)
        assertEquals("05/11/2026 08:42", r.timestampText)
        assertTrue(r.confidence >= 0.9, "confidence ${r.confidence}")
    }

    @Test fun enocArabicWithArabicDigits() {
        val text = """
            اينوك
            التاريخ ٢٠٢٦/١١/٠٣ ١٩:١٥
            المنتج: خصوصي 95
            الكمية
            ٣٥٫٥٠ لتر
            سعر اللتر ٢٫٤٧
            ضريبة القيمة المضافة ٤٫١٨
            الإجمالي ٨٧٫٦٩ درهم
        """.trimIndent()
        val r = ReceiptParser.parse(text)
        near(35.50, r.litres)
        near(2.47, r.unitPriceAed)
        near(87.69, r.totalAed)
        near(4.18, r.vatAed)
        assertEquals(FuelGrade.SPECIAL_95, r.grade)
        assertEquals("ENOC", r.stationBrand)
        assertEquals("2026/11/03 19:15", r.timestampText)
        assertTrue(r.confidence >= 0.85, "confidence ${r.confidence}")
    }

    @Test fun arabicGradesAndBrands() {
        assertEquals(FuelGrade.SUPER_98, ReceiptParser.detectGrade("بنزين سوبر 98"))
        assertEquals(FuelGrade.E_PLUS_91, ReceiptParser.detectGrade("إي بلس 91"))
        assertEquals(FuelGrade.E_PLUS_91, ReceiptParser.detectGrade("E-Plus 91"))
        assertEquals(FuelGrade.DIESEL, ReceiptParser.detectGrade("ديزل"))
        assertEquals(FuelGrade.DIESEL, ReceiptParser.detectGrade("DIESEL"))
        assertEquals(FuelGrade.SPECIAL_95, ReceiptParser.detectGrade("SPECIAL 95"))
        assertEquals("ADNOC", ReceiptParser.detectBrand("أدنوك للتوزيع"))
        assertEquals("EPPCO", ReceiptParser.detectBrand("EPPCO Site 22"))
        assertEquals("Emarat", ReceiptParser.detectBrand("EMARAT Station"))
        assertNull(ReceiptParser.detectBrand("Some shop"))
    }

    @Test fun unlabelledNumbersRecoveredByCrossCheck() {
        val text = """
            EMARAT
            DIESEL
            52.10
            2.65
            138.07
        """.trimIndent()
        val r = ReceiptParser.parse(text)
        near(52.10, r.litres)
        near(2.65, r.unitPriceAed)
        near(138.07, r.totalAed)
        assertEquals(FuelGrade.DIESEL, r.grade)
        assertTrue(r.confidence >= 0.5)
    }

    @Test fun inconsistentReceiptHasLowConfidence() {
        val good = ReceiptParser.parse("Volume 40.00 L\nPrice/L 2.58\nTotal 103.20")
        val bad = ReceiptParser.parse("Volume 40.00 L\nPrice/L 2.58\nTotal 183.20")
        assertTrue(bad.confidence < good.confidence, "bad ${bad.confidence} good ${good.confidence}")
        assertTrue(bad.confidence < 0.5)
    }

    @Test fun missingTotalIsDerived() {
        val r = ReceiptParser.parse("E-Plus 91\nLitres: 20.00\nRate 2.39")
        near(47.80, r.totalAed)
        assertEquals(FuelGrade.E_PLUS_91, r.grade)
    }

    @Test fun emptyText() {
        val r = ReceiptParser.parse("")
        assertNull(r.litres); assertNull(r.totalAed); assertEquals(0.0, r.confidence)
    }

    @Test fun digitNormalization() {
        assertEquals("123.45", OcrText.normalizeDigits("١٢٣٫٤٥"))
        assertEquals("1234", OcrText.normalizeDigits("۱۲۳۴"))
    }
}
