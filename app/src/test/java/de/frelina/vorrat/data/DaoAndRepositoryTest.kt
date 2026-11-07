package de.frelina.vorrat.data

import android.database.sqlite.SQLiteConstraintException
import de.frelina.vorrat.data.db.LocationEntity
import de.frelina.vorrat.data.image.ImageStore
import de.frelina.vorrat.data.repo.CatalogRepository
import de.frelina.vorrat.data.repo.DeleteResult
import de.frelina.vorrat.data.repo.QuantityChange
import de.frelina.vorrat.data.repo.SaveResult
import de.frelina.vorrat.data.repo.StockRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DaoAndRepositoryTest : DbTestBase() {

    private val stockRepo get() = StockRepository(db, db.stockEntryDao(), db.productDao())
    private val catalogRepo get() = CatalogRepository(db.locationDao(), db.unitDao(), db.quantityTypeDao())

    @Test fun `Startwerte fuer Einheiten und Menge-Arten`() = runBlocking {
        assertEquals(listOf("g", "kg", "ml", "l"), db.unitDao().getAll().map { it.symbol })
        assertEquals(listOf("Stck.", "Pck."), db.quantityTypeDao().getAll().map { it.symbol })
        assertEquals("Stück", db.quantityTypeDao().findBySymbol("stck.")?.name)
    }

    @Test fun `Join liefert Bestandseintrag mit Produkt, Ort und Menge-Art`() = runBlocking {
        val loc = location("Kühlschrank")
        val p = product()
        val e = entry(p, loc)
        val item = db.stockEntryDao().observeItem(e).first()!!
        assertEquals("Nutella", item.productName)
        assertEquals("Kühlschrank", item.locationName)
        assertEquals("Stck.", item.quantityTypeSymbol)
        assertEquals(1, item.level)
        assertEquals(3, item.shelf)
    }

    @Test fun `Ortsname ist eindeutig ohne Gross-Klein`() = runBlocking {
        location("Keller")
        try {
            db.locationDao().insert(LocationEntity(name = "keller"))
            fail("Duplikat erwartet")
        } catch (_: SQLiteConstraintException) {
        }
        assertEquals(SaveResult.Duplicate, catalogRepo.addLocation(" KELLER "))
        assertEquals(SaveResult.Blank, catalogRepo.addLocation("   "))
    }

    @Test fun `Barcode eindeutig, aber mehrere Produkte ohne Barcode erlaubt`() = runBlocking {
        product(name = "A", barcode = null)
        product(name = "B", barcode = null)
        product(name = "C", barcode = "123")
        try {
            product(name = "D", barcode = "123")
            fail("Duplikat erwartet")
        } catch (_: SQLiteConstraintException) {
        }
        assertEquals(3, db.productDao().getAll().size)
    }

    @Test fun `Loeschschutz fuer Orte, Einheiten und Menge-Arten`() = runBlocking {
        val loc = location()
        val p = product()
        entry(p, loc)
        val locEntity = db.locationDao().getAll().single()
        assertEquals(DeleteResult.InUse(1), catalogRepo.deleteLocation(locEntity))
        val gram = db.unitDao().findBySymbol("g")!!
        assertEquals(DeleteResult.InUse(1), catalogRepo.deleteUnit(gram))
        val stck = db.quantityTypeDao().findBySymbol("Stck.")!!
        assertEquals(DeleteResult.InUse(1), catalogRepo.deleteQuantityType(stck))
        // Nicht verwendet → löschbar
        val unused = location("Dachboden")
        assertEquals(DeleteResult.Deleted, catalogRepo.deleteLocation(LocationEntity(unused, "Dachboden")))
        assertEquals(DeleteResult.Deleted, catalogRepo.deleteUnit(db.unitDao().findBySymbol("l")!!))
        // Fremdschlüssel als zweite Sicherung
        try {
            db.locationDao().delete(locEntity)
            fail("Fremdschlüsselverletzung erwartet")
        } catch (_: SQLiteConstraintException) {
        }
    }

    @Test fun `Anzahl auf 0 loescht Eintrag, Produkt inkl Bild bleibt`() = runBlocking {
        val loc = location()
        val p = product(imagePath = "bild.jpg")
        val e = entry(p, loc, quantity = 2)

        assertEquals(QuantityChange.Updated(1), stockRepo.changeQuantity(e, -1))
        val removed = stockRepo.changeQuantity(e, -1)
        assertTrue(removed is QuantityChange.Removed)
        assertNull(db.stockEntryDao().getById(e))

        val productAfter = db.productDao().getById(p)
        assertNotNull(productAfter)
        assertEquals("bild.jpg", productAfter!!.imagePath)
        assertNotNull(db.productDao().getByBarcode("3017624010701"))

        // Rückgängig stellt denselben Eintrag wieder her
        stockRepo.restoreEntry((removed as QuantityChange.Removed).removed)
        assertEquals(1, db.stockEntryDao().getById(e)!!.quantity)
    }

    @Test fun `Anzahl erhoehen`() = runBlocking {
        val e = entry(product(), location(), quantity = 1)
        assertEquals(QuantityChange.Updated(4), stockRepo.changeQuantity(e, 3))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `Anzahl unter 1 wird beim Anlegen abgelehnt`() = runBlocking<Unit> {
        val p = product()
        val loc = location()
        val e = db.stockEntryDao().getById(entry(p, loc))!!
        stockRepo.addEntry(e.copy(quantity = 0))
    }

    @Test fun `Kategorien fuer Autovervollstaendigung`() = runBlocking {
        product(name = "A", barcode = "1", category = "Nudeln")
        product(name = "B", barcode = "2", category = "en:pastas")
        product(name = "C", barcode = "3", category = null)
        assertEquals(listOf("en:pastas", "Nudeln"), db.productDao().observeCategories().first())
    }

    @Test fun `ImageStore entfernt nur nicht referenzierte Bilder`() = runBlocking {
        val store = ImageStore(java.io.File(context.cacheDir, "imgtest").apply { deleteRecursively() })
        val keep = store.saveBytes(byteArrayOf(1, 2, 3))
        val drop = store.saveBytes(byteArrayOf(4))
        store.deleteUnreferenced(setOf(keep))
        assertNotNull(store.fileOrNull(keep))
        assertNull(store.fileOrNull(drop))
    }
}
