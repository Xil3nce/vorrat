package de.frelina.vorrat.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.frelina.vorrat.R
import de.frelina.vorrat.data.db.HistoryAction
import de.frelina.vorrat.data.db.HistoryEventEntity
import de.frelina.vorrat.data.household.HouseholdStore
import de.frelina.vorrat.data.repo.HistoryRepository
import de.frelina.vorrat.ui.components.UiMessage
import de.frelina.vorrat.ui.components.UiMessages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class HistoryFilter { ALL, ADDED, REMOVED, MESSAGES }

data class HistoryUiState(
    val loading: Boolean = true,
    val events: List<HistoryEventEntity> = emptyList(),
    val filter: HistoryFilter = HistoryFilter.ALL,
    /** UID dieses Geräts (eigene Einträge → "Du"). */
    val myUid: String? = null,
    /** Zuletzt aufgebrauchte Produkte – Vorschläge für eine Nachricht ("… ist alle"). */
    val outOfStockNames: List<String> = emptyList(),
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: HistoryRepository,
    household: HouseholdStore,
    private val messages: UiMessages,
) : ViewModel() {

    private val filter = MutableStateFlow(HistoryFilter.ALL)

    val state: StateFlow<HistoryUiState> = combine(repository.observeRecent(), filter, household.household) { events, f, h ->
        HistoryUiState(
            loading = false,
            events = events.filter { e ->
                when (f) {
                    HistoryFilter.ALL -> true
                    HistoryFilter.ADDED -> e.action == HistoryAction.ADDED
                    HistoryFilter.REMOVED -> e.action == HistoryAction.REMOVED || e.action == HistoryAction.DELETED
                    HistoryFilter.MESSAGES -> e.action == HistoryAction.MESSAGE || e.notify
                }
            },
            filter = f,
            myUid = h?.uid,
            outOfStockNames = events.filter { it.outOfStock && it.productName.isNotBlank() }
                .map { it.productName }.distinct().take(MAX_SUGGESTIONS),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun setFilter(f: HistoryFilter) {
        filter.value = f
    }

    fun send(text: String) = viewModelScope.launch {
        if (repository.sendMessage(text)) messages.show(UiMessage(R.string.history_message_sent))
    }

    fun notifyOther(event: HistoryEventEntity) = viewModelScope.launch {
        repository.notifyLater(event.id)
        messages.show(UiMessage(R.string.history_message_sent))
    }

    private companion object {
        const val MAX_SUGGESTIONS = 4
    }
}
