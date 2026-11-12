package de.frelina.vorrat.data.repo

import de.frelina.vorrat.data.db.LocationDao
import de.frelina.vorrat.data.db.QuantityTypeDao
import de.frelina.vorrat.data.image.ImageStore
import de.frelina.vorrat.data.trash.TrashItem
import de.frelina.vorrat.data.trash.TrashKind
import de.frelina.vorrat.data.trash.TrashStore
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

enum class TrashRestore { RESTORED, PRODUCT_MISSING, GONE }

/** Papierkorb: anzeigen, wiederherstellen, endgültig löschen. */
@Singleton
class TrashRepository @Inject constructor(
    private val store: TrashStore,
    private val stock: StockRepository,
    private val recipes: RecipeRepository,
    private val quantityTypeDao: QuantityTypeDao,
    private val locationDao: LocationDao,
    private val imageStore: ImageStore,
) {
    val items: Flow<List<TrashItem>> = store.items

    suspend fun restore(kind: TrashKind, id: String): TrashRestore {
        val item = store.get(kind, id) ?: return TrashRestore.GONE
        item.entry?.let { entry ->
            val result = stock.restoreFromTrash(
                entry,
                quantityTypeIds = quantityTypeDao.getAll().map { it.id },
                locationIds = locationDao.getIds().toSet(),
            )
            return if (result is StockRepository.RestoreResult.Restored) TrashRestore.RESTORED else TrashRestore.PRODUCT_MISSING
        }
        item.recipe?.let { recipe ->
            recipes.restoreFromTrash(recipe)
            return TrashRestore.RESTORED
        }
        store.remove(kind, id)
        return TrashRestore.GONE
    }

    suspend fun deleteForever(kind: TrashKind, id: String) {
        store.remove(kind, id)?.let { deleteFiles(listOf(it)) }
    }

    /** Papierkorb leeren; liefert die Anzahl der endgültig gelöschten Einträge. */
    suspend fun clear(): Int = store.clear().also { deleteFiles(it) }.size

    private suspend fun deleteFiles(items: List<TrashItem>) {
        items.mapNotNull { it.recipe?.imagePath }.forEach { imageStore.delete(it) }
    }
}
