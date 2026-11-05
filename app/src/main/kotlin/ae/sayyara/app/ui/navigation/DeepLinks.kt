package ae.sayyara.app.ui.navigation

import ae.sayyara.core.model.ExpenseCategoryKey
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

/** Pre-filled fill-up values, e.g. from Android Auto refuel detection. Levels are fractions 0..1. */
data class FillDraft(
    val litres: Double? = null,
    val levelBefore: Double? = null,
    val levelAfter: Double? = null,
    val odometerKm: Double? = null,
    val source: String? = null,
) {
    val fromCar: Boolean get() = source == "car"
}

sealed interface DeepLinkAction {
    data class AddFill(val draft: FillDraft?) : DeepLinkAction
    data object Dashboard : DeepLinkAction
    data class AddExpense(val category: ExpenseCategoryKey?) : DeepLinkAction
    data class Import(val uri: Uri?) : DeepLinkAction
    data object Route : DeepLinkAction
}

/**
 * Parses the deep links from the build brief:
 * - sayyara://add-fill[?litres=32.5&level_before=18&level_after=98&odo=123456&source=car]
 * - sayyara://dashboard, sayyara://expenses/add?category=PARKING, sayyara://import, sayyara://route
 * - ACTION_SEND with EXTRA_STREAM (Fuelio backup shared from Drive / Files)
 */
object DeepLinks {
    const val SCHEME = "sayyara"

    fun parse(intent: Intent?): DeepLinkAction? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                val stream = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                stream?.let { DeepLinkAction.Import(it) }
            }
            Intent.ACTION_VIEW -> intent.data?.let(::parse)
            else -> null
        }
    }

    fun parse(uri: Uri): DeepLinkAction? {
        if (uri.scheme != SCHEME) return null
        val path = uri.pathSegments.orEmpty()
        return when (uri.host) {
            "add-fill" -> DeepLinkAction.AddFill(parseDraft(uri))
            "dashboard" -> DeepLinkAction.Dashboard
            "expenses" -> if (path.firstOrNull() == null || path.first() == "add") {
                val cat = uri.getQueryParameter("category")?.uppercase()
                DeepLinkAction.AddExpense(ExpenseCategoryKey.entries.firstOrNull { it.name == cat })
            } else {
                null
            }
            "import" -> DeepLinkAction.Import(null)
            "route" -> DeepLinkAction.Route
            else -> null
        }
    }

    private fun parseDraft(uri: Uri): FillDraft? {
        fun d(name: String) = uri.getQueryParameter(name)?.trim()?.toDoubleOrNull()
        val draft = FillDraft(
            litres = d("litres")?.takeIf { it > 0 },
            levelBefore = d("level_before")?.let(::toFraction),
            levelAfter = d("level_after")?.let(::toFraction),
            odometerKm = d("odo")?.takeIf { it > 0 },
            source = uri.getQueryParameter("source"),
        )
        return draft.takeIf { it != FillDraft() }
    }

    /** Accepts percent (18, 98) or a fraction (0.18). */
    private fun toFraction(v: Double): Double? = when {
        v < 0 -> null
        v <= 1.0 -> v
        v <= 100.0 -> v / 100.0
        else -> null
    }
}
