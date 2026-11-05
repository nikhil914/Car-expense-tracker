package ae.sayyara.app.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-enqueues the periodic workers after a reboot (and after an app update, if the manifest also
 * lists MY_PACKAGE_REPLACED). WorkManager normally persists its schedule; this is a safety net for
 * OEMs that clear it. KEEP policies make it idempotent.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> WorkScheduler.scheduleAll(context.applicationContext)
        }
    }
}
