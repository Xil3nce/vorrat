package de.frelina.vorrat.domain

import de.frelina.vorrat.data.db.StockItem
import java.time.LocalDate
import java.util.Locale

enum class StockSort { EXPIRY, LOCATION }

enum class StockFilter { ALL, SOON, EXPIRED, FROZEN }

object StockQueries {

    /** Ortsfilter-Wert für "noch keinem Ort zugeordnet". */
    const val NO_LOCATION = "\u0000ohne-ort"

    private val byExpiry = compareBy<StockItem, LocalDate?>(nullsLast()) { it.effectiveExpiry }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.productName }

    /** Standard: Ablaufdatum (bei geöffneten Packungen "verbrauchen bis") aufsteigend, ohne Datum am Ende. */
    fun sort(items: List<StockItem>, sort: StockSort): List<StockItem> = when (sort) {
        StockSort.EXPIRY -> items.sortedWith(byExpiry)
        StockSort.LOCATION -> items.sortedWith(
            // Einträge ohne Ort zuerst – die sollen noch einsortiert werden.
            compareBy<StockItem, String?>(nullsFirst(String.CASE_INSENSITIVE_ORDER)) { it.locationName }
                .thenBy(nullsLast()) { it.level }
                .thenBy(nullsLast()) { it.shelf }
                .then(byExpiry),
        )
    }

    fun filter(
        items: List<StockItem>,
        filter: StockFilter,
        locationId: String?,
        today: LocalDate,
        leadDays: Int,
        level: Int? = null,
        shelf: Int? = null,
    ): List<StockItem> = items.filter { item ->
        val statusOk = when (filter) {
            StockFilter.ALL -> true
            StockFilter.SOON -> ExpiryLogic.status(item.effectiveExpiry, today, leadDays) == ExpiryStatus.SOON
            StockFilter.EXPIRED -> ExpiryLogic.status(item.effectiveExpiry, today, leadDays) == ExpiryStatus.EXPIRED
            StockFilter.FROZEN -> item.frozenAt != null
        }
        statusOk &&
            (locationId == null || item.locationId == locationId || (locationId == NO_LOCATION && item.locationId == null)) &&
            (level == null || item.level == level) &&
            (shelf == null || item.shelf == shelf)
    }

    /**
     * Auswahl für den Ebenen-/Fach-Filter: alle im Bestand (ggf. am gewählten Ort) vorkommenden Werte
     * plus 1 … [definedCount] des gewählten Orts, aufsteigend.
     */
    fun slotOptions(values: List<Int?>, definedCount: Int?): List<Int> =
        (values.filterNotNull() + (1..(definedCount ?: 0))).distinct().sorted()

    /**
     * Textsuche: Teilstring, Groß-/Kleinschreibung egal (auch Umlaute), über
     * Produktname, Marke, Barcode, Ortsname und Notiz.
     */
    fun search(items: List<StockItem>, query: String): List<StockItem> {
        val q = query.trim().lowercase(Locale.GERMAN)
        if (q.isEmpty()) return emptyList()
        return items.filter { item ->
            listOf(item.productName, item.brand, item.barcode, item.locationName, item.note)
                .any { it != null && it.lowercase(Locale.GERMAN).contains(q) }
        }.sortedWith(byExpiry)
    }
}
