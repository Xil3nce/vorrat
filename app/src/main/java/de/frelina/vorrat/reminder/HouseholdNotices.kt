package de.frelina.vorrat.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import de.frelina.vorrat.MainActivity
import de.frelina.vorrat.R
import de.frelina.vorrat.data.db.HistoryAction
import de.frelina.vorrat.data.db.HistoryEventEntity
import de.frelina.vorrat.data.household.HouseholdStore
import de.frelina.vorrat.data.settings.SettingsRepository
import de.frelina.vorrat.domain.HistoryLogic
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hinweise der anderen Person ("Milch ist alle") als Benachrichtigung. Der SyncManager reicht alle neu
 * übernommenen Verlaufseinträge herein; gezeigt wird nur, was [HistoryLogic.noticesToShow] auswählt.
 * Ein Kein-Server-Push-Aufbau: Die Hinweise kommen an, solange die App läuft oder [HouseholdSyncWorker]
 * (etwa alle 15 Minuten) den Haushalt abfragt.
 */
@Singleton
class HouseholdNotices @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val household: HouseholdStore,
    private val reminders: ReminderNotifications,
) {
    private val mutex = Mutex()

    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notice_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.notice_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Beim ersten Start nur künftige Hinweise zeigen (nicht den gesamten bisherigen Verlauf). */
    suspend fun initialize() = mutex.withLock {
        if (settings.noticesSeenUntil() == null) settings.setNoticesSeenUntil(System.currentTimeMillis())
    }

    suspend fun onIncoming(events: List<HistoryEventEntity>) {
        if (events.isEmpty()) return
        mutex.withLock {
            val now = System.currentTimeMillis()
            val seen = settings.noticesSeenUntil() ?: now.also { settings.setNoticesSeenUntil(it) }
            val toShow = HistoryLogic.noticesToShow(events, household.current()?.uid, seen, now)
            if (toShow.isEmpty()) return
            // Abgeschaltet: trotzdem als gesehen markieren, damit beim Einschalten kein Rückstau kommt.
            if (settings.currentHousehold().receiveNotices) toShow.forEach(::show)
            settings.setNoticesSeenUntil(HistoryLogic.newSeenUntil(seen, toShow))
        }
    }

    private fun show(event: HistoryEventEntity) {
        if (!reminders.hasPermission()) return
        createChannel()
        val res = context.resources
        val who = event.authorName ?: res.getString(R.string.history_someone)
        val (title, text) = when {
            event.action == HistoryAction.MESSAGE ->
                res.getString(R.string.notice_message_title, who) to event.message.orEmpty()
            else ->
                res.getString(R.string.notice_out_of_stock_title, event.productName) to
                    res.getString(R.string.notice_out_of_stock_text, who)
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_FILTER, MainActivity.OPEN_HISTORY)
        }
        val pending = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setWhen(event.createdAt)
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(event.id.hashCode(), notification)
        } catch (_: SecurityException) {
            // Berechtigung inzwischen entzogen – Hinweis bleibt im Verlauf sichtbar.
        }
    }

    companion object {
        const val CHANNEL_ID = "household_notices"
        private const val REQUEST_CODE = 2001
    }
}
