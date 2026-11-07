package de.frelina.vorrat.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import de.frelina.vorrat.MainActivity
import de.frelina.vorrat.R
import de.frelina.vorrat.data.db.StockItem
import de.frelina.vorrat.domain.Validation
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.notification_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** @return true, wenn die Benachrichtigung angezeigt wurde. */
    fun show(digest: ReminderDigest): Boolean {
        if (!hasPermission()) return false
        createChannel()

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_FILTER, MainActivity.FILTER_SOON)
        }
        val pending = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val res = context.resources
        val title = res.getQuantityString(R.plurals.notification_title, digest.total, digest.total)
        val style = NotificationCompat.InboxStyle()
        val lines = buildList {
            digest.expired.forEach { add(res.getString(R.string.notification_line_expired, label(it))) }
            digest.today.forEach { add(res.getString(R.string.notification_line_today, it.productName)) }
            digest.soon.forEach { add(res.getString(R.string.notification_line_soon, label(it))) }
        }
        lines.take(MAX_LINES).forEach(style::addLine)
        if (lines.size > MAX_LINES) {
            style.setSummaryText(res.getString(R.string.notification_more, lines.size - MAX_LINES))
        }
        val summary = res.getString(
            R.string.notification_summary,
            digest.expired.size,
            digest.today.size,
            digest.soon.size,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(style)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun label(item: StockItem) =
        "${item.productName} (${Validation.formatDate(item.expiryDate)})"

    companion object {
        const val CHANNEL_ID = "expiry_reminders"
        private const val NOTIFICATION_ID = 1001
        private const val MAX_LINES = 6
    }
}
