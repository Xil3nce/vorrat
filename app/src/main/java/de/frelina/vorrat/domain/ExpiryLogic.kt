package de.frelina.vorrat.domain

import java.time.LocalDate

enum class ExpiryStatus {
    /** Kein Ablaufdatum gesetzt. */
    NONE,

    /** Ablaufdatum liegt nach dem Vorlauf-Zeitraum. */
    OK,

    /** Läuft heute oder innerhalb der nächsten [ExpiryLogic.status]-Vorlauftage ab. */
    SOON,

    /** Ablaufdatum liegt vor heute. */
    EXPIRED,
}

object ExpiryLogic {
    const val DEFAULT_LEAD_DAYS = 3

    /**
     * - EXPIRED: expiry < today
     * - SOON:    today <= expiry <= today + leadDays (heute ablaufend zählt als "bald")
     * - OK:      expiry > today + leadDays
     */
    fun status(expiry: LocalDate?, today: LocalDate, leadDays: Int): ExpiryStatus {
        if (expiry == null) return ExpiryStatus.NONE
        return when {
            expiry.isBefore(today) -> ExpiryStatus.EXPIRED
            !expiry.isAfter(today.plusDays(leadDays.coerceAtLeast(0).toLong())) -> ExpiryStatus.SOON
            else -> ExpiryStatus.OK
        }
    }

    fun isExpiringToday(expiry: LocalDate?, today: LocalDate): Boolean = expiry == today
}
