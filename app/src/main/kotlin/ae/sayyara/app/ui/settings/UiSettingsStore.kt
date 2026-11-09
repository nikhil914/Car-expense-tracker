package ae.sayyara.app.ui.settings

import ae.sayyara.core.consumption.EconomyUnit
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class AppLanguage(val tag: String) { SYSTEM(""), ENGLISH("en"), ARABIC("ar") }

data class UiSettings(
    val language: AppLanguage = AppLanguage.SYSTEM,
    /** UAE default is km/L. */
    val economyUnit: EconomyUnit = EconomyUnit.KM_PER_L,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val onboardingDone: Boolean = false,
    /** PDPL: explicit, specific consent to store vehicle data on this device. */
    val consentAccepted: Boolean = false,
)

/**
 * Small, synchronous UI preferences (language, units, theme, onboarding).
 * SharedPreferences on purpose: theme and locale must be known before the first frame.
 */
class UiSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<UiSettings> = _state.asStateFlow()

    val current: UiSettings get() = _state.value

    fun update(transform: (UiSettings) -> UiSettings) {
        val next = transform(_state.value)
        prefs.edit {
            putString(K_LANGUAGE, next.language.name)
            putString(K_ECONOMY, next.economyUnit.name)
            putString(K_THEME, next.theme.name)
            putBoolean(K_DYNAMIC, next.dynamicColor)
            putBoolean(K_ONBOARDED, next.onboardingDone)
            putBoolean(K_CONSENT, next.consentAccepted)
        }
        _state.value = next
    }

    fun setLanguage(language: AppLanguage) {
        update { it.copy(language = language) }
        applyLocale(language)
    }

    private fun read(): UiSettings {
        val d = UiSettings()
        return UiSettings(
            language = enumOr(prefs.getString(K_LANGUAGE, null), d.language),
            economyUnit = enumOr(prefs.getString(K_ECONOMY, null), d.economyUnit),
            theme = enumOr(prefs.getString(K_THEME, null), d.theme),
            dynamicColor = prefs.getBoolean(K_DYNAMIC, d.dynamicColor),
            onboardingDone = prefs.getBoolean(K_ONBOARDED, d.onboardingDone),
            consentAccepted = prefs.getBoolean(K_CONSENT, d.consentAccepted),
        )
    }

    companion object {
        private const val FILE = "sayyara_ui_settings"
        private const val K_LANGUAGE = "language"
        private const val K_ECONOMY = "economy_unit"
        private const val K_THEME = "theme"
        private const val K_DYNAMIC = "dynamic_color"
        private const val K_ONBOARDED = "onboarding_done"
        private const val K_CONSENT = "pdpl_consent"

        private inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
            name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: fallback

        /** Applies the per-app locale via AppCompat (system per-app language on API 33+). */
        fun applyLocale(language: AppLanguage) {
            val wanted = if (language == AppLanguage.SYSTEM) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(language.tag)
            }
            if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != wanted.toLanguageTags()) {
                AppCompatDelegate.setApplicationLocales(wanted)
            }
        }
    }
}
