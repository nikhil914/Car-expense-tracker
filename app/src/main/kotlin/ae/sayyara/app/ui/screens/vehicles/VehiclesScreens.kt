package ae.sayyara.app.ui.screens.vehicles

import ae.sayyara.app.R
import ae.sayyara.app.data.AppContainer
import ae.sayyara.app.ui.appViewModel
import ae.sayyara.app.ui.common.labelRes
import ae.sayyara.app.ui.common.newId
import ae.sayyara.app.ui.components.BackTopBar
import ae.sayyara.app.ui.components.ChoiceChips
import ae.sayyara.app.ui.components.EmptyState
import ae.sayyara.app.ui.components.NumberField
import ae.sayyara.app.ui.components.SwitchRow
import ae.sayyara.app.ui.components.TextInput
import ae.sayyara.app.ui.format.parseDecimal
import ae.sayyara.app.ui.format.parseInt
import ae.sayyara.app.ui.format.toInputText
import ae.sayyara.core.model.Emirate
import ae.sayyara.core.model.EnergyStore
import ae.sayyara.core.model.EnergyStoreKind
import ae.sayyara.core.model.EnergyUnit
import ae.sayyara.core.model.FuelGrade
import ae.sayyara.core.model.Vehicle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ListItem
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

class VehiclesViewModel(private val c: AppContainer) : ViewModel() {
    val vehicles: StateFlow<List<Vehicle>> = c.vehicles.observeVehicles()
        .map { list -> list.filter { it.deletedAt == null } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val selected: StateFlow<String?> = c.vehicles.observeSelectedVehicleId()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun select(id: String) = viewModelScope.launch { c.vehicles.selectVehicle(id) }
}

@Composable
fun VehiclesScreen(onBack: () -> Unit, onEdit: (String?) -> Unit) {
    val vm = appViewModel { VehiclesViewModel(it.container) }
    val list by vm.vehicles.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.vehicles_title), onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEdit(null) }) { Icon(Icons.Filled.Add, stringResource(R.string.vehicle_add)) }
        },
    ) { pad ->
        if (list.isEmpty()) {
            EmptyState(stringResource(R.string.vehicles_empty_title), stringResource(R.string.vehicles_empty_body),
                Modifier.padding(pad), stringResource(R.string.vehicle_add)) { onEdit(null) }
        } else {
            LazyColumn(Modifier.padding(pad)) {
                items(list, key = { it.id }) { v ->
                    ListItem(
                        headlineContent = { Text(v.name) },
                        supportingContent = { Text(listOfNotNull(v.make, v.model, v.year?.toString(), v.plate).joinToString(" · ")) },
                        trailingContent = {
                            if (v.id == selected) Text(stringResource(R.string.vehicle_selected))
                            else TextButton(onClick = { vm.select(v.id) }) { Text(stringResource(R.string.vehicle_select)) }
                        },
                        modifier = Modifier.clickable { onEdit(v.id) },
                    )
                }
            }
        }
    }
}

data class VehicleForm(
    val id: String? = null,
    val name: String = "",
    val make: String = "",
    val model: String = "",
    val year: String = "",
    val plate: String = "",
    val emirate: Emirate = Emirate.DUBAI,
    val grade: FuelGrade = FuelGrade.SPECIAL_95,
    val tankText: String = "60",
    val hasBattery: Boolean = false,
    val batteryText: String = "",
    val saved: Boolean = false,
) {
    val isEv get() = grade == FuelGrade.ELECTRIC
    val valid get() = name.isNotBlank() && (isEv || parseDecimal(tankText)?.let { it > 0 } == true)
}

class VehicleEditViewModel(private val c: AppContainer, private val vehicleId: String?) : ViewModel() {
    private val _s = MutableStateFlow(VehicleForm())
    val state: StateFlow<VehicleForm> = _s.asStateFlow()
    private var existing: Vehicle? = null
    private var existingStores: List<EnergyStore> = emptyList()

    init {
        if (vehicleId != null) viewModelScope.launch {
            val v = c.vehicles.observeVehicles().first().firstOrNull { it.id == vehicleId } ?: return@launch
            val stores = c.vehicles.observeStores(v.id).first()
            existing = v; existingStores = stores
            val tank = stores.firstOrNull { it.kind == EnergyStoreKind.TANK }
            val batt = stores.firstOrNull { it.kind == EnergyStoreKind.BATTERY }
            _s.value = VehicleForm(
                id = v.id, name = v.name, make = v.make.orEmpty(), model = v.model.orEmpty(),
                year = v.year?.toString().orEmpty(), plate = v.plate.orEmpty(), emirate = v.emirate ?: Emirate.DUBAI,
                grade = v.defaultGrade, tankText = tank?.capacity?.toInputText(0).orEmpty(),
                hasBattery = batt != null && tank != null, batteryText = batt?.capacity?.toInputText(1).orEmpty(),
            )
        }
    }

    fun update(t: (VehicleForm) -> VehicleForm) = _s.update(t)

    fun save() {
        val f = _s.value
        if (!f.valid) return
        viewModelScope.launch {
            val now = Clock.System.now()
            val id = existing?.id ?: newId()
            val vehicle = (existing ?: Vehicle(id = id, name = f.name, updatedAt = now)).copy(
                name = f.name.trim(), make = f.make.trim().ifBlank { null }, model = f.model.trim().ifBlank { null },
                year = parseInt(f.year), plate = f.plate.trim().ifBlank { null }, emirate = f.emirate,
                defaultGrade = f.grade, updatedAt = now,
            )
            val stores = buildList {
                if (!f.isEv) {
                    val prev = existingStores.firstOrNull { it.kind == EnergyStoreKind.TANK }
                    add(EnergyStore(prev?.id ?: newId(), id, EnergyStoreKind.TANK, EnergyUnit.LITRE, parseDecimal(f.tankText), defaultGrade = f.grade))
                }
                if (f.isEv || f.hasBattery) {
                    val prev = existingStores.firstOrNull { it.kind == EnergyStoreKind.BATTERY }
                    add(EnergyStore(prev?.id ?: newId(), id, EnergyStoreKind.BATTERY, EnergyUnit.KWH, parseDecimal(f.batteryText), defaultGrade = FuelGrade.ELECTRIC))
                }
            }
            c.vehicles.upsertVehicle(vehicle, stores)
            if (existing == null) c.vehicles.selectVehicle(id)
            _s.update { it.copy(saved = true) }
        }
    }

    fun delete() = viewModelScope.launch {
        existing?.let { c.vehicles.deleteVehicle(it.id) }
        _s.update { it.copy(saved = true) }
    }
}

/** Shared vehicle form; also used by onboarding. */
@Composable
fun VehicleFormFields(f: VehicleForm, update: ((VehicleForm) -> VehicleForm) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextInput(f.name, { v -> update { it.copy(name = v) } }, stringResource(R.string.vehicle_name))
        TextInput(f.make, { v -> update { it.copy(make = v) } }, stringResource(R.string.vehicle_make))
        TextInput(f.model, { v -> update { it.copy(model = v) } }, stringResource(R.string.vehicle_model))
        NumberField(f.year, { v -> update { it.copy(year = v) } }, stringResource(R.string.vehicle_year), decimal = false)
        TextInput(f.plate, { v -> update { it.copy(plate = v) } }, stringResource(R.string.vehicle_plate))
        Text(stringResource(R.string.vehicle_emirate))
        ChoiceChips(Emirate.entries, f.emirate, { e -> update { it.copy(emirate = e) } }, { stringResource(it.labelRes()) })
        Text(stringResource(R.string.vehicle_grade))
        ChoiceChips(
            listOf(FuelGrade.SUPER_98, FuelGrade.SPECIAL_95, FuelGrade.E_PLUS_91, FuelGrade.DIESEL, FuelGrade.ELECTRIC),
            f.grade, { g -> update { it.copy(grade = g) } }, { stringResource(it.labelRes()) },
        )
        if (!f.isEv) {
            NumberField(f.tankText, { v -> update { it.copy(tankText = v) } }, stringResource(R.string.vehicle_tank), suffix = stringResource(R.string.unit_litre))
            SwitchRow(stringResource(R.string.vehicle_phev), f.hasBattery, { v -> update { it.copy(hasBattery = v) } })
        }
        if (f.isEv || f.hasBattery) {
            NumberField(f.batteryText, { v -> update { it.copy(batteryText = v) } }, stringResource(R.string.vehicle_battery), suffix = stringResource(R.string.unit_kwh))
        }
    }
}

@Composable
fun VehicleEditScreen(vehicleId: String?, onDone: () -> Unit) {
    val vm = appViewModel(key = "vehicle-$vehicleId") { VehicleEditViewModel(it.container, vehicleId) }
    val f by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(f.saved) { if (f.saved) onDone() }
    Scaffold(topBar = {
        BackTopBar(stringResource(if (vehicleId == null) R.string.vehicle_add else R.string.vehicle_edit), onDone) {
            if (vehicleId != null) TextButton(onClick = { vm.delete() }) { Text(stringResource(R.string.action_delete)) }
        }
    }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            VehicleFormFields(f, vm::update)
            Button(onClick = vm::save, enabled = f.valid, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_save)) }
        }
    }
}
