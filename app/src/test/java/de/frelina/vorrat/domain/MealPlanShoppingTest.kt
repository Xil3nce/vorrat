package de.frelina.vorrat.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MealPlanShoppingTest {

    private fun ing(name: String, amount: String? = null, group: String? = null, optional: Boolean = false) =
        RecipeIngredient(name = name, amount = amount, group = group, optional = optional)

    private fun recipe(title: String, servings: Int?, vararg ingredients: RecipeIngredient) =
        Recipe(id = "r-$title", title = title, servings = servings, ingredients = ingredients.toList(), steps = listOf("x"))

    private fun pantry(group: String, name: String = group) = PantryEntry("e-$group", "p-$group", name, group, null, 1)

    private fun build(
        meals: List<PlannedMeal>,
        pantry: List<PantryEntry> = emptyList(),
        staples: Set<String> = emptySet(),
        listNames: List<String> = emptyList(),
    ) = MealPlanShopping.build(meals, pantry, staples, listNames)

    private fun List<PlanLine>.line(name: String) = first { it.name == name }

    // ------------------------------------------------------------------ Mengen

    @Test fun `Mengen werden auf die Personenzahl umgerechnet`() {
        val r = recipe("Suppe", 2, ing("Kartoffeln", "500 g", "potatoes"))
        val lines = build(listOf(PlannedMeal(r, 4)))
        assertEquals("1 kg", lines.single().amount)
    }

    @Test fun `gleiche Gruppe wird zusammengefasst und Mengen summiert`() {
        val a = recipe("A", 2, ing("Kartoffeln", "500 g", "potatoes"))
        val b = recipe("B", 2, ing("festkochende Kartoffeln", "250 g", "potatoes"))
        val line = build(listOf(PlannedMeal(a, 2), PlannedMeal(b, 2))).single()
        assertEquals("750 g", line.amount)
        assertEquals(listOf("A", "B"), line.recipes)
        assertEquals("group:potatoes", line.key)
        // verschiedene Namen → Gruppenname als Anzeige
        assertEquals(IngredientGroups.label("potatoes"), line.name)
    }

    @Test fun `gleicher Name ohne Gruppe wird ueber den normalisierten Namen zusammengefasst`() {
        val a = recipe("A", 2, ing("Xyzzy", "1 Stück"))
        val b = recipe("B", 2, ing("xyzzy", "2 Stück"))
        val lines = build(listOf(PlannedMeal(a, 2), PlannedMeal(b, 2)))
        assertEquals(1, lines.size)
        assertEquals("3 Stück", lines.single().amount)
        assertEquals("Xyzzy", lines.single().name)
    }

    @Test fun `Mengen mit gleicher Einheit werden addiert, sonst mit Plus verbunden`() {
        assertEquals("1,5 kg", MealPlanShopping.combineAmounts(listOf("1 kg", "500 g")))
        assertEquals("750 ml", MealPlanShopping.combineAmounts(listOf("500 ml", "25 cl")))
        assertEquals("2 EL + 1 Prise", MealPlanShopping.combineAmounts(listOf("2 EL", "1 Prise")))
        assertEquals("200 g + etwas", MealPlanShopping.combineAmounts(listOf("200 g", "etwas")))
        assertEquals("etwas", MealPlanShopping.combineAmounts(listOf("etwas", "etwas", null)))
        assertNull(MealPlanShopping.combineAmounts(listOf(null, " ")))
    }

    // ------------------------------------------------------------------ Vorauswahl

    @Test fun `normale Zutat ist vorausgewaehlt`() {
        val line = build(listOf(PlannedMeal(recipe("A", 2, ing("Xyzzy", "1 Stück")), 2))).single()
        assertTrue(line.checked)
        assertNull(line.hint)
    }

    @Test fun `Zutat im Vorrat ist nicht vorausgewaehlt und nennt das Produkt`() {
        val r = recipe("A", 2, ing("Nudeln", "250 g", "pasta"))
        val line = build(listOf(PlannedMeal(r, 2)), pantry = listOf(pantry("pasta", "Spaghetti"))).single()
        assertFalse(line.checked)
        assertEquals(LineHint.InPantry("Spaghetti"), line.hint)
    }

    @Test fun `Grundvorrat-Gruppe ist nicht vorausgewaehlt`() {
        val r = recipe("A", 2, ing("Salz", null, "spices"), ing("Olivenöl", "2 EL", "oil"))
        val lines = build(listOf(PlannedMeal(r, 2)))
        assertTrue(lines.all { !it.checked && it.hint == LineHint.Basic })
    }

    @Test fun `Zutat ohne Gruppe die nach Grundvorrat klingt ist nicht vorausgewaehlt`() {
        val r = recipe("A", 2, ing("Backpulver", "1 Päckchen"), ing("Xyzzy", "1 Prise"), ing("Xyzzy2", "1 Stück"))
        val lines = build(listOf(PlannedMeal(r, 2)))
        assertEquals(LineHint.Basic, lines.line("Backpulver").hint)
        assertEquals(LineHint.Basic, lines.line("Xyzzy").hint) // "Prise" klingt nach Grundvorrat
        assertTrue(lines.line("Xyzzy2").checked)
        assertTrue(MealPlanShopping.soundsLikeStaple(listOf("Salz")))
        assertFalse(MealPlanShopping.soundsLikeStaple(listOf("Lachs"), listOf("200 g")))
    }

    @Test fun `Immer-zuhause-Liste schlaegt alles andere`() {
        val r = recipe("A", 2, ing("Xyzzy", "1 Stück"), ing("Nudeln", "250 g", "pasta"))
        val staples = setOf(MealPlanShopping.stapleKey(null, "Xyzzy")!!, "group:pasta")
        val lines = build(listOf(PlannedMeal(r, 2)), pantry = listOf(pantry("pasta")), staples = staples)
        assertTrue(lines.all { it.hint == LineHint.Staple && !it.checked })
    }

    @Test fun `Zutat die schon auf der Einkaufsliste steht ist nicht vorausgewaehlt`() {
        val r = recipe("A", 2, ing("Xyzzy", "1 Stück"), ing("Tomaten", "4", "tomatoes"))
        val lines = build(listOf(PlannedMeal(r, 2)), listNames = listOf("xyzzy", "Cherrytomaten"))
        assertEquals(LineHint.OnList, lines.line("Xyzzy").hint)
        assertEquals(LineHint.OnList, lines.line("Tomaten").hint) // über die Gruppe erkannt
        assertTrue(lines.none { it.checked })
    }

    @Test fun `nur optionale Zutat ist nicht vorausgewaehlt`() {
        val r = recipe("A", 2, ing("Xyzzy", "1 Stück", optional = true))
        val other = recipe("B", 2, ing("Xyzzy", "1 Stück"))
        val onlyOptional = build(listOf(PlannedMeal(r, 2))).single()
        assertTrue(onlyOptional.optional)
        assertFalse(onlyOptional.checked)
        // sobald ein Rezept sie braucht, ist sie keine optionale Zutat mehr
        val both = build(listOf(PlannedMeal(r, 2), PlannedMeal(other, 2))).single()
        assertFalse(both.optional)
        assertTrue(both.checked)
    }

    // ------------------------------------------------------------------ Notiz und Schluessel

    @Test fun `Notiz enthaelt Menge und Rezepte`() {
        val line = PlanLine("k", "Xyzzy", "3 Stück", listOf("A", "B"), false, true, null, null)
        assertEquals("3 Stück · A, B", line.note())
        assertEquals("A", line.copy(amount = null, recipes = listOf("A")).note())
        assertNull(line.copy(amount = null, recipes = emptyList()).note())
    }

    @Test fun `Schluessel fuer Immer zuhause`() {
        assertEquals("group:spices", MealPlanShopping.stapleKey("spices", "Salz"))
        assertEquals("xyzzy", MealPlanShopping.stapleKey(null, " Xyzzy "))
        assertNull(MealPlanShopping.stapleKey(null, "   "))
    }

    @Test fun `leerer Plan ergibt keine Zeilen`() {
        assertTrue(build(emptyList()).isEmpty())
    }

    // ------------------------------------------------------------------ Wochenkalender

    @Test fun `Woche beginnt am Montag und hat sieben Tage`() {
        val wed = LocalDate.of(2026, 11, 11)
        val monday = PlanWeek.monday(wed)
        assertEquals(LocalDate.of(2026, 11, 9), monday)
        assertEquals(monday, PlanWeek.monday(monday))
        assertEquals(monday, PlanWeek.monday(LocalDate.of(2026, 11, 15))) // Sonntag
        assertEquals(7, PlanWeek.days(monday).size)
        assertEquals(LocalDate.of(2026, 11, 15), PlanWeek.days(monday).last())
    }

    @Test fun `Auswahltage ab heute`() {
        val today = LocalDate.of(2026, 11, 10)
        val days = PlanWeek.offerDays(today)
        assertEquals(PlanWeek.OFFER_DAYS, days.size)
        assertEquals(today, days.first())
        assertEquals(today.plusDays(13), days.last())
    }

    @Test fun `Einkaufszeitraum ganze Woche oder ab heute`() {
        val monday = LocalDate.of(2026, 11, 9)
        val sunday = LocalDate.of(2026, 11, 15)
        val wed = LocalDate.of(2026, 11, 11)
        assertEquals(wed to sunday, PlanWeek.shoppingRange(monday, wed, fromToday = true))
        assertEquals(monday to sunday, PlanWeek.shoppingRange(monday, wed, fromToday = false))
        // heute nicht in der angezeigten Woche → immer ganze Woche
        assertEquals(monday to sunday, PlanWeek.shoppingRange(monday, wed.plusWeeks(1), fromToday = true))
        assertTrue(PlanWeek.hasFromTodayChoice(monday, wed))
        assertFalse(PlanWeek.hasFromTodayChoice(monday, monday))
        assertFalse(PlanWeek.hasFromTodayChoice(monday, wed.plusWeeks(1)))
    }
}
