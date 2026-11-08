package de.frelina.vorrat.domain

import java.time.LocalDate

/** Geöffnete Packungen: Haltbarkeit nach dem Öffnen. */
object OpenedLogic {
    /** Obergrenze für die Tage nach dem Öffnen (Eingabe und Sync). */
    const val MAX_DAYS = 3650

    /**
     * Verbrauchen bis = [openedAt] + [openedDays]; maßgeblich ist das frühere von diesem Datum und [expiry].
     * Ohne Öffnungsdaten bleibt es beim Ablaufdatum.
     */
    fun effectiveExpiry(expiry: LocalDate?, openedAt: LocalDate?, openedDays: Int?): LocalDate? {
        val useBy = useBy(openedAt, openedDays) ?: return expiry
        return if (expiry == null || useBy.isBefore(expiry)) useBy else expiry
    }

    fun useBy(openedAt: LocalDate?, openedDays: Int?): LocalDate? =
        if (openedAt != null && openedDays != null && openedDays >= 0) openedAt.plusDays(openedDays.toLong()) else null

    private val DAYS = Regex("""(\d{1,3})\s*(?:Tag|Tage|Tagen|days?|jours?)\b""", RegexOption.IGNORE_CASE)
    private val WEEKS = Regex("""(\d{1,2})\s*(?:Woche|Wochen|weeks?)\b""", RegexOption.IGNORE_CASE)
    private val OPENED = Regex("""(ge(ö|oe)ffnet|Öffnen|Oeffnen|opening|opened|ouverture)""", RegexOption.IGNORE_CASE)

    /**
     * Haltbarkeit nach dem Öffnen aus einem OFF-Lagerhinweis, z. B. "Nach dem Öffnen gekühlt aufbewahren und
     * innerhalb von 3 Tagen verbrauchen" → 3. Nur wenn der Text vom Öffnen spricht; sonst null.
     */
    fun daysFromHint(text: String?): Int? {
        if (text.isNullOrBlank() || !OPENED.containsMatchIn(text)) return null
        DAYS.find(text)?.let { m -> return m.groupValues[1].toInt().takeIf { it in 1..MAX_DAYS } }
        WEEKS.find(text)?.let { m -> return (m.groupValues[1].toInt() * 7).takeIf { it in 1..MAX_DAYS } }
        return null
    }
}
