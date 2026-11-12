package de.frelina.vorrat.ui.navigation

import kotlinx.serialization.Serializable

// Hauptbereiche (untere Navigationsleiste); locationId: Bestand gleich nach diesem Ort filtern (aus der Übersicht)
@Serializable data class StockRoute(val filter: String? = null, val locationId: String? = null)
@Serializable data object OverviewRoute
@Serializable data object ScanRoute
@Serializable data object ShoppingRoute

// Unterseiten (Orte: über Einstellungen erreichbar, gleiche Ansicht wie die Übersicht)
@Serializable data object LocationsRoute
@Serializable data object SettingsRoute

// Unterseiten
@Serializable data class EntryDetailRoute(val entryId: String)
@Serializable data object HistoryRoute

// Rezepte ("Was koche ich heute?"): Liste, Details, Editor (id = null → neues Rezept; "builtin:…" → Kopie davon;
// importText = geteilter Text/Link → Rezept von Chefkoch laden und vorbelegen)
@Serializable data object RecipesRoute
@Serializable data class RecipeDetailRoute(val id: String, val persons: Int? = null)
@Serializable data class EditRecipeRoute(val id: String? = null, val importText: String? = null)

// Einkaufsliste nach Abteilungen: Abteilungen verwalten
@Serializable data object DepartmentsRoute

// Wochenplan und Prüfseite "Zutaten auf die Einkaufsliste" (from/to als ISO-Datum, beide inklusive)
@Serializable data object MealPlanRoute
@Serializable data class PlanShoppingRoute(val from: String, val to: String)

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
    /** Artikel aus dem Einkauf ("+ Bestand") ergänzen – gespeichert wird in den Einkauf, nicht in den Bestand. */
    val draftId: String? = null,
)
/** Artikel der Einkaufsliste als gekauft erfassen: erst Barcode scannen, ohne Barcode manuell anlegen. */
@Serializable data class BoughtScanRoute(val shoppingItemId: String, val name: String)
/** Einkauf ("+ Bestand"): gescannte Artikel prüfen und in den Bestand übernehmen. */
@Serializable data object PurchaseRoute
/** Einkaufsmodus: Einkaufsliste im Laden abhaken; Abgehaktes landet im Einkauf. */
@Serializable data object ShopModeRoute
/** Entnehmen: im Scanner vorgemerkte Entnahmen prüfen und gesammelt ausbuchen. */
@Serializable data object ConsumeRoute
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
