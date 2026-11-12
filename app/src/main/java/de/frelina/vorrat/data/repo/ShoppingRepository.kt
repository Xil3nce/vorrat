package de.frelina.vorrat.data.repo

import de.frelina.vorrat.data.db.ProductDao
import de.frelina.vorrat.data.db.ProductEntity
import de.frelina.vorrat.data.db.ProductSource
import de.frelina.vorrat.data.image.ImageStore
import de.frelina.vorrat.data.off.LookedUpProduct
import de.frelina.vorrat.data.db.ShoppingDao
import de.frelina.vorrat.data.db.ShoppingItemEntity
import de.frelina.vorrat.data.db.StockEntryDao
import de.frelina.vorrat.data.db.UnitDao
import de.frelina.vorrat.data.sync.RemoteCollection
import de.frelina.vorrat.data.sync.RemoteMapper
import de.frelina.vorrat.data.sync.RemoteWriter
import de.frelina.vorrat.domain.ShoppingAmount
import de.frelina.vorrat.domain.ShoppingRecipes
import de.frelina.vorrat.domain.Shortage
import de.frelina.vorrat.domain.ShoppingLogic
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/** Manueller Listeneintrag samt verknüpftem Produkt (falls lokal bekannt). */
data class ShoppingEntry(val item: ShoppingItemEntity, val product: ProductEntity?)

/** Geplante Zutat: Menge als Text ("700g", "3 Stück") und die Rezepte, für die sie gebraucht wird. */
data class PlannedItem(val name: String, val amount: String?, val recipes: List<String>)

/** Neue Werte eines Listeneintrags aus dem Bearbeiten-Sheet. */
data class ShoppingItemUpdate(
    val name: String,
    val quantity: Int,
    val packageSize: BigDecimal?,
    val unitSymbol: String?,
    val note: String?,
    val recipes: List<String>,
    val productId: String?,
)

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
    private val unitDao: UnitDao,
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
    suspend fun addText(
        name: String,
        quantity: Int = 1,
        packageSize: BigDecimal? = null,
        unitSymbol: String? = null,
    ): ShoppingItemEntity? {
        val trimmed = name.trim().take(RemoteMapper.MAX_TEXT).takeIf { it.isNotEmpty() } ?: return null
        val product = ShoppingLogic.matchProduct(trimmed, productDao.getAll())
        return if (product != null) addProduct(product, quantity, packageSize, unitSymbol)
        else insert(null, trimmed, quantity, packageSize, unitSymbol)
    }

    /**
     * Fehlende Rezeptzutaten auf die Liste setzen – ohne Duplikate: Was schon (ohne Groß-/Kleinschreibung) mit
     * gleichem Namen oder als verknüpftes Produkt auf der Liste steht, wird übersprungen. Liefert die Anzahl neuer Einträge.
     */
    suspend fun addMissing(names: List<String>): Int {
        var added = 0
        val known = shoppingDao.getAll().map { it.name.trim().lowercase() }.toMutableSet()
        for (raw in names) {
            val name = raw.trim()
            if (name.isEmpty() || name.lowercase() in known) continue
            val product = ShoppingLogic.matchProduct(name, productDao.getAll())
            if (product != null && isOnList(product.id)) continue
            addText(name) ?: continue
            known += name.lowercase()
            added++
        }
        return added
    }

    /**
     * Zutaten aus dem Wochenplan bzw. Rezept auf die Liste setzen – ohne Duplikate wie bei [addMissing]. Eine Menge
     * wie "700g" wird zur Packungsgröße, jede andere ("3 Stück") bleibt als Notiz; die Rezepte werden gemerkt.
     * Liefert die Anzahl neuer Einträge.
     */
    suspend fun addPlanned(items: List<PlannedItem>): Int {
        var added = 0
        val known = shoppingDao.getAll().map { it.name.trim().lowercase() }.toMutableSet()
        for (planned in items) {
            val name = planned.name.trim()
            if (name.isEmpty() || name.lowercase() in known) continue
            val product = ShoppingLogic.matchProduct(name, productDao.getAll())
            if (product != null && isOnList(product.id)) continue
            val parsed = ShoppingAmount.parse(planned.amount)
            val item = addText(name, 1, parsed?.size, parsed?.unitSymbol) ?: continue
            val note = if (parsed == null) planned.amount?.trim()?.take(RemoteMapper.MAX_PLAN_NOTE)?.takeIf { it.isNotEmpty() } else null
            val recipes = ShoppingRecipes.join(planned.recipes, RemoteMapper.MAX_PLAN_NOTE)
            if (note != null || recipes != null) save(item.copy(note = note, recipes = recipes))
            known += name.lowercase()
            added++
        }
        return added
    }

    /** Namen aller Einträge auf der Liste (manuell und unter Mindestbestand) für den Abgleich mit dem Wochenplan. */
    suspend fun listedNames(): List<String> {
        val manual = shoppingDao.getAll().map { it.name }
        val shortages = ShoppingLogic.shortages(productDao.getAll(), stockDao.getAllItems()).map { it.product.name }
        return manual + shortages
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

    /**
     * Treffer der Open-Food-Facts-Suche auf die Liste setzen. Wie bei [addScanned] wird ein lokal unbekanntes
     * Produkt angelegt (Barcode, Marke, Kategorie, Bild aus [imageBytes]); ein bekannter Barcode bzw. ein
     * gleichnamiges Produkt wird wiederverwendet.
     */
    suspend fun addLookedUp(
        found: LookedUpProduct,
        imageBytes: ByteArray?,
        quantity: Int = 1,
        packageSize: BigDecimal? = null,
        unitSymbol: String? = null,
    ): ShoppingItemEntity? {
        val product = ensureProduct(found, imageBytes) ?: return null
        return addProduct(product, quantity, packageSize, unitSymbol)
    }

    /** Produkt zu einem OFF-Treffer: bekannter Barcode bzw. gleichnamiges Produkt wiederverwenden, sonst anlegen. */
    suspend fun ensureProduct(found: LookedUpProduct, imageBytes: ByteArray?): ProductEntity? {
        productDao.getByBarcode(found.barcode)?.let { return it }
        val name = found.name?.trim()?.take(RemoteMapper.MAX_TEXT)?.takeIf { it.isNotEmpty() } ?: return null
        ShoppingLogic.matchProduct(name, productDao.getAll())?.let { return it }
        val now = System.currentTimeMillis()
        val product = ProductEntity(
            barcode = found.barcode,
            name = name,
            brand = found.brand?.trim()?.take(RemoteMapper.MAX_TEXT)?.takeIf { it.isNotEmpty() },
            category = found.category?.trim()?.takeIf { it.isNotEmpty() },
            packageSize = null,
            unitId = null,
            imagePath = imageBytes?.let { imageStore.saveBytes(it) },
            source = ProductSource.OPEN_FOOD_FACTS,
            createdAt = now,
            updatedAt = now,
        )
        productDao.insert(product)
        remote.putProduct(product)
        return product
    }

    /** Produkt hinzufügen; steht es schon (manuell) auf der Liste, wird die Menge erhöht. */
    suspend fun addProduct(
        product: ProductEntity,
        quantity: Int = 1,
        packageSize: BigDecimal? = null,
        unitSymbol: String? = null,
    ): ShoppingItemEntity {
        val existing = shoppingDao.getForProduct(product.id).firstOrNull()
        if (existing != null) {
            val updated = existing.copy(quantity = (existing.quantity + quantity).coerceAtMost(RemoteMapper.MAX_COUNT))
            save(updated)
            return updated
        }
        // Eingetragene Packungsgröße gilt, sonst die des Produkts (änderbar über "Bearbeiten").
        if (packageSize != null && packageSize.signum() > 0) {
            return insert(product.id, product.name, quantity, packageSize, unitSymbol)
        }
        val unit = product.unitId?.let { unitDao.getById(it)?.symbol }
        return insert(product.id, product.name, quantity, product.packageSize, unit)
    }

    private suspend fun insert(
        productId: String?,
        name: String,
        quantity: Int,
        packageSize: BigDecimal? = null,
        unitSymbol: String? = null,
    ): ShoppingItemEntity {
        val item = ShoppingItemEntity(
            productId = productId,
            name = name,
            quantity = quantity.coerceIn(1, RemoteMapper.MAX_COUNT),
            note = null,
            createdAt = System.currentTimeMillis(),
            packageSize = packageSize?.takeIf { it.signum() > 0 },
            unitSymbol = unitSymbol?.trim()?.takeIf { it.isNotEmpty() },
        )
        save(item)
        return item
    }

    suspend fun setQuantity(id: String, quantity: Int) {
        val item = shoppingDao.getById(id) ?: return
        save(item.copy(quantity = quantity.coerceIn(1, RemoteMapper.MAX_COUNT)))
    }

    /** Alle Angaben eines Eintrags ändern ("2× 500 g", Notiz, Rezepte, Produkt); ein leerer Name wird nicht übernommen. */
    suspend fun updateDetails(id: String, update: ShoppingItemUpdate) {
        val item = shoppingDao.getById(id) ?: return
        val newName = update.name.trim().take(RemoteMapper.MAX_TEXT).takeIf { it.isNotEmpty() } ?: return
        save(
            item.copy(
                productId = update.productId?.takeIf { it.isNotBlank() },
                name = newName,
                quantity = update.quantity.coerceIn(1, RemoteMapper.MAX_COUNT),
                note = update.note?.trim()?.take(RemoteMapper.MAX_PLAN_NOTE)?.takeIf { it.isNotEmpty() },
                packageSize = update.packageSize?.takeIf { it.signum() > 0 },
                unitSymbol = update.unitSymbol?.trim()?.take(RemoteMapper.MAX_UNIT)?.takeIf { it.isNotEmpty() },
                recipes = ShoppingRecipes.join(update.recipes, RemoteMapper.MAX_PLAN_NOTE),
            ),
        )
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
