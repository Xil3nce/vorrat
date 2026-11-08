package de.frelina.vorrat.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RecipeDataTest {

    private val recipes by lazy {
        // Arbeitsverzeichnis der Unit-Tests ist das Modulverzeichnis (app/).
        RecipeJson.parseRecipes(File("src/main/assets/recipes.json").readText(Charsets.UTF_8))
    }

    @Test fun `mindestens 80 Rezepte mit eindeutigen IDs`() {
        assertTrue("nur ${recipes.size}", recipes.size >= 80)
        assertEquals(recipes.size, recipes.map { it.id }.toSet().size)
        assertTrue(recipes.all { it.builtin && it.id.removePrefix("builtin:").isNotBlank() })
    }

    @Test fun `alle Gruppen sind gueltig und Felder sinnvoll`() {
        recipes.forEach { r ->
            assertTrue("${r.id}: Titel", r.title.isNotBlank())
            assertTrue("${r.id}: Schritte", r.steps.size in 3..6)
            assertTrue("${r.id}: Zutaten", r.ingredients.isNotEmpty())
            assertTrue("${r.id}: Minuten", (r.minutes ?: 1) > 0)
            assertTrue("${r.id}: Portionen", (r.servings ?: 1) > 0)
            r.ingredients.forEach { i ->
                assertTrue("${r.id}: Zutat ohne Name", i.name.isNotBlank())
                assertTrue("${r.id}: unbekannte Gruppe ${i.group}", i.group == null || IngredientGroups.isKnown(i.group))
            }
            assertTrue("${r.id}: ohne Pflichtzutat mit Gruppe", r.ingredients.any { !it.optional && it.group != null })
        }
    }

    @Test fun `Encode und Decode der Zutaten`() {
        val list = recipes.first().ingredients
        assertEquals(list, RecipeJson.decodeIngredients(RecipeJson.encodeIngredients(list)))
        assertTrue(RecipeJson.decodeIngredients("kaputt").isEmpty())
        assertEquals(listOf("a", "b"), RecipeJson.textToSteps(" a \n\n b\n"))
    }
}
