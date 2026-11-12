package de.frelina.vorrat.domain

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/** Erkennt Chefkoch-Rezeptlinks, z. B. "https://www.chefkoch.de/rezepte/2291331365346705/Lasagne.html". */
object ChefkochLink {
    private val PATTERN = Regex("""chefkoch\.de/(?:amp/)?rezepte/(?:drucken/)?(\d{3,20})""", RegexOption.IGNORE_CASE)

    /** Rezept-ID aus einem Link oder geteilten Text (der Link darf mitten im Text stehen); sonst null. */
    fun recipeId(text: String?): String? = text?.let { PATTERN.find(it)?.groupValues?.get(1) }
}

/** Ausschnitt der Chefkoch-Rezeptantwort (api.chefkoch.de/v2/recipes/{id}); unbekannte Felder werden ignoriert. */
@Serializable
data class ChefkochRecipeDto(
    val id: String = "",
    val title: String = "",
    val servings: Int? = null,
    val preparationTime: Int? = null,
    val cookingTime: Int? = null,
    val totalTime: Int? = null,
    val instructions: String? = null,
    val ingredientGroups: List<ChefkochIngredientGroupDto> = emptyList(),
    val fullTags: List<ChefkochTagDto> = emptyList(),
    val siteUrl: String? = null,
    val hasImage: Boolean = false,
    /** z. B. "https://img.chefkoch-cdn.de/rezepte/{id}/bilder/{bild}/<format>/titel.jpg". */
    val previewImageUrlTemplate: String? = null,
    val previewImageCredits: String? = null,
)

@Serializable
data class ChefkochIngredientGroupDto(
    val header: String? = null,
    val ingredients: List<ChefkochIngredientDto> = emptyList(),
)

@Serializable
data class ChefkochIngredientDto(
    val name: String = "",
    val unit: String? = null,
    val amount: Double? = null,
    val usageInfo: String? = null,
)

@Serializable
data class ChefkochTagDto(val id: Int? = null, val name: String = "")

/** Wandelt ein Chefkoch-Rezept in das App-Modell um (Zutatengruppen werden wie im Editor automatisch zugeordnet). */
object ChefkochMapper {
    private const val MAX_TITLE = 100
    private const val MAX_NAME = 80
    private const val MAX_AMOUNT = 40
    private const val MAX_STEPS_CHARS = 8000
    private val VEGETARIAN_TAGS = setOf("vegetarisch", "vegan")

    fun toRecipe(dto: ChefkochRecipeDto): Recipe {
        val ingredients = dto.ingredientGroups.flatMap { it.ingredients }.mapNotNull { ing ->
            val baseName = ing.name.trim()
            if (baseName.isEmpty()) return@mapNotNull null
            val usage = ing.usageInfo?.trim().orEmpty()
            val name = when {
                usage.isEmpty() -> baseName
                usage.startsWith(",") -> "$baseName$usage"
                else -> "$baseName, $usage"
            }.take(MAX_NAME)
            RecipeIngredient(
                name = name,
                amount = formatAmount(ing.amount, ing.unit)?.take(MAX_AMOUNT),
                group = IngredientMatcher.matchName(baseName),
            )
        }
        val minutes = dto.totalTime?.takeIf { it > 0 }
            ?: ((dto.preparationTime ?: 0) + (dto.cookingTime ?: 0)).takeIf { it > 0 }
        val steps = RecipeJson.textToSteps(dto.instructions.orEmpty().take(MAX_STEPS_CHARS))
        val source = dto.siteUrl?.takeIf { it.isNotBlank() } ?: "https://www.chefkoch.de/rezepte/${dto.id}"
        val credits = dto.previewImageCredits?.trim()?.takeIf { it.isNotEmpty() && imageUrl(dto) != null }
        return Recipe(
            id = "",
            title = dto.title.trim().take(MAX_TITLE),
            minutes = minutes,
            servings = dto.servings?.takeIf { it > 0 },
            ingredients = ingredients,
            steps = steps + listOfNotNull("Quelle: $source", credits?.let { "Bild: ${it.take(MAX_NAME)}" }),
            vegetarian = dto.fullTags.any { it.name.trim().lowercase() in VEGETARIAN_TAGS },
        )
    }

    /**
     * Adresse des Vorschaubilds in mittlerer Größe; null ohne Bild oder bei unerwarteter Adresse
     * (nur https auf dem Chefkoch-Bildserver).
     */
    fun imageUrl(dto: ChefkochRecipeDto): String? {
        val template = dto.previewImageUrlTemplate?.trim()
        if (!dto.hasImage || template.isNullOrEmpty() || !template.contains(FORMAT_PLACEHOLDER)) return null
        if (!template.startsWith(IMAGE_HOST)) return null
        return template.replace(FORMAT_PLACEHOLDER, IMAGE_FORMAT)
    }

    private const val IMAGE_HOST = "https://img.chefkoch-cdn.de/"
    private const val FORMAT_PLACEHOLDER = "<format>"
    private const val IMAGE_FORMAT = "crop-960x720"

    /**
     * Menge im Format, das [AmountScaler] versteht: 400.0 + "g" → "400 g", 0.5 + "Dose" → "½ Dose",
     * 1.5 → "1 ½", 0.3 → "0,3"; ohne Menge nur die Einheit ("etwas"), ohne beides null.
     */
    fun formatAmount(amount: Double?, unit: String?): String? {
        val u = unit?.trim().orEmpty()
        val number = amount?.takeIf { it > 0 }?.let(::formatNumber)
        return listOfNotNull(number, u.ifEmpty { null }).joinToString(" ").ifEmpty { null }
    }

    private fun formatNumber(value: Double): String {
        val whole = floor(value).toLong()
        val rest = value - whole
        val fraction = FRACTIONS.entries.firstOrNull { abs(it.key - rest) < 0.01 }?.value
        return when {
            rest < 0.01 -> whole.toString()
            rest > 0.99 -> (whole + 1).toString()
            fraction != null -> if (whole == 0L) fraction else "$whole $fraction"
            else -> ((value * 100).roundToLong() / 100.0).toString().trimEnd('0').trimEnd('.').replace('.', ',')
        }
    }

    private val FRACTIONS = linkedMapOf(0.25 to "¼", 0.5 to "½", 0.75 to "¾", 1.0 / 3 to "⅓", 2.0 / 3 to "⅔")
}
