package ae.sayyara.core.importer

import ae.sayyara.core.model.Emirate
import ae.sayyara.core.model.Reminder
import ae.sayyara.core.model.ReminderKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

class CsvExporterTest {
    private val now = Instant.parse("2026-11-05T10:00:00Z")

    @Test fun roundTripFromFuelioImport() {
        var n = 0
        val imp = FuelioImporter.parse(FuelioFixtures.DUAL_TANK, null, "b1", now) { "id-${++n}" }
        val veh = imp.vehicle.copy(emirate = Emirate.DUBAI, plate = "Dubai, \"A\" 12345", purchaseDate = LocalDate(2020, 1, 2))
        val events = imp.events.mapIndexed { i, e -> if (i == 0) e.copy(note = "line1\nline2, with comma", levelBefore = 0.125) else e }
        val csv = CsvExporter.export(listOf(veh), imp.stores, events, imp.expenses)
        assertTrue(SayyaraCsv.isSayyaraCsv(csv))
        val back = SayyaraCsv.parse(csv)
        assertEquals(emptyList(), back.warnings)
        assertEquals(listOf(veh), back.vehicles)
        assertEquals(imp.stores, back.stores)
        assertEquals(events, back.events)
        assertEquals(imp.expenses, back.expenses)
        assertTrue(back.reminders.isEmpty())
    }

    @Test fun roundTripReminders() {
        val rem = Reminder("r1", "v1", ReminderKind.MULKIYA, "Renew Mulkiya", dueDate = LocalDate(2027, 3, 1),
            repeatMonths = 12, chainedToId = "r0")
        val csv = CsvExporter.export(emptyList(), emptyList(), emptyList(), emptyList(), listOf(rem))
        assertEquals(listOf(rem), SayyaraCsv.parse(csv).reminders)
    }

    @Test fun exportSectionsInOrder() {
        val csv = CsvExporter.export(emptyList(), emptyList(), emptyList(), emptyList())
        val markers = csv.lines().filter { it.startsWith("## ") }
        assertEquals(listOf("## Sayyara", "## Vehicles", "## EnergyStores", "## EnergyEvents", "## Expenses"), markers)
    }

    @Test fun badRowIsWarnedNotFatal() {
        val csv = "## Sayyara\nformat,version\nsayyara-csv,1\n## Expenses\nid,vehicleId,timestamp,amountAed,category,updatedAt\n" +
            "e1,v1,2026-01-01T00:00:00Z,12.5,PARKING,2026-01-01T00:00:00Z\ne2,v1,not-a-time,3,PARKING,2026-01-01T00:00:00Z\n"
        val r = SayyaraCsv.parse(csv)
        assertEquals(1, r.expenses.size)
        assertEquals(1, r.warnings.size)
    }
}
