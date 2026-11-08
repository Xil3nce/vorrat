package de.frelina.vorrat.data.repo

import androidx.room.withTransaction
import de.frelina.vorrat.data.db.ProductDao
import de.frelina.vorrat.data.db.ProductEntity
import de.frelina.vorrat.data.db.StockEntryDao
import de.frelina.vorrat.data.db.StockEntryEntity
import de.frelina.vorrat.data.db.StockItem
import de.frelina.vorrat.data.db.VorratDatabase
import de.frelina.vorrat.data.sync.RemoteCollection
import de.frelina.vorrat.data.sync.RemoteWriter
import de.frelina.vorrat.domain.StockMerge
import kotlinx.coroutines.flow.Flow
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
) {
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
        if (sourceExisted) remote.delete(RemoteCollection.ENTRIES, entry.id)
        return true
    }

    suspend fun addEntry(entry: StockEntryEntity): String {
        require(entry.quantity > 0) { "Anzahl muss mindestens 1 sein" }
        stockDao.insert(entry)
        remote.putEntry(entry)
        return entry.id
    }

    /** Legt ein neues Produkt und den ersten Bestandseintrag in einer Transaktion an. */
    suspend fun addProductWithEntry(product: ProductEntity, entry: StockEntryEntity): String {
        require(entry.quantity > 0) { "Anzahl muss mindestens 1 sein" }
        val newEntry = entry.copy(productId = product.id)
        db.withTransaction {
            productDao.insert(product)
            stockDao.insert(newEntry)
        }
        remote.putProduct(product)
        remote.putEntry(newEntry)
        return newEntry.id
    }

    suspend fun updateEntry(entry: StockEntryEntity) {
        require(entry.quantity > 0) { "Anzahl muss mindestens 1 sein" }
        stockDao.update(entry)
        remote.putEntry(entry)
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
            is QuantityChange.Updated -> remote.incrementQuantity(entryId, delta)
            is QuantityChange.Removed -> remote.delete(RemoteCollection.ENTRIES, entryId)
            QuantityChange.NotFound -> Unit
        }
        return result
    }

    suspend fun deleteEntry(entryId: String): StockEntryEntity? {
        val entry = db.withTransaction {
            val entry = stockDao.getById(entryId)
            if (entry != null) stockDao.deleteById(entryId)
            entry
        }
        if (entry != null) remote.delete(RemoteCollection.ENTRIES, entryId)
        return entry
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
    }
}
