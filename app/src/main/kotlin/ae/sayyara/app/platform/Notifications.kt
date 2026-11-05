package ae.sayyara.app.platform

import ae.sayyara.app.R
import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Deep links handled by MainActivity (see docs/BUILD_BRIEF.md). */
object DeepLinks {
    const val ADD_FILL = "sayyara://add-fill"
    const val DASHBOARD = "sayyara://dashboard"
    const val ADD_EXPENSE = "sayyara://expenses/add"
    const val IMPORT = "sayyara://import"
    const val ROUTE = "sayyara://route"

    fun intent(context: Context, link: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(link)).setPackage(context.packageName)
}

/** Notification channels and posting helpers. Posting is a no-op without POST_NOTIFICATIONS. */
object Notifications {
    const val CHANNEL_PRICES = "fuel_prices"
    const val CHANNEL_REMINDERS = "reminders"
    const val CHANNEL_BACKUP = "backup"

    const val ID_PRICES = 1001
    const val ID_BACKUP = 1002
    private const val REMINDER_ID_BASE = 20_000

    fun createChannels(context: Context) {
        val nm = NotificationManagerCompat.from(context)
        nm.createNotificationChannelsCompat(
            listOf(
                NotificationChannelCompat.Builder(CHANNEL_PRICES, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName(context.getString(R.string.channel_prices_name))
                    .setDescription(context.getString(R.string.channel_prices_description))
                    .build(),
                NotificationChannelCompat.Builder(CHANNEL_REMINDERS, NotificationManagerCompat.IMPORTANCE_HIGH)
                    .setName(context.getString(R.string.channel_reminders_name))
                    .setDescription(context.getString(R.string.channel_reminders_description))
                    .build(),
                NotificationChannelCompat.Builder(CHANNEL_BACKUP, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName(context.getString(R.string.channel_backup_name))
                    .setDescription(context.getString(R.string.channel_backup_description))
                    .build(),
            ),
        )
    }

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun reminderNotificationId(reminderId: String): Int = REMINDER_ID_BASE + (reminderId.hashCode() and 0x7FFF)

    fun deepLinkPendingIntent(context: Context, link: String, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            DeepLinks.intent(context, link).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun postPrices(context: Context, title: String, text: String, bigText: String = text) {
        post(
            context, ID_PRICES,
            NotificationCompat.Builder(context, CHANNEL_PRICES)
                .setSmallIcon(android.R.drawable.stat_notify_more)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setContentIntent(deepLinkPendingIntent(context, DeepLinks.DASHBOARD, ID_PRICES))
                .setAutoCancel(true),
        )
    }

    fun postReminder(context: Context, reminderId: String, title: String, text: String) {
        val id = reminderNotificationId(reminderId)
        post(
            context, id,
            NotificationCompat.Builder(context, CHANNEL_REMINDERS)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(title)
                .setContentText(text)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(deepLinkPendingIntent(context, DeepLinks.DASHBOARD, id))
                .setAutoCancel(true),
        )
    }

    fun postBackup(context: Context, title: String, text: String) {
        post(
            context, ID_BACKUP,
            NotificationCompat.Builder(context, CHANNEL_BACKUP)
                .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(deepLinkPendingIntent(context, DeepLinks.DASHBOARD, ID_BACKUP))
                .setAutoCancel(true),
        )
    }

    @SuppressLint("MissingPermission") // checked in canPost()
    private fun post(context: Context, id: Int, builder: NotificationCompat.Builder) {
        if (!canPost(context)) return
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call.
        }
    }
}
