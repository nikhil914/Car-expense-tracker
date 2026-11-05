package ae.sayyara.app.car

import ae.sayyara.app.BuildConfig
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

/**
 * Android Auto entry point (POI category, declared in the app manifest with minCarApiLevel 3).
 *
 * Host validation:
 *  - Debug builds accept any host so the Desktop Head Unit (DHU) and test hosts can connect.
 *  - Release builds accept only the hosts in the allowlist.
 *
 * PRODUCTION MUST USE THE ALLOWLIST. ALLOW_ALL_HOSTS_VALIDATOR lets any app on the phone bind to this
 * service and read the car data we receive (fuel level, odometer, speed).
 * The allowlist here is the library's sample list (`androidx.car.app.R.array.hosts_allowlist_sample`),
 * which holds the Android Auto and Android Automotive template hosts with their signing-certificate digests.
 * Before release, copy it into our own `res/values/car_hosts_allowlist.xml` and review it, so a library
 * update cannot change the trusted hosts silently.
 */
class SayyaraCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator =
        if (BuildConfig.DEBUG) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(applicationContext)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build()
        }

    override fun onCreateSession(): Session = SayyaraSession()
}
