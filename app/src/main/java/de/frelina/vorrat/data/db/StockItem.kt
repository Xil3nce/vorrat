package de.frelina.vorrat.data.db

import java.time.LocalDate

/** Bestandseintrag inkl. Produkt-, Orts- und Menge-Art-Daten (für Listen). */
data class StockItem(
    val entryId: Long,
    val productId: Long,
    val locationId: Long,
    val level: Int?,
    val shelf: Int?,
    val expiryDate: LocalDate?,
    val quantity: Int,
    val quantityTypeId: Long,
    val note: String?,
    val productName: String,
    val brand: String?,
    val barcode: String?,
    val imagePath: String?,
    val category: String?,
    val locationName: String,
    val quantityTypeSymbol: String,
)
