package de.frelina.vorrat.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Kalenderlogik des Wochenplans (Woche Montag bis Sonntag). */
object PlanWeek {
    /** Anzahl Tage, die beim Hinzufügen zur Auswahl stehen (heute + 13). */
    const val OFFER_DAYS = 14

    fun monday(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Die sieben Tage der Woche ab [weekStart] (Montag). */
    fun days(weekStart: LocalDate): List<LocalDate> = (0L..6L).map(weekStart::plusDays)

    /** Tage zur Auswahl: heute und die folgenden [OFFER_DAYS] − 1 Tage. */
    fun offerDays(today: LocalDate): List<LocalDate> = (0L until OFFER_DAYS.toLong()).map(today::plusDays)

    /**
     * Zeitraum für die Einkaufsliste: die ganze Woche, oder – mit [fromToday] und wenn [today] in der Woche liegt –
     * ab heute bis Sonntag. Liegt heute nicht in der Woche, gilt immer die ganze Woche.
     */
    fun shoppingRange(weekStart: LocalDate, today: LocalDate, fromToday: Boolean): Pair<LocalDate, LocalDate> {
        val end = weekStart.plusDays(6)
        val inWeek = !today.isBefore(weekStart) && !today.isAfter(end)
        return if (fromToday && inWeek) today to end else weekStart to end
    }

    /** Gibt es überhaupt eine Wahl "ab heute"? (Heute liegt in der Woche, aber nicht am Montag.) */
    fun hasFromTodayChoice(weekStart: LocalDate, today: LocalDate): Boolean =
        shoppingRange(weekStart, today, fromToday = true).first != weekStart
}
