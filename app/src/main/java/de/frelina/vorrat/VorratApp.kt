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
    @Inject lateinit var reminderScheduler: dagger.Lazy<ReminderScheduler>
    @Inject lateinit var notifications: ReminderNotifications
    @Inject lateinit var imageStore: dagger.Lazy<ImageStore>
    @Inject lateinit var productDao: dagger.Lazy<ProductDao>
    @Inject lateinit var lookupCacheDao: dagger.Lazy<LookupCacheDao>
    @Inject lateinit var recipeDao: dagger.Lazy<de.frelina.vorrat.data.db.RecipeDao>
    @Inject lateinit var syncManager: dagger.Lazy<SyncManager>
    @Inject lateinit var householdNotices: de.frelina.vorrat.reminder.HouseholdNotices
    @Inject lateinit var historyRepository: dagger.Lazy<de.frelina.vorrat.data.repo.HistoryRepository>
    @Inject lateinit var settingsRepository: dagger.Lazy<de.frelina.vorrat.data.settings.SettingsRepository>
    @Inject lateinit var workManager: dagger.Lazy<androidx.work.WorkManager>
    @Inject lateinit var householdStore: dagger.Lazy<de.frelina.vorrat.data.household.HouseholdStore>
    @Inject lateinit var departmentRepository: dagger.Lazy<de.frelina.vorrat.data.repo.DepartmentRepository>
    @Inject lateinit var purchaseDrafts: dagger.Lazy<de.frelina.vorrat.data.purchase.PurchaseDraftStore>
    @Inject lateinit var trash: dagger.Lazy<de.frelina.vorrat.data.trash.TrashStore>

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        notifications.createChannel()
        householdNotices.createChannel()
        // Alles Weitere im Hintergrund: Datenbank, Firestore und WorkManager werden erst hier (über dagger.Lazy)
        // erzeugt und blockieren so nicht den ersten Frame.
        appScope.launch {
            // Sobald ein Haushalt bekannt ist: Firestore-Listener → Room.
            syncManager.get().start()
        }
        appScope.launch {
            householdNotices.initialize()
            de.frelina.vorrat.reminder.HouseholdSyncWorker.schedule(
                workManager.get(),
                settingsRepository.get().currentHousehold().receiveNotices,
            )
            // Alten Verlauf aufräumen (auf beiden Handys, doppelt löschen schadet nicht).
            runCatching { historyRepository.get().prune() }
        }
        // Ohne Haushalt gibt es keinen Server-Stand, auf den man warten müsste: Standard-Abteilungen gleich anlegen.
        // Mit Haushalt übernimmt das der SyncManager nach dem ersten Server-Stand (sonst würde ein beitretendes
        // Handy die Reihenfolge des Haushalts überschreiben).
        appScope.launch {
            if (householdStore.get().currentId() == null) runCatching { departmentRepository.get().ensureDefaults() }
        }
        appScope.launch { reminderScheduler.get().ensureScheduled() }
        // Papierkorb: nach 30 Tagen endgültig löschen; danach verwaiste Bilder (z. B. abgebrochene Erfassung,
        // Fotos endgültig gelöschter Rezepte) entfernen.
        appScope.launch {
            runCatching { trash.get().purgeExpired() }
            runCatching {
                val referenced = productDao.get().getAllImagePaths().toSet() +
                    lookupCacheDao.get().getAllImagePaths().toSet() +
                    purchaseDrafts.get().imagePaths() + recipeDao.get().getAllImagePaths() + trash.get().imagePaths()
                imageStore.get().deleteUnreferenced(referenced)
            }
        }
    }
}
