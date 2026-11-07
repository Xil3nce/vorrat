package de.example.vorrat

import de.example.vorrat.data.db.StockItem
import java.time.LocalDate

fun stockItem(
    entryId: Long = 1,
    productId: Long = 1,
    name: String = "Produkt",
    barcode: String? = null,
    category: String? = null,
    expiry: LocalDate? = null,
    locationId: Long = 1,
    locationName: String = "Keller",
    brand: String? = null,
    note: String? = null,
    level: Int? = null,
    shelf: Int? = null,
) = StockItem(
    entryId = entryId,
    productId = productId,
    locationId = locationId,
    level = level,
    shelf = shelf,
    expiryDate = expiry,
    quantity = 1,
    quantityTypeId = 1,
    note = note,
    productName = name,
    brand = brand,
    barcode = barcode,
    imagePath = null,
    category = category,
    locationName = locationName,
    quantityTypeSymbol = "Stck.",
)
