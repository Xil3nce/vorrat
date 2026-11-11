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

data class RecipesUiState(
    val loading: Boolean = true,
    val matches: List<RecipeMatch> = emptyList(),
    val expiring: List<ExpiringGroup> = emptyList(),
    val query: String = "",
    /** Zusatzfilter, unabhängig kombinierbar (UND); "Alle" gilt, wenn keiner aktiv ist. */
    val onlyOwn: Boolean = false,
    val onlyVegetarian: Boolean = false,
    /** Nur Rezepte, die mit dem Vorrat (fast) kochbar sind. */
    val onlyPantry: Boolean = false,
    val selectedGroup: String? = null,
    val today: LocalDate = LocalDate.now(),
)

private data class Controls(
    val query: String = "",
    val onlyOwn: Boolean = false,
    val onlyVegetarian: Boolean = false,
    val onlyPantry: Boolean = false,
    val selectedGroup: String? = null,
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
        val ranked = if (c.onlyPantry) {
            RecipeMatcher.rank(recipes, pantry, today)
        } else {
            RecipeMatcher.rankAll(recipes, pantry, today)
        }
        val q = IngredientMatcher.normalize(c.query)
        val matches = ranked
            .filter { !c.onlyOwn || !it.recipe.builtin }
            .filter { !c.onlyVegetarian || it.recipe.vegetarian }
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
            onlyOwn = c.onlyOwn,
            onlyVegetarian = c.onlyVegetarian,
            onlyPantry = c.onlyPantry,
            selectedGroup = c.selectedGroup,
            today = today,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipesUiState())

    fun setQuery(q: String) = controls.value.let { controls.value = it.copy(query = q) }
    fun toggleOwn() = controls.value.let { controls.value = it.copy(onlyOwn = !it.onlyOwn) }
    fun toggleVegetarian() = controls.value.let { controls.value = it.copy(onlyVegetarian = !it.onlyVegetarian) }
    fun togglePantry() = controls.value.let { controls.value = it.copy(onlyPantry = !it.onlyPantry) }

    /** "Alle": setzt die Zusatzfilter zurück. */
    fun clearFilters() = controls.value.let {
        controls.value = it.copy(onlyOwn = false, onlyVegetarian = false, onlyPantry = false)
    }

    /** Tippen auf einen Chip filtert auf Rezepte mit dieser Gruppe, erneutes Tippen hebt den Filter auf. */
    fun toggleGroup(groupId: String) = controls.value.let {
        controls.value = it.copy(selectedGroup = if (it.selectedGroup == groupId) null else groupId)
    }
}
