package ae.sayyara.app.car

import ae.sayyara.app.R
import ae.sayyara.core.model.LatLon
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.text.SpannableString
import android.text.Spanned
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarLocation
import androidx.car.app.model.Distance
import androidx.car.app.model.DistanceSpan
import androidx.car.app.model.ItemList
import androidx.car.app.model.Metadata
import androidx.car.app.model.Place
import androidx.car.app.model.PlaceListMapTemplate
import androidx.car.app.model.PlaceMarker
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Root screen: nearby fuel stations (POI category) on the host-drawn map.
 *
 * Template rules kept: PlaceListMapTemplate with at most the host's place-list limit of rows, each with a Place
 * marker; an action strip with 2 actions ("Prices", "Log fill-up"); no free text input. Station coordinates are
 * from the bundled approximate list and every row says so.
 */
class StationsScreen(
    carContext: CarContext,
    private val monitor: CarRefuelMonitor,
) : Screen(carContext) {

    private var origin: LatLon? = null
    private var locating = true
    private var unavailableNoticeShown = false

    init {
        loadLocation()
        lifecycleScope.launch {
            monitor.state.collectLatest { state ->
                if (state is CarRefuelMonitor.State.Unavailable &&
                    state.reason == CarRefuelMonitor.Reason.NO_FUEL_LEVEL &&
                    !unavailableNoticeShown && AutoDetectInfoScreen.shouldShowOnce(carContext)
                ) {
                    unavailableNoticeShown = true
                    screenManager.push(AutoDetectInfoScreen(carContext, state.reason))
                }
            }
        }
    }

    override fun onGetTemplate(): Template {
        val builder = PlaceListMapTemplate.Builder()
            .setTitle(carContext.getString(R.string.car_stations_title))
            .setHeaderAction(Action.APP_ICON)
            .setActionStrip(actionStrip())
        if (hasLocationPermission(carContext)) builder.setCurrentLocationEnabled(true)
        if (locating) return builder.setLoading(true).build()

        val list = ItemList.Builder().setNoItemsMessage(carContext.getString(R.string.car_stations_empty))
        BundledStations.nearest(origin, rowLimit()).forEach { (station, km) ->
            list.addItem(stationRow(station, km))
        }
        return builder.setItemList(list.build()).build()
    }

    private fun stationRow(station: FuelStation, km: Double?): Row {
        val approx = carContext.getString(R.string.car_station_approx)
        val text = if (km != null) {
            // DistanceSpan replaces the placeholder character so the host formats the distance in local units.
            SpannableString("  · ${station.brand.label} · $approx").apply {
                setSpan(
                    DistanceSpan.create(Distance.create(km, Distance.UNIT_KILOMETERS)),
                    0, 1, Spanned.SPAN_INCLUSIVE_INCLUSIVE,
                )
            }
        } else {
            SpannableString("${station.brand.label} · $approx")
        }
        val marker = PlaceMarker.Builder()
            .setColor(
                when (station.brand) {
                    StationBrand.ADNOC -> CarColor.BLUE
                    StationBrand.ENOC -> CarColor.GREEN
                    StationBrand.EMARAT -> CarColor.RED
                },
            )
            .build()
        return Row.Builder()
            .setTitle(station.name)
            .addText(text)
            .setMetadata(
                Metadata.Builder()
                    .setPlace(
                        Place.Builder(CarLocation.create(station.location.lat, station.location.lon))
                            .setMarker(marker)
                            .build(),
                    )
                    .build(),
            )
            .setOnClickListener { screenManager.push(StationDetailScreen(carContext, station, km, monitor)) }
            .build()
    }

    private fun actionStrip(): ActionStrip = ActionStrip.Builder()
        .addAction(
            Action.Builder()
                .setTitle(carContext.getString(R.string.car_action_prices))
                .setOnClickListener { screenManager.push(PricesScreen(carContext, monitor)) }
                .build(),
        )
        .addAction(
            Action.Builder()
                .setTitle(carContext.getString(R.string.car_action_log_fill))
                .setOnClickListener { logFillOnPhone(carContext, monitor) }
                .build(),
        )
        .build()

    private fun rowLimit(): Int = try {
        carContext.getCarService(ConstraintManager::class.java)
            .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_PLACE_LIST)
            .coerceIn(1, MAX_ROWS)
    } catch (e: Exception) {
        MAX_ROWS
    }

    @SuppressLint("MissingPermission") // checked in hasLocationPermission
    private fun loadLocation() {
        if (!hasLocationPermission(carContext)) {
            locating = false
            return
        }
        try {
            LocationServices.getFusedLocationProviderClient(carContext).lastLocation
                .addOnSuccessListener { loc -> if (loc != null) origin = LatLon(loc.latitude, loc.longitude) }
                .addOnCompleteListener {
                    locating = false
                    invalidate()
                }
        } catch (e: Exception) {
            locating = false
        }
    }

    companion object {
        private const val MAX_ROWS = 6

        fun hasLocationPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        /**
         * "Log fill-up" from the car: the entry form is phone-only (no text input while driving), so we post a
         * phone notification that opens the quick add-fill sheet, with the current gauge as level_before.
         */
        fun logFillOnPhone(carContext: CarContext, monitor: CarRefuelMonitor) {
            val pct = monitor.currentFuelPercent
            val link = if (pct != null) "sayyara://add-fill?level_before=${CarFormat.percent(pct)}&source=car" else "sayyara://add-fill?source=car"
            val posted = CarNotifications.postDeepLink(
                carContext,
                CarNotifications.ID_LOG_FILL,
                title = carContext.getString(R.string.car_notification_log_title),
                text = carContext.getString(R.string.car_notification_log_text),
                deepLink = link,
            )
            CarToast.makeText(
                carContext,
                if (posted) R.string.car_toast_sent_to_phone else R.string.car_toast_notifications_off,
                CarToast.LENGTH_LONG,
            ).show()
        }
    }
}
