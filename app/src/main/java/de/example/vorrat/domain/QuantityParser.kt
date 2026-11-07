package de.example.vorrat.domain

import java.math.BigDecimal

/** Ergebnis: Packungsgröße + Einheitskürzel (g, kg, ml, l). */
data class ParsedQuantity(val size: BigDecimal, val unitSymbol: String)

/**
 * Überführt die Mengenangaben von Open Food Facts in Packungsgröße + Einheit.
 *
 * Verwendete OFF-Felder (laut API-Doku, schemas/product_base.yaml):
 * - `quantity`: Freitext wie auf der Packung, z. B. "500 g", "1,5 l", "3 x 150 g".
 * - `product_quantity` + `product_quantity_unit`: normalisierte Gesamtmenge in g oder ml,
 *   z. B. "3 x 150 g" → 450 / "g".
 *
 * Strategie:
 * 1. `quantity` ist eine einzelne Angabe "<Zahl> <Einheit>" mit Einheit g, kg, ml, cl, l
 *    → genau diese Angabe übernehmen (cl wird in ml umgerechnet, da cl keine Standard-Einheit ist).
 * 2. Sonst: `product_quantity` (Zahl > 0) mit `product_quantity_unit` g oder ml → übernehmen.
 * 3. Sonst: null (Felder bleiben leer, Nutzer kann ergänzen).
 */
object QuantityParser {

    private val SIMPLE = Regex(
        """^\s*(\d+(?:[.,]\d+)?)\s*(kg|g|ml|cl|l)\.?\s*℮?\s*$""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(quantity: String?, productQuantity: String?, productQuantityUnit: String?): ParsedQuantity? {
        parseSimple(quantity)?.let { return it }
        return parseNormalized(productQuantity, productQuantityUnit)
    }

    fun parseSimple(quantity: String?): ParsedQuantity? {
        if (quantity.isNullOrBlank()) return null
        val match = SIMPLE.matchEntire(quantity) ?: return null
        val number = parseNumber(match.groupValues[1]) ?: return null
        if (number.signum() <= 0) return null
        return when (val unit = match.groupValues[2].lowercase()) {
            "cl" -> ParsedQuantity(clean(number.multiply(BigDecimal.TEN)), "ml")
            else -> ParsedQuantity(clean(number), unit)
        }
    }

    fun parseNormalized(productQuantity: String?, unit: String?): ParsedQuantity? {
        val number = productQuantity?.trim()?.let(::parseNumber) ?: return null
        if (number.signum() <= 0) return null
        val u = unit?.trim()?.lowercase() ?: return null
        if (u != "g" && u != "ml") return null
        return ParsedQuantity(clean(number), u)
    }

    private fun parseNumber(raw: String): BigDecimal? = raw.replace(',', '.').toBigDecimalOrNull()

    /** "400.0" → "400", "1.50" → "1.5" */
    private fun clean(value: BigDecimal): BigDecimal {
        val stripped = value.stripTrailingZeros()
        return if (stripped.scale() < 0) stripped.setScale(0) else stripped
    }
}
