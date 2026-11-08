package de.frelina.vorrat.data

import de.frelina.vorrat.data.backup.BackupException
import de.frelina.vorrat.data.backup.BackupManager
import de.frelina.vorrat.data.db.VorratDatabase
import de.frelina.vorrat.data.image.ImageStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class BackupRoundTripTest : DbTestBase() {

    private lateinit var imageStore: ImageStore
    private lateinit var backup: BackupManager

    @Before
    fun setUpBackup() {
        imageStore = ImageStore(File(context.cacheDir, "images-test").apply { deleteRecursively() })
        backup = BackupManager(
            db,
            imageStore,
            "test",
            File(context.cacheDir, "staging").apply { mkdirs() },
            de.frelina.vorrat.data.sync.RemoteWriter(remote, householdStore, imageStore),
        )
    }

    private suspend fun exportBytes(): ByteArray = ByteArrayOutputStream().also { backup.export(it) }.toByteArray()

    private suspend fun snapshot() = listOf(
        db.locationDao().getAll(),
        db.unitDao().getAll(),
        db.quantityTypeDao().getAll(),
        db.productDao().getAll(),
        db.stockEntryDao().getAll(),
        db.shoppingDao().getAll(),
        db.recipeDao().getAll(),
    )

    @Test fun `Export und Import ergeben identische Daten inkl Bild`() = runBlocking {
        val image = imageStore.saveBytes(byteArrayOf(9, 8, 7, 6))
        val loc = location("Keller")
        location("Kühlschrank")
        db.unitDao().insert(de.frelina.vorrat.data.db.UnitEntity(symbol = "Dose", name = "Dose"))
        val p1 = product(imagePath = image)
        val p2 = product(name = "Ohne Barcode", barcode = null, category = null)
        entry(p1, loc, quantity = 3)
        entry(p2, loc, quantity = 1, expiry = null)
        // Neu in 1.4: Mindestbestand, Öffnungsdaten, Eintrag ohne Ort, Einkaufsliste
        db.productDao().update(db.productDao().getById(p2)!!.copy(minStock = 2, openedDays = 4))
        val typeId = db.quantityTypeDao().findBySymbol("Stck.")!!.id
        db.stockEntryDao().insert(
            de.frelina.vorrat.data.db.StockEntryEntity(
                productId = p2, locationId = null, level = null, shelf = null, expiryDate = null, quantity = 1,
                quantityTypeId = typeId, note = null, openedAt = java.time.LocalDate.of(2026, 11, 1), openedDays = 4,
            ),
        )
        db.shoppingDao().upsert(
            de.frelina.vorrat.data.db.ShoppingItemEntity(productId = p1, name = "Nutella", quantity = 2, note = null, createdAt = 7),
        )
        db.shoppingDao().upsert(
            de.frelina.vorrat.data.db.ShoppingItemEntity(productId = null, name = "Brot", quantity = 1, note = null, createdAt = 8),
        )
        db.productDao().update(db.productDao().getById(p1)!!.copy(ingredientGroup = "cream"))
        db.recipeDao().upsert(
            de.frelina.vorrat.data.db.RecipeEntity(
                id = "r1", title = "Suppe", minutes = 30, servings = 2,
                ingredients = "[]", steps = "A B", vegetarian = true, createdAt = 1, updatedAt = 2,
            ),
        )
        val before = snapshot()

        val zip = exportBytes()

        // Daten verändern/löschen und Bild entfernen
        db.stockEntryDao().deleteAll()
        db.productDao().deleteAll()
        db.shoppingDao().deleteAll()
        db.recipeDao().deleteAll()
        imageStore.delete(image)

        val parsed = backup.read(ByteArrayInputStream(zip))
        assertEquals(2, parsed.productCount)
        assertEquals(3, parsed.entryCount)
        backup.replaceAll(parsed)

        assertEquals(before, snapshot())
        assertEquals("cream", db.productDao().getById(p1)!!.ingredientGroup)
        assertEquals("Suppe", db.recipeDao().getById("r1")!!.title)
        assertArrayEquals(byteArrayOf(9, 8, 7, 6), imageStore.fileOrNull(image)!!.readBytes())
    }

    @Test fun `ungueltiges Backup wird abgelehnt und Daten bleiben unveraendert`() = runBlocking {
        val loc = location()
        entry(product(), loc)
        val before = snapshot()

        // Bestandseintrag mit Anzahl 0 und unbekanntem Ort
        val brokenJson = String(unzipJson(exportBytes()))
            .replace("\"quantity\": 2", "\"quantity\": 0")
        try {
            backup.read(ByteArrayInputStream(zipOf(brokenJson)))
            fail("Validierungsfehler erwartet")
        } catch (e: BackupException) {
            assertEquals(BackupException.Reason.INVALID_DATA, e.reason)
        }
        assertEquals(before, snapshot())
    }

    @Test fun `neuere Schema-Version wird abgelehnt`() = runBlocking {
        location()
        val json = String(unzipJson(exportBytes()))
            .replace("\"schemaVersion\": ${VorratDatabase.VERSION}", "\"schemaVersion\": ${VorratDatabase.VERSION + 1}")
        try {
            backup.read(ByteArrayInputStream(zipOf(json)))
            fail("Fehler erwartet")
        } catch (e: BackupException) {
            assertEquals(BackupException.Reason.NEWER_SCHEMA, e.reason)
        }
    }

    @Test fun `Datei ohne backup json ist kein Backup`() = runBlocking {
        val bytes = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { z -> z.putNextEntry(ZipEntry("foo.txt")); z.write(1); z.closeEntry() }
        }.toByteArray()
        try {
            backup.read(ByteArrayInputStream(bytes))
            fail("Fehler erwartet")
        } catch (e: BackupException) {
            assertEquals(BackupException.Reason.NOT_A_BACKUP, e.reason)
        }
    }

    @Test fun `Import scheitert in der Transaktion – bisherige Daten bleiben`() = runBlocking {
        val loc = location()
        entry(product(), loc)
        val zip = exportBytes()
        // Nach der Validierung manipuliert: doppelte Eintrags-ID → Fehler mitten in der Transaktion.
        val parsed = backup.read(ByteArrayInputStream(zip))
        val broken = de.frelina.vorrat.data.backup.ParsedBackup(
            parsed.data.copy(
                stockEntries = parsed.data.stockEntries + parsed.data.stockEntries.first(), // doppelte ID
            ),
            parsed.stagingDir,
        )
        val before = snapshot()
        try {
            backup.replaceAll(broken)
            fail("Fehler erwartet")
        } catch (_: Exception) {
        }
        assertEquals(before, snapshot())
        assertNotNull(db.productDao().getByBarcode("3017624010701"))
    }

    @Test fun `Backup mit Schema 1 (Zahlen-IDs) wird gelesen und ersetzt auch Firestore`() = runBlocking {
        // Vorher: anderer Stand in Room und Firestore
        val oldLoc = location("Garage")
        val old = db.locationDao().getById(oldLoc)!!
        writer.putLocation(old)

        val v1 = """
            {"formatVersion": 1, "schemaVersion": 1, "exportedAt": 1, "appVersion": "1.0.0",
             "locations": [{"id": 1, "name": "Keller"}],
             "units": [{"id": 1, "symbol": "g", "name": "Gramm"}],
             "quantityTypes": [{"id": 1, "symbol": "Stck.", "name": "Stück"}],
             "products": [{"id": 7, "barcode": "111", "name": "Nudeln", "packageSize": "500", "unitId": 1,
                           "source": "MANUAL", "createdAt": 1, "updatedAt": 2}],
             "stockEntries": [{"id": 3, "productId": 7, "locationId": 1, "quantity": 2, "quantityTypeId": 1,
                               "expiryDate": "2027-01-31"}]}
        """.trimIndent()
        val parsed = backup.read(ByteArrayInputStream(zipOf(v1)))
        assertEquals(1, parsed.productCount)
        backup.replaceAll(parsed)

        val item = db.stockEntryDao().getAllItems().single()
        assertEquals("3", item.entryId)
        assertEquals("7", item.productId)
        assertEquals("Keller", item.locationName)
        assertEquals("1", db.productDao().getById("7")!!.unitId)

        // Firestore: alter Ort gelöscht, neuer Stand geschrieben
        assertEquals(null, remote.doc(de.frelina.vorrat.data.sync.RemoteCollection.LOCATIONS, oldLoc))
        assertEquals("Keller", remote.doc(de.frelina.vorrat.data.sync.RemoteCollection.LOCATIONS, "1")!!["name"])
        assertEquals(2L, remote.doc(de.frelina.vorrat.data.sync.RemoteCollection.ENTRIES, "3")!!["quantity"])
        assertEquals("Nudeln", remote.doc(de.frelina.vorrat.data.sync.RemoteCollection.PRODUCTS, "7")!!["name"])
    }

    private fun unzipJson(zip: ByteArray): ByteArray {
        ZipInputStream(ByteArrayInputStream(zip)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (e.name == BackupManager.JSON_NAME) return z.readBytes()
            }
        }
        error("kein JSON")
    }

    private fun zipOf(json: String): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry(BackupManager.JSON_NAME))
            z.write(json.toByteArray())
            z.closeEntry()
        }
    }.toByteArray()
}
