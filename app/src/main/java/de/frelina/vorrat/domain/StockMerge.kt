package de.frelina.vorrat.domain

import de.frelina.vorrat.data.db.StockEntryEntity

/**
 * Zusammenführen von Bestandseinträgen: Zwei Einträge gelten als gleich, wenn alles außer ID und Anzahl
 * übereinstimmt (Produkt, Ort, Ebene, Fach, Ablaufdatum, Menge-Art, Notiz).
 */
object StockMerge {

    fun sameData(a: StockEntryEntity, b: StockEntryEntity): Boolean =
        a.productId == b.productId &&
            a.locationId == b.locationId &&
            a.level == b.level &&
            a.shelf == b.shelf &&
            a.expiryDate == b.expiryDate &&
            a.quantityTypeId == b.quantityTypeId &&
            normalizeNote(a.note) == normalizeNote(b.note)

    /** Erster anderer Eintrag mit denselben Daten wie [entry] (der Eintrag selbst zählt nicht). */
    fun findTwin(entry: StockEntryEntity, candidates: List<StockEntryEntity>): StockEntryEntity? =
        candidates.firstOrNull { it.id != entry.id && sameData(it, entry) }

    private fun normalizeNote(note: String?): String? = note?.trim()?.ifEmpty { null }
}
