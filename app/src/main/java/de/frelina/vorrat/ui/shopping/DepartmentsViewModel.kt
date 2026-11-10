package de.frelina.vorrat.ui.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.frelina.vorrat.R
import de.frelina.vorrat.data.db.DepartmentEntity
import de.frelina.vorrat.data.repo.DepartmentNameResult
import de.frelina.vorrat.data.repo.DepartmentRepository
import de.frelina.vorrat.ui.components.UiMessage
import de.frelina.vorrat.ui.components.UiMessages
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DepartmentsUiState(val loading: Boolean = true, val departments: List<DepartmentEntity> = emptyList())

@HiltViewModel
class DepartmentsViewModel @Inject constructor(
    private val repository: DepartmentRepository,
    private val messages: UiMessages,
) : ViewModel() {

    val state: StateFlow<DepartmentsUiState> = repository.observeDepartments()
        .map { DepartmentsUiState(loading = false, departments = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DepartmentsUiState())

    /** Neue Reihenfolge speichern (gilt im ganzen Haushalt). */
    fun saveOrder(ids: List<String>) = viewModelScope.launch { repository.saveOrder(ids) }

    /** Barrierearm: eine Abteilung um [delta] Plätze verschieben. */
    fun move(id: String, delta: Int) = viewModelScope.launch {
        val ids = state.value.departments.map { it.id }.toMutableList()
        val from = ids.indexOf(id)
        val to = (from + delta).coerceIn(0, ids.lastIndex)
        if (from < 0 || from == to) return@launch
        ids.add(to, ids.removeAt(from))
        repository.saveOrder(ids)
    }

    fun add(name: String, onResult: (DepartmentNameResult) -> Unit) = viewModelScope.launch {
        onResult(repository.add(name))
    }

    fun rename(id: String, name: String, onResult: (DepartmentNameResult) -> Unit) = viewModelScope.launch {
        onResult(repository.rename(id, name))
    }

    fun delete(id: String) = viewModelScope.launch { repository.delete(id) }

    fun resetToDefaults() = viewModelScope.launch {
        repository.resetToDefaults()
        messages.show(UiMessage(R.string.departments_reset_done))
    }
}
