package de.frelina.vorrat.domain

import de.frelina.vorrat.data.off.RateLimiter
import de.frelina.vorrat.stockItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ValidationAndSearchTest {

    @Test fun `Anzahl mindestens 1, ganze Zahl`() {
        assertEquals(Validated.Ok(3), Validation.quantity("3"))
        assertEquals(Validated.Error(ValidationError.QUANTITY_MIN_ONE), Validation.quantity("0"))
        assertEquals(Validated.Error(ValidationError.QUANTITY_MIN_ONE), Validation.quantity("-2"))
        assertEquals(Validated.Error(ValidationError.WHOLE_NUMBER), Validation.quantity("1,5"))
        assertEquals(Validated.Error(ValidationError.REQUIRED), Validation.quantity(" "))
    }

    @Test fun `Ebene und Fach nur ganze Zahlen`() {
        assertEquals(Validated.Ok(null), Validation.optionalWholeNumber(""))
        assertEquals(Validated.Ok(2), Validation.optionalWholeNumber("2"))
        assertEquals(Validated.Error(ValidationError.WHOLE_NUMBER), Validation.optionalWholeNumber("2.5"))
        assertEquals(Validated.Error(ValidationError.WHOLE_NUMBER), Validation.optionalWholeNumber("abc"))
        assertEquals(Validated.Error(ValidationError.WHOLE_NUMBER), Validation.optionalWholeNumber("-1"))
    }

    @Test fun `Datum nur gueltig`() {
        assertEquals(Validated.Ok(LocalDate.of(2026, 12, 31)), Validation.optionalDate("31.12.2026"))
        assertEquals(Validated.Ok(null), Validation.optionalDate(""))
        assertEquals(Validated.Error(ValidationError.INVALID_DATE), Validation.optionalDate("31.02.2026"))
        assertEquals(Validated.Error(ValidationError.INVALID_DATE), Validation.optionalDate("2026-12-31"))
        assertEquals(Validated.Ok(LocalDate.of(2028, 2, 29)), Validation.optionalDate("29.02.2028"))
    }

    @Test fun `Packungsgroesse Dezimalzahl groesser 0`() {
        assertEquals(Validated.Ok("1.5".toBigDecimal()), Validation.optionalPositiveDecimal("1,5"))
        assertEquals(Validated.Error(ValidationError.POSITIVE_NUMBER), Validation.optionalPositiveDecimal("0"))
    }

    @Test fun `Textsuche ueber Name, Marke, Barcode, Ort, Notiz – ohne Gross-Klein, auch Umlaute`() {
        val items = listOf(
            stockItem(entryId = 1, name = "Äpfel", brand = "Obsthof"),
            stockItem(entryId = 2, name = "Milch", barcode = "4001234567890"),
            stockItem(entryId = 3, name = "Mehl", locationName = "Vorratsschrank"),
            stockItem(entryId = 4, name = "Reis", note = "Für Risotto"),
        )
        assertEquals(listOf(1L), StockQueries.search(items, "äPF").map { it.entryId })
        assertEquals(listOf(1L), StockQueries.search(items, "obst").map { it.entryId })
        assertEquals(listOf(2L), StockQueries.search(items, "12345").map { it.entryId })
        assertEquals(listOf(3L), StockQueries.search(items, "SCHRANK").map { it.entryId })
        assertEquals(listOf(4L), StockQueries.search(items, "risotto").map { it.entryId })
        assertTrue(StockQueries.search(items, "  ").isEmpty())
    }

    @Test fun `Rate-Limit 15 pro Minute`() = runBlocking {
        var now = 0L
        val limiter = RateLimiter(15, 60_000) { now }
        repeat(15) { assertTrue(limiter.tryAcquire()) }
        assertFalse(limiter.tryAcquire())
        now = 60_000
        assertTrue(limiter.tryAcquire())
    }
}
