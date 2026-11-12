package de.frelina.vorrat.data.off

import de.frelina.vorrat.domain.CategoryResolver
import de.frelina.vorrat.domain.NutritionParser
import de.frelina.vorrat.domain.OpenedLogic
import de.frelina.vorrat.domain.QuantityParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenFoodFactsSource @Inject constructor(
    private val api: OffApi,
    private val searchApi: OffSearchApi,
    private val client: OkHttpClient,
) : ProductLookupSource {

    private val limiter = RateLimiter(maxRequests = 15, windowMillis = 60_000)

    /** OFF-Doku: höchstens 10 Suchanfragen pro Minute und IP. */
    private val searchLimiter = RateLimiter(maxRequests = 10, windowMillis = 60_000)

    override suspend fun lookup(barcode: String): LookupResult {
        if (!limiter.tryAcquire()) return LookupResult.Failed(LookupErrorKind.RATE_LIMITED)
        return guarded(LookupResult::Failed) {
            val response = api.product(barcode)
            when {
                response.code() == 404 -> LookupResult.NotFound
                response.code() == 429 -> LookupResult.Failed(LookupErrorKind.RATE_LIMITED)
                !response.isSuccessful -> LookupResult.Failed(LookupErrorKind.SERVER)
                else -> map(barcode, response.body())
            }
        }
    }

    override suspend fun search(query: String): SearchResult {
        val q = searchQuery(query) ?: return SearchResult.Hits(emptyList())
        if (!searchLimiter.tryAcquire()) return SearchResult.Failed(LookupErrorKind.RATE_LIMITED)
        return guarded(SearchResult::Failed) {
            val response = searchApi.search(q)
            when {
                response.code() == 429 || response.code() == 503 -> SearchResult.Failed(LookupErrorKind.RATE_LIMITED)
                !response.isSuccessful -> SearchResult.Failed(LookupErrorKind.SERVER)
                else -> SearchResult.Hits(mapHits(response.body()))
            }
        }
    }

    /** Netzwerk- und Parserfehler einheitlich in [LookupErrorKind] übersetzen. */
    private inline fun <T> guarded(failed: (LookupErrorKind) -> T, block: () -> T): T = try {
        block()
    } catch (e: SocketTimeoutException) {
        failed(LookupErrorKind.TIMEOUT)
    } catch (e: InterruptedIOException) {
        failed(LookupErrorKind.TIMEOUT)
    } catch (e: UnknownHostException) {
        failed(LookupErrorKind.NO_NETWORK)
    } catch (e: ConnectException) {
        failed(LookupErrorKind.NO_NETWORK)
    } catch (e: IOException) {
        failed(LookupErrorKind.NO_NETWORK)
    } catch (e: SerializationException) {
        failed(LookupErrorKind.SERVER)
    }

    override suspend fun downloadImage(url: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (r.isSuccessful) r.body.bytes() else null
            }
        }.getOrNull()
    }

    companion object {
        /** Mindestlänge des Suchbegriffs (ohne Leerzeichen am Rand). */
        const val MIN_QUERY_LENGTH = 3

        fun map(barcode: String, body: OffProductResponse?): LookupResult {
            val product = body?.product
            if (body == null || body.status != 1 || product == null) return LookupResult.NotFound
            val quantity = QuantityParser.parse(
                product.quantity,
                product.productQuantityText,
                product.productQuantityUnit,
            )
            return LookupResult.Found(
                LookedUpProduct(
                    barcode = barcode,
                    name = product.productNameDe?.takeIf { it.isNotBlank() }
                        ?: product.productName?.takeIf { it.isNotBlank() },
                    // `brands` ist eine kommagetrennte Liste → erste Marke verwenden.
                    brand = product.brands?.split(',')?.map { it.trim() }?.firstOrNull { it.isNotEmpty() },
                    category = CategoryResolver.mostSpecific(product.categoriesTags),
                    packageSize = quantity?.size,
                    unitSymbol = quantity?.unitSymbol,
                    imageUrl = product.imageFrontUrl?.takeIf { it.isNotBlank() }
                        ?: product.imageUrl?.takeIf { it.isNotBlank() },
                    nutrition = NutritionParser.fromNutriments(product.nutriments, quantity?.unitSymbol),
                    openedDays = OpenedLogic.daysFromHint(product.conservationConditionsDe)
                        ?: OpenedLogic.daysFromHint(product.conservationConditions),
                ),
            )
        }

        /**
         * Suchbegriff für Search-a-licious: Lucene-Sonderzeichen entfernen (Eingabe ist Freitext, kein Filter)
         * und auf in Deutschland verkaufte Produkte einschränken. Zu kurz → null (keine Anfrage).
         */
        fun searchQuery(input: String): String? {
            val terms = input.replace(LUCENE_SPECIAL, " ").trim().replace(Regex("\\s+"), " ")
            if (terms.length < MIN_QUERY_LENGTH) return null
            return "$terms countries_tags:\"en:germany\""
        }

        /** Treffer ohne Barcode oder Namen auslassen, doppelte Barcodes nur einmal. */
        fun mapHits(body: OffSearchResponse?): List<LookedUpProduct> =
            body?.hits.orEmpty().mapNotNull { hit ->
                val code = hit.code?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val name = hit.productNameDe?.trim()?.takeIf { it.isNotEmpty() }
                    ?: hit.productName?.trim()?.takeIf { it.isNotEmpty() }
                    ?: return@mapNotNull null
                val quantity = QuantityParser.parse(
                    hit.quantity,
                    (hit.productQuantity as? JsonPrimitive)?.contentOrNull,
                    hit.productQuantityUnit,
                )
                LookedUpProduct(
                    barcode = code,
                    name = name,
                    brand = firstBrand(hit.brands),
                    category = CategoryResolver.mostSpecific(hit.categoriesTags),
                    packageSize = quantity?.size,
                    unitSymbol = quantity?.unitSymbol,
                    imageUrl = hit.imageFrontSmallUrl?.takeIf { it.isNotBlank() },
                )
            }.distinctBy { it.barcode }

        private fun firstBrand(brands: JsonElement?): String? = when (brands) {
            is JsonArray -> brands.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            is JsonPrimitive -> brands.contentOrNull?.split(',').orEmpty()
            else -> emptyList()
        }.map { it.trim() }.firstOrNull { it.isNotEmpty() }

        private val LUCENE_SPECIAL = Regex("""[+\-!(){}\[\]^"~*?:\\/&|]""")
    }
}
