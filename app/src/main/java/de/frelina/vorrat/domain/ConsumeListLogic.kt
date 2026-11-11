package de.frelina.vorrat.domain

import de.frelina.vorrat.data.consume.ConsumeDraft
import de.frelina.vorrat.data.db.StockItem
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Ergebnis von [ConsumeListLogic.addScan]. [entry] = null: nichts mehr übrig, die Liste bleibt unverändert. */
data class ConsumeAddResult(
    val list: List<ConsumeDraft>,
    val entry: StockItem?,
    /** Neue Zeile (true) oder bestehende Zeile erhöht (false). */
    val isNew: Boolean,
    /** Gesamtmenge dieses Produkts in der Liste nach dem Scan. */
    val total: Int,
)

/** Reine Regeln für die Entnahme-Scan-Liste (Modus "Entnehmen"). */
object ConsumeListLogic {

    /**
     * Ein Scan nimmt 1 Stück aus dem Eintrag, der zuerst abläuft ([ConsumeLogic.order]). Ist die Menge dieses
     * Eintrags in der Liste ausgeschöpft, kommt der nächste Eintrag nach Ablauf dran (eigene Zeile). Ist alles
     * ausgeschöpft, ändert sich nichts ([ConsumeAddResult.entry] = null).
     */
    fun addScan(list: List<ConsumeDraft>, entries: List<StockItem>): ConsumeAddResult {
        val ordered = ConsumeLogic.order(entries)
        val productId = ordered.firstOrNull()?.productId
        fun totalOf(l: List<ConsumeDraft>) = l.filter { it.productId == productId }.sumOf { it.quantity }
        for (item in ordered) {
            val index = list.indexOfFirst { it.entryId == item.entryId }
            val reserved = if (index >= 0) list[index].quantity else 0
            if (reserved >= item.quantity) continue
            val next = if (index >= 0) {
                list.toMutableList().also { it[index] = it[index].copy(quantity = reserved + 1) }
            } else {
                list + ConsumeDraft(item.entryId, item.productId, 1)
            }
            return ConsumeAddResult(next, item, isNew = index < 0, total = totalOf(next))
        }
        return ConsumeAddResult(list, null, isNew = false, total = totalOf(list))
    }

    /**
     * Zeile [oldEntryId] auf [newEntryId] mit [amount] umstellen (Dialog "Übernehmen"). Gibt es für den neuen Eintrag
     * schon eine Zeile, bekommt sie die gewählte Menge und die alte Zeile entfällt.
     */
    fun change(list: List<ConsumeDraft>, oldEntryId: String, newEntryId: String, amount: Int): List<ConsumeDraft> {
        val oldIndex = list.indexOfFirst { it.entryId == oldEntryId }
        if (oldIndex < 0) return list
        val qty = amount.coerceAtLeast(1)
        if (oldEntryId == newEntryId) return list.mapIndexed { i, d -> if (i == oldIndex) d.copy(quantity = qty) else d }
        val target = list.indexOfFirst { it.entryId == newEntryId }
        return if (target >= 0) {
            list.mapIndexedNotNull { i, d ->
                when (i) {
                    oldIndex -> null
                    target -> d.copy(quantity = qty)
                    else -> d
                }
            }
        } else {
            list.mapIndexed { i, d -> if (i == oldIndex) d.copy(entryId = newEntryId, quantity = qty) else d }
        }
    }

    /** Menge einer Zeile um [delta] ändern, begrenzt auf 1 … [available] (mindestens 1). */
    fun adjust(list: List<ConsumeDraft>, entryId: String, delta: Int, available: Int): List<ConsumeDraft> =
        list.map { if (it.entryId == entryId) it.copy(quantity = ConsumeLogic.clamp(it.quantity + delta, available)) else it }

    /** "tt.mm." für die Zeile in der Liste. */
    fun shortDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("dd.MM."))
}
