package ae.sayyara.app.car

import ae.sayyara.app.R
import android.content.Context
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template

/** Explains why refuel auto-detect is off for this car or vehicle profile. */
class AutoDetectInfoScreen(
    carContext: CarContext,
    private val reason: CarRefuelMonitor.Reason,
) : Screen(carContext) {

    override fun onGetTemplate(): Template =
        MessageTemplate.Builder(carContext.getString(messageFor(reason)))
            .setTitle(carContext.getString(R.string.car_autodetect_title))
            .setHeaderAction(Action.BACK)
            .addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.car_action_ok))
                    .setOnClickListener { finish() }
                    .build(),
            )
            .build()

    companion object {
        private const val PREFS = "car_refuel_monitor"
        private const val KEY_NOTICE = "no_fuel_level_notice_shown"

        fun messageFor(reason: CarRefuelMonitor.Reason): Int = when (reason) {
            CarRefuelMonitor.Reason.NO_FUEL_LEVEL -> R.string.car_autodetect_no_fuel_level
            CarRefuelMonitor.Reason.NO_VEHICLE -> R.string.car_autodetect_no_vehicle
            CarRefuelMonitor.Reason.NO_TANK_CAPACITY -> R.string.car_autodetect_no_capacity
            CarRefuelMonitor.Reason.ELECTRIC_ONLY -> R.string.car_autodetect_electric
            CarRefuelMonitor.Reason.NO_PERMISSION -> R.string.car_autodetect_no_permission
            CarRefuelMonitor.Reason.NOT_SUPPORTED -> R.string.car_autodetect_not_supported
        }

        /** The automatic "your car does not share fuel level" notice is shown once; it stays reachable from Prices. */
        fun shouldShowOnce(context: Context): Boolean {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (prefs.getBoolean(KEY_NOTICE, false)) return false
            prefs.edit().putBoolean(KEY_NOTICE, true).apply()
            return true
        }
    }
}
