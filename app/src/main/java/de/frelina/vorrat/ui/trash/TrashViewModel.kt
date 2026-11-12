package de.frelina.vorrat.ui.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.frelina.vorrat.R
import de.frelina.vorrat.data.repo.TrashRepository
import de.frelina.vorrat.data.repo.TrashRestore
import de.frelina.vorrat.data.trash.TrashItem
import de.frelina.vorrat.ui.components.MessageTone
import de.frelina.vorrat.ui.components.UiMessage
import de.frelina.vorrat.ui.components.UiMessages
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrashUiState(
    val loading: Boolean = true,
    val items: List<TrashItem> = emptyList(),
    val now: Long = System.currentTimeMillis(),
)

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val repository: TrashRepository,
    private val messages: UiMessages,
) : ViewModel() {

    val state: StateFlow<TrashUiState> = repository.items
        .map { TrashUiState(loading = false, items = it.sortedByDescending(TrashItem::deletedAt)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrashUiState())

    fun restore(item: TrashItem) = viewModelScope.launch {
        when (repository.restore(item.kind, item.id)) {
            TrashRestore.RESTORED -> messages.show(UiMessage(R.string.trash_restored, listOf(item.title)))
            TrashRestore.PRODUCT_MISSING ->
                messages.show(UiMessage(R.string.trash_restore_product_missing, tone = MessageTone.ERROR))
            TrashRestore.GONE -> Unit
        }
    }

    fun deleteForever(item: TrashItem) = viewModelScope.launch {
        repository.deleteForever(item.kind, item.id)
    }

    fun clear() = viewModelScope.launch {
        val count = repository.clear()
        if (count > 0) messages.show(UiMessage(R.plurals.trash_cleared, pluralCount = count))
    }
}
