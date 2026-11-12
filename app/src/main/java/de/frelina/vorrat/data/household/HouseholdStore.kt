package de.frelina.vorrat.data.household

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Gemeinsamer Haushalt dieses Geräts: Firestore-ID, Einladungscode, UID der Mitgliedschaft. */
data class Household(val id: String, val code: String, val uid: String)

/** Haushaltszuordnung pro Gerät (DataStore, wird nicht synchronisiert). */
@Singleton
class HouseholdStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val household: Flow<Household?> = dataStore.data.map { p ->
        val id = p[ID] ?: return@map null
        Household(id, p[CODE].orEmpty(), p[UID].orEmpty())
    }.distinctUntilChanged()

    suspend fun current(): Household? = household.first()

    suspend fun currentId(): String? = current()?.id

    suspend fun set(household: Household) {
        dataStore.edit {
            it[ID] = household.id
            it[CODE] = household.code
            it[UID] = household.uid
            it.remove(REMOVED)
        }
    }

    /**
     * Haushaltszuordnung entfernen (verlassen oder entfernt worden). Der letzte Code bleibt als Vorschlag für
     * "Haushalt beitreten" gespeichert. [removed]: von einem anderen Handy entfernt – das Onboarding weist darauf hin.
     */
    suspend fun clear(removed: Boolean = false) {
        dataStore.edit {
            if (removed) it[REMOVED] = true else it.remove(REMOVED)
            it[CODE]?.takeIf(String::isNotBlank)?.let { code -> it[LAST_CODE] = code }
            it.remove(ID)
            it.remove(CODE)
            it.remove(UID)
        }
    }

    /** Nur den Code ersetzen (z. B. hat das andere Handy ihn erneuert). */
    suspend fun setCode(householdId: String, code: String) {
        dataStore.edit { if (it[ID] == householdId) it[CODE] = code }
    }

    /** Code des zuletzt verlassenen Haushalts (Vorbelegung im Onboarding). */
    val lastCode: Flow<String?> = dataStore.data.map { it[LAST_CODE] }.distinctUntilChanged()

    /** Dieses Handy wurde aus seinem Haushalt entfernt (bis zum nächsten Anlegen/Beitreten). */
    val removedNotice: Flow<Boolean> = dataStore.data.map { it[REMOVED] == true }.distinctUntilChanged()

    private companion object {
        val REMOVED = booleanPreferencesKey("household_removed")
        val LAST_CODE = stringPreferencesKey("household_last_code")
        val ID = stringPreferencesKey("household_id")
        val CODE = stringPreferencesKey("household_code")
        val UID = stringPreferencesKey("household_uid")
    }
}
