package de.frelina.vorrat.data

import de.frelina.vorrat.data.db.ProductEntity
import de.frelina.vorrat.data.db.ProductSource
import de.frelina.vorrat.data.db.RecipeEntity
import de.frelina.vorrat.data.repo.BuiltinRecipeSource
import de.frelina.vorrat.data.repo.RecipeRepository
import de.frelina.vorrat.data.repo.ShoppingRepository
import de.frelina.vorrat.data.sync.RemoteChange
import de.frelina.vorrat.data.sync.RemoteCollection
import de.frelina.vorrat.data.sync.RemoteMapper
import de.frelina.vorrat.data.sync.SyncApplier
import de.frelina.vorrat.domain.Recipe
import de.frelina.vorrat.domain.RecipeIngredient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecipeDataTest : DbTestBase() {

    private val builtinRecipe = Recipe(
        id = "builtin:test", title = "Eingebaut", minutes = 10, servings = 2,
        ingredients = listOf(RecipeIngredient("Nudeln", "200 g", "pasta", false)),
        steps = listOf("Kochen"),
    )
    private val repo get() = RecipeRepository(db.recipeDao(), writer, BuiltinRecipeSource { listOf(builtinRecipe) })

    private fun own(title: String = "Mein Rezept") = Recipe(
        id = "", title = title, minutes = 20, servings = 4,
        ingredients = listOf(RecipeIngredient("Sahne", "1 Becher", "cream", false), RecipeIngredient("Salz", null, null, true)),
        steps = listOf("Eins", "Zwei"), vegetarian = true,
    )

    @Test fun `Eigenes Rezept wird gespeichert, synchronisiert und wieder geladen`() = runBlocking {
        val id = repo.save(own())
        assertEquals(36, id.length)
        val loaded = repo.get(id)!!
        assertEquals("Mein Rezept", loaded.title)
        assertEquals(listOf("Eins", "Zwei"), loaded.steps)
        assertEquals("cream", loaded.ingredients.first().group)
        assertTrue(loaded.vegetarian)
        assertEquals("Mein Rezept", remote.doc(RemoteCollection.RECIPES, id)!!["title"])
        assertEquals(listOf("Mein Rezept", "Eingebaut"), repo.observeAll().first().map { it.title })
    }

    @Test fun `Aendern behaelt die ID, Kopie eines eingebauten Rezepts bekommt eine neue`() = runBlocking {
        val id = repo.save(own())
        val again = repo.save(repo.get(id)!!.copy(title = "Neu"))
        assertEquals(id, again)
        assertEquals("Neu", db.recipeDao().getById(id)!!.title)
        assertEquals(1, db.recipeDao().getAll().size)

        val copyId = repo.save(builtinRecipe)
        assertNotEquals("builtin:test", copyId)
        assertEquals(2, db.recipeDao().getAll().size)
    }

    @Test fun `Loeschen entfernt lokal und in Firestore, eingebaute bleiben`() = runBlocking {
        val id = repo.save(own())
        repo.delete(id)
        repo.delete("builtin:test")
        assertNull(db.recipeDao().getById(id))
        assertNull(remote.doc(RemoteCollection.RECIPES, id))
        assertNotNull(repo.get("builtin:test"))
    }

    @Test fun `SyncApplier uebernimmt Rezepte und loescht sie`() = runBlocking {
        val applier = SyncApplier(db)
        val entity = RecipeEntity(
            id = "r1", title = "Suppe", minutes = 30, servings = 2, ingredients = "[]", steps = "A\nB",
            vegetarian = true, createdAt = 1, updatedAt = 2,
        )
        applier.apply(RemoteCollection.RECIPES, listOf(RemoteChange.Upsert("r1", RemoteMapper.toMap(entity))))
        assertEquals(entity, db.recipeDao().getById("r1"))
        // Echo ist idempotent
        applier.apply(RemoteCollection.RECIPES, listOf(RemoteChange.Upsert("r1", RemoteMapper.toMap(entity))))
        assertEquals(1, db.recipeDao().getAll().size)
        applier.apply(RemoteCollection.RECIPES, listOf(RemoteChange.Removed("r1")))
        assertTrue(db.recipeDao().getAll().isEmpty())
    }

    @Test fun `RemoteMapper lehnt ungueltige Rezepte ab`() {
        assertNull(RemoteMapper.recipe("x", mapOf("title" to "  ", "ingredients" to "[]")))
        assertNull(RemoteMapper.recipe("x", mapOf("title" to "A")))
        val ok = RemoteMapper.recipe("x", mapOf("title" to "A", "ingredients" to "[]", "minutes" to 0L, "servings" to 4L))!!
        assertNull(ok.minutes)
        assertEquals(4, ok.servings)
    }

    @Test fun `Zutatengruppe am Produkt wird synchronisiert und validiert`() {
        val p = ProductEntity(
            id = "p", barcode = null, name = "X", brand = null, category = null, packageSize = null, unitId = null,
            imagePath = null, source = ProductSource.MANUAL, createdAt = 1, updatedAt = 2, ingredientGroup = "cream",
        )
        assertEquals("cream", RemoteMapper.product("p", RemoteMapper.toMap(p))!!.ingredientGroup)
        val bad = RemoteMapper.toMap(p) + ("ingredientGroup" to "Böse; DROP")
        assertNull(RemoteMapper.product("p", bad)!!.ingredientGroup)
    }

    @Test fun `Fehlendes auf die Einkaufsliste ohne Duplikate`() = runBlocking {
        val shopping = ShoppingRepository(db.shoppingDao(), db.productDao(), db.stockEntryDao(), writer, images)
        assertEquals(2, shopping.addMissing(listOf("Eier", "Mehl", " ")))
        assertEquals(1, shopping.addMissing(listOf("eier", "Butter", "Mehl")))
        assertEquals(3, db.shoppingDao().getAll().size)
    }
}
