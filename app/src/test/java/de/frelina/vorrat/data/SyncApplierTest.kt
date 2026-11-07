package de.frelina.vorrat.data

import de.frelina.vorrat.data.db.LocationEntity
import de.frelina.vorrat.data.db.ProductEntity
import de.frelina.vorrat.data.db.ProductSource
import de.frelina.vorrat.data.db.StockEntryEntity
import de.frelina.vorrat.data.sync.RemoteChange
import de.frelina.vorrat.data.sync.RemoteCollection
import de.frelina.vorrat.data.sync.RemoteMapper
import de.frelina.vorrat.data.sync.SyncApplier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class SyncApplierTest : DbTestBase() {

    private lateinit var applier: SyncApplier
    private lateinit var typeId: String

    @Before
    fun setUpApplier() = runBlocking {
        applier = SyncApplier(db)
        typeId = db.quantityTypeDao().findBySymbol("Stck.")!!.id
    }

    private fun productDoc(name: String = "Nutella", unitId: String? = null) = RemoteMapper.toMap(
        ProductEntity(
            id = "x", barcode = "111", name = name, brand = "Ferrero", category = "en:spreads",
            packageSize = "400".toBigDecimal(), unitId = unitId, imagePath = "bild.jpg",
            source = ProductSource.OPEN_FOOD_FACTS, createdAt = 1, updatedAt = 2,
        ),
    )

    private fun entryDoc(productId: String, locationId: String, quantity: Int = 2) = RemoteMapper.toMap(
        StockEntryEntity(
            id = "x", productId = productId, locationId = locationId, level = 1, shelf = null,
            expiryDate = LocalDate.of(2026, 12, 24), quantity = quantity, quantityTypeId = typeId, note = "N",
        ),
    )

    private suspend fun upsert(c: RemoteCollection, id: String, data: Map<String, Any?>) =
        applier.apply(c, listOf(RemoteChange.Upsert(id, data)))

    @Test fun `Upserts werden in Room uebernommen und das Echo ist idempotent`() = runBlocking {
        upsert(RemoteCollection.LOCATIONS, "l1", mapOf("name" to "Keller"))
        upsert(RemoteCollection.PRODUCTS, "p1", productDoc())
        upsert(RemoteCollection.ENTRIES, "e1", entryDoc("p1", "l1"))
        val before = db.stockEntryDao().getAllItems()
        assertEquals(1, before.size)
        assertEquals("Nutella", before.single().productName)
        assertEquals(LocalDate.of(2026, 12, 24), before.single().expiryDate)
        assertEquals("400".toBigDecimal(), db.productDao().getById("p1")!!.packageSize)

        // Echo desselben Stands
        upsert(RemoteCollection.ENTRIES, "e1", entryDoc("p1", "l1"))
        upsert(RemoteCollection.PRODUCTS, "p1", productDoc())
        assertEquals(before, db.stockEntryDao().getAllItems())
        assertEquals(1, db.productDao().getAll().size)
    }

    @Test fun `Aenderung und Loeschung werden uebernommen`() = runBlocking {
        upsert(RemoteCollection.LOCATIONS, "l1", mapOf("name" to "Keller"))
        upsert(RemoteCollection.PRODUCTS, "p1", productDoc())
        upsert(RemoteCollection.ENTRIES, "e1", entryDoc("p1", "l1", quantity = 2))
        upsert(RemoteCollection.ENTRIES, "e1", entryDoc("p1", "l1", quantity = 5))
        assertEquals(5, db.stockEntryDao().getById("e1")!!.quantity)

        applier.apply(RemoteCollection.ENTRIES, listOf(RemoteChange.Removed("e1")))
        assertNull(db.stockEntryDao().getById("e1"))
        // Löschen eines nicht vorhandenen Dokuments ist harmlos
        applier.apply(RemoteCollection.ENTRIES, listOf(RemoteChange.Removed("e1")))
        assertNotNull(db.productDao().getById("p1"))
    }

    @Test fun `Eintrag vor seinem Produkt wird zurueckgestellt und spaeter uebernommen`() = runBlocking {
        upsert(RemoteCollection.LOCATIONS, "l1", mapOf("name" to "Keller"))
        upsert(RemoteCollection.ENTRIES, "e1", entryDoc("p1", "l1"))
        assertNull(db.stockEntryDao().getById("e1"))
        assertEquals(1, applier.pendingCount())

        upsert(RemoteCollection.PRODUCTS, "p1", productDoc())
        assertNotNull(db.stockEntryDao().getById("e1"))
        assertEquals(0, applier.pendingCount())
    }

    @Test fun `Produkt mit noch unbekannter Einheit wartet auf die Einheit`() = runBlocking {
        upsert(RemoteCollection.PRODUCTS, "p1", productDoc(unitId = "u-neu"))
        assertNull(db.productDao().getById("p1"))
        upsert(RemoteCollection.UNITS, "u-neu", mapOf("symbol" to "Dose", "name" to "Dose"))
        assertEquals("u-neu", db.productDao().getById("p1")!!.unitId)
    }

    @Test fun `Ort wird erst geloescht, wenn keine Eintraege mehr darauf verweisen`() = runBlocking {
        upsert(RemoteCollection.LOCATIONS, "l1", mapOf("name" to "Keller"))
        upsert(RemoteCollection.PRODUCTS, "p1", productDoc())
        upsert(RemoteCollection.ENTRIES, "e1", entryDoc("p1", "l1"))

        applier.apply(RemoteCollection.LOCATIONS, listOf(RemoteChange.Removed("l1")))
        assertNotNull(db.locationDao().getById("l1"))

        applier.apply(RemoteCollection.ENTRIES, listOf(RemoteChange.Removed("e1")))
        assertNull(db.locationDao().getById("l1"))
        assertEquals(0, applier.pendingCount())
    }

    @Test fun `erster Server-Stand entfernt lokale Datensaetze ohne Gegenstueck`() = runBlocking {
        db.locationDao().insert(LocationEntity("alt", "Dachboden"))
        upsert(RemoteCollection.LOCATIONS, "l1", mapOf("name" to "Keller"))
        applier.apply(
            RemoteCollection.LOCATIONS,
            listOf(RemoteChange.Upsert("l1", mapOf("name" to "Keller"))),
            fullIds = setOf("l1"),
        )
        assertEquals(listOf("l1"), db.locationDao().getIds())
    }

    @Test fun `fehlerhafte Dokumente werden ignoriert`() = runBlocking {
        upsert(RemoteCollection.LOCATIONS, "l1", mapOf("name" to ""))
        upsert(RemoteCollection.PRODUCTS, "p1", mapOf("barcode" to "1"))
        upsert(RemoteCollection.LOCATIONS, "l2", mapOf("name" to "Keller"))
        upsert(RemoteCollection.PRODUCTS, "p2", productDoc())
        upsert(RemoteCollection.ENTRIES, "e0", entryDoc("p2", "l2", quantity = 0))
        upsert(
            RemoteCollection.PRODUCTS,
            "p3",
            productDoc(name = "Böse") + ("imagePath" to "../../shared_prefs/x.xml"),
        )
        assertEquals(listOf("l2"), db.locationDao().getIds())
        assertNull(db.productDao().getById("p1"))
        assertNull(db.stockEntryDao().getById("e0"))
        assertNull(db.productDao().getById("p3")!!.imagePath)
        assertEquals(0, applier.pendingCount())
    }

    @Test fun `Zahlen aus Firestore (Long) werden korrekt gelesen`() = runBlocking {
        upsert(RemoteCollection.LOCATIONS, "l1", mapOf("name" to "Keller"))
        upsert(RemoteCollection.PRODUCTS, "p1", productDoc())
        upsert(
            RemoteCollection.ENTRIES,
            "e1",
            mapOf(
                "productId" to "p1", "locationId" to "l1", "quantityTypeId" to typeId,
                "quantity" to 3L, "level" to 2L, "shelf" to null, "expiryDate" to null, "note" to null,
            ),
        )
        val e = db.stockEntryDao().getById("e1")!!
        assertEquals(3, e.quantity)
        assertEquals(2, e.level)
        assertNull(e.expiryDate)
    }
}
