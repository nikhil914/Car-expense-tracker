package ae.sayyara.automotive

import ae.sayyara.core.model.FuelGrade
import ae.sayyara.core.model.YearMonth
import android.content.Context
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Screen with its own main-thread coroutine scope, cancelled on destroy. The automotive module does not pull in
 * lifecycle-runtime-ktx, so it does not rely on `lifecycleScope`.
 */
abstract class ScopedScreen(carContext: CarContext) : Screen(carContext) {
    protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) = scope.cancel()
        })
    }
}

internal object AaosFormat {
    fun locale(context: Context): Locale = context.resources.configuration.locales[0] ?: Locale.getDefault()

    fun shortMonth(context: Context, ym: YearMonth): String =
        Month.of(ym.month).getDisplayName(TextStyle.SHORT, locale(context))

    fun aed2(context: Context, v: Double): String = String.format(locale(context), "%.2f", v)
    fun whole(v: Double): String = Math.round(v).toString()
    fun km(context: Context, v: Double): String = String.format(locale(context), "%,.0f", v)

    fun gradeLabel(context: Context, grade: FuelGrade): String = context.getString(
        when (grade) {
            FuelGrade.SUPER_98 -> R.string.aaos_grade_super_98
            FuelGrade.SPECIAL_95 -> R.string.aaos_grade_special_95
            FuelGrade.E_PLUS_91 -> R.string.aaos_grade_e_plus_91
            FuelGrade.DIESEL -> R.string.aaos_grade_diesel
            else -> R.string.aaos_grade_other
        },
    )

    fun triggerLabel(context: Context, trigger: String): String = context.getString(
        when (trigger) {
            "fuel_door" -> R.string.aaos_trigger_fuel_door
            "charge_port" -> R.string.aaos_trigger_charge_port
            "ignition" -> R.string.aaos_trigger_ignition
            "gap" -> R.string.aaos_trigger_gap
            else -> R.string.aaos_trigger_level
        },
    )

    val petrolGrades = listOf(FuelGrade.SUPER_98, FuelGrade.SPECIAL_95, FuelGrade.E_PLUS_91, FuelGrade.DIESEL)
}
