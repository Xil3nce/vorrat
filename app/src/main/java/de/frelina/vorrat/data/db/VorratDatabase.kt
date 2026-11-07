package de.frelina.vorrat.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        LocationEntity::class,
        UnitEntity::class,
        QuantityTypeEntity::class,
        ProductEntity::class,
        StockEntryEntity::class,
        LookupCacheEntity::class,
    ],
    version = VorratDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class VorratDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao
    abstract fun unitDao(): UnitDao
    abstract fun quantityTypeDao(): QuantityTypeDao
    abstract fun productDao(): ProductDao
    abstract fun stockEntryDao(): StockEntryDao
    abstract fun lookupCacheDao(): LookupCacheDao

    companion object {
        const val VERSION = 1
        const val NAME = "vorrat.db"

        /**
         * Alle Migrationen. Neue Schema-Versionen: VERSION erhöhen, Migration hier ergänzen,
         * exportiertes Schema (app/schemas) committen und einen MigrationTest schreiben.
         */
        val MIGRATIONS: Array<Migration> = emptyArray()

        /** Startwerte für Einheiten und Menge-Arten beim ersten Anlegen der Datenbank. */
        val SEED_CALLBACK = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                seed(db)
            }
        }

        fun seed(db: SupportSQLiteDatabase) {
            DefaultData.units.forEach { (symbol, name) ->
                db.execSQL("INSERT INTO units (symbol, name) VALUES (?, ?)", arrayOf(symbol, name))
            }
            DefaultData.quantityTypes.forEach { (symbol, name) ->
                db.execSQL(
                    "INSERT INTO quantity_types (symbol, name) VALUES (?, ?)",
                    arrayOf(symbol, name),
                )
            }
        }
    }
}

object DefaultData {
    val units = listOf(
        "g" to "Gramm",
        "kg" to "Kilogramm",
        "ml" to "Milliliter",
        "l" to "Liter",
    )
    val quantityTypes = listOf(
        "Stck." to "Stück",
        "Pck." to "Packungen",
    )
}
