package de.frelina.vorrat.ui.purchase

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.frelina.vorrat.R
import de.frelina.vorrat.data.db.StockItem
import de.frelina.vorrat.data.purchase.PurchaseDraft
import de.frelina.vorrat.data.purchase.PurchaseDraftStore
import de.frelina.vorrat.data.repo.CatalogRepository
import de.frelina.vorrat.data.settings.SettingsRepository
import de.frelina.vorrat.domain.ExpiryLogic
import de.frelina.vorrat.domain.Validated
import de.frelina.vorrat.domain.Validation
import de.frelina.vorrat.ui.components.MessageTone
import de.frelina.vorrat.ui.components.UiMessage
import de.frelina.vorrat.ui.components.UiMessages
import de.frelina.vorrat.ui.product.isComplete
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

/** Zeile der Einkaufsliste: Darstellung wie im Bestand plus Hinweis auf fehlende Angaben. */
data class PurchaseRow(val draft: PurchaseDraft, val item: StockItem, val complete: Boolean)

data class PurchaseUiState(val loading: Boolean = true, val rows: List<PurchaseRow> = emptyList()) {
    val incomplete: Int get() = rows.count { !it.complete }
}

@HiltViewModel
class PurchaseViewModel @Inject constructor(
    private val store: PurchaseDraftStore,
    private val committer: PurchaseCommitter,
    private val messages: UiMessages,
    catalogRepository: CatalogRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val state: StateFlow<PurchaseUiState> = combine(
        store.drafts,
        catalogRepository.observeLocations(),
        catalogRepository.observeQuantityTypes(),
    ) { drafts, locations, types ->
        val locationNames = locations.associate { it.id to it.name }
        val symbols = types.associate { it.id to it.symbol }
        PurchaseUiState(
            loading = false,
            rows = drafts.map { d -> PurchaseRow(d, toStockItem(d, locationNames, symbols), d.isComplete()) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PurchaseUiState())

    val leadDays: StateFlow<Int> = settingsRepository.reminderSettings.map { it.leadDays }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpiryLogic.DEFAULT_LEAD_DAYS)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _done = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val done: SharedFlow<Unit> = _done

    /** Artikel aus dem Einkauf entfernen (mit Rückgängig). */
    fun remove(id: String) = viewModelScope.launch {
        val index = store.all().indexOfFirst { it.id == id }
        val removed = store.remove(id) ?: return@launch
        messages.show(
            UiMessage(
                text = R.string.purchase_removed,
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
            if (result.committed > 0) {
                messages.show(UiMessage(R.plurals.purchase_committed, pluralCount = result.committed))
            }
            if (result.incomplete > 0) {
                messages.show(UiMessage(R.plurals.purchase_incomplete, pluralCount = result.incomplete, tone = MessageTone.ERROR))
            } else {
                _done.tryEmit(Unit)
            }
        } finally {
            _busy.value = false
        }
    }

    companion object {
        /** Artikel wie einen Bestandseintrag darstellen (für [de.frelina.vorrat.ui.components.StockItemCard]). */
        fun toStockItem(d: PurchaseDraft, locationNames: Map<String, String>, symbols: Map<String, String>) = StockItem(
            entryId = d.id,
            productId = d.productId.orEmpty(),
            locationId = d.locationId,
            level = d.level.trim().toIntOrNull(),
            shelf = d.shelf.trim().toIntOrNull(),
            expiryDate = date(d.expiry),
            quantity = d.quantity.trim().toIntOrNull() ?: 0,
            quantityTypeId = d.quantityTypeId.orEmpty(),
            note = d.note.ifBlank { null },
            productName = d.name,
            brand = d.brand.ifBlank { null },
            barcode = d.barcode,
            imagePath = d.imagePath,
            category = d.category.ifBlank { null },
            locationName = d.locationId?.let(locationNames::get),
            quantityTypeSymbol = d.quantityTypeId?.let(symbols::get).orEmpty(),
            frozenAt = if (d.frozen) date(d.frozenAt) ?: java.time.LocalDate.now() else null,
            nfcTagId = d.nfcTagId,
        )

        private fun date(text: String) = (Validation.optionalDate(text) as? Validated.Ok)?.value
    }
}
