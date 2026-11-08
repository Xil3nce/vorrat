package de.frelina.vorrat

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import de.frelina.vorrat.data.db.LookupCacheDao
import de.frelina.vorrat.data.db.ProductDao
import de.frelina.vorrat.data.image.ImageStore
import de.frelina.vorrat.data.sync.SyncManager
import de.frelina.vorrat.reminder.ReminderNotifications
import de.frelina.vorrat.reminder.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class VorratApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var reminderScheduler: ReminderScheduler
    @Inject lateinit var notifications: ReminderNotifications
    @Inject lateinit var imageStore: ImageStore
    @Inject lateinit var productDao: ProductDao
    @Inject lateinit var lookupCacheDao: LookupCacheDao
    @Inject lateinit var syncManager: SyncManager
    @Inject lateinit var householdNotices: de.frelina.vorrat.reminder.HouseholdNotices
    @Inject lateinit var historyRepository: de.frelina.vorrat.data.repo.HistoryRepository
    @Inject lateinit var settingsRepository: de.frelina.vorrat.data.settings.SettingsRepository
    @Inject lateinit var workManager: androidx.work.WorkManager

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        notifications.createChannel()
        householdNotices.createChannel()
        appScope.launch {
            householdNotices.initialize()
            de.frelina.vorrat.reminder.HouseholdSyncWorker.schedule(
                workManager,
                settingsRepository.currentHousehold().receiveNotices,
            )
            // Alten Verlauf aufräumen (auf beiden Handys, doppelt löschen schadet nicht).
            runCatching { historyRepository.prune() }
        }
        // Sobald ein Haushalt bekannt ist: Firestore-Listener → Room.
        syncManager.start()
        appScope.launch { reminderScheduler.ensureScheduled() }
        // Verwaiste Bilder (z. B. abgebrochene Erfassung) entfernen.
        appScope.launch {
            runCatching {
                val referenced = productDao.getAllImagePaths().toSet() + lookupCacheDao.getAllImagePaths().toSet()
                imageStore.deleteUnreferenced(referenced)
            }
        }
    }
}
