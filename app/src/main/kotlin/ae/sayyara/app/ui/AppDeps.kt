package ae.sayyara.app.ui

import ae.sayyara.app.data.AppContainer
import ae.sayyara.app.ui.settings.UiSettingsStore
import ae.sayyara.core.ocr.ReceiptFields
import ae.sayyara.core.route.RouteProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * UI-side seam for receipt scanning. MainActivity adapts the platform scanner to it, so the
 * fill-up sheet never depends on camera or OCR classes directly.
 */
fun interface ReceiptScanAction {
    /** Starts a scan. [onResult] receives the parsed fields, or null when cancelled or unreadable. */
    fun scan(onResult: (ReceiptFields?) -> Unit)
}

/** Everything the UI layer needs from the outside world. Built once in MainActivity. */
class AppDeps(
    val container: AppContainer,
    val settings: UiSettingsStore,
    val routeProvider: RouteProvider,
    val receiptScanner: ReceiptScanAction?,
)

val LocalAppDeps = staticCompositionLocalOf<AppDeps> { error("AppDeps not provided") }

/** ViewModelProvider.Factory that builds a ViewModel from the AppContainer (and UI settings). */
class ContainerViewModelFactory(
    private val deps: AppDeps,
    private val builder: (AppDeps) -> ViewModel,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = builder(deps) as T
}

/** Obtains a ViewModel scoped to the current ViewModelStoreOwner, created from [AppDeps]. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    noinline builder: (AppDeps) -> VM,
): VM {
    val deps = LocalAppDeps.current
    return viewModel(key = key, factory = ContainerViewModelFactory(deps, builder))
}
