package de.frelina.vorrat.domain

import de.frelina.vorrat.stockItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecipeMatcherTest {

    private val today = LocalDate.of(2026, 11, 8)

    private fun ing(name: String, group: String?, optional: Boolean = false) =
        RecipeIngredient(name = name, amount = null, group = group, optional = optional)

    private fun recipe(title: String, vararg ingredients: RecipeIngredient) =
        Recipe(id = "builtin:$title", title = title, ingredients = ingredients.toList(), steps = listOf("x"))

    private fun pantry(group: String, expiry: LocalDate?, id: String = group, name: String = group) =
        PantryEntry(id, "p-$id", name, group, expiry, 1)

    @Test fun `Gewichte nach Ablaufdatum`() {
        assertEquals(3.0, RecipeMatcher.expiryWeight(today.minusDays(2), today), 0.0)
        assertEquals(3.0, RecipeMatcher.expiryWeight(today.plusDays(1), today), 0.0)
        assertEquals(2.0, RecipeMatcher.expiryWeight(today.plusDays(3), today), 0.0)
        assertEquals(1.5, RecipeMatcher.expiryWeight(today.plusDays(7), today), 0.0)
        assertEquals(1.0, RecipeMatcher.expiryWeight(today.plusDays(8), today), 0.0)
        assertEquals(1.0, RecipeMatcher.expiryWeight(null, today), 0.0)
    }

    @Test fun `Punkte aus vorhandenen, optionalen und fehlenden Zutaten`() {
        val r = recipe(
            "A",
            ing("Sahne", "cream"),
            ing("Nudeln", "pasta"),
            ing("Käse", "cheese", optional = true),
            ing("Eier", "eggs"),
            ing("Salz", null),
            ing("Pfeffer", "spices"),
        )
        val p = listOf(
            pantry("cream", today.plusDays(1)),
            pantry("pasta", today.plusDays(30)),
            pantry("cheese", today.plusDays(30)),
        )
        val match = RecipeMatcher.evaluate(r, p, today)
        // 3.0 (Sahne) + 1.0 (Nudeln) + 0.5 (optional Käse) - 1.5 (Eier fehlt)
        assertEquals(3.0, match.score, 0.0001)
        assertEquals(listOf("Eier"), match.missing.map { it.name })
        assertEquals(listOf("Sahne"), match.rescues.map { it.ingredient.name })
        assertEquals(2, match.neutral.size)
    }

    @Test fun `fehlende optionale Zutat ist neutral`() {
        val r = recipe("B", ing("Nudeln", "pasta"), ing("Käse", "cheese", optional = true))
        val match = RecipeMatcher.evaluate(r, listOf(pantry("pasta", null)), today)
        assertEquals(1.0, match.score, 0.0001)
        assertTrue(match.missing.isEmpty())
        assertEquals(1, match.missingOptional.size)
    }

    @Test fun `ohne Gruppe wird der Name automatisch erkannt`() {
        val r = recipe("C", ing("Spaghetti", null))
        val match = RecipeMatcher.evaluate(r, listOf(pantry("pasta", null)), today)
        assertEquals(1, match.have.size)
    }

    @Test fun `Rezepte mit zu vielen fehlenden oder ohne vorhandene Zutaten werden ausgeblendet`() {
        val ok = recipe("Ok", ing("a", "pasta"), ing("b", "eggs"), ing("c", "cheese"))
        val tooMany = recipe("Zuviel", ing("a", "pasta"), ing("b", "eggs"), ing("c", "cheese"), ing("d", "butter"))
        val none = recipe("Keins", ing("b", "eggs"))
        val p = listOf(pantry("pasta", null))
        assertEquals(listOf("Ok"), RecipeMatcher.rank(listOf(ok, tooMany, none), p, today).map { it.recipe.title })
        assertEquals(3, RecipeMatcher.rankAll(listOf(ok, tooMany, none), p, today).size)
    }

    @Test fun `Sortierung nach Punkten, fehlenden Zutaten und Titel`() {
        val p = listOf(pantry("pasta", today), pantry("eggs", null))
        val high = recipe("Zebra", ing("n", "pasta"))
        val tieB = recipe("Beta", ing("e", "eggs"))
        val tieA = recipe("Alpha", ing("e", "eggs"))
        val fewerMissing = recipe("Gamma", ing("e", "eggs"), ing("x", "cheese"), ing("y", "butter"))
        val ranked = RecipeMatcher.rank(listOf(fewerMissing, tieB, high, tieA), p, today)
        assertEquals(listOf("Zebra", "Alpha", "Beta", "Gamma"), ranked.map { it.recipe.title })
    }

    @Test fun `frueherer Ablauf je Gruppe zaehlt`() {
        val p = listOf(
            pantry("milk", today.plusDays(20), id = "spaet"),
            pantry("milk", today.plusDays(2), id = "frueh"),
            pantry("milk", null, id = "ohne"),
        )
        assertEquals("frueh", RecipeMatcher.pantryByGroup(p).getValue("milk").entryId)
    }

    @Test fun `geoeffnete Packung nutzt das wirksame Ablaufdatum`() {
        val item = stockItem(
            name = "Sahne",
            expiry = today.plusDays(30),
            openedAt = today.minusDays(1),
            openedDays = 3,
        )
        val entry = item.toPantryEntry()
        assertEquals("cream", entry.group)
        assertEquals(today.plusDays(2), entry.expiry)
        val match = RecipeMatcher.evaluate(recipe("D", ing("Sahne", "cream")), listOf(entry), today)
        assertEquals(2.0, match.score, 0.0001)
        assertEquals(1, match.rescues.size)
    }

    @Test fun `manuelle Gruppe ueberschreibt Erkennung, unbekannte ID nicht`() {
        assertEquals("pasta", stockItem(name = "Sahne", ingredientGroup = "pasta").toPantryEntry().group)
        assertEquals("cream", stockItem(name = "Sahne", ingredientGroup = "gibt_es_nicht").toPantryEntry().group)
    }

    @Test fun `bald ablaufende Gruppen`() {
        val p = listOf(
            pantry("milk", today.plusDays(2), name = "Milch"),
            pantry("milk", today.plusDays(1), id = "m2", name = "H-Milch"),
            pantry("eggs", today.plusDays(10)),
            pantry("pasta", today.minusDays(1), name = "Nudeln"),
            PantryEntry("x", "px", "Unbekannt", null, today, 1),
        )
        val groups = RecipeMatcher.expiringGroups(p, today, 3)
        assertEquals(listOf("pasta", "milk"), groups.map { it.groupId })
        assertEquals(2, groups.last().count)
        assertEquals("H-Milch", groups.last().productName)
    }
}
