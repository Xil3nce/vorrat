package de.frelina.vorrat.data.backup

import kotlinx.serialization.Serializable

/**
 * Inhalt von backup.json im Export-ZIP.
 * formatVersion: Version dieses JSON-Formats; schemaVersion: Room-Datenbankversion beim Export.
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
)

@Serializable
data class LocationDto(val id: Long, val name: String)

@Serializable
data class UnitDto(val id: Long, val symbol: String, val name: String)

@Serializable
data class QuantityTypeDto(val id: Long, val symbol: String, val name: String)

@Serializable
data class ProductDto(
    val id: Long,
    val barcode: String? = null,
    val name: String,
    val brand: String? = null,
    val category: String? = null,
    /** Dezimalzahl als Text (verlustfrei), z. B. "1.5" */
    val packageSize: String? = null,
    val unitId: Long? = null,
    /** Dateiname im ZIP unter images/ */
    val imagePath: String? = null,
    val source: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class StockEntryDto(
    val id: Long,
    val productId: Long,
    val locationId: Long,
    val level: Int? = null,
    val shelf: Int? = null,
    /** ISO-8601, z. B. "2026-12-31" */
    val expiryDate: String? = null,
    val quantity: Int,
    val quantityTypeId: Long,
    val note: String? = null,
)

/** Validierungs-/Lesefehler beim Import; [reason] wird in der UI übersetzt. */
class BackupException(val reason: Reason, val detail: String? = null) : Exception("$reason ${detail.orEmpty()}") {
    enum class Reason { NOT_A_BACKUP, UNREADABLE, NEWER_SCHEMA, UNSUPPORTED_FORMAT, INVALID_DATA }
}
