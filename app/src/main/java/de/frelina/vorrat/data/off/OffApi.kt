package de.frelina.vorrat.data.off

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Open Food Facts API v2 – Produktabfrage per Barcode.
 * Doku: https://openfoodfacts.github.io/openfoodfacts-server/api/ (Stand 2026-11-07)
 * Endpoint: GET /api/v2/product/{code}?fields=…
 */
interface OffApi {
    @GET("api/v2/product/{code}")
    suspend fun product(
        @Path("code") code: String,
        @Query("fields") fields: String = FIELDS,
    ): Response<OffProductResponse>

    companion object {
        const val BASE_URL = "https://world.openfoodfacts.org/"

        /** Nur die benötigten, in der OFF-Doku beschriebenen Felder abfragen. */
        const val FIELDS =
            "code,product_name,product_name_de,brands,quantity,product_quantity," +
                "product_quantity_unit,image_front_url,image_url,categories_tags,nutriments," +
                "conservation_conditions_de,conservation_conditions"
    }
}

@Serializable
data class OffProductResponse(
    val code: String? = null,
    /** 1 = gefunden, 0 = nicht gefunden */
    val status: Int? = null,
    @SerialName("status_verbose") val statusVerbose: String? = null,
    val product: OffProduct? = null,
)

@Serializable
data class OffProduct(
    @SerialName("product_name") val productName: String? = null,
    @SerialName("product_name_de") val productNameDe: String? = null,
    val brands: String? = null,
    val quantity: String? = null,
    /** Laut Doku String, in der Praxis teils als Zahl geliefert → JsonElement. */
    @SerialName("product_quantity") val productQuantity: JsonElement? = null,
    @SerialName("product_quantity_unit") val productQuantityUnit: String? = null,
    @SerialName("image_front_url") val imageFrontUrl: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("categories_tags") val categoriesTags: List<String>? = null,
    /** Nährwerte; ausgewertet werden nur die `_100g`-Werte (siehe NutritionParser). */
    val nutriments: JsonObject? = null,
    /** Lagerhinweis, z. B. "Nach dem Öffnen innerhalb von 3 Tagen verbrauchen". */
    @SerialName("conservation_conditions_de") val conservationConditionsDe: String? = null,
    @SerialName("conservation_conditions") val conservationConditions: String? = null,
) {
    val productQuantityText: String?
        get() = (productQuantity as? JsonPrimitive)?.contentOrNull
}

/**
 * Volltextsuche über Search-a-licious (Nachfolger der alten /cgi/search.pl; die v2-API hat keine Volltextsuche).
 * Doku: https://search.openfoodfacts.org/docs (OpenAPI unter /openapi.json, Stand 2026-11-12).
 * `q` durchsucht Produktname, Gattungsname, Kategorien und Labels (je in `langs`) sowie Marken und erlaubt
 * Lucene-Filter wie `countries_tags:"en:germany"`.
 * OFF-Doku: Suchanfragen höchstens 10/min je IP, nicht für Suche beim Tippen geeignet.
 */
interface OffSearchApi {
    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("langs") langs: String = "de",
        @Query("page_size") pageSize: Int = PAGE_SIZE,
        @Query("fields") fields: String = FIELDS,
        @Query("sort_by") sortBy: String = "-unique_scans_n",
    ): Response<OffSearchResponse>

    companion object {
        const val BASE_URL = "https://search.openfoodfacts.org/"
        const val PAGE_SIZE = 20
        const val FIELDS =
            "code,product_name,product_name_de,brands,quantity,product_quantity,product_quantity_unit," +
                "image_front_small_url,categories_tags"
    }
}

@Serializable
data class OffSearchResponse(
    val hits: List<OffSearchHit> = emptyList(),
    val count: Int? = null,
)

@Serializable
data class OffSearchHit(
    val code: String? = null,
    @SerialName("product_name") val productName: String? = null,
    @SerialName("product_name_de") val productNameDe: String? = null,
    /** In Search-a-licious eine Liste, in der Produkt-API ein kommagetrennter Text → JsonElement. */
    val brands: JsonElement? = null,
    val quantity: String? = null,
    @SerialName("product_quantity") val productQuantity: JsonElement? = null,
    @SerialName("product_quantity_unit") val productQuantityUnit: String? = null,
    @SerialName("image_front_small_url") val imageFrontSmallUrl: String? = null,
    @SerialName("categories_tags") val categoriesTags: List<String>? = null,
)
