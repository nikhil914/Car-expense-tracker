package ae.sayyara.app.ui.screens.importer

import ae.sayyara.app.R
import ae.sayyara.app.data.AppContainer
import ae.sayyara.app.data.ImportResult
import ae.sayyara.app.ui.appViewModel
import ae.sayyara.app.ui.components.BackTopBar
import ae.sayyara.app.ui.components.SectionCard
import ae.sayyara.app.ui.components.WarningList
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ImportUi {
    data object Idle : ImportUi
    data object Busy : ImportUi
    data class Preview(val p: ImportResult.Preview) : ImportUi
    data class Done(val batchId: String, val p: ImportResult.Preview) : ImportUi
    data object Undone : ImportUi
    data class Error(val message: String) : ImportUi
}

class ImportViewModel(private val c: AppContainer) : ViewModel() {
    private val _s = MutableStateFlow<ImportUi>(ImportUi.Idle)
    val state = _s.asStateFlow()
    private var lastUri: Uri? = null

    fun load(context: Context, uri: Uri) {
        if (uri == lastUri) return
        lastUri = uri
        _s.value = ImportUi.Busy
        viewModelScope.launch {
            runCatching {
                val (name, bytes) = withContext(Dispatchers.IO) {
                    val cr = context.contentResolver
                    val n = cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cur ->
                        if (cur.moveToFirst()) cur.getString(0) else null
                    } ?: "fuelio.csv"
                    n to (cr.openInputStream(uri)?.use { it.readBytes() } ?: error("Cannot open file"))
                }
                c.importer.preview(name, bytes)
            }.onSuccess { r ->
                _s.value = when (r) {
                    is ImportResult.Preview -> ImportUi.Preview(r)
                    is ImportResult.Failed -> ImportUi.Error(r.message)
                }
            }.onFailure { _s.value = ImportUi.Error(it.message ?: it.toString()) }
        }
    }

    fun commit() {
        val p = (_s.value as? ImportUi.Preview)?.p ?: return
        _s.value = ImportUi.Busy
        viewModelScope.launch {
            runCatching { c.importer.commit(p.batchId) }
                .onSuccess { _s.value = ImportUi.Done(p.batchId, p) }
                .onFailure { _s.value = ImportUi.Error(it.message ?: it.toString()) }
        }
    }

    fun undo() {
        val d = _s.value as? ImportUi.Done ?: return
        viewModelScope.launch { c.importer.undo(d.batchId); _s.value = ImportUi.Undone }
    }
}

@Composable
fun ImportScreen(initialUri: String?, onBack: () -> Unit) {
    val vm = appViewModel { ImportViewModel(it.container) }
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.load(context, it) } }
    LaunchedEffect(initialUri) { initialUri?.let { vm.load(context, Uri.parse(it)) } }

    Scaffold(topBar = { BackTopBar(stringResource(R.string.import_title), onBack) }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.import_help), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(
                onClick = { picker.launch(arrayOf("text/*", "application/zip", "application/octet-stream")) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.import_pick)) }
            when (val st = s) {
                ImportUi.Idle -> {}
                ImportUi.Busy -> CircularProgressIndicator()
                is ImportUi.Preview -> {
                    Summary(st.p)
                    Button(onClick = vm::commit, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_commit)) }
                }
                is ImportUi.Done -> {
                    Text(stringResource(R.string.import_done), style = MaterialTheme.typography.titleMedium)
                    Summary(st.p)
                    OutlinedButton(onClick = vm::undo) { Text(stringResource(R.string.import_undo)) }
                }
                ImportUi.Undone -> Text(stringResource(R.string.import_undone))
                is ImportUi.Error -> Text(stringResource(R.string.import_failed, st.message), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun Summary(p: ImportResult.Preview) = SectionCard(title = stringResource(R.string.import_preview)) {
    Text(stringResource(R.string.import_counts, p.vehicles, p.fills, p.expenses))
    if (p.warnings.isNotEmpty()) WarningList(p.warnings)
}
