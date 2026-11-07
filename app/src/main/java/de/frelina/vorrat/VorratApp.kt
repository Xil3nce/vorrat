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

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        notifications.createChannel()
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
