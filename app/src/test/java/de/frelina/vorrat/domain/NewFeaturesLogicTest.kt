package de.frelina.vorrat.domain

import de.frelina.vorrat.data.db.ProductEntity
import de.frelina.vorrat.data.db.ProductSource
import de.frelina.vorrat.stockItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class NewFeaturesLogicTest {

    private val today = LocalDate.of(2026, 11, 8)

    // ---------------------------------------------------------------- Geöffnet am
    @Test fun `verbrauchen bis ist das fruehere Datum`() {
        val opened = LocalDate.of(2026, 11, 8)
        assertEquals(LocalDate.of(2026, 11, 11), OpenedLogic.effectiveExpiry(LocalDate.of(2026, 12, 1), opened, 3))
        assertEquals(LocalDate.of(2026, 11, 9), OpenedLogic.effectiveExpiry(LocalDate.of(2026, 11, 9), opened, 3))
        assertEquals(LocalDate.of(2026, 11, 11), OpenedLogic.effectiveExpiry(null, opened, 3))
        // ohne Tage oder ohne Öffnungsdatum zählt das Ablaufdatum
        assertEquals(LocalDate.of(2026, 12, 1), OpenedLogic.effectiveExpiry(LocalDate.of(2026, 12, 1), opened, null))
        assertNull(OpenedLogic.effectiveExpiry(null, null, 3))
    }

    @Test fun `StockItem nutzt das effektive Datum`() {
        val item = stockItem(expiry = LocalDate.of(2027, 1, 1), openedAt = today, openedDays = 4)
        assertEquals(LocalDate.of(2026, 11, 12), item.effectiveExpiry)
    }

    @Test fun `Haltbarkeit aus OFF-Lagerhinweis`() {
        assertEquals(3, OpenedLogic.daysFromHint("Nach dem Öffnen gekühlt aufbewahren und innerhalb von 3 Tagen verbrauchen."))
        assertEquals(14, OpenedLogic.daysFromHint("Nach dem Oeffnen innerhalb von 2 Wochen verbrauchen"))
        assertEquals(5, OpenedLogic.daysFromHint("Once opened, keep refrigerated and consume within 5 days."))
        assertNull(OpenedLogic.daysFromHint("Kühl und trocken lagern."))
        assertNull(OpenedLogic.daysFromHint("Mindestens 3 Tage reifen lassen")) // kein Bezug zum Öffnen
        assertNull(OpenedLogic.daysFromHint(null))
    }

    // ---------------------------------------------------------------- Datumseingabe
    @Test fun `Punkte werden beim Tippen automatisch gesetzt`() {
        assertEquals("2", Validation.formatDateInput("2"))
        assertEquals("24", Validation.formatDateInput("24"))
        assertEquals("24.1", Validation.formatDateInput("241"))
        assertEquals("24.12", Validation.formatDateInput("2412"))
        assertEquals("24.12.2", Validation.formatDateInput("24122"))
        assertEquals("24.12.2026", Validation.formatDateInput("24122026"))
        assertEquals("24.12.2026", Validation.formatDateInput("24.12.20269")) // höchstens 8 Ziffern
        assertEquals("24.12.2026", Validation.formatDateInput("24.12.2026")) // Auswahl per Kalender bleibt gleich
        assertEquals("24", Validation.formatDateInput("24.")) // Löschen über den Punkt hinweg
        assertEquals("", Validation.formatDateInput("ab"))
    }

    @Test fun `zweistelliges Jahr wird akzeptiert`() {
        assertEquals(LocalDate.of(2026, 12, 24), (Validation.optionalDate("24.12.26") as Validated.Ok).value)
        assertEquals(LocalDate.of(2026, 12, 24), (Validation.optionalDate("24.12.2026") as Validated.Ok).value)
        assertTrue(Validation.optionalDate("31.02.26") is Validated.Error)
    }

    // ---------------------------------------------------------------- MHD per Kamera
    @Test fun `MHD in typischen Schreibweisen`() {
        assertEquals(LocalDate.of(2027, 3, 12), ExpiryDateParser.parse("Mindestens haltbar bis: 12.03.2027", today))
        assertEquals(LocalDate.of(2027, 3, 12), ExpiryDateParser.parse("MHD 12.03.27 L4711", today))
        assertEquals(LocalDate.of(2027, 3, 12), ExpiryDateParser.parse("EXP 12/03/2027", today))
        assertEquals(LocalDate.of(2027, 3, 12), ExpiryDateParser.parse("12-03-27", today))
        assertEquals(LocalDate.of(2027, 3, 12), ExpiryDateParser.parse("BBE 12 03 2027", today))
    }

    @Test fun `Monat und Jahr ergeben das Monatsende`() {
        assertEquals(LocalDate.of(2027, 2, 28), ExpiryDateParser.parse("mind. haltbar bis Ende 02.2027", today))
        assertEquals(LocalDate.of(2026, 12, 31), ExpiryDateParser.parse("12/26", today))
    }

    @Test fun `typische Lesefehler werden korrigiert`() {
        assertEquals(LocalDate.of(2027, 1, 10), ExpiryDateParser.parse("MHD 1O.O1.2O27", today))
    }

    @Test fun `Stichwort hat Vorrang, sonst das spaeteste Datum`() {
        assertEquals(
            LocalDate.of(2026, 12, 1),
            ExpiryDateParser.parse("hergestellt 01.11.2026 zu verbrauchen bis 01.12.2026 los 30.12.2026", today),
        )
        assertEquals(LocalDate.of(2027, 5, 1), ExpiryDateParser.parse("01.10.2026  01.05.2027", today))
    }

    @Test fun `unplausible oder fehlende Daten ergeben null`() {
        assertNull(ExpiryDateParser.parse("Zutaten: Zucker, Palmöl, 13% Haselnüsse", today))
        assertNull(ExpiryDateParser.parse("MHD 01.01.2019", today)) // zu weit in der Vergangenheit
        assertNull(ExpiryDateParser.parse("31.02.2027", today)) // gibt es nicht
        assertNull(ExpiryDateParser.parse("", today))
    }

    // ---------------------------------------------------------------- Einkaufsliste
    private fun product(id: String, name: String, min: Int?) = ProductEntity(
        id = id, barcode = null, name = name, brand = null, category = null, packageSize = null, unitId = null,
        imagePath = null, source = ProductSource.MANUAL, createdAt = 0, updatedAt = 0, minStock = min,
    )

    @Test fun `unter Mindestbestand zaehlt die Summe aller Eintraege`() {
        val products = listOf(product("a", "Nudeln", 3), product("b", "Reis", 2), product("c", "Mehl", null), product("d", "Zucker", 1))
        val stock = listOf(
            stockItem(entryId = "1", productId = "a", quantity = 1),
            stockItem(entryId = "2", productId = "a", quantity = 1),
            stockItem(entryId = "3", productId = "b", quantity = 2),
            stockItem(entryId = "4", productId = "c", quantity = 1),
        )
        val short = ShoppingLogic.shortages(products, stock)
        assertEquals(listOf("Nudeln", "Zucker"), short.map { it.product.name })
        assertEquals(1, short.first().missing)
        assertEquals(0, short.last().have)
    }

    @Test fun `Freitext wird einem Produkt gleichen Namens zugeordnet`() {
        val products = listOf(product("a", "Nudeln", null))
        assertEquals("a", ShoppingLogic.matchProduct("  nudeln ", products)?.id)
        assertNull(ShoppingLogic.matchProduct("Nudelsalat", products))
    }

    // ---------------------------------------------------------------- Entnehmen
    @Test fun `Entnehmen schlaegt zuerst vor, was am fruehesten ablaeuft`() {
        val items = listOf(
            stockItem(entryId = "spaet", expiry = LocalDate.of(2027, 1, 1)),
            stockItem(entryId = "ohne", expiry = null),
            stockItem(entryId = "offen", expiry = LocalDate.of(2027, 6, 1), openedAt = today, openedDays = 3),
            stockItem(entryId = "frueh", expiry = LocalDate.of(2026, 12, 1)),
        )
        assertEquals(listOf("offen", "frueh", "spaet", "ohne"), ConsumeLogic.order(items).map { it.entryId })
    }

    @Test fun `Schnellwahl und Begrenzung`() {
        assertEquals(listOf(1), ConsumeLogic.quickAmounts(1))
        assertEquals(listOf(1, 2), ConsumeLogic.quickAmounts(2))
        assertEquals(listOf(1, 2, 3, 6), ConsumeLogic.quickAmounts(6))
        assertEquals(4, ConsumeLogic.clamp(9, 4))
        assertEquals(1, ConsumeLogic.clamp(0, 4))
    }

    // ---------------------------------------------------------------- Ohne Ort
    @Test fun `Filter ohne Ort und Sortierung nach Ort`() {
        val items = listOf(
            stockItem(entryId = "k", locationId = "l1", locationName = "Keller"),
            stockItem(entryId = "x", locationId = null, locationName = null),
        )
        assertEquals(
            listOf("x"),
            StockQueries.filter(items, StockFilter.ALL, StockQueries.NO_LOCATION, today, 3).map { it.entryId },
        )
        assertEquals(listOf("x", "k"), StockQueries.sort(items, StockSort.LOCATION).map { it.entryId })
        assertEquals(listOf("k", "x"), StockQueries.filter(items, StockFilter.ALL, null, today, 3).map { it.entryId })
    }

    @Test fun `Filter bald ablaufend beruecksichtigt geoeffnete Packungen`() {
        val items = listOf(stockItem(entryId = "offen", expiry = LocalDate.of(2027, 6, 1), openedAt = today, openedDays = 2))
        assertEquals(listOf("offen"), StockQueries.filter(items, StockFilter.SOON, null, today, 3).map { it.entryId })
    }
}
