package de.frelina.vorrat.domain

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Rechnet Mengenangaben aus Rezepten auf eine andere Personenzahl um, z. B. "400 g" × 1,5 → "600 g",
 * "1 Zehe" × 2 → "2 Zehen", "1 Dose" × 0,5 → "½ Dose".
 *
 * Erkannt wird eine Zahl am Anfang: ganze Zahl, Dezimalzahl ("1,5"), Bruch ("1/2", "½", "1 ½") oder Spanne ("2-3").
 * Angaben ohne Zahl ("etwas", "zum Servieren") bleiben unverändert.
 *
 * Rundung: Gramm/Milliliter auf sinnvolle Schritte (≥ 100 → 10er, ≥ 20 → 5er, sonst ganze Zahl);
 * Kilogramm/Liter auf eine Nachkommastelle; Stückangaben (Zehe, Dose, EL …) auf ½, unter 1 auf ¼.
 */
object AmountScaler {

    private const val NUM = """\d+(?:[.,]\d+)?"""
    private const val FRAC = "[½¼¾⅓⅔]"

    /** Zahl (auch "1 ½", "1½", "1/2", "½"), optional Spanne "2-3" / "2 – 3". */
    private const val VALUE = """\d+\s*/\s*\d+|$NUM(?:\s*$FRAC)?|$FRAC"""
    private val LEADING = Regex("""^\s*($VALUE)(?:\s*[-–]\s*($VALUE))?(?=\s|$|[A-Za-zÄÖÜäöü])""")

    private val FRACTIONS = mapOf('½' to 0.5, '¼' to 0.25, '¾' to 0.75, '⅓' to 1.0 / 3, '⅔' to 2.0 / 3)

    /** Einheiten mit feiner Rundung (Gewicht/Volumen klein). */
    private val FINE_UNITS = setOf("g", "ml", "gramm", "milliliter")

    /** Einheiten mit einer Nachkommastelle. */
    private val DECIMAL_UNITS = setOf("kg", "l", "liter", "kilo", "cl", "dl")

    /** Singular ↔ Plural für Zähl-Einheiten (erstes Wort nach der Zahl, auch nach "kleine"/"große"). */
    private val PLURALS = listOf(
        "Zehe" to "Zehen", "Dose" to "Dosen", "Scheibe" to "Scheiben", "Kugel" to "Kugeln", "Stange" to "Stangen",
        "Rolle" to "Rollen", "Prise" to "Prisen", "Portion" to "Portionen", "Packung" to "Packungen",
        "Kopf" to "Köpfe", "Blatt" to "Blätter", "Glas" to "Gläser", "Becher" to "Becher", "Tasse" to "Tassen",
        "Knolle" to "Knollen", "Zweig" to "Zweige", "Stück" to "Stück", "Topf" to "Töpfe", "Flasche" to "Flaschen",
        "Tüte" to "Tüten", "Beutel" to "Beutel", "Würfel" to "Würfel", "Schote" to "Schoten",
    )
    private val TO_PLURAL = PLURALS.associate { it.first to it.second }
    private val TO_SINGULAR = PLURALS.associate { it.second to it.first }

    /** Faktor für die Umrechnung von [base] auf [target] Personen (1, wenn keine Basis bekannt ist). */
    fun factor(base: Int?, target: Int): Double = if (base == null || base <= 0 || target <= 0) 1.0 else target.toDouble() / base

    fun scale(amount: String?, factor: Double): String? {
        if (amount.isNullOrBlank() || factor <= 0 || abs(factor - 1.0) < 1e-9) return amount
        val m = LEADING.find(amount) ?: return amount
        val from = parse(m.groupValues[1]) ?: return amount
        val to = m.groupValues[2].takeIf(String::isNotEmpty)?.let { parse(it) ?: return amount }
        val rest = amount.substring(m.range.last + 1)
        val unit = rest.trim().substringBefore(' ').lowercase().trimEnd('.')
        val a = from * factor
        val b = to?.times(factor)
        val number = if (b == null) format(a, unit) else format(a, unit) + "–" + format(b, unit)
        val many = (b ?: a) > 1.0 + 1e-9
        return number + adjustUnit(rest, many)
    }

    private fun parse(raw: String): Double? {
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

    private fun format(value: Double, unit: String): String = when (unit) {
        in FINE_UNITS -> {
            val step = when {
                value >= 100 -> 10.0
                value >= 20 -> 5.0
                else -> 1.0
            }
            (maxOf(1.0, (value / step).roundToInt() * step)).toInt().toString()
        }
        in DECIMAL_UNITS -> {
            val r = BigDecimal(value).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros()
            (if (r.signum() == 0) BigDecimal("0.1") else r).toPlainString().replace('.', ',')
        }
        else -> countFormat(value)
    }

    /** Stückzahlen: auf ½ runden, unter 1 auf ¼ (mindestens ¼). */
    private fun countFormat(value: Double): String {
        val rounded = if (value < 1) maxOf(0.25, (value * 4).roundToInt() / 4.0) else (value * 2).roundToInt() / 2.0
        val whole = floor(rounded).toInt()
        val frac = when (((rounded - whole) * 4).roundToInt()) {
            1 -> "¼"
            2 -> "½"
            3 -> "¾"
            else -> ""
        }
        return when {
            whole == 0 -> frac
            else -> "$whole$frac"
        }
    }

    /** Passt Singular/Plural der Einheit an ("1 Zehe" → "2 Zehen", "2 Dosen" → "1 Dose"). */
    private fun adjustUnit(rest: String, many: Boolean): String {
        val words = rest.split(' ')
        val index = words.indexOfFirst { it.isNotEmpty() }
        if (index < 0) return rest
        val target = if (words[index].lowercase() in ADJECTIVES && index + 1 < words.size) index + 1 else index
        val word = words[target]
        val replaced = if (many) TO_PLURAL[word] else TO_SINGULAR[word]
        if (replaced == null) return rest
        val adjusted = words.toMutableList()
        adjusted[target] = replaced
        return adjusted.joinToString(" ")
    }

    /** Adjektiv vor der Einheit ("kleine Dose" → "kleine Dosen"); bleibt im Nominativ unverändert. */
    private val ADJECTIVES = setOf("kleine", "große", "mittelgroße")
}
