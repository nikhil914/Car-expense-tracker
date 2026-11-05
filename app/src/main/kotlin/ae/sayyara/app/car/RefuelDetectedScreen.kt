package ae.sayyara.app.car

import ae.sayyara.app.R
import ae.sayyara.app.SayyaraApp
import ae.sayyara.core.model.EnergyEvent
import ae.sayyara.core.model.FuelPrice
import ae.sayyara.core.model.PriceSource
import ae.sayyara.core.refuel.toDraftDeepLink
import android.content.Context
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.versioning.CarAppApiLevels
import androidx.lifecycle.lifecycleScope
import java.util.Locale
import java.util.UUID
import kotlin.time.Clock
import kotlinx.coroutines.launch

/**
 * "Refuel detected: ~32 L (AED 129 at Special 95, Nov price)" with Save / Edit on phone / Dismiss.
 *
 * - Save writes the [EnergyEvent] directly (official monthly price, note "auto-detected (car)"). It is offered only
 *   when an odometer reading is known, because EnergyEvent.odometerKm is required and a guessed odometer would
 *   corrupt the full-to-full consumption maths.
 * - Edit on phone posts a phone notification with the `sayyara://add-fill?...&source=car` draft deep link.
 * - Dismiss (action strip and back) drops it.
 *
 * PaneTemplate allows at most 2 pane actions, so Dismiss lives in the action strip.
 */
class RefuelDetectedScreen(
    carContext: CarContext,
    private val detection: CarRefuelMonitor.Detection,
) : Screen(carContext) {

    private var price: FuelPrice? = null
    private var loading = true
    private var saving = false

    init {
        lifecycleScope.launch {
            price = lookupPrice(carContext, detection)
            loading = false
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val title = carContext.getString(R.string.car_refuel_title)
        if (loading) {
            return PaneTemplate.Builder(Pane.Builder().setLoading(true).build())
                .setTitle(title)
                .setHeaderAction(Action.BACK)
                .build()
        }
        val r = detection.refuel
        val pane = Pane.Builder()
            .addRow(Row.Builder().setTitle(summary(carContext, detection, price)).build())
            .addRow(
                Row.Builder()
                    .setTitle(
                        carContext.getString(
                            R.string.car_refuel_gauge,
                            CarFormat.percent(r.levelBeforePercent),
                            CarFormat.percent(r.levelAfterPercent),
                        ),
                    )
                    .addText(
                        carContext.getString(if (r.likelyFull) R.string.car_refuel_full_tank else R.string.car_refuel_partial),
                    )
                    .build(),
            )
            .addRow(
                Row.Builder()
                    .setTitle(
                        detection.odometerKm?.let { carContext.getString(R.string.car_refuel_odometer, CarFormat.km(carContext, it)) }
                            ?: carContext.getString(R.string.car_refuel_odometer_unknown),
                    )
                    .addText(carContext.getString(R.string.car_refuel_estimate_note))
                    .build(),
            )

        if (detection.odometerKm != null) {
            val save = Action.Builder()
                .setTitle(carContext.getString(R.string.car_action_save))
                .setOnClickListener { save() }
            if (carContext.carAppApiLevel >= CarAppApiLevels.LEVEL_4) save.setFlags(Action.FLAG_PRIMARY)
            pane.addAction(save.build())
        }
        pane.addAction(
            Action.Builder()
                .setTitle(carContext.getString(R.string.car_action_edit_on_phone))
                .setOnClickListener { editOnPhone() }
                .build(),
        )

        return PaneTemplate.Builder(pane.build())
            .setTitle(title)
            .setHeaderAction(Action.BACK)
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setTitle(carContext.getString(R.string.car_action_dismiss))
                            .setOnClickListener { finish() }
                            .build(),
                    )
                    .build(),
            )
            .build()
    }

    private fun save() {
        if (saving) return
        val odo = detection.odometerKm ?: return
        saving = true
        val r = detection.refuel
        val p = price
        val litres = CarFormat.round1(r.litresAdded)
        val now = Clock.System.now()
        val event = EnergyEvent(
            id = UUID.randomUUID().toString(),
            vehicleId = detection.vehicleId,
            storeId = detection.storeId,
            timestamp = r.at,
            odometerKm = odo,
            amount = litres,
            unitPriceAed = p?.aedPerLitre,
            totalAed = p?.let { CarFormat.round2(litres * it.aedPerLitre) },
            isFull = r.likelyFull,
            levelBefore = (r.levelBeforePercent / 100.0).coerceIn(0.0, 1.0),
            levelAfter = (r.levelAfterPercent / 100.0).coerceIn(0.0, 1.0),
            grade = detection.grade,
            // OFFICIAL_MONTH only when the official price was actually applied; otherwise the user must add it.
            priceSource = if (p != null) PriceSource.OFFICIAL_MONTH else PriceSource.USER,
            note = NOTE,
            updatedAt = now,
        )
        lifecycleScope.launch {
            val container = (carContext.applicationContext as SayyaraApp).container
            val ok = runCatching { container.events.upsert(event) }
                .onFailure { Log.e(TAG, "Saving detected refuel failed", it) }
                .isSuccess
            saving = false
            CarToast.makeText(
                carContext,
                if (ok) R.string.car_toast_saved else R.string.car_toast_save_failed,
                CarToast.LENGTH_LONG,
            ).show()
            if (ok) finish()
        }
    }

    private fun editOnPhone() {
        val posted = postDraftNotification(carContext, detection, price)
        CarToast.makeText(
            carContext,
            if (posted) R.string.car_toast_sent_to_phone else R.string.car_toast_notifications_off,
            CarToast.LENGTH_LONG,
        ).show()
        if (posted) finish()
    }

    companion object {
        private const val TAG = "RefuelDetectedScreen"
        const val NOTE = "auto-detected (car)"

        suspend fun lookupPrice(context: Context, detection: CarRefuelMonitor.Detection): FuelPrice? {
            val prices = (context.applicationContext as SayyaraApp).container.fuelPrices
            val month = CarFormat.monthOf(detection.refuel.at)
            return runCatching { prices.priceFor(month, detection.grade) ?: prices.latest(detection.grade) }.getOrNull()
        }

        fun summary(context: Context, detection: CarRefuelMonitor.Detection, price: FuelPrice?): String {
            val litres = CarFormat.litresApprox(detection.refuel.litresAdded)
            return if (price != null) {
                context.getString(
                    R.string.car_refuel_summary,
                    litres,
                    CarFormat.aedWhole(detection.refuel.litresAdded * price.aedPerLitre),
                    CarFormat.gradeLabel(context, detection.grade),
                    CarFormat.shortMonth(context, price.month),
                )
            } else {
                context.getString(R.string.car_refuel_summary_no_price, litres)
            }
        }

        /** The core draft link, plus the odometer from the mileage listener when the detector had none. */
        fun draftLink(detection: CarRefuelMonitor.Detection): String {
            val base = detection.refuel.toDraftDeepLink()
            val odo = detection.odometerKm
            return if (odo != null && !base.contains("odo=")) {
                base + (if (base.contains('?')) "&" else "?") + "odo=" + String.format(Locale.US, "%.0f", odo)
            } else base
        }

        fun postDraftNotification(context: Context, detection: CarRefuelMonitor.Detection, price: FuelPrice?): Boolean =
            CarNotifications.postDeepLink(
                context,
                CarNotifications.ID_REFUEL_DRAFT,
                title = context.getString(R.string.car_notification_refuel_title),
                text = summary(context, detection, price) + "\n" + context.getString(R.string.car_notification_refuel_text),
                deepLink = draftLink(detection),
            )
    }
}
