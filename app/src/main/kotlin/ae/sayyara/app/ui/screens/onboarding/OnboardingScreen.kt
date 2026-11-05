package ae.sayyara.app.ui.screens.onboarding

import ae.sayyara.app.R
import ae.sayyara.app.ui.LocalAppDeps
import ae.sayyara.app.ui.appViewModel
import ae.sayyara.app.ui.components.ChoiceChips
import ae.sayyara.app.ui.components.SwitchRow
import ae.sayyara.app.ui.screens.vehicles.VehicleEditViewModel
import ae.sayyara.app.ui.screens.vehicles.VehicleFormFields
import ae.sayyara.app.ui.settings.AppLanguage
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Step 0: language + PDPL consent. Step 1: first vehicle, or import from Fuelio. */
@Composable
fun OnboardingScreen(onFinished: () -> Unit, onImport: () -> Unit) {
    val deps = LocalAppDeps.current
    val settings by deps.settings.state.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableIntStateOf(0) }
    val vm = appViewModel(key = "onboarding-vehicle") { VehicleEditViewModel(it.container, null) }
    val form by vm.state.collectAsStateWithLifecycle()

    fun finish() { deps.settings.update { it.copy(onboardingDone = true) }; onFinished() }
    LaunchedEffect(form.saved) { if (form.saved) finish() }

    Scaffold { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineMedium)
            if (step == 0) {
                Text(stringResource(R.string.onboarding_body))
                ChoiceChips(listOf(AppLanguage.ENGLISH, AppLanguage.ARABIC), settings.language.takeIf { it != AppLanguage.SYSTEM },
                    { deps.settings.setLanguage(it) }, { if (it == AppLanguage.ARABIC) "العربية" else "English" })
                Text(stringResource(R.string.privacy_body), style = MaterialTheme.typography.bodySmall)
                SwitchRow(stringResource(R.string.onboarding_consent), settings.consentAccepted,
                    { v -> deps.settings.update { it.copy(consentAccepted = v) } })
                Button(onClick = { step = 1 }, enabled = settings.consentAccepted, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_continue))
                }
            } else {
                Text(stringResource(R.string.onboarding_vehicle), style = MaterialTheme.typography.titleMedium)
                VehicleFormFields(form, vm::update)
                Button(onClick = vm::save, enabled = form.valid, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_save)) }
                OutlinedButton(onClick = { deps.settings.update { it.copy(onboardingDone = true) }; onImport() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.onboarding_import))
                }
            }
        }
    }
}
