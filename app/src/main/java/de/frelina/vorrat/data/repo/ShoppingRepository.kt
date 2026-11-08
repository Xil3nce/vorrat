package de.frelina.vorrat.data.repo

import de.frelina.vorrat.data.db.ProductDao
import de.frelina.vorrat.data.db.ProductEntity
import de.frelina.vorrat.data.db.ProductSource
import de.frelina.vorrat.data.image.ImageStore
import de.frelina.vorrat.data.db.ShoppingDao
import de.frelina.vorrat.data.db.ShoppingItemEntity
import de.frelina.vorrat.data.db.StockEntryDao
import de.frelina.vorrat.data.sync.RemoteCollection
import de.frelina.vorrat.data.sync.RemoteMapper
import de.frelina.vorrat.data.sync.RemoteWriter
import de.frelina.vorrat.domain.Shortage
import de.frelina.vorrat.domain.ShoppingLogic
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/** Manueller Listeneintrag samt verknüpftem Produkt (falls lokal bekannt). */
data class ShoppingEntry(val item: ShoppingItemEntity, val product: ProductEntity?)

/** Einkaufsliste: berechnete Einträge unter Mindestbestand + manuell hinzugefügte. */
data class ShoppingList(val shortages: List<Shortage>, val items: List<ShoppingEntry>) {
    val isEmpty: Boolean get() = shortages.isEmpty() && items.isEmpty()
    val count: Int get() = shortages.size + items.size
}

@Singleton
class ShoppingRepository @Inject constructor(
    private val shoppingDao: ShoppingDao,
    private val productDao: ProductDao,
    private val stockDao: StockEntryDao,
    private val remote: RemoteWriter,
    private val imageStore: ImageStore,
) {
    fun observe(): Flow<ShoppingList> = combine(
        shoppingDao.observeAll(),
        productDao.observeAll(),
        stockDao.observeAllItems(),
    ) { items, products, stock ->
        val byId = products.associateBy { it.id }
        val shortages = ShoppingLogic.shortages(products, stock)
        val shortIds = shortages.map { it.product.id }.toSet()
        ShoppingList(
            shortages = shortages,
            // Manuelle Einträge zu einem Produkt, das ohnehin unter Mindestbestand ist, nur einmal zeigen.
            items = items.filter { it.productId == null || it.productId !in shortIds }
                // Freitext-Einträge, deren Name exakt zu einem Produkt passt, zeigen dessen Bild/Marke.
                .map { ShoppingEntry(it, it.productId?.let(byId::get) ?: ShoppingLogic.matchProduct(it.name, products)) }
                .filter { it.item.productId != null || it.product == null || it.product.id !in shortIds },
        )
    }

    /** Freitext hinzufügen; passt der Name exakt zu einem Produkt, wird es verknüpft. */
    suspend fun addText(name: String, quantity: Int = 1): ShoppingItemEntity? {
        val trimmed = name.trim().takeIf { it.isNotEmpty() } ?: return null
        val product = ShoppingLogic.matchProduct(trimmed, productDao.getAll())
        return if (product != null) addProduct(product, quantity) else insert(null, trimmed, quantity)
    }

    /**
     * Gescanntes, lokal unbekanntes Produkt (Daten aus Open Food Facts) auf die Liste setzen: Es wird als Produkt
     * angelegt (mit Barcode und einer Kopie des OFF-Bilds), damit die Liste das Bild zeigt, es auf das andere
     * Handy synchronisiert wird und "Gekauft" später alles vorbelegt.
     */
    suspend fun addScanned(prefill: ProductPrefill): ShoppingItemEntity? {
        productDao.getByBarcode(prefill.barcode)?.let { return addProduct(it) }
        val name = prefill.name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        ShoppingLogic.matchProduct(name, productDao.getAll())?.let { return addProduct(it) }
        val now = System.currentTimeMillis()
        val product = ProductEntity(
            barcode = prefill.barcode,
            name = name,
            brand = prefill.brand?.trim()?.takeIf { it.isNotEmpty() },
            category = prefill.category?.trim()?.takeIf { it.isNotEmpty() },
            packageSize = null,
            unitId = null,
            imagePath = prefill.imagePath?.let { imageStore.copy(it) },
            source = ProductSource.OPEN_FOOD_FACTS,
            createdAt = now,
            updatedAt = now,
        )
        productDao.insert(product)
        remote.putProduct(product)
        return addProduct(product)
    }

    /** Produkt hinzufügen; steht es schon (manuell) auf der Liste, wird die Menge erhöht. */
    suspend fun addProduct(product: ProductEntity, quantity: Int = 1): ShoppingItemEntity {
        val existing = shoppingDao.getForProduct(product.id).firstOrNull()
        if (existing != null) {
            val updated = existing.copy(quantity = (existing.quantity + quantity).coerceAtMost(RemoteMapper.MAX_COUNT))
            save(updated)
            return updated
        }
        return insert(product.id, product.name, quantity)
    }

    private suspend fun insert(productId: String?, name: String, quantity: Int): ShoppingItemEntity {
        val item = ShoppingItemEntity(
            productId = productId,
            name = name,
            quantity = quantity.coerceIn(1, RemoteMapper.MAX_COUNT),
            note = null,
            createdAt = System.currentTimeMillis(),
        )
        save(item)
        return item
    }

    suspend fun setQuantity(id: String, quantity: Int) {
        val item = shoppingDao.getById(id) ?: return
        save(item.copy(quantity = quantity.coerceIn(1, RemoteMapper.MAX_COUNT)))
    }

    suspend fun getById(id: String): ShoppingItemEntity? = shoppingDao.getById(id)

    /** Abhaken/Löschen; liefert den Eintrag für "Rückgängig". */
    suspend fun remove(id: String): ShoppingItemEntity? {
        val item = shoppingDao.getById(id) ?: return null
        shoppingDao.deleteById(id)
        remote.delete(RemoteCollection.SHOPPING, id)
        return item
    }

    suspend fun restore(item: ShoppingItemEntity) = save(item)

    /**
     * Nach dem Kauf (Produkt zum Bestand hinzugefügt): manuelle Einträge des Produkts entfernen –
     * auch Freitext-Einträge mit genau dem Produktnamen.
     */
    suspend fun removeForProduct(productId: String): List<ShoppingItemEntity> {
        val name = productDao.getById(productId)?.name?.trim()
        val matches = shoppingDao.getAll().filter { item ->
            item.productId == productId ||
                (item.productId == null && name != null && item.name.trim().equals(name, ignoreCase = true))
        }
        return matches.onEach { remove(it.id) }
    }

    suspend fun isOnListManually(productId: String): Boolean = shoppingDao.getForProduct(productId).isNotEmpty()

    /** Steht das Produkt auf der Liste (manuell oder unter Mindestbestand)? */
    suspend fun isOnList(productId: String): Boolean {
        if (shoppingDao.getForProduct(productId).isNotEmpty()) return true
        val product = productDao.getById(productId) ?: return false
        return ShoppingLogic.shortages(listOf(product), stockDao.getItemsForProduct(productId)).isNotEmpty()
    }

    private suspend fun save(item: ShoppingItemEntity) {
        shoppingDao.upsert(item)
        remote.putShopping(item)
    }
}
