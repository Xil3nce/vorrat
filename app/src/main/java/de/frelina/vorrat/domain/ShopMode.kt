package de.frelina.vorrat.domain

import de.frelina.vorrat.data.db.LocationEntity
import de.frelina.vorrat.data.db.ProductEntity
import de.frelina.vorrat.data.db.StockItem
import de.frelina.vorrat.data.purchase.PurchaseDraft
import de.frelina.vorrat.data.purchase.PurchaseLogic

/**
 * Einkaufsmodus: die Einkaufsliste im Laden abhaken. Abgehaktes landet als Artikel im Einkauf ("+ Bestand") und
 * wird von dort gesammelt in den Bestand übernommen (reine Funktionen).
 */
object ShopMode {
    private const val SHORTAGE_PREFIX = "s-"
    private const val ITEM_PREFIX = "i-"

    /** Schlüssel eines Eintrags unter Mindestbestand (wie in der gruppierten Einkaufsliste). */
    fun shortageKey(productId: String): String = SHORTAGE_PREFIX + productId

    /** Schlüssel eines manuellen Listeneintrags. */
    fun itemKey(itemId: String): String = ITEM_PREFIX + itemId

    /** ID des manuellen Listeneintrags zu einem Schlüssel; null bei Einträgen unter Mindestbestand. */
    fun itemId(key: String): String? =
        key.takeIf { it.startsWith(ITEM_PREFIX) }?.removePrefix(ITEM_PREFIX)?.takeIf(String::isNotEmpty)

    /** Offene Zeilen (mit Abteilungs-Überschriften) und abgehakte Einträge. */
    data class Split<T>(val open: List<BoardRow<T>>, val checked: List<BoardRow<T>>) {
        val openCount: Int get() = open.count { !it.isHeader }
        val total: Int get() = openCount + checked.size
    }

    /**
     * Teilt die gruppierte Liste: Abgehaktes wandert ans Ende ("Im Wagen"), Abteilungen ohne offene Einträge
     * entfallen. Die Reihenfolge der Abteilungen (Gang durch den Laden) bleibt.
     */
    fun <T> split(rows: List<BoardRow<T>>, isChecked: (BoardRow<T>) -> Boolean): Split<T> {
        val entries = rows.filter { !it.isHeader }
        val checked = entries.filter(isChecked)
        val checkedKeys = checked.map { it.key }.toSet()
        val openDepartments = entries.filter { it.key !in checkedKeys }.map { it.departmentId }.toSet()
        val open = rows.filter { if (it.isHeader) it.departmentId in openDepartments else it.key !in checkedKeys }
        return Split(open, checked)
    }

    /** Ort, Ebene und Fach für einen abgehakten Artikel. */
    data class Placement(val locationId: String?, val level: Int?, val shelf: Int?)

    /**
     * Wie beim "+ Bestand"-Scan: der im Scanner gewählte Ort, sonst der Ort des vorhandenen Bestands, sonst der
     * einzige Ort. Ebene/Fach: im Scanner gewählt, sonst wie beim vorhandenen Bestand am selben Ort.
     */
    fun placement(
        scanLocationId: String?,
        scanLevel: Int?,
        scanShelf: Int?,
        locations: List<LocationEntity>,
        match: StockItem?,
    ): Placement {
        val chosen = PurchaseLogic.resolveLocation(scanLocationId, locations.map { it.id })
        val locationId = chosen ?: match?.locationId ?: locations.singleOrNull()?.id
        val location = locations.firstOrNull { it.id == locationId }
        val sameAsMatch = match != null && match.locationId == locationId
        return Placement(
            locationId = locationId,
            level = PurchaseLogic.resolveSlot(scanLevel, location?.levels) ?: match?.level?.takeIf { sameAsMatch },
            shelf = PurchaseLogic.resolveSlot(scanShelf, location?.shelves) ?: match?.shelf?.takeIf { sameAsMatch },
        )
    }

    /**
     * Artikel für einen abgehakten Eintrag. Bekanntes [product]: neuer Bestandseintrag dafür; sonst entsteht beim
     * Übernehmen ein Produkt mit dem Namen des Listeneintrags (Packungsgröße nur zusammen mit einer Einheit).
     */
    fun draft(
        key: String,
        product: ProductEntity?,
        name: String,
        quantity: Int,
        packageSize: java.math.BigDecimal?,
        unitId: String?,
        placement: Placement,
        quantityTypeId: String?,
    ): PurchaseDraft {
        val size = (product?.packageSize ?: packageSize?.takeIf { unitId != null })?.takeIf { it.signum() > 0 }
        return PurchaseDraft(
            shoppingKey = key,
            barcode = product?.barcode,
            productId = product?.id,
            name = product?.name ?: name.trim(),
            brand = product?.brand.orEmpty(),
            category = product?.category.orEmpty(),
            packageSize = size?.stripTrailingZeros()?.toPlainString().orEmpty(),
            unitId = if (product != null) product.unitId else unitId.takeIf { size != null },
            imagePath = product?.imagePath,
            ingredientGroup = product?.ingredientGroup,
            locationId = placement.locationId,
            level = placement.level?.toString().orEmpty(),
            shelf = placement.shelf?.toString().orEmpty(),
            quantity = quantity.coerceAtLeast(1).toString(),
            quantityTypeId = quantityTypeId,
        )
    }
}
