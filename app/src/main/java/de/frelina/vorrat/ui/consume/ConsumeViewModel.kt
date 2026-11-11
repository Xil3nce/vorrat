package de.frelina.vorrat.ui.consume

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.frelina.vorrat.R
import de.frelina.vorrat.data.consume.ConsumeDraft
import de.frelina.vorrat.data.consume.ConsumeDraftStore
import de.frelina.vorrat.data.db.StockItem
import de.frelina.vorrat.data.repo.StockRepository
import de.frelina.vorrat.data.settings.SettingsRepository
import de.frelina.vorrat.domain.ExpiryLogic
import de.frelina.vorrat.ui.components.MessageTone
import de.frelina.vorrat.ui.components.UiMessage
import de.frelina.vorrat.ui.components.UiMessages
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Zeile der Entnahme-Scan-Liste. [item] = null: der Eintrag ist inzwischen verschwunden (wird übersprungen);
 * [options] = alle Einträge des Produkts (für die Auswahl im Dialog).
 */
data class ConsumeRow(val draft: ConsumeDraft, val item: StockItem?, val options: List<StockItem>)

data class ConsumeUiState(val loading: Boolean = true, val rows: List<ConsumeRow> = emptyList())

@HiltViewModel
class ConsumeViewModel @Inject constructor(
    private val store: ConsumeDraftStore,
    private val committer: ConsumeCommitter,
    private val messages: UiMessages,
    stockRepository: StockRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val state: StateFlow<ConsumeUiState> = combine(store.drafts, stockRepository.observeItems()) { drafts, items ->
        val byEntry = items.associateBy { it.entryId }
        val byProduct = items.groupBy { it.productId }
        ConsumeUiState(
            loading = false,
            rows = drafts.map { d -> ConsumeRow(d, byEntry[d.entryId], byProduct[d.productId].orEmpty()) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConsumeUiState())

    val leadDays: StateFlow<Int> = settingsRepository.reminderSettings.map { it.leadDays }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpiryLogic.DEFAULT_LEAD_DAYS)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _done = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val done: SharedFlow<Unit> = _done

    /** Menge einer Zeile per −/+ ändern. */
    fun adjust(row: ConsumeRow, delta: Int) = viewModelScope.launch {
        val item = row.item ?: return@launch
        store.adjust(row.draft.entryId, delta, item.quantity)
    }

    /** Dialog "Übernehmen": anderer Eintrag und/oder andere Menge. */
    fun change(row: ConsumeRow, newEntryId: String, amount: Int) = viewModelScope.launch {
        store.change(row.draft.entryId, newEntryId, amount)
    }

    /** Zeile aus der Scan-Liste entfernen (mit Rückgängig). */
    fun remove(entryId: String) = viewModelScope.launch {
        val index = store.all().indexOfFirst { it.entryId == entryId }
        val removed = store.remove(entryId) ?: return@launch
        messages.show(
            UiMessage(
                text = R.string.consume_list_removed,
                actionLabel = R.string.action_undo,
                onAction = { store.restore(removed, index) },
                tone = MessageTone.INFO,
            ),
        )
    }

    fun commit() = viewModelScope.launch {
        if (_busy.value) return@launch
        _busy.value = true
        try {
            val result = committer.commitAll()
            if (result.consumed > 0) {
                messages.show(UiMessage(R.plurals.consume_list_done, pluralCount = result.consumed))
            }
            if (result.skipped > 0) {
                messages.show(UiMessage(R.plurals.consume_list_skipped, pluralCount = result.skipped, tone = MessageTone.INFO))
            }
            _done.tryEmit(Unit)
        } finally {
            _busy.value = false
        }
    }
}
