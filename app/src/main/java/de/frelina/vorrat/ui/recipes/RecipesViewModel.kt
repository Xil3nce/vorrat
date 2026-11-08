package de.frelina.vorrat.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.frelina.vorrat.data.repo.RecipeRepository
import de.frelina.vorrat.data.repo.StockRepository
import de.frelina.vorrat.data.settings.SettingsRepository
import de.frelina.vorrat.domain.ExpiringGroup
import de.frelina.vorrat.domain.IngredientMatcher
import de.frelina.vorrat.domain.RecipeMatch
import de.frelina.vorrat.domain.RecipeMatcher
import de.frelina.vorrat.domain.toPantryEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

enum class RecipeFilter { ALL, OWN, VEGETARIAN }

data class RecipesUiState(
    val loading: Boolean = true,
    val matches: List<RecipeMatch> = emptyList(),
    val expiring: List<ExpiringGroup> = emptyList(),
    val query: String = "",
    val filter: RecipeFilter = RecipeFilter.ALL,
    val selectedGroup: String? = null,
    /** Alle Rezepte zeigen (auch ohne Treffer) statt nur der Vorschläge. */
    val showAll: Boolean = false,
    val today: LocalDate = LocalDate.now(),
)

private data class Controls(
    val query: String = "",
    val filter: RecipeFilter = RecipeFilter.ALL,
    val selectedGroup: String? = null,
    val showAll: Boolean = false,
)

@HiltViewModel
class RecipesViewModel @Inject constructor(
    recipeRepository: RecipeRepository,
    stockRepository: StockRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val controls = MutableStateFlow(Controls())

    val state: StateFlow<RecipesUiState> = combine(
        recipeRepository.observeAll(),
        stockRepository.observeItems(),
        settingsRepository.reminderSettings,
        controls,
    ) { recipes, items, settings, c ->
        val today = LocalDate.now()
        val pantry = items.map { it.toPantryEntry() }
        // Beim Suchen immer in allen Rezepten, sonst nur Vorschläge (oder alle, wenn eingeschaltet).
        val searching = c.query.isNotBlank()
        val ranked = if (c.showAll || searching) {
            RecipeMatcher.rankAll(recipes, pantry, today)
        } else {
            RecipeMatcher.rank(recipes, pantry, today)
        }
        val q = IngredientMatcher.normalize(c.query)
        val matches = ranked
            .filter {
                when (c.filter) {
                    RecipeFilter.ALL -> true
                    RecipeFilter.OWN -> !it.recipe.builtin
                    RecipeFilter.VEGETARIAN -> it.recipe.vegetarian
                }
            }
            .filter { m -> c.selectedGroup == null || m.have.any { it.groupId == c.selectedGroup } }
            .filter { m ->
                q.isEmpty() || IngredientMatcher.normalize(m.recipe.title).contains(q) ||
                    m.recipe.ingredients.any { IngredientMatcher.normalize(it.name).contains(q) }
            }
        RecipesUiState(
            loading = false,
            matches = matches,
            expiring = RecipeMatcher.expiringGroups(pantry, today, settings.leadDays),
            query = c.query,
            filter = c.filter,
            selectedGroup = c.selectedGroup,
            showAll = c.showAll,
            today = today,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipesUiState())

    fun setQuery(q: String) = controls.value.let { controls.value = it.copy(query = q) }
    fun setFilter(f: RecipeFilter) = controls.value.let { controls.value = it.copy(filter = f) }
    fun setShowAll(on: Boolean) = controls.value.let { controls.value = it.copy(showAll = on) }

    /** Tippen auf einen Chip filtert auf Rezepte mit dieser Gruppe, erneutes Tippen hebt den Filter auf. */
    fun toggleGroup(groupId: String) = controls.value.let {
        controls.value = it.copy(selectedGroup = if (it.selectedGroup == groupId) null else groupId)
    }
}
