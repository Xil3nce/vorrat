package de.frelina.vorrat.domain

import de.frelina.vorrat.data.db.ProductEntity
import java.util.Locale

/**
 * Vorschläge aus den eigenen Produkten beim manuellen Hinzufügen zur Einkaufsliste.
 * Jedes Wort der Eingabe muss (ohne Groß-/Kleinschreibung) in Name, Marke oder Kategorie vorkommen.
 * Reihenfolge: Name beginnt mit der Eingabe → ein Wort im Namen beginnt damit → sonstige Treffer, jeweils alphabetisch.
 */
object ProductSuggest {

    const val MIN_QUERY_LENGTH = 2

    fun suggest(query: String, products: List<ProductEntity>, limit: Int = 5): List<ProductEntity> {
        val q = query.trim().lowercase(Locale.GERMAN)
        if (q.length < MIN_QUERY_LENGTH) return emptyList()
        val words = q.split(Regex("\\s+"))
        return products
            .filter { p ->
                val haystack = listOfNotNull(p.name, p.brand, p.category, CategoryResolver.displayName(p.category))
                    .joinToString(" ")
                    .lowercase(Locale.GERMAN)
                words.all { haystack.contains(it) }
            }
            .sortedWith(compareBy<ProductEntity> { rank(it.name.lowercase(Locale.GERMAN), q) }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            .distinctBy { it.name.trim().lowercase(Locale.GERMAN) to it.brand?.trim()?.lowercase(Locale.GERMAN) }
            .take(limit)
    }

    private fun rank(name: String, q: String): Int = when {
        name.startsWith(q) -> 0
        name.split(Regex("[\\s\\-]+")).any { it.startsWith(q) } -> 1
        else -> 2
    }
}
