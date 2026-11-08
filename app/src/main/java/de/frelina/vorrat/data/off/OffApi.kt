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
                "product_quantity_unit,image_front_url,image_url,categories_tags,nutriments"
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
) {
    val productQuantityText: String?
        get() = (productQuantity as? JsonPrimitive)?.contentOrNull
}
