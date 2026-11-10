package de.frelina.vorrat.ui.mealplan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.frelina.vorrat.R
import de.frelina.vorrat.data.db.MealPlanEntity
import de.frelina.vorrat.data.db.StapleEntity
import de.frelina.vorrat.data.repo.MealPlanRepository
import de.frelina.vorrat.data.repo.RecipeRepository
import de.frelina.vorrat.data.repo.StapleRepository
import de.frelina.vorrat.data.repo.StockRepository
import de.frelina.vorrat.domain.PlanWeek
import de.frelina.vorrat.domain.Recipe
import de.frelina.vorrat.domain.RecipeMatcher
import de.frelina.vorrat.domain.toPantryEntry
import de.frelina.vorrat.ui.components.UiMessage
import de.frelina.vorrat.ui.components.UiMessages
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Eintrag im Wochenplan; [recipe] ist null, wenn das Rezept nicht mehr vorhanden ist. */
data class PlanEntryUi(val entry: MealPlanEntity, val recipe: Recipe?)

data class PlanDayUi(val date: LocalDate, val entries: List<PlanEntryUi>)

data class MealPlanUiState(
    val loading: Boolean = true,
    val weekStart: LocalDate = PlanWeek.monday(LocalDate.now()),
    val today: LocalDate = LocalDate.now(),
    val days: List<PlanDayUi> = emptyList(),
    /** Alle Rezepte (eigene und eingebaute) für die Auswahl. */
    val recipes: List<Recipe> = emptyList(),
    /** Vorschläge passend zum Vorrat (wie "Was koche ich heute?"). */
    val suggestions: List<Recipe> = emptyList(),
    val staples: List<StapleEntity> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MealPlanViewModel @Inject constructor(
    private val plan: MealPlanRepository,
    private val recipeRepository: RecipeRepository,
    stockRepository: StockRepository,
    private val staples: StapleRepository,
    private val messages: UiMessages,
) : ViewModel() {

    private val weekStart = MutableStateFlow(PlanWeek.monday(LocalDate.now()))

    val state: StateFlow<MealPlanUiState> = combine(
        weekStart,
        weekStart.flatMapLatest { start -> plan.observeBetween(start, start.plusDays(6)) },
        recipeRepository.observeAll(),
        stockRepository.observeItems(),
        staples.observeAll(),
    ) { start, entries, recipes, items, stapleList ->
        val today = LocalDate.now()
        val byId = recipes.associateBy { it.id }
        val byDay = entries.groupBy { it.date }
        val pantry = items.map { it.toPantryEntry() }
        MealPlanUiState(
            loading = false,
            weekStart = start,
            today = today,
            days = PlanWeek.days(start).map { day ->
                PlanDayUi(day, byDay[day].orEmpty().map { PlanEntryUi(it, byId[it.recipeId]) })
            },
            recipes = recipes,
            suggestions = RecipeMatcher.rank(recipes, pantry, today).take(MAX_SUGGESTIONS).map { it.recipe },
            staples = stapleList,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MealPlanUiState())

    fun previousWeek() { weekStart.value = weekStart.value.minusWeeks(1) }

    fun nextWeek() { weekStart.value = weekStart.value.plusWeeks(1) }

    fun thisWeek() { weekStart.value = PlanWeek.monday(LocalDate.now()) }

    fun add(date: LocalDate, recipeId: String, persons: Int) = viewModelScope.launch { plan.add(date, recipeId, persons) }

    fun move(id: String, date: LocalDate) = viewModelScope.launch { plan.move(id, date) }

    /** Entfernen mit "Rückgängig". */
    fun remove(entry: PlanEntryUi) = viewModelScope.launch {
        val removed = plan.remove(entry.entry.id) ?: return@launch
        messages.show(
            UiMessage(
                text = R.string.mealplan_removed,
                args = listOf(entry.recipe?.title ?: ""),
                actionLabel = R.string.action_undo,
                onAction = { plan.restore(removed) },
            ),
        )
    }

    fun removeStaple(key: String) = viewModelScope.launch { staples.remove(key) }

    private companion object {
        const val MAX_SUGGESTIONS = 6
    }
}
