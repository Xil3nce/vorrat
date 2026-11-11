package de.frelina.vorrat.ui.consume

import de.frelina.vorrat.data.consume.ConsumeDraftStore
import de.frelina.vorrat.data.repo.StockRepository
import de.frelina.vorrat.domain.ConsumeLogic
import javax.inject.Inject
import javax.inject.Singleton

/** Ergebnis von "n Artikel entnehmen": [skipped] = Einträge, die es nicht mehr gab. */
data class ConsumeCommitResult(val consumed: Int, val skipped: Int)

/**
 * Bucht die Entnahme-Scan-Liste aus: je Zeile [StockRepository.changeQuantity] (Verlauf, Sync, Einkaufslisten-
 * Automatik beim Mindestbestand wie beim Einzel-Entnehmen). Die Menge wird auf den aktuellen Bestand des Eintrags
 * begrenzt; Zeilen, deren Eintrag inzwischen fehlt, werden übersprungen. Danach ist die Liste leer.
 */
@Singleton
class ConsumeCommitter @Inject constructor(
    private val store: ConsumeDraftStore,
    private val stockRepository: StockRepository,
) {
    suspend fun commitAll(): ConsumeCommitResult {
        var consumed = 0
        var skipped = 0
        for (draft in store.all()) {
            val entry = stockRepository.getEntry(draft.entryId)
            if (entry == null) {
                skipped++
            } else {
                stockRepository.changeQuantity(draft.entryId, -ConsumeLogic.clamp(draft.quantity, entry.quantity))
                consumed++
            }
            store.remove(draft.entryId)
        }
        return ConsumeCommitResult(consumed, skipped)
    }
}
