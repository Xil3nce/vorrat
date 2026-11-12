package de.frelina.vorrat.data.repo

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import de.frelina.vorrat.data.db.RecipeDao
import de.frelina.vorrat.data.db.RecipeEntity
import de.frelina.vorrat.data.db.newId
import de.frelina.vorrat.data.image.ImageStore
import de.frelina.vorrat.data.sync.RemoteCollection
import de.frelina.vorrat.data.sync.RemoteMapper
import de.frelina.vorrat.data.sync.RemoteWriter
import de.frelina.vorrat.data.trash.TrashItem
import de.frelina.vorrat.data.trash.TrashKind
import de.frelina.vorrat.data.trash.TrashStore
import de.frelina.vorrat.data.trash.TrashedRecipe
import de.frelina.vorrat.domain.Recipe
import de.frelina.vorrat.domain.RecipeJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Quelle der eingebauten Rezepte (in der App: assets/recipes.json; in Tests austauschbar). */
fun interface BuiltinRecipeSource {
    fun load(): List<Recipe>
}

class AssetRecipeSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : BuiltinRecipeSource {
    override fun load(): List<Recipe> = runCatching {
        context.assets.open(ASSET_NAME).bufferedReader(Charsets.UTF_8).use { RecipeJson.parseRecipes(it.readText()) }
    }.getOrDefault(emptyList())

    companion object {
        const val ASSET_NAME = "recipes.json"
    }
}

/** Eingebaute und eigene Rezepte als gemeinsames Modell [Recipe]. Eigene werden im Haushalt synchronisiert. */
@Singleton
class RecipeRepository @Inject constructor(
    private val dao: RecipeDao,
    private val remote: RemoteWriter,
    private val imageStore: ImageStore,
    builtinSource: BuiltinRecipeSource,
    /** Papierkorb (null in Tests ohne Papierkorb: dann wird sofort endgültig gelöscht). */
    private val trash: TrashStore? = null,
) {
    val builtin: List<Recipe> by lazy { builtinSource.load() }

    /** Alle Rezepte: eigene zuerst nach Titel, danach die eingebauten. */
    fun observeAll(): Flow<List<Recipe>> = dao.observeAll().map { own -> own.map(::toRecipe) + builtin }

    fun observe(id: String): Flow<Recipe?> =
        if (id.startsWith(Recipe.BUILTIN_PREFIX)) {
            kotlinx.coroutines.flow.flowOf(builtin.firstOrNull { it.id == id })
        } else {
            dao.observeById(id).map { it?.let(::toRecipe) }
        }

    suspend fun get(id: String): Recipe? =
        if (id.startsWith(Recipe.BUILTIN_PREFIX)) builtin.firstOrNull { it.id == id } else dao.getById(id)?.let(::toRecipe)

    /**
     * Speichert ein eigenes Rezept (neu oder geändert). Eine ID mit "builtin:" oder ohne Eintrag ergibt ein neues
     * Rezept – so wird aus einem eingebauten Rezept eine eigene Kopie. Liefert die ID.
     */
    suspend fun save(recipe: Recipe): String {
        val existing = if (recipe.builtin) null else dao.getById(recipe.id)
        val now = System.currentTimeMillis()
        val entity = RecipeEntity(
            id = if (existing != null) recipe.id else newId(),
            title = recipe.title.trim().take(RemoteMapper.MAX_TITLE),
            minutes = recipe.minutes?.takeIf { it in 1..RemoteMapper.MAX_MINUTES },
            servings = recipe.servings?.takeIf { it in 1..RemoteMapper.MAX_SERVINGS },
            ingredients = RecipeJson.encodeIngredients(recipe.ingredients.filter { it.name.isNotBlank() }),
            steps = RecipeJson.stepsToText(recipe.steps).take(RemoteMapper.MAX_STEPS),
            vegetarian = recipe.vegetarian,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            imagePath = recipe.imagePath,
        )
        dao.upsert(entity)
        remote.putRecipe(entity, previousImage = existing?.imagePath)
        if (existing?.imagePath != null && existing.imagePath != entity.imagePath) imageStore.delete(existing.imagePath)
        return entity.id
    }

    /**
     * Löscht ein eigenes Rezept. Mit Papierkorb bleibt es 30 Tage auf diesem Gerät wiederherstellbar (das Foto bleibt
     * dafür lokal liegen, nur das synchronisierte Bild verschwindet). Liefert false, wenn es nichts zu löschen gab.
     */
    suspend fun delete(id: String): Boolean {
        if (id.startsWith(Recipe.BUILTIN_PREFIX)) return false
        val existing = dao.getById(id) ?: return false
        val image = existing.imagePath
        dao.deleteById(id)
        remote.delete(RemoteCollection.RECIPES, id)
        if (image != null) remote.delete(RemoteCollection.IMAGES, image)
        if (trash != null) {
            trash.add(
                TrashItem(
                    id = id,
                    kind = TrashKind.RECIPE,
                    title = existing.title,
                    deletedAt = System.currentTimeMillis(),
                    recipe = TrashedRecipe.of(existing),
                ),
            )
        } else if (image != null) {
            imageStore.delete(image)
        }
        return true
    }

    /** Rezept aus dem Papierkorb zurückholen (mit Foto, falls die Datei noch da ist). */
    suspend fun restoreFromTrash(trashed: TrashedRecipe) {
        var entity = trashed.toEntity()
        if (entity.imagePath != null && imageStore.fileOrNull(entity.imagePath) == null) entity = entity.copy(imagePath = null)
        dao.upsert(entity)
        remote.putRecipe(entity)
        trash?.remove(TrashKind.RECIPE, entity.id)
    }

    private fun toRecipe(e: RecipeEntity) = Recipe(
        id = e.id,
        title = e.title,
        minutes = e.minutes,
        servings = e.servings,
        ingredients = RecipeJson.decodeIngredients(e.ingredients),
        steps = RecipeJson.textToSteps(e.steps),
        vegetarian = e.vegetarian,
        imagePath = e.imagePath,
    )
}
