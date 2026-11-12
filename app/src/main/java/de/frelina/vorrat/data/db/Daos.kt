package de.frelina.vorrat.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface LocationDao {
    @Query("SELECT * FROM locations ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<LocationEntity>>

    @Query("SELECT * FROM locations ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<LocationEntity>

    @Query("SELECT * FROM locations WHERE id = :id")
    suspend fun getById(id: String): LocationEntity?

    @Query("SELECT * FROM locations WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): LocationEntity?

    @Insert
    suspend fun insert(location: LocationEntity)

    @Insert
    suspend fun insertAll(locations: List<LocationEntity>)

    @Upsert
    suspend fun upsert(location: LocationEntity)

    @Update
    suspend fun update(location: LocationEntity)

    @Delete
    suspend fun delete(location: LocationEntity)

    @Query("DELETE FROM locations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT id FROM locations")
    suspend fun getIds(): List<String>

    @Query("SELECT COUNT(*) FROM stock_entries WHERE locationId = :locationId")
    suspend fun countStockEntries(locationId: String): Int

    @Query("DELETE FROM locations")
    suspend fun deleteAll()
}

@Dao
interface UnitDao {
    @Query("SELECT * FROM units ORDER BY rowid")
    fun observeAll(): Flow<List<UnitEntity>>

    @Query("SELECT * FROM units ORDER BY rowid")
    suspend fun getAll(): List<UnitEntity>

    @Query("SELECT * FROM units WHERE id = :id")
    suspend fun getById(id: String): UnitEntity?

    @Query("SELECT * FROM units WHERE symbol = :symbol COLLATE NOCASE LIMIT 1")
    suspend fun findBySymbol(symbol: String): UnitEntity?

    @Insert
    suspend fun insert(unit: UnitEntity)

    @Insert
    suspend fun insertAll(units: List<UnitEntity>)

    @Upsert
    suspend fun upsert(unit: UnitEntity)

    @Update
    suspend fun update(unit: UnitEntity)

    @Delete
    suspend fun delete(unit: UnitEntity)

    @Query("DELETE FROM units WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT id FROM units")
    suspend fun getIds(): List<String>

    @Query("SELECT COUNT(*) FROM products WHERE unitId = :unitId")
    suspend fun countProducts(unitId: String): Int

    @Query("DELETE FROM units")
    suspend fun deleteAll()
}

@Dao
interface QuantityTypeDao {
    @Query("SELECT * FROM quantity_types ORDER BY rowid")
    fun observeAll(): Flow<List<QuantityTypeEntity>>

    @Query("SELECT * FROM quantity_types ORDER BY rowid")
    suspend fun getAll(): List<QuantityTypeEntity>

    @Query("SELECT * FROM quantity_types WHERE id = :id")
    suspend fun getById(id: String): QuantityTypeEntity?

    @Query("SELECT * FROM quantity_types WHERE symbol = :symbol COLLATE NOCASE LIMIT 1")
    suspend fun findBySymbol(symbol: String): QuantityTypeEntity?

    @Insert
    suspend fun insert(type: QuantityTypeEntity)

    @Insert
    suspend fun insertAll(types: List<QuantityTypeEntity>)

    @Upsert
    suspend fun upsert(type: QuantityTypeEntity)

    @Update
    suspend fun update(type: QuantityTypeEntity)

    @Delete
    suspend fun delete(type: QuantityTypeEntity)

    @Query("DELETE FROM quantity_types WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT id FROM quantity_types")
    suspend fun getIds(): List<String>

    @Query("SELECT COUNT(*) FROM stock_entries WHERE quantityTypeId = :typeId")
    suspend fun countStockEntries(typeId: String): Int

    @Query("DELETE FROM quantity_types")
    suspend fun deleteAll()
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getById(id: String): ProductEntity?

    @Query("SELECT * FROM products WHERE id = :id")
    fun observeById(id: String): Flow<ProductEntity?>

    @Query("SELECT * FROM products WHERE barcode = :barcode ORDER BY createdAt LIMIT 1")
    suspend fun getByBarcode(barcode: String): ProductEntity?

    @Query("SELECT * FROM products ORDER BY createdAt, id")
    suspend fun getAll(): List<ProductEntity>

    @Query("SELECT * FROM products ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ProductEntity>>

    /** Produkt ohne Barcode mit diesem Namen (selbst Eingefrorenes wiederverwenden). */
    @Query("SELECT * FROM products WHERE barcode IS NULL AND name = :name COLLATE NOCASE ORDER BY createdAt LIMIT 1")
    suspend fun findWithoutBarcodeByName(name: String): ProductEntity?

    @Query(
        "SELECT DISTINCT category FROM products WHERE category IS NOT NULL AND category != '' " +
            "ORDER BY category COLLATE NOCASE",
    )
    fun observeCategories(): Flow<List<String>>

    @Query("SELECT imagePath FROM products WHERE imagePath IS NOT NULL")
    suspend fun getAllImagePaths(): List<String>

    @Query("SELECT COUNT(*) FROM products WHERE imagePath = :imagePath")
    suspend fun countWithImage(imagePath: String): Int

    @Insert
    suspend fun insert(product: ProductEntity)

    @Insert
    suspend fun insertAll(products: List<ProductEntity>)

    @Upsert
    suspend fun upsert(product: ProductEntity)

    @Update
    suspend fun update(product: ProductEntity)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT id FROM products")
    suspend fun getIds(): List<String>

    @Query("DELETE FROM products")
    suspend fun deleteAll()
}

@Dao
interface StockEntryDao {
    @Query(STOCK_ITEM_SELECT)
    fun observeAllItems(): Flow<List<StockItem>>

    @Query("$STOCK_ITEM_SELECT WHERE e.id = :entryId")
    fun observeItem(entryId: String): Flow<StockItem?>

    @Query("$STOCK_ITEM_SELECT WHERE e.productId = :productId")
    suspend fun getItemsForProduct(productId: String): List<StockItem>

    @Query(STOCK_ITEM_SELECT)
    suspend fun getAllItems(): List<StockItem>

    @Query("SELECT * FROM stock_entries WHERE id = :id")
    suspend fun getById(id: String): StockEntryEntity?

    @Query("SELECT * FROM stock_entries ORDER BY id")
    suspend fun getAll(): List<StockEntryEntity>

    @Query("SELECT * FROM stock_entries WHERE productId = :productId ORDER BY id")
    suspend fun getEntriesForProduct(productId: String): List<StockEntryEntity>

    @Insert
    suspend fun insert(entry: StockEntryEntity)

    @Insert
    suspend fun insertAll(entries: List<StockEntryEntity>)

    @Upsert
    suspend fun upsert(entry: StockEntryEntity)

    @Update
    suspend fun update(entry: StockEntryEntity)

    @Query("DELETE FROM stock_entries WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT id FROM stock_entries")
    suspend fun getIds(): List<String>

    @Query("DELETE FROM stock_entries")
    suspend fun deleteAll()

    @Query("SELECT * FROM stock_entries WHERE nfcTagId = :tagId ORDER BY id")
    suspend fun getByNfcTag(tagId: String): List<StockEntryEntity>

    @Query("SELECT COALESCE(SUM(quantity), 0) FROM stock_entries WHERE productId = :productId")
    suspend fun totalQuantity(productId: String): Int

    companion object {
        const val STOCK_ITEM_SELECT = """
            SELECT e.id AS entryId, e.productId, e.locationId, e.level, e.shelf, e.expiryDate,
                   e.quantity, e.quantityTypeId, e.note, e.openedAt, e.openedDays, e.frozenAt, e.nfcTagId,
                   p.name AS productName, p.brand, p.barcode, p.imagePath, p.category, p.ingredientGroup,
                   l.name AS locationName, q.symbol AS quantityTypeSymbol
            FROM stock_entries e
            JOIN products p ON p.id = e.productId
            LEFT JOIN locations l ON l.id = e.locationId
            JOIN quantity_types q ON q.id = e.quantityTypeId
        """
    }
}

@Dao
interface LookupCacheDao {
    @Query("SELECT * FROM lookup_cache WHERE barcode = :barcode")
    suspend fun get(barcode: String): LookupCacheEntity?

    @Upsert
    suspend fun upsert(entry: LookupCacheEntity)

    @Query("SELECT imagePath FROM lookup_cache WHERE imagePath IS NOT NULL")
    suspend fun getAllImagePaths(): List<String>

    @Query("DELETE FROM lookup_cache")
    suspend fun deleteAll()
}

@Dao
interface ShoppingDao {
    @Query("SELECT * FROM shopping_items ORDER BY createdAt, id")
    fun observeAll(): Flow<List<ShoppingItemEntity>>

    @Query("SELECT * FROM shopping_items ORDER BY createdAt, id")
    suspend fun getAll(): List<ShoppingItemEntity>

    @Query("SELECT * FROM shopping_items WHERE id = :id")
    suspend fun getById(id: String): ShoppingItemEntity?

    @Query("SELECT * FROM shopping_items WHERE productId = :productId")
    suspend fun getForProduct(productId: String): List<ShoppingItemEntity>

    @Upsert
    suspend fun upsert(item: ShoppingItemEntity)

    @Insert
    suspend fun insertAll(items: List<ShoppingItemEntity>)

    @Query("DELETE FROM shopping_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT id FROM shopping_items")
    suspend fun getIds(): List<String>

    @Query("DELETE FROM shopping_items")
    suspend fun deleteAll()
}

/** Wer wann etwas im Verlauf eingetragen hat (für die Mitgliederliste des Haushalts). */
data class AuthorActivity(val authorUid: String, val authorName: String?, val createdAt: Long)

@Dao
interface HistoryDao {
    @Query("SELECT authorUid, authorName, createdAt FROM history_events WHERE authorUid IS NOT NULL")
    fun observeAuthorActivity(): Flow<List<AuthorActivity>>

    @Query("SELECT * FROM history_events ORDER BY createdAt DESC, id LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<HistoryEventEntity>>

    @Query("SELECT * FROM history_events WHERE id = :id")
    suspend fun getById(id: String): HistoryEventEntity?

    @Query("SELECT * FROM history_events ORDER BY createdAt, id")
    suspend fun getAll(): List<HistoryEventEntity>

    @Upsert
    suspend fun upsert(event: HistoryEventEntity)

    @Query("DELETE FROM history_events WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT id FROM history_events")
    suspend fun getIds(): List<String>

    @Query("SELECT id FROM history_events WHERE createdAt < :before")
    suspend fun getIdsOlderThan(before: Long): List<String>

    @Query("DELETE FROM history_events WHERE createdAt < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM history_events")
    suspend fun deleteAll()
}

@Dao
interface RecipeDao {
    @Query("SELECT * FROM recipes ORDER BY title COLLATE NOCASE, id")
    fun observeAll(): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipes ORDER BY title COLLATE NOCASE, id")
    suspend fun getAll(): List<RecipeEntity>

    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun getById(id: String): RecipeEntity?

    @Query("SELECT * FROM recipes WHERE id = :id")
    fun observeById(id: String): Flow<RecipeEntity?>

    @Upsert
    suspend fun upsert(recipe: RecipeEntity)

    @Insert
    suspend fun insertAll(recipes: List<RecipeEntity>)

    @Query("DELETE FROM recipes WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT id FROM recipes")
    suspend fun getIds(): List<String>

    @Query("SELECT imagePath FROM recipes WHERE imagePath IS NOT NULL")
    suspend fun getAllImagePaths(): List<String>

    @Query("DELETE FROM recipes")
    suspend fun deleteAll()
}

@Dao
interface DepartmentDao {
    @Query("SELECT * FROM departments ORDER BY position, name COLLATE NOCASE, id")
    fun observeAll(): Flow<List<DepartmentEntity>>

    @Query("SELECT * FROM departments ORDER BY position, name COLLATE NOCASE, id")
    suspend fun getAll(): List<DepartmentEntity>

    @Query("SELECT * FROM departments WHERE id = :id")
    suspend fun getById(id: String): DepartmentEntity?

    @Query("SELECT COUNT(*) FROM departments")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(department: DepartmentEntity)

    @Upsert
    suspend fun upsertAll(departments: List<DepartmentEntity>)

    @Insert
    suspend fun insertAll(departments: List<DepartmentEntity>)

    @Query("DELETE FROM departments WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT id FROM departments")
    suspend fun getIds(): List<String>

    @Query("DELETE FROM departments")
    suspend fun deleteAll()
}

@Dao
interface DepartmentAssignmentDao {
    @Query("SELECT * FROM department_assignments")
    fun observeAll(): Flow<List<DepartmentAssignmentEntity>>

    @Query("SELECT * FROM department_assignments ORDER BY `key`")
    suspend fun getAll(): List<DepartmentAssignmentEntity>

    @Query("SELECT * FROM department_assignments WHERE `key` = :key")
    suspend fun getByKey(key: String): DepartmentAssignmentEntity?

    @Query("SELECT * FROM department_assignments WHERE departmentId = :departmentId")
    suspend fun getForDepartment(departmentId: String): List<DepartmentAssignmentEntity>

    @Upsert
    suspend fun upsert(assignment: DepartmentAssignmentEntity)

    @Insert
    suspend fun insertAll(assignments: List<DepartmentAssignmentEntity>)

    @Query("DELETE FROM department_assignments WHERE `key` = :key")
    suspend fun deleteByKey(key: String)

    @Query("SELECT `key` FROM department_assignments")
    suspend fun getIds(): List<String>

    @Query("DELETE FROM department_assignments")
    suspend fun deleteAll()
}

@Dao
interface MealPlanDao {
    @Query("SELECT * FROM meal_plan WHERE date BETWEEN :from AND :to ORDER BY date, createdAt, id")
    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<MealPlanEntity>>

    @Query("SELECT * FROM meal_plan WHERE date BETWEEN :from AND :to ORDER BY date, createdAt, id")
    suspend fun getBetween(from: LocalDate, to: LocalDate): List<MealPlanEntity>

    @Query("SELECT * FROM meal_plan ORDER BY date, createdAt, id")
    suspend fun getAll(): List<MealPlanEntity>

    @Query("SELECT * FROM meal_plan WHERE id = :id")
    suspend fun getById(id: String): MealPlanEntity?

    @Upsert
    suspend fun upsert(entry: MealPlanEntity)

    @Insert
    suspend fun insertAll(entries: List<MealPlanEntity>)

    @Query("DELETE FROM meal_plan WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT id FROM meal_plan")
    suspend fun getIds(): List<String>

    @Query("DELETE FROM meal_plan")
    suspend fun deleteAll()
}

@Dao
interface StapleDao {
    @Query("SELECT * FROM staples ORDER BY label COLLATE NOCASE, `key`")
    fun observeAll(): Flow<List<StapleEntity>>

    @Query("SELECT * FROM staples ORDER BY label COLLATE NOCASE, `key`")
    suspend fun getAll(): List<StapleEntity>

    @Upsert
    suspend fun upsert(staple: StapleEntity)

    @Insert
    suspend fun insertAll(staples: List<StapleEntity>)

    @Query("DELETE FROM staples WHERE `key` = :key")
    suspend fun deleteByKey(key: String)

    @Query("SELECT `key` FROM staples")
    suspend fun getIds(): List<String>

    @Query("DELETE FROM staples")
    suspend fun deleteAll()
}
