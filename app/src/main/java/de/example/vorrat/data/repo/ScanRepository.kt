package de.example.vorrat.data.repo

import de.example.vorrat.data.db.LookupCacheDao
import de.example.vorrat.data.db.LookupCacheEntity
import de.example.vorrat.data.db.ProductDao
import de.example.vorrat.data.db.ProductEntity
import de.example.vorrat.data.db.StockEntryDao
import de.example.vorrat.data.db.StockItem
import de.example.vorrat.data.image.ImageStore
import de.example.vorrat.data.off.LookupErrorKind
import de.example.vorrat.data.off.LookupResult
import de.example.vorrat.data.off.ProductLookupSource
import de.example.vorrat.domain.AlternativeMatcher
import javax.inject.Inject
import javax.inject.Singleton

/** Vorbelegung für "Zum Bestand hinzufügen" aus dem lokalen OFF-Cache. */
data class ProductPrefill(
    val barcode: String,
    val name: String?,
    val brand: String?,
    val category: String?,
    val packageSize: java.math.BigDecimal?,
    val unitSymbol: String?,
    val imagePath: String?,
)

/** Ergebnis eines Scans (siehe ScanScreen: drei Zustände). */
data class ScanOutcome(
    val barcode: String,
    /** Lokal bekanntes Produkt (unabhängig davon, ob es im Bestand ist). */
    val localProduct: ProductEntity?,
    /** Daten aus Open Food Facts (Cache), falls das Produkt lokal unbekannt ist. */
    val remote: ProductPrefill?,
    /** true, wenn OFF das Produkt nicht kennt. */
    val remoteNotFound: Boolean,
    /** Fehler bei der OFF-Abfrage (z. B. offline). */
    val lookupError: LookupErrorKind?,
    val exactMatches: List<StockItem>,
    val alternatives: List<StockItem>,
) {
    val displayName: String? get() = localProduct?.name ?: remote?.name
    val category: String? get() = localProduct?.category ?: remote?.category
}

@Singleton
class ScanRepository @Inject constructor(
    private val productDao: ProductDao,
    private val stockDao: StockEntryDao,
    private val cacheDao: LookupCacheDao,
    private val source: ProductLookupSource,
    private val imageStore: ImageStore,
) {
    /**
     * 1. Lokal bekannter Barcode → komplett offline auflösen.
     * 2. Sonst OFF-Cache verwenden, sonst OFF abfragen (Ergebnis wird gecacht, Bild lokal abgelegt).
     * 3. Exakte Treffer bzw. Alternativen (gleiche Kategorie) aus dem Bestand ermitteln.
     */
    suspend fun resolve(rawBarcode: String, now: Long = System.currentTimeMillis()): ScanOutcome {
        val barcode = rawBarcode.trim()
        val local = productDao.getByBarcode(barcode)
        val allStock = stockDao.getAllItems()

        if (local != null) {
            val exact = allStock.filter { it.productId == local.id }
            val alternatives = if (exact.isEmpty()) {
                AlternativeMatcher.findAlternatives(local.category, barcode, local.id, allStock)
            } else {
                emptyList()
            }
            return ScanOutcome(barcode, local, null, false, null, exact, alternatives)
        }

        var error: LookupErrorKind? = null
        var cached = cacheDao.get(barcode)?.takeIf { it.found || now - it.fetchedAt < NOT_FOUND_TTL_MS }
        if (cached == null) {
            when (val result = source.lookup(barcode)) {
                is LookupResult.Found -> {
                    val p = result.product
                    val image = p.imageUrl?.let { url -> source.downloadImage(url) }
                        ?.let { bytes -> imageStore.saveBytes(bytes) }
                    cached = LookupCacheEntity(
                        barcode = barcode,
                        found = true,
                        name = p.name,
                        brand = p.brand,
                        category = p.category,
                        packageSize = p.packageSize,
                        unitSymbol = p.unitSymbol,
                        imagePath = image,
                        fetchedAt = now,
                    )
                    cacheDao.get(barcode)?.imagePath?.let { imageStore.delete(it) }
                    cacheDao.upsert(cached)
                }
                LookupResult.NotFound -> {
                    cached = LookupCacheEntity(barcode, false, null, null, null, null, null, null, now)
                    cacheDao.upsert(cached)
                }
                is LookupResult.Failed -> error = result.kind
            }
        }

        val prefill = cached?.takeIf { it.found }?.toPrefill()
        val alternatives = AlternativeMatcher.findAlternatives(prefill?.category, barcode, null, allStock)
        return ScanOutcome(
            barcode = barcode,
            localProduct = null,
            remote = prefill,
            remoteNotFound = cached?.found == false,
            lookupError = error,
            exactMatches = emptyList(),
            alternatives = alternatives,
        )
    }

    /** Für das Erfassen: Vorbelegung aus dem Cache (ohne erneute Netzabfrage). */
    suspend fun cachedPrefill(barcode: String): ProductPrefill? =
        cacheDao.get(barcode.trim())?.takeIf { it.found }?.toPrefill()

    private fun LookupCacheEntity.toPrefill() =
        ProductPrefill(barcode, name, brand, category, packageSize, unitSymbol, imagePath)

    companion object {
        /** "Nicht gefunden" wird 24 h gecacht, danach erneut gefragt (OFF wächst ständig). */
        const val NOT_FOUND_TTL_MS = 24L * 60 * 60 * 1000
    }
}
