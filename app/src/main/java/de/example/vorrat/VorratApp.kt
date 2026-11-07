package de.example.vorrat

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import de.example.vorrat.data.db.LookupCacheDao
import de.example.vorrat.data.db.ProductDao
import de.example.vorrat.data.image.ImageStore
import de.example.vorrat.reminder.ReminderNotifications
import de.example.vorrat.reminder.ReminderScheduler
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

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        notifications.createChannel()
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
