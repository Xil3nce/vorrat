package de.frelina.vorrat.ui.purchase

import de.frelina.vorrat.data.db.ProductSource
import de.frelina.vorrat.data.image.ImageStore
import de.frelina.vorrat.data.purchase.PurchaseDraft
import de.frelina.vorrat.data.purchase.PurchaseDraftStore
import de.frelina.vorrat.data.repo.ProductRepository
import de.frelina.vorrat.data.repo.ShoppingRepository
import de.frelina.vorrat.data.repo.StockRepository
import de.frelina.vorrat.ui.product.toEntryForm
import de.frelina.vorrat.ui.product.toProductForm
import javax.inject.Inject
import javax.inject.Singleton

/** Ergebnis von "In Bestand übernehmen". */
data class CommitResult(val committed: Int, val incomplete: Int)

/**
 * Übernimmt die Einkaufsliste des Mehrfachscans in den Bestand: bekannte Produkte bekommen einen neuen Eintrag
 * (oder er wird mit einem gleichen Eintrag zusammengeführt), unbekannte werden als Produkt angelegt.
 * Gekaufte Produkte verschwinden von der Einkaufsliste. Unvollständige Artikel bleiben in der Liste.
 */
@Singleton
class PurchaseCommitter @Inject constructor(
    private val store: PurchaseDraftStore,
    private val stockRepository: StockRepository,
    private val productRepository: ProductRepository,
    private val shoppingRepository: ShoppingRepository,
    private val imageStore: ImageStore,
) {
    suspend fun commitAll(now: Long = System.currentTimeMillis()): CommitResult {
        var committed = 0
        var incomplete = 0
        for (draft in store.all()) {
            if (commit(draft, now)) {
                store.remove(draft.id)
                committed++
            } else {
                incomplete++
            }
        }
        return CommitResult(committed, incomplete)
    }

    private suspend fun commit(draft: PurchaseDraft, now: Long): Boolean {
        val entry = draft.toEntryForm().validate(entryId = draft.id).second ?: return false
        // Bekanntes Produkt (oder inzwischen – z. B. durch einen früheren Artikel dieser Liste – angelegt).
        val known = draft.productId?.let { productRepository.getById(it) }
            ?: draft.barcode?.let { productRepository.getByBarcode(it) }
        val productId = if (known != null) {
            val forProduct = entry.copy(productId = known.id)
            val twin = stockRepository.findTwin(forProduct)
            if (twin == null || !stockRepository.mergeInto(twin.id, forProduct)) stockRepository.addEntry(forProduct)
            known.id
        } else {
            val fields = draft.toProductForm().validate().second ?: return false
            val image = if (draft.imageFromCache) draft.imagePath?.let { imageStore.copy(it) } else draft.imagePath
            val source = if (draft.fromOff) ProductSource.OPEN_FOOD_FACTS else ProductSource.MANUAL
            val product = fields.toNewEntity(image, source, now)
            stockRepository.addProductWithEntry(product, entry)
            product.id
        }
        shoppingRepository.removeForProduct(productId)
        return true
    }
}
