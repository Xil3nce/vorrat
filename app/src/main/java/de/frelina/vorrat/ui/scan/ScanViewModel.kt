package de.frelina.vorrat.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.frelina.vorrat.R
import de.frelina.vorrat.data.repo.QuantityChange
import de.frelina.vorrat.data.repo.ScanOutcome
import de.frelina.vorrat.data.repo.ScanRepository
import de.frelina.vorrat.data.repo.ShoppingRepository
import de.frelina.vorrat.data.repo.StockRepository
import de.frelina.vorrat.data.settings.SettingsRepository
import de.frelina.vorrat.domain.ConsumeLogic
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
    data class Result(val outcome: ScanOutcome, val onShoppingList: Boolean = false) : ScanUiState
}

/** Prüfen (im Laden: habe ich das?) oder Entnehmen (zuhause: verbraucht). */
enum class ScanMode { CHECK, CONSUME }

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val scanRepository: ScanRepository,
    private val stockRepository: StockRepository,
    private val shoppingRepository: ShoppingRepository,
    private val messages: UiMessages,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    val leadDays: StateFlow<Int> = settingsRepository.reminderSettings.map { it.leadDays }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpiryLogic.DEFAULT_LEAD_DAYS)

    private var job: Job? = null

    private val _mode = MutableStateFlow(ScanMode.CHECK)
    val mode: StateFlow<ScanMode> = _mode.asStateFlow()

    fun setMode(mode: ScanMode) {
        _mode.value = mode
    }

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
            val outcome = scanRepository.resolve(barcode)
            val onList = outcome.localProduct?.let { shoppingRepository.isOnList(it.id) } ?: false
            _state.value = ScanUiState.Result(outcome, onList)
        }
    }

    fun increase(entryId: String, amount: Int) = viewModelScope.launch {
        if (amount < 1) return@launch
        val productId = stockRepository.getEntry(entryId)?.productId
        if (stockRepository.changeQuantity(entryId, amount) is QuantityChange.Updated) {
            val removed = productId?.let { shoppingRepository.removeForProduct(it) }.orEmpty()
            messages.show(UiMessage(if (removed.isEmpty()) R.string.scan_increased else R.string.shopping_removed_after_purchase))
        }
        refresh()
    }

    /**
     * Entnehmen: Anzahl um [amount] verringern (höchstens bis 0 → Eintrag wird entfernt), mit Rückgängig.
     * Im Entnehmen-Modus geht es danach direkt mit dem nächsten Scan weiter.
     */
    fun consume(entryId: String, amount: Int) = viewModelScope.launch {
        val entry = stockRepository.getEntry(entryId) ?: return@launch refresh()
        val n = ConsumeLogic.clamp(amount, entry.quantity)
        val symbol = stockRepository.itemsForProduct(entry.productId)
            .firstOrNull { it.entryId == entryId }?.quantityTypeSymbol.orEmpty()
        val result = stockRepository.changeQuantity(entryId, -n)
        // Hinweis, wenn der Vorrat dadurch unter den Mindestbestand fällt.
        val nowShort = !shoppingRepository.isOnListManually(entry.productId) &&
            shoppingRepository.isOnList(entry.productId)
        messages.show(
            UiMessage(
                text = if (nowShort) R.string.consume_done_shopping else R.string.consume_done,
                args = listOf(n, symbol),
                actionLabel = R.string.action_undo,
                onAction = {
                    when (result) {
                        is QuantityChange.Removed -> stockRepository.restoreEntry(result.removed)
                        is QuantityChange.Updated -> stockRepository.changeQuantity(entryId, n)
                        QuantityChange.NotFound -> Unit
                    }
                    refresh()
                },
            ),
        )
        if (_mode.value == ScanMode.CONSUME) rescan() else refresh()
    }

    /** Gescanntes Produkt auf die Einkaufsliste setzen. */
    fun addToShoppingList() = viewModelScope.launch {
        val outcome = (_state.value as? ScanUiState.Result)?.outcome ?: return@launch
        val added = when {
            outcome.localProduct != null -> shoppingRepository.addProduct(outcome.localProduct)
            outcome.remote != null -> shoppingRepository.addScanned(outcome.remote)
            else -> outcome.displayName?.let { shoppingRepository.addText(it) }
        }
        if (added != null) messages.show(UiMessage(R.string.shopping_added))
        refresh()
    }
}
