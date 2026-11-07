package de.example.vorrat.reminder

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import de.example.vorrat.data.settings.ReminderSettings
import de.example.vorrat.data.settings.SettingsRepository
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plant die tägliche Erinnerung als periodische WorkManager-Arbeit (24 h).
 *
 * Hinweis: WorkManager garantiert keine exakte Uhrzeit. Die erste Ausführung wird auf die
 * eingestellte Uhrzeit gelegt; danach kann sie durch Doze/Akku-Optimierung und das
 * Flex-Intervall um einige Minuten bis Stunden abweichen. Siehe README.
 *
 * WorkManager speichert geplante Arbeit selbst persistent und plant sie nach einem
 * Geräteneustart neu. Zusätzlich ruft [RescheduleReceiver] nach Neustart/App-Update
 * [ensureScheduled] auf.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val settings: SettingsRepository,
) {
    /** Plant nur, falls noch nicht geplant (App-Start, Neustart, Update). */
    suspend fun ensureScheduled() = apply(settings.current(), ExistingPeriodicWorkPolicy.KEEP)

    /** Nach Änderung von Uhrzeit/An-Aus: neu planen. */
    suspend fun reschedule() = apply(settings.current(), ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE)

    private fun apply(s: ReminderSettings, policy: ExistingPeriodicWorkPolicy) {
        if (!s.enabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val delay = initialDelay(ZonedDateTime.now(), s.hour, s.minute)
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniquePeriodicWork(WORK_NAME, policy, request)
    }

    companion object {
        const val WORK_NAME = "expiry_reminder"

        /** Zeit bis zum nächsten Auftreten von hour:minute (heute oder morgen). */
        fun initialDelay(now: ZonedDateTime, hour: Int, minute: Int): Duration {
            var next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            return Duration.between(now, next)
        }
    }
}
