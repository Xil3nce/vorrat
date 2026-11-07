package de.frelina.vorrat.ui.navigation

import kotlinx.serialization.Serializable

// Hauptbereiche (untere Navigationsleiste)
@Serializable data class StockRoute(val filter: String? = null)
@Serializable data object SearchRoute
@Serializable data object ScanRoute
@Serializable data object LocationsRoute
@Serializable data object SettingsRoute

// Unterseiten
@Serializable data class EntryDetailRoute(val entryId: Long)

/**
 * Erfassen:
 * - productId gesetzt (≠ NONE) → neuer Bestandseintrag für bekanntes Produkt
 * - nur barcode gesetzt → neues Produkt (Vorbelegung aus Open Food Facts, falls vorhanden)
 * - nichts gesetzt → Produkt manuell anlegen
 */
@Serializable data class AddStockRoute(val barcode: String? = null, val productId: Long = NONE) {
    companion object {
        const val NONE = -1L
    }
}
@Serializable data class EditEntryRoute(val editEntryId: Long)
@Serializable data class EditProductRoute(val editProductId: Long)
@Serializable data class CatalogRoute(val kind: String) {
    companion object {
        const val UNITS = "units"
        const val QUANTITY_TYPES = "quantity_types"
    }
}
@Serializable data object AboutRoute
