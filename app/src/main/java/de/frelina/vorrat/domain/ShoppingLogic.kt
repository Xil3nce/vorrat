package de.frelina.vorrat.domain

import de.frelina.vorrat.data.db.ProductEntity
import de.frelina.vorrat.data.db.StockItem

/** Produkt unter Mindestbestand: [have] vorhanden (Summe aller Einträge), [min] gewünscht. */
data class Shortage(val product: ProductEntity, val have: Int, val min: Int) {
    val missing: Int get() = min - have
}

object ShoppingLogic {

    /** Produkte, deren Bestand (Summe der Anzahlen aller Einträge) unter dem Mindestbestand liegt. */
    fun shortages(products: List<ProductEntity>, stock: List<StockItem>): List<Shortage> {
        val totals = stock.groupBy { it.productId }.mapValues { (_, items) -> items.sumOf { it.quantity } }
        return products.mapNotNull { p ->
            val min = p.minStock?.takeIf { it > 0 } ?: return@mapNotNull null
            val have = totals[p.id] ?: 0
            if (have < min) Shortage(p, have, min) else null
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.product.name })
    }

    /** Freitext einem Produkt zuordnen, wenn der Name exakt (ohne Groß-/Kleinschreibung) passt. */
    fun matchProduct(name: String, products: List<ProductEntity>): ProductEntity? {
        val n = name.trim()
        return products.firstOrNull { it.name.trim().equals(n, ignoreCase = true) }
    }
}
