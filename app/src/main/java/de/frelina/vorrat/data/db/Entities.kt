package de.frelina.vorrat.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/** Neue, geräteübergreifend eindeutige ID (Sync: beide Handys legen unabhängig Datensätze an). */
fun newId(): String = UUID.randomUUID().toString()

/**
 * Lagerort, z. B. "Keller" oder "Kühlschrank". Name eindeutig (ohne Groß-/Kleinschreibung) –
 * geprüft im CatalogRepository. Seit Schema 2 kein UNIQUE-Index mehr: Room spiegelt den
 * Firestore-Stand, und zwei Handys können offline denselben Namen anlegen.
 */
@Entity(
    tableName = "locations",
    indices = [Index(value = ["name"])],
)
data class LocationEntity(
    @PrimaryKey val id: String = newId(),
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
)

/** Packungseinheit (g, kg, ml, l, …). */
@Entity(
    tableName = "units",
    indices = [Index(value = ["symbol"])],
)
data class UnitEntity(
    @PrimaryKey val id: String = newId(),
    @ColumnInfo(collate = ColumnInfo.NOCASE) val symbol: String,
    val name: String,
)

/** Menge-Art (Stck., Pck., …). */
@Entity(
    tableName = "quantity_types",
    indices = [Index(value = ["symbol"])],
)
data class QuantityTypeEntity(
    @PrimaryKey val id: String = newId(),
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
        // Eindeutigkeit (falls gesetzt) prüft das Formular; kein UNIQUE-Index (siehe LocationEntity).
        Index(value = ["barcode"]),
        Index(value = ["unitId"]),
        Index(value = ["category"]),
    ],
)
data class ProductEntity(
    @PrimaryKey val id: String = newId(),
    val barcode: String?,
    val name: String,
    val brand: String?,
    val category: String?,
    val packageSize: BigDecimal?,
    val unitId: String?,
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
    @PrimaryKey val id: String = newId(),
    val productId: String,
    val locationId: String,
    /** Ebene (optional). */
    val level: Int?,
    /** Fach (optional). */
    val shelf: Int?,
    val expiryDate: LocalDate?,
    /** Immer > 0; bei 0 wird der Eintrag gelöscht (siehe StockRepository). */
    val quantity: Int,
    val quantityTypeId: String,
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
