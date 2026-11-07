package de.example.vorrat.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {
    @Query("SELECT * FROM locations ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<LocationEntity>>

    @Query("SELECT * FROM locations ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<LocationEntity>

    @Query("SELECT * FROM locations WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): LocationEntity?

    @Insert
    suspend fun insert(location: LocationEntity): Long

    @Insert
    suspend fun insertAll(locations: List<LocationEntity>)

    @Update
    suspend fun update(location: LocationEntity)

    @Delete
    suspend fun delete(location: LocationEntity)

    @Query("SELECT COUNT(*) FROM stock_entries WHERE locationId = :locationId")
    suspend fun countStockEntries(locationId: Long): Int

    @Query("DELETE FROM locations")
    suspend fun deleteAll()
}

@Dao
interface UnitDao {
    @Query("SELECT * FROM units ORDER BY id")
    fun observeAll(): Flow<List<UnitEntity>>

    @Query("SELECT * FROM units ORDER BY id")
    suspend fun getAll(): List<UnitEntity>

    @Query("SELECT * FROM units WHERE symbol = :symbol COLLATE NOCASE LIMIT 1")
    suspend fun findBySymbol(symbol: String): UnitEntity?

    @Insert
    suspend fun insert(unit: UnitEntity): Long

    @Insert
    suspend fun insertAll(units: List<UnitEntity>)

    @Update
    suspend fun update(unit: UnitEntity)

    @Delete
    suspend fun delete(unit: UnitEntity)

    @Query("SELECT COUNT(*) FROM products WHERE unitId = :unitId")
    suspend fun countProducts(unitId: Long): Int

    @Query("DELETE FROM units")
    suspend fun deleteAll()
}

@Dao
interface QuantityTypeDao {
    @Query("SELECT * FROM quantity_types ORDER BY id")
    fun observeAll(): Flow<List<QuantityTypeEntity>>

    @Query("SELECT * FROM quantity_types ORDER BY id")
    suspend fun getAll(): List<QuantityTypeEntity>

    @Query("SELECT * FROM quantity_types WHERE symbol = :symbol COLLATE NOCASE LIMIT 1")
    suspend fun findBySymbol(symbol: String): QuantityTypeEntity?

    @Insert
    suspend fun insert(type: QuantityTypeEntity): Long

    @Insert
    suspend fun insertAll(types: List<QuantityTypeEntity>)

    @Update
    suspend fun update(type: QuantityTypeEntity)

    @Delete
    suspend fun delete(type: QuantityTypeEntity)

    @Query("SELECT COUNT(*) FROM stock_entries WHERE quantityTypeId = :typeId")
    suspend fun countStockEntries(typeId: Long): Int

    @Query("DELETE FROM quantity_types")
    suspend fun deleteAll()
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getById(id: Long): ProductEntity?

    @Query("SELECT * FROM products WHERE id = :id")
    fun observeById(id: Long): Flow<ProductEntity?>

    @Query("SELECT * FROM products WHERE barcode = :barcode LIMIT 1")
    suspend fun getByBarcode(barcode: String): ProductEntity?

    @Query("SELECT * FROM products ORDER BY id")
    suspend fun getAll(): List<ProductEntity>

    @Query(
        "SELECT DISTINCT category FROM products WHERE category IS NOT NULL AND category != '' " +
            "ORDER BY category COLLATE NOCASE",
    )
    fun observeCategories(): Flow<List<String>>

    @Query("SELECT imagePath FROM products WHERE imagePath IS NOT NULL")
    suspend fun getAllImagePaths(): List<String>

    @Insert
    suspend fun insert(product: ProductEntity): Long

    @Insert
    suspend fun insertAll(products: List<ProductEntity>)

    @Update
    suspend fun update(product: ProductEntity)

    @Query("DELETE FROM products")
    suspend fun deleteAll()
}

@Dao
interface StockEntryDao {
    @Query(STOCK_ITEM_SELECT)
    fun observeAllItems(): Flow<List<StockItem>>

    @Query("$STOCK_ITEM_SELECT WHERE e.id = :entryId")
    fun observeItem(entryId: Long): Flow<StockItem?>

    @Query("$STOCK_ITEM_SELECT WHERE e.productId = :productId")
    suspend fun getItemsForProduct(productId: Long): List<StockItem>

    @Query(STOCK_ITEM_SELECT)
    suspend fun getAllItems(): List<StockItem>

    @Query("SELECT * FROM stock_entries WHERE id = :id")
    suspend fun getById(id: Long): StockEntryEntity?

    @Query("SELECT * FROM stock_entries ORDER BY id")
    suspend fun getAll(): List<StockEntryEntity>

    @Insert
    suspend fun insert(entry: StockEntryEntity): Long

    @Insert
    suspend fun insertAll(entries: List<StockEntryEntity>)

    @Update
    suspend fun update(entry: StockEntryEntity)

    @Query("DELETE FROM stock_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM stock_entries")
    suspend fun deleteAll()

    companion object {
        const val STOCK_ITEM_SELECT = """
            SELECT e.id AS entryId, e.productId, e.locationId, e.level, e.shelf, e.expiryDate,
                   e.quantity, e.quantityTypeId, e.note,
                   p.name AS productName, p.brand, p.barcode, p.imagePath, p.category,
                   l.name AS locationName, q.symbol AS quantityTypeSymbol
            FROM stock_entries e
            JOIN products p ON p.id = e.productId
            JOIN locations l ON l.id = e.locationId
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
