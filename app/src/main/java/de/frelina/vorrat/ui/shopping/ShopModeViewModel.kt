package de.frelina.vorrat.ui.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.frelina.vorrat.R
import de.frelina.vorrat.data.purchase.PurchaseDraft
import de.frelina.vorrat.data.purchase.PurchaseDraftStore
import de.frelina.vorrat.data.purchase.PurchaseLogic
import de.frelina.vorrat.data.repo.CatalogRepository
import de.frelina.vorrat.data.repo.DepartmentRepository
import de.frelina.vorrat.data.repo.ShoppingRepository
import de.frelina.vorrat.data.repo.StockRepository
import de.frelina.vorrat.data.settings.SettingsRepository
import de.frelina.vorrat.domain.BoardRow
import de.frelina.vorrat.domain.DepartmentDefaults
import de.frelina.vorrat.domain.ShopMode
import de.frelina.vorrat.domain.ShoppingBoard
import de.frelina.vorrat.ui.components.MessageTone
import de.frelina.vorrat.ui.components.UiMessage
import de.frelina.vorrat.ui.components.UiMessages
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShopModeUiState(
    val loading: Boolean = true,
    /** Offene Einträge mit Abteilungs-Überschriften, in der Reihenfolge der Abteilungen. */
    val open: List<BoardRow<ShoppingPayload>> = emptyList(),
    /** Abgehakte Einträge ("Im Wagen"). */
    val checked: List<BoardRow<ShoppingPayload>> = emptyList(),
    /** Abteilungs-ID → Name. */
    val departmentNames: Map<String, String> = emptyMap(),
    /** Einheiten-ID → Symbol (Packungsgröße der Produkte). */
    val units: Map<String, String> = emptyMap(),
    /** Artikel im Einkauf insgesamt (abgehakt und gescannt). */
    val cartCount: Int = 0,
) {
    val total: Int get() = open.count { !it.isHeader } + checked.size
}

/** Einkaufsmodus: Einkaufsliste im Laden abhaken; Abgehaktes landet im Einkauf ("+ Bestand"). */
@HiltViewModel
class ShopModeViewModel @Inject constructor(
    repository: ShoppingRepository,
    departmentRepository: DepartmentRepository,
    private val store: PurchaseDraftStore,
    private val stockRepository: StockRepository,
    private val catalogRepository: CatalogRepository,
    private val settings: SettingsRepository,
    private val messages: UiMessages,
) : ViewModel() {

    val state: StateFlow<ShopModeUiState> = combine(
        repository.observe(),
        departmentRepository.observeDepartments(),
        departmentRepository.observeAssignments(),
        catalogRepository.observeUnits(),
        store.drafts,
    ) { list, stored, assignments, units, drafts ->
        val departments = stored.ifEmpty { DepartmentDefaults.entities() }
        val rows = ShoppingBoard.build(departments, assignments, shoppingInputs(list))
        val split = ShopMode.split(rows) { row ->
            PurchaseLogic.forShoppingEntry(drafts, row.key, row.payload?.productId).isNotEmpty()
        }
        ShopModeUiState(
            loading = false,
            open = split.open,
            checked = split.checked,
            departmentNames = departments.associate { it.id to it.name },
            units = units.associate { it.id to it.symbol },
            cartCount = drafts.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShopModeUiState())

    /** Abhaken: Artikel in den Einkauf legen. */
    fun check(row: BoardRow<ShoppingPayload>) = viewModelScope.launch {
        val payload = row.payload ?: return@launch
        store.addChecked(buildDraft(row.key, payload))
    }

    /** Haken entfernen: Artikel wieder aus dem Einkauf nehmen (mit Rückgängig). */
    fun uncheck(row: BoardRow<ShoppingPayload>) = viewModelScope.launch {
        val payload = row.payload ?: return@launch
        val all = store.all()
        val mine = PurchaseLogic.forShoppingEntry(all, row.key, payload.productId)
        if (mine.isEmpty()) return@launch
        val positions = mine.map { draft -> draft to all.indexOf(draft) }
        mine.forEach { store.remove(it.id) }
        messages.show(
            UiMessage(
                text = R.string.shop_mode_unchecked,
                args = listOf(payload.name),
                actionLabel = R.string.action_undo,
                onAction = { positions.forEach { (draft, index) -> store.restore(draft, index) } },
                tone = MessageTone.INFO,
            ),
        )
    }

    private suspend fun buildDraft(key: String, payload: ShoppingPayload): PurchaseDraft {
        val product = when (payload) {
            is ShoppingPayload.Short -> payload.shortage.product
            is ShoppingPayload.Item -> payload.entry.product
        }
        val item = (payload as? ShoppingPayload.Item)?.entry?.item
        // Bekanntes Produkt: Ort und Menge-Art wie beim vorhandenen Bestand vorschlagen.
        val match = product?.let { stockRepository.itemsForProduct(it.id).firstOrNull() }
        val placement = ShopMode.placement(
            scanLocationId = settings.scanLocationId.first(),
            scanLevel = settings.scanLevel.first(),
            scanShelf = settings.scanShelf.first(),
            locations = catalogRepository.observeLocations().first(),
            match = match,
        )
        return ShopMode.draft(
            key = key,
            product = product,
            name = payload.name,
            quantity = when (payload) {
                is ShoppingPayload.Short -> payload.shortage.missing
                is ShoppingPayload.Item -> payload.entry.item.quantity
            },
            packageSize = item?.packageSize,
            unitId = item?.unitSymbol?.let { catalogRepository.findUnitIdBySymbol(it) },
            placement = placement,
            quantityTypeId = match?.quantityTypeId ?: catalogRepository.observeQuantityTypes().first().firstOrNull()?.id,
        )
    }
}
