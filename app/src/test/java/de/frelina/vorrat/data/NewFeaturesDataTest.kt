package de.frelina.vorrat.data

import de.frelina.vorrat.data.db.StockEntryEntity
import de.frelina.vorrat.data.repo.ProductPrefill
import de.frelina.vorrat.data.repo.ShoppingRepository
import de.frelina.vorrat.data.repo.StockRepository
import de.frelina.vorrat.data.sync.RemoteChange
import de.frelina.vorrat.data.sync.RemoteCollection
import de.frelina.vorrat.data.sync.RemoteMapper
import de.frelina.vorrat.data.sync.SyncApplier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class NewFeaturesDataTest : DbTestBase() {

    private val stockRepo get() = StockRepository(db, db.stockEntryDao(), db.productDao(), writer)
    private val shopping get() = ShoppingRepository(db.shoppingDao(), db.productDao(), db.stockEntryDao(), writer, images)

    private suspend fun typeId() = db.quantityTypeDao().findBySymbol("Stck.")!!.id

    // ---------------------------------------------------------------- Ohne Ort
    @Test fun `Eintrag ohne Ort wird gespeichert, angezeigt und synchronisiert`() = runBlocking {
        val p = product()
        val entry = StockEntryEntity(
            productId = p, locationId = null, level = null, shelf = null, expiryDate = null,
            quantity = 1, quantityTypeId = typeId(), note = null,
        )
        stockRepo.addEntry(entry)
        val item = db.stockEntryDao().getAllItems().single()
        assertNull(item.locationId)
        assertNull(item.locationName)
        assertNull(remote.doc(RemoteCollection.ENTRIES, entry.id)!!["locationId"])
        // Rückweg: Dokument ohne locationId wird übernommen
        assertEquals(entry, RemoteMapper.entry(entry.id, RemoteMapper.toMap(entry)))
    }

    @Test fun `Sync uebernimmt Eintraege ohne Ort`() = runBlocking {
        val p = product()
        val applier = SyncApplier(db)
        val data = mapOf("productId" to p, "quantityTypeId" to typeId(), "quantity" to 2L)
        applier.apply(RemoteCollection.ENTRIES, listOf(RemoteChange.Upsert("e1", data)))
        assertEquals(0, applier.pendingCount())
        assertNull(db.stockEntryDao().getById("e1")!!.locationId)
    }

    // ---------------------------------------------------------------- Einkaufsliste

    @Test fun `Gescanntes unbekanntes Produkt kommt mit Bild auf die Liste`() = runBlocking {
        val offImage = images.saveBytes(byteArrayOf(1, 2, 3))
        val prefill = ProductPrefill("4000000000001", "Haferflocken", "Kölln", null, null, null, offImage)
        val added = shopping.addScanned(prefill)!!
        val product = db.productDao().getByBarcode("4000000000001")!!
        assertEquals(product.id, added.productId)
        assertNotEquals(offImage, product.imagePath) // Kopie, der OFF-Cache behält sein Bild
        assertTrue(images.fileOrNull(product.imagePath) != null)
        assertEquals(product.imagePath, shopping.observe().first().items.single().product!!.imagePath)
        // Nochmal scannen → nur Menge erhöht, kein zweites Produkt
        shopping.addScanned(prefill)
        assertEquals(2, db.shoppingDao().getAll().single().quantity)
        assertEquals(1, db.productDao().getAll().count { it.barcode == "4000000000001" })
    }

    @Test fun `Freitext mit Produktnamen zeigt das Produktbild`() = runBlocking {
        product(name = "Milch")
        db.shoppingDao().upsert(
            de.frelina.vorrat.data.db.ShoppingItemEntity(productId = null, name = "milch", quantity = 1, note = null, createdAt = 0),
        )
        assertEquals("Milch", shopping.observe().first().items.single().product!!.name)
    }
    @Test fun `Mindestbestand bringt Produkt auf die Liste und wieder runter`() = runBlocking {
        val loc = location()
        val p = product()
        db.productDao().update(db.productDao().getById(p)!!.copy(minStock = 3))
        val e = entry(p, loc, quantity = 2)
        assertEquals(listOf(p), shopping.observe().first().shortages.map { it.product.id })
        assertTrue(shopping.isOnList(p))
        stockRepo.changeQuantity(e, +1)
        assertTrue(shopping.observe().first().shortages.isEmpty())
        assertFalse(shopping.isOnList(p))
    }

    @Test fun `manuelle Eintraege - Freitext, Produkt, Menge, Abhaken, Sync`() = runBlocking {
        val p = product(name = "Nudeln")
        val text = shopping.addText("Brot")!!
        val linked = shopping.addText("nudeln")!! // passt exakt zu einem Produkt → verknüpft
        assertNull(text.productId)
        assertEquals(p, linked.productId)
        // Gleiches Produkt nochmal → Menge erhöht statt doppelt
        shopping.addProduct(db.productDao().getById(p)!!, 2)
        val list = shopping.observe().first()
        assertEquals(2, list.items.size)
        assertEquals(3, list.items.single { it.item.productId == p }.item.quantity)
        assertEquals("Brot", remote.doc(RemoteCollection.SHOPPING, text.id)!!["name"])

        val removed = shopping.remove(text.id)!!
        assertNull(remote.doc(RemoteCollection.SHOPPING, text.id))
        shopping.restore(removed)
        assertEquals(2, shopping.observe().first().items.size)
    }

    @Test fun `Kauf entfernt Produkt- und gleichnamige Freitext-Eintraege`() = runBlocking {
        val p = product(name = "Milch")
        shopping.addProduct(db.productDao().getById(p)!!)
        db.shoppingDao().upsert(
            de.frelina.vorrat.data.db.ShoppingItemEntity(productId = null, name = "MILCH", quantity = 1, note = null, createdAt = 1),
        )
        shopping.addText("Butter")
        assertEquals(2, shopping.removeForProduct(p).size)
        assertEquals(listOf("Butter"), shopping.observe().first().items.map { it.item.name })
    }

    @Test fun `Einkaufsliste wird vom anderen Handy uebernommen`() = runBlocking {
        val applier = SyncApplier(db)
        val data = mapOf("name" to "Eier", "quantity" to 10L, "createdAt" to 5L)
        applier.apply(RemoteCollection.SHOPPING, listOf(RemoteChange.Upsert("s1", data)))
        assertEquals("Eier", db.shoppingDao().getById("s1")!!.name)
        assertEquals(10, db.shoppingDao().getById("s1")!!.quantity)
        applier.apply(RemoteCollection.SHOPPING, listOf(RemoteChange.Removed("s1")))
        assertNull(db.shoppingDao().getById("s1"))
    }

    // ---------------------------------------------------------------- Geöffnet am
    @Test fun `Oeffnen bei mehreren Packungen spaltet eine ab und merkt die Haltbarkeit`() = runBlocking {
        val loc = location()
        val p = product()
        val e = entry(p, loc, quantity = 3)
        val today = LocalDate.of(2026, 11, 8)
        val openedId = stockRepo.markOpened(e, today, 4)!!
        assertNotEquals(e, openedId)
        assertEquals(2, db.stockEntryDao().getById(e)!!.quantity)
        assertNull(db.stockEntryDao().getById(e)!!.openedAt)
        val opened = db.stockEntryDao().getById(openedId)!!
        assertEquals(1, opened.quantity)
        assertEquals(today, opened.openedAt)
        assertEquals(4, opened.openedDays)
        assertEquals(loc, opened.locationId)
        assertEquals(4, db.productDao().getById(p)!!.openedDays)
        assertEquals("2026-11-08", remote.doc(RemoteCollection.ENTRIES, openedId)!!["openedAt"])

        // Einzelne Packung: Eintrag selbst wird markiert; zurücknehmen entfernt die Daten
        assertEquals(openedId, stockRepo.markOpened(openedId, today, 2))
        assertEquals(2, db.stockEntryDao().getById(openedId)!!.openedDays)
        stockRepo.clearOpened(openedId)
        assertNull(db.stockEntryDao().getById(openedId)!!.openedAt)
    }

    @Test fun `Mapper uebertraegt Mindestbestand und Oeffnungsdaten`() = runBlocking {
        val p = db.productDao().getById(product())!!.copy(minStock = 2, openedDays = 5)
        assertEquals(p, RemoteMapper.product(p.id, RemoteMapper.toMap(p)))
        val e = StockEntryEntity(
            productId = p.id, locationId = "l", level = null, shelf = null, expiryDate = null, quantity = 1,
            quantityTypeId = "q", note = null, openedAt = LocalDate.of(2026, 11, 1), openedDays = 3,
        )
        assertEquals(e, RemoteMapper.entry(e.id, RemoteMapper.toMap(e)))
    }
}
