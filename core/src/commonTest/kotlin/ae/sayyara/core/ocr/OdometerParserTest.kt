package ae.sayyara.core.ocr

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OdometerParserTest {

    @Test fun picksValueAtOrAboveLast() {
        val text = "TRIP A 345.6\n12:45  32°C\nODO 123856 km\n80 km/h"
        assertEquals(123856.0, OdometerParser.parse(text, 123400.0))
    }

    @Test fun ignoresValuesBelowLast() {
        // 98765 is below the last reading, 99012 is plausible.
        assertEquals(99012.0, OdometerParser.parse("98765\n99012", 98900.0))
    }

    @Test fun nullWhenNothingPlausible() {
        assertNull(OdometerParser.parse("12345", 50000.0))
        assertNull(OdometerParser.parse("no numbers here", null))
    }

    @Test fun groupedDigitsAndOcrConfusions() {
        assertEquals(123456.0, OdometerParser.parse("123 456 km", null))
        assertEquals(104512.0, OdometerParser.parse("1O4512", 104000.0))
    }

    @Test fun arabicDigits() {
        assertEquals(45678.0, OdometerParser.parse("العداد ٤٥٦٧٨ كم", 45000.0))
    }

    @Test fun withoutLastPrefersLabelledThenLongest() {
        assertEquals(87654.0, OdometerParser.parse("RANGE 420\nTRIP 1234.5\n87654 km", null))
        assertEquals(234567.0, OdometerParser.parse("2345\n234567", null))
    }

    @Test fun dropsTenths() {
        assertEquals(56789.0, OdometerParser.parse("56789.4", 56000.0))
    }
}
