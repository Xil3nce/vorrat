package de.frelina.vorrat.domain

import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle

/** Fehlerarten; die deutschen Texte liegen in strings.xml (siehe ui/components/ErrorText.kt). */
enum class ValidationError {
    REQUIRED,
    QUANTITY_MIN_ONE,
    WHOLE_NUMBER,
    INVALID_DATE,
    POSITIVE_NUMBER,
}

sealed interface Validated<out T> {
    data class Ok<T>(val value: T) : Validated<T>
    data class Error(val error: ValidationError) : Validated<Nothing>
}

object Validation {

    /** Datumsformat für Eingaben: TT.MM.JJJJ (streng, z. B. kein 31.02.). */
    val DATE_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT)

    fun required(text: String): Validated<String> =
        text.trim().let { if (it.isEmpty()) Validated.Error(ValidationError.REQUIRED) else Validated.Ok(it) }

    /** Anzahl: ganze Zahl ≥ 1. */
    fun quantity(text: String): Validated<Int> {
        val t = text.trim()
        if (t.isEmpty()) return Validated.Error(ValidationError.REQUIRED)
        val n = t.toIntOrNull() ?: return Validated.Error(ValidationError.WHOLE_NUMBER)
        return if (n < 1) Validated.Error(ValidationError.QUANTITY_MIN_ONE) else Validated.Ok(n)
    }

    /** Ebene/Fach: optional, sonst ganze Zahl (≥ 0). */
    fun optionalWholeNumber(text: String): Validated<Int?> {
        val t = text.trim()
        if (t.isEmpty()) return Validated.Ok(null)
        val n = t.toIntOrNull()
        return if (n == null || n < 0) Validated.Error(ValidationError.WHOLE_NUMBER) else Validated.Ok(n)
    }

    /** Ablaufdatum: optional, sonst gültiges Datum im Format TT.MM.JJJJ (oder TT.MM.JJ → 20JJ). */
    fun optionalDate(text: String): Validated<LocalDate?> {
        val t = text.trim()
        if (t.isEmpty()) return Validated.Ok(null)
        val full = SHORT_YEAR.matchEntire(t)?.let { m -> "${m.groupValues[1]}20${m.groupValues[2]}" } ?: t
        return try {
            Validated.Ok(LocalDate.parse(full, DATE_FORMAT))
        } catch (_: DateTimeParseException) {
            Validated.Error(ValidationError.INVALID_DATE)
        }
    }

    private val SHORT_YEAR = Regex("""^(\d{2}\.\d{2}\.)(\d{2})$""")

    /**
     * Eingabehilfe fürs Datumsfeld: nur Ziffern zählen, die Punkte werden automatisch gesetzt
     * ("24122026" → "24.12.2026", "2412" → "24.12"). Höchstens 8 Ziffern.
     */
    fun formatDateInput(raw: String): String {
        val digits = raw.filter(Char::isDigit).take(8)
        return buildString {
            digits.forEachIndexed { i, c ->
                if (i == 2 || i == 4) append('.')
                append(c)
            }
        }
    }

    /** Packungsgröße: optional, sonst Dezimalzahl > 0 (Komma oder Punkt). */
    fun optionalPositiveDecimal(text: String): Validated<BigDecimal?> {
        val t = text.trim()
        if (t.isEmpty()) return Validated.Ok(null)
        val n = t.replace(',', '.').toBigDecimalOrNull()
        return if (n == null || n.signum() <= 0) Validated.Error(ValidationError.POSITIVE_NUMBER) else Validated.Ok(n)
    }

    fun formatDate(date: LocalDate?): String = date?.format(DATE_FORMAT) ?: ""
}
