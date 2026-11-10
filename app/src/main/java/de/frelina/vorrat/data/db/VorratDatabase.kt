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
        ShoppingItemEntity::class,
        HistoryEventEntity::class,
        RecipeEntity::class,
        DepartmentEntity::class,
        DepartmentAssignmentEntity::class,
        MealPlanEntity::class,
        StapleEntity::class,
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
    abstract fun shoppingDao(): ShoppingDao
    abstract fun historyDao(): HistoryDao
    abstract fun recipeDao(): RecipeDao
    abstract fun departmentDao(): DepartmentDao
    abstract fun departmentAssignmentDao(): DepartmentAssignmentDao
    abstract fun mealPlanDao(): MealPlanDao
    abstract fun stapleDao(): StapleDao

    companion object {
        const val VERSION = 8
        const val NAME = "vorrat.db"

        /**
         * Alle Migrationen. Neue Schema-Versionen: VERSION erhöhen, Migration hier ergänzen,
         * exportiertes Schema (app/schemas) committen und einen MigrationTest schreiben.
         */
        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)

        /** Startwerte für Einheiten und Menge-Arten beim ersten Anlegen der Datenbank. */
        val SEED_CALLBACK = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                seed(db)
            }
        }

        fun seed(db: SupportSQLiteDatabase) {
            DefaultData.units.forEach { (symbol, name) ->
                db.execSQL("INSERT INTO units (id, symbol, name) VALUES (?, ?, ?)", arrayOf(newId(), symbol, name))
            }
            DefaultData.quantityTypes.forEach { (symbol, name) ->
                db.execSQL(
                    "INSERT INTO quantity_types (id, symbol, name) VALUES (?, ?, ?)",
                    arrayOf(newId(), symbol, name),
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

/**
 * v1 → v2: IDs werden Text (UUIDs für neue Datensätze; bestehende Zahlen-IDs werden zu "1", "2", …),
 * UNIQUE-Indizes auf Name/Kürzel/Barcode entfallen (Eindeutigkeit prüfen die Repositories; Room spiegelt
 * Firestore). Fremdschlüssel bleiben erhalten – die Werte werden identisch umgewandelt.
 *
 * Ablauf: alte Tabellen umbenennen, neue Tabellen anlegen (SQL wie in schemas/…/2.json), Daten kopieren,
 * alte Tabellen (samt Indizes) löschen, Indizes neu anlegen.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val tables = listOf("stock_entries", "products", "locations", "units", "quantity_types")
        tables.forEach { db.execSQL("ALTER TABLE `$it` RENAME TO `${it}_v1`") }

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `locations` (`id` TEXT NOT NULL, `name` TEXT NOT NULL COLLATE NOCASE, " +
                "PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `units` (`id` TEXT NOT NULL, `symbol` TEXT NOT NULL COLLATE NOCASE, " +
                "`name` TEXT NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `quantity_types` (`id` TEXT NOT NULL, `symbol` TEXT NOT NULL COLLATE NOCASE, " +
                "`name` TEXT NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `products` (`id` TEXT NOT NULL, `barcode` TEXT, `name` TEXT NOT NULL, " +
                "`brand` TEXT, `category` TEXT, `packageSize` TEXT, `unitId` TEXT, `imagePath` TEXT, " +
                "`source` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`unitId`) REFERENCES `units`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `stock_entries` (`id` TEXT NOT NULL, `productId` TEXT NOT NULL, " +
                "`locationId` TEXT NOT NULL, `level` INTEGER, `shelf` INTEGER, `expiryDate` INTEGER, " +
                "`quantity` INTEGER NOT NULL, `quantityTypeId` TEXT NOT NULL, `note` TEXT, PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`productId`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`locationId`) REFERENCES `locations`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , " +
                "FOREIGN KEY(`quantityTypeId`) REFERENCES `quantity_types`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
        )

        db.execSQL("INSERT INTO `units` (id, symbol, name) SELECT CAST(id AS TEXT), symbol, name FROM `units_v1`")
        db.execSQL(
            "INSERT INTO `quantity_types` (id, symbol, name) SELECT CAST(id AS TEXT), symbol, name FROM `quantity_types_v1`",
        )
        db.execSQL("INSERT INTO `locations` (id, name) SELECT CAST(id AS TEXT), name FROM `locations_v1`")
        db.execSQL(
            "INSERT INTO `products` (id, barcode, name, brand, category, packageSize, unitId, imagePath, source, " +
                "createdAt, updatedAt) SELECT CAST(id AS TEXT), barcode, name, brand, category, packageSize, " +
                "CAST(unitId AS TEXT), imagePath, source, createdAt, updatedAt FROM `products_v1`",
        )
        db.execSQL(
            "INSERT INTO `stock_entries` (id, productId, locationId, level, shelf, expiryDate, quantity, " +
                "quantityTypeId, note) SELECT CAST(id AS TEXT), CAST(productId AS TEXT), CAST(locationId AS TEXT), " +
                "level, shelf, expiryDate, quantity, CAST(quantityTypeId AS TEXT), note FROM `stock_entries_v1`",
        )

        tables.forEach { db.execSQL("DROP TABLE `${it}_v1`") }

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_locations_name` ON `locations` (`name`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_units_symbol` ON `units` (`symbol`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_quantity_types_symbol` ON `quantity_types` (`symbol`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_barcode` ON `products` (`barcode`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_unitId` ON `products` (`unitId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_category` ON `products` (`category`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_entries_productId` ON `stock_entries` (`productId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_entries_locationId` ON `stock_entries` (`locationId`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_stock_entries_quantityTypeId` ON `stock_entries` (`quantityTypeId`)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_entries_expiryDate` ON `stock_entries` (`expiryDate`)")
    }
}

/** v2 → v3: Orte bekommen optional die Anzahl ihrer Ebenen und Fächer (Auswahllisten im Formular). */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `locations` ADD COLUMN `levels` INTEGER")
        db.execSQL("ALTER TABLE `locations` ADD COLUMN `shelves` INTEGER")
    }
}

/** v3 → v4: OFF-Cache speichert Nährwerte (JSON) und ob sie schon abgefragt wurden. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `lookup_cache` ADD COLUMN `nutrition` TEXT")
        db.execSQL("ALTER TABLE `lookup_cache` ADD COLUMN `nutritionLoaded` INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * v4 → v5:
 * - Bestandseinträge: Ort optional (Tabelle neu anlegen, da SQLite NOT NULL nicht ändern kann), "Geöffnet am"
 * - Produkte: Mindestbestand, gemerkte Haltbarkeit nach dem Öffnen
 * - OFF-Cache: Haltbarkeit nach dem Öffnen laut Lagerhinweis
 * - neue Tabelle für die Einkaufsliste
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `products` ADD COLUMN `minStock` INTEGER")
        db.execSQL("ALTER TABLE `products` ADD COLUMN `openedDays` INTEGER")
        db.execSQL("ALTER TABLE `lookup_cache` ADD COLUMN `openedDays` INTEGER")

        db.execSQL("ALTER TABLE `stock_entries` RENAME TO `stock_entries_old`")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `stock_entries` (`id` TEXT NOT NULL, `productId` TEXT NOT NULL, " +
                "`locationId` TEXT, `level` INTEGER, `shelf` INTEGER, `expiryDate` INTEGER, " +
                "`quantity` INTEGER NOT NULL, `quantityTypeId` TEXT NOT NULL, `note` TEXT, `openedAt` INTEGER, " +
                "`openedDays` INTEGER, PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`productId`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`locationId`) REFERENCES `locations`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , " +
                "FOREIGN KEY(`quantityTypeId`) REFERENCES `quantity_types`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
        )
        db.execSQL(
            "INSERT INTO `stock_entries` (id, productId, locationId, level, shelf, expiryDate, quantity, " +
                "quantityTypeId, note) SELECT id, productId, locationId, level, shelf, expiryDate, quantity, " +
                "quantityTypeId, note FROM `stock_entries_old`",
        )
        db.execSQL("DROP TABLE `stock_entries_old`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_entries_productId` ON `stock_entries` (`productId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_entries_locationId` ON `stock_entries` (`locationId`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_stock_entries_quantityTypeId` ON `stock_entries` (`quantityTypeId`)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_entries_expiryDate` ON `stock_entries` (`expiryDate`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `shopping_items` (`id` TEXT NOT NULL, `productId` TEXT, `name` TEXT NOT NULL, " +
                "`quantity` INTEGER NOT NULL, `note` TEXT, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
    }
}

/**
 * v5 → v6:
 * - Bestandseinträge: "Eingefroren am" (selbst Eingefrorenes) und verknüpfter NFC-Chip
 * - neue Tabelle für den Verlauf im Haushalt
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `stock_entries` ADD COLUMN `frozenAt` INTEGER")
        db.execSQL("ALTER TABLE `stock_entries` ADD COLUMN `nfcTagId` TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_entries_nfcTagId` ON `stock_entries` (`nfcTagId`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `history_events` (`id` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                "`action` TEXT NOT NULL, `authorUid` TEXT, `authorName` TEXT, `productId` TEXT, " +
                "`productName` TEXT NOT NULL, `quantity` INTEGER, `quantityTypeSymbol` TEXT, `entryId` TEXT, " +
                "`outOfStock` INTEGER NOT NULL, `message` TEXT, `notify` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_history_events_createdAt` ON `history_events` (`createdAt`)")
    }
}

/**
 * v6 → v7:
 * - Produkte: manuell gewählte Zutatengruppe für Rezepte
 * - neue Tabelle für eigene Rezepte
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `products` ADD COLUMN `ingredientGroup` TEXT")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `recipes` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `minutes` INTEGER, " +
                "`servings` INTEGER, `ingredients` TEXT NOT NULL, `steps` TEXT NOT NULL, `vegetarian` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
    }
}

/**
 * v7 → v8: neue Tabellen für Abteilungen der Einkaufsliste (samt gemerkten Zuordnungen), den Wochenplan und die
 * "Immer zuhause"-Liste. Die Standard-Abteilungen legt die App an (feste IDs, siehe DepartmentRepository).
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `departments` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`position` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `department_assignments` (`key` TEXT NOT NULL, `departmentId` TEXT NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`key`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `meal_plan` (`id` TEXT NOT NULL, `date` INTEGER NOT NULL, " +
                "`recipeId` TEXT NOT NULL, `persons` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_meal_plan_date` ON `meal_plan` (`date`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `staples` (`key` TEXT NOT NULL, `label` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`key`))",
        )
    }
}
