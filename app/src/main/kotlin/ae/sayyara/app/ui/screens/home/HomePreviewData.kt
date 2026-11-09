package ae.sayyara.app.ui.screens.home

import ae.sayyara.app.ui.settings.UiSettings
import ae.sayyara.core.consumption.EconomyUnit
import ae.sayyara.core.model.Emirate
import ae.sayyara.core.model.EnergyStoreKind
import ae.sayyara.core.model.ExpenseCategoryKey
import ae.sayyara.core.model.FuelGrade
import ae.sayyara.core.model.Reminder
import ae.sayyara.core.model.ReminderKind
import ae.sayyara.core.model.Vehicle
import ae.sayyara.core.model.YearMonth
import ae.sayyara.core.reminders.ReminderPlanner
import ae.sayyara.core.uae.PriceQuote
import ae.sayyara.core.uae.QuoteStatus
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/** Realistic UAE sample data for Home previews (November 2026). */
object HomePreviewData {
    private val now = Instant.parse("2026-11-09T08:00:00Z")

    val altima = Vehicle(
        id = "v1", name = "Nissan Altima", make = "Nissan", model = "Altima", year = 2021,
        plate = "5", emirate = Emirate.ABU_DHABI, defaultGrade = FuelGrade.SPECIAL_95, updatedAt = now,
    )
    val landCruiser = Vehicle(
        id = "v2", name = "Toyota Land Cruiser", make = "Toyota", model = "Land Cruiser", year = 2023,
        plate = "Q 48213", emirate = Emirate.DUBAI, defaultGrade = FuelGrade.SUPER_98, updatedAt = now,
    )

    /** Mulkiya chain not set up yet, so the CTA banner shows; one overdue item, one upcoming. */
    fun state(): HomeUiState {
        val insurance = Reminder("r1", "v1", ReminderKind.INSURANCE, "Insurance renewal", dueDate = LocalDate(2026, 12, 1), leadDays = 21)
        val oil = Reminder("r2", "v1", ReminderKind.OIL, "Oil change", dueOdometerKm = 45_090.0)
        return HomeUiState(
            loading = false,
            vehicles = listOf(altima, landCruiser),
            vehicle = altima,
            settings = UiSettings(economyUnit = EconomyUnit.KM_PER_L),
            storeKind = EnergyStoreKind.TANK,
            lastPer100km = 100 / 11.4,
            averagePer100km = 100 / 10.8,
            basedOnFills = 3,
            basedSince = Instant.parse("2026-11-02T06:00:00Z"),
            pendingPartials = 2,
            fuelCostPerKmAed = 0.37,
            totalCostPerKmAed = 0.42,
            fillCount = 6,
            month = YearMonth(2026, 11),
            monthTotalAed = 1240.0,
            monthBreakdown = listOf(
                ExpenseCategoryKey.FUEL to 820.0,
                ExpenseCategoryKey.SERVICE to 150.0,
                ExpenseCategoryKey.SALIK to 126.0,
                ExpenseCategoryKey.PARKING to 96.0,
                ExpenseCategoryKey.DARB to 48.0,
            ),
            priceGrade = FuelGrade.SPECIAL_95,
            priceNow = PriceQuote(YearMonth(2026, 11), FuelGrade.SPECIAL_95, 4.04, QuoteStatus.FINAL),
            pricePrevious = PriceQuote(YearMonth(2026, 10), FuelGrade.SPECIAL_95, 4.28, QuoteStatus.FINAL),
            reminders = listOf(
                ReminderDue(oil, ReminderPlanner.Due("r2", null, null, -120.0, true)),
                ReminderDue(insurance, ReminderPlanner.Due("r1", LocalDate(2026, 12, 1), 22, null, false)),
            ),
            hasMulkiyaReminder = false,
        )
    }

    /** Next month's price not announced yet: provisional badge and explanation. */
    fun provisionalPriceState(): HomeUiState = state().copy(
        priceNow = PriceQuote(YearMonth(2026, 12), FuelGrade.SPECIAL_95, 4.04, QuoteStatus.PROVISIONAL),
        pricePrevious = PriceQuote(YearMonth(2026, 11), FuelGrade.SPECIAL_95, 4.04, QuoteStatus.FINAL),
    )
}
