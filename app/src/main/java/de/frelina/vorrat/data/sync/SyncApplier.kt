package de.frelina.vorrat.data.sync

import androidx.room.withTransaction
import de.frelina.vorrat.data.db.LocationEntity
import de.frelina.vorrat.data.db.ProductEntity
import de.frelina.vorrat.data.db.QuantityTypeEntity
import de.frelina.vorrat.data.db.StockEntryEntity
import de.frelina.vorrat.data.db.UnitEntity
import de.frelina.vorrat.data.db.VorratDatabase
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Spiegelt Firestore-Änderungen in Room (Upsert/Delete in einer Transaktion).
 *
 * - Idempotent: das Echo eigener Schreibvorgänge schreibt dieselben Werte noch einmal.
 * - Die Listener der Sammlungen liefern unabhängig voneinander. Damit die Fremdschlüssel in Room gültig bleiben,
 *   werden Datensätze, deren Elterndatensatz (Produkt, Ort, Menge-Art, Einheit) noch fehlt, zurückgestellt und
 *   bei jedem weiteren Ereignis erneut versucht. Ebenso wartet das Löschen eines noch verwendeten Orts /
 *   einer Einheit / Menge-Art, bis die abhängigen Einträge weg sind.
 * - Beim ersten Server-Snapshot einer Sammlung ([RemoteSnapshot.allIds]) werden lokale Datensätze entfernt,
 *   die es im Haushalt nicht mehr gibt (z. B. während die App geschlossen war gelöscht).
 */
class SyncApplier(private val db: VorratDatabase) {

    private sealed interface Pending {
        data class Upsert(val entity: Any) : Pending
        data object Delete : Pending
    }

    private val mutex = Mutex()
    private val pending: Map<RemoteCollection, LinkedHashMap<String, Pending>> =
        RemoteCollection.DATA.associateWith { LinkedHashMap() }

    /** Anzahl zurückgestellter Änderungen (für Tests/Diagnose). */
    suspend fun pendingCount(): Int = mutex.withLock { pending.values.sumOf { it.size } }

    /**
     * Übernimmt Änderungen einer Sammlung.
     * @param fullIds alle IDs laut Server → lokale Datensätze ohne Gegenstück werden gelöscht.
     * @return in Room übernommene Produkte (für das Nachladen fehlender Bilder).
     */
    suspend fun apply(
        collection: RemoteCollection,
        changes: List<RemoteChange>,
        fullIds: Set<String>? = null,
    ): List<ProductEntity> = mutex.withLock {
        require(collection in RemoteCollection.DATA) { "keine Datensammlung: $collection" }
        val queue = pending.getValue(collection)
        changes.forEach { change ->
            when (change) {
                is RemoteChange.Upsert -> {
                    val entity = toEntity(collection, change.id, change.data)
                    if (entity != null) queue[change.id] = Pending.Upsert(entity)
                }
                is RemoteChange.Removed -> queue[change.id] = Pending.Delete
            }
        }
        db.withTransaction {
            if (fullIds != null) {
                localIds(collection).filter { it !in fullIds && queue[it] !is Pending.Upsert }
                    .forEach { queue[it] = Pending.Delete }
            }
            flush()
        }
    }

    private fun toEntity(collection: RemoteCollection, id: String, data: Map<String, Any?>): Any? = when (collection) {
        RemoteCollection.UNITS -> RemoteMapper.unit(id, data)
        RemoteCollection.QUANTITY_TYPES -> RemoteMapper.quantityType(id, data)
        RemoteCollection.LOCATIONS -> RemoteMapper.location(id, data)
        RemoteCollection.PRODUCTS -> RemoteMapper.product(id, data)
        RemoteCollection.ENTRIES -> RemoteMapper.entry(id, data)
        RemoteCollection.IMAGES -> null
    }

    private suspend fun localIds(collection: RemoteCollection): List<String> = when (collection) {
        RemoteCollection.UNITS -> db.unitDao().getIds()
        RemoteCollection.QUANTITY_TYPES -> db.quantityTypeDao().getIds()
        RemoteCollection.LOCATIONS -> db.locationDao().getIds()
        RemoteCollection.PRODUCTS -> db.productDao().getIds()
        RemoteCollection.ENTRIES -> db.stockEntryDao().getIds()
        RemoteCollection.IMAGES -> emptyList()
    }

    /** Alle zurückgestellten Änderungen versuchen: Upserts Eltern → Kinder, Löschungen Kinder → Eltern. */
    private suspend fun flush(): List<ProductEntity> {
        val products = mutableListOf<ProductEntity>()
        for (collection in RemoteCollection.DATA) {
            val queue = pending.getValue(collection)
            val it = queue.entries.iterator()
            while (it.hasNext()) {
                val op = it.next().value as? Pending.Upsert ?: continue
                if (tryUpsert(op.entity)) {
                    if (op.entity is ProductEntity) products += op.entity
                    it.remove()
                }
            }
        }
        for (collection in RemoteCollection.DATA.asReversed()) {
            val queue = pending.getValue(collection)
            val it = queue.entries.iterator()
            while (it.hasNext()) {
                val (id, op) = it.next()
                if (op == Pending.Delete && tryDelete(collection, id)) it.remove()
            }
        }
        return products
    }

    private suspend fun tryUpsert(entity: Any): Boolean = when (entity) {
        is UnitEntity -> db.unitDao().upsert(entity).let { true }
        is QuantityTypeEntity -> db.quantityTypeDao().upsert(entity).let { true }
        is LocationEntity -> db.locationDao().upsert(entity).let { true }
        is ProductEntity -> {
            val unitOk = entity.unitId == null || db.unitDao().getById(entity.unitId) != null
            if (unitOk) db.productDao().upsert(entity)
            unitOk
        }
        is StockEntryEntity -> {
            val ok = db.productDao().getById(entity.productId) != null &&
                db.locationDao().getById(entity.locationId) != null &&
                db.quantityTypeDao().getById(entity.quantityTypeId) != null
            if (ok) db.stockEntryDao().upsert(entity)
            ok
        }
        else -> true
    }

    private suspend fun tryDelete(collection: RemoteCollection, id: String): Boolean = when (collection) {
        RemoteCollection.ENTRIES -> db.stockEntryDao().deleteById(id).let { true }
        // Bestandseinträge des Produkts entfernt der Fremdschlüssel (CASCADE).
        RemoteCollection.PRODUCTS -> db.productDao().deleteById(id).let { true }
        RemoteCollection.LOCATIONS ->
            (db.locationDao().countStockEntries(id) == 0).also { if (it) db.locationDao().deleteById(id) }
        RemoteCollection.QUANTITY_TYPES ->
            (db.quantityTypeDao().countStockEntries(id) == 0).also { if (it) db.quantityTypeDao().deleteById(id) }
        RemoteCollection.UNITS ->
            (db.unitDao().countProducts(id) == 0).also { if (it) db.unitDao().deleteById(id) }
        RemoteCollection.IMAGES -> true
    }
}
