package de.frelina.vorrat.domain

import de.frelina.vorrat.data.db.StockEntryEntity
import de.frelina.vorrat.stockItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StockMergeAndSlotsTest {

    private val base = StockEntryEntity(
        id = "a",
        productId = "p1",
        locationId = "l1",
        level = 1,
        shelf = 2,
        expiryDate = LocalDate.of(2026, 12, 24),
        quantity = 3,
        quantityTypeId = "q1",
        note = "Notiz",
    )

    @Test fun `gleiche Daten trotz anderer ID und Anzahl`() {
        assertTrue(StockMerge.sameData(base, base.copy(id = "b", quantity = 7)))
        // Leere Notiz und null gelten als gleich, Leerzeichen am Rand zählen nicht.
        assertTrue(StockMerge.sameData(base.copy(note = null), base.copy(id = "b", note = "  ")))
        assertTrue(StockMerge.sameData(base, base.copy(id = "b", note = " Notiz ")))
    }

    @Test fun `jede abweichende Angabe verhindert das Zusammenfuehren`() {
        listOf(
            base.copy(productId = "p2"),
            base.copy(locationId = "l2"),
            base.copy(level = 2),
            base.copy(level = null),
            base.copy(shelf = 3),
            base.copy(expiryDate = LocalDate.of(2026, 12, 25)),
            base.copy(expiryDate = null),
            base.copy(quantityTypeId = "q2"),
            base.copy(note = "andere"),
        ).forEach { other -> assertFalse(other.toString(), StockMerge.sameData(base, other.copy(id = "b"))) }
    }

    @Test fun `Zwilling wird gefunden, der Eintrag selbst nicht`() {
        val twin = base.copy(id = "b", quantity = 1)
        assertEquals(twin, StockMerge.findTwin(base, listOf(base, base.copy(id = "c", level = 9), twin)))
        assertNull(StockMerge.findTwin(base, listOf(base)))
    }

    @Test fun `Filter nach Ebene und Fach`() {
        val items = listOf(
            stockItem(entryId = "1", level = 1, shelf = 1),
            stockItem(entryId = "2", level = 1, shelf = 2),
            stockItem(entryId = "3", level = 2, shelf = 2),
            stockItem(entryId = "4", locationId = "l2", level = 1, shelf = 2),
            stockItem(entryId = "5"),
        )
        val today = LocalDate.of(2026, 1, 1)
        fun ids(location: String?, level: Int?, shelf: Int?) =
            StockQueries.filter(items, StockFilter.ALL, location, today, 3, level, shelf).map { it.entryId }

        assertEquals(listOf("1", "2", "4"), ids(null, 1, null))
        assertEquals(listOf("2", "3", "4"), ids(null, null, 2))
        assertEquals(listOf("2"), ids("l1", 1, 2))
        assertEquals(listOf("1", "2", "3", "5"), ids("l1", null, null))
    }

    @Test fun `Auswahl fuer Ebene-Fach-Filter`() {
        assertEquals(listOf(1, 2, 3, 7), StockQueries.slotOptions(listOf(7, null, 2), definedCount = 3))
        assertEquals(listOf(2, 5), StockQueries.slotOptions(listOf(5, 2, 5, null), definedCount = null))
        assertEquals(emptyList<Int>(), StockQueries.slotOptions(listOf(null), definedCount = null))
    }
}
