package ae.sayyara.app.ui.navigation

import ae.sayyara.app.R
import ae.sayyara.app.ui.screens.home.HomeScreen
import ae.sayyara.app.ui.screens.fill.AddFillUpSheet
import ae.sayyara.app.ui.screens.expense.AddExpenseSheet
import ae.sayyara.app.ui.screens.expense.ExpenseSheetRequest
import ae.sayyara.app.ui.screens.fill.FillSheetRequest
import ae.sayyara.app.ui.screens.history.HistoryScreen
import ae.sayyara.app.ui.screens.importer.ImportScreen
import ae.sayyara.app.ui.screens.more.AboutScreen
import ae.sayyara.app.ui.screens.more.MoreScreen
import ae.sayyara.app.ui.screens.more.SettingsScreen
import ae.sayyara.app.ui.screens.onboarding.OnboardingScreen
import ae.sayyara.app.ui.screens.reminders.MulkiyaWizardScreen
import ae.sayyara.app.ui.screens.reminders.ReminderEditScreen
import ae.sayyara.app.ui.screens.reminders.RemindersScreen
import ae.sayyara.app.ui.screens.route.RouteCostScreen
import ae.sayyara.app.ui.screens.stats.StatsScreen
import ae.sayyara.app.ui.screens.vehicles.VehicleEditScreen
import ae.sayyara.app.ui.screens.vehicles.VehiclesScreen
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlin.reflect.KClass

private data class Tab(val route: Any, val cls: KClass<*>, val icon: ImageVector, val label: Int)

private val tabs = listOf(
    Tab(DashboardRoute, DashboardRoute::class, Icons.Filled.Home, R.string.nav_dashboard),
    Tab(HistoryRoute, HistoryRoute::class, Icons.Filled.History, R.string.nav_history),
    Tab(StatsRoute, StatsRoute::class, Icons.Filled.BarChart, R.string.nav_stats),
    Tab(MoreRoute, MoreRoute::class, Icons.Filled.MoreHoriz, R.string.nav_more),
)

/**
 * Root navigation. [pendingLink] is the latest deep link from MainActivity; [onLinkHandled] clears it.
 * The fill-up form is a bottom sheet over any screen, not a destination.
 */
@Composable
fun AppNavHost(onboardingDone: Boolean, pendingLink: DeepLinkAction?, onLinkHandled: () -> Unit) {
    val nav = rememberNavController()
    var fill by remember { mutableStateOf<FillSheetRequest?>(null) }
    var expense by remember { mutableStateOf<ExpenseSheetRequest?>(null) }
    val entry by nav.currentBackStackEntryAsState()
    val dest = entry?.destination
    val showBar = tabs.any { t -> dest?.hasRoute(t.cls) == true }

    LaunchedEffect(pendingLink) {
        when (val l = pendingLink ?: return@LaunchedEffect) {
            is DeepLinkAction.AddFill -> fill = FillSheetRequest(draft = l.draft)
            DeepLinkAction.Dashboard -> nav.navigate(DashboardRoute) { launchSingleTop = true }
            is DeepLinkAction.AddExpense -> expense = ExpenseSheetRequest(category = l.category)
            is DeepLinkAction.Import -> nav.navigate(ImportRoute(l.uri?.toString()))
            DeepLinkAction.Route -> nav.navigate(RouteCostRoute)
        }
        onLinkHandled()
    }

    Scaffold(bottomBar = {
        if (showBar) NavigationBar {
            tabs.forEach { t ->
                NavigationBarItem(
                    selected = dest?.hasRoute(t.cls) == true,
                    onClick = {
                        nav.navigate(t.route) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true; restoreState = true
                        }
                    },
                    icon = { Icon(t.icon, null) },
                    label = { Text(stringResource(t.label)) },
                )
            }
        }
    }) { pad ->
        NavHost(nav, startDestination = if (onboardingDone) DashboardRoute else OnboardingRoute, modifier = Modifier.padding(pad)) {
            composable<OnboardingRoute> {
                OnboardingScreen(
                    onFinished = { nav.navigate(DashboardRoute) { popUpTo(OnboardingRoute) { inclusive = true } } },
                    onImport = { nav.navigate(DashboardRoute) { popUpTo(OnboardingRoute) { inclusive = true } }; nav.navigate(ImportRoute()) },
                )
            }
            composable<DashboardRoute> {
                HomeScreen(
                    onAddFill = { fill = FillSheetRequest() },
                    onAddVehicle = { nav.navigate(VehicleEditRoute()) },
                    onOpenReminders = { nav.navigate(RemindersRoute) },
                    onMulkiyaWizard = { nav.navigate(MulkiyaWizardRoute) },
                    onOpenStats = { nav.navigate(StatsRoute) },
                )
            }
            composable<HistoryRoute> {
                HistoryScreen(
                    onEditFill = { fill = FillSheetRequest(eventId = it) },
                    onEditExpense = { expense = ExpenseSheetRequest(expenseId = it) },
                    onAddExpense = { expense = ExpenseSheetRequest() },
                )
            }
            composable<StatsRoute> { StatsScreen() }
            composable<MoreRoute> {
                MoreScreen(
                    onVehicles = { nav.navigate(VehiclesRoute) }, onReminders = { nav.navigate(RemindersRoute) },
                    onRoute = { nav.navigate(RouteCostRoute) }, onImport = { nav.navigate(ImportRoute()) },
                    onSettings = { nav.navigate(SettingsRoute) }, onAbout = { nav.navigate(AboutRoute) },
                )
            }
            composable<RouteCostRoute> { RouteCostScreen(onBack = { nav.popBackStack() }) }
            composable<RemindersRoute> {
                RemindersScreen(onBack = { nav.popBackStack() }, onEdit = { nav.navigate(ReminderEditRoute(it)) },
                    onMulkiya = { nav.navigate(MulkiyaWizardRoute) })
            }
            composable<ReminderEditRoute> { e -> ReminderEditScreen(e.toRoute<ReminderEditRoute>().reminderId, onDone = { nav.popBackStack() }) }
            composable<MulkiyaWizardRoute> { MulkiyaWizardScreen(onDone = { nav.popBackStack() }) }
            composable<VehiclesRoute> { VehiclesScreen(onBack = { nav.popBackStack() }, onEdit = { nav.navigate(VehicleEditRoute(it)) }) }
            composable<VehicleEditRoute> { e -> VehicleEditScreen(e.toRoute<VehicleEditRoute>().vehicleId, onDone = { nav.popBackStack() }) }
            composable<ImportRoute> { e -> ImportScreen(e.toRoute<ImportRoute>().uri, onBack = { nav.popBackStack() }) }
            composable<SettingsRoute> { SettingsScreen(onBack = { nav.popBackStack() }) }
            composable<AboutRoute> { AboutScreen(onBack = { nav.popBackStack() }) }
        }
    }

    fill?.let { req -> AddFillUpSheet(req, onDismiss = { fill = null }) }
    expense?.let { req -> AddExpenseSheet(req, onDismiss = { expense = null }) }
}
