package de.frelina.vorrat.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IngredientMatcherTest {

    private fun m(name: String, category: String? = null) = IngredientMatcher.match(name, category)

    @Test fun `Gruppen haben eindeutige IDs und Labels`() {
        val all = IngredientGroups.all
        assertTrue(all.size >= 60)
        assertEquals(all.size, all.map { it.id }.toSet().size)
        assertEquals(all.size, all.map { it.label }.toSet().size)
        assertTrue(IngredientGroups.isBasic("spices"))
        assertTrue(IngredientGroups.isBasic("oil"))
        assertTrue(IngredientGroups.isBasic("water"))
        assertEquals(all.size, IngredientGroups.sortedByLabel.size)
    }

    @Test fun `einfache Namen`() {
        assertEquals("pasta", m("Spaghetti"))
        assertEquals("pasta", m("Penne Rigate"))
        assertEquals("filled_pasta", m("Frische Tortellini mit Ricotta"))
        assertEquals("filled_pasta", m("Ravioli"))
        assertEquals("eggs", m("Eier"))
        assertEquals("milk", m("Vollmilch"))
        assertEquals("butter", m("Butter"))
        assertEquals("onions", m("Zwiebeln"))
        assertEquals("potatoes", m("Kartoffeln"))
        assertEquals("canned_tomatoes", m("Gehackte Tomaten"))
        assertEquals("cream", m("Schlagsahne"))
    }

    @Test fun `Gross-Kleinschreibung und Umlaute werden ignoriert`() {
        assertEquals(m("Käse"), m("KAESE"))
        assertEquals(m("Käse"), m("käse"))
        assertEquals("cheese", m("Bergkäse"))
    }

    @Test fun `Kategorie hat Vorrang vor dem Namen`() {
        assertEquals("pasta", m("Barilla Nr. 5", "en:pastas"))
        assertEquals("milk", m("Irgendwas", "en:milks"))
        assertEquals("filled_pasta", m("Irgendwas", "en:stuffed-pastas"))
    }

    @Test fun `Fehltreffer werden vermieden`() {
        assertEquals("yogurt", m("Sahnejoghurt"))
        assertEquals("rice", m("Milchreis"))
        assertEquals("garlic", m("Knoblauch"))
        assertEquals("leek", m("Lauch"))
        assertNotEquals("rice", m("Reisnudeln"))
    }

    @Test fun `unbekannte oder leere Namen ergeben null`() {
        assertNull(m(""))
        assertNull(m("   "))
        assertNull(m("Xyzzy"))
    }

    @Test fun `normalize entfernt Umlaute und Sonderzeichen`() {
        assertEquals("kaese brot", IngredientMatcher.normalize("Käse-Brot!"))
        assertEquals("grosse", IngredientMatcher.normalize("Große"))
    }
}
