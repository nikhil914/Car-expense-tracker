package ae.sayyara.app.ui.navigation

import kotlinx.serialization.Serializable

// Type-safe navigation-compose destinations.

@Serializable data object OnboardingRoute

// Bottom-bar destinations
@Serializable data object DashboardRoute
@Serializable data object HistoryRoute
@Serializable data object StatsRoute
@Serializable data object MoreRoute

/** [category] is an ExpenseCategoryKey name, used to preselect from deep links. */
@Serializable data class ExpenseEditRoute(val expenseId: String? = null, val category: String? = null)

@Serializable data object RouteCostRoute
@Serializable data object RemindersRoute
@Serializable data class ReminderEditRoute(val reminderId: String? = null)
@Serializable data object MulkiyaWizardRoute
@Serializable data object VehiclesRoute
@Serializable data class VehicleEditRoute(val vehicleId: String? = null)

/** [uri] is a content Uri string (ACTION_SEND share) to preview straight away. */
@Serializable data class ImportRoute(val uri: String? = null)

@Serializable data object SettingsRoute
@Serializable data object AboutRoute
