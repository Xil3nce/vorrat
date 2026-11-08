package de.frelina.vorrat.domain

import de.frelina.vorrat.data.db.HistoryEventEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Verlauf im Haushalt: Auswahl der Benachrichtigungen, Aufbewahrung, Gruppierung nach Tagen. */
object HistoryLogic {

    /** Verlaufseinträge werden nach dieser Zeit gelöscht (auf beiden Handys). */
    const val RETENTION_DAYS = 90L

    /** Ältere Hinweise werden nicht mehr als Benachrichtigung gezeigt (z. B. nach langer Offline-Zeit). */
    const val MAX_NOTICE_AGE_MS = 2L * 24 * 60 * 60 * 1000

    /**
     * Hinweise, die dieses Gerät als Benachrichtigung zeigen soll: von einer anderen Person, mit
     * Benachrichtigungswunsch, neuer als [seenUntil] und nicht älter als [MAX_NOTICE_AGE_MS].
     * Ohne eigene UID (kein Haushalt) gibt es nichts zu zeigen. Sortiert nach Zeit.
     */
    fun noticesToShow(
        events: List<HistoryEventEntity>,
        myUid: String?,
        seenUntil: Long,
        now: Long,
    ): List<HistoryEventEntity> {
        if (myUid.isNullOrBlank()) return emptyList()
        return events.filter {
            it.notify &&
                it.authorUid != null && it.authorUid != myUid &&
                it.createdAt > seenUntil &&
                it.createdAt >= now - MAX_NOTICE_AGE_MS
        }.sortedBy { it.createdAt }
    }

    /** Grenze für [noticesToShow] nach dem Anzeigen. */
    fun newSeenUntil(seenUntil: Long, shown: List<HistoryEventEntity>): Long =
        maxOf(seenUntil, shown.maxOfOrNull { it.createdAt } ?: seenUntil)

    fun retentionCutoff(now: Long): Long = now - RETENTION_DAYS * 24 * 60 * 60 * 1000

    /** Einträge nach Kalendertag gruppieren (neueste zuerst, Reihenfolge innerhalb des Tags bleibt). */
    fun groupByDay(
        events: List<HistoryEventEntity>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Pair<LocalDate, List<HistoryEventEntity>>> =
        events.sortedByDescending { it.createdAt }
            .groupBy { Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() }
            .toList()

    /** Vom Nutzer eingegebener Name bzw. Nachrichtentext: getrimmt, begrenzt, leer → null. */
    fun cleanText(text: String?, max: Int): String? = text?.trim()?.replace(Regex("\\s+"), " ")?.take(max)?.ifEmpty { null }
}
