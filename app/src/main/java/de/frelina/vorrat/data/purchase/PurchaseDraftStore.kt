package de.frelina.vorrat.data.purchase

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import de.frelina.vorrat.data.db.newId
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

/**
 * Ein beim Einkauf ("+ Bestand"-Scan) erfasster, noch nicht übernommener Artikel. Alle Eingaben als Text wie im
 * Formular, damit unvollständige Angaben gespeichert und später ergänzt werden können.
 * [id] wird beim Übernehmen die ID des Bestandseintrags (NFC-Chips können so schon vorher beschrieben werden).
 */
@Serializable
data class PurchaseDraft(
    val id: String = newId(),
    val barcode: String? = null,
    /** Lokal bekanntes Produkt; null = neues Produkt aus den Feldern unten. */
    val productId: String? = null,
    val name: String = "",
    val brand: String = "",
    val category: String = "",
    val packageSize: String = "",
    val unitId: String? = null,
    val imagePath: String? = null,
    /** Bild stammt aus dem OFF-Cache und wird beim Übernehmen kopiert. */
    val imageFromCache: Boolean = false,
    val fromOff: Boolean = false,
    val minStock: String = "",
    val ingredientGroup: String? = null,
    val locationId: String? = null,
    val level: String = "",
    val shelf: String = "",
    /** Ablaufdatum im Formularformat (TT.MM.JJJJ), leer = keins. */
    val expiry: String = "",
    val quantity: String = "1",
    val quantityTypeId: String? = null,
    val note: String = "",
    val frozen: Boolean = false,
    val frozenAt: String = "",
    val nfcTagId: String? = null,
    /** Vom Nutzer im Formular geprüft/ergänzt. */
    val edited: Boolean = false,
    /**
     * Im Einkaufsmodus abgehakt: Schlüssel des Eintrags der Einkaufsliste ("s-<Produkt>" unter Mindestbestand,
     * "i-<Listeneintrag>" manuell). null = per Scan erfasst.
     */
    val shoppingKey: String? = null,
    /** Wie oft ein abgehakter Artikel danach gescannt wurde (0 = nur abgehakt, noch ohne Scan-Angaben). */
    val scans: Int = 0,
)

/**
 * Einkaufsliste des Mehrfachscans – nur auf diesem Gerät (kein Sync), als JSON-Datei gespeichert,
 * damit nichts verloren geht, wenn die App zwischendurch beendet wird.
 */
@Singleton
class PurchaseDraftStore(private val file: File) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(File(context.filesDir, FILE_NAME))

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val mutex = Mutex()
    private val state = MutableStateFlow<List<PurchaseDraft>?>(null)

    val drafts: Flow<List<PurchaseDraft>> = state.onStart { ensureLoaded() }.filterNotNull()

    suspend fun all(): List<PurchaseDraft> = mutex.withLock { loadLocked() }

    suspend fun get(id: String): PurchaseDraft? = all().firstOrNull { it.id == id }

    /**
     * Gescannten Artikel hinzufügen. Gleicher Barcode mit gleichem Ablaufdatum und Ort (und noch nicht bearbeitet)
     * erhöht nur die Anzahl des vorhandenen Artikels. Liefert den betroffenen Artikel.
     */
    suspend fun addScanned(draft: PurchaseDraft): PurchaseDraft = modify { list ->
        // Im Einkaufsmodus abgehakt und jetzt zum ersten Mal gescannt: Artikel ergänzen statt verdoppeln.
        val checked = PurchaseLogic.checkedIndex(list, draft)
        val index = if (checked >= 0) checked else PurchaseLogic.twinIndex(list, draft)
        if (index < 0) {
            (list + draft) to draft
        } else {
            val merged = if (checked >= 0) PurchaseLogic.fillChecked(list[index], draft) else PurchaseLogic.increment(list[index])
            list.toMutableList().also { it[index] = merged } to merged
        }
    }

    /**
     * Eintrag der Einkaufsliste im Einkaufsmodus abhaken: Artikel anlegen, falls für diesen Eintrag noch keiner da ist
     * (doppeltes Antippen). Liefert true, wenn er neu angelegt wurde.
     */
    suspend fun addChecked(draft: PurchaseDraft): Boolean = modify { list ->
        if (draft.shoppingKey == null || list.any { it.shoppingKey == draft.shoppingKey }) list to false
        else (list + draft) to true
    }

    suspend fun update(draft: PurchaseDraft) = modify { list ->
        list.map { if (it.id == draft.id) draft else it } to Unit
    }

    suspend fun remove(id: String): PurchaseDraft? = modify { list ->
        list.filterNot { it.id == id } to list.firstOrNull { it.id == id }
    }

    /** Artikel wieder einfügen (Rückgängig), an der alten Position. */
    suspend fun restore(draft: PurchaseDraft, index: Int) = modify { list ->
        if (list.any { it.id == draft.id }) list to Unit
        else list.toMutableList().also { it.add(index.coerceIn(0, it.size), draft) } to Unit
    }

    suspend fun clear() = modify { emptyList<PurchaseDraft>() to Unit }

    /** Bilder, die von Artikeln verwendet werden (dürfen beim Aufräumen nicht gelöscht werden). */
    suspend fun imagePaths(): Set<String> = all().mapNotNull { it.imagePath }.toSet()

    private suspend fun ensureLoaded() {
        if (state.value == null) mutex.withLock { loadLocked() }
    }

    private suspend fun <T> modify(change: (List<PurchaseDraft>) -> Pair<List<PurchaseDraft>, T>): T = mutex.withLock {
        val (list, result) = change(loadLocked())
        save(list)
        state.value = list
        result
    }

    private suspend fun loadLocked(): List<PurchaseDraft> {
        state.value?.let { return it }
        val list = withContext(Dispatchers.IO) {
            runCatching { if (file.exists()) json.decodeFromString<List<PurchaseDraft>>(file.readText()) else emptyList() }
                .getOrDefault(emptyList())
        }
        state.value = list
        return list
    }

    private suspend fun save(list: List<PurchaseDraft>) = withContext(Dispatchers.IO) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(list))
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    companion object {
        const val FILE_NAME = "purchase_drafts.json"
    }
}

/** Reine Regeln für die Einkaufsliste des Mehrfachscans. */
object PurchaseLogic {
    /** Index eines unbearbeiteten Artikels mit gleichem Barcode, Ablaufdatum, Ort, Ebene und Fach, sonst -1. */
    fun twinIndex(list: List<PurchaseDraft>, scanned: PurchaseDraft): Int {
        val barcode = scanned.barcode ?: return -1
        return list.indexOfFirst {
            !it.edited && it.barcode == barcode && it.expiry == scanned.expiry && it.locationId == scanned.locationId &&
                it.level == scanned.level && it.shelf == scanned.shelf
        }
    }

    /** Gespeicherter Ort für neue Artikel, nur wenn es ihn noch gibt (gelöschter Ort → kein Ort). */
    fun resolveLocation(savedId: String?, existingIds: Collection<String>): String? =
        savedId?.takeIf { it in existingIds }

    /**
     * Gespeicherte Ebene bzw. gespeichertes Fach, nur wenn der Ort so viele hat ([max] = Anzahl Ebenen/Fächer des
     * Orts; null = Ort ohne Ebenen/Fächer).
     */
    fun resolveSlot(saved: Int?, max: Int?): Int? = saved?.takeIf { max != null && it in 1..max }

    /**
     * Weiterer Scan desselben Artikels: Anzahl + 1. Bei einem abgehakten Artikel zählt die Anzahl von der
     * Einkaufsliste, bis mehr Packungen gescannt wurden ("2×" abgehakt: zwei Scans bleiben 2, der dritte ergibt 3).
     */
    fun increment(draft: PurchaseDraft): PurchaseDraft {
        val n = draft.quantity.trim().toIntOrNull() ?: 0
        if (draft.shoppingKey != null && draft.scans > 0) {
            val scans = draft.scans + 1
            return draft.copy(scans = scans, quantity = maxOf(n, scans).coerceAtMost(MAX_QUANTITY).toString())
        }
        return draft.copy(quantity = (n + 1).coerceAtMost(MAX_QUANTITY).toString())
    }

    /**
     * Index eines im Einkaufsmodus abgehakten, noch nicht gescannten oder bearbeiteten Artikels zum gescannten
     * Produkt (gleiches Produkt oder gleicher Barcode), sonst -1.
     */
    fun checkedIndex(list: List<PurchaseDraft>, scanned: PurchaseDraft): Int = list.indexOfFirst {
        it.shoppingKey != null && it.scans == 0 && !it.edited &&
            ((it.productId != null && it.productId == scanned.productId) || (it.barcode != null && it.barcode == scanned.barcode))
    }

    /**
     * Abgehakten Artikel mit den Angaben des Scans ergänzen (Barcode, Produktdaten, Ablaufdatum, Ort);
     * ID, Bezug zur Einkaufsliste und die Anzahl von der Liste bleiben.
     */
    fun fillChecked(checked: PurchaseDraft, scanned: PurchaseDraft): PurchaseDraft = scanned.copy(
        id = checked.id,
        shoppingKey = checked.shoppingKey,
        scans = 1,
        quantity = checked.quantity,
        productId = scanned.productId ?: checked.productId,
        name = scanned.name.ifBlank { checked.name },
        quantityTypeId = scanned.quantityTypeId ?: checked.quantityTypeId,
    )

    /** Artikel, die zum Eintrag [key] der Einkaufsliste gehören: dort abgehakt oder dasselbe Produkt gescannt. */
    fun forShoppingEntry(list: List<PurchaseDraft>, key: String, productId: String?): List<PurchaseDraft> =
        list.filter { it.shoppingKey == key || (productId != null && it.productId == productId) }

    private const val MAX_QUANTITY = 999
}
