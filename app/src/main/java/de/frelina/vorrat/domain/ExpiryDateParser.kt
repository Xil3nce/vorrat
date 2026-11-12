package de.frelina.vorrat.domain

import java.time.DateTimeException
import java.time.LocalDate
import java.time.YearMonth

/**
 * Findet ein Ablaufdatum (MHD) in per Kamera erkanntem Text.
 *
 * Erkannt werden u. a.
 * - Tag/Monat/Jahr: "24.12.2026", "24.12.26", "24/12/26", "24-12-2026", "24,12,26", "24 12 2026"
 * - ISO: "2026-12-24"
 * - Monatsnamen: "24 NOV 2026", "24.Nov.26", "NOV 2026", "Dez. 26" (deutsch, englisch, französisch)
 * - Monat/Jahr: "12.2026", "12/26" (→ letzter Tag des Monats)
 * - ohne Trennzeichen: "241226", "24122026" (nur direkt hinter einem Stichwort)
 *
 * Typische Lesefehler in Ziffernfolgen (O→0, I/l/|→1, S→5, B→8, Z→2) werden korrigiert; jede Zahlengruppe muss
 * aber mindestens eine echte Ziffer enthalten, damit Wörter nicht als Datum gelesen werden.
 *
 * Auswahl: Datumsangaben direkt hinter einem Stichwort (MHD, mindestens haltbar, verbrauchen bis, EXP, best before …)
 * haben Vorrang. Sonst gewinnt das späteste plausible Datum (Herstellungsdaten liegen davor) – dabei zählen
 * nur "sichere" Schreibweisen; unsichere (nur Leerzeichen als Trenner, ohne Trenner, "12.30" = Preis?) zählen
 * nur hinter einem Stichwort.
 * Plausibel: höchstens [MAX_PAST_DAYS] Tage in der Vergangenheit und höchstens [MAX_FUTURE_YEARS] Jahre in der Zukunft.
 */
object ExpiryDateParser {
    /** Großzügig, weil Trockenware oft lange nach dem MHD noch erfasst wird (Herstellungsdaten verlieren ohnehin). */
    const val MAX_PAST_DAYS = 730L
    const val MAX_FUTURE_YEARS = 15L

    private const val D = "[0-9OoIlSsBZz|]"
    private const val SEP = """\s?[./\-,]\s?|\s"""

    // Jahr-Monat-Tag (ISO)
    private val YEAR_MONTH_DAY = Regex("""(?<![0-9])(20[0-9]{2})[./\-]([0-9]{1,2})[./\-]([0-9]{1,2})(?![0-9])""")

    // Tag.Monat.Jahr (Jahr 2- oder 4-stellig)
    private val DAY_MONTH_YEAR =
        Regex("""(?<![0-9])($D{1,2})($SEP)($D{1,2})($SEP)($D{4}|$D{2})(?![0-9])""")

    // (Tag) Monatsname Jahr, z. B. "24 NOV 2026", "24.Nov.26", "Dez. 2026"
    private val NAMED_MONTH = Regex(
        """(?:(?<![0-9])($D{1,2})\s?[./\-]?\s?|(?<![0-9A-Za-zÄÖÜäöüÉÛéû]))""" +
            """([A-Za-zÄÖÜäöüÉÛéû]{3,9})\.?\s?[./\-]?\s?($D{4}|$D{2})(?![0-9A-Za-z])""",
    )

    // Tag Monat Jahr ohne Trennzeichen, z. B. "241226" oder "24122026"
    private val COMPACT = Regex("""(?<![0-9])([0-9]{2})([0-9]{2})([0-9]{4}|[0-9]{2})(?![0-9])""")

    // Monat.Jahr (ohne Tag), z. B. "12.2026" oder "12/26"
    private val MONTH_YEAR = Regex("""(?<![0-9./\-,])($D{1,2})\s?([./\-])\s?($D{4}|$D{2})(?![0-9./\-,])""")

    private val KEYWORD = Regex(
        """(?<![A-Za-zÄÖÜäöü])(MHD|M\.H\.D|mind(?:estens)?\.?\s*haltbar|haltbar\s*bis|verbr(?:auchen|\.)?\s*bis|""" +
            """zu\s*verbrauchen|ablaufdatum|best\s*before|best\s*by|BBE|BB|EXP|exp\.?\s*date|use\s*by|""" +
            """à\s*consommer|A\s*consommer|DLC|DDM|TGT)(?![A-Za-zÄÖÜäöü])""",
        RegexOption.IGNORE_CASE,
    )

    /** Monatsnamen und Abkürzungen (kleingeschrieben, deutsch/englisch/französisch) → Monat. */
    private val MONTH_NAMES: Map<String, Int> = mapOf(
        1 to "jan jän janv januar jänner january janvier",
        2 to "feb fev fév févr fevr februar february février fevrier",
        3 to "mär mae mrz mar märz maerz march mars",
        4 to "apr avr april avril",
        5 to "mai may",
        6 to "jun juni june juin",
        7 to "jul juil juli july juillet",
        8 to "aug aou aoû august août aout",
        9 to "sep sept september septembre",
        10 to "okt oct oktober october octobre",
        11 to "nov november novembre",
        12 to "dez dec déc dezember december décembre decembre",
    ).flatMap { (month, names) -> names.split(' ').map { it to month } }.toMap()

    /** Abstand (Zeichen) zwischen Stichwort und Datum, der noch als "direkt dahinter" gilt. */
    private const val KEYWORD_DISTANCE = 40

    private data class Candidate(
        val date: LocalDate,
        val range: IntRange,
        val dayGiven: Boolean,
        /** Unsichere Schreibweise: zählt nur hinter einem Stichwort. */
        val weak: Boolean,
    )

    fun parse(text: String, today: LocalDate = LocalDate.now()): LocalDate? {
        if (text.isBlank()) return null
        val candidates = findAll(text)
            .filter { !it.date.isBefore(today.minusDays(MAX_PAST_DAYS)) && !it.date.isAfter(today.plusYears(MAX_FUTURE_YEARS)) }
        if (candidates.isEmpty()) return null
        val keywordEnds = KEYWORD.findAll(text).map { it.range.last + 1 }.toList()
        fun distance(c: Candidate): Int? = keywordEnds
            .filter { end -> c.range.first >= end - 1 && c.range.first - end <= KEYWORD_DISTANCE }
            .minOfOrNull { end -> c.range.first - end }
        // Hinter einem Stichwort: das nächstgelegene Datum (spätere Angaben sind oft Chargen-/Herstellungsdaten).
        candidates.mapNotNull { c -> distance(c)?.let { c to it } }
            .minWithOrNull(
                compareByDescending<Pair<Candidate, Int>> { it.first.dayGiven }
                    .thenBy { it.first.weak }
                    .thenBy { it.second },
            )
            ?.let { return it.first.date }
        // Sonst: nur sichere Schreibweisen; vollständige Datumsangaben vor Monat/Jahr, dann das späteste.
        return candidates.filterNot { it.weak }
            .sortedWith(compareByDescending<Candidate> { it.dayGiven }.thenByDescending { it.date })
            .firstOrNull()?.date
    }

    private fun findAll(text: String): List<Candidate> {
        val result = mutableListOf<Candidate>()
        fun free(range: IntRange) = result.none { c -> range.first <= c.range.last && c.range.first <= range.last }
        fun add(regex: Regex, build: (MatchResult) -> Candidate?) {
            var start = 0
            while (start < text.length) {
                val m = regex.find(text, start) ?: break
                val candidate = if (free(m.range)) build(m) else null
                if (candidate != null) {
                    result += candidate
                    start = m.range.last + 1
                } else {
                    // Kein gültiges Datum: einen Schritt weiter suchen (sonst verdeckt z. B. "s 30.12" das echte Datum).
                    start = m.range.first + 1
                }
            }
        }

        add(YEAR_MONTH_DAY) { m ->
            val (y, mo, d) = m.destructured
            date(d.toInt(), mo.toInt(), y.toInt())?.let { Candidate(it, m.range, dayGiven = true, weak = false) }
        }
        add(DAY_MONTH_YEAR) { m ->
            val (d, sep1, mo, sep2, y) = m.destructured
            date(num(d), num(mo), year(y))?.let {
                val weak = sep1.isBlank() || sep2.isBlank()
                Candidate(it, m.range, dayGiven = true, weak = weak)
            }
        }
        add(NAMED_MONTH) { m ->
            val (d, name, y) = m.destructured
            val month = monthOf(name) ?: return@add null
            val year = year(y) ?: return@add null
            if (d.isEmpty()) {
                Candidate(YearMonth.of(year, month).atEndOfMonth(), m.range, dayGiven = false, weak = false)
            } else {
                date(num(d), month, year)?.let { Candidate(it, m.range, dayGiven = true, weak = false) }
            }
        }
        add(COMPACT) { m ->
            val (d, mo, y) = m.destructured
            date(d.toInt(), mo.toInt(), year(y))?.let { Candidate(it, m.range, dayGiven = true, weak = true) }
        }
        add(MONTH_YEAR) { m ->
            val (mo, sep, y) = m.destructured
            val month = num(mo)?.takeIf { it in 1..12 } ?: return@add null
            val year = year(y) ?: return@add null
            // "12.30" kann auch ein Preis oder Gewicht sein.
            val weak = sep == "." && y.length == 2
            Candidate(YearMonth.of(year, month).atEndOfMonth(), m.range, dayGiven = false, weak = weak)
        }
        return result
    }

    private fun monthOf(name: String): Int? = MONTH_NAMES[name.lowercase()]

    /** Zahl aus einer Zifferngruppe; ohne echte Ziffer (nur Buchstaben) kein Treffer. */
    private fun num(s: String): Int? = if (s.none { it.isDigit() }) null else fix(s).toIntOrNull()

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
            'I', 'l', '|' -> '1'
            'S', 's' -> '5'
            'B' -> '8'
            'Z', 'z' -> '2'
            else -> it
        }
    }.joinToString("")
}
