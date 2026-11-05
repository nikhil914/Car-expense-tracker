package ae.sayyara.app.ui.screens.route

import ae.sayyara.app.R
import ae.sayyara.app.ui.appViewModel
import ae.sayyara.app.ui.common.labelRes
import ae.sayyara.app.ui.components.BackTopBar
import ae.sayyara.app.ui.components.ChoiceChips
import ae.sayyara.app.ui.components.DateField
import ae.sayyara.app.ui.components.NumberField
import ae.sayyara.app.ui.components.SectionCard
import ae.sayyara.app.ui.components.StatusBadge
import ae.sayyara.app.ui.components.SwitchRow
import ae.sayyara.app.ui.components.TextInput
import ae.sayyara.app.ui.format.UiFormatter
import ae.sayyara.app.ui.format.parseInt
import ae.sayyara.app.ui.format.rememberUiFormatter
import ae.sayyara.core.model.Emirate
import ae.sayyara.core.model.FuelGrade
import ae.sayyara.core.route.RouteCostEstimate
import ae.sayyara.core.uae.QuoteStatus
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** UAE-only trip cost: fuel at the departure month's official price + Salik/Darb + parking. */
@Composable
fun RouteCostScreen(onBack: () -> Unit) {
    val vm = appViewModel { RouteCostViewModel(it.container, it.routeProvider) }
    val s by vm.state.collectAsStateWithLifecycle()
    val fmt = rememberUiFormatter()

    Scaffold(topBar = { BackTopBar(stringResource(R.string.route_title), onBack) }) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ChoiceChips(
                options = DistanceMode.entries, selected = s.mode,
                onSelect = { m -> vm.update { it.copy(mode = m) } },
                label = { stringResource(if (it == DistanceMode.COORDINATES) R.string.route_mode_coords else R.string.route_mode_manual) },
            )
            if (s.mode == DistanceMode.COORDINATES) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextInput(s.fromLat, { v -> vm.update { it.copy(fromLat = v) } }, stringResource(R.string.route_from_lat), Modifier.weight(1f))
                    TextInput(s.fromLon, { v -> vm.update { it.copy(fromLon = v) } }, stringResource(R.string.route_from_lon), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextInput(s.toLat, { v -> vm.update { it.copy(toLat = v) } }, stringResource(R.string.route_to_lat), Modifier.weight(1f))
                    TextInput(s.toLon, { v -> vm.update { it.copy(toLon = v) } }, stringResource(R.string.route_to_lon), Modifier.weight(1f))
                }
                OutlinedButton(onClick = vm::swap) { Text(stringResource(R.string.route_swap)) }
            } else {
                NumberField(s.distanceText, { v -> vm.update { it.copy(distanceText = v) } },
                    stringResource(R.string.route_distance), suffix = stringResource(R.string.unit_km))
            }
            DateField(stringResource(R.string.route_departure), s.date, { d -> vm.update { it.copy(date = d) } }, fmt)
            NumberField(s.hour.toString(), { v -> parseInt(v)?.takeIf { it in 0..23 }?.let { h -> vm.update { it.copy(hour = h) } } },
                stringResource(R.string.route_hour), decimal = false)
            ChoiceChips(
                options = listOf(FuelGrade.SUPER_98, FuelGrade.SPECIAL_95, FuelGrade.E_PLUS_91, FuelGrade.DIESEL),
                selected = s.grade, onSelect = { g -> vm.update { it.copy(grade = g) } },
                label = { stringResource(it.labelRes()) },
            )
            NumberField(s.per100kmText, { v -> vm.update { it.copy(per100kmText = v, per100kmFromHistory = false) } },
                stringResource(R.string.route_consumption), suffix = stringResource(R.string.unit_l_per_100km),
                supportingText = if (s.per100kmFromHistory) stringResource(R.string.route_consumption_history) else null)
            SwitchRow(stringResource(R.string.route_round_trip), s.roundTrip, { v -> vm.update { it.copy(roundTrip = v) } })
            NumberField(s.passengers.toString(), { v -> parseInt(v)?.takeIf { it in 1..20 }?.let { p -> vm.update { it.copy(passengers = p) } } },
                stringResource(R.string.route_passengers), decimal = false)
            SwitchRow(stringResource(R.string.route_parking), s.parking, { v -> vm.update { it.copy(parking = v) } })
            if (s.parking) {
                ChoiceChips(listOf(Emirate.DUBAI, Emirate.ABU_DHABI, Emirate.SHARJAH), s.parkingEmirate,
                    { e -> vm.update { it.copy(parkingEmirate = e) } }, { stringResource(it.labelRes()) })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextInput(s.parkingZone, { v -> vm.update { it.copy(parkingZone = v) } }, stringResource(R.string.parking_zone), Modifier.weight(1f))
                    NumberField(s.parkingHoursText, { v -> vm.update { it.copy(parkingHoursText = v) } },
                        stringResource(R.string.parking_hours), Modifier.weight(1f))
                }
            }
            Button(onClick = vm::estimate, enabled = !s.busy, modifier = Modifier.fillMaxWidth()) {
                if (s.busy) CircularProgressIndicator(Modifier.padding(end = 8.dp).width(18.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.route_estimate))
            }
            s.error?.let { Text(stringResource(errorRes(it)), color = MaterialTheme.colorScheme.error) }
            s.estimate?.let { EstimateCard(it, fmt) }
        }
    }
}

@Composable
private fun EstimateCard(e: RouteCostEstimate, fmt: UiFormatter) {
    SectionCard(title = stringResource(R.string.route_result)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(fmt.aed(e.totalAed), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(8.dp))
            when (e.priceQuote?.status) {
                QuoteStatus.PROVISIONAL -> StatusBadge(stringResource(R.string.price_provisional), warning = true)
                QuoteStatus.FORECAST -> StatusBadge(stringResource(R.string.price_forecast), warning = true)
                else -> {}
            }
        }
        Text(stringResource(R.string.route_band, fmt.aed(e.lowAed), fmt.aed(e.highAed)), style = MaterialTheme.typography.bodySmall)
        Line(stringResource(R.string.route_fuel, fmt.number(e.litres, 1)), fmt.aed(e.fuelAed))
        Line(stringResource(R.string.route_tolls, e.tolls.size), fmt.aed(e.tollAed))
        e.tolls.forEach { Text("· ${it.gateName} — ${fmt.aed(it.aed)}", style = MaterialTheme.typography.bodySmall) }
        Line(stringResource(R.string.route_parking), fmt.aed(e.parkingAed))
        if (e.perPersonAed != e.totalAed) Line(stringResource(R.string.route_per_person), fmt.aed(e.perPersonAed))
        e.priceQuote?.let {
            Text(stringResource(R.string.route_price_note, fmt.monthLong(it.month), fmt.aed(it.aedPerLitre)),
                style = MaterialTheme.typography.bodySmall)
            if (it.status != QuoteStatus.FINAL) Text(stringResource(R.string.route_price_change_note), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Line(label: String, value: String) = Row(Modifier.fillMaxWidth()) {
    Text(label, Modifier.weight(1f)); Text(value)
}

private fun errorRes(e: RouteError): Int = when (e) {
    RouteError.BAD_COORDINATES -> R.string.route_err_coords
    RouteError.BAD_DISTANCE -> R.string.route_err_distance
    RouteError.BAD_CONSUMPTION -> R.string.route_err_consumption
    RouteError.NO_ROUTE -> R.string.route_err_no_route
    RouteError.FAILED -> R.string.route_err_failed
}
