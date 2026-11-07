package de.frelina.vorrat.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.frelina.vorrat.R
import de.frelina.vorrat.data.repo.QuantityChange
import de.frelina.vorrat.data.repo.ScanOutcome
import de.frelina.vorrat.data.repo.ScanRepository
import de.frelina.vorrat.data.repo.StockRepository
import de.frelina.vorrat.data.settings.SettingsRepository
import de.frelina.vorrat.domain.ExpiryLogic
import de.frelina.vorrat.ui.components.UiMessage
import de.frelina.vorrat.ui.components.UiMessages
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ScanUiState {
    data object Idle : ScanUiState
    data class Loading(val barcode: String) : ScanUiState
    data class Result(val outcome: ScanOutcome) : ScanUiState
}

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val scanRepository: ScanRepository,
    private val stockRepository: StockRepository,
    private val messages: UiMessages,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    val leadDays: StateFlow<Int> = settingsRepository.reminderSettings.map { it.leadDays }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpiryLogic.DEFAULT_LEAD_DAYS)

    private var job: Job? = null

    /** Von der Kamera (automatische Erkennung) oder aus der manuellen Eingabe. */
    fun onBarcode(raw: String) {
        val barcode = raw.trim()
        if (barcode.isEmpty()) return
        val current = _state.value
        if (current is ScanUiState.Loading) return
        if (current is ScanUiState.Result && current.outcome.barcode == barcode) return
        resolve(barcode, showLoading = true)
    }

    fun rescan() {
        job?.cancel()
        _state.value = ScanUiState.Idle
    }

    fun retry() {
        (state.value as? ScanUiState.Result)?.let { resolve(it.outcome.barcode, showLoading = true) }
    }

    /** Nach Rückkehr vom Erfassen: Ergebnis aktualisieren (ohne Ladeanzeige). */
    fun refresh() {
        (state.value as? ScanUiState.Result)?.let { resolve(it.outcome.barcode, showLoading = false) }
    }

    private fun resolve(barcode: String, showLoading: Boolean) {
        job?.cancel()
        if (showLoading) _state.value = ScanUiState.Loading(barcode)
        job = viewModelScope.launch {
            _state.value = ScanUiState.Result(scanRepository.resolve(barcode))
        }
    }

    fun increase(entryId: Long, amount: Int) = viewModelScope.launch {
        if (amount < 1) return@launch
        if (stockRepository.changeQuantity(entryId, amount) is QuantityChange.Updated) {
            messages.show(UiMessage(R.string.scan_increased))
        }
        refresh()
    }
}
