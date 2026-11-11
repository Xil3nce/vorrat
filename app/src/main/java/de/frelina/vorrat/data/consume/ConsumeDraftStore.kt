package de.frelina.vorrat.data.consume

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import de.frelina.vorrat.data.db.StockItem
import de.frelina.vorrat.domain.ConsumeAddResult
import de.frelina.vorrat.domain.ConsumeListLogic
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
import javax.inject.Inject
import javax.inject.Singleton

/** Eine in der Entnahme-Scan-Liste vorgemerkte Entnahme: [quantity] Stück aus dem Bestandseintrag [entryId]. */
@Serializable
data class ConsumeDraft(
    val entryId: String,
    val productId: String,
    val quantity: Int = 1,
)

/**
 * Scan-Liste im Modus "Entnehmen" – nur auf diesem Gerät (kein Sync), als JSON-Datei gespeichert, damit nichts
 * verloren geht, wenn die App zwischendurch beendet wird. Name, Ort und Ablauf kommen beim Anzeigen aus dem Bestand.
 */
@Singleton
class ConsumeDraftStore(private val file: File) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(File(context.filesDir, FILE_NAME))

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val mutex = Mutex()
    private val state = MutableStateFlow<List<ConsumeDraft>?>(null)

    val drafts: Flow<List<ConsumeDraft>> = state.onStart { ensureLoaded() }.filterNotNull()

    suspend fun all(): List<ConsumeDraft> = mutex.withLock { loadLocked() }

    /** Scan: 1 Stück des Produkts vormerken, siehe [ConsumeListLogic.addScan]. */
    suspend fun addScan(entries: List<StockItem>): ConsumeAddResult = modify { list ->
        val result = ConsumeListLogic.addScan(list, entries)
        result.list to result
    }

    /** Zeile auf anderen Eintrag/andere Menge umstellen, siehe [ConsumeListLogic.change]. */
    suspend fun change(oldEntryId: String, newEntryId: String, amount: Int) = modify { list ->
        ConsumeListLogic.change(list, oldEntryId, newEntryId, amount) to Unit
    }

    suspend fun adjust(entryId: String, delta: Int, available: Int) = modify { list ->
        ConsumeListLogic.adjust(list, entryId, delta, available) to Unit
    }

    suspend fun remove(entryId: String): ConsumeDraft? = modify { list ->
        list.filterNot { it.entryId == entryId } to list.firstOrNull { it.entryId == entryId }
    }

    /** Zeile wieder einfügen (Rückgängig), an der alten Position. */
    suspend fun restore(draft: ConsumeDraft, index: Int) = modify { list ->
        if (list.any { it.entryId == draft.entryId }) list to Unit
        else list.toMutableList().also { it.add(index.coerceIn(0, it.size), draft) } to Unit
    }

    suspend fun clear() = modify { emptyList<ConsumeDraft>() to Unit }

    private suspend fun ensureLoaded() {
        if (state.value == null) mutex.withLock { loadLocked() }
    }

    private suspend fun <T> modify(change: (List<ConsumeDraft>) -> Pair<List<ConsumeDraft>, T>): T = mutex.withLock {
        val (list, result) = change(loadLocked())
        save(list)
        state.value = list
        result
    }

    private suspend fun loadLocked(): List<ConsumeDraft> {
        state.value?.let { return it }
        val list = withContext(Dispatchers.IO) {
            runCatching { if (file.exists()) json.decodeFromString<List<ConsumeDraft>>(file.readText()) else emptyList() }
                .getOrDefault(emptyList())
        }
        state.value = list
        return list
    }

    private suspend fun save(list: List<ConsumeDraft>) = withContext(Dispatchers.IO) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(list))
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    companion object {
        const val FILE_NAME = "consume_drafts.json"
    }
}
