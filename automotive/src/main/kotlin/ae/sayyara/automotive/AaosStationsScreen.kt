package ae.sayyara.automotive

import ae.sayyara.core.model.LatLon
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.Uri
import android.text.SpannableString
import android.text.Spanned
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarLocation
import androidx.car.app.model.Distance
import androidx.car.app.model.DistanceSpan
import androidx.car.app.model.ItemList
import androidx.car.app.model.Metadata
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Place
import androidx.car.app.model.PlaceListMapTemplate
import androidx.car.app.model.PlaceMarker
import androidx.car.app.model.Row
import androidx.car.app.model.Template

/** Root POI screen on AAOS: nearby stations from the bundled (approximate) list, plus Detected and Prices. */
class AaosStationsScreen(
    carContext: CarContext,
    private val monitor: AaosRefuelMonitor,
) : ScopedScreen(carContext) {

    override fun onGetTemplate(): Template {
        val origin = lastLocation(carContext)
        val approx = carContext.getString(R.string.aaos_station_approx)
        val list = ItemList.Builder().setNoItemsMessage(carContext.getString(R.string.aaos_stations_empty))
        AaosStations.nearest(origin, rowLimit()).forEach { (station, km) ->
            val text = if (km != null) {
                SpannableString("  · ${station.brand.label} · $approx").apply {
                    setSpan(DistanceSpan.create(Distance.create(km, Distance.UNIT_KILOMETERS)), 0, 1, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
                }
            } else SpannableString("${station.brand.label} · $approx")
            val color = when (station.brand) {
                StationBrand.ADNOC -> CarColor.BLUE
                StationBrand.ENOC -> CarColor.GREEN
                StationBrand.EMARAT -> CarColor.RED
            }
            list.addItem(
                Row.Builder()
                    .setTitle(station.name)
                    .addText(text)
                    .setMetadata(
                        Metadata.Builder().setPlace(
                            Place.Builder(CarLocation.create(station.location.lat, station.location.lon))
                                .setMarker(PlaceMarker.Builder().setColor(color).build())
                                .build(),
                        ).build(),
                    )
                    .setOnClickListener { screenManager.push(AaosStationDetailScreen(carContext, station, km)) }
                    .build(),
            )
        }
        val builder = PlaceListMapTemplate.Builder()
            .setTitle(carContext.getString(R.string.aaos_stations_title))
            .setHeaderAction(Action.APP_ICON)
            .setItemList(list.build())
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setTitle(carContext.getString(R.string.aaos_action_detected))
                            .setOnClickListener { screenManager.push(PendingRefuelsScreen(carContext, monitor)) }
                            .build(),
                    )
                    .addAction(
                        Action.Builder()
                            .setTitle(carContext.getString(R.string.aaos_action_prices))
                            .setOnClickListener { screenManager.push(AaosPricesScreen(carContext)) }
                            .build(),
                    )
                    .build(),
            )
        if (hasLocation(carContext)) builder.setCurrentLocationEnabled(true)
        return builder.build()
    }

    private fun rowLimit(): Int = try {
        carContext.getCarService(ConstraintManager::class.java)
            .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_PLACE_LIST).coerceIn(1, 6)
    } catch (e: Exception) {
        6
    }

    companion object {
        fun hasLocation(context: Context): Boolean =
            AaosPermissions.granted(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
                AaosPermissions.granted(context, Manifest.permission.ACCESS_COARSE_LOCATION)

        /** Last known fix from the platform LocationManager (no Play services dependency in this module). */
        @SuppressLint("MissingPermission") // checked by hasLocation()
        fun lastLocation(context: Context): LatLon? {
            if (!hasLocation(context)) return null
            val lm = context.getSystemService(LocationManager::class.java) ?: return null
            val providers = buildList {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
                add(LocationManager.GPS_PROVIDER)
                add(LocationManager.NETWORK_PROVIDER)
            }
            return providers.asSequence()
                .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
                .maxByOrNull { it.time }
                ?.let { LatLon(it.latitude, it.longitude) }
        }
    }
}

/** Station detail with Navigate. */
class AaosStationDetailScreen(
    carContext: CarContext,
    private val station: FuelStation,
    private val km: Double?,
) : ScopedScreen(carContext) {
    override fun onGetTemplate(): Template {
        val pane = Pane.Builder()
            .addRow(Row.Builder().setTitle(station.brand.label).addText(station.area).build())
        if (km != null) {
            pane.addRow(Row.Builder().setTitle(carContext.getString(R.string.aaos_station_distance, String.format(AaosFormat.locale(carContext), "%.1f", km))).build())
        }
        pane.addRow(Row.Builder().setTitle(carContext.getString(R.string.aaos_station_approx_long)).build())
        pane.addAction(
            Action.Builder()
                .setTitle(carContext.getString(R.string.aaos_action_navigate))
                .setOnClickListener {
                    try {
                        carContext.startCarApp(Intent(CarContext.ACTION_NAVIGATE, Uri.parse("geo:${station.location.lat},${station.location.lon}")))
                    } catch (e: Exception) {
                        CarToast.makeText(carContext, R.string.aaos_toast_no_navigation, CarToast.LENGTH_LONG).show()
                    }
                }
                .build(),
        )
        return PaneTemplate.Builder(pane.build())
            .setTitle(station.name)
            .setHeaderAction(Action.BACK)
            .build()
    }
}

/** This month's UAE prices from core FuelPriceBook. Tapping a grade makes it this car's grade (used for pricing). */
class AaosPricesScreen(carContext: CarContext) : ScopedScreen(carContext) {
    override fun onGetTemplate(): Template {
        val book = AaosServices.prices
        val prefs = AaosServices.prefs(carContext)
        val month = book.currentMonth()
        val list = ItemList.Builder()
        AaosFormat.petrolGrades.forEach { grade ->
            val q = book.quote(month, grade)
            val row = Row.Builder().setTitle(AaosFormat.gradeLabel(carContext, grade))
            row.addText(
                q?.let { carContext.getString(R.string.aaos_price_per_litre, AaosFormat.aed2(carContext, it.aedPerLitre)) }
                    ?: carContext.getString(R.string.aaos_price_unknown),
            )
            val second = buildList {
                if (q != null && q.status != ae.sayyara.core.uae.QuoteStatus.FINAL) add(carContext.getString(R.string.aaos_price_provisional))
                if (grade == prefs.grade) add(carContext.getString(R.string.aaos_price_your_grade))
            }
            if (second.isNotEmpty()) row.addText(second.joinToString(" · "))
            row.setOnClickListener {
                prefs.grade = grade
                invalidate()
            }
            list.addItem(row.build())
        }
        return androidx.car.app.model.ListTemplate.Builder()
            .setTitle(carContext.getString(R.string.aaos_prices_title, AaosFormat.shortMonth(carContext, month)))
            .setHeaderAction(Action.BACK)
            .setSingleList(list.build())
            .build()
    }
}
