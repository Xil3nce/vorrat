package de.frelina.vorrat.ui.recipes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import de.frelina.vorrat.R
import de.frelina.vorrat.data.chefkoch.ChefkochResult
import de.frelina.vorrat.data.chefkoch.ChefkochSource
import de.frelina.vorrat.data.repo.RecipeRepository
import de.frelina.vorrat.domain.ChefkochLink
import de.frelina.vorrat.domain.IngredientMatcher
import de.frelina.vorrat.domain.Recipe
import de.frelina.vorrat.domain.RecipeIngredient
import de.frelina.vorrat.domain.RecipeJson
import de.frelina.vorrat.domain.Validated
import de.frelina.vorrat.domain.Validation
import de.frelina.vorrat.domain.ValidationError
import de.frelina.vorrat.ui.components.MessageTone
import de.frelina.vorrat.ui.components.UiMessage
import de.frelina.vorrat.ui.components.UiMessages
import de.frelina.vorrat.ui.navigation.EditRecipeRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Eine Zutatenzeile im Editor. [groupManual]: Gruppe wurde gewählt und wird nicht mehr automatisch vorgeschlagen. */
data class IngredientRowState(
    val key: Long,
    val name: String = "",
    val amount: String = "",
    val group: String? = null,
    val groupManual: Boolean = false,
    val optional: Boolean = false,
)

data class EditRecipeState(
    val loading: Boolean = true,
    /** Bearbeitet ein bestehendes eigenes Rezept (sonst: neu bzw. Kopie). */
    val editingExisting: Boolean = false,
    val title: String = "",
    val minutes: String = "",
    val servings: String = "2",
    val vegetarian: Boolean = false,
    val rows: List<IngredientRowState> = emptyList(),
    val steps: String = "",
    val titleError: ValidationError? = null,
    val minutesError: ValidationError? = null,
    val servingsError: ValidationError? = null,
    val saved: Boolean = false,
    /** Rezept wird gerade von Chefkoch geladen. */
    val importing: Boolean = false,
)

@HiltViewModel
class EditRecipeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: RecipeRepository,
    private val messages: UiMessages,
    private val chefkoch: ChefkochSource,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<EditRecipeRoute>()
    private val sourceId: String? = route.id
    private var nextKey = 0L

    private val _state = MutableStateFlow(EditRecipeState())
    val state: StateFlow<EditRecipeState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            load()
            route.importText?.let { importFrom(it) }
        }
    }

    /**
     * Lädt ein Rezept von Chefkoch (Link oder geteilter Text mit Link) und füllt das Formular damit.
     * Gespeichert wird erst mit "Speichern" – vorher kann alles angepasst werden.
     */
    fun importFrom(text: String) {
        val id = ChefkochLink.recipeId(text)
        if (id == null) {
            messages.show(UiMessage(R.string.recipe_import_invalid_link, tone = MessageTone.ERROR))
            return
        }
        if (_state.value.importing) return
        _state.update { it.copy(importing = true) }
        viewModelScope.launch {
            when (val result = chefkoch.load(id)) {
                is ChefkochResult.Ok -> {
                    fill(result.recipe, editingExisting = false)
                    messages.show(UiMessage(R.string.recipe_import_done))
                }
                ChefkochResult.NotFound -> messages.show(UiMessage(R.string.recipe_import_not_found, tone = MessageTone.ERROR))
                ChefkochResult.NoNetwork -> messages.show(UiMessage(R.string.recipe_import_no_network, tone = MessageTone.ERROR))
                ChefkochResult.Failed -> messages.show(UiMessage(R.string.recipe_import_failed, tone = MessageTone.ERROR))
            }
            _state.update { it.copy(importing = false) }
        }
    }

    private suspend fun load() {
        val recipe = sourceId?.let { repository.get(it) }
        if (recipe == null) {
            _state.value = EditRecipeState(loading = false, rows = listOf(newRow()))
            return
        }
        fill(recipe, editingExisting = !recipe.builtin)
    }

    private fun fill(recipe: Recipe, editingExisting: Boolean) {
        _state.value = EditRecipeState(
            loading = false,
            editingExisting = editingExisting,
            title = recipe.title,
            minutes = recipe.minutes?.toString().orEmpty(),
            servings = recipe.servings?.toString().orEmpty(),
            vegetarian = recipe.vegetarian,
            rows = recipe.ingredients.map {
                IngredientRowState(nextKey++, it.name, it.amount.orEmpty(), it.group, groupManual = it.group != null, optional = it.optional)
            }.ifEmpty { listOf(newRow()) },
            steps = RecipeJson.stepsToText(recipe.steps),
        )
    }

    private fun newRow() = IngredientRowState(nextKey++)

    fun setTitle(v: String) = _state.update { it.copy(title = v.take(MAX_TITLE), titleError = null) }
    fun setMinutes(v: String) = _state.update { it.copy(minutes = v.filter(Char::isDigit).take(4), minutesError = null) }
    fun setServings(v: String) = _state.update { it.copy(servings = v.filter(Char::isDigit).take(3), servingsError = null) }
    fun setVegetarian(v: Boolean) = _state.update { it.copy(vegetarian = v) }
    fun setSteps(v: String) = _state.update { it.copy(steps = v.take(MAX_STEPS)) }

    fun addRow() = _state.update { if (it.rows.size >= MAX_ROWS) it else it.copy(rows = it.rows + newRow()) }

    fun removeRow(key: Long) = _state.update { s ->
        val rows = s.rows.filterNot { it.key == key }
        s.copy(rows = rows.ifEmpty { listOf(newRow()) })
    }

    /** Beim Tippen des Namens schlägt der Editor die Gruppe automatisch vor, solange sie nicht manuell gewählt wurde. */
    fun setName(key: Long, name: String) = updateRow(key) { row ->
        row.copy(
            name = name.take(MAX_NAME),
            group = if (row.groupManual) row.group else IngredientMatcher.matchName(name),
        )
    }

    fun setAmount(key: Long, amount: String) = updateRow(key) { it.copy(amount = amount.take(MAX_AMOUNT)) }

    fun setGroup(key: Long, group: String?) = updateRow(key) { it.copy(group = group, groupManual = true) }

    fun setOptional(key: Long, optional: Boolean) = updateRow(key) { it.copy(optional = optional) }

    private fun updateRow(key: Long, transform: (IngredientRowState) -> IngredientRowState) =
        _state.update { s -> s.copy(rows = s.rows.map { if (it.key == key) transform(it) else it }) }

    fun save() = viewModelScope.launch {
        val s = _state.value
        val titleV = Validation.required(s.title)
        val minutesV = Validation.optionalWholeNumber(s.minutes)
        val servingsV = Validation.optionalWholeNumber(s.servings)
        _state.update {
            it.copy(
                titleError = (titleV as? Validated.Error)?.error,
                minutesError = (minutesV as? Validated.Error)?.error,
                servingsError = (servingsV as? Validated.Error)?.error,
            )
        }
        if (titleV !is Validated.Ok || minutesV !is Validated.Ok || servingsV !is Validated.Ok) return@launch
        val ingredients = s.rows.filter { it.name.isNotBlank() }.map {
            RecipeIngredient(
                name = it.name.trim(),
                amount = it.amount.trim().ifEmpty { null },
                group = it.group,
                optional = it.optional,
            )
        }
        val recipe = Recipe(
            // Neu bzw. Kopie eines eingebauten Rezepts: das Repository vergibt eine neue ID.
            id = if (s.editingExisting) sourceId.orEmpty() else "",
            title = titleV.value,
            minutes = minutesV.value?.takeIf { it > 0 },
            servings = servingsV.value?.takeIf { it > 0 },
            ingredients = ingredients,
            steps = RecipeJson.textToSteps(s.steps),
            vegetarian = s.vegetarian,
        )
        repository.save(recipe)
        messages.show(UiMessage(R.string.recipe_saved))
        _state.update { it.copy(saved = true) }
    }

    private companion object {
        const val MAX_TITLE = 100
        const val MAX_NAME = 80
        const val MAX_AMOUNT = 40
        const val MAX_STEPS = 8000
        const val MAX_ROWS = 60
    }
}
