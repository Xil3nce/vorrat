package de.frelina.vorrat.data

import de.frelina.vorrat.data.FakeRemoteStore.Call
import de.frelina.vorrat.data.db.LocationEntity
import de.frelina.vorrat.data.household.HouseholdRepository
import de.frelina.vorrat.data.household.HouseholdResult
import de.frelina.vorrat.data.repo.CatalogRepository
import de.frelina.vorrat.data.repo.QuantityChange
import de.frelina.vorrat.data.repo.SaveResult
import de.frelina.vorrat.data.repo.StockRepository
import de.frelina.vorrat.data.sync.RemoteCollection
import de.frelina.vorrat.data.sync.RemoteMapper
import de.frelina.vorrat.data.sync.RemoteOp
import de.frelina.vorrat.data.sync.RemoteWriter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Schreibpfad Room → Firestore (Fake): Anzahl ±, Löschen bei 0, Rückgängig, Stammdaten, Batches. */
@RunWith(RobolectricTestRunner::class)
class RemoteWriteTest : DbTestBase() {

    private val stockRepo get() = StockRepository(db, db.stockEntryDao(), db.productDao(), writer)
    private val catalogRepo get() = CatalogRepository(db.locationDao(), db.unitDao(), db.quantityTypeDao(), writer)

    /** Eintrag lokal und (wie nach dem Hochladen) in Firestore anlegen. */
    private suspend fun syncedEntry(quantity: Int): String {
        val id = entry(product(), location(), quantity = quantity)
        writer.putEntry(db.stockEntryDao().getById(id)!!)
        remote.calls.clear()
        return id
    }

    @Test fun `Anzahl erhoehen und verringern nutzt Increment`() = runBlocking {
        val e = syncedEntry(quantity = 2)
        assertEquals(QuantityChange.Updated(5), stockRepo.changeQuantity(e, +3))
        assertEquals(QuantityChange.Updated(4), stockRepo.changeQuantity(e, -1))
        assertEquals(
            listOf(
                Call.Increment(RemoteCollection.ENTRIES, e, "quantity", 3),
                Call.Increment(RemoteCollection.ENTRIES, e, "quantity", -1),
            ),
            remote.calls,
        )
        assertEquals(4L, remote.doc(RemoteCollection.ENTRIES, e)!!["quantity"])
        assertEquals(4, db.stockEntryDao().getById(e)!!.quantity)
    }

    @Test fun `gleichzeitige Aenderungen beider Handys addieren sich`() = runBlocking {
        val e = syncedEntry(quantity = 2)
        // Anderes Handy erhöht um 1 (direkt in Firestore)
        remote.increment(DbTestBase.HOUSEHOLD_ID, RemoteCollection.ENTRIES, e, "quantity", 1)
        stockRepo.changeQuantity(e, +2)
        assertEquals(5L, remote.doc(RemoteCollection.ENTRIES, e)!!["quantity"])
    }

    @Test fun `Anzahl 0 loescht das Dokument, Rueckgaengig legt es neu an`() = runBlocking {
        val e = syncedEntry(quantity = 1)
        val removed = stockRepo.changeQuantity(e, -1)
        assertTrue(removed is QuantityChange.Removed)
        assertEquals(listOf(Call.Delete(RemoteCollection.ENTRIES, e)), remote.calls)
        assertNull(remote.doc(RemoteCollection.ENTRIES, e))
        assertNull(db.stockEntryDao().getById(e))

        stockRepo.restoreEntry((removed as QuantityChange.Removed).removed)
        val doc = remote.doc(RemoteCollection.ENTRIES, e)!!
        assertEquals(1L, doc["quantity"])
        assertEquals(removed.removed.productId, doc["productId"])
        assertEquals(1, db.stockEntryDao().getById(e)!!.quantity)
    }

    @Test fun `Loeschen und Rueckgaengig`() = runBlocking {
        val e = syncedEntry(quantity = 3)
        val removed = stockRepo.deleteEntry(e)!!
        assertNull(remote.doc(RemoteCollection.ENTRIES, e))
        stockRepo.restoreEntry(removed)
        assertEquals(3L, remote.doc(RemoteCollection.ENTRIES, e)!!["quantity"])
    }

    @Test fun `Zusammenfuehren beim Verschieben erhoeht Ziel und loescht Quelle`() = runBlocking {
        val p = product()
        val keller = location("Keller")
        val garage = location("Garage")
        val target = entry(p, garage, quantity = 2)
        val source = entry(p, keller, quantity = 3)
        writer.putEntry(db.stockEntryDao().getById(target)!!)
        writer.putEntry(db.stockEntryDao().getById(source)!!)

        // Verschieben in die Garage: gleiche Daten wie dort vorhandener Eintrag.
        val moved = db.stockEntryDao().getById(source)!!.copy(locationId = garage)
        assertEquals(target, stockRepo.findTwin(moved)?.id)
        assertTrue(stockRepo.mergeInto(target, moved))

        assertEquals(5, db.stockEntryDao().getById(target)!!.quantity)
        assertNull(db.stockEntryDao().getById(source))
        assertEquals(5L, remote.doc(RemoteCollection.ENTRIES, target)!!["quantity"])
        assertNull(remote.doc(RemoteCollection.ENTRIES, source))
    }

    @Test fun `Zusammenfuehren eines neuen Eintrags loescht kein Dokument`() = runBlocking {
        val p = product()
        val keller = location()
        val target = entry(p, keller, quantity = 1)
        writer.putEntry(db.stockEntryDao().getById(target)!!)
        remote.calls.clear()

        val fresh = db.stockEntryDao().getById(target)!!.copy(id = "neu", quantity = 4)
        assertEquals(target, stockRepo.findTwin(fresh)?.id)
        assertTrue(stockRepo.mergeInto(target, fresh))
        assertEquals(5, db.stockEntryDao().getById(target)!!.quantity)
        assertEquals(listOf(Call.Increment(RemoteCollection.ENTRIES, target, "quantity", 4)), remote.calls)

        // Ziel inzwischen weg → false, nichts geschrieben.
        stockRepo.deleteEntry(target)
        remote.calls.clear()
        assertTrue(!stockRepo.mergeInto(target, fresh))
        assertTrue(remote.calls.isEmpty())
    }

    @Test fun `Ebenen und Faecher eines Orts werden synchronisiert`() = runBlocking {
        val saved = catalogRepo.addLocation("Regal", levels = 4, shelves = 0) as SaveResult.Saved
        val doc = remote.doc(RemoteCollection.LOCATIONS, saved.id)!!
        assertEquals(4L, doc["levels"])
        assertNull(doc["shelves"])
        catalogRepo.updateLocation(db.locationDao().getById(saved.id)!!, "Regal", 4, 999)
        assertEquals(LocationEntity.MAX_SLOTS, db.locationDao().getById(saved.id)!!.shelves)
        val back = RemoteMapper.location(saved.id, remote.doc(RemoteCollection.LOCATIONS, saved.id)!!)!!
        assertEquals(4, back.levels)
        assertEquals(LocationEntity.MAX_SLOTS, back.shelves)
        // Umbenennen behält die Angaben.
        catalogRepo.renameLocation(back, "Hochregal")
        assertEquals(4, db.locationDao().getById(saved.id)!!.levels)
    }

    @Test fun `Stammdaten werden geschrieben und geloescht`() = runBlocking {
        val saved = catalogRepo.addLocation("Keller") as SaveResult.Saved
        assertEquals("Keller", remote.doc(RemoteCollection.LOCATIONS, saved.id)!!["name"])
        catalogRepo.renameLocation(db.locationDao().getById(saved.id)!!, "Vorratskeller")
        assertEquals("Vorratskeller", remote.doc(RemoteCollection.LOCATIONS, saved.id)!!["name"])
        catalogRepo.deleteLocation(db.locationDao().getById(saved.id)!!)
        assertNull(remote.doc(RemoteCollection.LOCATIONS, saved.id))

        val unit = catalogRepo.saveUnit(null, "Dose", "Dose") as SaveResult.Saved
        assertEquals("Dose", remote.doc(RemoteCollection.UNITS, unit.id)!!["symbol"])
    }

    @Test fun `ohne Haushalt wird nichts an Firestore geschrieben`() = runBlocking {
        householdStoreWithout()
        val e = entry(product(), location(), quantity = 2)
        stockRepo.changeQuantity(e, 1)
        catalogRepo.addLocation("Garage")
        assertTrue(remote.calls.isEmpty())
    }

    @Test fun `Haushalt anlegen laedt lokale Daten in Batches hoch`() = runBlocking {
        householdStoreWithout()
        val loc = location()
        val p = product()
        repeat(3) { entry(p, loc) }
        val repo = HouseholdRepository(remote, householdStore, db, writer)
        assertEquals(HouseholdResult.Success, repo.create())
        val h = householdStore.current()!!
        assertEquals("hh-1", h.id)
        assertEquals(10, h.code.length)
        // 4 Einheiten + 2 Menge-Arten + 1 Ort + 1 Produkt + 3 Einträge
        assertEquals(11, remote.calls.filterIsInstance<Call.Commit>().sumOf { it.ops.size })
        assertEquals(3, remote.docs[RemoteCollection.ENTRIES]!!.size)
    }

    @Test fun `Beitreten ersetzt lokale Daten, falscher Code wird abgelehnt`() = runBlocking {
        householdStoreWithout()
        entry(product(), location())
        remote.invites["ABCDE23456"] = "hh-fremd"
        val repo = HouseholdRepository(remote, householdStore, db, writer)

        assertEquals(HouseholdResult.InvalidCode, repo.join("ABC"))
        assertEquals(HouseholdResult.UnknownCode, repo.join("ZZZZZ-ZZZZZ"))
        assertNotNull(db.productDao().getByBarcode("3017624010701")) // unverändert

        assertEquals(HouseholdResult.Success, repo.join("abcde-23456"))
        assertEquals("hh-fremd", householdStore.current()!!.id)
        assertTrue(db.productDao().getAll().isEmpty())
        assertTrue(db.unitDao().getAll().isEmpty())
        assertTrue("uid-test" in remote.members["hh-fremd"]!!)
    }

    @Test fun `Batches haben hoechstens 500 Operationen und begrenzte Groesse`() {
        val ops = List(1201) { RemoteOp.Delete(RemoteCollection.ENTRIES, "id$it") }
        assertEquals(listOf(500, 500, 201), RemoteWriter.chunk(ops).map { it.size })

        val big = "x".repeat(300_000)
        val images = List(70) { RemoteOp.Set(RemoteCollection.IMAGES, "img$it.jpg", mapOf("data" to big)) }
        val chunks = RemoteWriter.chunk(images)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { c -> c.sumOf { ((it as RemoteOp.Set).data["data"] as String).length } < 9_000_000 })
        assertEquals(70, chunks.sumOf { it.size })
    }

    /** Für Tests ohne Haushalt: neuer, leerer DataStore. */
    private fun householdStoreWithout() {
        householdStore = de.frelina.vorrat.data.household.HouseholdStore(
            androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
                scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO),
            ) { java.io.File(context.cacheDir, "prefs-leer-${java.util.UUID.randomUUID()}.preferences_pb") },
        )
        writer = RemoteWriter(remote, householdStore, images)
    }
}
