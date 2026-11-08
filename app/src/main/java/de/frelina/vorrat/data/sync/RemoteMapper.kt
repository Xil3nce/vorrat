package de.frelina.vorrat.data.sync

import de.frelina.vorrat.data.db.HistoryAction
import de.frelina.vorrat.data.db.HistoryEventEntity
import de.frelina.vorrat.data.db.LocationEntity
import de.frelina.vorrat.data.db.ProductEntity
import de.frelina.vorrat.data.db.ProductSource
import de.frelina.vorrat.data.db.ShoppingItemEntity
import de.frelina.vorrat.domain.OpenedLogic
import de.frelina.vorrat.data.db.QuantityTypeEntity
import de.frelina.vorrat.data.db.StockEntryEntity
import de.frelina.vorrat.data.db.UnitEntity
import java.time.LocalDate

/**
 * Umwandlung Room-Entity ↔ Firestore-Dokument (Map). Die Dokument-ID ist die Entity-ID.
 * Dezimalzahlen und Datumswerte werden als Text gespeichert (verlustfrei, lesbar in der Konsole).
 * Unvollständige oder fehlerhafte Dokumente ergeben null und werden beim Spiegeln ignoriert.
 */
object RemoteMapper {
    const val FIELD_QUANTITY = "quantity"

    fun toMap(e: LocationEntity): Map<String, Any?> = mapOf(
        "name" to e.name,
        "levels" to e.levels?.toLong(),
        "shelves" to e.shelves?.toLong(),
    )

    fun toMap(e: UnitEntity): Map<String, Any?> = mapOf("symbol" to e.symbol, "name" to e.name)

    fun toMap(e: QuantityTypeEntity): Map<String, Any?> = mapOf("symbol" to e.symbol, "name" to e.name)

    fun toMap(p: ProductEntity): Map<String, Any?> = mapOf(
        "barcode" to p.barcode,
        "name" to p.name,
        "brand" to p.brand,
        "category" to p.category,
        "packageSize" to p.packageSize?.toPlainString(),
        "unitId" to p.unitId,
        "imagePath" to p.imagePath,
        "source" to p.source.name,
        "createdAt" to p.createdAt,
        "updatedAt" to p.updatedAt,
        "minStock" to p.minStock?.toLong(),
        "openedDays" to p.openedDays?.toLong(),
    )

    fun toMap(e: StockEntryEntity): Map<String, Any?> = mapOf(
        "productId" to e.productId,
        "locationId" to e.locationId,
        "level" to e.level?.toLong(),
        "shelf" to e.shelf?.toLong(),
        "expiryDate" to e.expiryDate?.toString(),
        FIELD_QUANTITY to e.quantity.toLong(),
        "quantityTypeId" to e.quantityTypeId,
        "note" to e.note,
        "openedAt" to e.openedAt?.toString(),
        "openedDays" to e.openedDays?.toLong(),
        "frozenAt" to e.frozenAt?.toString(),
        "nfcTagId" to e.nfcTagId,
    )

    fun toMap(h: HistoryEventEntity): Map<String, Any?> = mapOf(
        "createdAt" to h.createdAt,
        "action" to h.action.name,
        "authorUid" to h.authorUid,
        "authorName" to h.authorName,
        "productId" to h.productId,
        "productName" to h.productName,
        "quantity" to h.quantity?.toLong(),
        "quantityTypeSymbol" to h.quantityTypeSymbol,
        "entryId" to h.entryId,
        "outOfStock" to h.outOfStock,
        "message" to h.message,
        "notify" to h.notify,
    )

    fun toMap(s: ShoppingItemEntity): Map<String, Any?> = mapOf(
        "productId" to s.productId,
        "name" to s.name,
        "quantity" to s.quantity.toLong(),
        "note" to s.note,
        "createdAt" to s.createdAt,
    )

    fun location(id: String, m: Map<String, Any?>): LocationEntity? {
        val name = m.str("name")?.takeIf(String::isNotBlank) ?: return null
        return LocationEntity(
            id = id,
            name = name,
            levels = m.long("levels")?.toInt()?.takeIf { it in 1..LocationEntity.MAX_SLOTS },
            shelves = m.long("shelves")?.toInt()?.takeIf { it in 1..LocationEntity.MAX_SLOTS },
        )
    }

    fun unit(id: String, m: Map<String, Any?>): UnitEntity? {
        val symbol = m.str("symbol")?.takeIf(String::isNotBlank) ?: return null
        val name = m.str("name")?.takeIf(String::isNotBlank) ?: return null
        return UnitEntity(id, symbol, name)
    }

    fun quantityType(id: String, m: Map<String, Any?>): QuantityTypeEntity? {
        val symbol = m.str("symbol")?.takeIf(String::isNotBlank) ?: return null
        val name = m.str("name")?.takeIf(String::isNotBlank) ?: return null
        return QuantityTypeEntity(id, symbol, name)
    }

    fun product(id: String, m: Map<String, Any?>): ProductEntity? {
        val name = m.str("name")?.takeIf(String::isNotBlank) ?: return null
        val source = m.str("source")?.let { s -> ProductSource.entries.firstOrNull { it.name == s } }
            ?: ProductSource.MANUAL
        return ProductEntity(
            id = id,
            barcode = m.str("barcode")?.takeIf(String::isNotBlank),
            name = name,
            brand = m.str("brand"),
            category = m.str("category"),
            packageSize = m.str("packageSize")?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 },
            unitId = m.str("unitId"),
            imagePath = m.str("imagePath")?.takeIf(::isSafeFileName),
            source = source,
            createdAt = m.long("createdAt") ?: 0,
            updatedAt = m.long("updatedAt") ?: 0,
            minStock = m.long("minStock")?.toInt()?.takeIf { it in 1..MAX_COUNT },
            openedDays = m.long("openedDays")?.toInt()?.takeIf { it in 0..OpenedLogic.MAX_DAYS },
        )
    }

    fun entry(id: String, m: Map<String, Any?>): StockEntryEntity? {
        val productId = m.str("productId") ?: return null
        val locationId = m.str("locationId") // null = noch keinem Ort zugeordnet
        val typeId = m.str("quantityTypeId") ?: return null
        val quantity = m.long(FIELD_QUANTITY)?.toInt()?.takeIf { it > 0 } ?: return null
        return StockEntryEntity(
            id = id,
            productId = productId,
            locationId = locationId,
            level = m.long("level")?.toInt()?.takeIf { it >= 0 },
            shelf = m.long("shelf")?.toInt()?.takeIf { it >= 0 },
            expiryDate = m.str("expiryDate")?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            quantity = quantity,
            quantityTypeId = typeId,
            note = m.str("note"),
            openedAt = m.str("openedAt")?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            openedDays = m.long("openedDays")?.toInt()?.takeIf { it in 0..OpenedLogic.MAX_DAYS },
            frozenAt = m.str("frozenAt")?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            nfcTagId = m.str("nfcTagId")?.takeIf(String::isNotBlank),
        )
    }

    fun history(id: String, m: Map<String, Any?>): HistoryEventEntity? {
        val createdAt = m.long("createdAt") ?: return null
        val action = m.str("action")?.let { a -> HistoryAction.entries.firstOrNull { it.name == a } } ?: return null
        return HistoryEventEntity(
            id = id,
            createdAt = createdAt,
            action = action,
            authorUid = m.str("authorUid"),
            authorName = m.str("authorName")?.trim()?.take(MAX_NAME)?.takeIf(String::isNotBlank),
            productId = m.str("productId"),
            productName = m.str("productName")?.take(MAX_TEXT).orEmpty(),
            quantity = m.long("quantity")?.toInt()?.takeIf { it > 0 },
            quantityTypeSymbol = m.str("quantityTypeSymbol"),
            entryId = m.str("entryId"),
            outOfStock = m["outOfStock"] as? Boolean ?: false,
            message = m.str("message")?.trim()?.take(MAX_TEXT)?.takeIf(String::isNotBlank),
            notify = m["notify"] as? Boolean ?: false,
        )
    }

    /** Längen-Obergrenzen für Texte im Verlauf (Benachrichtigungen bleiben lesbar). */
    const val MAX_NAME = 40
    const val MAX_TEXT = 200

    fun shopping(id: String, m: Map<String, Any?>): ShoppingItemEntity? {
        val name = m.str("name")?.takeIf(String::isNotBlank) ?: return null
        return ShoppingItemEntity(
            id = id,
            productId = m.str("productId")?.takeIf(String::isNotBlank),
            name = name,
            quantity = m.long("quantity")?.toInt()?.takeIf { it in 1..MAX_COUNT } ?: 1,
            note = m.str("note"),
            createdAt = m.long("createdAt") ?: 0,
        )
    }

    /** Obergrenze für Mengen (Mindestbestand, Einkaufsliste). */
    const val MAX_COUNT = 999

    /** Nur einfache Dateinamen (kein Pfad) – Bilder werden unter diesem Namen lokal abgelegt. */
    fun isSafeFileName(name: String): Boolean = SAFE_NAME.matches(name)

    private val SAFE_NAME = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")

    private fun Map<String, Any?>.str(key: String): String? = this[key] as? String

    private fun Map<String, Any?>.long(key: String): Long? = (this[key] as? Number)?.toLong()
}
