package de.frelina.vorrat.ui.recipes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import de.frelina.vorrat.R
import de.frelina.vorrat.data.repo.RecipeRepository
import de.frelina.vorrat.data.repo.ShoppingRepository
import de.frelina.vorrat.data.repo.StockRepository
import de.frelina.vorrat.domain.RecipeMatch
import de.frelina.vorrat.domain.RecipeMatcher
import de.frelina.vorrat.domain.toPantryEntry
import de.frelina.vorrat.ui.components.UiMessage
import de.frelina.vorrat.ui.components.UiMessages
import de.frelina.vorrat.ui.navigation.RecipeDetailRoute
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class RecipeDetailUiState(
    val loading: Boolean = true,
    /** null nach dem Laden = Rezept nicht (mehr) vorhanden. */
    val match: RecipeMatch? = null,
    val today: LocalDate = LocalDate.now(),
)

@HiltViewModel
class RecipeDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val recipes: RecipeRepository,
    private val shopping: ShoppingRepository,
    private val stockRepository: StockRepository,
    private val messages: UiMessages,
) : ViewModel() {

    val recipeId: String = savedStateHandle.toRoute<RecipeDetailRoute>().id

    val state: StateFlow<RecipeDetailUiState> = combine(recipes.observe(recipeId), stockRepository.observeItems()) { recipe, items ->
        val today = LocalDate.now()
        RecipeDetailUiState(
            loading = false,
            match = recipe?.let { RecipeMatcher.evaluate(it, items.map { item -> item.toPantryEntry() }, today) },
            today = today,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipeDetailUiState())

    /** Fehlende Pflichtzutaten auf die Einkaufsliste (ohne Duplikate gleichen Namens). */
    fun addMissingToShopping() = viewModelScope.launch {
        val missing = state.value.match?.missing?.map { it.name }.orEmpty()
        val added = shopping.addMissing(missing)
        messages.show(
            if (added > 0) UiMessage(R.string.recipe_missing_added) else UiMessage(R.string.recipe_missing_none_added),
        )
    }

    /** "Gekocht": je Eintrag die Anzahl entnehmen (Verlauf wie beim normalen Entnehmen). */
    fun cooked(amounts: Map<String, Int>) = viewModelScope.launch {
        var taken = 0
        amounts.forEach { (entryId, count) ->
            if (count > 0) {
                stockRepository.changeQuantity(entryId, -count)
                taken++
            }
        }
        if (taken > 0) messages.show(UiMessage(R.string.recipe_cooked_done))
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        recipes.delete(recipeId)
        onDone()
    }
}
