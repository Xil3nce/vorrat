package de.frelina.vorrat.data.repo

import de.frelina.vorrat.data.db.ProductDao
import de.frelina.vorrat.data.db.ProductEntity
import de.frelina.vorrat.data.image.ImageStore
import de.frelina.vorrat.data.sync.RemoteWriter
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductRepository @Inject constructor(
    private val productDao: ProductDao,
    private val imageStore: ImageStore,
    private val remote: RemoteWriter,
) {
    suspend fun getById(id: String): ProductEntity? = productDao.getById(id)

    fun observeById(id: String): Flow<ProductEntity?> = productDao.observeById(id)

    suspend fun getByBarcode(barcode: String): ProductEntity? = productDao.getByBarcode(barcode.trim())

    fun observeCategories(): Flow<List<String>> = productDao.observeCategories()

    fun observeAll(): Flow<List<ProductEntity>> = productDao.observeAll()

    /** Speichert Änderungen; ein ersetztes Bild wird aus dem Speicher entfernt. */
    suspend fun update(product: ProductEntity) {
        val old = productDao.getById(product.id)
        val updated = product.copy(updatedAt = System.currentTimeMillis())
        productDao.update(updated)
        remote.putProduct(updated, previousImage = old?.imagePath)
        if (old?.imagePath != null && old.imagePath != product.imagePath) {
            imageStore.delete(old.imagePath)
        }
    }
}
