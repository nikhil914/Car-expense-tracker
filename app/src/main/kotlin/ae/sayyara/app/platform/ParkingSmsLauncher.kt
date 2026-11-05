package ae.sayyara.app.platform

import ae.sayyara.core.model.Emirate
import ae.sayyara.core.uae.Parking
import ae.sayyara.core.uae.SmsDraft
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Parking "composer": opens the user's SMS app with the parking number and `<plate> <zone> <hours>`
 * prefilled. The user presses send, so the app needs no SEND_SMS / READ_SMS permission.
 */
object ParkingSmsLauncher {

    fun intent(draft: SmsDraft): Intent =
        Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(draft.number)))
            .putExtra("sms_body", draft.body)

    /** Returns false when no SMS app can handle the intent. */
    fun launch(context: Context, draft: SmsDraft): Boolean {
        val intent = intent(draft)
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    fun launch(context: Context, emirate: Emirate, plate: String, zone: String, hours: Int): Boolean =
        launch(context, Parking.smsDraft(emirate, plate, zone, hours))
}
