package de.frelina.vorrat.data.repo

import androidx.room.withTransaction
import de.frelina.vorrat.data.db.HistoryAction
import de.frelina.vorrat.data.db.ProductDao
import de.frelina.vorrat.data.db.ProductEntity
import de.frelina.vorrat.data.db.StockEntryDao
import de.frelina.vorrat.data.db.StockEntryEntity
import de.frelina.vorrat.data.db.StockItem
import de.frelina.vorrat.data.db.VorratDatabase
import de.frelina.vorrat.data.sync.RemoteCollection
import de.frelina.vorrat.data.sync.RemoteWriter
import de.frelina.vorrat.data.trash.TrashItem
import de.frelina.vorrat.data.trash.TrashKind
import de.frelina.vorrat.data.trash.TrashStore
import de.frelina.vorrat.data.trash.TrashedEntry
import de.frelina.vorrat.domain.StockMerge
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

sealed interface QuantityChange {
    data class Updated(val newQuantity: Int) : QuantityChange

    /** Anzahl fiel auf 0 → Eintrag gelöscht. [removed] erlaubt "Rückgängig". */
    data class Removed(val removed: StockEntryEntity) : QuantityChange

    data object NotFound : QuantityChange
}

@Singleton
class StockRepository @Inject constructor(
    private val db: VorratDatabase,
    private val stockDao: StockEntryDao,
    private val productDao: ProductDao,
    private val remote: RemoteWriter,
    private val history: HistoryRecorder = HistoryRecorder.NONE,
    /** Papierkorb (null in Tests ohne Papierkorb). */
    private val trash: TrashStore? = null,
) {
    /** Verlaufseintrag der letzten Entnahme/Löschung je Eintrag – "Rückgängig" nimmt ihn zurück. */
    private val removalEvents = ConcurrentHashMap<String, String>()

    private suspend fun log(action: HistoryAction, entry: StockEntryEntity, quantity: Int): String? {
        if (quantity <= 0) return null
        val outOfStock = action != HistoryAction.ADDED && action != HistoryAction.OPENED &&
            stockDao.totalQuantity(entry.productId) == 0
        return history.record(StockChange(action, entry, quantity, outOfStock))
    }

    private fun rememberRemoval(entryId: String, eventId: String?) {
        if (eventId != null) removalEvents[entryId] = eventId else removalEvents.remove(entryId)
    }

    /** Einträge, die mit dem NFC-Chip [tagId] verknüpft sind (normalerweise höchstens einer). */
    suspend fun entriesForNfcTag(tagId: String): List<StockEntryEntity> = stockDao.getByNfcTag(tagId)

    /**
     * Verknüpft den NFC-Chip [tagId] mit dem Eintrag [entryId]; andere Einträge mit demselben Chip verlieren
     * die Verknüpfung (ein Chip wird für die nächste Portion wiederverwendet).
     */
    suspend fun assignNfcTag(entryId: String, tagId: String): Boolean {
        val changed = db.withTransaction {
            val entry = stockDao.getById(entryId) ?: return@withTransaction null
            val others = stockDao.getByNfcTag(tagId).filter { it.id != entryId }.map { it.copy(nfcTagId = null) }
            others.forEach { stockDao.update(it) }
            val updated = entry.copy(nfcTagId = tagId)
            stockDao.update(updated)
            others + updated
        } ?: return false
        changed.forEach { remote.putEntry(it) }
        return true
    }

    /** Ein Chip gehört höchstens zu einem Eintrag: anderen Einträgen mit dem Chip von [entry] die Verknüpfung nehmen. */
    private suspend fun releaseNfcTag(entry: StockEntryEntity) {
        val tag = entry.nfcTagId ?: return
        stockDao.getByNfcTag(tag).filter { it.id != entry.id }.forEach { other ->
            val cleared = other.copy(nfcTagId = null)
            stockDao.update(cleared)
            remote.putEntry(cleared)
        }
    }

    /** Verknüpfung mit dem NFC-Chip lösen. */
    suspend fun clearNfcTag(entryId: String) {
        val entry = stockDao.getById(entryId)?.takeIf { it.nfcTagId != null } ?: return
        val updated = entry.copy(nfcTagId = null)
        stockDao.update(updated)
        remote.putEntry(updated)
    }

    fun observeItems(): Flow<List<StockItem>> = stockDao.observeAllItems()

    fun observeItem(entryId: String): Flow<StockItem?> = stockDao.observeItem(entryId)

    suspend fun allItems(): List<StockItem> = stockDao.getAllItems()

    suspend fun itemsForProduct(productId: String): List<StockItem> = stockDao.getItemsForProduct(productId)

    suspend fun getEntry(id: String): StockEntryEntity? = stockDao.getById(id)

    /** Anderer Bestandseintrag mit exakt denselben Daten wie [entry] (für die Frage "Zusammenführen?"). */
    suspend fun findTwin(entry: StockEntryEntity): StockEntryEntity? =
        StockMerge.findTwin(entry, stockDao.getEntriesForProduct(entry.productId))

    /**
     * Führt [entry] mit dem gleichen Eintrag [targetId] zusammen: dessen Anzahl erhöht sich um die Anzahl von
     * [entry]. Existiert [entry] bereits (Bearbeiten/Verschieben), wird er danach gelöscht.
     * Liefert false, wenn das Ziel inzwischen nicht mehr existiert (z. B. auf dem anderen Handy gelöscht).
     */
    suspend fun mergeInto(targetId: String, entry: StockEntryEntity): Boolean {
        require(entry.quantity > 0) { "Anzahl muss mindestens 1 sein" }
        require(targetId != entry.id) { "Eintrag kann nicht mit sich selbst zusammengeführt werden" }
        // null = Ziel fehlt; sonst: ob [entry] schon gespeichert war (dann auch dessen Dokument löschen).
        val sourceExisted: Boolean? = db.withTransaction {
            val target = stockDao.getById(targetId) ?: return@withTransaction null
            stockDao.update(target.copy(quantity = target.quantity + entry.quantity))
            val existed = stockDao.getById(entry.id) != null
            if (existed) stockDao.deleteById(entry.id)
            existed
        }
        if (sourceExisted == null) return false
        remote.incrementQuantity(targetId, entry.quantity)
        if (sourceExisted) {
            remote.delete(RemoteCollection.ENTRIES, entry.id)
        } else {
            log(HistoryAction.ADDED, entry.copy(id = targetId), entry.quantity)
        }
        return true
    }

    suspend fun addEntry(entry: StockEntryEntity): String {
        require(entry.quantity > 0) { "Anzahl muss mindestens 1 sein" }
        releaseNfcTag(entry)
        stockDao.insert(entry)
        remote.putEntry(entry)
        log(HistoryAction.ADDED, entry, entry.quantity)
        return entry.id
    }

    /** Legt ein neues Produkt und den ersten Bestandseintrag in einer Transaktion an. */
    suspend fun addProductWithEntry(product: ProductEntity, entry: StockEntryEntity): String {
        require(entry.quantity > 0) { "Anzahl muss mindestens 1 sein" }
        val newEntry = entry.copy(productId = product.id)
        releaseNfcTag(newEntry)
        db.withTransaction {
            productDao.insert(product)
            stockDao.insert(newEntry)
        }
        remote.putProduct(product)
        remote.putEntry(newEntry)
        log(HistoryAction.ADDED, newEntry, newEntry.quantity)
        return newEntry.id
    }

    suspend fun updateEntry(entry: StockEntryEntity) {
        require(entry.quantity > 0) { "Anzahl muss mindestens 1 sein" }
        val before = stockDao.getById(entry.id)
        releaseNfcTag(entry)
        stockDao.update(entry)
        remote.putEntry(entry)
        // Anzahl im Formular geändert → im Verlauf als Hinzufügen bzw. Entnehmen.
        if (before != null && before.quantity != entry.quantity) {
            val diff = entry.quantity - before.quantity
            log(if (diff > 0) HistoryAction.ADDED else HistoryAction.REMOVED, entry, kotlin.math.abs(diff))
        }
    }

    /**
     * Ändert die Anzahl um [delta]. Fällt sie auf 0 (oder darunter), wird der Bestandseintrag
     * gelöscht; das Produkt (inkl. Bild) bleibt erhalten, damit spätere Scans es wiedererkennen.
     */
    suspend fun changeQuantity(entryId: String, delta: Int): QuantityChange {
        val result = db.withTransaction {
            val entry = stockDao.getById(entryId) ?: return@withTransaction QuantityChange.NotFound
            val newQuantity = entry.quantity + delta
            if (newQuantity <= 0) {
                stockDao.deleteById(entryId)
                QuantityChange.Removed(entry)
            } else {
                stockDao.update(entry.copy(quantity = newQuantity))
                QuantityChange.Updated(newQuantity)
            }
        }
        // Firestore: atomar per FieldValue.increment (Änderungen beider Handys addieren sich); 0 → Dokument löschen.
        when (result) {
            is QuantityChange.Updated -> {
                remote.incrementQuantity(entryId, delta)
                stockDao.getById(entryId)?.let { e ->
                    log(if (delta > 0) HistoryAction.ADDED else HistoryAction.REMOVED, e, kotlin.math.abs(delta))
                }
            }
            is QuantityChange.Removed -> {
                remote.delete(RemoteCollection.ENTRIES, entryId)
                rememberRemoval(entryId, log(HistoryAction.REMOVED, result.removed, result.removed.quantity))
            }
            QuantityChange.NotFound -> Unit
        }
        return result
    }

    /** Löschen (Korrektur, nicht verbraucht): Eintrag landet für 30 Tage im Papierkorb dieses Geräts. */
    suspend fun deleteEntry(entryId: String): StockEntryEntity? {
        val item = stockDao.getById(entryId)?.let { e ->
            stockDao.getItemsForProduct(e.productId).firstOrNull { it.entryId == entryId }
        }
        val entry = db.withTransaction {
            val entry = stockDao.getById(entryId)
            if (entry != null) stockDao.deleteById(entryId)
            entry
        }
        if (entry != null) {
            remote.delete(RemoteCollection.ENTRIES, entryId)
            rememberRemoval(entryId, log(HistoryAction.DELETED, entry, entry.quantity))
            trash?.add(
                TrashItem(
                    id = entry.id,
                    kind = TrashKind.ENTRY,
                    title = item?.productName.orEmpty(),
                    detail = item?.let { listOfNotNull("${it.quantity}x", it.locationName).joinToString(" · ") },
                    deletedAt = System.currentTimeMillis(),
                    entry = TrashedEntry.of(entry),
                ),
            )
        }
        return entry
    }

    sealed interface RestoreResult {
        data class Restored(val entryId: String) : RestoreResult

        /** Das Produkt gibt es nicht mehr (z. B. per Backup ersetzt) – Wiederherstellen unmöglich. */
        data object ProductMissing : RestoreResult
    }

    /**
     * Eintrag aus dem Papierkorb zurückholen. Ein inzwischen gelöschter Ort entfällt, eine fehlende Menge-Art wird
     * durch die erste vorhandene ersetzt, ein inzwischen anders verwendeter NFC-Chip bleibt beim anderen Eintrag.
     */
    suspend fun restoreFromTrash(
        trashed: TrashedEntry,
        quantityTypeIds: List<String>,
        locationIds: Set<String>,
    ): RestoreResult {
        if (productDao.getById(trashed.productId) == null) return RestoreResult.ProductMissing
        var entry = trashed.toEntity()
        if (entry.locationId != null && entry.locationId !in locationIds) {
            entry = entry.copy(locationId = null, level = null, shelf = null)
        }
        if (entry.quantityTypeId !in quantityTypeIds) {
            entry = entry.copy(quantityTypeId = quantityTypeIds.firstOrNull() ?: return RestoreResult.ProductMissing)
        }
        val tag = entry.nfcTagId
        if (tag != null && stockDao.getByNfcTag(tag).any { it.id != entry.id }) entry = entry.copy(nfcTagId = null)
        stockDao.upsert(entry)
        remote.putEntry(entry)
        val event = removalEvents.remove(entry.id)
        if (event != null) history.discard(event) else log(HistoryAction.ADDED, entry, entry.quantity)
        trash?.remove(TrashKind.ENTRY, entry.id)
        return RestoreResult.Restored(entry.id)
    }

    /**
     * Eine Packung als geöffnet markieren. Bei Anzahl > 1 wird genau eine Packung als eigener Eintrag
     * abgespalten (die übrigen bleiben ungeöffnet). Liefert die ID des geöffneten Eintrags (null = nicht gefunden).
     */
    suspend fun markOpened(entryId: String, openedAt: java.time.LocalDate, openedDays: Int?): String? {
        val days = openedDays?.takeIf { it in 0..de.frelina.vorrat.domain.OpenedLogic.MAX_DAYS }
        val (opened, split) = db.withTransaction {
            val entry = stockDao.getById(entryId) ?: return@withTransaction null
            if (entry.quantity > 1 && entry.openedAt == null) {
                val single = entry.copy(id = de.frelina.vorrat.data.db.newId(), quantity = 1, openedAt = openedAt, openedDays = days)
                stockDao.update(entry.copy(quantity = entry.quantity - 1))
                stockDao.insert(single)
                single to true
            } else {
                val updated = entry.copy(openedAt = openedAt, openedDays = days)
                stockDao.update(updated)
                updated to false
            }
        } ?: return null
        if (split) {
            remote.incrementQuantity(entryId, -1)
            remote.putEntry(opened)
        } else {
            remote.putEntry(opened)
        }
        log(HistoryAction.OPENED, opened, 1)
        // Haltbarkeit fürs nächste Öffnen dieses Produkts merken.
        if (days != null) {
            productDao.getById(opened.productId)?.takeIf { it.openedDays != days }?.let { p ->
                val updated = p.copy(openedDays = days, updatedAt = System.currentTimeMillis())
                productDao.update(updated)
                remote.putProduct(updated, previousImage = p.imagePath)
            }
        }
        return opened.id
    }

    /** "Doch nicht geöffnet": Öffnungsdaten entfernen. */
    suspend fun clearOpened(entryId: String) {
        val entry = stockDao.getById(entryId) ?: return
        val updated = entry.copy(openedAt = null, openedDays = null)
        stockDao.update(updated)
        remote.putEntry(updated)
    }

    /** Rückgängig nach Löschen: Eintrag mit derselben ID wiederherstellen (legt auch das Dokument neu an). */
    suspend fun restoreEntry(entry: StockEntryEntity) {
        stockDao.upsert(entry)
        remote.putEntry(entry)
        removalEvents.remove(entry.id)?.let { history.discard(it) }
        trash?.remove(TrashKind.ENTRY, entry.id)
    }
}
