package de.frelina.vorrat.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Zutat eines Rezepts. [group] = ID einer [IngredientGroups]-Gruppe; ohne Gruppe zählt sie als neutral. */
@Serializable
data class RecipeIngredient(
    val name: String,
    val amount: String? = null,
    val group: String? = null,
    val optional: Boolean = false,
)

/**
 * Gemeinsames Modell für eingebaute (`builtin:…`, aus assets/recipes.json) und eigene Rezepte (Room/Firestore).
 */
@Serializable
data class Recipe(
    val id: String,
    val title: String,
    val minutes: Int? = null,
    val servings: Int? = null,
    val ingredients: List<RecipeIngredient> = emptyList(),
    val steps: List<String> = emptyList(),
    val vegetarian: Boolean = false,
) {
    val builtin: Boolean get() = id.startsWith(BUILTIN_PREFIX)

    companion object {
        const val BUILTIN_PREFIX = "builtin:"
    }
}

/** Lesen/Schreiben der Rezept-Daten (JSON in assets/recipes.json und in der Spalte `ingredients`). */
object RecipeJson {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    /** Liste der eingebauten Rezepte; Rezepte ohne Titel oder ohne Zutaten werden übersprungen. */
    fun parseRecipes(text: String): List<Recipe> =
        json.decodeFromString<List<Recipe>>(text).filter { it.title.isNotBlank() && it.ingredients.isNotEmpty() }

    fun encodeIngredients(list: List<RecipeIngredient>): String = json.encodeToString(list)

    /** Fehlerhaftes JSON ergibt eine leere Liste. */
    fun decodeIngredients(text: String): List<RecipeIngredient> =
        runCatching { json.decodeFromString<List<RecipeIngredient>>(text) }.getOrDefault(emptyList())
            .filter { it.name.isNotBlank() }

    /** Freitext (eine Zeile = ein Schritt) ↔ Liste. */
    fun stepsToText(steps: List<String>): String = steps.joinToString("\n")

    fun textToSteps(text: String): List<String> = text.lines().map(String::trim).filter(String::isNotEmpty)
}
