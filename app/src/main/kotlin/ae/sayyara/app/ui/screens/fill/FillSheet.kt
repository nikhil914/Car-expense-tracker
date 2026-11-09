@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package ae.sayyara.app.ui.screens.fill

import ae.sayyara.app.ui.components.PillBadge
import ae.sayyara.app.ui.theme.badgeColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
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
import ae.sayyara.core.consumption.ValidationWarning
import ae.sayyara.core.consumption.WarningCodes
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
    var moreDetails by rememberSaveable { mutableStateOf(state.showLevels || state.missedPrevious) }

    // Header: vehicle + car-detected badge + scan receipt.
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(state.vehicleName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            if (state.fromCar) {
                PillBadge("✨ " + stringResource(R.string.fill_from_car), MaterialTheme.colorScheme.secondary, Modifier.padding(top = 4.dp))
            }
        }
        if (scanner != null) {
            FilledTonalIconButton(onClick = { scanner.scan(vm::onReceipt) }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.CameraAlt, contentDescription = stringResource(R.string.fill_scan_receipt))
            }
        }
    }
    if (state.scanFailed) {
        Text(stringResource(R.string.fill_scan_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }

    // Tap 1: amount (AED first: UAE drivers pay round amounts at the pump).
    val unitLabel = stringResource(if (state.isElectric) R.string.unit_kwh else R.string.unit_litres)
    val unitShort = stringResource(if (state.isElectric) R.string.unit_kwh else R.string.unit_litre)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        val modes = listOf(AmountMode.AED, AmountMode.LITRES)
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
            suffix = unitShort,
            modifier = Modifier.focusRequester(amountFocus),
            imeAction = ImeAction.Done,
        )
        AmountMode.AED -> {
            NumberField(
                value = state.totalText,
                onValueChange = vm::onTotal,
                label = stringResource(R.string.fill_total_label),
                suffix = stringResource(R.string.currency_aed),
                modifier = Modifier.focusRequester(amountFocus),
                imeAction = ImeAction.Done,
            )
            // Quick pump denominations: no keyboard needed for the common AED 50 / 100 fill.
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(50, 100, 150, 200).forEach { aed ->
                    FilterChip(
                        selected = state.totalText.trim() == aed.toString(),
                        onClick = { vm.onTotal(aed.toString()) },
                        label = { Text(stringResource(R.string.fill_quick_aed, aed)) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
        }
    }
    // Live computed counterpart.
    val computed = when (state.amountMode) {
        AmountMode.AED -> state.amount?.let { "≈ ${fmt.number(it, 2)} $unitShort" }
        AmountMode.LITRES -> state.total?.let { "≈ ${fmt.aed(it)}" }
    }
    val atPrice = state.unitPrice?.let { stringResource(R.string.fill_at_price, fmt.aed(it), unitShort) }
    if (computed != null || atPrice != null) {
        PillBadge(listOfNotNull(computed, atPrice).joinToString(" "), MaterialTheme.colorScheme.primary)
    }
    AnimatedVisibility(visible = state.suggestPartial, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        AssistChip(
            onClick = vm::acceptPartial,
            label = { Text(stringResource(R.string.fill_suggest_partial)) },
            leadingIcon = { Icon(Icons.Filled.Lightbulb, contentDescription = null, Modifier.size(18.dp)) },
        )
    }
    LaunchedEffect(state.amountMode) {
        if (!state.isEdit) runCatching { amountFocus.requestFocus() }
    }

    // Tap 2: odometer, prefilled, with quick steppers.
    val odo = state.odometer
    NumberField(
        value = state.odometerText,
        onValueChange = vm::onOdometer,
        label = stringResource(R.string.fill_odometer),
        suffix = stringResource(R.string.unit_km),
        supportingText = when {
            state.lastOdometerKm != null && odo != null ->
                stringResource(R.string.fill_odometer_last_delta, fmt.integer(state.lastOdometerKm), fmt.integer(odo - state.lastOdometerKm))
            state.lastOdometerKm != null -> stringResource(R.string.fill_odometer_last, fmt.integer(state.lastOdometerKm))
            state.odometerEstimated -> stringResource(R.string.fill_odometer_estimated)
            else -> null
        },
    )
    val base = odo ?: state.lastOdometerKm
    if (base != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(100, 400).forEach { step ->
                AssistChip(
                    onClick = { vm.onOdometer((base + step).roundToInt().toString()) },
                    label = { Text(stringResource(R.string.fill_odo_step, step)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }

    // Prefilled defaults: grade and station chips, pump-coded.
    if (state.grades.size > 1) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.grades.forEach { g ->
                FilterChip(
                    selected = state.grade == g,
                    onClick = { vm.onGrade(g) },
                    label = { Text(stringResource(g.labelRes())) },
                    leadingIcon = { Box(Modifier.size(10.dp).background(g.badgeColor(), CircleShape)) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = g.badgeColor().copy(alpha = 0.18f)),
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
    if (!state.isElectric) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StationBrands.forEach { brand ->
                FilterChip(
                    selected = state.stationBrand == brand,
                    onClick = { vm.onStation(brand) },
                    label = { Text(brand) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
    SwitchRow(
        title = stringResource(R.string.fill_full_tank),
        subtitle = stringResource(R.string.fill_full_tank_hint),
        checked = state.isFull,
        onCheckedChange = vm::onFull,
    )

    // Collapsible details keep the primary sheet short.
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(MaterialTheme.shapes.medium).clickable { moreDetails = !moreDetails }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.fill_more_details), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Icon(if (moreDetails) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
    }
    AnimatedVisibility(visible = moreDetails) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
        }
    }

    WarningList(state.warnings.map { warningText(it) })

    // Tap 3: save.
    Button(
        onClick = vm::save,
        enabled = state.canSave,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        val total = state.total
        Text(
            if (total != null) stringResource(R.string.fill_save_with_total, fmt.aed(total)) else stringResource(R.string.fill_save),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
        )
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

/** Localized text for engine warning codes; the engine's English message is the fallback. */
@Composable
private fun warningText(w: ValidationWarning): String = when (w.code) {
    WarningCodes.ODO_NOT_INCREASING -> stringResource(R.string.warning_odo_not_increasing)
    WarningCodes.ODO_DATE_MISMATCH -> stringResource(R.string.warning_odo_date_mismatch)
    WarningCodes.IMPLAUSIBLE_CONSUMPTION -> stringResource(R.string.warning_implausible_consumption)
    WarningCodes.POSSIBLE_MISSED_FILL -> stringResource(R.string.warning_possible_missed_fill)
    WarningCodes.GAUGE_RECALIBRATE -> stringResource(R.string.warning_gauge_recalibrate)
    WarningCodes.AMOUNT_NOT_POSITIVE -> stringResource(R.string.warning_amount_not_positive)
    WarningCodes.AMOUNT_EXCEEDS_CAPACITY -> stringResource(R.string.warning_amount_exceeds_capacity)
    WarningCodes.IMPLAUSIBLE_PRICE -> stringResource(R.string.warning_implausible_price)
    WarningCodes.TOTAL_MISMATCH -> stringResource(R.string.warning_total_mismatch)
    WarningCodes.LEVEL_OUT_OF_RANGE -> stringResource(R.string.warning_level_out_of_range)
    else -> w.message
}
