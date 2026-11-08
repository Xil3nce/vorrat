package de.frelina.vorrat.domain

import java.time.DateTimeException
import java.time.LocalDate
import java.time.YearMonth

/**
 * Findet ein Ablaufdatum (MHD) in per Kamera erkanntem Text.
 *
 * Erkannt werden u. a. "24.12.2026", "24.12.26", "24/12/26", "24-12-2026", "24 12 2026" sowie Monat/Jahr
 * ("12.2026", "12/26" → letzter Tag des Monats). Typische Lesefehler (O statt 0, I/l statt 1) werden korrigiert.
 *
 * Auswahl: Datumsangaben direkt hinter einem Stichwort (MHD, mindestens haltbar, verbrauchen bis, EXP, best before …)
 * haben Vorrang; sonst gewinnt das späteste plausible Datum (Herstellungsdaten liegen davor).
 * Plausibel: höchstens [MAX_PAST_DAYS] Tage in der Vergangenheit und höchstens [MAX_FUTURE_YEARS] Jahre in der Zukunft.
 */
object ExpiryDateParser {
    const val MAX_PAST_DAYS = 60L
    const val MAX_FUTURE_YEARS = 15L

    private const val D = "[0-9OoIl]"
    private const val SEP = """\s?[./\-]\s?|\s"""

    // Tag.Monat.Jahr (Jahr 2- oder 4-stellig)
    private val DAY_MONTH_YEAR = Regex("""(?<!\d)($D{1,2})(?:$SEP)($D{1,2})(?:$SEP)($D{4}|$D{2})(?![0-9])""")

    // Monat.Jahr (ohne Tag), z. B. "12.2026" oder "12/26"
    private val MONTH_YEAR = Regex("""(?<![0-9./\-])($D{1,2})\s?[./\-]\s?($D{4}|$D{2})(?![0-9./\-])""")

    private val KEYWORD = Regex(
        """(?<![A-Za-zÄÖÜäöü])(MHD|M\.H\.D|mind(?:estens)?\.?\s*haltbar|haltbar\s*bis|verbr(?:auchen|\.)?\s*bis|zu\s*verbrauchen|""" +
            """best\s*before|BBE|BB|EXP|use\s*by|à\s*consommer|A\s*consommer|DLC|DDM|TGT)(?![A-Za-zÄÖÜäöü])""",
        RegexOption.IGNORE_CASE,
    )

    /** Abstand (Zeichen) zwischen Stichwort und Datum, der noch als "direkt dahinter" gilt. */
    private const val KEYWORD_DISTANCE = 40

    private data class Candidate(val date: LocalDate, val start: Int, val dayGiven: Boolean)

    fun parse(text: String, today: LocalDate = LocalDate.now()): LocalDate? {
        if (text.isBlank()) return null
        val candidates = findAll(text)
            .filter { !it.date.isBefore(today.minusDays(MAX_PAST_DAYS)) && !it.date.isAfter(today.plusYears(MAX_FUTURE_YEARS)) }
        if (candidates.isEmpty()) return null
        val keywordEnds = KEYWORD.findAll(text).map { it.range.last + 1 }.toList()
        fun distance(c: Candidate): Int? = keywordEnds
            .filter { end -> c.start >= end - 1 && c.start - end <= KEYWORD_DISTANCE }
            .minOfOrNull { end -> c.start - end }
        // Hinter einem Stichwort: das nächstgelegene Datum (spätere Angaben sind oft Chargen-/Herstellungsdaten).
        candidates.mapNotNull { c -> distance(c)?.let { c to it } }
            .minWithOrNull(compareByDescending<Pair<Candidate, Int>> { it.first.dayGiven }.thenBy { it.second })
            ?.let { return it.first.date }
        // Sonst: vollständige Datumsangaben vor Monat/Jahr, dann das späteste.
        return candidates.sortedWith(compareByDescending<Candidate> { it.dayGiven }.thenByDescending { it.date }).first().date
    }

    private fun findAll(text: String): List<Candidate> {
        val result = mutableListOf<Candidate>()
        val covered = mutableListOf<IntRange>()
        DAY_MONTH_YEAR.findAll(text).forEach { m ->
            val (d, mo, y) = m.destructured
            date(num(d), num(mo), year(y))?.let {
                result += Candidate(it, m.range.first, dayGiven = true)
                covered += m.range
            }
        }
        MONTH_YEAR.findAll(text).forEach { m ->
            if (covered.any { r -> m.range.first in r || m.range.last in r }) return@forEach
            val (mo, y) = m.destructured
            val month = num(mo) ?: return@forEach
            val year = year(y) ?: return@forEach
            if (month !in 1..12) return@forEach
            result += Candidate(YearMonth.of(year, month).atEndOfMonth(), m.range.first, dayGiven = false)
        }
        return result
    }

    private fun num(s: String): Int? = fix(s).toIntOrNull()

    private fun year(s: String): Int? {
        val n = num(s) ?: return null
        return when (s.length) {
            2 -> 2000 + n
            4 -> n.takeIf { it in 2000..2099 }
            else -> null
        }
    }

    private fun date(day: Int?, month: Int?, year: Int?): LocalDate? {
        if (day == null || month == null || year == null) return null
        return try {
            LocalDate.of(year, month, day)
        } catch (_: DateTimeException) {
            null
        }
    }

    /** Typische OCR-Verwechslungen in Ziffernfolgen korrigieren. */
    private fun fix(s: String): String = s.map {
        when (it) {
            'O', 'o' -> '0'
            'I', 'l' -> '1'
            else -> it
        }
    }.joinToString("")
}
