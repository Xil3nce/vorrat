package de.example.vorrat.domain

import de.example.vorrat.data.db.StockItem

/**
 * Alternativprodukte nach einem Scan.
 *
 * Regeln (siehe README, "Alternativprodukte"):
 * - Nur exakt dieselbe (spezifischste) Kategorie, keine Oberkategorien, keine Namenssuche.
 * - Vergleich ohne Berücksichtigung von Groß-/Kleinschreibung und umgebenden Leerzeichen.
 * - Hat das gescannte Produkt oder das Bestandsprodukt keine Kategorie → keine Alternative.
 * - Das gescannte Produkt selbst (gleicher Barcode bzw. gleiche Produkt-ID) ist nie eine Alternative.
 */
object AlternativeMatcher {

    fun normalize(category: String?): String? =
        category?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

    fun findAlternatives(
        scannedCategory: String?,
        scannedBarcode: String?,
        scannedProductId: Long?,
        stock: List<StockItem>,
    ): List<StockItem> {
        val wanted = normalize(scannedCategory) ?: return emptyList()
        return stock.filter { item ->
            val sameProduct = (scannedProductId != null && item.productId == scannedProductId) ||
                (scannedBarcode != null && item.barcode == scannedBarcode)
            !sameProduct && normalize(item.category) == wanted
        }
    }
}
