package de.frelina.vorrat.data.household

import android.util.Log
import androidx.room.withTransaction
import de.frelina.vorrat.data.db.VorratDatabase
import de.frelina.vorrat.data.sync.RemoteStore
import de.frelina.vorrat.data.sync.RemoteWriter
import de.frelina.vorrat.domain.InviteCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

sealed interface HouseholdResult {
    data object Success : HouseholdResult

    /** Eingabe hat nicht das Format eines Einladungscodes. */
    data object InvalidCode : HouseholdResult

    /** Code hat das richtige Format, existiert aber nicht. */
    data object UnknownCode : HouseholdResult

    /** Keine Verbindung / Zeitüberschreitung / vom Server abgelehnt. */
    data object Failed : HouseholdResult
}

/** Haushalt anlegen oder beitreten (braucht eine Internetverbindung). */
@Singleton
class HouseholdRepository @Inject constructor(
    private val remote: RemoteStore,
    private val store: HouseholdStore,
    private val db: VorratDatabase,
    private val writer: RemoteWriter,
) {
    val household: Flow<Household?> = store.household

    @OptIn(ExperimentalCoroutinesApi::class)
    val memberCount: Flow<Int?> = store.household.flatMapLatest { h ->
        if (h == null) flowOf(null) else remote.observeMemberCount(h.id)
    }

    /** Neuer Haushalt: bestehende lokale Daten werden hochgeladen. */
    suspend fun create(): HouseholdResult = guarded {
        val uid = withTimeout(TIMEOUT_MS) { remote.signIn() }
        val code = InviteCode.generate()
        val hid = withTimeout(TIMEOUT_MS) { remote.createHousehold(uid, code) }
        writer.uploadAll(hid, db)
        store.set(Household(hid, code, uid))
    }

    /** Beitreten: lokale Daten werden durch die des Haushalts ersetzt (der Listener füllt Room danach). */
    suspend fun join(input: String): HouseholdResult {
        val code = InviteCode.normalize(input) ?: return HouseholdResult.InvalidCode
        var unknown = false
        val result = guarded {
            val uid = withTimeout(TIMEOUT_MS) { remote.signIn() }
            val hid = withTimeout(TIMEOUT_MS) { remote.findHouseholdByCode(code) }
            if (hid == null) {
                unknown = true
                return@guarded
            }
            withTimeout(TIMEOUT_MS) { remote.joinHousehold(hid, uid, code) }
            clearLocalData()
            store.set(Household(hid, code, uid))
        }
        return if (unknown) HouseholdResult.UnknownCode else result
    }

    private suspend fun clearLocalData() = db.withTransaction {
        db.stockEntryDao().deleteAll()
        db.productDao().deleteAll()
        db.locationDao().deleteAll()
        db.unitDao().deleteAll()
        db.quantityTypeDao().deleteAll()
    }

    private inline fun guarded(block: () -> Unit): HouseholdResult = try {
        block()
        HouseholdResult.Success
    } catch (e: TimeoutCancellationException) {
        Log.w(TAG, "Zeitüberschreitung", e)
        HouseholdResult.Failed
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        Log.w(TAG, "Haushalt-Aktion fehlgeschlagen", e)
        HouseholdResult.Failed
    }

    private companion object {
        const val TAG = "HouseholdRepository"
        const val TIMEOUT_MS = 20_000L
    }
}
