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

    private fun createV1(fill: SupportSQLiteDatabase.() -> Unit) = createAt(1, fill)

    /** Legt die Datenbank exakt nach `schemas/…/<version>.json` an. */
    private fun createAt(version: Int, fill: SupportSQLiteDatabase.() -> Unit) {
        val schema = JSONObject(File(SCHEMA_DIR, "$version.json").readText()).getJSONObject("database")
        val callback = object : SupportSQLiteOpenHelper.Callback(version) {
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

    @Test fun `Migration 3 nach 4 ergaenzt Naehrwert-Spalten im OFF-Cache`() {
        createAt(3) {
            execSQL(
                "INSERT INTO lookup_cache (barcode, found, name, brand, category, packageSize, unitSymbol, " +
                    "imagePath, fetchedAt) VALUES ('3017624010701', 1, 'Nutella', NULL, NULL, '400', 'g', 'b.jpg', 5)",
            )
        }
        val db = Room.databaseBuilder(context, VorratDatabase::class.java, DB)
            .addMigrations(*VorratDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            runBlocking {
                val row = db.lookupCacheDao().get("3017624010701")!!
                assertEquals("Nutella", row.name)
                assertEquals("b.jpg", row.imagePath)
                assertNull(row.nutrition)
                assertEquals(false, row.nutritionLoaded)
            }
        } finally {
            db.close()
        }
    }

    @Test fun `Migration 4 nach 5 erhaelt Bestand und ergaenzt Einkaufsliste und Oeffnungsdaten`() {
        createAt(4) {
            execSQL("INSERT INTO locations (id, name, levels, shelves) VALUES ('l1', 'Keller', 2, NULL)")
            execSQL("INSERT INTO quantity_types (id, symbol, name) VALUES ('q1', 'Stck.', 'Stück')")
            execSQL(
                "INSERT INTO products (id, barcode, name, brand, category, packageSize, unitId, imagePath, source, " +
                    "createdAt, updatedAt) VALUES ('p1', '123', 'Nudeln', NULL, NULL, NULL, NULL, NULL, 'MANUAL', 1, 2)",
            )
            execSQL(
                "INSERT INTO stock_entries (id, productId, locationId, level, shelf, expiryDate, quantity, " +
                    "quantityTypeId, note) VALUES ('e1', 'p1', 'l1', 1, NULL, ${LocalDate.of(2027, 1, 2).toEpochDay()}, 3, 'q1', 'n')",
            )
        }
        val db = Room.databaseBuilder(context, VorratDatabase::class.java, DB)
            .addMigrations(*VorratDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            runBlocking {
                val e = db.stockEntryDao().getById("e1")!!
                assertEquals("l1", e.locationId)
                assertEquals(3, e.quantity)
                assertEquals(LocalDate.of(2027, 1, 2), e.expiryDate)
                assertNull(e.openedAt)
                assertNull(e.openedDays)
                val p = db.productDao().getById("p1")!!
                assertNull(p.minStock)
                assertNull(p.openedDays)
                assertEquals("Keller", db.stockEntryDao().getAllItems().single().locationName)
                assertTrue(db.shoppingDao().getAll().isEmpty())
                // Ort ist jetzt optional
                db.stockEntryDao().insert(e.copy(id = "e2", locationId = null))
                assertNull(db.stockEntryDao().getById("e2")!!.locationId)
            }
        } finally {
            db.close()
        }
    }

    @Test fun `Migration 5 nach 6 ergaenzt Einfrierdatum, NFC-Chip und Verlauf`() {
        createAt(5) {
            execSQL("INSERT INTO quantity_types (id, symbol, name) VALUES ('q1', 'Stck.', 'Stück')")
            execSQL(
                "INSERT INTO products (id, barcode, name, brand, category, packageSize, unitId, imagePath, source, " +
                    "createdAt, updatedAt, minStock, openedDays) VALUES " +
                    "('p1', NULL, 'Bolognese', NULL, NULL, NULL, NULL, NULL, 'MANUAL', 1, 2, NULL, NULL)",
            )
            execSQL(
                "INSERT INTO stock_entries (id, productId, locationId, level, shelf, expiryDate, quantity, " +
                    "quantityTypeId, note, openedAt, openedDays) VALUES ('e1', 'p1', NULL, 2, 1, NULL, 3, 'q1', NULL, NULL, NULL)",
            )
        }
        val db = Room.databaseBuilder(context, VorratDatabase::class.java, DB)
            .addMigrations(*VorratDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            runBlocking {
                val e = db.stockEntryDao().getById("e1")!!
                assertEquals(3, e.quantity)
                assertNull(e.frozenAt)
                assertNull(e.nfcTagId)
                db.stockEntryDao().update(e.copy(frozenAt = LocalDate.of(2026, 11, 1), nfcTagId = "AB"))
                assertEquals(listOf("e1"), db.stockEntryDao().getByNfcTag("AB").map { it.id })
                assertEquals(LocalDate.of(2026, 11, 1), db.stockEntryDao().getAllItems().single().frozenAt)
                assertTrue(db.historyDao().getAll().isEmpty())
            }
        } finally {
            db.close()
        }
    }

    @Test fun `Migration 6 nach 7 ergaenzt Zutatengruppe und Rezepte`() {
        createAt(6) {
            execSQL(
                "INSERT INTO products (id, barcode, name, brand, category, packageSize, unitId, imagePath, source, " +
                    "createdAt, updatedAt, minStock, openedDays) VALUES " +
                    "('p1', NULL, 'Sahne', NULL, NULL, NULL, NULL, NULL, 'MANUAL', 1, 2, NULL, NULL)",
            )
        }
        val db = Room.databaseBuilder(context, VorratDatabase::class.java, DB)
            .addMigrations(*VorratDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            runBlocking {
                val p = db.productDao().getById("p1")!!
                assertEquals("Sahne", p.name)
                assertNull(p.ingredientGroup)
                db.productDao().update(p.copy(ingredientGroup = "cream"))
                assertEquals("cream", db.productDao().getById("p1")!!.ingredientGroup)
                assertTrue(db.recipeDao().getAll().isEmpty())
                db.recipeDao().upsert(
                    RecipeEntity(id = "r1", title = "Suppe", ingredients = "[]", steps = "Kochen", createdAt = 1, updatedAt = 2),
                )
                assertEquals(listOf("Suppe"), db.recipeDao().getAll().map { it.title })
            }
        } finally {
            db.close()
        }
    }

    @Test fun `Migration 7 nach 8 ergaenzt Abteilungen, Zuordnungen, Wochenplan und Immer zuhause`() {
        createAt(7) {
            execSQL(
                "INSERT INTO products (id, barcode, name, brand, category, packageSize, unitId, imagePath, source, " +
                    "createdAt, updatedAt, minStock, openedDays, ingredientGroup) VALUES " +
                    "('p1', NULL, 'Sahne', NULL, NULL, NULL, NULL, NULL, 'MANUAL', 1, 2, NULL, NULL, 'cream')",
            )
            execSQL(
                "INSERT INTO recipes (id, title, minutes, servings, ingredients, steps, vegetarian, createdAt, updatedAt) " +
                    "VALUES ('r1', 'Suppe', NULL, 2, '[]', 'Kochen', 0, 1, 2)",
            )
        }
        val db = Room.databaseBuilder(context, VorratDatabase::class.java, DB)
            .addMigrations(*VorratDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            runBlocking {
                // bestehende Daten bleiben
                assertEquals("Sahne", db.productDao().getById("p1")!!.name)
                assertEquals("Suppe", db.recipeDao().getById("r1")!!.title)
                // neue Tabellen sind da und leer (Standard-Abteilungen legt die App idempotent selbst an)
                assertTrue(db.departmentDao().getAll().isEmpty())
                assertTrue(db.departmentAssignmentDao().getAll().isEmpty())
                assertTrue(db.mealPlanDao().getAll().isEmpty())
                assertTrue(db.stapleDao().getAll().isEmpty())

                db.departmentDao().upsert(DepartmentEntity("dept_x", "Bio", 0))
                db.departmentAssignmentDao().upsert(DepartmentAssignmentEntity("product:p1", "dept_x", 5))
                val day = LocalDate.of(2026, 11, 12)
                db.mealPlanDao().upsert(MealPlanEntity("m1", day, "r1", 3, 6))
                db.stapleDao().upsert(StapleEntity("salz", "Salz", 7))
                assertEquals(listOf("Bio"), db.departmentDao().getAll().map { it.name })
                assertEquals("dept_x", db.departmentAssignmentDao().getByKey("product:p1")!!.departmentId)
                assertEquals(day, db.mealPlanDao().getBetween(day, day).single().date)
                assertEquals(listOf("Salz"), db.stapleDao().getAll().map { it.label })
            }
        } finally {
            db.close()
        }
    }

    @Test fun `Migration 8 nach 9 ergaenzt Packungsgroesse in der Einkaufsliste`() {
        createAt(8) {
            execSQL(
                "INSERT INTO shopping_items (id, productId, name, quantity, note, createdAt) " +
                    "VALUES ('s1', NULL, 'Mehl', 2, NULL, 3)",
            )
        }
        val db = Room.databaseBuilder(context, VorratDatabase::class.java, DB)
            .addMigrations(*VorratDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            runBlocking {
                val item = db.shoppingDao().getById("s1")!!
                assertEquals("Mehl", item.name)
                assertEquals(2, item.quantity)
                assertNull(item.packageSize)
                assertNull(item.unitSymbol)
                db.shoppingDao().upsert(item.copy(packageSize = java.math.BigDecimal("1.5"), unitSymbol = "kg"))
                val saved = db.shoppingDao().getById("s1")!!
                assertEquals(java.math.BigDecimal("1.5"), saved.packageSize)
                assertEquals("kg", saved.unitSymbol)
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
