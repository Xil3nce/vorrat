package de.frelina.vorrat.ui.mealplan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import de.frelina.vorrat.R
import de.frelina.vorrat.data.repo.MealPlanRepository
import de.frelina.vorrat.data.repo.RecipeRepository
import de.frelina.vorrat.data.repo.ShoppingRepository
import de.frelina.vorrat.data.repo.StapleRepository
import de.frelina.vorrat.data.repo.StockRepository
import de.frelina.vorrat.domain.MealPlanShopping
import de.frelina.vorrat.domain.PlanLine
import de.frelina.vorrat.domain.PlannedMeal
import de.frelina.vorrat.domain.toPantryEntry
import de.frelina.vorrat.ui.components.UiMessage
import de.frelina.vorrat.ui.components.UiMessages
import de.frelina.vorrat.ui.navigation.PlanShoppingRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Zeile der Prüfliste mit dem aktuellen Haken (Vorauswahl oder Entscheidung der Person). */
data class PlanLineUi(val line: PlanLine, val checked: Boolean)

data class PlanShoppingUiState(
    val loading: Boolean = true,
    val lines: List<PlanLineUi> = emptyList(),
) {
    val checkedCount: Int get() = lines.count { it.checked }
}

/**
 * Prüfseite "Zutaten auf die Einkaufsliste": Die Zutaten der geplanten Rezepte im gewählten Zeitraum, mit
 * Vorauswahl (siehe [MealPlanShopping]); die Person kann Haken ändern oder Zutaten auf "Immer zuhause" setzen.
 */
@HiltViewModel
class PlanShoppingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    plan: MealPlanRepository,
    recipes: RecipeRepository,
    stockRepository: StockRepository,
    private val staples: StapleRepository,
    private val shopping: ShoppingRepository,
    private val messages: UiMessages,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<PlanShoppingRoute>()
    private val from: LocalDate = LocalDate.parse(route.from)
    private val to: LocalDate = LocalDate.parse(route.to)

    /** Entscheidungen der Person: Zeilenschlüssel → Haken. */
    private val overrides = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    private val computed: StateFlow<List<PlanLine>?> = combine(
        plan.observeBetween(from, to),
        recipes.observeAll(),
        stockRepository.observeItems(),
        staples.observeAll(),
        shopping.observe(),
    ) { entries, allRecipes, items, stapleList, list ->
        val byId = allRecipes.associateBy { it.id }
        val meals = entries.mapNotNull { e -> byId[e.recipeId]?.let { PlannedMeal(it, e.persons) } }
        val listed = list.shortages.map { it.product.name } + list.items.map { it.item.name }
        MealPlanShopping.build(meals, items.map { it.toPantryEntry() }, stapleList.map { it.key }.toSet(), listed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val state: StateFlow<PlanShoppingUiState> = combine(computed, overrides) { lines, over ->
        if (lines == null) {
            PlanShoppingUiState()
        } else {
            PlanShoppingUiState(loading = false, lines = lines.map { PlanLineUi(it, over[it.key] ?: it.checked) })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanShoppingUiState())

    fun toggle(key: String, checked: Boolean) = overrides.update { it + (key to checked) }

    fun setAll(checked: Boolean) {
        val keys = state.value.lines.map { it.line.key }
        overrides.update { current -> current + keys.associateWith { checked } }
    }

    /** "Immer zuhause": Zutat in die Liste aufnehmen und abwählen. */
    fun addStaple(line: PlanLine) = viewModelScope.launch {
        staples.add(line.key, line.name)
        overrides.update { it + (line.key to false) }
    }

    /** Abgehakte Zeilen auf die Einkaufsliste setzen (ohne Dubletten) und zurück zum Wochenplan. */
    fun apply(onDone: () -> Unit) = viewModelScope.launch {
        val chosen = state.value.lines.filter { it.checked }.map { it.line }
        val added = shopping.addPlanned(chosen.map { it.name to it.note() })
        messages.show(
            if (added > 0) {
                UiMessage(R.plurals.planshop_done, pluralCount = added)
            } else {
                UiMessage(R.string.planshop_nothing_new)
            },
        )
        onDone()
    }
}
