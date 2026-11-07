package de.example.vorrat.domain

import de.example.vorrat.stockItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlternativeMatcherTest {
    private val stock = listOf(
        stockItem(entryId = 1, productId = 10, barcode = "111", category = "en:hazelnut-spreads"),
        stockItem(entryId = 2, productId = 11, barcode = "222", category = "en:spreads"),
        stockItem(entryId = 3, productId = 12, barcode = null, category = "EN:Hazelnut-Spreads "),
        stockItem(entryId = 4, productId = 13, barcode = "444", category = null),
        stockItem(entryId = 5, productId = 14, name = "Haselnusscreme", barcode = "555", category = "en:chocolate-spreads"),
    )

    @Test fun `gleiche spezifische Kategorie ist Alternative (Gross-Klein und Leerzeichen egal)`() {
        val result = AlternativeMatcher.findAlternatives("en:hazelnut-spreads", "999", null, stock)
        assertEquals(listOf(1L, 3L), result.map { it.entryId })
    }

    @Test fun `keine Oberkategorien`() {
        // "en:spreads" ist Oberkategorie von hazelnut-spreads → darf nicht passen
        val result = AlternativeMatcher.findAlternatives("en:hazelnut-spreads", "999", null, stock)
        assertTrue(result.none { it.entryId == 2L })
    }

    @Test fun `keine Stichwortsuche im Namen`() {
        val result = AlternativeMatcher.findAlternatives("en:hazelnut-spreads", "999", null, stock)
        assertTrue(result.none { it.entryId == 5L })
    }

    @Test fun `gescanntes Produkt ohne Kategorie liefert keine Alternativen`() {
        assertTrue(AlternativeMatcher.findAlternatives(null, "999", null, stock).isEmpty())
        assertTrue(AlternativeMatcher.findAlternatives("   ", "999", null, stock).isEmpty())
    }

    @Test fun `Bestandsprodukt ohne Kategorie ist nie Alternative`() {
        val result = AlternativeMatcher.findAlternatives("en:hazelnut-spreads", "999", null, stock)
        assertTrue(result.none { it.entryId == 4L })
    }

    @Test fun `das gescannte Produkt selbst ist keine Alternative`() {
        assertTrue(AlternativeMatcher.findAlternatives("en:hazelnut-spreads", "111", null, stock).none { it.entryId == 1L })
        assertTrue(AlternativeMatcher.findAlternatives("en:hazelnut-spreads", null, 12, stock).none { it.entryId == 3L })
    }
}
