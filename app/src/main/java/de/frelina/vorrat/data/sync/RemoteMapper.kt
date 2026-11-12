package de.frelina.vorrat.data.sync

import de.frelina.vorrat.data.db.DepartmentAssignmentEntity
import de.frelina.vorrat.data.db.DepartmentEntity
import de.frelina.vorrat.data.db.HistoryAction
import de.frelina.vorrat.data.db.HistoryEventEntity
import de.frelina.vorrat.data.db.LocationEntity
import de.frelina.vorrat.data.db.MealPlanEntity
import de.frelina.vorrat.data.db.ProductEntity
import de.frelina.vorrat.data.db.ProductSource
import de.frelina.vorrat.data.db.ShoppingItemEntity
import de.frelina.vorrat.domain.OpenedLogic
import de.frelina.vorrat.data.db.QuantityTypeEntity
import de.frelina.vorrat.data.db.RecipeEntity
import de.frelina.vorrat.data.db.StapleEntity
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
        "ingredientGroup" to p.ingredientGroup,
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
        "packageSize" to s.packageSize?.toPlainString(),
        "unitSymbol" to s.unitSymbol,
        "recipes" to s.recipes,
    )

    fun toMap(r: RecipeEntity): Map<String, Any?> = mapOf(
        "title" to r.title,
        "minutes" to r.minutes?.toLong(),
        "servings" to r.servings?.toLong(),
        "ingredients" to r.ingredients,
        "steps" to r.steps,
        "vegetarian" to r.vegetarian,
        "createdAt" to r.createdAt,
        "updatedAt" to r.updatedAt,
    )

    fun toMap(d: DepartmentEntity): Map<String, Any?> = mapOf(
        "name" to d.name,
        "position" to d.position.toLong(),
    )

    fun toMap(a: DepartmentAssignmentEntity): Map<String, Any?> = mapOf(
        "departmentId" to a.departmentId,
        "updatedAt" to a.updatedAt,
    )

    fun toMap(e: MealPlanEntity): Map<String, Any?> = mapOf(
        "date" to e.date.toString(),
        "recipeId" to e.recipeId,
        "persons" to e.persons.toLong(),
        "createdAt" to e.createdAt,
    )

    fun toMap(s: StapleEntity): Map<String, Any?> = mapOf(
        "label" to s.label,
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
            ingredientGroup = m.str("ingredientGroup")?.takeIf { GROUP_ID.matches(it) },
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
            packageSize = m.str("packageSize")?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 },
            unitSymbol = m.str("unitSymbol")?.trim()?.take(MAX_UNIT)?.takeIf(String::isNotBlank),
            recipes = m.str("recipes")?.take(MAX_PLAN_NOTE)?.takeIf(String::isNotBlank),
        )
    }

    fun recipe(id: String, m: Map<String, Any?>): RecipeEntity? {
        val title = m.str("title")?.trim()?.take(MAX_TITLE)?.takeIf(String::isNotBlank) ?: return null
        val ingredients = m.str("ingredients")?.takeIf { it.length <= MAX_INGREDIENTS_JSON } ?: return null
        return RecipeEntity(
            id = id,
            title = title,
            minutes = m.long("minutes")?.toInt()?.takeIf { it in 1..MAX_MINUTES },
            servings = m.long("servings")?.toInt()?.takeIf { it in 1..MAX_SERVINGS },
            ingredients = ingredients,
            steps = m.str("steps").orEmpty().take(MAX_STEPS),
            vegetarian = m["vegetarian"] as? Boolean ?: false,
            createdAt = m.long("createdAt") ?: 0,
            updatedAt = m.long("updatedAt") ?: 0,
        )
    }

    fun department(id: String, m: Map<String, Any?>): DepartmentEntity? {
        val name = m.str("name")?.trim()?.take(MAX_DEPARTMENT_NAME)?.takeIf(String::isNotBlank) ?: return null
        return DepartmentEntity(id = id, name = name, position = m.long("position")?.toInt() ?: Int.MAX_VALUE)
    }

    /** Die ID ist der Zuordnungsschlüssel ("product:…" oder "name:…"); andere Schlüssel werden ignoriert. */
    fun departmentAssignment(key: String, m: Map<String, Any?>): DepartmentAssignmentEntity? {
        if (!isValidAssignmentKey(key)) return null
        val departmentId = m.str("departmentId")?.takeIf(String::isNotBlank) ?: return null
        return DepartmentAssignmentEntity(key = key, departmentId = departmentId, updatedAt = m.long("updatedAt") ?: 0)
    }

    fun mealPlan(id: String, m: Map<String, Any?>): MealPlanEntity? {
        val date = m.str("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val recipeId = m.str("recipeId")?.takeIf { it.isNotBlank() && it.length <= MAX_ID_LENGTH } ?: return null
        return MealPlanEntity(
            id = id,
            date = date,
            recipeId = recipeId,
            persons = m.long("persons")?.toInt()?.takeIf { it in 1..MAX_PLAN_PERSONS } ?: 1,
            createdAt = m.long("createdAt") ?: 0,
        )
    }

    /** Die ID ist der Schlüssel der "Immer zuhause"-Zutat (normalisierter Name oder "group:<id>"). */
    fun staple(key: String, m: Map<String, Any?>): StapleEntity? {
        if (!isValidStapleKey(key)) return null
        val label = m.str("label")?.trim()?.take(MAX_STAPLE_LABEL)?.takeIf(String::isNotBlank) ?: return null
        return StapleEntity(key = key, label = label, createdAt = m.long("createdAt") ?: 0)
    }

    /** Obergrenzen für Abteilungen, Wochenplan und "Immer zuhause". */
    const val MAX_DEPARTMENT_NAME = 40
    const val MAX_PLAN_PERSONS = 99
    const val MAX_STAPLE_LABEL = 60
    const val MAX_PLAN_NOTE = 300
    const val MAX_UNIT = 12
    private const val MAX_ID_LENGTH = 200
    private const val MAX_KEY_LENGTH = 160

    /** Dokument-IDs dürfen kein "/" enthalten; Schlüssel haben ein festes Präfix. */
    fun isValidAssignmentKey(key: String): Boolean =
        key.length in 6..MAX_KEY_LENGTH && '/' !in key && (key.startsWith("product:") || key.startsWith("name:"))

    fun isValidStapleKey(key: String): Boolean =
        key.isNotBlank() && key.length <= MAX_KEY_LENGTH && '/' !in key && key.trim() == key

    /** Obergrenzen für Rezepte (Firestore-Dokument ≤ 1 MiB, Anzeige bleibt handhabbar). */
    const val MAX_TITLE = 100
    const val MAX_STEPS = 8000
    const val MAX_INGREDIENTS_JSON = 20000
    const val MAX_MINUTES = 9999
    const val MAX_SERVINGS = 999

    private val GROUP_ID = Regex("^[a-z][a-z0-9_]{0,39}$")

    /** Obergrenze für Mengen (Mindestbestand, Einkaufsliste). */
    const val MAX_COUNT = 999

    /** Nur einfache Dateinamen (kein Pfad) – Bilder werden unter diesem Namen lokal abgelegt. */
    fun isSafeFileName(name: String): Boolean = SAFE_NAME.matches(name)

    private val SAFE_NAME = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")

    private fun Map<String, Any?>.str(key: String): String? = this[key] as? String

    private fun Map<String, Any?>.long(key: String): Long? = (this[key] as? Number)?.toLong()
}
