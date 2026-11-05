@file:OptIn(ExperimentalMaterial3Api::class)

package ae.sayyara.app.ui.screens.expense

import ae.sayyara.app.R
import ae.sayyara.app.ui.appViewModel
import ae.sayyara.app.ui.common.UaeExpenseCategories
import ae.sayyara.app.ui.common.labelRes
import ae.sayyara.app.ui.components.BackTopBar
import ae.sayyara.app.ui.components.ChoiceChips
import ae.sayyara.app.ui.components.DateField
import ae.sayyara.app.ui.components.NumberField
import ae.sayyara.app.ui.components.SectionCard
import ae.sayyara.app.ui.components.SwitchRow
import ae.sayyara.app.ui.components.TextInput
import ae.sayyara.app.ui.format.rememberUiFormatter
import ae.sayyara.core.model.Emirate
import ae.sayyara.core.model.ExpenseCategoryKey
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ExpenseScreen(expenseId: String?, category: String?, onBack: () -> Unit) {
    val initial = ExpenseCategoryKey.entries.firstOrNull { it.name == category }
    val vm = appViewModel { ExpenseViewModel(it.container, expenseId, initial) }
    val state by vm.state.collectAsStateWithLifecycle()
    val fmt = rememberUiFormatter()

    LaunchedEffect(state.done) { if (state.done) onBack() }

    Scaffold(
        topBar = {
            BackTopBar(
                title = stringResource(if (state.isEdit) R.string.expense_edit_title else R.string.expense_add_title),
                onBack = onBack,
                actions = {
                    if (state.isEdit) {
                        IconButton(onClick = vm::delete) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.noVehicle) {
                Text(stringResource(R.string.fill_no_vehicle))
            } else {
                ExpenseForm(state, vm, fmt)
            }
        }
    }
}

@Composable
private fun ExpenseForm(state: ExpenseUiState, vm: ExpenseViewModel, fmt: ae.sayyara.app.ui.format.UiFormatter) {
    val context = LocalContext.current
    Text(stringResource(R.string.expense_category), style = MaterialTheme.typography.labelLarge)
    ChoiceChips(
        options = UaeExpenseCategories,
        selected = state.category,
        onSelect = vm::onCategory,
        label = { stringResource(it.labelRes()) },
    )

    if (state.category == ExpenseCategoryKey.PARKING) {
        SectionCard(title = stringResource(R.string.parking_helper_title)) {
            ChoiceChips(
                options = Emirate.entries,
                selected = state.emirate,
                onSelect = vm::onEmirate,
                label = { stringResource(it.labelRes()) },
            )
            TextInput(state.plate, vm::onPlate, stringResource(R.string.vehicle_plate))
            TextInput(
                state.zone,
                vm::onZone,
                stringResource(R.string.parking_zone),
                supportingText = stringResource(R.string.parking_zone_hint),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.parking_hours), modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.onHours(state.hours - 1) }) {
                    Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.action_decrease))
                }
                Text(fmt.integer(state.hours), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { vm.onHours(state.hours + 1) }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_increase))
                }
            }
            state.parkingRate?.let { rate ->
                Text(
                    stringResource(R.string.parking_rate, fmt.aed(rate)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(
                onClick = {
                    val draft = vm.smsDraft()
                    if (draft == null) {
                        Toast.makeText(context, R.string.parking_sms_missing, Toast.LENGTH_SHORT).show()
                    } else {
                        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${draft.number}"))
                            .putExtra("sms_body", draft.body)
                        try {
                            context.startActivity(intent)
                        } catch (_: ActivityNotFoundException) {
                            Toast.makeText(context, R.string.parking_sms_no_app, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Sms, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.parking_pay_sms))
            }
            Text(
                stringResource(R.string.parking_sms_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    NumberField(
        value = state.amountText,
        onValueChange = vm::onAmount,
        label = stringResource(R.string.expense_amount),
        suffix = stringResource(R.string.currency_aed),
    )
    DateField(stringResource(R.string.expense_date), state.date, vm::onDate, fmt)
    TextInput(
        state.title,
        vm::onTitle,
        stringResource(if (state.category == ExpenseCategoryKey.OTHER) R.string.expense_custom_category else R.string.expense_title),
    )
    NumberField(
        value = state.odometerText,
        onValueChange = vm::onOdometer,
        label = stringResource(R.string.expense_odometer_optional),
        suffix = stringResource(R.string.unit_km),
        decimal = false,
    )
    TextInput(state.note, vm::onNote, stringResource(R.string.expense_note), singleLine = false)
    SwitchRow(
        title = stringResource(R.string.expense_is_income),
        subtitle = stringResource(R.string.expense_is_income_hint),
        checked = state.isIncome,
        onCheckedChange = vm::onIncome,
    )
    Spacer(Modifier.height(4.dp))
    Button(onClick = vm::save, enabled = state.canSave, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Text(stringResource(R.string.action_save))
    }
}
