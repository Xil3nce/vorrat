package de.frelina.vorrat.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import de.frelina.vorrat.data.consume.ConsumeDraft
import de.frelina.vorrat.data.consume.ConsumeDraftStore
import de.frelina.vorrat.data.db.StockItem
import de.frelina.vorrat.data.purchase.PurchaseLogic
import de.frelina.vorrat.data.repo.StockRepository
import de.frelina.vorrat.data.settings.SettingsRepository
import de.frelina.vorrat.domain.ConsumeListLogic
import de.frelina.vorrat.ui.consume.ConsumeCommitter
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.LocalDate
import java.util.UUID

/** Entnehmen per Scan: Scan-Liste sammeln (Hinzufügen/Erhöhen/Deckeln/Entfernen) und gesammelt ausbuchen. */
@RunWith(RobolectricTestRunner::class)
class ConsumeListDataTest : DbTestBase() {

    private val file by lazy { File(context.cacheDir, "consume-${UUID.randomUUID()}.json") }
    private val store by lazy { ConsumeDraftStore(file) }
    private val stockRepo get() = StockRepository(db, db.stockEntryDao(), db.productDao(), writer)
    private val committer get() = ConsumeCommitter(store, stockRepo)

    private suspend fun items(productId: String): List<StockItem> = db.stockEntryDao().getItemsForProduct(productId)

    @Test
    fun scan_addsOneFromEarliestExpiry_thenIncreases_thenCapsAndTakesNextEntry() = runBlocking {
        val loc = location()
        val milk = product(name = "Milch")
        val later = entry(milk, loc, quantity = 3, expiry = LocalDate.of(2027, 1, 1))
        val sooner = entry(milk, loc, quantity = 1, expiry = LocalDate.of(2026, 12, 1))

        val first = store.addScan(items(milk))
        assertEquals(sooner, first.entry!!.entryId)
        assertTrue(first.isNew)
        assertEquals(1, first.total)

        // Erster Eintrag (Menge 1) ausgeschöpft → nächster Eintrag nach Ablauf als eigene Zeile.
        val second = store.addScan(items(milk))
        assertEquals(later, second.entry!!.entryId)
        assertTrue(second.isNew)
        assertEquals(2, second.total)

        val third = store.addScan(items(milk))
        assertEquals(later, third.entry!!.entryId)
        assertEquals(false, third.isNew)
        assertEquals(3, third.total)
        assertEquals(listOf(1, 2), store.all().map { it.quantity })
    }

    @Test
    fun scan_whenEverythingReserved_changesNothing() = runBlocking {
        val milk = product(name = "Milch")
        val e = entry(milk, location(), quantity = 1)
        store.addScan(items(milk))
        val again = store.addScan(items(milk))
        assertNull(again.entry)
        assertEquals(listOf(ConsumeDraft(e, milk, 1)), store.all())
    }

    @Test
    fun list_survivesRestart_adjustChangeRemoveRestore() = runBlocking {
        val loc = location()
        val milk = product(name = "Milch")
        val a = entry(milk, loc, quantity = 5, expiry = LocalDate.of(2026, 12, 1))
        val b = entry(milk, loc, quantity = 2, expiry = LocalDate.of(2027, 1, 1))
        store.addScan(items(milk))
        assertEquals(listOf(a), ConsumeDraftStore(file).all().map { it.entryId })

        store.adjust(a, +10, available = 5)
        assertEquals(5, store.all().single().quantity)
        store.adjust(a, -10, available = 5)
        assertEquals(1, store.all().single().quantity)

        store.change(a, b, 2)
        assertEquals(listOf(ConsumeDraft(b, milk, 2)), store.all())

        val removed = store.remove(b)!!
        assertTrue(store.all().isEmpty())
        store.restore(removed, 0)
        assertEquals(listOf(b), store.all().map { it.entryId })
        store.clear()
        assertTrue(store.drafts.first().isEmpty())
    }

    @Test
    fun change_ontoExistingRow_replacesQuantityAndDropsOldRow() {
        val list = listOf(ConsumeDraft("a", "p", 2), ConsumeDraft("b", "p", 1))
        assertEquals(listOf(ConsumeDraft("b", "p", 3)), ConsumeListLogic.change(list, "a", "b", 3))
    }

    @Test
    fun commit_booksOutAllRows_clampsAndSkipsMissing_clearsList() = runBlocking {
        val loc = location()
        val milk = product(name = "Milch")
        val butter = product(name = "Butter", barcode = "4000000000002")
        val rice = product(name = "Reis", barcode = "4000000000003")
        val milkEntry = entry(milk, loc, quantity = 3)
        val butterEntry = entry(butter, loc, quantity = 1)
        val riceEntry = entry(rice, loc, quantity = 2)
        store.restore(ConsumeDraft(milkEntry, milk, 2), 0)
        // Bestand ist inzwischen kleiner als die vorgemerkte Menge → auf den Bestand begrenzen.
        store.restore(ConsumeDraft(butterEntry, butter, 5), 1)
        store.restore(ConsumeDraft(riceEntry, rice, 1), 2)
        // Eintrag verschwindet zwischen Scannen und Ausbuchen → wird übersprungen.
        stockRepo.deleteEntry(riceEntry)

        val result = committer.commitAll()

        assertEquals(2, result.consumed)
        assertEquals(1, result.skipped)
        assertEquals(1, db.stockEntryDao().getById(milkEntry)!!.quantity)
        assertNull(db.stockEntryDao().getById(butterEntry)) // 0 → Eintrag entfernt
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun purchaseLocation_isSavedPerDevice_deletedLocationFallsBackToNone() = runBlocking {
        val prefs = File(context.cacheDir, "settings-${UUID.randomUUID()}.preferences_pb")
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
        try {
            val settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { prefs })
            assertNull(settings.scanLocationId.first())
            settings.setScanLocationId("loc-1")
            assertEquals("loc-1", settings.scanLocationId.first())
            assertEquals("loc-1", PurchaseLogic.resolveLocation(settings.scanLocationId.first(), listOf("loc-1", "loc-2")))
            // Ort gelöscht → kein Ort
            assertNull(PurchaseLogic.resolveLocation(settings.scanLocationId.first(), listOf("loc-2")))
            settings.setScanLocationId(null)
            assertNull(settings.scanLocationId.first())
        } finally {
            scope.cancel()
        }
    }
}
