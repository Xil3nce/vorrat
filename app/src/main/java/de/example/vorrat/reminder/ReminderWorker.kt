package de.example.vorrat.reminder

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.example.vorrat.data.repo.StockRepository
import de.example.vorrat.data.settings.SettingsRepository
import java.time.LocalDate

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val stockRepository: StockRepository,
    private val settings: SettingsRepository,
    private val notifications: ReminderNotifications,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val s = settings.current()
        if (!s.enabled) return Result.success()
        val today = LocalDate.now()
        // Höchstens eine Sammelbenachrichtigung pro Tag (z. B. nach Neuplanung am selben Tag).
        if (settings.lastNotifiedDate() == today) return Result.success()

        val digest = ReminderDigest.from(stockRepository.allItems(), today, s.leadDays)
        if (digest.isEmpty) return Result.success() // nichts steht an → keine Benachrichtigung
        if (notifications.show(digest, s.leadDays)) {
            settings.setLastNotifiedDate(today)
        }
        return Result.success()
    }
}
