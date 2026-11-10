package de.frelina.vorrat.domain

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** Geplantes Rezept mit der Personenzahl, für die gekocht wird. */
data class PlannedMeal(val recipe: Recipe, val persons: Int)

/** Warum eine Zutat nicht vorausgewählt ist. */
sealed interface LineHint {
    /** Im Bestand vorhanden. */
    data class InPantry(val productName: String) : LineHint

    /** Steht schon auf der Einkaufsliste. */
    data object OnList : LineHint

    /** Auf der Liste "Immer zuhause". */
    data object Staple : LineHint

    /** Grundvorrat (Salz, Gewürze, Öl …) bzw. klingt danach. */
    data object Basic : LineHint
}

/**
 * Zeile der Prüfliste "Zutaten auf die Einkaufsliste".
 * @param key Gruppe ("group:<id>") oder normalisierter Name – zugleich der Schlüssel in "Immer zuhause"
 * @param recipes Titel der Rezepte, für die die Zutat gebraucht wird
 * @param checked Vorauswahl (kaufen)
 * @param hint Grund, falls nicht vorausgewählt
 */
data class PlanLine(
    val key: String,
    val name: String,
    val amount: String?,
    val recipes: List<String>,
    val optional: Boolean,
    val checked: Boolean,
    val hint: LineHint?,
    val groupId: String?,
) {
    /** Notiz für den Eintrag auf der Einkaufsliste: Menge und Rezepte. */
    fun note(): String? =
        listOfNotNull(amount?.takeIf(String::isNotBlank), recipes.joinToString(", ").takeIf(String::isNotEmpty))
            .joinToString(" · ").takeIf(String::isNotEmpty)
}

/**
 * Zutaten der geplanten Rezepte für die Einkaufsliste: auf die Personenzahl umrechnen, gleiche Zutaten
 * zusammenfassen (gleiche Gruppe, sonst gleicher normalisierter Name), Mengen summieren und vorauswählen, was
 * wirklich gekauft werden muss.
 */
object MealPlanShopping {

    /** Schlüssel für "Immer zuhause": Gruppe ("group:<id>") oder normalisierter Name; null ohne verwertbaren Namen. */
    fun stapleKey(groupId: String?, name: String): String? =
        groupId?.let { "group:$it" } ?: IngredientMatcher.normalize(name).takeIf(String::isNotEmpty)

    private class Acc(val key: String, val groupId: String?) {
        val names = mutableListOf<String>()
        val amounts = mutableListOf<String?>()
        val recipes = linkedSetOf<String>()
        var allOptional = true
    }

    /**
     * @param pantry Bestand wie im Rezeptabgleich
     * @param staples Schlüssel der Liste "Immer zuhause"
     * @param listNames Namen der Einträge, die schon auf der Einkaufsliste stehen
     */
    fun build(
        meals: List<PlannedMeal>,
        pantry: List<PantryEntry>,
        staples: Set<String>,
        listNames: Collection<String>,
    ): List<PlanLine> {
        val accs = linkedMapOf<String, Acc>()
        meals.forEach { meal ->
            val factor = AmountScaler.factor(meal.recipe.servings, meal.persons)
            meal.recipe.ingredients.forEach { ingredient ->
                val name = ingredient.name.trim()
                val group = RecipeMatcher.groupOf(ingredient)
                val key = stapleKey(group, name) ?: return@forEach
                val acc = accs.getOrPut(key) { Acc(key, group) }
                acc.names += name
                acc.amounts += AmountScaler.scale(ingredient.amount, factor)
                acc.recipes += meal.recipe.title
                if (!ingredient.optional) acc.allOptional = false
            }
        }

        val byGroup = RecipeMatcher.pantryByGroup(pantry)
        val listNorm = listNames.map(IngredientMatcher::normalize).filter(String::isNotEmpty).toSet()
        val listGroups = listNames.mapNotNull(IngredientMatcher::matchName).toSet()

        return accs.values.map { acc ->
            val displayName = displayName(acc)
            val amount = combineAmounts(acc.amounts)
            val normalizedNames = acc.names.map(IngredientMatcher::normalize)
            val hint = when {
                acc.key in staples || normalizedNames.any { it in staples } -> LineHint.Staple
                normalizedNames.any { it in listNorm } || (acc.groupId != null && acc.groupId in listGroups) -> LineHint.OnList
                acc.groupId != null && byGroup[acc.groupId] != null ->
                    LineHint.InPantry(byGroup.getValue(acc.groupId).productName)
                IngredientGroups.isBasic(acc.groupId) -> LineHint.Basic
                acc.groupId == null && soundsLikeStaple(acc.names, acc.amounts) -> LineHint.Basic
                else -> null
            }
            PlanLine(
                key = acc.key,
                name = displayName,
                amount = amount,
                recipes = acc.recipes.toList(),
                optional = acc.allOptional,
                checked = hint == null && !acc.allOptional,
                hint = hint,
                groupId = acc.groupId,
            )
        }
    }

    /** Ein Name, wenn alle gleich lauten; sonst (verschiedene Namen derselben Gruppe) der Gruppenname. */
    private fun displayName(acc: Acc): String {
        val distinct = acc.names.map(IngredientMatcher::normalize).distinct()
        return if (distinct.size > 1 && acc.groupId != null) {
            IngredientGroups.label(acc.groupId) ?: acc.names.first()
        } else {
            acc.names.first()
        }
    }

    // ------------------------------------------------------------------ Grundvorrat ohne Gruppe

    private val STAPLE_WORDS = listOf(
        "salz", "pfeffer", "gewuerz", "wuerze", "maggi", "zucker", "mehl", "essig", "backpulver", "natron", "hefe",
        "vanille", "muskat", "zimt", "oregano", "paprikapulver", "currypulver", "kreuzkuemmel", "lorbeer", "speisestaerke",
    )
    private val STAPLE_AMOUNTS = listOf("prise", "nach geschmack", "abschmecken", "etwas", "schuss", "spritzer", "bei bedarf")

    /** Klingt nach Grundvorrat (Salz, Pfeffer, Gewürze …) – für Zutaten ohne erkannte Gruppe. */
    fun soundsLikeStaple(names: List<String>, amounts: List<String?> = emptyList()): Boolean {
        val n = names.joinToString(" ") { IngredientMatcher.normalize(it) }
        if (STAPLE_WORDS.any { it in n }) return true
        val a = amounts.filterNotNull().joinToString(" ") { IngredientMatcher.normalize(it) }
        return STAPLE_AMOUNTS.any { it in a }
    }

    // ------------------------------------------------------------------ Mengen

    private class Quantity(val value: Double, val unit: String, val unitText: String)

    private val AMOUNT = Regex("""^\s*(\d+\s*/\s*\d+|\d+(?:[.,]\d+)?\s*[½¼¾⅓⅔]?|[½¼¾⅓⅔])\s*(.*)$""")
    private val FRACTIONS = mapOf('½' to 0.5, '¼' to 0.25, '¾' to 0.75, '⅓' to 1.0 / 3, '⅔' to 2.0 / 3)
    private val UNIT_ALIASES = mapOf(
        "gramm" to "g", "kilo" to "kg", "kilogramm" to "kg", "milliliter" to "ml", "liter" to "l",
        "stk" to "", "stk." to "", "stueck" to "", "stück" to "", "st" to "", "st." to "",
        "esslöffel" to "el", "teelöffel" to "tl",
    )
    private val MASS = mapOf("g" to 1.0, "kg" to 1000.0)
    private val VOLUME = mapOf("ml" to 1.0, "cl" to 10.0, "dl" to 100.0, "l" to 1000.0)

    private fun parse(text: String): Quantity? {
        val m = AMOUNT.find(text) ?: return null
        val rest = m.groupValues[2].trim()
        // Spannen ("2-3 EL") und weitere Zahlen ("1 1/2 TL") bleiben Text.
        if (rest.firstOrNull()?.let { it.isDigit() || it == '-' || it == '–' || it == '/' } == true) return null
        val value = parseNumber(m.groupValues[1]) ?: return null
        val key = AmountScaler.adjustUnit(" " + rest.replaceFirstChar { it.uppercase() }, false).trim().lowercase(Locale.GERMAN)
        return Quantity(value, UNIT_ALIASES[key] ?: key, rest)
    }

    private fun parseNumber(raw: String): Double? {
        val s = raw.replace(" ", "")
        if ('/' in s) {
            val (n, d) = s.split('/').map { it.toDoubleOrNull() ?: return null }
            return if (d == 0.0) null else n / d
        }
        val frac = s.lastOrNull()?.let(FRACTIONS::get)
        val head = if (frac != null) s.dropLast(1) else s
        val whole = if (head.isEmpty()) 0.0 else head.replace(',', '.').toDoubleOrNull() ?: return null
        return whole + (frac ?: 0.0)
    }

    /**
     * Fasst Mengenangaben zusammen: gleiche Einheit wird addiert (g/kg und ml/l auch untereinander), alles
     * andere – auch Angaben ohne Zahl ("etwas") – bleibt als Text und wird mit " + " verbunden.
     */
    fun combineAmounts(amounts: List<String?>): String? {
        val sums = linkedMapOf<String, Double>()
        val units = linkedMapOf<String, String>()
        val texts = linkedSetOf<String>()
        val order = mutableListOf<String>() // "q:<familie>" oder "t:<text>" in Reihenfolge des Auftretens
        amounts.filterNotNull().map(String::trim).filter(String::isNotEmpty).forEach { text ->
            val q = parse(text)
            if (q == null) {
                if (texts.add(text)) order += "t:$text"
                return@forEach
            }
            val family = when {
                q.unit in MASS -> "mass"
                q.unit in VOLUME -> "volume"
                else -> "u:${q.unit}"
            }
            val base = q.value * (MASS[q.unit] ?: VOLUME[q.unit] ?: 1.0)
            if (family !in sums) {
                order += "q:$family"
                units[family] = q.unitText
            }
            sums[family] = (sums[family] ?: 0.0) + base
        }
        val result = order.map { entry ->
            val kind = entry.substring(0, 1)
            val id = entry.substring(2)
            if (kind == "t") id else formatSum(id, sums.getValue(id), units.getValue(id))
        }
        return result.joinToString(" + ").takeIf(String::isNotEmpty)
    }

    private fun formatSum(family: String, base: Double, firstUnitText: String): String = when (family) {
        "mass" -> if (base >= 1000) number(base / 1000) + " kg" else number(base) + " g"
        "volume" -> if (base >= 1000) number(base / 1000) + " l" else number(base) + " ml"
        else -> {
            val many = base > 1.0 + 1e-9
            val unit = AmountScaler.adjustUnit(" $firstUnitText", many).trim()
            if (unit.isEmpty()) number(base) else number(base) + " " + unit
        }
    }

    private fun number(value: Double): String {
        val rounded = (value * 100).roundToLong() / 100.0
        return if (abs(rounded - rounded.roundToLong()) < 1e-9) {
            rounded.roundToLong().toString()
        } else {
            String.format(Locale.GERMAN, "%.2f", rounded).trimEnd('0').trimEnd(',')
        }
    }
}
