package de.frelina.vorrat.data.off

import de.frelina.vorrat.domain.Nutrition
import java.math.BigDecimal

/** Produktdaten aus einer externen Quelle (derzeit nur Open Food Facts). */
data class LookedUpProduct(
    val barcode: String,
    val name: String?,
    val brand: String?,
    val category: String?,
    val packageSize: BigDecimal?,
    val unitSymbol: String?,
    val imageUrl: String?,
    /** Nährwerte je 100 g/ml, null wenn OFF keine hat. */
    val nutrition: Nutrition? = null,
    /** Haltbarkeit nach dem Öffnen laut Lagerhinweis (Tage), falls erkennbar. */
    val openedDays: Int? = null,
)

enum class LookupErrorKind { NO_NETWORK, TIMEOUT, RATE_LIMITED, SERVER }

sealed interface LookupResult {
    data class Found(val product: LookedUpProduct) : LookupResult
    data object NotFound : LookupResult
    data class Failed(val kind: LookupErrorKind) : LookupResult
}

/**
 * Abstraktion für Barcode-Abfragen. Bewusst gibt es nur Open Food Facts; weitere Quellen
 * können später als zusätzliche Implementierung ergänzt werden.
 */
interface ProductLookupSource {
    suspend fun lookup(barcode: String): LookupResult

    /** Lädt ein Produktbild herunter (null bei Fehler). */
    suspend fun downloadImage(url: String): ByteArray?
}
