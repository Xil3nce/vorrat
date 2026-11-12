package de.frelina.vorrat.domain

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** Eine von der Texterkennung gelesene Zeile mit Lage im Bild (Pixel); [block] = Nummer des Absatzes. */
data class OcrLine(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val block: Int = 0,
)

/**
 * Bringt die Zeilen der Texterkennung in einen Fließtext für [RecipeTextParser]: Absätze werden durch eine
 * Leerzeile getrennt; steht eine Menge allein ("250 g") und rechts daneben auf gleicher Höhe eine Zutat
 * (Zutatenliste als Tabelle), werden beide zu einer Zeile verbunden.
 */
object RecipeTextLayout {

    fun toText(lines: List<OcrLine>): String {
        val clean = lines.filter { it.text.isNotBlank() }
        // Menge → Zutat rechts daneben
        val partnerOf = HashMap<Int, Int>()
        val taken = HashSet<Int>()
        clean.forEachIndexed { i, a ->
            if (!RecipeTextParser.isAmountOnly(a.text)) return@forEachIndexed
            val partner = clean.indices
                .filter { j ->
                    val b = clean[j]
                    j != i && j !in taken && b.left >= (a.left + a.right) / 2 && sameRow(a, b) &&
                        !RecipeTextParser.isAmountOnly(b.text)
                }
                .minByOrNull { clean[it].left }
            if (partner != null) {
                partnerOf[i] = partner
                taken += partner
            }
        }
        val out = StringBuilder()
        var lastBlock: Int? = null
        clean.forEachIndexed { i, line ->
            if (i in taken) return@forEachIndexed
            if (lastBlock != null && lastBlock != line.block) out.append('\n')
            lastBlock = line.block
            out.append(line.text.trim())
            partnerOf[i]?.let { out.append(' ').append(clean[it].text.trim()) }
            out.append('\n')
        }
        return out.toString().trimEnd()
    }

    private fun sameRow(a: OcrLine, b: OcrLine): Boolean {
        val heightA = a.bottom - a.top
        val heightB = b.bottom - b.top
        if (heightA <= 0 || heightB <= 0) return false
        val centerA = (a.top + a.bottom) / 2.0
        val centerB = (b.top + b.bottom) / 2.0
        return abs(centerA - centerB) <= 0.5 * min(heightA, heightB)
    }
}

/**
 * Liest ein Rezept aus fotografiertem Text (gedruckt oder handgeschrieben): Titel, Portionen, Zeit, Zutaten
 * mit Mengen und Zubereitungsschritte. Die Erkennung ist bewusst nachsichtig – das Ergebnis landet im
 * Rezept-Editor und wird dort vor dem Speichern geprüft.
 *
 * Aufbau, der erkannt wird: Überschriften "Zutaten" und "Zubereitung" (o. Ä.); ohne Überschriften gelten
 * Zeilen mit einer Menge am Anfang als Zutaten und der folgende Fließtext als Zubereitung.
 */
object RecipeTextParser {

    private const val MAX_TITLE = 100
    private const val MAX_NAME = 80
    private const val MAX_AMOUNT = 40
    private const val MAX_STEPS_CHARS = 8000
    private const val MAX_INGREDIENTS = 60

    private const val NUM = """\d+(?:[.,]\d+)?"""
    private const val FRAC = "[½¼¾⅓⅔]"

    /** Zahl wie in [AmountScaler]: "2", "1,5", "1/2", "½", "1 ½", zusätzlich "1 1/2". */
    private const val VALUE = """\d+\s+\d\s*/\s*\d|\d+\s*/\s*\d+|$NUM(?:\s*$FRAC)?|$FRAC"""
    private const val RANGE = """(?:$VALUE)(?:\s*(?:[-–]|bis)\s*(?:$VALUE))?"""

    private val UNITS = listOf(
        "Esslöffel", "Teelöffel", "Messerspitzen", "Messerspitze", "Päckchen", "Packungen", "Packung", "Flaschen",
        "Flasche", "Scheiben", "Scheibe", "Spritzer", "Handvoll", "Portionen", "Portion", "Tropfen", "Stangen",
        "Stange", "Knollen", "Knolle", "Schoten", "Schote", "Blätter", "Becher", "Beutel", "Würfel", "Zweige",
        "Prisen", "Tassen", "Kugeln", "Rollen", "Gläser", "Zehen", "Dosen", "Tüten", "Köpfe", "Stück", "Gramm",
        "Liter", "Schuss", "Prise", "Tasse", "Kugel", "Rolle", "Zweig", "Blatt", "Zehe", "Dose", "Tüte", "Kopf",
        "Glas", "Bund", "Kilo", "Topf", "Msp", "Pck", "Pkg", "Pkt", "Stk", "EL", "TL", "kg", "ml", "cl", "dl",
        "St", "g", "l",
    ).joinToString("|")

    private const val ADJECTIVES =
        "kleine[rsn]?|große[rsn]?|mittelgroße[rsn]?|gehäufte[rn]?|gestrichene[rn]?|geh\\.|gestr\\.|kl\\.|gr\\."

    private const val NO_LETTER = "(?![A-Za-zÄÖÜäöüß])"

    /** Menge am Zeilenanfang: Zahl, optional Adjektiv + Einheit; Rest ist der Zutatenname. */
    private val AMOUNT_LINE = Regex(
        """^($RANGE)\s*((?:(?:$ADJECTIVES)\s+)?(?:$UNITS)$NO_LETTER\.?)?\s*(.*)$""",
        RegexOption.IGNORE_CASE,
    )
    private val STARTS_WITH_VALUE = Regex("""^(?:$VALUE)(?=\s|$|[A-Za-zÄÖÜäöü])""")

    /** Ausgeschriebene Zahlen am Zeilenanfang ("eine Prise Salz", "zwei Eier"). */
    private val NUMBER_WORDS = mapOf(
        "ein" to "1", "eine" to "1", "einen" to "1", "einem" to "1", "zwei" to "2", "drei" to "3", "vier" to "4",
        "fünf" to "5", "sechs" to "6", "sieben" to "7", "acht" to "8", "neun" to "9", "zehn" to "10",
        "halbe" to "½", "halber" to "½", "halbes" to "½",
    )
    private val VAGUE_AMOUNT = Regex("""^(etwas|einige|ein paar|wenig|evtl\.|eventuell)\s+(.+)$""", RegexOption.IGNORE_CASE)

    private val BULLET = Regex("""^[•\-–—*·▪■□☐●○◦>]+\s*""")
    private val STEP_NUMBER = Regex("""^(?:Schritt\s*)?\d{1,2}\s*[.):]\s+(?=\S)""", RegexOption.IGNORE_CASE)
    private val INGREDIENTS_HEADER = Regex("""^zutaten(?:liste)?(?![a-zäöüß])""", RegexOption.IGNORE_CASE)
    private val STEPS_HEADER = Regex(
        """^(?:zubereitung|anleitung|zubereiten|arbeitsschritte|schritte|vorgehensweise|(?:und )?so geht['’]?s|so wird['’]?s gemacht)(?![a-zäöüß])""",
        RegexOption.IGNORE_CASE,
    )

    private val SERVINGS = listOf(
        Regex("""(?:für|ergibt|reicht für)\s*(?:ca\.?\s*)?(\d{1,2})(?:\s*[-–]\s*\d{1,2})?\s*(?:personen|portionen|pers\b|port\b|stück|stk\b)""", RegexOption.IGNORE_CASE),
        Regex("""(?:portionen|personen)\s*:?\s*(\d{1,2})""", RegexOption.IGNORE_CASE),
        Regex("""(\d{1,2})\s*(?:personen|portionen)""", RegexOption.IGNORE_CASE),
    )
    private val TIME_LABEL = Regex("""zeit(?![a-zäöüß])|dauer""", RegexOption.IGNORE_CASE)
    private val TIME_ONLY = Regex("""^(?:ca\.?\s*)?$NUM\s*(?:min|minuten|std|stunden?|h)\.?$""", RegexOption.IGNORE_CASE)
    private val HOURS = Regex("""($NUM)\s*(?:std|stunden?|h)(?![a-zäöüß])""", RegexOption.IGNORE_CASE)
    private val MINUTES = Regex("""(\d{1,3})\s*(?:min|minuten?)(?![a-zäöüß])""", RegexOption.IGNORE_CASE)
    private val ACTIVE_TIME = Regex("""arbeits|zubereitungs|koch|back|gar""", RegexOption.IGNORE_CASE)
    private val VEGETARIAN = Regex("""vegetarisch|vegan(?![a-zäöüß])""", RegexOption.IGNORE_CASE)
    private val COMMON_FRACTIONS = listOf("1/2" to "½", "1/4" to "¼", "3/4" to "¾", "1/3" to "⅓", "2/3" to "⅔")

    private enum class Section { INTRO, INGREDIENTS, STEPS }

    private class Draft(val amount: String?, val name: String)

    /** Steht in der Zeile nur eine Menge ("250 g", "2", "1 EL"), aber keine Zutat? */
    fun isAmountOnly(line: String): Boolean {
        val m = AMOUNT_LINE.find(clean(line)) ?: return false
        return m.groupValues[3].isBlank()
    }

    fun parse(text: String): Recipe {
        // Leerzeilen (Absatzgrenzen) bleiben als "" erhalten; sie zählen nur in der Zubereitung.
        val lines = text.lines().map(::clean)
        val hasStepsHeader = lines.any(::isStepsHeader)
        val hasAnyHeader = hasStepsHeader || lines.any(::isIngredientsHeader)

        var title = ""
        var servings: Int? = null
        val timeLines = ArrayList<String>()
        val drafts = ArrayList<Draft>()
        val stepLines = ArrayList<String>()
        var section = Section.INTRO

        for (line in lines) {
            if (line.isEmpty()) {
                if (section == Section.STEPS) stepLines += ""
                continue
            }
            if (isStepsHeader(line)) {
                section = Section.STEPS
                continue
            }
            if (isIngredientsHeader(line)) {
                if (servings == null) servings = servingsIn(line)
                section = Section.INGREDIENTS
                continue
            }
            if (section != Section.STEPS) {
                if (servings == null) servings = servingsIn(line)
                if (isMeta(line)) {
                    if (hasTime(line)) timeLines += line
                    continue
                }
            }
            when (section) {
                Section.INTRO -> when {
                    startsWithAmount(line) -> {
                        section = Section.INGREDIENTS
                        drafts += ingredient(line)
                    }
                    title.isEmpty() && line.count(Char::isLetter) >= 3 -> title = line
                    // Ganz ohne Überschriften und Mengen: Fließtext nach dem Titel ist die Zubereitung.
                    !hasAnyHeader && isSentence(line) -> {
                        section = Section.STEPS
                        stepLines += line
                    }
                }
                Section.INGREDIENTS -> when {
                    !hasStepsHeader && isSentence(line) -> {
                        section = Section.STEPS
                        stepLines += line
                    }
                    // Zwischenüberschrift wie "Für den Teig:"
                    line.endsWith(":") && !startsWithAmount(line) -> Unit
                    else -> drafts += ingredient(line)
                }
                Section.STEPS -> stepLines += line
            }
        }

        val ingredients = pairAmounts(drafts)
            .filter { it.name.isNotBlank() }
            .take(MAX_INGREDIENTS)
            .map { d ->
                RecipeIngredient(
                    name = d.name.take(MAX_NAME),
                    amount = d.amount?.take(MAX_AMOUNT),
                    group = IngredientMatcher.matchName(d.name),
                )
            }
        return Recipe(
            id = "",
            title = tidyTitle(title).take(MAX_TITLE),
            minutes = totalMinutes(timeLines),
            servings = servings?.takeIf { it > 0 },
            ingredients = ingredients,
            steps = RecipeJson.textToSteps(buildSteps(stepLines).joinToString("\n").take(MAX_STEPS_CHARS)),
            vegetarian = VEGETARIAN.containsMatchIn(text),
        )
    }

    private val WHITESPACE = Regex("""\s+""")

    private fun clean(line: String): String = line.trim().replace(BULLET, "").replace(WHITESPACE, " ").trim()

    private fun words(line: String): Int = line.split(' ').count { it.isNotEmpty() }

    private fun isIngredientsHeader(line: String) = INGREDIENTS_HEADER.containsMatchIn(line) && words(line) <= 6

    private fun isStepsHeader(line: String) = STEPS_HEADER.containsMatchIn(line) && words(line) <= 4

    private fun servingsIn(line: String): Int? =
        SERVINGS.firstNotNullOfOrNull { it.find(line)?.groupValues?.get(1)?.toIntOrNull() }

    private fun hasTime(line: String) = HOURS.containsMatchIn(line) || MINUTES.containsMatchIn(line)

    /** Kurze Zeile nur mit Angaben wie "Zubereitungszeit: 30 Min." oder "für 4 Personen" – keine Zutat, kein Titel. */
    private fun isMeta(line: String): Boolean {
        if (words(line) > 8) return false
        val start = line.trimStart('(')
        // "Lasagne für 4 Personen" bleibt Titel: die Portionsangabe muss am Zeilenanfang stehen.
        val servingsAtStart = SERVINGS.any { it.find(start)?.range?.first == 0 }
        return (TIME_LABEL.containsMatchIn(line) && hasTime(line)) || TIME_ONLY.matches(line) || servingsAtStart
    }

    /** "Gesamtzeit" hat Vorrang; sonst Summe aus Arbeits- und Koch-/Backzeit; sonst die erste Zeitangabe. */
    private fun totalMinutes(lines: List<String>): Int? {
        fun minutes(line: String): Int {
            val hours = HOURS.find(line)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
            val minutes = MINUTES.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            return (hours * 60).roundToInt() + minutes
        }
        val total = lines.firstOrNull { it.contains("gesamt", ignoreCase = true) }?.let(::minutes)
            ?: lines.filter { ACTIVE_TIME.containsMatchIn(it) }.sumOf(::minutes).takeIf { it > 0 }
            ?: lines.firstOrNull()?.let(::minutes)
        return total?.takeIf { it in 1..9999 }
    }

    private fun startsWithAmount(line: String): Boolean =
        STARTS_WITH_VALUE.containsMatchIn(line) && !STEP_NUMBER.containsMatchIn(line)

    /** Fließtext statt Zutat? Beendet die Zutatenliste, wenn es keine Überschrift "Zubereitung" gibt. */
    private fun isSentence(line: String): Boolean {
        if (STEP_NUMBER.containsMatchIn(line)) return true
        val count = words(line)
        return if (startsWithAmount(line)) count >= 9 else count >= 7 || (count >= 5 && (line.endsWith(".") || line.endsWith("!")))
    }

    private fun ingredient(line: String): Draft {
        val m = AMOUNT_LINE.find(line)
        if (m != null) {
            val unit = m.groupValues[2].trim()
            val amount = listOf(normalizeNumber(m.groupValues[1]), unit).filter { it.isNotEmpty() }.joinToString(" ")
            return Draft(amount, tidyName(m.groupValues[3]))
        }
        VAGUE_AMOUNT.find(line)?.let { return Draft(it.groupValues[1].lowercase(), tidyName(it.groupValues[2])) }
        val first = line.substringBefore(' ')
        val number = NUMBER_WORDS[first.lowercase()]
        if (number != null && ' ' in line) {
            // "eine Prise Salz" → "1 Prise" + "Salz"
            val rest = AMOUNT_LINE.find("$number ${line.substringAfter(' ')}")
            if (rest != null && rest.groupValues[3].isNotBlank()) {
                val unit = rest.groupValues[2].trim()
                return Draft(listOf(number, unit).filter { it.isNotEmpty() }.joinToString(" "), tidyName(rest.groupValues[3]))
            }
        }
        return Draft(null, tidyName(line))
    }

    private fun normalizeNumber(raw: String): String {
        var s = raw.trim().replace(Regex("""\s*/\s*"""), "/").replace(Regex("""\s*bis\s*""", RegexOption.IGNORE_CASE), "–")
        for ((plain, glyph) in COMMON_FRACTIONS) {
            s = s.replace(Regex("""(?<!\d)${Regex.escape(plain)}(?!\d)"""), glyph)
        }
        return s.replace(Regex("""\s+"""), " ")
    }

    private fun tidyName(raw: String): String = raw.trim().trimStart(',', ';', ':', '.').trim().trimEnd(',', ';').trim()

    /**
     * Mengen ohne Zutat mit Zutaten ohne Menge verbinden: "250 g" + nächste Zeile "Mehl", oder – wenn die
     * Texterkennung eine Tabelle spaltenweise gelesen hat – n Mengen gefolgt von n Zutaten.
     */
    private fun pairAmounts(drafts: List<Draft>): List<Draft> {
        val out = ArrayList<Draft>()
        var i = 0
        while (i < drafts.size) {
            if (drafts[i].name.isNotEmpty()) {
                out += drafts[i]
                i++
                continue
            }
            var amountsEnd = i
            while (amountsEnd < drafts.size && drafts[amountsEnd].name.isEmpty()) amountsEnd++
            var namesEnd = amountsEnd
            while (namesEnd < drafts.size && drafts[namesEnd].amount == null && drafts[namesEnd].name.isNotEmpty()) namesEnd++
            val amounts = amountsEnd - i
            val names = namesEnd - amountsEnd
            val pairs = min(amounts, names)
            // Überzählige Mengen ohne Zutat (z. B. Seitenzahlen) entfallen.
            for (k in 0 until pairs) out += Draft(drafts[i + k].amount, drafts[amountsEnd + k].name)
            for (k in pairs until names) out += drafts[amountsEnd + k]
            i = namesEnd
        }
        return out
    }

    /**
     * Zeilen der Zubereitung zu Schritten verbinden. Mit Nummerierung ("1.", "2)") beginnt ein Schritt an jeder
     * Nummer; sonst an einer Absatzgrenze nach einem Satzende. Text ohne Absätze wird an Zeilen geteilt,
     * die mit einem Satz enden.
     */
    private fun buildSteps(allLines: List<String>): List<String> {
        val lines = allLines.dropWhile { it.isEmpty() }.dropLastWhile { it.isEmpty() }
        val numbered = lines.count { STEP_NUMBER.containsMatchIn(it) } >= 2
        if (!numbered && lines.none { it.isEmpty() }) return splitAtLineEnds(lines)
        val steps = ArrayList<StringBuilder>()
        var paragraphBreak = false
        for (line in lines) {
            if (line.isEmpty()) {
                paragraphBreak = true
                continue
            }
            val last = steps.lastOrNull()
            val startsNumbered = STEP_NUMBER.containsMatchIn(line)
            val newStep = last == null || if (numbered) startsNumbered else paragraphBreak && endsSentence(last)
            val content = if (startsNumbered) line.replace(STEP_NUMBER, "") else line
            if (newStep) steps += StringBuilder(content) else append(last!!, content)
            paragraphBreak = false
        }
        return steps.map { it.toString() }
    }

    private fun splitAtLineEnds(lines: List<String>): List<String> {
        val steps = ArrayList<StringBuilder>()
        var startNew = true
        for (line in lines) {
            if (startNew) steps += StringBuilder(line) else append(steps.last(), line)
            startNew = endsSentence(line)
        }
        return steps.map { it.toString() }
    }

    private fun endsSentence(text: CharSequence): Boolean = text.lastOrNull().let { it == '.' || it == '!' || it == '?' }

    /** Zeile anhängen; ein Trennstrich am Zeilenende ("Zwie-" + "beln") wird aufgelöst. */
    private fun append(step: StringBuilder, line: String) {
        val hyphenated = step.length >= 2 && step.last() == '-' && step[step.length - 2].isLetter() &&
            line.firstOrNull()?.isLowerCase() == true
        if (hyphenated) step.setLength(step.length - 1) else step.append(' ')
        step.append(line)
    }

    /** "APFELKUCHEN" → "Apfelkuchen"; abschließenden Doppelpunkt entfernen. */
    private fun tidyTitle(raw: String): String {
        val title = raw.trim().trimEnd(':').trim()
        val letters = title.filter(Char::isLetter)
        if (letters.length < 4 || letters.any(Char::isLowerCase)) return title
        return title.lowercase().split(' ').joinToString(" ") { word -> word.replaceFirstChar(Char::uppercase) }
    }
}
