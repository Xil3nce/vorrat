package de.example.vorrat.data

import de.example.vorrat.data.backup.BackupException
import de.example.vorrat.data.backup.BackupManager
import de.example.vorrat.data.db.VorratDatabase
import de.example.vorrat.data.image.ImageStore
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
        backup = BackupManager(db, imageStore, "test", File(context.cacheDir, "staging").apply { mkdirs() })
    }

    private suspend fun exportBytes(): ByteArray = ByteArrayOutputStream().also { backup.export(it) }.toByteArray()

    private suspend fun snapshot() = listOf(
        db.locationDao().getAll(),
        db.unitDao().getAll(),
        db.quantityTypeDao().getAll(),
        db.productDao().getAll(),
        db.stockEntryDao().getAll(),
    )

    @Test fun `Export und Import ergeben identische Daten inkl Bild`() = runBlocking {
        val image = imageStore.saveBytes(byteArrayOf(9, 8, 7, 6))
        val loc = location("Keller")
        location("Kühlschrank")
        db.unitDao().insert(de.example.vorrat.data.db.UnitEntity(symbol = "Dose", name = "Dose"))
        val p1 = product(imagePath = image)
        val p2 = product(name = "Ohne Barcode", barcode = null, category = null)
        entry(p1, loc, quantity = 3)
        entry(p2, loc, quantity = 1, expiry = null)
        val before = snapshot()

        val zip = exportBytes()

        // Daten verändern/löschen und Bild entfernen
        db.stockEntryDao().deleteAll()
        db.productDao().deleteAll()
        imageStore.delete(image)

        val parsed = backup.read(ByteArrayInputStream(zip))
        assertEquals(2, parsed.productCount)
        assertEquals(2, parsed.entryCount)
        backup.replaceAll(parsed)

        assertEquals(before, snapshot())
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
        val broken = de.example.vorrat.data.backup.ParsedBackup(
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
