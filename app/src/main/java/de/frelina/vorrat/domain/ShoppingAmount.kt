package de.frelina.vorrat.domain

/**
 * Menge einer geplanten Zutat ("700g", "1,5 kg") → Packungsgröße + Einheit der Einkaufsliste. Nur wenn der Text
 * genau aus Zahl und einer Masse/Volumen-Einheit der App (g, kg, ml, l) besteht; alles andere ("3 Stück",
 * "1 EL", "2 Dosen + 1 Prise") bleibt Text und wandert in die Notiz.
 */
object ShoppingAmount {
    fun parse(text: String?): ParsedQuantity? = QuantityParser.parseSimple(text)
}

/** Rezepttitel eines Listeneintrags: in der Datenbank durch Zeilenumbruch getrennt (Titel können Kommas enthalten). */
object ShoppingRecipes {
    private const val SEPARATOR = "\n"

    /** Titel bereinigt (ohne Leere/Doppelte) zu einem Text; was über [maxLength] hinausginge, entfällt als Ganzes. */
    fun join(titles: List<String>, maxLength: Int): String? {
        val result = StringBuilder()
        titles.map { it.trim().replace(SEPARATOR, " ") }.filter { it.isNotEmpty() }.distinct().forEach { title ->
            val needed = title.length + if (result.isEmpty()) 0 else SEPARATOR.length
            if (result.length + needed <= maxLength) {
                if (result.isNotEmpty()) result.append(SEPARATOR)
                result.append(title)
            }
        }
        return result.toString().takeIf { it.isNotEmpty() }
    }

    fun split(stored: String?): List<String> =
        stored?.split(SEPARATOR)?.map(String::trim)?.filter(String::isNotEmpty).orEmpty()

    /** Anzeige in der Liste: "Suppe, Kuchen". */
    fun label(stored: String?): String? = split(stored).joinToString(", ").takeIf(String::isNotEmpty)
}
