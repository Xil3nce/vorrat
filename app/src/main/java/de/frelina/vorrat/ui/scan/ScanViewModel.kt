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
import de.frelina.vorrat.data.purchase.PurchaseDraft
import de.frelina.vorrat.data.purchase.PurchaseDraftStore
import de.frelina.vorrat.domain.Validation
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import java.time.LocalDate
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

    /** "+ Bestand": Produkt erkannt, jetzt wird das Ablaufdatum gescannt. */
    data class PurchaseExpiry(val outcome: ScanOutcome) : ScanUiState
}

/**
 * Prüfen (im Laden: habe ich das?), + Bestand (Einkauf nacheinander scannen, danach gesammelt übernehmen)
 * oder Entnehmen (zuhause: verbraucht).
 */
enum class ScanMode { CHECK, PURCHASE, CONSUME }

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val scanRepository: ScanRepository,
    private val stockRepository: StockRepository,
    private val shoppingRepository: ShoppingRepository,
    private val messages: UiMessages,
    private val purchaseDrafts: PurchaseDraftStore,
    settingsRepository: SettingsRepository,
    private val catalogRepository: de.frelina.vorrat.data.repo.CatalogRepository,
) : ViewModel() {

    /** Anzahl der Artikel im aktuellen Einkauf ("+ Bestand"). */
    val purchaseCount: StateFlow<Int> = purchaseDrafts.drafts.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Zuletzt im Einkauf erfasster Barcode – kurz danach nicht gleich noch einmal erkennen. */
    private var lastPurchase: Pair<String, Long>? = null

    /** Einheiten-ID → Symbol für die Packungsgröße im Ergebnis. */
    val units: StateFlow<Map<String, String>> = catalogRepository.observeUnits()
        .map { list -> list.associate { it.id to it.symbol } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _state = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    val leadDays: StateFlow<Int> = settingsRepository.reminderSettings.map { it.leadDays }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpiryLogic.DEFAULT_LEAD_DAYS)

    private var job: Job? = null

    private val _mode = MutableStateFlow(ScanMode.CHECK)
    val mode: StateFlow<ScanMode> = _mode.asStateFlow()

    fun setMode(mode: ScanMode) {
        val old = _mode.value
        _mode.value = mode
        // Beim Wechsel von/zu "+ Bestand" neu beginnen (dort gibt es keine Ergebnis-Ansicht).
        if (old != mode && (old == ScanMode.PURCHASE || mode == ScanMode.PURCHASE)) rescan()
    }

    /** Von der Kamera (automatische Erkennung) oder aus der manuellen Eingabe. */
    fun onBarcode(raw: String) {
        val barcode = raw.trim()
        if (barcode.isEmpty()) return
        val current = _state.value
        if (current is ScanUiState.Loading) return
        if (current is ScanUiState.Result && current.outcome.barcode == barcode) return
        if (_mode.value == ScanMode.PURCHASE) {
            if (current !is ScanUiState.Idle) return
            val last = lastPurchase
            if (last != null && last.first == barcode && System.currentTimeMillis() - last.second < SAME_BARCODE_PAUSE_MS) return
            job?.cancel()
            _state.value = ScanUiState.Loading(barcode)
            job = viewModelScope.launch { _state.value = ScanUiState.PurchaseExpiry(scanRepository.resolve(barcode)) }
            return
        }
        resolve(barcode, showLoading = true)
    }

    /** "+ Bestand": Artikel mit Ablaufdatum ([expiry] = null: ohne) in den Einkauf übernehmen, weiter scannen. */
    fun addToPurchase(expiry: LocalDate?) = viewModelScope.launch {
        val outcome = (_state.value as? ScanUiState.PurchaseExpiry)?.outcome ?: return@launch
        val draft = purchaseDrafts.addScanned(buildDraft(outcome, expiry))
        lastPurchase = outcome.barcode to System.currentTimeMillis()
        _state.value = ScanUiState.Idle
        val count = draft.quantity.toIntOrNull() ?: 1
        val name = outcome.displayName
        messages.show(
            when {
                name == null -> UiMessage(R.string.purchase_added, listOf(outcome.barcode))
                count > 1 -> UiMessage(R.string.purchase_added_more, listOf(name, count))
                else -> UiMessage(R.string.purchase_added, listOf(name))
            },
        )
    }

    /** Ablaufdatum-Scan abgebrochen: Artikel nicht übernehmen. */
    fun cancelPurchase() {
        (_state.value as? ScanUiState.PurchaseExpiry)?.let { lastPurchase = it.outcome.barcode to System.currentTimeMillis() }
        rescan()
    }

    private suspend fun buildDraft(outcome: ScanOutcome, expiry: LocalDate?): PurchaseDraft {
        val types = catalogRepository.observeQuantityTypes().first()
        val locations = catalogRepository.observeLocations().first()
        val local = outcome.localProduct
        val remote = outcome.remote.takeIf { local == null }
        // Bekanntes Produkt: Ort und Menge-Art wie beim vorhandenen Bestand vorschlagen.
        val match = outcome.exactMatches.firstOrNull()
        val remoteUnit = remote?.unitSymbol?.let { catalogRepository.findUnitIdBySymbol(it) }
        return PurchaseDraft(
            barcode = outcome.barcode,
            productId = local?.id,
            name = local?.name ?: remote?.name.orEmpty(),
            brand = local?.brand ?: remote?.brand.orEmpty(),
            category = local?.category ?: remote?.category.orEmpty(),
            packageSize = local?.packageSize?.toPlainString()
                ?: remote?.packageSize?.takeIf { remoteUnit != null }?.toPlainString().orEmpty(),
            unitId = local?.unitId ?: remoteUnit,
            imagePath = local?.imagePath ?: remote?.imagePath,
            imageFromCache = local == null && remote?.imagePath != null,
            fromOff = remote != null,
            locationId = match?.locationId ?: locations.singleOrNull()?.id,
            level = match?.level?.toString().orEmpty(),
            shelf = match?.shelf?.toString().orEmpty(),
            expiry = expiry?.let(Validation::formatDate).orEmpty(),
            quantityTypeId = match?.quantityTypeId ?: types.firstOrNull()?.id,
        )
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

    private companion object {
        const val SAME_BARCODE_PAUSE_MS = 2_500L
    }
}
