package ae.sayyara.automotive

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.car.app.notification.CarPendingIntent
import kotlin.math.roundToInt

/**
 * Head-unit notification "Refuel detected, tap to confirm". Tapping opens our car app (through CarPendingIntent),
 * and [SayyaraAutomotiveSession.onNewIntent] shows the pending list. Low importance on purpose: no heads-up popup
 * while driving, the entry simply waits in the notification centre.
 */
internal object RefuelNotifier {
    private const val CHANNEL_ID = "aaos_refuel"
    private const val NOTIFICATION_ID = 4301
    const val ACTION_SHOW_PENDING = "ae.sayyara.automotive.SHOW_PENDING"

    fun notifyDetected(context: Context, item: PendingRefuel) {
        val app = context.applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !AaosPermissions.granted(app, Manifest.permission.POST_NOTIFICATIONS)
        ) return
        val nm = app.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, app.getString(R.string.aaos_channel_name), NotificationManager.IMPORTANCE_LOW),
            )
        }
        val intent = Intent(ACTION_SHOW_PENDING)
            .setComponent(ComponentName(app, SayyaraAutomotiveCarAppService::class.java))
        val pending = runCatching {
            CarPendingIntent.getCarApp(app, NOTIFICATION_ID, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }.getOrNull()
        val text = when (item.kind) {
            PendingRefuel.Kind.FUEL -> app.getString(R.string.aaos_notif_fuel, (item.amount ?: 0.0).roundToInt())
            PendingRefuel.Kind.CHARGE -> item.amount?.let { app.getString(R.string.aaos_notif_charge_kwh, it.roundToInt()) }
                ?: app.getString(R.string.aaos_notif_charge_soc, item.levelBeforePercent.roundToInt(), item.levelAfterPercent.roundToInt())
        }
        val notification = Notification.Builder(app, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_input_add)
            .setContentTitle(app.getString(R.string.aaos_notif_title))
            .setContentText(text)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .apply { if (pending != null) setContentIntent(pending) }
            .build()
        runCatching { nm.notify(NOTIFICATION_ID, notification) }
    }
}
