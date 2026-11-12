package de.frelina.vorrat.domain

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChefkochImportTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

    @Test
    fun `Rezept-ID aus Links und geteiltem Text`() {
        assertEquals("2291331365346705", ChefkochLink.recipeId("https://www.chefkoch.de/rezepte/2291331365346705/Lasagne.html"))
        assertEquals("123456", ChefkochLink.recipeId("Schau mal: https://m.chefkoch.de/rezepte/123456/Reis.html?utm=x lecker"))
        assertEquals("123456", ChefkochLink.recipeId("https://www.chefkoch.de/rezepte/drucken/123456/Reis.html"))
        assertEquals("123456", ChefkochLink.recipeId("chefkoch.de/amp/rezepte/123456/x.html"))
        assertNull(ChefkochLink.recipeId("https://www.chefkoch.de/rs/s0/reis/Rezepte.html"))
        assertNull(ChefkochLink.recipeId("https://example.com/rezepte/123456"))
        assertNull(ChefkochLink.recipeId(null))
    }

    @Test
    fun `Mengen im Format des AmountScaler`() {
        assertEquals("400 g", ChefkochMapper.formatAmount(400.0, "g"))
        assertEquals("2", ChefkochMapper.formatAmount(2.0, ""))
        assertEquals("½ Dose", ChefkochMapper.formatAmount(0.5, "Dose"))
        assertEquals("1 ½ TL", ChefkochMapper.formatAmount(1.5, "TL"))
        assertEquals("0,3 l", ChefkochMapper.formatAmount(0.3, "l"))
        assertEquals("etwas", ChefkochMapper.formatAmount(0.0, "etwas"))
        assertNull(ChefkochMapper.formatAmount(0.0, null))
        // Umrechnen auf andere Personenzahl funktioniert mit dem erzeugten Format
        assertEquals("1 Dose", AmountScaler.scale(ChefkochMapper.formatAmount(0.5, "Dose"), 2.0))
    }

    @Test
    fun `Rezept wird vollständig umgewandelt`() {
        val dto = json.decodeFromString<ChefkochRecipeDto>(SAMPLE)
        val r = ChefkochMapper.toRecipe(dto)
        assertEquals("", r.id) // neue ID vergibt das Repository
        assertEquals("Gemüsereis", r.title)
        assertEquals(35, r.minutes)
        assertEquals(4, r.servings)
        assertTrue(r.vegetarian)
        assertEquals(4, r.ingredients.size) // beide Zutatengruppen, leere Namen übersprungen
        assertEquals(RecipeIngredient("Reis", "250 g", group = "rice"), r.ingredients[0])
        assertEquals("Zwiebel(n), fein gewürfelt", r.ingredients[1].name)
        assertEquals("1 kleine", r.ingredients[1].amount)
        assertEquals("onions", r.ingredients[1].group)
        assertEquals("Salz", r.ingredients[3].name)
        assertNull(r.ingredients[3].amount)
        assertEquals(
            listOf("Reis kochen.", "Gemüse anbraten.", "Alles mischen.", "Quelle: https://www.chefkoch.de/rezepte/42/Gemuesereis.html"),
            r.steps,
        )
    }

    @Test
    fun `Zeit aus Einzelzeiten und nicht vegetarisch ohne Tag`() {
        val r = ChefkochMapper.toRecipe(
            ChefkochRecipeDto(
                id = "7", title = "Nudeln", preparationTime = 10, cookingTime = 15, totalTime = 0,
                ingredientGroups = listOf(ChefkochIngredientGroupDto(ingredients = listOf(ChefkochIngredientDto("Spaghetti", "g", 500.0)))),
                fullTags = listOf(ChefkochTagDto(16, "Fleisch")),
            ),
        )
        assertEquals(25, r.minutes)
        assertFalse(r.vegetarian)
        assertEquals("pasta", r.ingredients.single().group)
        assertEquals("Quelle: https://www.chefkoch.de/rezepte/7", r.steps.last())
    }

    @Test
    fun `Vorschaubild mit Größe und Bildnachweis`() {
        val dto = ChefkochRecipeDto(
            id = "42", title = "Lasagne",
            ingredientGroups = listOf(ChefkochIngredientGroupDto(ingredients = listOf(ChefkochIngredientDto("Nudeln")))),
            hasImage = true,
            previewImageUrlTemplate = "https://img.chefkoch-cdn.de/rezepte/42/bilder/9/<format>/lasagne.jpg",
            previewImageCredits = "Koch123",
        )
        assertEquals("https://img.chefkoch-cdn.de/rezepte/42/bilder/9/crop-960x720/lasagne.jpg", ChefkochMapper.imageUrl(dto))
        assertEquals(listOf("Quelle: https://www.chefkoch.de/rezepte/42", "Bild: Koch123"), ChefkochMapper.toRecipe(dto).steps)
    }

    @Test
    fun `kein Bild ohne Vorschau oder bei fremder Adresse`() {
        val base = ChefkochRecipeDto(id = "1", previewImageCredits = "X")
        assertNull(ChefkochMapper.imageUrl(base))
        assertNull(ChefkochMapper.imageUrl(base.copy(previewImageUrlTemplate = "https://img.chefkoch-cdn.de/a/<format>/b.jpg")))
        assertNull(ChefkochMapper.imageUrl(base.copy(hasImage = true, previewImageUrlTemplate = "https://evil.example/<format>/b.jpg")))
        assertNull(ChefkochMapper.imageUrl(base.copy(hasImage = true, previewImageUrlTemplate = "http://img.chefkoch-cdn.de/<format>/b.jpg")))
        assertEquals(listOf("Quelle: https://www.chefkoch.de/rezepte/1"), ChefkochMapper.toRecipe(base).steps)
    }

    private companion object {
        val SAMPLE = """
            {
              "id": "42", "title": " Gemüsereis ", "servings": 4, "preparationTime": 15, "cookingTime": 20,
              "totalTime": 35, "rating": {"rating": 4.5, "numVotes": 12},
              "instructions": "Reis kochen.\n\nGemüse anbraten.\r\nAlles mischen.",
              "ingredientGroups": [
                {"header": "", "ingredients": [
                  {"name": "Reis", "unit": "g", "amount": 250.0, "usageInfo": ""},
                  {"name": "Zwiebel(n)", "unit": "kleine", "amount": 1.0, "usageInfo": ", fein gewürfelt"},
                  {"name": "  ", "unit": "g", "amount": 5.0}
                ]},
                {"header": "Für die Soße", "ingredients": [
                  {"name": "Paprikaschote(n)", "unit": "", "amount": 2.0},
                  {"name": "Salz", "unit": "", "amount": 0.0, "isBasic": true}
                ]}
              ],
              "fullTags": [{"id": 32, "name": "Vegetarisch"}, {"id": 37, "name": "Reis"}],
              "siteUrl": "https://www.chefkoch.de/rezepte/42/Gemuesereis.html"
            }
        """.trimIndent()
    }
}
