package ae.sayyara.app.car

import ae.sayyara.app.R
import ae.sayyara.app.SayyaraApp
import ae.sayyara.core.model.FuelGrade
import ae.sayyara.core.model.FuelPrice
import ae.sayyara.core.model.PriceStatus
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * This month's UAE fuel prices (one national price per grade) plus the refuel auto-detect status.
 * Five rows at most, inside every host's list limit.
 */
class PricesScreen(
    carContext: CarContext,
    private val monitor: CarRefuelMonitor,
) : Screen(carContext) {

    private val grades = listOf(FuelGrade.SUPER_98, FuelGrade.SPECIAL_95, FuelGrade.E_PLUS_91, FuelGrade.DIESEL)
    private var prices: Map<FuelGrade, FuelPrice?>? = null
    private var state: CarRefuelMonitor.State = monitor.state.value

    init {
        lifecycleScope.launch {
            val repo = (carContext.applicationContext as SayyaraApp).container.fuelPrices
            val month = CarFormat.currentMonth()
            prices = grades.associateWith { g ->
                runCatching { repo.priceFor(month, g) ?: repo.latest(g) }.getOrNull()
            }
            invalidate()
        }
        lifecycleScope.launch {
            monitor.state.collectLatest {
                state = it
                invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template {
        val builder = ListTemplate.Builder()
            .setTitle(carContext.getString(R.string.car_prices_title, CarFormat.shortMonth(carContext, CarFormat.currentMonth())))
            .setHeaderAction(Action.BACK)
        val loaded = prices ?: return builder.setLoading(true).build()

        val list = ItemList.Builder()
        val current = CarFormat.currentMonth()
        grades.forEach { grade ->
            val p = loaded[grade]
            val row = Row.Builder().setTitle(CarFormat.gradeLabel(carContext, grade))
            if (p == null) {
                row.addText(carContext.getString(R.string.car_price_unknown))
            } else {
                row.addText(carContext.getString(R.string.car_price_per_litre, CarFormat.aedPerLitre(carContext, p.aedPerLitre)))
                when {
                    p.month != current -> row.addText(carContext.getString(R.string.car_price_last_known, CarFormat.shortMonth(carContext, p.month)))
                    p.status == PriceStatus.PROVISIONAL -> row.addText(carContext.getString(R.string.car_price_provisional))
                }
            }
            list.addItem(row.build())
        }
        list.addItem(statusRow())
        return builder.setSingleList(list.build()).build()
    }

    private fun statusRow(): Row {
        val row = Row.Builder().setTitle(carContext.getString(R.string.car_autodetect_title))
        when (val s = state) {
            CarRefuelMonitor.State.Starting -> row.addText(carContext.getString(R.string.car_autodetect_starting))
            is CarRefuelMonitor.State.Active -> row.addText(
                s.fuelPercent?.let { carContext.getString(R.string.car_autodetect_on_level, CarFormat.percent(it)) }
                    ?: carContext.getString(R.string.car_autodetect_on),
            )
            is CarRefuelMonitor.State.Unavailable -> {
                row.addText(carContext.getString(R.string.car_autodetect_off))
                row.setOnClickListener { screenManager.push(AutoDetectInfoScreen(carContext, s.reason)) }
            }
        }
        return row.build()
    }
}
