package de.frelina.vorrat.domain

import de.frelina.vorrat.data.db.HistoryAction
import de.frelina.vorrat.data.db.HistoryEventEntity
import de.frelina.vorrat.data.db.StockItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class HistoryAndNfcLogicTest {

    private fun ev(id: String, at: Long, uid: String? = "andere", notify: Boolean = true) = HistoryEventEntity(
        id = id, createdAt = at, action = HistoryAction.MESSAGE, authorUid = uid, authorName = null,
        productId = null, productName = "", quantity = null, quantityTypeSymbol = null, entryId = null,
        message = "m", notify = notify,
    )

    @Test fun `Nur fremde, neue Hinweise mit Benachrichtigung werden gezeigt`() {
        val now = 10_000_000_000L
        val events = listOf(
            ev("eigen", now - 10, uid = "ich"),
            ev("ohne", now - 10, notify = false),
            ev("alt", 500),
            ev("gesehen", now - 1000),
            ev("neu2", now - 5),
            ev("neu1", now - 50),
        )
        val shown = HistoryLogic.noticesToShow(events, "ich", seenUntil = now - 1000, now = now)
        assertEquals(listOf("neu1", "neu2"), shown.map { it.id })
        assertEquals(now - 5, HistoryLogic.newSeenUntil(now - 1000, shown))
        assertTrue(HistoryLogic.noticesToShow(events, null, 0, now).isEmpty())
    }

    @Test fun `Zu alte Hinweise kommen nicht mehr`() {
        val now = 10_000_000_000L
        val e = ev("x", now - HistoryLogic.MAX_NOTICE_AGE_MS - 1)
        assertTrue(HistoryLogic.noticesToShow(listOf(e), "ich", 0, now).isEmpty())
    }

    @Test fun `Gruppierung nach Tagen, neueste zuerst`() {
        val day1 = LocalDate.of(2026, 11, 7).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val day2 = day1 + 24 * 3600 * 1000
        val groups = HistoryLogic.groupByDay(listOf(ev("a", day1 + 10), ev("b", day2 + 5), ev("c", day2 + 50)), ZoneOffset.UTC)
        assertEquals(listOf(LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 7)), groups.map { it.first })
        assertEquals(listOf("c", "b"), groups.first().second.map { it.id })
    }

    @Test fun `Texte werden bereinigt`() {
        assertNull(HistoryLogic.cleanText("   ", 10))
        assertEquals("Milch ist", HistoryLogic.cleanText(" Milch\n ist alle", 9))
    }

    @Test fun `NFC-URI hin und zurueck`() {
        val id = "3f2c1a8e-1111-4c1e-9f00-123456789abc"
        assertEquals(id, NfcLink.entryIdFrom(NfcLink.uriFor(id)))
        assertEquals(id, NfcLink.entryIdFrom("VORRAT://entry/$id/"))
        assertNull(NfcLink.entryIdFrom("https://example.com/entry/$id"))
        assertNull(NfcLink.entryIdFrom("vorrat://entry/../etwas"))
        assertNull(NfcLink.entryIdFrom(null))
        assertEquals("04A1FF", NfcLink.tagIdHex(byteArrayOf(0x04, 0xA1.toByte(), 0xFF.toByte())))
        assertNull(NfcLink.tagIdHex(ByteArray(0)))
    }

    @Test fun `Chip-Kennung hat Vorrang vor der URI`() {
        assertEquals("perTag", NfcLink.resolve(listOf("perTag"), "perUri", setOf("perUri")))
        assertEquals("perUri", NfcLink.resolve(emptyList(), "perUri", setOf("perUri")))
        // URI zeigt auf aufgebrauchten Eintrag → unbekannt
        assertNull(NfcLink.resolve(emptyList(), "weg", emptySet()))
    }

    @Test fun `Filter Eingefroren`() {
        fun item(id: String, frozen: LocalDate?) = StockItem(
            entryId = id, productId = "p", locationId = null, level = null, shelf = null, expiryDate = null,
            quantity = 1, quantityTypeId = "q", note = null, productName = "x", brand = null, barcode = null,
            imagePath = null, category = null, locationName = null, quantityTypeSymbol = "Port.", frozenAt = frozen,
        )
        val items = listOf(item("a", LocalDate.of(2026, 11, 1)), item("b", null))
        val result = StockQueries.filter(items, StockFilter.FROZEN, null, LocalDate.of(2026, 11, 8), 3)
        assertEquals(listOf("a"), result.map { it.entryId })
    }
}
