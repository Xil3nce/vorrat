package de.frelina.vorrat.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.LocalDate

/** Lagerort, z. B. "Keller" oder "Kühlschrank". Name eindeutig (ohne Groß-/Kleinschreibung). */
@Entity(
    tableName = "locations",
    indices = [Index(value = ["name"], unique = true)],
)
data class LocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
)

/** Packungseinheit (g, kg, ml, l, …). */
@Entity(
    tableName = "units",
    indices = [Index(value = ["symbol"], unique = true)],
)
data class UnitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val symbol: String,
    val name: String,
)

/** Menge-Art (Stck., Pck., …). */
@Entity(
    tableName = "quantity_types",
    indices = [Index(value = ["symbol"], unique = true)],
)
data class QuantityTypeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val symbol: String,
    val name: String,
)

enum class ProductSource { OPEN_FOOD_FACTS, MANUAL }

@Entity(
    tableName = "products",
    foreignKeys = [
        ForeignKey(
            entity = UnitEntity::class,
            parentColumns = ["id"],
            childColumns = ["unitId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        // SQLite erlaubt mehrere NULL-Werte in einem UNIQUE-Index → "eindeutig, falls gesetzt".
        Index(value = ["barcode"], unique = true),
        Index(value = ["unitId"]),
        Index(value = ["category"]),
    ],
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val barcode: String?,
    val name: String,
    val brand: String?,
    val category: String?,
    val packageSize: BigDecimal?,
    val unitId: Long?,
    /** Dateiname relativ zum App-internen Bildordner (siehe ImageStore). */
    val imagePath: String?,
    val source: ProductSource,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "stock_entries",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["locationId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = QuantityTypeEntity::class,
            parentColumns = ["id"],
            childColumns = ["quantityTypeId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["productId"]),
        Index(value = ["locationId"]),
        Index(value = ["quantityTypeId"]),
        Index(value = ["expiryDate"]),
    ],
)
data class StockEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val locationId: Long,
    /** Ebene (optional). */
    val level: Int?,
    /** Fach (optional). */
    val shelf: Int?,
    val expiryDate: LocalDate?,
    /** Immer > 0; bei 0 wird der Eintrag gelöscht (siehe StockRepository). */
    val quantity: Int,
    val quantityTypeId: Long,
    val note: String?,
)

/**
 * Lokaler Cache für Barcode-Abfragen bei Open Food Facts (auch "nicht gefunden"),
 * damit dieselbe Abfrage nicht wiederholt werden muss.
 */
@Entity(tableName = "lookup_cache")
data class LookupCacheEntity(
    @PrimaryKey val barcode: String,
    val found: Boolean,
    val name: String?,
    val brand: String?,
    val category: String?,
    val packageSize: BigDecimal?,
    val unitSymbol: String?,
    /** Lokal heruntergeladenes Bild (Dateiname im Bildordner). */
    val imagePath: String?,
    val fetchedAt: Long,
)
