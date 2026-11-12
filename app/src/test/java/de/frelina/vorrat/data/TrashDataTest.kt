package de.frelina.vorrat.data

import de.frelina.vorrat.data.repo.BuiltinRecipeSource
import de.frelina.vorrat.data.repo.RecipeRepository
import de.frelina.vorrat.data.repo.StockRepository
import de.frelina.vorrat.data.repo.TrashRepository
import de.frelina.vorrat.data.repo.TrashRestore
import de.frelina.vorrat.data.sync.RemoteCollection
import de.frelina.vorrat.data.trash.TrashKind
import de.frelina.vorrat.data.trash.TrashStore
import de.frelina.vorrat.domain.Recipe
import de.frelina.vorrat.domain.RecipeIngredient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class TrashDataTest : DbTestBase() {

    private val file by lazy { File(context.cacheDir, "trash-${UUID.randomUUID()}.json") }
    private val store by lazy { TrashStore(file) }
    private val stock by lazy { StockRepository(db, db.stockEntryDao(), db.productDao(), writer, trash = store) }
    private val recipes by lazy { RecipeRepository(db.recipeDao(), writer, images, BuiltinRecipeSource { emptyList() }, store) }
    private val trash by lazy {
        TrashRepository(store, stock, recipes, db.quantityTypeDao(), db.locationDao(), images)
    }

    @Test fun `Geloeschter Eintrag landet im Papierkorb und kommt vollstaendig zurueck`() = runBlocking {
        val p = product()
        val loc = location()
        val id = entry(p, loc, quantity = 3, expiry = LocalDate.of(2027, 1, 2))
        val before = db.stockEntryDao().getById(id)!!

        stock.deleteEntry(id)
        assertNull(db.stockEntryDao().getById(id))
        assertNull(remote.doc(RemoteCollection.ENTRIES, id))
        val item = store.all().single()
        assertEquals("Nutella", item.title)
        assertEquals("3 Stck. · Keller", item.detail)
        // Auch nach Neustart (neue Instanz liest die Datei) noch da.
        assertEquals(1, TrashStore(file).all().size)

        assertEquals(TrashRestore.RESTORED, trash.restore(TrashKind.ENTRY, id))
        assertEquals(before, db.stockEntryDao().getById(id))
        assertNotNull(remote.doc(RemoteCollection.ENTRIES, id))
        assertTrue(store.all().isEmpty())
    }

    @Test fun `Rueckgaengig nimmt den Eintrag auch aus dem Papierkorb`() = runBlocking {
        val id = entry(product(), location())
        val removed = stock.deleteEntry(id)!!
        stock.restoreEntry(removed)
        assertTrue(store.all().isEmpty())
    }

    @Test fun `Geloeschter Ort entfaellt beim Wiederherstellen, fehlendes Produkt verhindert es`() = runBlocking {
        val p = product()
        val loc = location()
        val id = entry(p, loc)
        stock.deleteEntry(id)
        db.locationDao().deleteById(loc)
        assertEquals(TrashRestore.RESTORED, trash.restore(TrashKind.ENTRY, id))
        val restored = db.stockEntryDao().getById(id)!!
        assertNull(restored.locationId)
        assertNull(restored.level)

        stock.deleteEntry(id)
        db.productDao().deleteById(p)
        assertEquals(TrashRestore.PRODUCT_MISSING, trash.restore(TrashKind.ENTRY, id))
        assertEquals(1, store.all().size)
    }

    @Test fun `Rezept mit Foto kommt aus dem Papierkorb zurueck, endgueltig loeschen entfernt das Foto`() = runBlocking {
        val photo = images.saveBytes(byteArrayOf(1, 2, 3))
        val id = recipes.save(
            Recipe(
                id = "", title = "Suppe", minutes = 5, servings = 2,
                ingredients = listOf(RecipeIngredient("Wasser", null, null, false)), steps = listOf("Kochen"),
                imagePath = photo,
            ),
        )
        assertTrue(recipes.delete(id))
        assertNull(db.recipeDao().getById(id))
        assertNull(remote.doc(RemoteCollection.RECIPES, id))
        assertNotNull(images.fileOrNull(photo))
        assertEquals(setOf(photo), store.imagePaths())

        assertEquals(TrashRestore.RESTORED, trash.restore(TrashKind.RECIPE, id))
        assertEquals("Suppe", recipes.get(id)!!.title)
        assertEquals(photo, db.recipeDao().getById(id)!!.imagePath)
        assertNotNull(remote.doc(RemoteCollection.RECIPES, id))

        recipes.delete(id)
        trash.deleteForever(TrashKind.RECIPE, id)
        assertNull(images.fileOrNull(photo))
        assertTrue(trash.items.first().isEmpty())
    }

    @Test fun `Nach 30 Tagen wird aufgeraeumt`() = runBlocking {
        val a = entry(product(), location())
        stock.deleteEntry(a)
        val later = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(30) + 1
        assertTrue(store.purgeExpired(System.currentTimeMillis()).isEmpty())
        assertEquals(1, store.purgeExpired(later).size)
        assertTrue(store.all().isEmpty())
    }
}
