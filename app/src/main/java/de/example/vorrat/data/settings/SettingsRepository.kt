package de.example.vorrat.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import de.example.vorrat.domain.ExpiryLogic
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class ReminderSettings(
    val enabled: Boolean = true,
    val hour: Int = 9,
    val minute: Int = 0,
    val leadDays: Int = ExpiryLogic.DEFAULT_LEAD_DAYS,
)

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val reminderSettings: Flow<ReminderSettings> = dataStore.data.map { p ->
        ReminderSettings(
            enabled = p[ENABLED] ?: true,
            hour = p[HOUR] ?: 9,
            minute = p[MINUTE] ?: 0,
            leadDays = p[LEAD_DAYS] ?: ExpiryLogic.DEFAULT_LEAD_DAYS,
        )
    }

    suspend fun current(): ReminderSettings = reminderSettings.first()

    suspend fun setEnabled(enabled: Boolean) = dataStore.edit { it[ENABLED] = enabled }

    suspend fun setTime(hour: Int, minute: Int) = dataStore.edit {
        it[HOUR] = hour.coerceIn(0, 23)
        it[MINUTE] = minute.coerceIn(0, 59)
    }

    suspend fun setLeadDays(days: Int) = dataStore.edit { it[LEAD_DAYS] = days.coerceIn(0, 60) }

    suspend fun lastNotifiedDate(): LocalDate? =
        dataStore.data.first()[LAST_NOTIFIED]?.let(LocalDate::ofEpochDay)

    suspend fun setLastNotifiedDate(date: LocalDate) =
        dataStore.edit { it[LAST_NOTIFIED] = date.toEpochDay() }

    private companion object {
        val ENABLED = booleanPreferencesKey("reminders_enabled")
        val HOUR = intPreferencesKey("reminder_hour")
        val MINUTE = intPreferencesKey("reminder_minute")
        val LEAD_DAYS = intPreferencesKey("reminder_lead_days")
        val LAST_NOTIFIED = longPreferencesKey("reminder_last_notified")
    }
}
