package de.frelina.vorrat.domain

import de.frelina.vorrat.data.db.StockItem
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Bestandseintrag für den Rezeptabgleich: [group] ist die wirksame Zutatengruppe, [expiry] das wirksame
 * Ablaufdatum (bei geöffneten Packungen "verbrauchen bis", siehe [OpenedLogic.effectiveExpiry]).
 */
data class PantryEntry(
    val entryId: String,
    val productId: String,
    val productName: String,
    val group: String?,
    val expiry: LocalDate?,
    val quantity: Int,
)

/** Wirksame Gruppe eines Produkts: manuell gewählte, sonst automatisch erkannte (unbekannte IDs zählen nicht). */
fun effectiveIngredientGroup(manual: String?, name: String, category: String?): String? =
    manual?.takeIf(IngredientGroups::isKnown) ?: IngredientMatcher.match(name, category)

fun StockItem.toPantryEntry() = PantryEntry(
    entryId = entryId,
    productId = productId,
    productName = productName,
    group = effectiveIngredientGroup(ingredientGroup, productName, category),
    expiry = effectiveExpiry,
    quantity = quantity,
)

/** Vorhandene Zutat: der Bestandseintrag mit dem frühesten Ablaufdatum der Gruppe. */
data class HaveIngredient(
    val ingredient: RecipeIngredient,
    val groupId: String,
    val entryId: String,
    val productId: String,
    val productName: String,
    val expiry: LocalDate?,
    val quantity: Int,
)

data class RecipeMatch(
    val recipe: Recipe,
    val score: Double,
    val have: List<HaveIngredient>,
    /** Fehlende Pflichtzutaten. */
    val missing: List<RecipeIngredient>,
    /** Fehlende optionale Zutaten (zählen nicht negativ). */
    val missingOptional: List<RecipeIngredient>,
    /** Vorhandene Zutaten, die innerhalb von [RecipeMatcher.RESCUE_DAYS] Tagen ablaufen (oder schon abgelaufen sind). */
    val rescues: List<HaveIngredient>,
    /** Zutaten ohne Gruppe oder Grundvorrat (weder vorhanden noch fehlend). */
    val neutral: List<RecipeIngredient>,
)

/** Chip "läuft bald ab" in der Rezeptliste: eine Gruppe samt dem am frühesten ablaufenden Produkt. */
data class ExpiringGroup(val groupId: String, val productName: String, val expiry: LocalDate, val count: Int)

object RecipeMatcher {
    const val RESCUE_DAYS = 7
    const val MAX_MISSING = 2
    private const val MISSING_PENALTY = 1.5
    private const val OPTIONAL_BONUS = 0.5

    /** Wirksame Gruppe einer Rezeptzutat: gesetzte Gruppe, sonst automatisch aus dem Namen erkannt. */
    fun groupOf(ingredient: RecipeIngredient): String? =
        ingredient.group?.takeIf(IngredientGroups::isKnown) ?: IngredientMatcher.matchName(ingredient.name)

    /** Gewicht einer vorhandenen Zutat: je früher sie abläuft, desto wichtiger. */
    fun expiryWeight(expiry: LocalDate?, today: LocalDate): Double {
        if (expiry == null) return 1.0
        val days = ChronoUnit.DAYS.between(today, expiry)
        return when {
            days <= 1 -> 3.0
            days <= 3 -> 2.0
            days <= RESCUE_DAYS -> 1.5
            else -> 1.0
        }
    }

    /** Frühester Eintrag je Gruppe (ohne Datum zuletzt). */
    fun pantryByGroup(pantry: List<PantryEntry>): Map<String, PantryEntry> =
        pantry.filter { it.group != null }
            .groupBy { it.group!! }
            .mapValues { (_, entries) ->
                entries.minWith(compareBy<PantryEntry> { it.expiry == null }.thenBy { it.expiry }.thenBy { it.productName.lowercase() })
            }

    fun evaluate(recipe: Recipe, pantry: List<PantryEntry>, today: LocalDate): RecipeMatch =
        evaluate(recipe, pantryByGroup(pantry), today)

    fun evaluate(recipe: Recipe, byGroup: Map<String, PantryEntry>, today: LocalDate): RecipeMatch {
        var score = 0.0
        val have = mutableListOf<HaveIngredient>()
        val missing = mutableListOf<RecipeIngredient>()
        val missingOptional = mutableListOf<RecipeIngredient>()
        val neutral = mutableListOf<RecipeIngredient>()
        recipe.ingredients.forEach { ingredient ->
            val group = groupOf(ingredient)
            if (group == null || IngredientGroups.isBasic(group)) {
                neutral += ingredient
                return@forEach
            }
            val entry = byGroup[group]
            if (entry != null) {
                score += if (ingredient.optional) OPTIONAL_BONUS else expiryWeight(entry.expiry, today)
                have += HaveIngredient(
                    ingredient, group, entry.entryId, entry.productId, entry.productName, entry.expiry, entry.quantity,
                )
            } else if (ingredient.optional) {
                missingOptional += ingredient
            } else {
                score -= MISSING_PENALTY
                missing += ingredient
            }
        }
        val rescues = have.filter { h ->
            h.expiry != null && ChronoUnit.DAYS.between(today, h.expiry) <= RESCUE_DAYS
        }
        return RecipeMatch(recipe, score, have, missing, missingOptional, rescues, neutral)
    }

    private val ORDER = compareByDescending<RecipeMatch> { it.score }
        .thenBy { it.missing.size }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.recipe.title }

    /**
     * Vorschläge: mehr als [MAX_MISSING] fehlende Pflichtzutaten oder keine einzige vorhandene Zutat blendet
     * das Rezept aus. Sortierung: Punkte absteigend, weniger fehlend, Titel.
     */
    fun rank(recipes: List<Recipe>, pantry: List<PantryEntry>, today: LocalDate): List<RecipeMatch> {
        val byGroup = pantryByGroup(pantry)
        return recipes.map { evaluate(it, byGroup, today) }
            .filter { it.have.isNotEmpty() && it.missing.size <= MAX_MISSING }
            .sortedWith(ORDER)
    }

    /** Alle Rezepte (auch ohne Treffer), gleich sortiert. */
    fun rankAll(recipes: List<Recipe>, pantry: List<PantryEntry>, today: LocalDate): List<RecipeMatch> {
        val byGroup = pantryByGroup(pantry)
        return recipes.map { evaluate(it, byGroup, today) }.sortedWith(ORDER)
    }

    /**
     * Bald ablaufende Gruppen (Ablauf innerhalb von [leadDays] Tagen oder schon abgelaufen) für die Chips,
     * frühestes Ablaufdatum zuerst. Produkte ohne Gruppe tauchen nicht auf (sie passen zu keinem Rezept).
     */
    fun expiringGroups(pantry: List<PantryEntry>, today: LocalDate, leadDays: Int): List<ExpiringGroup> {
        val limit = today.plusDays(leadDays.coerceAtLeast(0).toLong())
        return pantry.filter { it.group != null && it.expiry != null && !it.expiry.isAfter(limit) }
            .groupBy { it.group!! }
            .map { (group, entries) ->
                val first = entries.minWith(compareBy<PantryEntry> { it.expiry }.thenBy { it.productName.lowercase() })
                ExpiringGroup(group, first.productName, first.expiry!!, entries.size)
            }
            .sortedWith(compareBy<ExpiringGroup> { it.expiry }.thenBy { it.productName.lowercase() })
    }

    /** Tage bis zum Ablauf (negativ = abgelaufen). */
    fun daysUntil(expiry: LocalDate, today: LocalDate): Long = ChronoUnit.DAYS.between(today, expiry)
}
