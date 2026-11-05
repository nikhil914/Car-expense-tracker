@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package ae.sayyara.app.ui.screens.fill

import ae.sayyara.app.R
import ae.sayyara.app.ui.LocalAppDeps
import ae.sayyara.app.ui.appViewModel
import ae.sayyara.app.ui.common.labelRes
import ae.sayyara.app.ui.components.ChoiceChips
import ae.sayyara.app.ui.components.NumberField
import ae.sayyara.app.ui.components.SwitchRow
import ae.sayyara.app.ui.components.WarningList
import ae.sayyara.app.ui.format.rememberUiFormatter
import ae.sayyara.app.ui.navigation.FillDraft
import ae.sayyara.core.model.Id
import ae.sayyara.core.model.PriceSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** What the fill-up sheet should open with. [nonce] makes each request a fresh sheet. */
data class FillSheetRequest(val eventId: Id? = null, val draft: FillDraft? = null, val nonce: Long = System.nanoTime())

/**
 * Add / edit fill-up bottom sheet. The common path is 3 taps: FAB, type the amount, Save
 * (odometer, grade and the official monthly price are prefilled; full tank defaults to on).
 */
@Composable
fun FillSheet(request: FillSheetRequest, onDismiss: () -> Unit) {
    // A ViewModelStore scoped to this sheet instance, cleared when the sheet leaves composition.
    val owner = remember(request) {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val close: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
            val vm = appViewModel { FillViewModel(it.container, request.eventId, request.draft) }
            val state by vm.state.collectAsStateWithLifecycle()
            LaunchedEffect(state.done) { if (state.done) close() }
            FillSheetContent(state, vm, onCancel = close)
        }
    }
}

@Composable
private fun FillSheetContent(state: FillUiState, vm: FillViewModel, onCancel: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(if (state.isEdit) R.string.fill_title_edit else R.string.fill_title_add),
            style = MaterialTheme.typography.headlineSmall,
        )
        when {
            state.loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            state.noVehicle -> {
                Text(stringResource(R.string.fill_no_vehicle), style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = onCancel) { Text(stringResource(R.string.action_close)) }
            }
            else -> FillForm(state, vm, onCancel)
        }
    }
}

@Composable
private fun ColumnScope.FillForm(state: FillUiState, vm: FillViewModel, onCancel: () -> Unit) {
    val fmt = rememberUiFormatter()
    val scanner = LocalAppDeps.current.receiptScanner
    val amountFocus = remember { FocusRequester() }
    Text(
        state.vehicleName,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (state.fromCar) {
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.DirectionsCar, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.fill_from_car), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    // Amount: litres OR AED, the other one is computed.
    val unitLabel = stringResource(if (state.isElectric) R.string.unit_kwh else R.string.unit_litres)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        val modes = AmountMode.entries
        modes.forEachIndexed { i, mode ->
            SegmentedButton(
                selected = state.amountMode == mode,
                onClick = { vm.onAmountMode(mode) },
                shape = SegmentedButtonDefaults.itemShape(i, modes.size),
            ) {
                Text(
                    when (mode) {
                        AmountMode.LITRES -> stringResource(R.string.fill_mode_by_amount, unitLabel)
                        AmountMode.AED -> stringResource(R.string.fill_mode_by_aed)
                    },
                )
            }
        }
    }
    when (state.amountMode) {
        AmountMode.LITRES -> NumberField(
            value = state.litresText,
            onValueChange = vm::onLitres,
            label = stringResource(R.string.fill_amount_label, unitLabel),
            suffix = unitLabel,
            modifier = Modifier.focusRequester(amountFocus),
            supportingText = state.total?.let { stringResource(R.string.fill_computed_total, fmt.aed(it)) },
            imeAction = ImeAction.Done,
        )
        AmountMode.AED -> NumberField(
            value = state.totalText,
            onValueChange = vm::onTotal,
            label = stringResource(R.string.fill_total_label),
            suffix = stringResource(R.string.currency_aed),
            modifier = Modifier.focusRequester(amountFocus),
            supportingText = state.amount?.let {
                stringResource(R.string.fill_computed_amount, fmt.number(it, 2), unitLabel)
            },
            imeAction = ImeAction.Done,
        )
    }
    LaunchedEffect(state.amountMode) {
        if (!state.isEdit) runCatching { amountFocus.requestFocus() }
    }

    NumberField(
        value = state.odometerText,
        onValueChange = vm::onOdometer,
        label = stringResource(R.string.fill_odometer),
        suffix = stringResource(R.string.unit_km),
        supportingText = when {
            state.odometerEstimated -> stringResource(R.string.fill_odometer_estimated)
            state.lastOdometerKm != null -> stringResource(R.string.fill_odometer_last, fmt.integer(state.lastOdometerKm))
            else -> null
        },
    )

    NumberField(
        value = state.unitPriceText,
        onValueChange = vm::onUnitPrice,
        label = stringResource(if (state.isElectric) R.string.fill_price_per_kwh else R.string.fill_price_per_litre),
        suffix = stringResource(R.string.currency_aed),
        supportingText = priceSupport(state, fmt),
    )
    if (state.officialPrice != null && state.priceSource != PriceSource.OFFICIAL_MONTH) {
        TextButton(onClick = vm::useOfficialPrice) {
            Text(stringResource(R.string.fill_use_official_price, fmt.aed(state.officialPrice)))
        }
    }

    if (state.grades.size > 1) {
        ChoiceChips(
            options = state.grades,
            selected = state.grade,
            onSelect = vm::onGrade,
            label = { stringResource(it.labelRes()) },
        )
    }

    SwitchRow(
        title = stringResource(R.string.fill_full_tank),
        subtitle = stringResource(R.string.fill_full_tank_hint),
        checked = state.isFull,
        onCheckedChange = vm::onFull,
    )
    if (state.suggestPartial) {
        AssistChip(
            onClick = vm::acceptPartial,
            label = { Text(stringResource(R.string.fill_suggest_partial)) },
            leadingIcon = { Icon(Icons.Filled.Lightbulb, contentDescription = null, Modifier.size(18.dp)) },
        )
    }
    SwitchRow(
        title = stringResource(R.string.fill_missed_previous),
        subtitle = stringResource(R.string.fill_missed_previous_hint),
        checked = state.missedPrevious,
        onCheckedChange = vm::onMissedPrevious,
    )

    SwitchRow(
        title = stringResource(if (state.isElectric) R.string.fill_battery_level else R.string.fill_tank_level),
        checked = state.showLevels,
        onCheckedChange = vm::onShowLevels,
    )
    if (state.showLevels) {
        LevelSlider(stringResource(R.string.fill_level_before), state.levelBefore, vm::onLevelBefore)
        LevelSlider(stringResource(R.string.fill_level_after), state.levelAfter, vm::onLevelAfter)
    }

    if (!state.isElectric) {
        Text(stringResource(R.string.fill_station), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StationBrands.forEach { brand ->
                FilterChip(
                    selected = state.stationBrand == brand,
                    onClick = { vm.onStation(brand) },
                    label = { Text(brand) },
                )
            }
        }
    }

    if (scanner != null) {
        OutlinedButton(onClick = { scanner.scan(vm::onReceipt) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.CameraAlt, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.fill_scan_receipt))
        }
        if (state.scanFailed) {
            Text(
                stringResource(R.string.fill_scan_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    WarningList(state.warnings.map { it.message })

    Button(
        onClick = vm::save,
        enabled = state.canSave,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Text(stringResource(R.string.action_save))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
        if (state.isEdit) {
            TextButton(onClick = vm::delete) {
                Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun priceSupport(state: FillUiState, fmt: ae.sayyara.app.ui.format.UiFormatter): String? {
    val month = state.priceMonth
    return when (state.priceSource) {
        PriceSource.OFFICIAL_MONTH -> month?.let { stringResource(R.string.fill_price_official, fmt.monthLong(it)) }
        PriceSource.RECEIPT_OCR -> stringResource(R.string.fill_price_receipt)
        PriceSource.IMPORT -> null
        PriceSource.USER -> if (state.officialPrice == null && !state.isElectric) {
            stringResource(R.string.fill_price_unknown)
        } else {
            null
        }
    }
}

@Composable
private fun LevelSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.percent_value, (value * 100).roundToInt()), style = MaterialTheme.typography.bodyMedium)
        }
        Slider(value = value, onValueChange = onChange, valueRange = 0f..1f, steps = 19)
    }
}
