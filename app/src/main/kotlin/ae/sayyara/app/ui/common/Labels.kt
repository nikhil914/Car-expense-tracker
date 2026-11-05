package ae.sayyara.app.ui.common

import ae.sayyara.app.R
import ae.sayyara.app.ui.format.UiFormatter
import ae.sayyara.core.consumption.EconomyUnit
import ae.sayyara.core.consumption.Units
import ae.sayyara.core.model.Emirate
import ae.sayyara.core.model.EnergyStoreKind
import ae.sayyara.core.model.ExpenseCategoryKey
import ae.sayyara.core.model.FuelGrade
import ae.sayyara.core.model.ReminderKind
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

@StringRes
fun FuelGrade.labelRes(): Int = when (this) {
    FuelGrade.SUPER_98 -> R.string.grade_super_98
    FuelGrade.SPECIAL_95 -> R.string.grade_special_95
    FuelGrade.E_PLUS_91 -> R.string.grade_e_plus_91
    FuelGrade.DIESEL -> R.string.grade_diesel
    FuelGrade.ELECTRIC -> R.string.grade_electric
    FuelGrade.LPG -> R.string.grade_lpg
    FuelGrade.CNG -> R.string.grade_cng
    FuelGrade.OTHER -> R.string.grade_other
}

@StringRes
fun Emirate.labelRes(): Int = when (this) {
    Emirate.DUBAI -> R.string.emirate_dubai
    Emirate.ABU_DHABI -> R.string.emirate_abu_dhabi
    Emirate.SHARJAH -> R.string.emirate_sharjah
    Emirate.AJMAN -> R.string.emirate_ajman
    Emirate.UMM_AL_QUWAIN -> R.string.emirate_uaq
    Emirate.RAS_AL_KHAIMAH -> R.string.emirate_rak
    Emirate.FUJAIRAH -> R.string.emirate_fujairah
}

@StringRes
fun ExpenseCategoryKey.labelRes(): Int = when (this) {
    ExpenseCategoryKey.FUEL -> R.string.category_fuel
    ExpenseCategoryKey.SALIK -> R.string.category_salik
    ExpenseCategoryKey.DARB -> R.string.category_darb
    ExpenseCategoryKey.PARKING -> R.string.category_parking
    ExpenseCategoryKey.MULKIYA -> R.string.category_mulkiya
    ExpenseCategoryKey.INSPECTION -> R.string.category_inspection
    ExpenseCategoryKey.INSURANCE -> R.string.category_insurance
    ExpenseCategoryKey.SERVICE -> R.string.category_service
    ExpenseCategoryKey.TYRES -> R.string.category_tyres
    ExpenseCategoryKey.FINE -> R.string.category_fine
    ExpenseCategoryKey.WASH -> R.string.category_wash
    ExpenseCategoryKey.OTHER -> R.string.category_other
}

@StringRes
fun ReminderKind.labelRes(): Int = when (this) {
    ReminderKind.MULKIYA -> R.string.reminder_kind_mulkiya
    ReminderKind.INSPECTION -> R.string.reminder_kind_inspection
    ReminderKind.INSURANCE -> R.string.reminder_kind_insurance
    ReminderKind.SERVICE -> R.string.reminder_kind_service
    ReminderKind.OIL -> R.string.reminder_kind_oil
    ReminderKind.TYRES -> R.string.reminder_kind_tyres
    ReminderKind.CUSTOM -> R.string.reminder_kind_custom
}

/** Expense categories offered in the UI (fuel is logged through the fill-up sheet). */
val UaeExpenseCategories = listOf(
    ExpenseCategoryKey.SALIK, ExpenseCategoryKey.DARB, ExpenseCategoryKey.PARKING,
    ExpenseCategoryKey.MULKIYA, ExpenseCategoryKey.INSPECTION, ExpenseCategoryKey.INSURANCE,
    ExpenseCategoryKey.SERVICE, ExpenseCategoryKey.TYRES, ExpenseCategoryKey.FINE,
    ExpenseCategoryKey.WASH, ExpenseCategoryKey.OTHER,
)

/** Maps the user's liquid-fuel unit preference onto the matching electric unit for batteries. */
fun EconomyUnit.forStore(kind: EnergyStoreKind?): EconomyUnit = when {
    kind != EnergyStoreKind.BATTERY -> this
    this == EconomyUnit.L_PER_100KM -> EconomyUnit.KWH_PER_100KM
    this == EconomyUnit.KM_PER_L -> EconomyUnit.KM_PER_KWH
    else -> this
}

@StringRes
fun EconomyUnit.labelRes(): Int = when (this) {
    EconomyUnit.L_PER_100KM -> R.string.unit_l_per_100km
    EconomyUnit.KM_PER_L -> R.string.unit_km_per_l
    EconomyUnit.MPG_US -> R.string.unit_mpg_us
    EconomyUnit.MPG_UK -> R.string.unit_mpg_uk
    EconomyUnit.KWH_PER_100KM -> R.string.unit_kwh_per_100km
    EconomyUnit.KM_PER_KWH -> R.string.unit_km_per_kwh
}

/** Converts an internal per-100 km figure to the display unit. */
fun economyValue(per100km: Double, unit: EconomyUnit): Double = Units.fromPer100km(per100km, unit)

/** "12.4 km/L" in the current locale. */
@Composable
fun economyText(per100km: Double?, unit: EconomyUnit, fmt: UiFormatter): String {
    if (per100km == null || per100km <= 0.0) return stringResource(R.string.value_missing)
    return stringResource(
        R.string.economy_value,
        fmt.number(economyValue(per100km, unit), 1),
        stringResource(unit.labelRes()),
    )
}
