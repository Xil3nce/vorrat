package de.frelina.vorrat.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import de.frelina.vorrat.domain.ExpiryLogic
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

/** Verlauf/Benachrichtigungen im Haushalt (pro Gerät). */
data class HouseholdSettings(
    /** Name, unter dem dieses Handy im Verlauf erscheint (z. B. "Frank"); leer = ohne Namen. */
    val myName: String = "",
    /** Wird etwas aufgebraucht, die andere Person automatisch benachrichtigen ("Milch ist alle"). */
    val notifyWhenEmpty: Boolean = true,
    /** Hinweise der anderen Person als Benachrichtigung anzeigen. */
    val receiveNotices: Boolean = true,
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

    val householdSettings: Flow<HouseholdSettings> = dataStore.data.map { p ->
        HouseholdSettings(
            myName = p[MY_NAME].orEmpty(),
            notifyWhenEmpty = p[NOTIFY_EMPTY] ?: true,
            receiveNotices = p[RECEIVE_NOTICES] ?: true,
        )
    }

    suspend fun currentHousehold(): HouseholdSettings = householdSettings.first()

    /** Einkaufsliste nach Abteilungen gruppieren (pro Gerät, Standard: an). */
    val groupShoppingByDepartment: Flow<Boolean> = dataStore.data.map { p -> p[SHOPPING_BY_DEPARTMENT] ?: true }

    suspend fun setGroupShoppingByDepartment(enabled: Boolean) = dataStore.edit { it[SHOPPING_BY_DEPARTMENT] = enabled }

    /** Im Scanner-Modus "+ Bestand" gewählter Ort für neue Artikel (pro Gerät); null = kein Ort. */
    val scanLocationId: Flow<String?> = dataStore.data.map { p -> p[SCAN_LOCATION] }

    suspend fun setScanLocationId(id: String?) = dataStore.edit {
        if (id == null) it.remove(SCAN_LOCATION) else it[SCAN_LOCATION] = id
    }

    suspend fun setMyName(name: String) = dataStore.edit { it[MY_NAME] = name.trim().take(40) }

    suspend fun setNotifyWhenEmpty(enabled: Boolean) = dataStore.edit { it[NOTIFY_EMPTY] = enabled }

    suspend fun setReceiveNotices(enabled: Boolean) = dataStore.edit { it[RECEIVE_NOTICES] = enabled }

    /** Zeitpunkt (ms) des neuesten bereits gezeigten Hinweises; null = noch nie geprüft. */
    suspend fun noticesSeenUntil(): Long? = dataStore.data.first()[NOTICES_SEEN_UNTIL]

    suspend fun setNoticesSeenUntil(value: Long) = dataStore.edit { it[NOTICES_SEEN_UNTIL] = value }

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
        val MY_NAME = androidx.datastore.preferences.core.stringPreferencesKey("household_my_name")
        val NOTIFY_EMPTY = booleanPreferencesKey("household_notify_empty")
        val RECEIVE_NOTICES = booleanPreferencesKey("household_receive_notices")
        val NOTICES_SEEN_UNTIL = longPreferencesKey("household_notices_seen_until")
        val SCAN_LOCATION = androidx.datastore.preferences.core.stringPreferencesKey("scan_purchase_location")
        val SHOPPING_BY_DEPARTMENT = booleanPreferencesKey("shopping_by_department")
    }
}
