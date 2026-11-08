package de.frelina.vorrat.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Nährwerte je 100 g bzw. 100 ml (bei Getränken, siehe [perMl]) aus Open Food Facts.
 * Jeder Wert ist optional – OFF-Daten sind oft unvollständig; fehlende Werte werden nicht angezeigt.
 */
@Serializable
data class Nutrition(
    val energyKj: Double? = null,
    val energyKcal: Double? = null,
    val fat: Double? = null,
    val saturatedFat: Double? = null,
    val carbohydrates: Double? = null,
    val sugars: Double? = null,
    val fiber: Double? = null,
    val proteins: Double? = null,
    val salt: Double? = null,
    /** true: Werte gelten je 100 ml (Packungseinheit ml/l), sonst je 100 g. */
    val perMl: Boolean = false,
) {
    val isEmpty: Boolean
        get() = listOf(energyKj, energyKcal, fat, saturatedFat, carbohydrates, sugars, fiber, proteins, salt)
            .all { it == null }
}

object NutritionParser {
    private const val KJ_PER_KCAL = 4.184
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Liest die `_100g`-Werte aus dem OFF-Objekt `nutriments` (Zahlen kommen teils als Text).
     * Fehlt kJ oder kcal, wird der andere Wert umgerechnet. Liefert null, wenn kein Wert vorhanden ist.
     */
    fun fromNutriments(nutriments: JsonObject?, unitSymbol: String?): Nutrition? {
        if (nutriments == null) return null
        fun value(key: String): Double? = (nutriments[key] as? JsonPrimitive)?.contentOrNull
            ?.trim()?.replace(',', '.')?.toDoubleOrNull()
            ?.takeIf { it.isFinite() && it >= 0 }

        var kj = value("energy-kj_100g") ?: value("energy_100g")
        var kcal = value("energy-kcal_100g")
        if (kcal == null && kj != null) kcal = kj / KJ_PER_KCAL
        if (kj == null && kcal != null) kj = kcal * KJ_PER_KCAL

        return Nutrition(
            energyKj = kj,
            energyKcal = kcal,
            fat = value("fat_100g"),
            saturatedFat = value("saturated-fat_100g"),
            carbohydrates = value("carbohydrates_100g"),
            sugars = value("sugars_100g"),
            fiber = value("fiber_100g"),
            proteins = value("proteins_100g"),
            salt = value("salt_100g"),
            perMl = unitSymbol?.lowercase() in setOf("ml", "l"),
        ).takeUnless { it.isEmpty }
    }

    fun encode(nutrition: Nutrition?): String? = nutrition?.let { json.encodeToString(Nutrition.serializer(), it) }

    fun decode(text: String?): Nutrition? =
        text?.let { runCatching { json.decodeFromString(Nutrition.serializer(), it) }.getOrNull() }

    /** Energie ohne Nachkommastellen, sonst höchstens zwei (deutsches Zahlenformat, z. B. "0,25"). */
    fun formatAmount(value: Double): String = format(value, 2)

    fun formatEnergy(value: Double): String = format(value, 0)

    private fun format(value: Double, decimals: Int): String {
        val rounded = BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP)
        val pattern = if (decimals == 0) "#,##0" else "#,##0." + "#".repeat(decimals)
        return DecimalFormat(pattern, DecimalFormatSymbols(Locale.GERMANY)).format(rounded)
    }
}
