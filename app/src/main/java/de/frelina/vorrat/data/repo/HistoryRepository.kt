package de.frelina.vorrat.data.repo

import android.util.Log
import de.frelina.vorrat.data.db.HistoryAction
import de.frelina.vorrat.data.db.HistoryDao
import de.frelina.vorrat.data.db.HistoryEventEntity
import de.frelina.vorrat.data.db.ProductDao
import de.frelina.vorrat.data.db.QuantityTypeDao
import de.frelina.vorrat.data.db.StockEntryEntity
import de.frelina.vorrat.data.household.HouseholdStore
import de.frelina.vorrat.data.settings.SettingsRepository
import de.frelina.vorrat.data.sync.RemoteCollection
import de.frelina.vorrat.data.sync.RemoteMapper
import de.frelina.vorrat.data.sync.RemoteWriter
import de.frelina.vorrat.domain.HistoryLogic
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Eine Bestandsänderung für den Verlauf. [quantity] ist immer positiv. */
data class StockChange(
    val action: HistoryAction,
    val entry: StockEntryEntity,
    val quantity: Int,
    /** Nach der Änderung ist vom Produkt nichts mehr im Bestand. */
    val outOfStock: Boolean = false,
)

/** Schnittstelle für das StockRepository (Tests ohne Verlauf nutzen [NONE]). */
interface HistoryRecorder {
    /** @return ID des Verlaufseintrags oder null, wenn nichts protokolliert wurde. */
    suspend fun record(change: StockChange): String?

    /** Verlaufseintrag zurücknehmen (z. B. nach "Rückgängig"). */
    suspend fun discard(eventId: String)

    companion object {
        val NONE = object : HistoryRecorder {
            override suspend fun record(change: StockChange): String? = null
            override suspend fun discard(eventId: String) = Unit
        }
    }
}

/**
 * Verlauf im Haushalt: wer hat wann was hinzugefügt oder entnommen, dazu Nachrichten wie "Milch ist alle".
 * Wie alle Daten: Room sofort, dann Firestore (`history/{id}`); das andere Handy bekommt die Einträge über den
 * Snapshot-Listener und zeigt Hinweise mit `notify = true` als Benachrichtigung (siehe HouseholdNotices).
 * Fehler beim Protokollieren dürfen die eigentliche Bestandsänderung nie verhindern.
 */
@Singleton
class HistoryRepository @Inject constructor(
    private val dao: HistoryDao,
    private val productDao: ProductDao,
    private val quantityTypeDao: QuantityTypeDao,
    private val remote: RemoteWriter,
    private val household: HouseholdStore,
    private val settings: SettingsRepository,
) : HistoryRecorder {

    fun observeRecent(limit: Int = MAX_SHOWN): Flow<List<HistoryEventEntity>> = dao.observeRecent(limit)

    override suspend fun record(change: StockChange): String? = try {
        val product = productDao.getById(change.entry.productId)
        val symbol = quantityTypeDao.getById(change.entry.quantityTypeId)?.symbol
        val notify = change.outOfStock && settings.currentHousehold().notifyWhenEmpty
        val event = newEvent(
            action = change.action,
            productId = change.entry.productId,
            productName = product?.name.orEmpty(),
            quantity = change.quantity.takeIf { it > 0 },
            quantityTypeSymbol = symbol,
            entryId = change.entry.id,
            outOfStock = change.outOfStock,
            notify = notify,
        )
        save(event)
        event.id
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        Log.w(TAG, "Verlauf nicht gespeichert", e)
        null
    }

    override suspend fun discard(eventId: String) {
        dao.deleteById(eventId)
        remote.delete(RemoteCollection.HISTORY, eventId)
    }

    /**
     * Nachricht an den Haushalt (immer mit Benachrichtigung), z. B. "Milch ist alle".
     * @return false bei leerem Text.
     */
    suspend fun sendMessage(text: String, productId: String? = null, productName: String? = null): Boolean {
        val message = HistoryLogic.cleanText(text, RemoteMapper.MAX_TEXT) ?: return false
        save(
            newEvent(
                action = HistoryAction.MESSAGE,
                productId = productId,
                productName = productName.orEmpty(),
                message = message,
                notify = true,
            ),
        )
        return true
    }

    /** Für einen "ist alle"-Eintrag nachträglich Bescheid geben. */
    suspend fun notifyLater(eventId: String) {
        val event = dao.getById(eventId) ?: return
        if (event.notify) return
        // Neuer Zeitstempel, damit das andere Handy den Hinweis als neu erkennt.
        save(event.copy(notify = true, createdAt = System.currentTimeMillis()))
    }

    /** Einträge älter als [HistoryLogic.RETENTION_DAYS] entfernen (lokal und im Haushalt). */
    suspend fun prune(now: Long = System.currentTimeMillis()) {
        val cutoff = HistoryLogic.retentionCutoff(now)
        val old = dao.getIdsOlderThan(cutoff)
        if (old.isEmpty()) return
        dao.deleteOlderThan(cutoff)
        old.forEach { remote.delete(RemoteCollection.HISTORY, it) }
    }

    private suspend fun newEvent(
        action: HistoryAction,
        productId: String?,
        productName: String,
        quantity: Int? = null,
        quantityTypeSymbol: String? = null,
        entryId: String? = null,
        outOfStock: Boolean = false,
        message: String? = null,
        notify: Boolean = false,
    ) = HistoryEventEntity(
        createdAt = System.currentTimeMillis(),
        action = action,
        authorUid = household.current()?.uid?.takeIf { it.isNotBlank() },
        authorName = HistoryLogic.cleanText(settings.currentHousehold().myName, RemoteMapper.MAX_NAME),
        productId = productId,
        productName = productName,
        quantity = quantity,
        quantityTypeSymbol = quantityTypeSymbol,
        entryId = entryId,
        outOfStock = outOfStock,
        message = message,
        notify = notify,
    )

    private suspend fun save(event: HistoryEventEntity) {
        dao.upsert(event)
        remote.putHistory(event)
    }

    private companion object {
        const val TAG = "HistoryRepository"
        const val MAX_SHOWN = 500
    }
}
