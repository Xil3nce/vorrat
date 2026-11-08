package de.frelina.vorrat.domain

import de.frelina.vorrat.data.db.StockItem

/** Entnehmen per Scan. */
object ConsumeLogic {

    /**
     * Reihenfolge der Einträge: zuerst, was am frühesten abläuft (bei geöffneten Packungen "verbrauchen bis");
     * geöffnete Packungen bei gleichem Datum zuerst; ohne Datum am Ende.
     */
    fun order(entries: List<StockItem>): List<StockItem> = entries.sortedWith(
        compareBy<StockItem, java.time.LocalDate?>(nullsLast()) { it.effectiveExpiry }
            .thenBy { if (it.openedAt != null) 0 else 1 }
            .thenBy(nullsFirst(String.CASE_INSENSITIVE_ORDER)) { it.locationName },
    )

    /** Schnellwahl: 1, 2, 3 (soweit vorhanden) und "Alle" (= [max]). */
    fun quickAmounts(max: Int): List<Int> = ((1..minOf(3, max)).toList() + max).distinct()

    /** Gewünschte Anzahl auf 1 … vorhandene Anzahl begrenzen. */
    fun clamp(amount: Int, available: Int): Int = amount.coerceIn(1, available.coerceAtLeast(1))
}
