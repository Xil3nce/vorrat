package de.frelina.vorrat.data.repo

import androidx.room.withTransaction
import de.frelina.vorrat.data.db.ProductDao
import de.frelina.vorrat.data.db.ProductEntity
import de.frelina.vorrat.data.db.StockEntryDao
import de.frelina.vorrat.data.db.StockEntryEntity
import de.frelina.vorrat.data.db.StockItem
import de.frelina.vorrat.data.db.VorratDatabase
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
) {
    fun observeItems(): Flow<List<StockItem>> = stockDao.observeAllItems()

    fun observeItem(entryId: Long): Flow<StockItem?> = stockDao.observeItem(entryId)

    suspend fun allItems(): List<StockItem> = stockDao.getAllItems()

    suspend fun itemsForProduct(productId: Long): List<StockItem> = stockDao.getItemsForProduct(productId)

    suspend fun getEntry(id: Long): StockEntryEntity? = stockDao.getById(id)

    suspend fun addEntry(entry: StockEntryEntity): Long {
        require(entry.quantity > 0) { "Anzahl muss mindestens 1 sein" }
        return stockDao.insert(entry.copy(id = 0))
    }

    /** Legt ein neues Produkt und den ersten Bestandseintrag in einer Transaktion an. */
    suspend fun addProductWithEntry(product: ProductEntity, entry: StockEntryEntity): Long {
        require(entry.quantity > 0) { "Anzahl muss mindestens 1 sein" }
        return db.withTransaction {
            val productId = productDao.insert(product)
            stockDao.insert(entry.copy(id = 0, productId = productId))
        }
    }

    suspend fun updateEntry(entry: StockEntryEntity) {
        require(entry.quantity > 0) { "Anzahl muss mindestens 1 sein" }
        stockDao.update(entry)
    }

    /**
     * Ändert die Anzahl um [delta]. Fällt sie auf 0 (oder darunter), wird der Bestandseintrag
     * gelöscht; das Produkt (inkl. Bild) bleibt erhalten, damit spätere Scans es wiedererkennen.
     */
    suspend fun changeQuantity(entryId: Long, delta: Int): QuantityChange = db.withTransaction {
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

    suspend fun deleteEntry(entryId: Long): StockEntryEntity? = db.withTransaction {
        val entry = stockDao.getById(entryId)
        if (entry != null) stockDao.deleteById(entryId)
        entry
    }

    /** Rückgängig nach Löschen: Eintrag mit derselben ID wiederherstellen. */
    suspend fun restoreEntry(entry: StockEntryEntity) {
        stockDao.insert(entry)
    }
}
