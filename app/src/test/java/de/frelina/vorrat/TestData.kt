package de.frelina.vorrat

import de.frelina.vorrat.data.db.StockItem
import java.time.LocalDate

fun stockItem(
    entryId: String = "e1",
    productId: String = "p1",
    name: String = "Produkt",
    barcode: String? = null,
    category: String? = null,
    expiry: LocalDate? = null,
    locationId: String? = "l1",
    locationName: String? = "Keller",
    brand: String? = null,
    note: String? = null,
    level: Int? = null,
    shelf: Int? = null,
    quantity: Int = 1,
    openedAt: LocalDate? = null,
    openedDays: Int? = null,
) = StockItem(
    entryId = entryId,
    productId = productId,
    locationId = locationId,
    level = level,
    shelf = shelf,
    expiryDate = expiry,
    quantity = quantity,
    quantityTypeId = "q1",
    note = note,
    productName = name,
    brand = brand,
    barcode = barcode,
    imagePath = null,
    category = category,
    locationName = locationName,
    quantityTypeSymbol = "Stck.",
    openedAt = openedAt,
    openedDays = openedDays,
)
