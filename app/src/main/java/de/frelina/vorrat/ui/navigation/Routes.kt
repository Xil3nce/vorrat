package de.frelina.vorrat.ui.navigation

import kotlinx.serialization.Serializable

// Hauptbereiche (untere Navigationsleiste)
@Serializable data class StockRoute(val filter: String? = null)
@Serializable data object SearchRoute
@Serializable data object ScanRoute
@Serializable data object ShoppingRoute

// Unterseiten (Orte: über Einstellungen erreichbar)
@Serializable data object LocationsRoute
@Serializable data object SettingsRoute

// Unterseiten
@Serializable data class EntryDetailRoute(val entryId: String)
@Serializable data object HistoryRoute

// Rezepte ("Was koche ich heute?"): Liste, Details, Editor (id = null → neues Rezept; "builtin:…" → Kopie davon)
@Serializable data object RecipesRoute
@Serializable data class RecipeDetailRoute(val id: String)
@Serializable data class EditRecipeRoute(val id: String? = null)

/**
 * Erfassen:
 * - productId gesetzt (≠ NONE) → neuer Bestandseintrag für bekanntes Produkt
 * - nur barcode gesetzt → neues Produkt (Vorbelegung aus Open Food Facts, falls vorhanden)
 * - nichts gesetzt → Produkt manuell anlegen
 * - shoppingItemId: Kauf von der Einkaufsliste – Name vorbelegen, Listeneintrag nach dem Speichern entfernen
 * - frozen: selbst Eingefrorenes (ohne Barcode, mit Einfrierdatum); nfcTagId: bereits gelesener Chip
 */
@Serializable data class AddStockRoute(
    val barcode: String? = null,
    val productId: String? = null,
    val shoppingItemId: String? = null,
    val frozen: Boolean = false,
    val nfcTagId: String? = null,
)
@Serializable data class EditEntryRoute(val editEntryId: String)

/** Formular vorbelegt mit den Werten eines Eintrags; neuer Eintrag entsteht erst beim Speichern. */
@Serializable data class DuplicateEntryRoute(val copyFromEntryId: String)
@Serializable data class EditProductRoute(val editProductId: String)
@Serializable data class CatalogRoute(val kind: String) {
    companion object {
        const val UNITS = "units"
        const val QUANTITY_TYPES = "quantity_types"
    }
}
@Serializable data object AboutRoute
