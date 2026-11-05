package ae.sayyara.core.importer

import ae.sayyara.core.model.EnergyStoreKind
import ae.sayyara.core.model.EnergyUnit
import ae.sayyara.core.model.ExpenseCategoryKey
import ae.sayyara.core.model.ExpenseSource
import ae.sayyara.core.model.FuelGrade
import ae.sayyara.core.model.LocationType
import ae.sayyara.core.model.PriceSource
import ae.sayyara.core.model.ReminderKind
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

class FuelioImporterTest {
    private val now = Instant.parse("2026-11-05T10:00:00Z")
    private val dubai = TimeZone.of("Asia/Dubai")

    private fun run(csv: String, file: String? = "vehicle-1-sync.csv"): FuelioImport {
        var n = 0
        return FuelioImporter.parse(csv, file, "batch-1", now) { "id-${++n}" }
    }

    private fun near(expected: Double, actual: Double?, eps: Double = 0.01) {
        assertTrue(actual != null && abs(expected - actual) <= eps, "expected $expected but was $actual")
    }

    @Test fun milesSample() {
        val r = run(FuelioFixtures.SAMPLE_MILES)
        assertEquals("BMW Z3", r.vehicle.name)
        assertEquals("AB12CDE", r.vehicle.plate)
        assertEquals(1997, r.vehicle.year)
        assertEquals(1, r.stores.size)
        assertEquals(EnergyStoreKind.TANK, r.stores[0].kind)
        near(51.0, r.stores[0].capacity)
        val e = r.events.single()
        near(212477.0 * 1.609344, e.odometerKm, 0.1)
        near(46.301, e.amount, 1e-9) // FuelUnit=0 litres even though distance is miles
        near(67.09, e.totalAed)
        near(1.449, e.unitPriceAed, 1e-9)
        assertEquals(FuelGrade.SPECIAL_95, e.grade) // FuelType -1 -> tank default (gasoline 100)
        assertEquals("Somerset - Sun all Service Station", e.stationName)
        assertTrue(e.isFull)
        assertFalse(e.missedPrevious)
        assertEquals("batch-1", e.importBatchId)
        assertEquals(PriceSource.IMPORT, e.priceSource)
        assertEquals(LocalDateTime(2024, 3, 10, 16, 1), e.timestamp.toLocalDateTime(dubai))
        // MOT costs 0.0 with RemindDate -> reminder only
        assertTrue(r.expenses.isEmpty())
        val rem = r.reminders.single()
        assertEquals(ReminderKind.INSPECTION, rem.kind)
        assertEquals(LocalDate(2024, 7, 19), rem.dueDate)
        assertTrue(r.warnings.any { it.contains("AED") })
        assertTrue(r.warnings.any { it.contains("miles") })
    }

    @Test fun kmSampleSortedOldestFirst() {
        val r = run(FuelioFixtures.SAMPLE_KM)
        assertEquals(2, r.events.size)
        assertEquals(listOf(5.0, 424.0), r.events.map { it.odometerKm })
        assertTrue(r.events[0].timestamp < r.events[1].timestamp)
        assertEquals("Hlavní město Praha, Československého exilu - OMV", r.events[0].stationName)
        near(1675.9, r.events[0].totalAed)
        near(34.9, r.events[0].unitPriceAed)
        assertEquals(FuelGrade.SPECIAL_95, r.events[0].grade)
        near(50.0436, r.events[1].latitude, 1e-9)
        assertEquals(r.stores[0].id, r.events[0].storeId)
        assertEquals(r.vehicle.id, r.events[0].vehicleId)
        assertFalse(r.warnings.any { it.contains("miles") })
    }

    @Test fun oldLayoutPaddedMarkersDottedDatesAndFlags() {
        val r = run(FuelioFixtures.OLD_LAYOUT)
        assertEquals("Patrol", r.vehicle.name)
        assertEquals(FuelGrade.SUPER_98, r.vehicle.defaultGrade)
        assertEquals(2, r.events.size)
        val (a, b) = r.events
        assertEquals(LocalDate(2026, 2, 15), a.timestamp.toLocalDateTime(dubai).date)
        assertEquals(10500.0, a.odometerKm)
        assertTrue(a.isFull) // "1.0"
        assertEquals(FuelGrade.SUPER_98, a.grade)
        near(181.30, a.totalAed) // derived 70 x 2.59
        assertEquals("ADNOC Al Barsha", a.stationName)
        assertNull(a.latitude) // 0,0 is "no geo"
        assertFalse(b.isFull)
        assertTrue(b.missedPrevious) // "1.0"
        assertEquals(FuelGrade.SPECIAL_95, b.grade)
        assertEquals("partial, rush", b.note)
        assertTrue(r.warnings.any { it.contains("derived") })
    }

    @Test fun gallonsAndMilesWithBomAndCrlf() {
        val r = run(FuelioFixtures.GALLONS_US)
        assertEquals("F-150", r.vehicle.name)
        assertEquals(FuelGrade.DIESEL, r.vehicle.defaultGrade)
        near(26.0 * 3.785411784, r.stores[0].capacity)
        val (a, b) = r.events
        near(1609.344, a.odometerKm, 1e-6)
        near(37.854, a.amount, 0.001)
        near(35.0, a.totalAed)
        near(35.0 / 37.854, a.unitPriceAed, 0.001) // per litre
        near(12 * 3.785411784, b.amount, 0.001)
        near(3.60 / 3.785411784, b.unitPriceAed, 0.001) // VolumePrice per gallon -> per litre
        near(43.20, b.totalAed)
        assertTrue(r.warnings.any { it.contains("gallons") })
    }

    @Test fun dualTankAndCosts() {
        val r = run(FuelioFixtures.DUAL_TANK)
        assertEquals(2, r.stores.size)
        val (petrol, lpg) = r.stores
        assertEquals(FuelGrade.SPECIAL_95, petrol.defaultGrade)
        assertEquals(FuelGrade.LPG, lpg.defaultGrade)
        near(40.0, lpg.capacity)
        assertEquals(listOf(petrol.id, lpg.id), r.events.map { it.storeId }) // sorted by date
        val lpgFill = r.events[1]
        assertEquals(FuelGrade.LPG, lpgFill.grade)
        near(80.0 / 30, lpgFill.unitPriceAed, 0.001)

        // Template skipped, income kept, clamped date
        assertEquals(5, r.expenses.size)
        val byTitle = r.expenses.associateBy { it.title }
        val salik = byTitle.getValue("Salik top-up")
        assertEquals(ExpenseCategoryKey.SALIK, salik.category)
        assertEquals("Tolls", salik.customCategory)
        assertEquals(LocalDate(2026, 9, 30), salik.timestamp.toLocalDateTime(dubai).date)
        assertEquals(2100.0, salik.odometerKm)
        assertEquals(ExpenseCategoryKey.PARKING, byTitle.getValue("Mall parking").category)
        assertEquals(ExpenseCategoryKey.INSURANCE, byTitle.getValue("AXA").category)
        val careem = byTitle.getValue("Careem trip")
        assertTrue(careem.isIncome)
        assertEquals(ExpenseCategoryKey.OTHER, careem.category)
        assertEquals("Ride share", careem.customCategory)
        assertEquals(ExpenseCategoryKey.SERVICE, byTitle.getValue("Oil change").category)
        assertTrue(r.expenses.all { it.importBatchId == "batch-1" && it.source == ExpenseSource.IMPORT })
        assertFalse(byTitle.containsKey("Template"))

        assertEquals(2, r.reminders.size)
        val ins = r.reminders.first { it.kind == ReminderKind.INSURANCE }
        assertEquals(LocalDate(2027, 2, 1), ins.dueDate)
        assertEquals(12, ins.repeatMonths)
        val oil = r.reminders.first { it.kind == ReminderKind.OIL }
        assertEquals(12500.0, oil.dueOdometerKm)
        assertEquals(10000.0, oil.repeatKm)
        assertEquals(6, oil.repeatMonths)
        assertTrue(r.warnings.any { it.contains("clamped") })
        assertTrue(r.warnings.any { it.contains("template") })
    }

    @Test fun electricGivesBattery() {
        val r = run(FuelioFixtures.ELECTRIC)
        val s = r.stores.single()
        assertEquals(EnergyStoreKind.BATTERY, s.kind)
        assertEquals(EnergyUnit.KWH, s.unit)
        assertEquals(75.0, s.capacity)
        assertEquals(FuelGrade.ELECTRIC, r.vehicle.defaultGrade)
        val (ac, dc) = r.events
        assertEquals(40.5, ac.amount)
        assertEquals(LocationType.PUBLIC_AC, ac.locationType)
        assertEquals(LocationType.PUBLIC_DC, dc.locationType)
        near(11.75, ac.totalAed)
        near(1.2, dc.unitPriceAed)
        assertFalse(dc.isFull)
    }

    @Test fun gradeMapping() {
        assertEquals(FuelGrade.SPECIAL_95, FuelioImporter.gradeFor(100))
        assertEquals(FuelGrade.SPECIAL_95, FuelioImporter.gradeFor(110))
        assertEquals(FuelGrade.SPECIAL_95, FuelioImporter.gradeFor(111))
        assertEquals(FuelGrade.SUPER_98, FuelioImporter.gradeFor(112))
        assertEquals(FuelGrade.SUPER_98, FuelioImporter.gradeFor(113))
        assertEquals(FuelGrade.E_PLUS_91, FuelioImporter.gradeFor(106))
        assertEquals(FuelGrade.DIESEL, FuelioImporter.gradeFor(201))
        assertEquals(FuelGrade.LPG, FuelioImporter.gradeFor(401))
        assertEquals(FuelGrade.CNG, FuelioImporter.gradeFor(501))
        assertEquals(FuelGrade.ELECTRIC, FuelioImporter.gradeFor(602))
        assertNull(FuelioImporter.gradeFor(-1))
        assertNull(FuelioImporter.gradeFor(0))
    }

    @Test fun categoryHeuristics() {
        assertEquals(ExpenseCategoryKey.MULKIYA, FuelioImporter.categoryFor("Registration"))
        assertEquals(ExpenseCategoryKey.SALIK, FuelioImporter.categoryFor("Tolls"))
        assertEquals(ExpenseCategoryKey.WASH, FuelioImporter.categoryFor("Wash"))
        assertEquals(ExpenseCategoryKey.FINE, FuelioImporter.categoryFor("Tickets/Fines"))
        assertEquals(ExpenseCategoryKey.TYRES, FuelioImporter.categoryFor("Tires"))
        assertEquals(ExpenseCategoryKey.SERVICE, FuelioImporter.categoryFor("Maintenance"))
        assertEquals(ExpenseCategoryKey.PARKING, FuelioImporter.categoryFor("مواقف"))
        assertEquals(ExpenseCategoryKey.OTHER, FuelioImporter.categoryFor("Accessories"))
    }

    @Test fun dedupeKeyStableAcrossReimport() {
        val a = run(FuelioFixtures.SAMPLE_KM).events
        val b = run(FuelioFixtures.SAMPLE_KM).events
        assertEquals(a.map(FuelioImporter::dedupeKey), b.map(FuelioImporter::dedupeKey))
        assertNotEquals(FuelioImporter.dedupeKey(a[0]), FuelioImporter.dedupeKey(a[1]))
        assertEquals("${a[0].timestamp.epochSeconds / 60}|5.0|48.02", FuelioImporter.dedupeKey(a[0]))
    }

    @Test fun headerNormalization() {
        assertEquals(FuelioImporter.HeaderKey("odo", "km"), FuelioImporter.normalizeHeader("Odo(km)"))
        assertEquals(FuelioImporter.HeaderKey("odo", "mi"), FuelioImporter.normalizeHeader("Odo (mi)"))
        assertEquals(FuelioImporter.HeaderKey("price", null), FuelioImporter.normalizeHeader("Price (optional)"))
        assertEquals(FuelioImporter.HeaderKey("city", null), FuelioImporter.normalizeHeader("City(optional)"))
    }

    @Test fun garbageInputGivesWarningsNotCrash() {
        val r = run("hello,world\n1,2", null)
        assertTrue(r.events.isEmpty())
        assertEquals("Imported vehicle", r.vehicle.name)
        assertTrue(r.warnings.isNotEmpty())
    }

    @Test fun badRowsSkippedWithWarning() {
        val csv = FuelioFixtures.SAMPLE_KM + "\n\"not-a-date\",\"10\",\"5\",\"1\",\"\",\"\",\"\",\"\",\"\",\"\",\"0\",\"1\",\"0\",\"\",\"\",\"\",\"\",\"\""
        val r = run(csv)
        assertEquals(2, r.events.size)
        assertTrue(r.warnings.any { it.contains("skipped") })
    }

    @Test fun dateParsing() {
        val d = ImportText.parseDate("2026-09-31")!!
        assertTrue(d.clamped)
        assertEquals(LocalDateTime(2026, 9, 30, 0, 0), d.dateTime)
        assertEquals(LocalDateTime(2024, 2, 29, 0, 0), ImportText.parseDate("2024-02-30")!!.dateTime)
        assertEquals(LocalDateTime(2024, 3, 10, 16, 1), ImportText.parseDate("2024-03-10 16:01", "yyyy-MM-dd")!!.dateTime)
        assertEquals(LocalDateTime(2026, 2, 15, 0, 0), ImportText.parseDate("15.02.2026", "dd.MM.yyyy")!!.dateTime)
        assertEquals(LocalDateTime(2026, 2, 3, 0, 0), ImportText.parseDate("02/03/2026", "MM/dd/yyyy")!!.dateTime)
        assertNull(ImportText.parseDate("2026-13-01"))
        assertEquals(
            LocalDateTime(2024, 3, 10, 16, 1).toInstant(dubai),
            run(FuelioFixtures.SAMPLE_MILES).events[0].timestamp,
        )
    }

    @Test fun csvParserQuotesAndBom() {
        val rows = Csv.parse("﻿\"a\",\"b,c\",\"d\"\"e\",\r\nx,\"multi\nline\",z\n")
        assertEquals(listOf("a", "b,c", "d\"e", ""), rows[0])
        assertEquals(listOf("x", "multi\nline", "z"), rows[1])
        assertEquals(2, rows.size)
        assertEquals(';', Csv.sniffDelimiter("a;b;c\n1;2,5;3"))
    }
}
