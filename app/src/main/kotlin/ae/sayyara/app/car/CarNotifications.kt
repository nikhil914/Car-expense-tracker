package ae.sayyara.app.car

import ae.sayyara.app.R
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Phone notifications posted from the car session. Tapping one opens MainActivity through a
 * `sayyara://` deep link (MainActivity owns the routing), so the user can finish a fill-up on the
 * phone once parked. These are plain phone notifications: no CarAppExtender, so they do not pop up
 * on the car screen while driving.
 */
internal object CarNotifications {
    private const val CHANNEL_ID = "car_refuel"
    const val ID_REFUEL_DRAFT = 4101
    const val ID_LOG_FILL = 4102

    /** Returns false when notifications are blocked (no POST_NOTIFICATIONS grant or channel disabled). */
    fun postDeepLink(context: Context, notificationId: Int, title: String, text: String, deepLink: String): Boolean {
        val app = context.applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        ensureChannel(app)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink))
            .setPackage(app.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            app, notificationId, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_input_add)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        val manager = NotificationManagerCompat.from(app)
        if (!manager.areNotificationsEnabled()) return false
        return try {
            manager.notify(notificationId, notification)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.car_notification_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.car_notification_channel_desc) },
        )
    }
}
