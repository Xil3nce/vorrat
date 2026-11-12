package de.frelina.vorrat.data

import de.frelina.vorrat.data.db.LocationEntity
import de.frelina.vorrat.data.purchase.PurchaseDraft
import de.frelina.vorrat.data.purchase.PurchaseDraftStore
import de.frelina.vorrat.data.purchase.PurchaseLogic
import de.frelina.vorrat.data.repo.ProductRepository
import de.frelina.vorrat.data.repo.ShoppingRepository
import de.frelina.vorrat.data.repo.StockRepository
import de.frelina.vorrat.domain.BoardRow
import de.frelina.vorrat.domain.ShopMode
import de.frelina.vorrat.stockItem
import de.frelina.vorrat.ui.product.isComplete
import de.frelina.vorrat.ui.purchase.PurchaseCommitter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.math.BigDecimal
import java.util.UUID

/** Einkaufsmodus: Einkaufsliste abhaken → Artikel im Einkauf → gesammelt in den Bestand. */
@RunWith(RobolectricTestRunner::class)
class ShopModeDataTest : DbTestBase() {

    private val file by lazy { File(context.cacheDir, "drafts-${UUID.randomUUID()}.json") }
    private val store by lazy { PurchaseDraftStore(file) }
    private val stockRepo get() = StockRepository(db, db.stockEntryDao(), db.productDao(), writer)
    private val shopping get() = ShoppingRepository(db.shoppingDao(), db.productDao(), db.stockEntryDao(), db.unitDao(), writer, images)
    private val committer
        get() = PurchaseCommitter(store, stockRepo, ProductRepository(db.productDao(), images, writer), shopping, images)

    private suspend fun stck() = db.quantityTypeDao().findBySymbol("Stck.")!!.id

    private val noPlace = ShopMode.Placement(null, null, null)

    @Test
    fun split_movesCheckedToTheEnd_andDropsEmptyDepartments() {
        val rows = listOf(
            BoardRow<String>("d-a", "a", null),
            BoardRow("i-1", "a", "Äpfel"),
            BoardRow("i-2", "a", "Birnen"),
            BoardRow<String>("d-b", "b", null),
            BoardRow("i-3", "b", "Milch"),
        )
        val split = ShopMode.split(rows) { it.key in setOf("i-1", "i-3") }
        assertEquals(listOf("d-a", "i-2"), split.open.map { it.key })
        assertEquals(listOf("i-1", "i-3"), split.checked.map { it.key })
        assertEquals(1, split.openCount)
        assertEquals(3, split.total)
    }

    @Test
    fun keys_itemIdOnlyForManualEntries() {
        assertEquals("abc", ShopMode.itemId(ShopMode.itemKey("abc")))
        assertNull(ShopMode.itemId(ShopMode.shortageKey("abc")))
    }

    @Test
    fun placement_scannerLocationWins_thenExistingStock_thenSingleLocation() {
        val kitchen = LocationEntity(id = "k", name = "Küche", levels = 3)
        val cellar = LocationEntity(id = "c", name = "Keller")
        val inCellar = stockItem(locationId = "c", level = 2)
        assertEquals(ShopMode.Placement("k", 2, null), ShopMode.placement("k", 2, null, listOf(kitchen, cellar), inCellar))
        assertEquals(ShopMode.Placement("c", 2, null), ShopMode.placement(null, null, null, listOf(kitchen, cellar), inCellar))
        assertEquals(ShopMode.Placement("c", null, null), ShopMode.placement("gone", 5, null, listOf(cellar), null))
        assertEquals(ShopMode.Placement(null, null, null), ShopMode.placement(null, null, null, listOf(kitchen, cellar), null))
    }

    @Test
    fun checkTwice_addsOnlyOneArticle_andUncheckFindsIt() = runBlocking {
        val draft = ShopMode.draft("i-1", null, "Tomaten", 2, null, null, noPlace, stck())
        assertTrue(store.addChecked(draft))
        assertFalse(store.addChecked(draft.copy(id = "other")))
        val all = store.all()
        assertEquals(listOf("2"), all.map { it.quantity })
        assertTrue(all.single().isComplete())
        assertEquals(1, PurchaseLogic.forShoppingEntry(all, "i-1", null).size)
        assertTrue(PurchaseLogic.forShoppingEntry(all, "i-2", null).isEmpty())
    }

    @Test
    fun draftForFreeText_packageSizeOnlyWithUnit() = runBlocking {
        val gram = db.unitDao().findBySymbol("g")!!.id
        val withUnit = ShopMode.draft("i-1", null, " Mehl ", 1, BigDecimal("500.0"), gram, noPlace, stck())
        assertEquals("Mehl", withUnit.name)
        assertEquals("500", withUnit.packageSize)
        assertEquals(gram, withUnit.unitId)
        val withoutUnit = ShopMode.draft("i-2", null, "Mehl", 1, BigDecimal("500"), null, noPlace, stck())
        assertEquals("", withoutUnit.packageSize)
        assertNull(withoutUnit.unitId)
        assertTrue(withUnit.isComplete() && withoutUnit.isComplete())
    }

    @Test
    fun scanAfterCheck_fillsTheCheckedArticle_andCountsScansAgainstListQuantity() = runBlocking {
        val type = stck()
        val nutella = db.productDao().getById(product())!!
        store.addChecked(ShopMode.draft(ShopMode.shortageKey(nutella.id), nutella, nutella.name, 2, null, null, noPlace, type))

        fun scan() = PurchaseDraft(barcode = nutella.barcode, productId = nutella.id, name = nutella.name, expiry = "24.12.2026", quantityTypeId = type)
        val first = store.addScanned(scan())
        assertEquals("2", first.quantity)
        assertEquals("24.12.2026", first.expiry)
        assertEquals(ShopMode.shortageKey(nutella.id), first.shoppingKey)
        // Zweite Packung gescannt: bleibt bei den 2 von der Liste, die dritte zählt hoch.
        assertEquals("2", store.addScanned(scan()).quantity)
        assertEquals("3", store.addScanned(scan()).quantity)
        assertEquals(1, store.all().size)
    }

    @Test
    fun scannedProduct_countsAsCheckedOnTheList() = runBlocking {
        val nutella = product()
        store.addScanned(PurchaseDraft(barcode = "3017624010701", productId = nutella, name = "Nutella", quantityTypeId = stck()))
        assertEquals(1, PurchaseLogic.forShoppingEntry(store.all(), ShopMode.shortageKey(nutella), nutella).size)
    }

    @Test
    fun commit_checkedArticles_goToStock_andLeaveTheShoppingList() = runBlocking {
        val type = stck()
        val loc = location()
        val nutella = db.productDao().getById(product())!!
        val listed = shopping.addProduct(nutella, quantity = 2)
        val freeText = shopping.addText("Tomaten")!!
        val place = ShopMode.Placement(loc, null, null)

        store.addChecked(ShopMode.draft(ShopMode.itemKey(listed.id), nutella, nutella.name, listed.quantity, null, null, place, type))
        store.addChecked(ShopMode.draft(ShopMode.itemKey(freeText.id), null, freeText.name, 1, null, null, place, type))

        val result = committer.commitAll()

        assertEquals(2, result.committed)
        assertEquals(0, result.incomplete)
        assertEquals(2, db.stockEntryDao().getEntriesForProduct(nutella.id).single().quantity)
        val tomatoes = db.productDao().getAll().single { it.name == "Tomaten" }
        assertEquals(1, db.stockEntryDao().getEntriesForProduct(tomatoes.id).single().quantity)
        assertTrue(db.shoppingDao().getAll().isEmpty())
        assertTrue(store.all().isEmpty())
    }
}
