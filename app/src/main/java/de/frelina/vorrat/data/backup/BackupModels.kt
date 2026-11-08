package de.frelina.vorrat.data.backup

import kotlinx.serialization.Serializable

/**
 * Inhalt von backup.json im Export-ZIP.
 * formatVersion: Version dieses JSON-Formats; schemaVersion: Room-Datenbankversion beim Export.
 * Ab schemaVersion 2 sind IDs Text (UUIDs); Backups mit schemaVersion 1 enthalten Zahlen-IDs und werden
 * beim Lesen (lenient) als Text übernommen – dieselbe Umwandlung wie in der Room-Migration 1 → 2.
 */
@Serializable
data class BackupData(
    val formatVersion: Int,
    val schemaVersion: Int,
    val exportedAt: Long,
    val appVersion: String,
    val locations: List<LocationDto>,
    val units: List<UnitDto>,
    val quantityTypes: List<QuantityTypeDto>,
    val products: List<ProductDto>,
    val stockEntries: List<StockEntryDto>,
    /** Seit Schema 5. */
    val shoppingItems: List<ShoppingItemDto> = emptyList(),
)

@Serializable
data class ShoppingItemDto(
    val id: String,
    val productId: String? = null,
    val name: String,
    val quantity: Int = 1,
    val note: String? = null,
    val createdAt: Long = 0,
)

@Serializable
data class LocationDto(val id: String, val name: String, val levels: Int? = null, val shelves: Int? = null)

@Serializable
data class UnitDto(val id: String, val symbol: String, val name: String)

@Serializable
data class QuantityTypeDto(val id: String, val symbol: String, val name: String)

@Serializable
data class ProductDto(
    val id: String,
    val barcode: String? = null,
    val name: String,
    val brand: String? = null,
    val category: String? = null,
    /** Dezimalzahl als Text (verlustfrei), z. B. "1.5" */
    val packageSize: String? = null,
    val unitId: String? = null,
    /** Dateiname im ZIP unter images/ */
    val imagePath: String? = null,
    val source: String,
    val createdAt: Long,
    val updatedAt: Long,
    val minStock: Int? = null,
    val openedDays: Int? = null,
)

@Serializable
data class StockEntryDto(
    val id: String,
    val productId: String,
    /** null = keinem Ort zugeordnet (seit Schema 5). */
    val locationId: String? = null,
    val level: Int? = null,
    val shelf: Int? = null,
    /** ISO-8601, z. B. "2026-12-31" */
    val expiryDate: String? = null,
    val quantity: Int,
    val quantityTypeId: String,
    val note: String? = null,
    /** ISO-8601 (seit Schema 5). */
    val openedAt: String? = null,
    val openedDays: Int? = null,
)

/** Validierungs-/Lesefehler beim Import; [reason] wird in der UI übersetzt. */
class BackupException(val reason: Reason, val detail: String? = null) : Exception("$reason ${detail.orEmpty()}") {
    enum class Reason { NOT_A_BACKUP, UNREADABLE, NEWER_SCHEMA, UNSUPPORTED_FORMAT, INVALID_DATA }
}
