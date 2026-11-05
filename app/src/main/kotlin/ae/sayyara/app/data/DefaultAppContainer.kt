package ae.sayyara.app.data

import ae.sayyara.app.data.db.SayyaraDatabase
import ae.sayyara.app.platform.BackupManager
import ae.sayyara.app.platform.OsrmRouteProvider
import ae.sayyara.app.platform.ReceiptScanner
import ae.sayyara.app.platform.widget.DashboardWidgetUpdater
import ae.sayyara.core.route.RouteProvider
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Manual DI root, created once in SayyaraApp. Repository properties are typed as the concrete Room
 * implementations (covariant overrides), so platform code can reach the extra helpers while the UI
 * keeps depending on the [AppContainer] interfaces only.
 */
class DefaultAppContainer(context: Context) : AppContainer {
    private val appContext = context.applicationContext

    /** Lives as long as the process; for work that must outlive a screen. */
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: SayyaraDatabase by lazy { SayyaraDatabase.build(appContext) }
    val preferences: AppPreferences by lazy { AppPreferences(appContext) }

    override val vehicles: RoomVehicleRepository by lazy { RoomVehicleRepository(database, preferences) }
    override val events: RoomEnergyEventRepository by lazy { RoomEnergyEventRepository(database) }
    override val expenses: RoomExpenseRepository by lazy { RoomExpenseRepository(database) }
    override val reminders: RoomReminderRepository by lazy { RoomReminderRepository(database) }
    override val trips: RoomTripRepository by lazy { RoomTripRepository(database) }
    override val fuelPrices: RoomFuelPriceRepository by lazy { RoomFuelPriceRepository(database.fuelPriceDao()) }
    override val importer: RoomImportRepository by lazy { RoomImportRepository(appContext, database, preferences) }

    /** Demo OSRM server; see the note in OsrmRouteProvider before shipping. */
    val routeProvider: RouteProvider by lazy { OsrmRouteProvider() }

    val backupManager: BackupManager by lazy { BackupManager(appContext, database, preferences) }

    /** New scanner per caller; the caller must close() it. */
    fun newReceiptScanner(): ReceiptScanner = ReceiptScanner(appContext)

    /** Starts process-wide background work. Called from SayyaraApp.onCreate. */
    fun start() {
        applicationScope.launch { fuelPrices.ensureSeeded() }
        DashboardWidgetUpdater.start(appContext, this, applicationScope)
    }
}
