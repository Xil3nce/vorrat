package de.frelina.vorrat.domain

import de.frelina.vorrat.data.db.DepartmentEntity

/**
 * Standard-Abteilungen der Einkaufsliste. Die IDs sind FEST (nie ändern): Legen zwei Handys gleichzeitig die
 * Standard-Abteilungen an, schreiben beide dieselben Dokumente (idempotenter Upsert) statt Dubletten zu erzeugen.
 * Die Namen sind Startwerte und im Haushalt umbenennbar ("Auf Standard zurücksetzen" stellt sie wieder her).
 */
object DepartmentDefaults {
    const val PRODUCE = "dept_produce"
    const val BAKERY = "dept_bakery"
    const val DAIRY = "dept_dairy"
    const val MEAT = "dept_meat"
    const val CHEESE = "dept_cheese"
    const val PASTA = "dept_pasta"
    const val CANNED = "dept_canned"
    const val BAKING = "dept_baking"
    const val BREAKFAST = "dept_breakfast"
    const val DRINKS = "dept_drinks"
    const val FROZEN = "dept_frozen"
    const val HOUSEHOLD = "dept_household"

    /** Auffangabteilung ("Sonstiges"): nicht löschbar. */
    const val OTHER = "dept_other"

    class Def(val id: String, val name: String)

    /** In der Standardreihenfolge (Gang durch den Laden). */
    val all: List<Def> = listOf(
        Def(PRODUCE, "Obst & Gemüse"),
        Def(BAKERY, "Brot & Backwaren"),
        Def(DAIRY, "Milchprodukte & Kühlregal"),
        Def(MEAT, "Fleisch, Wurst & Fisch"),
        Def(CHEESE, "Käse"),
        Def(PASTA, "Nudeln, Reis & Beilagen"),
        Def(CANNED, "Konserven & Soßen"),
        Def(BAKING, "Backen & Gewürze"),
        Def(BREAKFAST, "Frühstück & Süßes"),
        Def(DRINKS, "Getränke"),
        Def(FROZEN, "Tiefkühl"),
        Def(HOUSEHOLD, "Drogerie & Haushalt"),
        Def(OTHER, "Sonstiges"),
    )

    val ids: Set<String> = all.map { it.id }.toSet()

    fun isDefault(id: String): Boolean = id in ids

    fun entities(): List<DepartmentEntity> = all.mapIndexed { i, d -> DepartmentEntity(d.id, d.name, i) }

    fun nameOf(id: String): String? = all.firstOrNull { it.id == id }?.name

    /** Standard-Abteilung je Zutatengruppe (für alle Gruppen aus [IngredientGroups]). */
    private val groupDefaults: Map<String, String> = buildMap<String, String> {
        fun assign(department: String, vararg groups: String) = groups.forEach { this[it] = department }
        assign(
            PRODUCE, "potatoes", "tomatoes", "bell_pepper", "onions", "garlic", "zucchini", "eggplant", "pumpkin",
            "carrots", "mushrooms", "spinach", "broccoli", "cauliflower", "cabbage", "leek", "cucumber", "salad",
            "apples", "bananas", "lemons", "berries", "herbs",
        )
        assign(BAKERY, "bread", "toast", "tortilla")
        assign(
            DAIRY, "cream", "sour_cream", "creme_fraiche", "milk", "eggs", "butter", "yogurt", "quark", "puff_pastry",
            "pizza_dough", "filled_pasta", "tofu",
        )
        assign(
            MEAT, "minced_meat", "chicken", "pork", "beef", "bacon", "ham", "sausage", "fish", "salmon",
        )
        assign(CHEESE, "cheese", "cream_cheese", "mozzarella", "parmesan", "feta")
        assign(PASTA, "pasta", "rice", "gnocchi", "noodles_asian", "spaetzle", "dumplings")
        assign(
            CANNED, "canned_tomatoes", "tomato_paste", "tomato_sauce", "tuna", "lentils", "chickpeas", "beans", "corn",
            "peas", "coconut_milk", "pesto", "soy_sauce", "curry_paste",
        )
        assign(BAKING, "flour", "sugar", "stock", "spices", "oil")
        assign(BREAKFAST, "oats", "honey", "chocolate")
        assign(DRINKS, "water")
        assign(FROZEN, "fish_sticks", "frozen_vegetables")
    }

    /** Standard-Abteilung einer Zutatengruppe oder null (unbekannte Gruppe). */
    fun forGroup(groupId: String?): String? = groupId?.let(groupDefaults::get)
}

/** Eintrag der Einkaufsliste für die Gruppierung: [payload] ist das Anzeigeobjekt der UI. */
data class BoardInput<T>(
    val key: String,
    val name: String,
    /** Produkt, falls bekannt (auch über den Namen gefunden). */
    val productId: String?,
    val category: String?,
    /** Manuell gewählte Zutatengruppe des Produkts. */
    val manualGroup: String?,
    val payload: T,
)

/**
 * Zeile der gruppierten Einkaufsliste: Überschrift ([payload] = null) oder Eintrag. Die Liste ist flach
 * (Überschrift, Einträge, Überschrift, …) – so lassen sich Einträge und Abteilungen per Drag & Drop verschieben.
 */
data class BoardRow<T>(val key: String, val departmentId: String, val payload: T?) {
    val isHeader: Boolean get() = payload == null
}

/** Bestimmt die Abteilung eines Listeneintrags und gruppiert/verschiebt Einträge (reine Funktionen). */
object DepartmentResolver {
    private const val MAX_NAME_KEY = 100

    fun productKey(productId: String): String = "product:$productId"

    /** Schlüssel über den normalisierten Namen; null, wenn der Name keine Zeichen enthält. */
    fun nameKey(name: String): String? =
        IngredientMatcher.normalize(name).take(MAX_NAME_KEY).trim().takeIf(String::isNotEmpty)?.let { "name:$it" }

    /** Schlüssel, unter dem eine neue Zuordnung gespeichert wird: Produkt, sonst Name. */
    fun assignmentKey(productId: String?, name: String): String? =
        productId?.let(::productKey) ?: nameKey(name)

    /**
     * Abteilung eines Eintrags: Zuordnung per Produkt, dann per Name, dann Standard der Zutatengruppe, sonst
     * "Sonstiges". Zuordnungen und Standards zu einer nicht (mehr) vorhandenen Abteilung zählen nicht.
     * @param assignments Schlüssel → Abteilungs-ID
     * @param departmentIds vorhandene Abteilungen
     */
    fun resolve(
        productId: String?,
        name: String,
        category: String?,
        manualGroup: String?,
        assignments: Map<String, String>,
        departmentIds: Set<String>,
    ): String {
        productId?.let { assignments[productKey(it)] }?.takeIf { it in departmentIds }?.let { return it }
        nameKey(name)?.let { assignments[it] }?.takeIf { it in departmentIds }?.let { return it }
        val group = effectiveIngredientGroup(manualGroup, name, category)
        DepartmentDefaults.forGroup(group)?.takeIf { it in departmentIds }?.let { return it }
        return DepartmentDefaults.OTHER
    }
}

/** Aufbau und Bearbeitung der flachen, nach Abteilungen gruppierten Liste. */
object ShoppingBoard {
    fun headerKey(departmentId: String): String = "d-$departmentId"

    /**
     * Gruppiert [inputs] nach Abteilung in der Reihenfolge von [departments]; leere Abteilungen entfallen,
     * Einträge einer Abteilung sind nach Name sortiert. Fehlt "Sonstiges" in [departments], steht es am Ende.
     */
    fun <T> build(
        departments: List<DepartmentEntity>,
        assignments: Map<String, String>,
        inputs: List<BoardInput<T>>,
    ): List<BoardRow<T>> {
        val ids = departments.map { it.id }.toSet()
        val byDepartment = inputs.groupBy {
            DepartmentResolver.resolve(it.productId, it.name, it.category, it.manualGroup, assignments, ids)
        }
        val order = departments.map { it.id } + if (DepartmentDefaults.OTHER !in ids) listOf(DepartmentDefaults.OTHER) else emptyList()
        val comparator = compareBy<BoardInput<T>, String>(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.key }
        return buildList {
            order.forEach { id ->
                val items = byDepartment[id]?.sortedWith(comparator) ?: return@forEach
                add(BoardRow(headerKey(id), id, null))
                items.forEach { add(BoardRow(it.key, id, it.payload)) }
            }
        }
    }

    /** Abteilung, zu der die Zeile [index] gehört: die nächste Überschrift darüber (oder die Zeile selbst). */
    fun <T> departmentAt(rows: List<BoardRow<T>>, index: Int): String? =
        (index downTo 0).firstNotNullOfOrNull { i -> rows.getOrNull(i)?.takeIf { it.isHeader }?.departmentId }

    /** Reihenfolge der Abteilungen (Überschriften) in der Liste. */
    fun <T> departmentOrder(rows: List<BoardRow<T>>): List<String> = rows.filter { it.isHeader }.map { it.departmentId }

    /**
     * Eintrag [from] an Position [to] ziehen (Drag & Drop). Er gehört danach zur Abteilung der Überschrift darüber.
     * null, wenn nichts passiert (kein Eintrag, gleiche Position, oberhalb der ersten Überschrift).
     */
    fun <T> moveEntry(rows: List<BoardRow<T>>, from: Int, to: Int): List<BoardRow<T>>? {
        if (from == to || from !in rows.indices || to !in rows.indices || rows[from].isHeader) return null
        if (to == 0) return null // nie oberhalb der ersten Überschrift
        val moved = rows.toMutableList()
        val row = moved.removeAt(from)
        moved.add(to, row)
        val department = departmentAt(moved, to) ?: return null
        moved[to] = row.copy(departmentId = department)
        return moved
    }

    /**
     * Abteilung samt Einträgen verschieben: Die Überschrift [from] wird über/unter die Überschrift [to] gesetzt.
     * Andere Ziele (Einträge) ergeben null.
     */
    fun <T> moveDepartment(rows: List<BoardRow<T>>, from: Int, to: Int): List<BoardRow<T>>? {
        if (from == to || from !in rows.indices || to !in rows.indices) return null
        if (!rows[from].isHeader || !rows[to].isHeader) return null
        val fromEnd = blockEnd(rows, from)
        val block = rows.subList(from, fromEnd).toList()
        val rest = rows.subList(0, from) + rows.subList(fromEnd, rows.size)
        val insertAt = if (to < from) {
            to
        } else {
            // hinter den Block der Ziel-Abteilung (in [rest] liegt er um die Blocklänge weiter vorn)
            blockEnd(rows, to) - block.size
        }
        return rest.subList(0, insertAt) + block + rest.subList(insertAt, rest.size)
    }

    private fun <T> blockEnd(rows: List<BoardRow<T>>, headerIndex: Int): Int {
        var i = headerIndex + 1
        while (i < rows.size && !rows[i].isHeader) i++
        return i
    }

    /**
     * Neue Gesamtreihenfolge, wenn nur die sichtbaren Abteilungen ([visibleNewOrder]) umsortiert wurden: Die
     * ausgeblendeten (leeren) behalten ihren Platz, die sichtbaren werden auf ihre Plätze neu verteilt.
     */
    fun mergeOrder(allOrdered: List<String>, visibleNewOrder: List<String>): List<String> {
        val visible = visibleNewOrder.toSet()
        val next = visibleNewOrder.iterator()
        val merged = allOrdered.map { id -> if (id in visible && next.hasNext()) next.next() else id }
        // Sichtbare, aber (noch) nicht gespeicherte Abteilungen (z. B. ein fehlendes "Sonstiges") kommen ans Ende.
        return merged + visibleNewOrder.filter { it !in merged }
    }
}
