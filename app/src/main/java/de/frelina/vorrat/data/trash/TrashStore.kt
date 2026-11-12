package de.frelina.vorrat.data.trash

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import de.frelina.vorrat.data.db.RecipeEntity
import de.frelina.vorrat.data.db.StockEntryEntity
import de.frelina.vorrat.domain.TrashLogic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

enum class TrashKind { ENTRY, RECIPE }

/** Gelöschter Bestandseintrag (alle Felder von [StockEntryEntity], Daten als ISO-Text). */
@Serializable
data class TrashedEntry(
    val id: String,
    val productId: String,
    val locationId: String? = null,
    val level: Int? = null,
    val shelf: Int? = null,
    val expiryDate: String? = null,
    val quantity: Int,
    val quantityTypeId: String,
    val note: String? = null,
    val openedAt: String? = null,
    val openedDays: Int? = null,
    val frozenAt: String? = null,
    val nfcTagId: String? = null,
) {
    fun toEntity() = StockEntryEntity(
        id = id,
        productId = productId,
        locationId = locationId,
        level = level,
        shelf = shelf,
        expiryDate = expiryDate?.let(LocalDate::parse),
        quantity = quantity,
        quantityTypeId = quantityTypeId,
        note = note,
        openedAt = openedAt?.let(LocalDate::parse),
        openedDays = openedDays,
        frozenAt = frozenAt?.let(LocalDate::parse),
        nfcTagId = nfcTagId,
    )

    companion object {
        fun of(e: StockEntryEntity) = TrashedEntry(
            id = e.id,
            productId = e.productId,
            locationId = e.locationId,
            level = e.level,
            shelf = e.shelf,
            expiryDate = e.expiryDate?.toString(),
            quantity = e.quantity,
            quantityTypeId = e.quantityTypeId,
            note = e.note,
            openedAt = e.openedAt?.toString(),
            openedDays = e.openedDays,
            frozenAt = e.frozenAt?.toString(),
            nfcTagId = e.nfcTagId,
        )
    }
}

/** Gelöschtes eigenes Rezept (alle Felder von [RecipeEntity]); das Foto bleibt bis zum endgültigen Löschen liegen. */
@Serializable
data class TrashedRecipe(
    val id: String,
    val title: String,
    val minutes: Int? = null,
    val servings: Int? = null,
    val ingredients: String,
    val steps: String,
    val vegetarian: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val imagePath: String? = null,
) {
    fun toEntity() = RecipeEntity(id, title, minutes, servings, ingredients, steps, vegetarian, createdAt, updatedAt, imagePath)

    companion object {
        fun of(e: RecipeEntity) = TrashedRecipe(
            e.id, e.title, e.minutes, e.servings, e.ingredients, e.steps, e.vegetarian, e.createdAt, e.updatedAt, e.imagePath,
        )
    }
}

/**
 * Eintrag im Papierkorb. [id] ist die ID des gelöschten Datensatzes (pro Art eindeutig); [title] und [detail]
 * werden beim Löschen festgehalten, damit die Liste auch ohne Produkt/Ort lesbar bleibt.
 */
@Serializable
data class TrashItem(
    val id: String,
    val kind: TrashKind,
    val title: String,
    val detail: String? = null,
    val deletedAt: Long,
    val entry: TrashedEntry? = null,
    val recipe: TrashedRecipe? = null,
) {
    val key: String get() = "${kind.name}:$id"
}

/**
 * Papierkorb – nur auf diesem Gerät (wie die Scan-Listen als JSON-Datei). Gelöschtes bleibt
 * [TrashLogic.RETENTION_DAYS] Tage, danach räumt [purgeExpired] es weg.
 */
@Singleton
class TrashStore(private val file: File) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(File(context.filesDir, FILE_NAME))

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val mutex = Mutex()
    private val state = MutableStateFlow<List<TrashItem>?>(null)

    /** Neueste zuerst. */
    val items: Flow<List<TrashItem>> = state.onStart { ensureLoaded() }.filterNotNull()

    suspend fun all(): List<TrashItem> = mutex.withLock { loadLocked() }

    suspend fun get(kind: TrashKind, id: String): TrashItem? = all().firstOrNull { it.kind == kind && it.id == id }

    /** Ablegen; ein älterer Stand desselben Datensatzes wird ersetzt. */
    suspend fun add(item: TrashItem) = modify { list ->
        listOf(item) + list.filterNot { it.key == item.key } to Unit
    }

    suspend fun remove(kind: TrashKind, id: String): TrashItem? = modify { list ->
        val found = list.firstOrNull { it.kind == kind && it.id == id }
        (if (found == null) list else list - found) to found
    }

    /** Alles entfernen; liefert die entfernten Einträge (z. B. um Fotos zu löschen). */
    suspend fun clear(): List<TrashItem> = modify { list -> emptyList<TrashItem>() to list }

    /** Abgelaufene Einträge entfernen und zurückgeben. */
    suspend fun purgeExpired(now: Long = System.currentTimeMillis()): List<TrashItem> = modify { list ->
        val (expired, keep) = list.partition { TrashLogic.isExpired(it.deletedAt, now) }
        keep to expired
    }

    /** Fotos gelöschter Rezepte (dürfen beim Aufräumen verwaister Bilder nicht verschwinden). */
    suspend fun imagePaths(): Set<String> = all().mapNotNull { it.recipe?.imagePath }.toSet()

    private suspend fun ensureLoaded() {
        if (state.value == null) mutex.withLock { loadLocked() }
    }

    private suspend fun <T> modify(change: (List<TrashItem>) -> Pair<List<TrashItem>, T>): T = mutex.withLock {
        val before = loadLocked()
        val (list, result) = change(before)
        if (list != before) {
            save(list)
            state.value = list
        }
        result
    }

    private suspend fun loadLocked(): List<TrashItem> {
        state.value?.let { return it }
        val list = withContext(Dispatchers.IO) {
            runCatching { if (file.exists()) json.decodeFromString<List<TrashItem>>(file.readText()) else emptyList() }
                .getOrDefault(emptyList())
        }
        state.value = list
        return list
    }

    private suspend fun save(list: List<TrashItem>) = withContext(Dispatchers.IO) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(list))
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    companion object {
        const val FILE_NAME = "trash.json"
    }
}
