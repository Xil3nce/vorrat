package de.frelina.vorrat.data.chefkoch

import de.frelina.vorrat.BuildConfig
import de.frelina.vorrat.domain.ChefkochMapper
import de.frelina.vorrat.domain.ChefkochRecipeDto
import de.frelina.vorrat.domain.Recipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ChefkochResult {
    /** [imageUrl]: Vorschaubild des Rezepts zum Nachladen mit [ChefkochSource.downloadImage], falls vorhanden. */
    data class Ok(val recipe: Recipe, val imageUrl: String? = null) : ChefkochResult
    data object NotFound : ChefkochResult
    data object NoNetwork : ChefkochResult
    data object Failed : ChefkochResult
}

/**
 * Lädt ein einzelnes Rezept von Chefkoch (inoffizielle JSON-Schnittstelle der Chefkoch-App).
 * Nur auf Wunsch des Nutzers für ein geteiltes/eingefügtes Rezept – keine Massenabfragen.
 */
@Singleton
class ChefkochSource @Inject constructor(
    client: OkHttpClient,
    private val json: Json,
) {
    // Eigener User-Agent ohne die OFF-Kontaktadresse (die ist nur für Open Food Facts gedacht).
    private val client = client.newBuilder()
        .apply { interceptors().clear() }
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", "Vorrat/${BuildConfig.VERSION_NAME}").build())
        }
        .build()

    suspend fun load(recipeId: String): ChefkochResult = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$BASE_URL/recipes/$recipeId").header("Accept", "application/json").build()
        try {
            client.newCall(request).execute().use { r ->
                when {
                    r.code == 404 -> ChefkochResult.NotFound
                    !r.isSuccessful -> ChefkochResult.Failed
                    else -> {
                        val dto = json.decodeFromString<ChefkochRecipeDto>(r.body.string())
                        val recipe = ChefkochMapper.toRecipe(dto)
                        if (recipe.title.isBlank() || recipe.ingredients.isEmpty()) ChefkochResult.Failed
                        else ChefkochResult.Ok(recipe, ChefkochMapper.imageUrl(dto))
                    }
                }
            }
        } catch (e: SerializationException) {
            ChefkochResult.Failed
        } catch (e: IllegalArgumentException) {
            ChefkochResult.Failed
        } catch (e: IOException) {
            ChefkochResult.NoNetwork
        }
    }

    /** Bild laden (höchstens [MAX_IMAGE_BYTES]); null bei Fehler – das Rezept wird dann ohne Bild übernommen. */
    suspend fun downloadImage(url: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                val length = r.body.contentLength()
                if (!r.isSuccessful || length > MAX_IMAGE_BYTES) null
                else r.body.bytes().takeIf { it.isNotEmpty() && it.size <= MAX_IMAGE_BYTES }
            }
        }.getOrNull()
    }

    private companion object {
        const val MAX_IMAGE_BYTES = 5L * 1024 * 1024
        const val BASE_URL = "https://api.chefkoch.de/v2"
    }
}
