package ae.sayyara.app.car

import ae.sayyara.app.R
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.versioning.CarAppApiLevels

/** One station: brand, distance, the "approximate location" note, and Navigate / Log fill-up. */
class StationDetailScreen(
    carContext: CarContext,
    private val station: FuelStation,
    private val distanceKm: Double?,
    private val monitor: CarRefuelMonitor,
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val pane = Pane.Builder()
            .addRow(
                Row.Builder()
                    .setTitle(station.brand.label)
                    .addText(station.area)
                    .build(),
            )
        if (distanceKm != null) {
            pane.addRow(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.car_station_distance, String.format(CarFormat.locale(carContext), "%.1f", distanceKm)))
                    .build(),
            )
        }
        pane.addRow(Row.Builder().setTitle(carContext.getString(R.string.car_station_approx_long)).build())

        val navigate = Action.Builder()
            .setTitle(carContext.getString(R.string.car_action_navigate))
            .setOnClickListener { navigate() }
        if (carContext.carAppApiLevel >= CarAppApiLevels.LEVEL_4) navigate.setFlags(Action.FLAG_PRIMARY)
        pane.addAction(navigate.build())
        pane.addAction(
            Action.Builder()
                .setTitle(carContext.getString(R.string.car_action_log_fill))
                .setOnClickListener { StationsScreen.logFillOnPhone(carContext, monitor) }
                .build(),
        )
        return PaneTemplate.Builder(pane.build())
            .setTitle(station.name)
            .setHeaderAction(Action.BACK)
            .build()
    }

    /** Hands the destination to the user's navigation app (allowed for POI apps). */
    private fun navigate() {
        val uri = Uri.parse("geo:${station.location.lat},${station.location.lon}")
        try {
            carContext.startCarApp(Intent(CarContext.ACTION_NAVIGATE, uri))
        } catch (e: Exception) {
            Log.w("StationDetailScreen", "No navigation app", e)
            CarToast.makeText(carContext, R.string.car_toast_no_navigation, CarToast.LENGTH_LONG).show()
        }
    }
}
