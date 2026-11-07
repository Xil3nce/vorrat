package de.frelina.vorrat.data.off

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Gleitendes Fenster: höchstens [maxRequests] Anfragen pro [windowMillis].
 * OFF-Doku: 15 Produkt-Leseanfragen pro Minute und IP (Stand 2026-11-07).
 */
class RateLimiter(
    private val maxRequests: Int,
    private val windowMillis: Long,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val timestamps = ArrayDeque<Long>()

    /** true = Anfrage erlaubt (und wird gezählt), false = Limit erreicht. */
    suspend fun tryAcquire(): Boolean = mutex.withLock {
        val now = clock()
        while (timestamps.isNotEmpty() && now - timestamps.first() >= windowMillis) {
            timestamps.removeFirst()
        }
        if (timestamps.size >= maxRequests) return@withLock false
        timestamps.addLast(now)
        true
    }
}
