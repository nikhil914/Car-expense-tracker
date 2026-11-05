package ae.sayyara.app

import ae.sayyara.app.ui.AppDeps
import ae.sayyara.app.ui.LocalAppDeps
import ae.sayyara.app.ui.ReceiptScanAction
import ae.sayyara.app.ui.navigation.AppNavHost
import ae.sayyara.app.ui.navigation.DeepLinkAction
import ae.sayyara.app.ui.navigation.DeepLinks
import ae.sayyara.app.ui.settings.UiSettingsStore
import ae.sayyara.app.ui.theme.SayyaraTheme
import ae.sayyara.core.ocr.ReceiptFields
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Single activity. AppCompatActivity so per-app language (AppCompatDelegate.setApplicationLocales)
 * applies on API < 33 too.
 */
class MainActivity : AppCompatActivity() {

    private var pendingLink by mutableStateOf<DeepLinkAction?>(null)
    private var scanCallback: ((ReceiptFields?) -> Unit)? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val cb = scanCallback ?: return@registerForActivityResult
        scanCallback = null
        if (uri == null) { cb(null); return@registerForActivityResult }
        lifecycleScope.launch {
            val fields = runCatching {
                appContainer.newReceiptScanner().use { it.scanReceipt(uri).fields }
            }.getOrNull()
            cb(fields)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val settings = UiSettingsStore(this)
        val deps = AppDeps(
            container = appContainer,
            settings = settings,
            routeProvider = appContainer.routeProvider,
            receiptScanner = ReceiptScanAction { onResult ->
                scanCallback = onResult
                pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
        )
        if (savedInstanceState == null) pendingLink = DeepLinks.parse(intent)

        setContent {
            val s by settings.state.collectAsStateWithLifecycle()
            SayyaraTheme(themeMode = s.theme, dynamicColor = s.dynamicColor) {
                CompositionLocalProvider(LocalAppDeps provides deps) {
                    AppNavHost(
                        onboardingDone = s.onboardingDone,
                        pendingLink = pendingLink,
                        onLinkHandled = { pendingLink = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        DeepLinks.parse(intent)?.let { pendingLink = it }
    }
}
