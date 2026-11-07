package de.frelina.vorrat.data.household

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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
        }
    }

    private companion object {
        val ID = stringPreferencesKey("household_id")
        val CODE = stringPreferencesKey("household_code")
        val UID = stringPreferencesKey("household_uid")
    }
}
