package de.frelina.vorrat.data.sync

import android.util.Log
import de.frelina.vorrat.data.db.ProductEntity
import de.frelina.vorrat.data.db.VorratDatabase
import de.frelina.vorrat.data.household.Household
import de.frelina.vorrat.data.household.HouseholdStore
import de.frelina.vorrat.data.image.ImageStore
import de.frelina.vorrat.data.repo.DepartmentRepository
import de.frelina.vorrat.di.ApplicationScope
import de.frelina.vorrat.reminder.HouseholdNotices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hält Room mit dem Haushalt in Firestore synchron: sobald ein Haushalt bekannt ist, hängt je Datensammlung
 * ein Snapshot-Listener; dessen Änderungen übernimmt [SyncApplier] nacheinander (eine Coroutine, Reihenfolge
 * bleibt erhalten). Fehlende Produktbilder werden nachgeladen.
 */
@Singleton
class SyncManager @Inject constructor(
    private val remote: RemoteStore,
    private val household: HouseholdStore,
    private val db: VorratDatabase,
    private val writer: RemoteWriter,
    private val imageStore: ImageStore,
    private val notices: HouseholdNotices,
    private val departments: DepartmentRepository,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val applier = SyncApplier(db)
    private val started = AtomicBoolean(false)

    /** Sammlungen, die seit dem Anhängen der Listener mindestens einen Server-Stand geliefert haben. */
    private val serverSynced = MutableStateFlow<Set<RemoteCollection>>(emptySet())

    private class Event(val hid: String, val collection: RemoteCollection, val snapshot: RemoteSnapshot, val full: Boolean)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            household.household.collectLatest { h ->
                serverSynced.value = emptySet()
                if (h != null) listen(h)
            }
        }
    }

    /**
     * Wartet höchstens [timeoutMs], bis alle Sammlungen einmal vom Server gelesen und in Room übernommen
     * wurden (für den ReminderWorker). Ohne Haushalt oder offline: false, Room bleibt wie es ist.
     */
    suspend fun awaitServerSync(
        timeoutMs: Long,
        collections: List<RemoteCollection> = RemoteCollection.STOCK,
    ): Boolean {
        if (household.currentId() == null) return false
        start()
        return withTimeoutOrNull(timeoutMs) {
            serverSynced.first { it.containsAll(collections) }
            true
        } ?: false
    }

    private suspend fun listen(h: Household) {
        ensureMembership(h)
        val events = Channel<Event>(Channel.UNLIMITED)
        val registrations = RemoteCollection.DATA.map { collection ->
            var reconciled = false
            remote.listen(h.id, collection) { snapshot ->
                // Erster Server-Stand → vollständiger Abgleich (lokal gelöschte Altlasten entfernen).
                val full = !snapshot.fromCache && !reconciled
                if (full) reconciled = true
                events.trySend(Event(h.id, collection, snapshot, full))
            }
        }
        try {
            for (event in events) process(event)
            awaitCancellation()
        } finally {
            registrations.forEach { it.remove() }
            events.close()
        }
    }

    private suspend fun process(e: Event) {
        try {
            val applied = applier.apply(
                e.collection,
                e.snapshot.changes,
                if (e.full) e.snapshot.allIds else null,
            )
            fetchMissingImages(e.hid, applied.products)
            notices.onIncoming(applied.history)
            // Hat der Haushalt noch keine Abteilungen (z. B. nach dem Update), die Standard-Abteilungen anlegen.
            if (e.full && e.collection == RemoteCollection.DEPARTMENTS) departments.ensureDefaults()
            if (e.full) serverSynced.value = serverSynced.value + e.collection
        } catch (ex: Exception) {
            Log.w(TAG, "Übernahme von ${e.collection.path} fehlgeschlagen", ex)
        }
    }

    private fun fetchMissingImages(hid: String, products: List<ProductEntity>) {
        val missing = products.mapNotNull { it.imagePath }.filter { imageStore.fileOrNull(it) == null }
        if (missing.isEmpty()) return
        scope.launch {
            missing.forEach { name ->
                runCatching { writer.downloadImage(hid, name) }
                    .onFailure { Log.w(TAG, "Bild $name nicht geladen", it) }
            }
        }
    }

    /**
     * Anonyme Anmeldung verloren (z. B. App-Daten teilweise gelöscht)? Dann mit dem gespeicherten Code erneut
     * als Mitglied eintragen. Offline wird es beim nächsten Start erneut versucht.
     */
    private suspend fun ensureMembership(h: Household) {
        if (remote.currentUid() != null && remote.currentUid() == h.uid) return
        try {
            val uid = remote.signIn()
            if (uid != h.uid) {
                val joined = withTimeoutOrNull(MEMBERSHIP_TIMEOUT_MS) { remote.joinHousehold(h.id, uid, h.code) }
                if (joined != null) household.set(h.copy(uid = uid))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Mitgliedschaft nicht erneuert", e)
        }
    }

    private companion object {
        const val TAG = "SyncManager"
        const val MEMBERSHIP_TIMEOUT_MS = 15_000L
    }
}
