package de.frelina.vorrat.data.repo

import de.frelina.vorrat.data.db.ProductDao
import de.frelina.vorrat.data.db.ProductEntity
import de.frelina.vorrat.data.image.ImageStore
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductRepository @Inject constructor(
    private val productDao: ProductDao,
    private val imageStore: ImageStore,
) {
    suspend fun getById(id: Long): ProductEntity? = productDao.getById(id)

    fun observeById(id: Long): Flow<ProductEntity?> = productDao.observeById(id)

    suspend fun getByBarcode(barcode: String): ProductEntity? = productDao.getByBarcode(barcode.trim())

    fun observeCategories(): Flow<List<String>> = productDao.observeCategories()

    /** Speichert Änderungen; ein ersetztes Bild wird aus dem Speicher entfernt. */
    suspend fun update(product: ProductEntity) {
        val old = productDao.getById(product.id)
        productDao.update(product.copy(updatedAt = System.currentTimeMillis()))
        if (old?.imagePath != null && old.imagePath != product.imagePath) {
            imageStore.delete(old.imagePath)
        }
    }
}
