package ae.sayyara.automotive

import android.text.format.DateFormat
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.OnRequestPermissionsListener
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.versioning.CarAppApiLevels
import java.util.Date
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Detected refuels / charges waiting for confirmation, with the auto-detect status on top. */
class PendingRefuelsScreen(
    carContext: CarContext,
    private val monitor: AaosRefuelMonitor,
) : ScopedScreen(carContext) {

    private var items: List<PendingRefuel>? = null
    private var status: AaosRefuelMonitor.Status = monitor.status.value

    init {
        val store = AaosServices.store(carContext)
        scope.launch {
            store.version.collectLatest {
                items = withContext(Dispatchers.IO) { store.pending() }
                invalidate()
            }
        }
        scope.launch {
            monitor.status.drop(1).collectLatest {
                status = it
                invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template {
        val builder = ListTemplate.Builder()
            .setTitle(carContext.getString(R.string.aaos_pending_title))
            .setHeaderAction(Action.BACK)
        val loaded = items ?: return builder.setLoading(true).build()
        val list = ItemList.Builder()
        list.addItem(statusRow())
        if (loaded.isEmpty()) {
            list.addItem(Row.Builder().setTitle(carContext.getString(R.string.aaos_pending_empty)).build())
        }
        loaded.take(MAX_ROWS - 1).forEach { item ->
            list.addItem(
                Row.Builder()
                    .setTitle(titleOf(carContext, item))
                    .addText(
                        DateFormat.getMediumDateFormat(carContext).format(Date(item.atEpochMs)) + " " +
                            DateFormat.getTimeFormat(carContext).format(Date(item.atEpochMs)) + " · " +
                            AaosFormat.triggerLabel(carContext, item.trigger),
                    )
                    .setOnClickListener { screenManager.push(ConfirmRefuelScreen(carContext, item.id)) }
                    .build(),
            )
        }
        return builder.setSingleList(list.build()).build()
    }

    private fun statusRow(): Row {
        val row = Row.Builder().setTitle(carContext.getString(R.string.aaos_status_title))
        when (val s = status) {
            AaosRefuelMonitor.Status.Connecting -> row.addText(carContext.getString(R.string.aaos_status_connecting))
            AaosRefuelMonitor.Status.CarServiceUnavailable -> row.addText(carContext.getString(R.string.aaos_status_no_car_service))
            AaosRefuelMonitor.Status.NoEnergyData -> row.addText(carContext.getString(R.string.aaos_status_no_data))
            is AaosRefuelMonitor.Status.NeedsPermission -> {
                row.addText(carContext.getString(R.string.aaos_status_needs_permission))
                row.setOnClickListener { screenManager.push(PermissionScreen(carContext, monitor)) }
            }
            is AaosRefuelMonitor.Status.Running -> {
                val parts = buildList {
                    s.fuelLitres?.let { add(carContext.getString(R.string.aaos_status_fuel_litres, it.roundToInt())) }
                    s.socPercent?.let { add(carContext.getString(R.string.aaos_status_soc, it.roundToInt())) }
                    s.rangeKm?.let { add(carContext.getString(R.string.aaos_status_range, it.roundToInt())) }
                }
                row.addText(carContext.getString(if (s.caps.fuelDoor) R.string.aaos_status_on_door else R.string.aaos_status_on))
                if (parts.isNotEmpty()) row.addText(parts.joinToString(" · "))
            }
        }
        return row.build()
    }

    companion object {
        private const val MAX_ROWS = 6

        fun titleOf(context: CarContext, item: PendingRefuel): String = when (item.kind) {
            PendingRefuel.Kind.FUEL -> context.getString(R.string.aaos_item_fuel, (item.amount ?: 0.0).roundToInt())
            PendingRefuel.Kind.CHARGE -> item.amount?.let { context.getString(R.string.aaos_item_charge_kwh, it.roundToInt()) }
                ?: context.getString(R.string.aaos_item_charge_soc, item.levelBeforePercent.roundToInt(), item.levelAfterPercent.roundToInt())
        }
    }
}

/**
 * Confirm or dismiss one detection. Fuel is priced with the car's grade (Prices screen) at the official UAE price of
 * the refuel's month from core FuelPriceBook. Confirmed entries stay in the local JSON store until the Phase 2
 * cloud sync hands them to the phone app.
 */
class ConfirmRefuelScreen(
    carContext: CarContext,
    private val id: String,
) : ScopedScreen(carContext) {

    private var item: PendingRefuel? = null
    private var loaded = false

    init {
        scope.launch {
            item = withContext(Dispatchers.IO) { AaosServices.store(carContext).get(id) }
            loaded = true
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val title = carContext.getString(R.string.aaos_confirm_title)
        if (!loaded) {
            return PaneTemplate.Builder(Pane.Builder().setLoading(true).build()).setTitle(title).setHeaderAction(Action.BACK).build()
        }
        val p = item ?: return MessageTemplate.Builder(carContext.getString(R.string.aaos_confirm_gone))
            .setTitle(title).setHeaderAction(Action.BACK).build()

        val grade = AaosServices.prefs(carContext).grade
        val quote = if (p.kind == PendingRefuel.Kind.FUEL) AaosServices.prices.quote(AaosServices.monthOf(p.atEpochMs), grade) else null
        val pane = Pane.Builder()
        val summary = when {
            p.kind == PendingRefuel.Kind.FUEL && quote != null && p.amount != null -> carContext.getString(
                R.string.aaos_confirm_fuel_priced,
                p.amount.roundToInt(),
                AaosFormat.whole(p.amount * quote.aedPerLitre),
                AaosFormat.gradeLabel(carContext, grade),
                AaosFormat.shortMonth(carContext, quote.month),
            )
            else -> PendingRefuelsScreen.titleOf(carContext, p)
        }
        pane.addRow(Row.Builder().setTitle(summary).build())
        pane.addRow(
            Row.Builder()
                .setTitle(carContext.getString(R.string.aaos_confirm_levels, p.levelBeforePercent.roundToInt(), p.levelAfterPercent.roundToInt()))
                .addText(AaosFormat.triggerLabel(carContext, p.trigger) + if (p.likelyFull) " · " + carContext.getString(R.string.aaos_confirm_full) else "")
                .build(),
        )
        pane.addRow(
            Row.Builder()
                .setTitle(
                    p.odometerKm?.let { carContext.getString(R.string.aaos_confirm_odo, AaosFormat.km(carContext, it)) }
                        ?: carContext.getString(R.string.aaos_confirm_odo_unknown),
                )
                .addText(carContext.getString(R.string.aaos_confirm_sync_note))
                .build(),
        )
        val confirm = Action.Builder()
            .setTitle(carContext.getString(R.string.aaos_action_confirm))
            .setOnClickListener { decide(p, PendingRefuel.Status.CONFIRMED, grade, quote?.aedPerLitre) }
        if (carContext.carAppApiLevel >= CarAppApiLevels.LEVEL_4) confirm.setFlags(Action.FLAG_PRIMARY)
        pane.addAction(confirm.build())
        pane.addAction(
            Action.Builder()
                .setTitle(carContext.getString(R.string.aaos_action_dismiss))
                .setOnClickListener { decide(p, PendingRefuel.Status.DISMISSED, null, null) }
                .build(),
        )
        val template = PaneTemplate.Builder(pane.build()).setTitle(title).setHeaderAction(Action.BACK)
        if (p.kind == PendingRefuel.Kind.FUEL) {
            // Quick grade switch without leaving the screen (cycles Super 98 -> Special 95 -> E-Plus 91 -> Diesel).
            template.setActionStrip(
                ActionStrip.Builder().addAction(
                    Action.Builder()
                        .setTitle(AaosFormat.gradeLabel(carContext, grade))
                        .setOnClickListener {
                            val g = AaosFormat.petrolGrades
                            val prefs = AaosServices.prefs(carContext)
                            prefs.grade = g[(g.indexOf(prefs.grade) + 1).mod(g.size)]
                            invalidate()
                        }
                        .build(),
                ).build(),
            )
        }
        return template.build()
    }

    private fun decide(p: PendingRefuel, status: PendingRefuel.Status, grade: ae.sayyara.core.model.FuelGrade?, unitPrice: Double?) {
        scope.launch {
            withContext(Dispatchers.IO) {
                AaosServices.store(carContext).update(p.id) {
                    it.copy(
                        status = status,
                        grade = grade ?: it.grade,
                        unitPriceAed = unitPrice,
                        totalAed = if (unitPrice != null && it.amount != null) Math.round(it.amount * unitPrice * 100.0) / 100.0 else null,
                        decidedAtEpochMs = System.currentTimeMillis(),
                    )
                }
            }
            CarToast.makeText(
                carContext,
                if (status == PendingRefuel.Status.CONFIRMED) R.string.aaos_toast_confirmed else R.string.aaos_toast_dismissed,
                CarToast.LENGTH_SHORT,
            ).show()
            finish()
        }
    }
}

/**
 * Runtime permission flow. CarContext.requestPermissions shows the platform prompt through the library's
 * permission activity, which the system allows only while parked. Only CAR_ENERGY is required; speed, mileage,
 * location and notifications make detection better.
 */
class PermissionScreen(
    carContext: CarContext,
    private val monitor: AaosRefuelMonitor,
) : ScopedScreen(carContext) {
    override fun onGetTemplate(): Template =
        MessageTemplate.Builder(carContext.getString(R.string.aaos_permission_message))
            .setTitle(carContext.getString(R.string.aaos_permission_title))
            .setHeaderAction(Action.BACK)
            .addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.aaos_action_allow))
                    .setOnClickListener { request() }
                    .build(),
            )
            .addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.aaos_action_not_now))
                    .setOnClickListener {
                        AaosServices.prefs(carContext).permissionPromptShown = true
                        finish()
                    }
                    .build(),
            )
            .build()

    private fun request() {
        val missing = AaosPermissions.missingRuntime(carContext) + locationIfMissing()
        AaosServices.prefs(carContext).permissionPromptShown = true
        if (missing.isEmpty()) {
            finish()
            return
        }
        try {
            carContext.requestPermissions(missing, OnRequestPermissionsListener { granted, _ ->
                if (granted.isNotEmpty()) monitor.restart()
                if (!AaosPermissions.canDetect(carContext)) {
                    CarToast.makeText(carContext, R.string.aaos_toast_permission_denied, CarToast.LENGTH_LONG).show()
                }
                finish()
            })
        } catch (e: Exception) {
            // Host too old for in-car permission requests: the user must grant them in Settings > Apps.
            CarToast.makeText(carContext, R.string.aaos_toast_permission_settings, CarToast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun locationIfMissing(): List<String> =
        if (AaosStationsScreen.hasLocation(carContext)) emptyList()
        else listOf(android.Manifest.permission.ACCESS_COARSE_LOCATION, android.Manifest.permission.ACCESS_FINE_LOCATION)
}
