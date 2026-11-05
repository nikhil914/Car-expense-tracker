package ae.sayyara.app.ui.screens.more

import ae.sayyara.app.R
import ae.sayyara.app.ui.LocalAppDeps
import ae.sayyara.app.ui.common.labelRes
import ae.sayyara.app.ui.components.BackTopBar
import ae.sayyara.app.ui.components.ChoiceChips
import ae.sayyara.app.ui.components.SwitchRow
import ae.sayyara.app.ui.settings.AppLanguage
import ae.sayyara.app.ui.settings.ThemeMode
import ae.sayyara.core.consumption.EconomyUnit
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MoreScreen(
    onVehicles: () -> Unit, onReminders: () -> Unit, onRoute: () -> Unit,
    onImport: () -> Unit, onSettings: () -> Unit, onAbout: () -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_more)) }) }) { pad ->
        Column(Modifier.padding(pad)) {
            Entry(Icons.Filled.DirectionsCar, R.string.vehicles_title, onVehicles)
            Entry(Icons.Filled.Notifications, R.string.reminders_title, onReminders)
            Entry(Icons.Filled.Route, R.string.route_title, onRoute)
            Entry(Icons.Filled.FileDownload, R.string.import_title, onImport)
            Entry(Icons.Filled.Settings, R.string.settings_title, onSettings)
            Entry(Icons.Filled.Info, R.string.about_title, onAbout)
        }
    }
}

@Composable
private fun Entry(icon: ImageVector, title: Int, onClick: () -> Unit) = ListItem(
    headlineContent = { Text(stringResource(title)) },
    leadingContent = { Icon(icon, null) },
    trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, null) },
    modifier = Modifier.clickable(onClick = onClick),
)

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val deps = LocalAppDeps.current
    val s by deps.settings.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            val ok = runCatching {
                val bytes = deps.container.importer.exportCsv()
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } }
            }.isSuccess
            Toast.makeText(context, if (ok) R.string.export_done else R.string.export_failed, Toast.LENGTH_SHORT).show()
        }
    }
    Scaffold(topBar = { BackTopBar(stringResource(R.string.settings_title), onBack) }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleSmall)
            ChoiceChips(AppLanguage.entries, s.language, { deps.settings.setLanguage(it) }, {
                when (it) {
                    AppLanguage.SYSTEM -> stringResource(R.string.settings_system)
                    AppLanguage.ENGLISH -> "English"
                    AppLanguage.ARABIC -> "العربية"
                }
            })
            Text(stringResource(R.string.settings_economy_unit), style = MaterialTheme.typography.titleSmall)
            ChoiceChips(listOf(EconomyUnit.KM_PER_L, EconomyUnit.L_PER_100KM, EconomyUnit.MPG_US), s.economyUnit,
                { u -> deps.settings.update { it.copy(economyUnit = u) } }, { stringResource(it.labelRes()) })
            Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleSmall)
            ChoiceChips(ThemeMode.entries, s.theme, { t -> deps.settings.update { it.copy(theme = t) } }, {
                stringResource(when (it) {
                    ThemeMode.SYSTEM -> R.string.settings_system
                    ThemeMode.LIGHT -> R.string.settings_theme_light
                    ThemeMode.DARK -> R.string.settings_theme_dark
                })
            })
            SwitchRow(stringResource(R.string.settings_dynamic_color), s.dynamicColor, { v -> deps.settings.update { it.copy(dynamicColor = v) } })
            OutlinedButton(onClick = { exporter.launch("sayyara-export.csv") }) { Text(stringResource(R.string.settings_export)) }
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(topBar = { BackTopBar(stringResource(R.string.about_title), onBack) }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.about_body))
            Text(stringResource(R.string.privacy_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.privacy_body))
            Text(stringResource(R.string.about_price_disclaimer), style = MaterialTheme.typography.bodySmall)
        }
    }
}
