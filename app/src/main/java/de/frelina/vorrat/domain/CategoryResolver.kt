package de.frelina.vorrat.domain

/**
 * Bestimmt die "spezifischste Kategorie" aus den OFF-Kategorien eines Produkts.
 *
 * Grundlage (OFF-API-Doku, schemas/product_base_tags.yaml + tags/taxonomy_tag_entry.yaml):
 * `categories_tags` enthält die Kategorien inkl. automatisch ergänzter Oberkategorien.
 * Einträge haben die Form "<Sprachcode>:<Name>"; Einträge, die zur Taxonomie gehören,
 * sind kanonische IDs wie "en:hazelnut-spreads". Beim Bearbeiten gibt man laut OFF nur die
 * spezifischste Kategorie an, die Eltern werden automatisch ergänzt.
 *
 * Festlegung für diese App (siehe README):
 * - Die Liste wird in der gelieferten Reihenfolge betrachtet (allgemein → spezifisch).
 * - Spezifischste Kategorie = der letzte Eintrag mit Präfix "en:" (kanonische Taxonomie-ID).
 * - Gibt es keinen "en:"-Eintrag, wird der letzte Eintrag überhaupt verwendet.
 * - Leere Liste → keine Kategorie.
 *
 * Unsicherheit: Die OFF-Doku garantiert die Reihenfolge der Liste nicht ausdrücklich;
 * sie entspricht aber dem beobachteten Verhalten der API.
 */
object CategoryResolver {

    fun mostSpecific(categoriesTags: List<String>?): String? {
        val tags = categoriesTags.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }
        if (tags.isEmpty()) return null
        return tags.lastOrNull { it.startsWith("en:") } ?: tags.last()
    }

    /** Anzeigeform: "en:hazelnut-spreads" → "hazelnut spreads". Freitext bleibt unverändert. */
    fun displayName(category: String?): String? {
        if (category.isNullOrBlank()) return null
        val match = Regex("^[a-z]{2}:(.+)$").matchEntire(category.trim()) ?: return category.trim()
        return match.groupValues[1].replace('-', ' ')
    }
}
