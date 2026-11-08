package de.frelina.vorrat.data.off

import de.frelina.vorrat.domain.CategoryResolver
import de.frelina.vorrat.domain.NutritionParser
import de.frelina.vorrat.domain.QuantityParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
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
    private val client: OkHttpClient,
) : ProductLookupSource {

    private val limiter = RateLimiter(maxRequests = 15, windowMillis = 60_000)

    override suspend fun lookup(barcode: String): LookupResult {
        if (!limiter.tryAcquire()) return LookupResult.Failed(LookupErrorKind.RATE_LIMITED)
        return try {
            val response = api.product(barcode)
            when {
                response.code() == 404 -> LookupResult.NotFound
                response.code() == 429 -> LookupResult.Failed(LookupErrorKind.RATE_LIMITED)
                !response.isSuccessful -> LookupResult.Failed(LookupErrorKind.SERVER)
                else -> map(barcode, response.body())
            }
        } catch (e: SocketTimeoutException) {
            LookupResult.Failed(LookupErrorKind.TIMEOUT)
        } catch (e: InterruptedIOException) {
            LookupResult.Failed(LookupErrorKind.TIMEOUT)
        } catch (e: UnknownHostException) {
            LookupResult.Failed(LookupErrorKind.NO_NETWORK)
        } catch (e: ConnectException) {
            LookupResult.Failed(LookupErrorKind.NO_NETWORK)
        } catch (e: IOException) {
            LookupResult.Failed(LookupErrorKind.NO_NETWORK)
        } catch (e: SerializationException) {
            LookupResult.Failed(LookupErrorKind.SERVER)
        }
    }

    override suspend fun downloadImage(url: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (r.isSuccessful) r.body.bytes() else null
            }
        }.getOrNull()
    }

    companion object {
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
                ),
            )
        }
    }
}
