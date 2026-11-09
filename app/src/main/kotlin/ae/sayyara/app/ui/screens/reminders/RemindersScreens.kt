package ae.sayyara.app.ui.screens.reminders

import ae.sayyara.app.R
import ae.sayyara.app.data.AppContainer
import ae.sayyara.app.ui.appViewModel
import ae.sayyara.app.ui.common.labelRes
import ae.sayyara.app.ui.common.newId
import ae.sayyara.app.ui.common.selectedVehicleData
import ae.sayyara.app.ui.components.BackTopBar
import ae.sayyara.app.ui.components.ChoiceChips
import ae.sayyara.app.ui.components.DateField
import ae.sayyara.app.ui.components.EmptyState
import ae.sayyara.app.ui.components.NumberField
import ae.sayyara.app.ui.components.TextInput
import ae.sayyara.app.ui.format.parseDecimal
import ae.sayyara.app.ui.format.parseInt
import ae.sayyara.app.ui.format.rememberUiFormatter
import ae.sayyara.app.ui.format.toInputText
import ae.sayyara.app.ui.screens.home.ReminderDue
import ae.sayyara.app.ui.screens.home.ReminderDueRow
import ae.sayyara.core.model.Reminder
import ae.sayyara.core.model.ReminderKind
import ae.sayyara.core.reminders.ReminderPlanner
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

private fun today(): LocalDate = Clock.System.todayIn(TimeZone.of("Asia/Dubai"))

class RemindersViewModel(private val c: AppContainer) : ViewModel() {
    val items: StateFlow<List<ReminderDue>> = c.selectedVehicleData().map { d ->
        if (d == null) return@map emptyList()
        val odo = d.lastOdometerKm(); val kmDay = d.kmPerDay(); val t = today()
        d.reminders.filter { !it.done }
            .mapNotNull { r -> runCatching { ReminderDue(r, ReminderPlanner.due(r, t, odo, kmDay)) }.getOrNull() }
            .sortedWith(compareBy({ !it.due.overdue }, { it.due.daysLeft ?: Int.MAX_VALUE }))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Mark done; repeating reminders roll forward via ReminderPlanner.next. */
    fun complete(r: Reminder) = viewModelScope.launch {
        val odo = c.selectedVehicleData().first()?.lastOdometerKm()
        c.reminders.upsert(r.copy(done = true))
        ReminderPlanner.next(r, today(), odo)?.let { c.reminders.upsert(it.copy(id = newId(), done = false)) }
    }
}

@Composable
fun RemindersScreen(onBack: () -> Unit, onEdit: (String?) -> Unit, onMulkiya: () -> Unit) {
    val vm = appViewModel { RemindersViewModel(it.container) }
    val items by vm.items.collectAsStateWithLifecycle()
    val fmt = rememberUiFormatter()
    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.reminders_title), onBack) },
        floatingActionButton = { FloatingActionButton(onClick = { onEdit(null) }) { Icon(Icons.Filled.Add, stringResource(R.string.reminder_add)) } },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            OutlinedButton(onClick = onMulkiya, modifier = Modifier.padding(16.dp).fillMaxWidth()) { Text(stringResource(R.string.mulkiya_setup)) }
            if (items.isEmpty()) {
                EmptyState(stringResource(R.string.reminders_empty_title), stringResource(R.string.reminders_empty_body))
            } else {
                LazyColumn {
                    items(items, key = { it.reminder.id }) { item ->
                        Row(Modifier.fillMaxWidth().clickable { onEdit(item.reminder.id) }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            ReminderDueRow(item, fmt, Modifier.weight(1f))
                            TextButton(onClick = { vm.complete(item.reminder) }) { Text(stringResource(R.string.reminder_done)) }
                        }
                    }
                }
            }
        }
    }
}

data class ReminderForm(
    val kind: ReminderKind = ReminderKind.SERVICE,
    val title: String = "",
    val dueDate: LocalDate? = null,
    val dueOdoText: String = "",
    val repeatMonthsText: String = "",
    val repeatKmText: String = "",
    val leadDaysText: String = "14",
    val saved: Boolean = false,
) {
    val valid get() = title.isNotBlank() && (dueDate != null || parseDecimal(dueOdoText) != null)
}

class ReminderEditViewModel(private val c: AppContainer, private val id: String?) : ViewModel() {
    private val _s = MutableStateFlow(ReminderForm())
    val state: StateFlow<ReminderForm> = _s.asStateFlow()
    private var existing: Reminder? = null

    init {
        if (id != null) viewModelScope.launch {
            val r = c.selectedVehicleData().first()?.reminders?.firstOrNull { it.id == id } ?: return@launch
            existing = r
            _s.value = ReminderForm(r.kind, r.title, r.dueDate, r.dueOdometerKm?.toInputText(0).orEmpty(),
                r.repeatMonths?.toString().orEmpty(), r.repeatKm?.toInputText(0).orEmpty(), r.leadDays.toString())
        }
    }

    fun update(t: (ReminderForm) -> ReminderForm) = _s.update(t)

    fun save() = viewModelScope.launch {
        val f = _s.value
        val vehicleId = c.selectedVehicleData().first()?.vehicle?.id ?: return@launch
        val base = existing ?: Reminder(id = newId(), vehicleId = vehicleId, kind = f.kind, title = f.title)
        c.reminders.upsert(base.copy(kind = f.kind, title = f.title.trim(), dueDate = f.dueDate,
            dueOdometerKm = parseDecimal(f.dueOdoText), repeatMonths = parseInt(f.repeatMonthsText),
            repeatKm = parseDecimal(f.repeatKmText), leadDays = parseInt(f.leadDaysText) ?: 14))
        _s.update { it.copy(saved = true) }
    }

    fun delete() = viewModelScope.launch {
        existing?.let { c.reminders.delete(it.id) }
        _s.update { it.copy(saved = true) }
    }
}

@Composable
fun ReminderEditScreen(reminderId: String?, onDone: () -> Unit) {
    val vm = appViewModel(key = "reminder-$reminderId") { ReminderEditViewModel(it.container, reminderId) }
    val f by vm.state.collectAsStateWithLifecycle()
    val fmt = rememberUiFormatter()
    LaunchedEffect(f.saved) { if (f.saved) onDone() }
    Scaffold(topBar = {
        BackTopBar(stringResource(if (reminderId == null) R.string.reminder_add else R.string.reminder_edit), onDone) {
            if (reminderId != null) TextButton(onClick = { vm.delete() }) { Text(stringResource(R.string.action_delete)) }
        }
    }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ChoiceChips(ReminderKind.entries, f.kind, { k -> vm.update { it.copy(kind = k) } }, { stringResource(it.labelRes()) })
            TextInput(f.title, { v -> vm.update { it.copy(title = v) } }, stringResource(R.string.reminder_title_field))
            DateField(stringResource(R.string.reminder_due_date), f.dueDate, { d -> vm.update { it.copy(dueDate = d) } }, fmt, allowClear = true)
            NumberField(f.dueOdoText, { v -> vm.update { it.copy(dueOdoText = v) } }, stringResource(R.string.reminder_due_odo), suffix = stringResource(R.string.unit_km))
            NumberField(f.repeatMonthsText, { v -> vm.update { it.copy(repeatMonthsText = v) } }, stringResource(R.string.reminder_repeat_months), decimal = false)
            NumberField(f.repeatKmText, { v -> vm.update { it.copy(repeatKmText = v) } }, stringResource(R.string.reminder_repeat_km), suffix = stringResource(R.string.unit_km))
            NumberField(f.leadDaysText, { v -> vm.update { it.copy(leadDaysText = v) } }, stringResource(R.string.reminder_lead_days), decimal = false)
            Button(onClick = { vm.save() }, enabled = f.valid, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_save)) }
        }
    }
}

class MulkiyaWizardViewModel(private val c: AppContainer) : ViewModel() {
    val expiry = MutableStateFlow<LocalDate?>(null)
    val preview = MutableStateFlow<List<Reminder>>(emptyList())
    val done = MutableStateFlow(false)

    fun setExpiry(d: LocalDate?) = viewModelScope.launch {
        expiry.value = d
        val v = c.selectedVehicleData().first()?.vehicle ?: return@launch
        preview.value = if (d == null) emptyList() else ReminderPlanner.mulkiyaChain(v.id, d, v.year, today(), ::newId)
    }

    fun confirm() = viewModelScope.launch {
        preview.value.forEach { c.reminders.upsert(it) }
        done.value = true
    }
}

@Composable
fun MulkiyaWizardScreen(onDone: () -> Unit) {
    val vm = appViewModel { MulkiyaWizardViewModel(it.container) }
    val expiry by vm.expiry.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val done by vm.done.collectAsStateWithLifecycle()
    val fmt = rememberUiFormatter()
    LaunchedEffect(done) { if (done) onDone() }
    Scaffold(topBar = { BackTopBar(stringResource(R.string.mulkiya_setup), onDone) }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.mulkiya_intro), style = MaterialTheme.typography.bodyMedium)
            DateField(stringResource(R.string.mulkiya_expiry), expiry, { vm.setExpiry(it) }, fmt)
            preview.forEach { r ->
                Text("• ${r.title} — ${r.dueDate?.let(fmt::date).orEmpty()}")
            }
            Button(onClick = { vm.confirm() }, enabled = preview.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.mulkiya_create, preview.size))
            }
        }
    }
}
