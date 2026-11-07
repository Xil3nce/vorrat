package de.frelina.vorrat.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.LocalDate

/**
 * Migration 1 → 2: Eine Datenbank wird exakt nach `schemas/…/1.json` angelegt (Tabellen, Indizes,
 * Room-Identität) und befüllt; danach öffnet Room sie mit Version 2. Room führt dabei MIGRATION_1_2 aus und
 * prüft das Ergebnis gegen das erwartete Schema 2 (Tabellen, Spalten, Indizes, Fremdschlüssel) – bei
 * Abweichungen wirft Room "Migration didn't properly handle …".
 *
 * (Der MigrationTestHelper liest die Schemas aus den Test-Assets; die stehen unter Robolectric nicht zur
 * Verfügung, daher dieses gleichwertige Vorgehen.)
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DB)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(DB)
    }

    private fun createV1(fill: SupportSQLiteDatabase.() -> Unit) {
        val schema = JSONObject(File(SCHEMA_DIR, "1.json").readText()).getJSONObject("database")
        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                val entities = schema.getJSONArray("entities")
                for (i in 0 until entities.length()) {
                    val e = entities.getJSONObject(i)
                    val table = e.getString("tableName")
                    db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
                    val indices = e.optJSONArray("indices") ?: continue
                    for (j in 0 until indices.length()) {
                        db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                    }
                }
                val setup = schema.getJSONArray("setupQueries")
                for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(DB).callback(callback).build(),
        )
        helper.writableDatabase.fill()
        helper.close()
    }

    @Test fun `Migration 1 nach 2 wandelt IDs in Text um und erhaelt Beziehungen`() {
        createV1 {
            execSQL("INSERT INTO units (id, symbol, name) VALUES (1, 'g', 'Gramm'), (2, 'kg', 'Kilogramm')")
            execSQL("INSERT INTO quantity_types (id, symbol, name) VALUES (1, 'Stck.', 'Stück')")
            execSQL("INSERT INTO locations (id, name) VALUES (5, 'Keller'), (6, 'Kühlschrank')")
            execSQL(
                "INSERT INTO products (id, barcode, name, brand, category, packageSize, unitId, imagePath, source, " +
                    "createdAt, updatedAt) VALUES " +
                    "(10, '3017624010701', 'Nutella', 'Ferrero', 'en:hazelnut-spreads', '400', 1, 'bild.jpg', " +
                    "'OPEN_FOOD_FACTS', 1, 2), " +
                    "(11, NULL, 'Mehl', NULL, NULL, NULL, NULL, NULL, 'MANUAL', 3, 4)",
            )
            execSQL(
                "INSERT INTO stock_entries (id, productId, locationId, level, shelf, expiryDate, quantity, " +
                    "quantityTypeId, note) VALUES " +
                    "(100, 10, 5, 1, 3, ${LocalDate.of(2026, 12, 24).toEpochDay()}, 2, 1, 'Notiz'), " +
                    "(101, 11, 6, NULL, NULL, NULL, 1, 1, NULL)",
            )
        }

        val db = Room.databaseBuilder(context, VorratDatabase::class.java, DB)
            .addMigrations(*VorratDatabase.MIGRATIONS)
            .addCallback(VorratDatabase.SEED_CALLBACK) // darf bei einer Migration NICHT erneut befüllen
            .allowMainThreadQueries()
            .build()
        try {
            runBlocking {
                assertEquals(listOf("1", "2"), db.unitDao().getAll().map { it.id })
                assertEquals(listOf("1"), db.quantityTypeDao().getAll().map { it.id })
                assertEquals(listOf("Keller", "Kühlschrank"), db.locationDao().getAll().map { it.name })

                val nutella = db.productDao().getById("10")!!
                assertEquals("1", nutella.unitId)
                assertEquals("400".toBigDecimal(), nutella.packageSize)
                assertEquals("bild.jpg", nutella.imagePath)
                assertEquals(ProductSource.OPEN_FOOD_FACTS, nutella.source)
                assertNull(db.productDao().getById("11")!!.unitId)

                val items = db.stockEntryDao().getAllItems()
                assertEquals(2, items.size)
                val item = items.single { it.entryId == "100" }
                assertEquals("10", item.productId)
                assertEquals("5", item.locationId)
                assertEquals("1", item.quantityTypeId)
                assertEquals("Nutella", item.productName)
                assertEquals("Keller", item.locationName)
                assertEquals("Stck.", item.quantityTypeSymbol)
                assertEquals(LocalDate.of(2026, 12, 24), item.expiryDate)
                assertEquals(2, item.quantity)
                assertEquals(1, item.level)
                assertEquals("Notiz", item.note)

                // Fremdschlüssel greifen weiterhin: verwendeter Ort lässt sich nicht löschen …
                assertTrue(runCatching { db.locationDao().deleteById("5") }.isFailure)
                // … und das Löschen eines Produkts entfernt seine Einträge (CASCADE).
                db.productDao().deleteById("11")
                assertEquals(listOf("100"), db.stockEntryDao().getIds())

                // Neue Datensätze bekommen UUIDs.
                val loc = LocationEntity(name = "Dachboden")
                db.locationDao().insert(loc)
                assertEquals(36, loc.id.length)
            }
        } finally {
            db.close()
        }
    }

    private companion object {
        const val DB = "migration-test.db"

        /** Arbeitsverzeichnis der Unit-Tests ist das Modulverzeichnis (app/). */
        val SCHEMA_DIR = File("schemas/de.frelina.vorrat.data.db.VorratDatabase")
    }
}
