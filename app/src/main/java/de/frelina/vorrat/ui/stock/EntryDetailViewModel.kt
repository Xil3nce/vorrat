package de.frelina.vorrat.ui.stock

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import de.frelina.vorrat.R
import de.frelina.vorrat.data.db.ProductEntity
import de.frelina.vorrat.data.db.StockItem
import de.frelina.vorrat.data.db.UnitEntity
import de.frelina.vorrat.data.repo.CatalogRepository
import de.frelina.vorrat.data.repo.ProductRepository
import de.frelina.vorrat.data.repo.QuantityChange
import de.frelina.vorrat.data.repo.ScanRepository
import de.frelina.vorrat.data.repo.StockRepository
import de.frelina.vorrat.data.settings.SettingsRepository
import de.frelina.vorrat.domain.ExpiryLogic
import de.frelina.vorrat.domain.Nutrition
import de.frelina.vorrat.ui.components.UiMessage
import de.frelina.vorrat.ui.components.UiMessages
import de.frelina.vorrat.ui.navigation.EntryDetailRoute
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class EntryDetailState(
    val loading: Boolean = true,
    val item: StockItem? = null,
    val product: ProductEntity? = null,
    val units: List<UnitEntity> = emptyList(),
    val leadDays: Int = ExpiryLogic.DEFAULT_LEAD_DAYS,
    val today: LocalDate = LocalDate.now(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EntryDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val stockRepository: StockRepository,
    productRepository: ProductRepository,
    catalogRepository: CatalogRepository,
    settingsRepository: SettingsRepository,
    scanRepository: ScanRepository,
    private val messages: UiMessages,
) : ViewModel() {

    private val entryId = savedStateHandle.toRoute<EntryDetailRoute>().entryId

    private val itemFlow = stockRepository.observeItem(entryId)

    private val productFlow =
        itemFlow.flatMapLatest { item -> item?.let { productRepository.observeById(it.productId) } ?: flowOf(null) }

    /** Nährwerte aus Open Food Facts (Cache bzw. einmalige Nachfrage); null = keine vorhanden. */
    val nutrition: StateFlow<Nutrition?> = productFlow
        .map { it?.barcode?.takeIf { code -> code.isNotBlank() } }
        .distinctUntilChanged()
        .mapLatest { barcode -> barcode?.let { scanRepository.nutritionFor(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val state: StateFlow<EntryDetailState> = combine(
        itemFlow,
        productFlow,
        catalogRepository.observeUnits(),
        settingsRepository.reminderSettings,
    ) { item, product, units, settings ->
        EntryDetailState(false, item, product, units, settings.leadDays, LocalDate.now())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EntryDetailState())

    /** Signalisiert dem Screen, dass der Eintrag entfernt wurde → zurück navigieren. */
    private val _closed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val closed: SharedFlow<Unit> = _closed

    fun increase() = viewModelScope.launch { stockRepository.changeQuantity(entryId, +1) }

    /** Verringern; bei Anzahl 1 muss der Screen vorher bestätigen lassen. */
    fun decrease() = viewModelScope.launch {
        val result = stockRepository.changeQuantity(entryId, -1)
        if (result is QuantityChange.Removed) {
            offerUndo(R.string.entry_removed, result.removed)
        }
    }

    fun delete() = viewModelScope.launch {
        val removed = stockRepository.deleteEntry(entryId) ?: return@launch
        offerUndo(R.string.entry_deleted, removed)
    }

    private fun offerUndo(text: Int, removed: de.frelina.vorrat.data.db.StockEntryEntity) {
        messages.show(
            UiMessage(
                text = text,
                actionLabel = R.string.action_undo,
                onAction = { stockRepository.restoreEntry(removed) },
            ),
        )
        _closed.tryEmit(Unit)
    }
}
