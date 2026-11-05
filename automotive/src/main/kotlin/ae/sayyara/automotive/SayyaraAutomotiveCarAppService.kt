package ae.sayyara.automotive

import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * AAOS entry point (POI category). Hosts the stations/prices screens and owns the refuel monitor
 * while the session is alive. Background snapshots between sessions come from RefuelSnapshotJobService.
 */
class SayyaraAutomotiveCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator {
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        // Production must allowlist hosts (R.array.hosts_allowlist_sample from car-app library or our own list).
        return if (debuggable) HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        else HostValidator.Builder(applicationContext)
            .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
            .build()
    }

    override fun onCreateSession(): Session = SayyaraAutomotiveSession()
}

class SayyaraAutomotiveSession : Session() {
    // Lazy: onCreateScreen may run before the lifecycle observer's onCreate is dispatched.
    private val monitor by lazy { AaosRefuelMonitor(carContext) }
    private var started = false

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                if (!started) { monitor.start(); started = true }
                RefuelSnapshot.schedulePeriodic(carContext)
            }

            override fun onDestroy(owner: LifecycleOwner) {
                if (started) monitor.stop()
            }
        })
    }

    override fun onCreateScreen(intent: Intent): Screen {
        val root = AaosStationsScreen(carContext, monitor)
        val sm = carContext.getCarService(ScreenManager::class.java)
        if (!AaosPermissions.canDetect(carContext)) {
            sm.push(root)
            return PermissionScreen(carContext, monitor)
        }
        if (intent.action == RefuelNotifier.ACTION_SHOW_PENDING) {
            sm.push(root)
            return PendingRefuelsScreen(carContext, monitor)
        }
        return root
    }

    override fun onNewIntent(intent: Intent) {
        if (intent.action == RefuelNotifier.ACTION_SHOW_PENDING) {
            carContext.getCarService(ScreenManager::class.java).push(PendingRefuelsScreen(carContext, monitor))
        }
    }
}
