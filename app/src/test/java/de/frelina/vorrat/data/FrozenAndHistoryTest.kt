package de.frelina.vorrat.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import de.frelina.vorrat.data.db.HistoryAction
import de.frelina.vorrat.data.db.HistoryEventEntity
import de.frelina.vorrat.data.db.StockEntryEntity
import de.frelina.vorrat.data.repo.HistoryRepository
import de.frelina.vorrat.data.repo.QuantityChange
import de.frelina.vorrat.data.repo.StockRepository
import de.frelina.vorrat.data.settings.SettingsRepository
import de.frelina.vorrat.data.sync.RemoteChange
import de.frelina.vorrat.data.sync.RemoteCollection
import de.frelina.vorrat.data.sync.RemoteMapper
import de.frelina.vorrat.data.sync.SyncApplier
import de.frelina.vorrat.domain.HistoryLogic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.LocalDate
import java.util.UUID

/** Selbst Eingefrorenes (Einfrierdatum, NFC-Chip) und Verlauf im Haushalt. */
@RunWith(RobolectricTestRunner::class)
class FrozenAndHistoryTest : DbTestBase() {

    private lateinit var settingsScope: CoroutineScope
    private lateinit var settings: SettingsRepository
    private lateinit var history: HistoryRepository
    private lateinit var stockRepo: StockRepository

    @Before
    fun setUpHistory() {
        settingsScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val file = File(context.cacheDir, "settings-${UUID.randomUUID()}.preferences_pb")
        settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = settingsScope) { file })
        history = HistoryRepository(db.historyDao(), db.productDao(), db.quantityTypeDao(), writer, householdStore, settings)
        stockRepo = StockRepository(db, db.stockEntryDao(), db.productDao(), writer, history)
    }

    @After
    fun tearDownHistory() {
        settingsScope.cancel()
    }

    private suspend fun typeId() = db.quantityTypeDao().findBySymbol("Stck.")!!.id

    private suspend fun frozenEntry(productId: String, quantity: Int = 2, tag: String? = null) = StockEntryEntity(
        productId = productId, locationId = location("Gefriertruhe"), level = 2, shelf = 1, expiryDate = null,
        quantity = quantity, quantityTypeId = typeId(), note = null,
        frozenAt = LocalDate.of(2026, 11, 1), nfcTagId = tag,
    )

    private suspend fun events() = db.historyDao().getAll()

    // ---------------------------------------------------------------- Eingefrorenes / NFC

    @Test fun `Eingefrorenes mit Fach, Ebene und Chip wird gespeichert und synchronisiert`() = runBlocking {
        val p = product(name = "Bolognese", barcode = null)
        val e = frozenEntry(p, tag = "04A1B2C3")
        stockRepo.addEntry(e)
        val item = db.stockEntryDao().getAllItems().single()
        assertEquals(LocalDate.of(2026, 11, 1), item.frozenAt)
        assertEquals("04A1B2C3", item.nfcTagId)
        assertEquals(2, item.level)
        assertEquals(1, item.shelf)
        val doc = remote.doc(RemoteCollection.ENTRIES, e.id)!!
        assertEquals("2026-11-01", doc["frozenAt"])
        assertEquals("04A1B2C3", doc["nfcTagId"])
        assertEquals(e, RemoteMapper.entry(e.id, doc))
    }

    @Test fun `Chip gehoert immer nur zu einem Eintrag`() = runBlocking {
        val p = product(name = "Bolognese", barcode = null)
        val first = frozenEntry(p, tag = "TAG1")
        stockRepo.addEntry(first)
        val second = frozenEntry(p, tag = "TAG1").copy(frozenAt = LocalDate.of(2026, 11, 5))
        stockRepo.addEntry(second)
        assertNull(db.stockEntryDao().getById(first.id)!!.nfcTagId)
        assertEquals(listOf(second.id), stockRepo.entriesForNfcTag("TAG1").map { it.id })
        // Neu zuordnen über die Detailansicht
        assertTrue(stockRepo.assignNfcTag(first.id, "TAG1"))
        assertEquals(listOf(first.id), stockRepo.entriesForNfcTag("TAG1").map { it.id })
        assertNull(remote.doc(RemoteCollection.ENTRIES, second.id)!!["nfcTagId"])
        stockRepo.clearNfcTag(first.id)
        assertTrue(stockRepo.entriesForNfcTag("TAG1").isEmpty())
    }

    @Test fun `Eintraege mit Chip werden nicht zusammengefuehrt, ohne Chip schon`() = runBlocking {
        val p = product(name = "Bolognese", barcode = null)
        val a = frozenEntry(p)
        stockRepo.addEntry(a)
        val twin = frozenEntry(p).copy(locationId = a.locationId)
        assertEquals(a.id, stockRepo.findTwin(twin)?.id)
        assertNull(stockRepo.findTwin(twin.copy(nfcTagId = "X")))
        assertNull(stockRepo.findTwin(twin.copy(frozenAt = LocalDate.of(2026, 10, 1))))
    }

    @Test fun `Produkt ohne Barcode wird per Name gefunden`() = runBlocking {
        val p = product(name = "Bolognese", barcode = null)
        product(name = "Bolognese-Sauce Glas", barcode = "123")
        assertEquals(p, db.productDao().findWithoutBarcodeByName("bolognese")?.id)
        assertNull(db.productDao().findWithoutBarcodeByName("Chili"))
    }

    // ---------------------------------------------------------------- Verlauf

    @Test fun `Hinzufuegen und Entnehmen landen mit Person und Menge im Verlauf`() = runBlocking {
        settings.setMyName("Frank")
        val p = product(name = "Milch", barcode = "400")
        val e = StockEntryEntity(
            productId = p, locationId = null, level = null, shelf = null, expiryDate = null,
            quantity = 2, quantityTypeId = typeId(), note = null,
        )
        stockRepo.addEntry(e)
        stockRepo.changeQuantity(e.id, -1)
        val list = events()
        assertEquals(listOf(HistoryAction.ADDED, HistoryAction.REMOVED), list.map { it.action })
        assertEquals(listOf(2, 1), list.map { it.quantity })
        assertTrue(list.all { it.authorName == "Frank" && it.authorUid == "uid-test" && it.productName == "Milch" })
        assertTrue(list.all { it.quantityTypeSymbol == "Stck." })
        assertFalse(list.last().outOfStock)
        // Im Haushalt gespeichert
        list.forEach { assertNotNull(remote.doc(RemoteCollection.HISTORY, it.id)) }
    }

    @Test fun `Letztes entnommen - ist alle mit Benachrichtigung, Rueckgaengig nimmt den Eintrag zurueck`() = runBlocking {
        val p = product(name = "Milch", barcode = "400")
        val e = StockEntryEntity(
            productId = p, locationId = null, level = null, shelf = null, expiryDate = null,
            quantity = 1, quantityTypeId = typeId(), note = null,
        )
        stockRepo.addEntry(e)
        val removed = stockRepo.changeQuantity(e.id, -1) as QuantityChange.Removed
        val last = events().last()
        assertEquals(HistoryAction.REMOVED, last.action)
        assertTrue(last.outOfStock)
        assertTrue(last.notify) // Standard: Bescheid geben, wenn etwas alle ist
        stockRepo.restoreEntry(removed.removed)
        assertNull(db.historyDao().getById(last.id))
        assertNull(remote.doc(RemoteCollection.HISTORY, last.id))
    }

    @Test fun `Ohne automatische Meldung - nachtraeglich Bescheid geben`() = runBlocking {
        settings.setNotifyWhenEmpty(false)
        val p = product(name = "Milch", barcode = "400")
        val e = StockEntryEntity(
            productId = p, locationId = null, level = null, shelf = null, expiryDate = null,
            quantity = 1, quantityTypeId = typeId(), note = null,
        )
        stockRepo.addEntry(e)
        stockRepo.deleteEntry(e.id)
        val ev = events().last()
        assertEquals(HistoryAction.DELETED, ev.action)
        assertTrue(ev.outOfStock)
        assertFalse(ev.notify)
        history.notifyLater(ev.id)
        assertTrue(db.historyDao().getById(ev.id)!!.notify)
        assertEquals(true, remote.doc(RemoteCollection.HISTORY, ev.id)!!["notify"])
    }

    @Test fun `Nicht alle, solange ein anderer Eintrag des Produkts da ist`() = runBlocking {
        val p = product(name = "Milch", barcode = "400")
        fun e(q: Int) = StockEntryEntity(
            productId = p, locationId = null, level = null, shelf = null, expiryDate = null,
            quantity = q, quantityTypeId = "", note = null,
        )
        val a = e(1).copy(quantityTypeId = typeId())
        val b = e(3).copy(quantityTypeId = typeId(), note = "Keller")
        stockRepo.addEntry(a)
        stockRepo.addEntry(b)
        stockRepo.changeQuantity(a.id, -1)
        assertFalse(events().last().outOfStock)
    }

    @Test fun `Bearbeiten der Anzahl wird als Differenz protokolliert`() = runBlocking {
        val p = product(name = "Bolognese", barcode = null)
        val e = frozenEntry(p, quantity = 2)
        stockRepo.addEntry(e)
        stockRepo.updateEntry(e.copy(quantity = 5))
        stockRepo.updateEntry(e.copy(quantity = 4, note = "scharf"))
        stockRepo.updateEntry(e.copy(quantity = 4, note = "mild"))
        assertEquals(
            listOf(HistoryAction.ADDED to 2, HistoryAction.ADDED to 3, HistoryAction.REMOVED to 1),
            events().map { it.action to it.quantity },
        )
    }

    @Test fun `Nachricht senden - leer wird abgelehnt`() = runBlocking {
        assertFalse(history.sendMessage("   "))
        assertTrue(history.sendMessage("  Milch   ist alle "))
        val ev = events().single()
        assertEquals(HistoryAction.MESSAGE, ev.action)
        assertEquals("Milch ist alle", ev.message)
        assertTrue(ev.notify)
    }

    @Test fun `Alte Verlaufseintraege werden lokal und im Haushalt geloescht`() = runBlocking {
        val now = System.currentTimeMillis()
        val old = HistoryEventEntity(
            createdAt = now - (HistoryLogic.RETENTION_DAYS + 1) * 24 * 3600 * 1000,
            action = HistoryAction.MESSAGE, authorUid = "x", authorName = null, productId = null,
            productName = "", quantity = null, quantityTypeSymbol = null, entryId = null, message = "alt",
        )
        val fresh = old.copy(id = "neu", createdAt = now)
        db.historyDao().upsert(old)
        db.historyDao().upsert(fresh)
        history.prune(now)
        assertEquals(listOf("neu"), events().map { it.id })
        assertTrue(remote.calls.contains(FakeRemoteStore.Call.Delete(RemoteCollection.HISTORY, old.id)))
    }

    @Test fun `Sync uebernimmt Verlauf vom anderen Handy und meldet ihn`() = runBlocking {
        val ev = HistoryEventEntity(
            id = "h1", createdAt = 123, action = HistoryAction.REMOVED, authorUid = "uid-andere",
            authorName = "Melina", productId = "p", productName = "Milch", quantity = 1,
            quantityTypeSymbol = "Stck.", entryId = "e", outOfStock = true, notify = true,
        )
        val applier = SyncApplier(db)
        val applied = applier.apply(RemoteCollection.HISTORY, listOf(RemoteChange.Upsert("h1", RemoteMapper.toMap(ev))))
        assertEquals(listOf(ev), applied.history)
        assertEquals(ev, db.historyDao().getById("h1"))
        applier.apply(RemoteCollection.HISTORY, listOf(RemoteChange.Removed("h1")))
        assertNull(db.historyDao().getById("h1"))
    }

    @Test fun `Fehlerhafte Verlaufsdokumente werden ignoriert`() {
        assertNull(RemoteMapper.history("x", mapOf("action" to "ADDED")))
        assertNull(RemoteMapper.history("x", mapOf("createdAt" to 1L, "action" to "UNBEKANNT")))
        val long = RemoteMapper.history("x", mapOf("createdAt" to 1L, "action" to "MESSAGE", "message" to "a".repeat(500)))!!
        assertEquals(RemoteMapper.MAX_TEXT, long.message!!.length)
    }

    @Test fun `Backup-Import ersetzt den Verlauf im Haushalt nicht`() = runBlocking {
        history.sendMessage("Hallo")
        val id = events().single().id
        writer.replaceAll(db, emptySet())
        assertNotNull(remote.doc(RemoteCollection.HISTORY, id))
    }
}
