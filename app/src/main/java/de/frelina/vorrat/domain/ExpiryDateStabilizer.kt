package de.frelina.vorrat.domain

import java.time.LocalDate

/**
 * Glättet die laufend erkannten Ablaufdaten der Kamera: Ein Datum gilt erst als erkannt, wenn es unter den
 * letzten [window] Lesungen (Frames ohne Datum zählen nicht) mindestens [required]-mal vorkam. So setzt sich ein
 * einzelner Lesefehler ("28" statt "26") nicht durch. Das zuletzt bestätigte Datum bleibt stehen, bis ein anderes
 * bestätigt wird.
 */
class ExpiryDateStabilizer(private val window: Int = 5, private val required: Int = 3) {
    private val recent = ArrayDeque<LocalDate>()

    var confirmed: LocalDate? = null
        private set

    /** Neue Lesung melden; liefert das (ggf. unveränderte) bestätigte Datum. */
    fun offer(date: LocalDate?): LocalDate? {
        if (date == null) return confirmed
        recent.addLast(date)
        while (recent.size > window) recent.removeFirst()
        val best = recent.groupingBy { it }.eachCount().maxByOrNull { it.value }
        if (best != null && best.value >= required) confirmed = best.key
        return confirmed
    }
}
