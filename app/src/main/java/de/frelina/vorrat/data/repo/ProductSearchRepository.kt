package de.frelina.vorrat.data.repo

import de.frelina.vorrat.data.db.ProductDao
import de.frelina.vorrat.data.db.ProductEntity
import de.frelina.vorrat.data.off.ProductLookupSource
import de.frelina.vorrat.data.off.SearchResult
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Artikelsuche beim manuellen Hinzufügen: eigene Produkte (offline) und auf Wunsch Open Food Facts. */
@Singleton
class ProductSearchRepository @Inject constructor(
    private val productDao: ProductDao,
    private val source: ProductLookupSource,
) {
    fun observeProducts(): Flow<List<ProductEntity>> = productDao.observeAll()

    suspend fun searchOnline(query: String): SearchResult = source.search(query)

    suspend fun downloadImage(url: String): ByteArray? = source.downloadImage(url)
}
