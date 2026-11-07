package de.frelina.vorrat.data.sync

import android.util.Log
import de.frelina.vorrat.data.db.LocationEntity
import de.frelina.vorrat.data.db.ProductEntity
import de.frelina.vorrat.data.db.QuantityTypeEntity
import de.frelina.vorrat.data.db.StockEntryEntity
import de.frelina.vorrat.data.db.UnitEntity
import de.frelina.vorrat.data.db.VorratDatabase
import de.frelina.vorrat.data.household.HouseholdStore
import de.frelina.vorrat.data.image.ImageStore
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schreibt lokale Änderungen zusätzlich an Firestore (Room wird vorher von den Repositories geschrieben).
 * Ohne Haushalt (vor dem Onboarding) passiert nichts. Firestore puffert offline; Fehler werden nur
 * protokolliert – der Snapshot-Listener gleicht Room anschließend mit dem tatsächlichen Stand ab.
 */
@Singleton
class RemoteWriter @Inject constructor(
    private val remote: RemoteStore,
    private val household: HouseholdStore,
    private val imageStore: ImageStore,
) {
    suspend fun putLocation(e: LocationEntity) = set(RemoteCollection.LOCATIONS, e.id, RemoteMapper.toMap(e))

    suspend fun putUnit(e: UnitEntity) = set(RemoteCollection.UNITS, e.id, RemoteMapper.toMap(e))

    suspend fun putQuantityType(e: QuantityTypeEntity) =
        set(RemoteCollection.QUANTITY_TYPES, e.id, RemoteMapper.toMap(e))

    suspend fun putEntry(e: StockEntryEntity) = set(RemoteCollection.ENTRIES, e.id, RemoteMapper.toMap(e))

    /**
     * Produkt schreiben; ein neues Bild wird VOR dem Produkt hochgeladen (Firestore überträgt die Schreibvorgänge
     * eines Geräts in Reihenfolge → das andere Handy findet das Bild, sobald es das Produkt sieht).
     * Ein ersetztes [previousImage] wird entfernt.
     */
    suspend fun putProduct(p: ProductEntity, previousImage: String? = null) {
        val hid = household.currentId() ?: return
        val newImage = p.imagePath?.takeIf { it != previousImage }
        newImage?.let { name -> imageDoc(name)?.let { doc -> safely { remote.set(hid, RemoteCollection.IMAGES, name, doc) } } }
        safely { remote.set(hid, RemoteCollection.PRODUCTS, p.id, RemoteMapper.toMap(p)) }
        if (previousImage != null && previousImage != p.imagePath) {
            safely { remote.delete(hid, RemoteCollection.IMAGES, previousImage) }
        }
    }

    suspend fun delete(collection: RemoteCollection, id: String) {
        val hid = household.currentId() ?: return
        safely { remote.delete(hid, collection, id) }
    }

    /** Anzahl atomar ändern – gleichzeitige Änderungen auf beiden Handys gehen nicht verloren. */
    suspend fun incrementQuantity(entryId: String, delta: Int) {
        val hid = household.currentId() ?: return
        safely { remote.increment(hid, RemoteCollection.ENTRIES, entryId, RemoteMapper.FIELD_QUANTITY, delta.toLong()) }
    }

    /** Ersteller eines Haushalts: alle lokalen Daten inkl. Bilder hochladen (Batches ≤ 500 Operationen). */
    suspend fun uploadAll(householdId: String, db: VorratDatabase) {
        val ops = imageOps(db.productDao().getAllImagePaths().distinct()) + dataOps(db)
        chunk(ops).forEach { remote.commit(householdId, it) }
    }

    /**
     * Backup-Import: Haushaltsdaten in Firestore durch den (bereits in Room übernommenen) Stand ersetzen.
     * Dokumente, die es im neuen Stand nicht gibt, werden gelöscht; [previousImages] sind die Bilder vor dem Import.
     */
    suspend fun replaceAll(db: VorratDatabase, previousImages: Set<String>) {
        val hid = household.currentId() ?: return
        val sets = dataOps(db)
        val newIds = sets.groupBy({ it.collection }, { it.id }).mapValues { it.value.toSet() }
        val deletes = RemoteCollection.DATA.flatMap { c ->
            val existing = runCatching { remote.listIds(hid, c) }.getOrDefault(emptySet())
            (existing - newIds[c].orEmpty()).map { RemoteOp.Delete(c, it) }
        }
        val images = db.productDao().getAllImagePaths().toSet()
        val imageDeletes = (previousImages - images).map { RemoteOp.Delete(RemoteCollection.IMAGES, it) }
        chunk(deletes + imageDeletes + imageOps(images.toList()) + sets).forEach { remote.commit(hid, it) }
    }

    /** Fehlendes Bild aus `images/{name}` laden und lokal ablegen. */
    suspend fun downloadImage(householdId: String, name: String): Boolean {
        if (!RemoteMapper.isSafeFileName(name) || imageStore.fileOrNull(name) != null) return true
        val data = remote.getDocument(householdId, RemoteCollection.IMAGES, name)?.get(FIELD_IMAGE_DATA) as? String
            ?: return false
        imageStore.saveAs(name, Base64.getDecoder().decode(data))
        return true
    }

    private suspend fun dataOps(db: VorratDatabase): List<RemoteOp.Set> =
        db.unitDao().getAll().map { RemoteOp.Set(RemoteCollection.UNITS, it.id, RemoteMapper.toMap(it)) } +
            db.quantityTypeDao().getAll()
                .map { RemoteOp.Set(RemoteCollection.QUANTITY_TYPES, it.id, RemoteMapper.toMap(it)) } +
            db.locationDao().getAll().map { RemoteOp.Set(RemoteCollection.LOCATIONS, it.id, RemoteMapper.toMap(it)) } +
            db.productDao().getAll().map { RemoteOp.Set(RemoteCollection.PRODUCTS, it.id, RemoteMapper.toMap(it)) } +
            db.stockEntryDao().getAll().map { RemoteOp.Set(RemoteCollection.ENTRIES, it.id, RemoteMapper.toMap(it)) }

    private suspend fun imageOps(names: List<String>): List<RemoteOp.Set> = names.mapNotNull { name ->
        imageDoc(name)?.let { RemoteOp.Set(RemoteCollection.IMAGES, name, it) }
    }

    private suspend fun imageDoc(name: String): Map<String, Any?>? {
        if (!RemoteMapper.isSafeFileName(name)) return null
        val bytes = runCatching { imageStore.encodeForSync(name) }.getOrNull() ?: return null
        return mapOf(FIELD_IMAGE_DATA to Base64.getEncoder().encodeToString(bytes), "updatedAt" to System.currentTimeMillis())
    }

    private suspend fun set(collection: RemoteCollection, id: String, data: Map<String, Any?>) {
        val hid = household.currentId() ?: return
        safely { remote.set(hid, collection, id, data) }
    }

    private inline fun safely(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.w(TAG, "Schreiben an Firestore fehlgeschlagen", e)
        }
    }

    companion object {
        private const val TAG = "RemoteWriter"
        const val FIELD_IMAGE_DATA = "data"

        /** Teilt Operationen in Batches mit höchstens [maxOps] Operationen und ungefähr [maxBytes] Daten. */
        fun chunk(
            ops: List<RemoteOp>,
            maxOps: Int = RemoteStore.MAX_BATCH_OPS,
            maxBytes: Int = RemoteStore.MAX_BATCH_BYTES,
        ): List<List<RemoteOp>> {
            val result = mutableListOf<List<RemoteOp>>()
            var current = mutableListOf<RemoteOp>()
            var bytes = 0
            ops.forEach { op ->
                val size = estimateSize(op)
                if (current.isNotEmpty() && (current.size >= maxOps || bytes + size > maxBytes)) {
                    result += current
                    current = mutableListOf()
                    bytes = 0
                }
                current += op
                bytes += size
            }
            if (current.isNotEmpty()) result += current
            return result
        }

        private fun estimateSize(op: RemoteOp): Int = 64 + op.id.length + when (op) {
            is RemoteOp.Delete -> 0
            is RemoteOp.Set -> op.data.entries.sumOf { (k, v) -> k.length + ((v as? String)?.length ?: 8) }
        }
    }
}
