package de.frelina.vorrat.reminder

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.frelina.vorrat.data.settings.SettingsRepository
import de.frelina.vorrat.data.sync.RemoteCollection
import de.frelina.vorrat.data.sync.SyncManager
import java.util.concurrent.TimeUnit

/**
 * Holt im Hintergrund etwa alle 15 Minuten den Verlauf des Haushalts, damit Hinweise der anderen Person
 * ("Milch ist alle") auch bei geschlossener App ankommen. Die Benachrichtigung selbst zeigt [HouseholdNotices],
 * sobald der SyncManager die neuen Einträge übernimmt. Android kann die Ausführung (Doze) verzögern.
 */
@HiltWorker
class HouseholdSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncManager: SyncManager,
    private val settings: SettingsRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!settings.currentHousehold().receiveNotices) return Result.success()
        syncManager.awaitServerSync(SYNC_TIMEOUT_MS, listOf(RemoteCollection.HISTORY))
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "household_sync"
        private const val SYNC_TIMEOUT_MS = 20_000L

        /** Ein- bzw. ausplanen (App-Start, Änderung der Einstellung). */
        fun schedule(workManager: WorkManager, enabled: Boolean) {
            if (!enabled) {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<HouseholdSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
