package ae.sayyara.app.ui.screens.expense

import ae.sayyara.app.R
import ae.sayyara.app.ui.appViewModel
import ae.sayyara.app.ui.common.UaeExpenseCategories
import ae.sayyara.app.ui.common.labelRes
import ae.sayyara.app.ui.components.CategoryIcon
import ae.sayyara.app.ui.components.DateField
import ae.sayyara.app.ui.components.NumberField
import ae.sayyara.app.ui.components.Overline
import ae.sayyara.app.ui.components.PillBadge
import ae.sayyara.app.ui.components.SwitchRow
import ae.sayyara.app.ui.components.TextInput
import ae.sayyara.app.ui.format.UiFormatter
import ae.sayyara.app.ui.format.rememberUiFormatter
import ae.sayyara.app.ui.settings.ThemeMode
import ae.sayyara.app.ui.theme.NestedShape
import ae.sayyara.app.ui.theme.SayyaraTheme
import ae.sayyara.app.ui.theme.color
import ae.sayyara.core.model.Emirate
import ae.sayyara.core.model.ExpenseCategoryKey
import ae.sayyara.core.model.Id
import ae.sayyara.core.uae.SmsDraft
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** Open the expense sheet: [expenseId] edits, [category] preselects (e.g. PARKING from a deep link). */
data class ExpenseSheetRequest(val expenseId: Id? = null, val category: ExpenseCategoryKey? = null, val nonce: Long = System.nanoTime())

/** Everything the expense form can ask of its owner; ExpenseViewModel implements it, previews fake it. */
interface ExpenseActions {
    fun onCategory(c: ExpenseCategoryKey)
    fun onAmount(t: String)
    fun onDate(d: LocalDate?)
    fun onOdometer(t: String)
    fun onTitle(t: String)
    fun onNote(t: String)
    fun onIncome(v: Boolean)
    fun onPlate(t: String)
    fun onEmirate(e: Emirate)
    fun onZone(t: String)
    fun onHours(h: Int)
    fun smsDraft(): SmsDraft?
    fun save()
    fun delete()
}

/** Emirates with an SMS parking service, and their short codes. */
private val SmsParkingEmirates = listOf(Emirate.ABU_DHABI to "3009", Emirate.DUBAI to "7275", Emirate.SHARJAH to "5566")

@Composable
fun AddExpenseSheet(request: ExpenseSheetRequest, onDismiss: () -> Unit) {
    val owner = remember(request) { object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() } }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val close: () -> Unit = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
            val vm = appViewModel { ExpenseViewModel(it.container, request.expenseId, request.category) }
            val state by vm.state.collectAsStateWithLifecycle()
            LaunchedEffect(state.done) { if (state.done) close() }
            ExpenseSheetContent(state, vm, onCancel = close)
        }
    }
}

@Composable
fun ExpenseSheetContent(state: ExpenseUiState, actions: ExpenseActions, onCancel: () -> Unit) {
    val fmt = rememberUiFormatter()
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            stringResource(if (state.isEdit) R.string.expense_edit_title else R.string.expense_add_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        when {
            state.loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            state.noVehicle -> {
                Text(stringResource(R.string.fill_no_vehicle))
                TextButton(onClick = onCancel) { Text(stringResource(R.string.action_close)) }
            }
            else -> ExpenseForm(state, actions, fmt, onCancel)
        }
    }
}

@Composable
private fun ColumnScope.ExpenseForm(state: ExpenseUiState, actions: ExpenseActions, fmt: UiFormatter, onCancel: () -> Unit) {
    // Category: icon chips in one scrollable row (thumb-reachable).
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        UaeExpenseCategories.forEach { c ->
            FilterChip(
                selected = state.category == c,
                onClick = { actions.onCategory(c) },
                label = { Text(stringResource(c.labelRes())) },
                leadingIcon = { CategoryIcon(c, size = 24.dp) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.color().copy(alpha = 0.18f)),
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }

    NumberField(
        value = state.amountText,
        onValueChange = actions::onAmount,
        label = stringResource(R.string.expense_amount),
        suffix = stringResource(R.string.currency_aed),
    )

    if (state.category == ExpenseCategoryKey.PARKING) ParkingSmsHelper(state, actions, fmt)

    DateField(stringResource(R.string.expense_date), state.date, actions::onDate, fmt)
    TextInput(
        state.title,
        actions::onTitle,
        stringResource(if (state.category == ExpenseCategoryKey.OTHER) R.string.expense_custom_category else R.string.expense_title),
    )
    NumberField(
        value = state.odometerText,
        onValueChange = actions::onOdometer,
        label = stringResource(R.string.expense_odometer_optional),
        suffix = stringResource(R.string.unit_km),
        decimal = false,
    )
    TextInput(state.note, actions::onNote, stringResource(R.string.expense_note), singleLine = false)
    SwitchRow(
        title = stringResource(R.string.expense_is_income),
        subtitle = stringResource(R.string.expense_is_income_hint),
        checked = state.isIncome,
        onCheckedChange = actions::onIncome,
    )
    Button(
        onClick = actions::save,
        enabled = state.canSave,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        val amount = state.amount
        Text(
            if (amount != null && amount > 0) stringResource(R.string.expense_save_with_total, fmt.aed(amount)) else stringResource(R.string.action_save),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
        )
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
        if (state.isEdit) {
            TextButton(onClick = actions::delete) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
        }
    }
}

/** Mawaqif (Abu Dhabi 3009), RTA (Dubai 7275), Sharjah (5566): compose the SMS for the user to send. */
@Composable
private fun ParkingSmsHelper(state: ExpenseUiState, actions: ExpenseActions, fmt: UiFormatter) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh, NestedShape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Overline(stringResource(R.string.parking_helper_title))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmsParkingEmirates.forEach { (e, code) ->
                FilterChip(
                    selected = state.emirate == e,
                    onClick = { actions.onEmirate(e) },
                    label = { Text(stringResource(e.labelRes()) + " · " + code) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextInput(state.plate, actions::onPlate, stringResource(R.string.vehicle_plate), Modifier.weight(1f))
            TextInput(state.zone, actions::onZone, stringResource(R.string.parking_zone), Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.parking_hours), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            FilledTonalIconButton(onClick = { actions.onHours(state.hours - 1) }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.action_decrease))
            }
            Text(fmt.integer(state.hours), style = MaterialTheme.typography.titleLarge)
            FilledTonalIconButton(onClick = { actions.onHours(state.hours + 1) }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_increase))
            }
        }
        state.parkingRate?.let { rate ->
            PillBadge(
                stringResource(R.string.parking_rate, fmt.aed(rate)) + " · ≈ " + fmt.aed(rate * state.hours),
                MaterialTheme.colorScheme.primary,
            )
        }
        val draft = actions.smsDraft()
        if (draft != null) {
            Surface(shape = NestedShape, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Text(
                    stringResource(R.string.parking_sms_preview, draft.number, draft.body),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                )
            }
        }
        FilledTonalButton(
            onClick = {
                if (draft == null) {
                    Toast.makeText(context, R.string.parking_sms_missing, Toast.LENGTH_SHORT).show()
                } else {
                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${draft.number}")).putExtra("sms_body", draft.body)
                    try {
                        context.startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(context, R.string.parking_sms_no_app, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            Icon(Icons.Filled.Sms, contentDescription = null)
            androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.parking_pay_sms))
        }
        Text(stringResource(R.string.parking_sms_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------- previews

private class PreviewExpenseActions(private val state: ExpenseUiState) : ExpenseActions {
    override fun onCategory(c: ExpenseCategoryKey) = Unit
    override fun onAmount(t: String) = Unit
    override fun onDate(d: LocalDate?) = Unit
    override fun onOdometer(t: String) = Unit
    override fun onTitle(t: String) = Unit
    override fun onNote(t: String) = Unit
    override fun onIncome(v: Boolean) = Unit
    override fun onPlate(t: String) = Unit
    override fun onEmirate(e: Emirate) = Unit
    override fun onZone(t: String) = Unit
    override fun onHours(h: Int) = Unit
    override fun smsDraft(): SmsDraft = SmsDraft(SmsParkingEmirates.first { it.first == state.emirate }.second, "${state.plate} ${state.zone} ${state.hours}")
    override fun save() = Unit
    override fun delete() = Unit
}

private val previewParking = ExpenseUiState(
    loading = false,
    category = ExpenseCategoryKey.PARKING,
    amountText = "6",
    date = LocalDate(2026, 11, 9),
    title = "Mawaqif Zone 365C",
    plate = "AD5",
    emirate = Emirate.ABU_DHABI,
    zone = "365C",
    hours = 2,
    parkingRate = 3.0,
)

@Composable
private fun ExpensePreview(dark: Boolean) {
    SayyaraTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            ExpenseSheetContent(previewParking, PreviewExpenseActions(previewParking), onCancel = {})
        }
    }
}

@Preview(name = "Light - EN", heightDp = 1400) @Composable private fun ExpenseLight() = ExpensePreview(false)
@Preview(name = "Dark - EN", heightDp = 1400) @Composable private fun ExpenseDark() = ExpensePreview(true)
@Preview(name = "Light - AR (RTL)", locale = "ar", heightDp = 1400) @Composable private fun ExpenseArabic() = ExpensePreview(false)
