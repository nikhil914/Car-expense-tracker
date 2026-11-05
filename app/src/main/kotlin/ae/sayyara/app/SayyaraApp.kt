package ae.sayyara.app

import ae.sayyara.app.data.DefaultAppContainer
import ae.sayyara.app.platform.Notifications
import ae.sayyara.app.platform.WorkScheduler
import android.app.Application
import android.content.Context

class SayyaraApp : Application() {

    /** DI root. UI code should use it through the AppContainer interface. */
    lateinit var container: DefaultAppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
        Notifications.createChannels(this)
        WorkScheduler.scheduleAll(this)
        container.start()
    }
}

/** `context.appContainer` from any Context of this app (activities, workers, receivers, widgets). */
val Context.appContainer: DefaultAppContainer
    get() = (applicationContext as SayyaraApp).container
