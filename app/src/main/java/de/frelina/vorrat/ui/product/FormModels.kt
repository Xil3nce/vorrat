package de.frelina.vorrat.ui.product

import de.frelina.vorrat.data.db.ProductEntity
import de.frelina.vorrat.data.db.ProductSource
import de.frelina.vorrat.data.db.StockEntryEntity
import de.frelina.vorrat.data.db.newId
import de.frelina.vorrat.domain.Validated
import de.frelina.vorrat.domain.Validation
import de.frelina.vorrat.domain.ValidationError

data class ProductForm(
    val name: String = "",
    val brand: String = "",
    val category: String = "",
    val packageSize: String = "",
    val unitId: String? = null,
    val barcode: String = "",
    val imagePath: String? = null,
    val minStock: String = "",
    val nameError: ValidationError? = null,
    val packageSizeError: ValidationError? = null,
    val minStockError: ValidationError? = null,
    val barcodeDuplicate: Boolean = false,
) {
    /** Validiert und baut die Entity; null bei Fehlern (Fehler stehen dann im zurückgegebenen Formular). */
    fun validate(): Pair<ProductForm, ProductFields?> {
        val nameV = Validation.required(name)
        val sizeV = Validation.optionalPositiveDecimal(packageSize)
        val minV = Validation.optionalWholeNumber(minStock)
        val checked = copy(
            nameError = (nameV as? Validated.Error)?.error,
            packageSizeError = (sizeV as? Validated.Error)?.error,
            minStockError = (minV as? Validated.Error)?.error,
            barcodeDuplicate = false,
        )
        if (nameV !is Validated.Ok || sizeV !is Validated.Ok || minV !is Validated.Ok) return checked to null
        return checked to ProductFields(
            name = nameV.value,
            brand = brand.trim().ifEmpty { null },
            category = category.trim().ifEmpty { null },
            packageSize = sizeV.value,
            unitId = unitId,
            barcode = barcode.trim().ifEmpty { null },
            // 0 = kein Mindestbestand
            minStock = minV.value?.takeIf { it > 0 }?.coerceAtMost(999),
        )
    }

    companion object {
        fun from(p: ProductEntity) = ProductForm(
            name = p.name,
            brand = p.brand.orEmpty(),
            category = p.category.orEmpty(),
            packageSize = p.packageSize?.toPlainString().orEmpty(),
            unitId = p.unitId,
            barcode = p.barcode.orEmpty(),
            imagePath = p.imagePath,
            minStock = p.minStock?.toString().orEmpty(),
        )
    }
}

data class ProductFields(
    val name: String,
    val brand: String?,
    val category: String?,
    val packageSize: java.math.BigDecimal?,
    val unitId: String?,
    val barcode: String?,
    val minStock: Int? = null,
) {
    fun toNewEntity(imagePath: String?, source: ProductSource, now: Long) = ProductEntity(
        barcode = barcode,
        name = name,
        brand = brand,
        category = category,
        packageSize = packageSize,
        unitId = unitId,
        imagePath = imagePath,
        source = source,
        createdAt = now,
        updatedAt = now,
        minStock = minStock,
    )

    fun applyTo(p: ProductEntity, imagePath: String?) = p.copy(
        barcode = barcode,
        name = name,
        brand = brand,
        category = category,
        packageSize = packageSize,
        unitId = unitId,
        imagePath = imagePath,
        minStock = minStock,
    )
}

data class EntryForm(
    val locationId: String? = null,
    val level: String = "",
    val shelf: String = "",
    val expiry: String = "",
    val quantity: String = "1",
    val quantityTypeId: String? = null,
    val note: String = "",
    /** Beim Bearbeiten unverändert übernommen ("Geöffnet am" wird in den Details gesetzt). */
    val openedAt: java.time.LocalDate? = null,
    val openedDays: Int? = null,
    val levelError: ValidationError? = null,
    val shelfError: ValidationError? = null,
    val expiryError: ValidationError? = null,
    val quantityError: ValidationError? = null,
    val quantityTypeMissing: Boolean = false,
) {
    fun validate(entryId: String = newId(), productId: String = ""): Pair<EntryForm, StockEntryEntity?> {
        val levelV = Validation.optionalWholeNumber(level)
        val shelfV = Validation.optionalWholeNumber(shelf)
        val expiryV = Validation.optionalDate(expiry)
        val qtyV = Validation.quantity(quantity)
        val checked = copy(
            levelError = (levelV as? Validated.Error)?.error,
            shelfError = (shelfV as? Validated.Error)?.error,
            expiryError = (expiryV as? Validated.Error)?.error,
            quantityError = (qtyV as? Validated.Error)?.error,
            quantityTypeMissing = quantityTypeId == null,
        )
        if (quantityTypeId == null || levelV !is Validated.Ok || shelfV !is Validated.Ok ||
            expiryV !is Validated.Ok || qtyV !is Validated.Ok
        ) {
            return checked to null
        }
        return checked to StockEntryEntity(
            id = entryId,
            productId = productId,
            locationId = locationId,
            level = levelV.value,
            shelf = shelfV.value,
            expiryDate = expiryV.value,
            quantity = qtyV.value,
            quantityTypeId = quantityTypeId,
            note = note.trim().ifEmpty { null },
            openedAt = openedAt,
            openedDays = openedDays,
        )
    }

    companion object {
        fun from(e: StockEntryEntity) = EntryForm(
            locationId = e.locationId,
            level = e.level?.toString().orEmpty(),
            shelf = e.shelf?.toString().orEmpty(),
            expiry = Validation.formatDate(e.expiryDate),
            quantity = e.quantity.toString(),
            quantityTypeId = e.quantityTypeId,
            note = e.note.orEmpty(),
            openedAt = e.openedAt,
            openedDays = e.openedDays,
        )
    }
}
