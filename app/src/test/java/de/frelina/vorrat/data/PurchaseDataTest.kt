package de.frelina.vorrat.data

import de.frelina.vorrat.data.purchase.PurchaseDraft
import de.frelina.vorrat.data.purchase.PurchaseDraftStore
import de.frelina.vorrat.data.purchase.PurchaseLogic
import de.frelina.vorrat.data.repo.ProductRepository
import de.frelina.vorrat.data.repo.ShoppingRepository
import de.frelina.vorrat.data.repo.StockRepository
import de.frelina.vorrat.ui.product.EntryForm
import de.frelina.vorrat.ui.product.ProductForm
import de.frelina.vorrat.ui.product.isComplete
import de.frelina.vorrat.ui.product.withForms
import de.frelina.vorrat.ui.purchase.PurchaseCommitter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.LocalDate
import java.util.UUID

/** Einkauf per Mehrfachscan ("+ Bestand"): Artikel sammeln, ergänzen, gesammelt übernehmen. */
@RunWith(RobolectricTestRunner::class)
class PurchaseDataTest : DbTestBase() {

    private val file by lazy { File(context.cacheDir, "drafts-${UUID.randomUUID()}.json") }
    private val store by lazy { PurchaseDraftStore(file) }
    private val stockRepo get() = StockRepository(db, db.stockEntryDao(), db.productDao(), writer)
    private val shopping get() = ShoppingRepository(db.shoppingDao(), db.productDao(), db.stockEntryDao(), db.unitDao(), writer, images)
    private val committer
        get() = PurchaseCommitter(store, stockRepo, ProductRepository(db.productDao(), images, writer), shopping, images)

    private suspend fun stck() = db.quantityTypeDao().findBySymbol("Stck.")!!.id

    @Test
    fun sameBarcodeAndExpiry_increasesQuantity_otherExpiry_addsSecondArticle() = runBlocking {
        val type = stck()
        store.addScanned(PurchaseDraft(barcode = "123", name = "Milch", expiry = "01.12.2026", quantityTypeId = type))
        val second = store.addScanned(PurchaseDraft(barcode = "123", name = "Milch", expiry = "01.12.2026", quantityTypeId = type))
        assertEquals("2", second.quantity)
        store.addScanned(PurchaseDraft(barcode = "123", name = "Milch", expiry = "05.12.2026", quantityTypeId = type))
        val all = store.drafts.first()
        assertEquals(2, all.size)
        assertEquals(listOf("2", "1"), all.map { it.quantity })
    }

    @Test
    fun drafts_surviveRestart() = runBlocking {
        store.addScanned(PurchaseDraft(barcode = "42", name = "Reis"))
        val reloaded = PurchaseDraftStore(file).all()
        assertEquals(listOf("Reis"), reloaded.map { it.name })
    }

    @Test
    fun removeAndRestore_keepsPosition() = runBlocking {
        val a = store.addScanned(PurchaseDraft(barcode = "1", name = "A"))
        store.addScanned(PurchaseDraft(barcode = "2", name = "B"))
        val removed = store.remove(a.id)!!
        store.restore(removed, 0)
        assertEquals(listOf("A", "B"), store.all().map { it.name })
    }

    @Test
    fun unknownProductWithoutName_isIncomplete_untilEdited() = runBlocking {
        val draft = store.addScanned(PurchaseDraft(barcode = "999", quantityTypeId = stck()))
        assertFalse(draft.isComplete())
        val edited = draft.withForms(
            ProductForm(name = "Hafermilch", barcode = "999"),
            EntryForm(quantity = "3", quantityTypeId = stck(), expiry = "01.01.2027"),
        )
        assertTrue(edited.isComplete())
        assertTrue(edited.edited)
    }

    @Test
    fun commit_addsKnownAndNewProducts_mergesTwin_removesFromShoppingList() = runBlocking {
        val loc = location()
        val nutella = product()
        val type = stck()
        // Vorhandener Eintrag mit denselben Daten → wird zusammengeführt.
        entry(nutella, loc, quantity = 2, expiry = LocalDate.of(2026, 12, 24))
        val existingEntry = db.stockEntryDao().getEntriesForProduct(nutella).single()
        shopping.addProduct(db.productDao().getById(nutella)!!)

        store.addScanned(
            PurchaseDraft(
                barcode = "3017624010701", productId = nutella, name = "Nutella", locationId = loc,
                level = "1", shelf = "3", expiry = "24.12.2026", quantityTypeId = type, note = "Notiz",
            ),
        )
        store.addScanned(PurchaseDraft(barcode = "4000000000001", name = "Haferflocken", locationId = loc, quantityTypeId = type))
        // Zweiter Artikel desselben neuen Produkts (anderes Datum) → kein zweites Produkt.
        store.addScanned(
            PurchaseDraft(barcode = "4000000000001", name = "Haferflocken", locationId = loc, quantityTypeId = type, expiry = "01.03.2027"),
        )

        val result = committer.commitAll()

        assertEquals(3, result.committed)
        assertEquals(0, result.incomplete)
        assertTrue(store.all().isEmpty())
        assertEquals(3, db.stockEntryDao().getById(existingEntry.id)!!.quantity)
        val oats = db.productDao().getByBarcode("4000000000001")
        assertNotNull(oats)
        assertEquals(2, db.stockEntryDao().getEntriesForProduct(oats!!.id).size)
        assertFalse(shopping.isOnList(nutella))
    }

    @Test
    fun commit_keepsIncompleteArticles() = runBlocking {
        val type = stck()
        store.addScanned(PurchaseDraft(barcode = "1", name = "Brot", quantityTypeId = type))
        store.addScanned(PurchaseDraft(barcode = "2", name = "", quantityTypeId = type))

        val result = committer.commitAll()

        assertEquals(1, result.committed)
        assertEquals(1, result.incomplete)
        assertEquals(listOf("2"), store.all().map { it.barcode })
    }

    @Test
    fun sameBarcodeAndExpiry_butOtherLocation_addsSecondArticle_andCommitUsesDraftLocation() = runBlocking {
        val type = stck()
        val kitchen = location("Küche")
        val cellar = location("Keller")
        store.addScanned(PurchaseDraft(barcode = "777", name = "Reis", locationId = kitchen, quantityTypeId = type))
        val same = store.addScanned(PurchaseDraft(barcode = "777", name = "Reis", locationId = kitchen, quantityTypeId = type))
        assertEquals("2", same.quantity)
        store.addScanned(PurchaseDraft(barcode = "777", name = "Reis", locationId = cellar, quantityTypeId = type))
        assertEquals(listOf(kitchen, cellar), store.all().map { it.locationId })

        val result = committer.commitAll()

        assertEquals(2, result.committed)
        val rice = db.productDao().getByBarcode("777")!!
        val entries = db.stockEntryDao().getEntriesForProduct(rice.id)
        assertEquals(setOf(kitchen, cellar), entries.map { it.locationId }.toSet())
        assertEquals(2, entries.single { it.locationId == kitchen }.quantity)
    }

    @Test
    fun resolveLocation_onlyKnownLocations() {
        assertEquals("a", PurchaseLogic.resolveLocation("a", listOf("a", "b")))
        assertEquals(null, PurchaseLogic.resolveLocation("gone", listOf("a", "b")))
        assertEquals(null, PurchaseLogic.resolveLocation(null, listOf("a")))
    }
}
