package de.frelina.vorrat.domain

import de.frelina.vorrat.reminder.ReminderDigest
import de.frelina.vorrat.reminder.ReminderScheduler
import de.frelina.vorrat.stockItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class ExpiryLogicTest {
    private val today = LocalDate.of(2026, 11, 7)

    @Test fun `ohne Datum ist NONE`() =
        assertEquals(ExpiryStatus.NONE, ExpiryLogic.status(null, today, 3))

    @Test fun `gestern ist abgelaufen`() =
        assertEquals(ExpiryStatus.EXPIRED, ExpiryLogic.status(today.minusDays(1), today, 3))

    @Test fun `heute zaehlt als bald, nicht als abgelaufen`() =
        assertEquals(ExpiryStatus.SOON, ExpiryLogic.status(today, today, 3))

    @Test fun `Grenze der Vorlaufzeit ist inklusive`() {
        assertEquals(ExpiryStatus.SOON, ExpiryLogic.status(today.plusDays(3), today, 3))
        assertEquals(ExpiryStatus.OK, ExpiryLogic.status(today.plusDays(4), today, 3))
    }

    @Test fun `Vorlaufzeit 0 heisst nur heute ist bald`() {
        assertEquals(ExpiryStatus.SOON, ExpiryLogic.status(today, today, 0))
        assertEquals(ExpiryStatus.OK, ExpiryLogic.status(today.plusDays(1), today, 0))
    }

    @Test fun `negative Vorlaufzeit wird wie 0 behandelt`() =
        assertEquals(ExpiryStatus.OK, ExpiryLogic.status(today.plusDays(1), today, -5))

    @Test fun `Filter bald und abgelaufen`() {
        val items = listOf(
            stockItem(entryId = "1", expiry = today.minusDays(2)),
            stockItem(entryId = "2", expiry = today),
            stockItem(entryId = "3", expiry = today.plusDays(2)),
            stockItem(entryId = "4", expiry = today.plusDays(10)),
            stockItem(entryId = "5", expiry = null),
        )
        assertEquals(listOf("2", "3"), StockQueries.filter(items, StockFilter.SOON, null, today, 3).map { it.entryId })
        assertEquals(listOf("1"), StockQueries.filter(items, StockFilter.EXPIRED, null, today, 3).map { it.entryId })
        assertEquals(5, StockQueries.filter(items, StockFilter.ALL, null, today, 3).size)
    }

    @Test fun `Sortierung nach Ablaufdatum, ohne Datum am Ende`() {
        val items = listOf(
            stockItem(entryId = "1", name = "C", expiry = null),
            stockItem(entryId = "2", name = "B", expiry = today.plusDays(5)),
            stockItem(entryId = "3", name = "A", expiry = today.minusDays(1)),
        )
        assertEquals(listOf("3", "2", "1"), StockQueries.sort(items, StockSort.EXPIRY).map { it.entryId })
    }

    @Test fun `Sammelbenachrichtigung gruppiert abgelaufen, heute, bald`() {
        val items = listOf(
            stockItem(entryId = "1", expiry = today.minusDays(1)),
            stockItem(entryId = "2", expiry = today),
            stockItem(entryId = "3", expiry = today.plusDays(3)),
            stockItem(entryId = "4", expiry = today.plusDays(4)),
            stockItem(entryId = "5", expiry = null),
        )
        val digest = ReminderDigest.from(items, today, 3)
        assertEquals(listOf("1"), digest.expired.map { it.entryId })
        assertEquals(listOf("2"), digest.today.map { it.entryId })
        assertEquals(listOf("3"), digest.soon.map { it.entryId })
        assertEquals(3, digest.total)
    }

    @Test fun `keine Benachrichtigung wenn nichts ansteht`() {
        val digest = ReminderDigest.from(listOf(stockItem(expiry = today.plusDays(30))), today, 3)
        assertTrue(digest.isEmpty)
    }

    @Test fun `erste Ausfuehrung heute 9 Uhr, wenn es noch frueher ist`() {
        val now = ZonedDateTime.of(2026, 11, 7, 7, 30, 0, 0, ZoneId.of("Europe/Berlin"))
        assertEquals(Duration.ofMinutes(90), ReminderScheduler.initialDelay(now, 9, 0))
    }

    @Test fun `erste Ausfuehrung morgen 9 Uhr, wenn 9 Uhr vorbei ist`() {
        val now = ZonedDateTime.of(2026, 11, 7, 9, 0, 0, 0, ZoneId.of("Europe/Berlin"))
        assertEquals(Duration.ofHours(24), ReminderScheduler.initialDelay(now, 9, 0))
    }
}
