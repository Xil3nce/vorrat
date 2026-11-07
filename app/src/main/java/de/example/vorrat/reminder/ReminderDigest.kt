package de.example.vorrat.reminder

import de.example.vorrat.data.db.StockItem
import de.example.vorrat.domain.ExpiryLogic
import de.example.vorrat.domain.ExpiryStatus
import java.time.LocalDate

/** Inhalt der täglichen Sammelbenachrichtigung. */
data class ReminderDigest(
    val expired: List<StockItem>,
    val today: List<StockItem>,
    val soon: List<StockItem>,
) {
    val isEmpty: Boolean get() = expired.isEmpty() && today.isEmpty() && soon.isEmpty()
    val total: Int get() = expired.size + today.size + soon.size

    companion object {
        fun from(items: List<StockItem>, today: LocalDate, leadDays: Int): ReminderDigest {
            val sorted = items.sortedBy { it.expiryDate }
            val expired = mutableListOf<StockItem>()
            val dueToday = mutableListOf<StockItem>()
            val soon = mutableListOf<StockItem>()
            sorted.forEach { item ->
                when (ExpiryLogic.status(item.expiryDate, today, leadDays)) {
                    ExpiryStatus.EXPIRED -> expired += item
                    ExpiryStatus.SOON ->
                        if (ExpiryLogic.isExpiringToday(item.expiryDate, today)) dueToday += item else soon += item
                    else -> Unit
                }
            }
            return ReminderDigest(expired, dueToday, soon)
        }
    }
}
