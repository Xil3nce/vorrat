package de.frelina.vorrat.data

import de.frelina.vorrat.data.db.StockEntryEntity
import de.frelina.vorrat.data.off.LookedUpProduct
import de.frelina.vorrat.data.repo.PlannedItem
import de.frelina.vorrat.data.repo.ProductPrefill
import de.frelina.vorrat.data.repo.ShoppingItemUpdate
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
    private val shopping get() = ShoppingRepository(db.shoppingDao(), db.productDao(), db.stockEntryDao(), db.unitDao(), writer, images)

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

    @Test fun `OFF-Suchtreffer wird als Produkt mit Bild angelegt, bekannter Barcode wiederverwendet`() = runBlocking {
        val found = LookedUpProduct(
            barcode = "4000000000009", name = "Haferflocken zart", brand = "Kölln", category = "en:oat-flakes",
            packageSize = "500".toBigDecimal(), unitSymbol = "g", imageUrl = "https://example.org/x.jpg",
        )
        val added = shopping.addLookedUp(found, byteArrayOf(1, 2, 3), quantity = 2, packageSize = "500".toBigDecimal(), unitSymbol = "g")!!
        val product = db.productDao().getByBarcode("4000000000009")!!
        assertEquals(product.id, added.productId)
        assertEquals("Kölln", product.brand)
        assertTrue(images.fileOrNull(product.imagePath) != null)
        assertEquals(2, added.quantity)
        assertEquals("500".toBigDecimal(), added.packageSize)
        assertTrue(remote.doc(RemoteCollection.PRODUCTS, product.id) != null)
        // Gleicher Treffer erneut → kein zweites Produkt, Menge erhöht
        shopping.addLookedUp(found, null)
        assertEquals(1, db.productDao().getAll().count { it.barcode == "4000000000009" })
        assertEquals(3, db.shoppingDao().getAll().single().quantity)
    }

    @Test fun `OFF-Suchtreffer mit Namen eines eigenen Produkts verknuepft dieses`() = runBlocking {
        val own = product(name = "Nutella", barcode = "111")
        val found = LookedUpProduct("999", "nutella", "Ferrero", null, null, null, null)
        assertEquals(own, shopping.addLookedUp(found, null)!!.productId)
        assertNull(db.productDao().getByBarcode("999"))
    }

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

    @Test fun `Umbenennen und Packung aendern wird gespeichert und synchronisiert`() = runBlocking {
        val item = shopping.addText("Mehl")!!
        shopping.updateDetails(item.id, ShoppingItemUpdate("  Dinkelmehl ", 3, "1.5".toBigDecimal(), "kg", null, emptyList(), null))
        val saved = db.shoppingDao().getById(item.id)!!
        assertEquals("Dinkelmehl", saved.name)
        assertEquals(3, saved.quantity)
        assertEquals("kg", saved.unitSymbol)
        assertEquals("Dinkelmehl", remote.doc(RemoteCollection.SHOPPING, item.id)!!["name"])
        assertEquals("Dinkelmehl", shopping.observe().first().items.single().item.name)
        // Leerer Name wird nicht übernommen
        shopping.updateDetails(item.id, ShoppingItemUpdate("   ", 1, null, null, "x", listOf("R"), null))
        assertEquals(saved, db.shoppingDao().getById(item.id))
    }

    @Test fun `Alle Angaben eines Eintrags werden gespeichert und synchronisiert`() = runBlocking {
        val p = product(name = "Hack")
        val item = shopping.addText("Fleisch")!!
        shopping.updateDetails(
            item.id,
            ShoppingItemUpdate("Fleisch", 2, null, null, "  Bio, vom Metzger ", listOf("Suppe", "Gulasch, scharf", "Suppe"), p),
        )
        val saved = db.shoppingDao().getById(item.id)!!
        assertEquals("Bio, vom Metzger", saved.note)
        assertEquals("Suppe\nGulasch, scharf", saved.recipes)
        assertEquals(p, saved.productId)
        assertEquals("Suppe\nGulasch, scharf", remote.doc(RemoteCollection.SHOPPING, item.id)!!["recipes"])
        // Verknüpfung lösen, Notiz und Rezepte leeren
        shopping.updateDetails(item.id, ShoppingItemUpdate("Fleisch", 1, null, null, " ", emptyList(), null))
        val cleared = db.shoppingDao().getById(item.id)!!
        assertNull(cleared.productId)
        assertNull(cleared.note)
        assertNull(cleared.recipes)
    }

    @Test fun `Geplante Zutaten - Menge wird Packung, sonst Notiz, Rezepte getrennt`() = runBlocking {
        val added = shopping.addPlanned(
            listOf(
                PlannedItem("Hackfleisch", "700g", listOf("Käse-Hack-Lauch Suppe", "Gulasch")),
                PlannedItem("Eier", "3 Stück", listOf("Kuchen")),
                PlannedItem("Salz", null, emptyList()),
                PlannedItem("Milch", "1,5 l", listOf("Pudding")),
            ),
        )
        assertEquals(4, added)
        val byName = db.shoppingDao().getAll().associateBy { it.name }
        val hack = byName.getValue("Hackfleisch")
        assertEquals(0, "700".toBigDecimal().compareTo(hack.packageSize))
        assertEquals("g", hack.unitSymbol)
        assertNull(hack.note)
        assertEquals("Käse-Hack-Lauch Suppe\nGulasch", hack.recipes)
        val eggs = byName.getValue("Eier")
        assertNull(eggs.packageSize)
        assertEquals("3 Stück", eggs.note)
        assertEquals("Kuchen", eggs.recipes)
        val salt = byName.getValue("Salz")
        assertNull(salt.note)
        assertNull(salt.recipes)
        assertEquals("l", byName.getValue("Milch").unitSymbol)
        // Zweiter Durchgang: alles steht schon auf der Liste
        assertEquals(0, shopping.addPlanned(listOf(PlannedItem("hackfleisch", "1 kg", listOf("X")))))
    }

    @Test fun `Hinzufuegen mit Packung - eigene Angabe gewinnt, sonst Produktpackung`() = runBlocking {
        val own = shopping.addText("Reis", packageSize = "500".toBigDecimal(), unitSymbol = "g")!!
        assertEquals(0, "500".toBigDecimal().compareTo(own.packageSize))
        assertEquals("g", own.unitSymbol)
        product(name = "Zucker")
        val known = shopping.addText("zucker")!!
        assertEquals(0, "400".toBigDecimal().compareTo(known.packageSize))
        assertEquals("g", known.unitSymbol)
    }

    @Test fun `Hinzufuegen mit Menge - Freitext, Produkt verknuepft, bestehender Artikel wird erhoeht`() = runBlocking {
        val p = product(name = "Nudeln")
        val text = shopping.addText("Brot", quantity = 3)!!
        assertEquals(3, text.quantity)
        val linked = shopping.addText("nudeln", quantity = 2)!!
        assertEquals(p, linked.productId)
        assertEquals(2, linked.quantity)
        val again = shopping.addText("Nudeln", quantity = 4)!!
        assertEquals(6, again.quantity)
        assertEquals(6, db.shoppingDao().getById(linked.id)!!.quantity)
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
