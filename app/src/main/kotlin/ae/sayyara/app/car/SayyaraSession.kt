package ae.sayyara.app.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.Session
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlin.time.Instant
import kotlinx.coroutines.launch

/**
 * One Android Auto session (one car connection).
 *
 * The [CarRefuelMonitor] lives for the whole session, from creation to destroy, not only while our screen
 * is visible: the user usually has Maps in front. When a refuel is detected:
 *  - a [RefuelDetectedScreen] is pushed (shown at once, or when the user returns to Sayyara), and
 *  - if Sayyara is not on the car screen, a phone notification with the draft deep link is posted as well,
 *    so the fill-up is not lost if the user never opens the car app again.
 */
class SayyaraSession : Session() {

    private var lastShown: Instant? = null

    override fun onCreateScreen(intent: Intent): Screen {
        val monitor = CarRefuelMonitor(carContext, lifecycleScope)
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) = monitor.start()
            override fun onDestroy(owner: LifecycleOwner) = monitor.stop()
        })
        // onCreateScreen runs after ON_CREATE, so the observer above may not see onCreate: start explicitly.
        monitor.start()

        lifecycleScope.launch {
            monitor.detections.collect { detection ->
                val at = detection.refuel.at
                if (at == lastShown) return@collect
                lastShown = at
                val screenManager = carContext.getCarService(ScreenManager::class.java)
                screenManager.push(RefuelDetectedScreen(carContext, detection))
                if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    RefuelDetectedScreen.postDraftNotification(carContext, detection, price = null)
                }
            }
        }
        return StationsScreen(carContext, monitor)
    }
}
