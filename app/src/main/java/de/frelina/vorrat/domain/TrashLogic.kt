package de.frelina.vorrat.domain

import java.util.concurrent.TimeUnit

/** Aufbewahrung im Papierkorb: gelöschte Einträge und Rezepte bleiben [RETENTION_DAYS] Tage wiederherstellbar. */
object TrashLogic {
    const val RETENTION_DAYS = 30
    private val RETENTION_MS = TimeUnit.DAYS.toMillis(RETENTION_DAYS.toLong())

    fun isExpired(deletedAt: Long, now: Long): Boolean = now - deletedAt >= RETENTION_MS

    /** Verbleibende ganze Tage bis zum endgültigen Löschen (mindestens 1, solange nicht abgelaufen; danach 0). */
    fun daysLeft(deletedAt: Long, now: Long): Int {
        val left = deletedAt + RETENTION_MS - now
        if (left <= 0) return 0
        return ((left + TimeUnit.DAYS.toMillis(1) - 1) / TimeUnit.DAYS.toMillis(1)).toInt().coerceIn(1, RETENTION_DAYS)
    }
}
