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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
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

    /** Suchtext direkt fürs Eingabefeld (synchron, damit der Cursor beim Tippen nicht springt). */
    private val queryText = MutableStateFlow("")
    val query: StateFlow<String> = queryText.asStateFlow()

    /** Teurer Abgleich Rezepte ↔ Vorrat: nur neu, wenn sich Daten oder "nur mit Vorrat" ändern – nicht beim Tippen. */
    private val ranked = combine(
        recipeRepository.observeAll(),
        stockRepository.observeItems(),
        controls.map { it.onlyPantry }.distinctUntilChanged(),
    ) { recipes, items, onlyPantry ->
        val today = LocalDate.now()
        val pantry = items.map { it.toPantryEntry() }
        val ranked = if (onlyPantry) RecipeMatcher.rank(recipes, pantry, today) else RecipeMatcher.rankAll(recipes, pantry, today)
        Ranked(ranked.map { it to searchText(it) }, pantry, today)
    }

    val state: StateFlow<RecipesUiState> = combine(
        ranked,
        settingsRepository.reminderSettings,
        controls,
        queryText,
    ) { r, settings, c, query ->
        val q = IngredientMatcher.normalize(query)
        val matches = r.matches
            .filter { (m, _) -> !c.onlyOwn || !m.recipe.builtin }
            .filter { (m, _) -> !c.onlyVegetarian || m.recipe.vegetarian }
            .filter { (m, _) -> c.selectedGroup == null || m.have.any { it.groupId == c.selectedGroup } }
            .filter { (_, text) -> q.isEmpty() || text.any { it.contains(q) } }
            .map { it.first }
        RecipesUiState(
            loading = false,
            matches = matches,
            expiring = RecipeMatcher.expiringGroups(r.pantry, r.today, settings.leadDays),
            query = query,
            onlyOwn = c.onlyOwn,
            onlyVegetarian = c.onlyVegetarian,
            onlyPantry = c.onlyPantry,
            selectedGroup = c.selectedGroup,
            today = r.today,
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipesUiState())

    fun setQuery(q: String) { queryText.value = q }
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

private class Ranked(
    /** Treffer mit vorab normalisierten Suchtexten (Titel + Zutaten). */
    val matches: List<Pair<RecipeMatch, List<String>>>,
    val pantry: List<de.frelina.vorrat.domain.PantryEntry>,
    val today: LocalDate,
)

private fun searchText(m: RecipeMatch): List<String> =
    listOf(IngredientMatcher.normalize(m.recipe.title)) + m.recipe.ingredients.map { IngredientMatcher.normalize(it.name) }
