package de.frelina.vorrat.domain

import de.frelina.vorrat.data.db.StockItem
import java.time.LocalDate
import java.util.Locale

enum class StockSort { EXPIRY, LOCATION }

enum class StockFilter { ALL, SOON, EXPIRED }

object StockQueries {

    private val byExpiry = compareBy<StockItem, LocalDate?>(nullsLast()) { it.expiryDate }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.productName }

    /** Standard: Ablaufdatum aufsteigend, Einträge ohne Datum am Ende. */
    fun sort(items: List<StockItem>, sort: StockSort): List<StockItem> = when (sort) {
        StockSort.EXPIRY -> items.sortedWith(byExpiry)
        StockSort.LOCATION -> items.sortedWith(
            compareBy<StockItem, String>(String.CASE_INSENSITIVE_ORDER) { it.locationName }
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
    ): List<StockItem> = items.filter { item ->
        val statusOk = when (filter) {
            StockFilter.ALL -> true
            StockFilter.SOON -> ExpiryLogic.status(item.expiryDate, today, leadDays) == ExpiryStatus.SOON
            StockFilter.EXPIRED -> ExpiryLogic.status(item.expiryDate, today, leadDays) == ExpiryStatus.EXPIRED
        }
        statusOk && (locationId == null || item.locationId == locationId)
    }

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
