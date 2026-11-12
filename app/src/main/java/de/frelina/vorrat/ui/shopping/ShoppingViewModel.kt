package de.frelina.vorrat.ui.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.frelina.vorrat.R
import de.frelina.vorrat.data.db.DepartmentEntity
import de.frelina.vorrat.data.db.ProductEntity
import de.frelina.vorrat.data.off.LookedUpProduct
import de.frelina.vorrat.data.off.LookupErrorKind
import de.frelina.vorrat.data.off.OpenFoodFactsSource
import de.frelina.vorrat.data.off.SearchResult
import de.frelina.vorrat.data.repo.ProductSearchRepository
import de.frelina.vorrat.data.repo.DepartmentRepository
import de.frelina.vorrat.data.repo.ShoppingEntry
import de.frelina.vorrat.data.repo.ShoppingList
import de.frelina.vorrat.data.repo.ShoppingRepository
import de.frelina.vorrat.data.repo.CatalogRepository
import de.frelina.vorrat.data.settings.SettingsRepository
import de.frelina.vorrat.domain.BoardInput
import de.frelina.vorrat.domain.BoardRow
import de.frelina.vorrat.domain.DepartmentDefaults
import de.frelina.vorrat.domain.DepartmentResolver
import de.frelina.vorrat.domain.ProductSuggest
import de.frelina.vorrat.domain.Shortage
import de.frelina.vorrat.domain.ShoppingBoard
import de.frelina.vorrat.ui.components.UiMessage
import de.frelina.vorrat.ui.components.UiMessages
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Eintrag der gruppierten Liste: berechnet (unter Mindestbestand) oder manuell hinzugefügt. */
sealed interface ShoppingPayload {
    val name: String
    val productId: String?

    data class Short(val shortage: Shortage) : ShoppingPayload {
        override val name: String get() = shortage.product.name
        override val productId: String get() = shortage.product.id
    }

    data class Item(val entry: ShoppingEntry) : ShoppingPayload {
        override val name: String get() = entry.item.name
        override val productId: String? get() = entry.item.productId ?: entry.product?.id
    }
}

data class ShoppingUiState(
    val loading: Boolean = true,
    val list: ShoppingList = ShoppingList(emptyList(), emptyList()),
    /** Nach Abteilungen gruppieren (Einstellung dieses Geräts). */
    val grouped: Boolean = true,
    /** Abteilungen in der gespeicherten Reihenfolge (Standard-Abteilungen, solange noch keine vorliegen). */
    val departments: List<DepartmentEntity> = emptyList(),
    /** Einheiten-ID → Symbol (Packungsgröße der Produkte). */
    val units: Map<String, String> = emptyMap(),
    /** Flache Liste aus Überschriften und Einträgen. */
    val rows: List<BoardRow<ShoppingPayload>> = emptyList(),
)

/** Im Hinzufügen-Dialog gewählter Treffer (eigenes Produkt oder Open Food Facts). */
sealed interface AddPick {
    data class Local(val product: ProductEntity) : AddPick
    data class Remote(val product: LookedUpProduct) : AddPick
}

/** Suche bei Open Food Facts – nur auf Knopfdruck (OFF erlaubt höchstens 10 Suchen pro Minute). */
sealed interface OnlineSearch {
    data object Idle : OnlineSearch
    data object Loading : OnlineSearch
    data class Done(val hits: List<LookedUpProduct>) : OnlineSearch
    data class Failed(val kind: LookupErrorKind) : OnlineSearch
}

data class AddSearchState(
    /** Vorschläge aus den eigenen Produkten (offline, beim Tippen). */
    val local: List<ProductEntity> = emptyList(),
    val online: OnlineSearch = OnlineSearch.Idle,
    /** Eingabe lang genug für die Online-Suche. */
    val canSearchOnline: Boolean = false,
)

@HiltViewModel
class ShoppingViewModel @Inject constructor(
    private val repository: ShoppingRepository,
    private val departmentRepository: DepartmentRepository,
    private val settings: SettingsRepository,
    private val messages: UiMessages,
    private val search: ProductSearchRepository,
    catalogRepository: CatalogRepository,
) : ViewModel() {

    private val addQuery = MutableStateFlow("")
    private val online = MutableStateFlow<OnlineSearch>(OnlineSearch.Idle)
    private var onlineJob: Job? = null

    val addSearch: StateFlow<AddSearchState> = combine(addQuery, search.observeProducts(), online) { q, products, o ->
        AddSearchState(
            local = ProductSuggest.suggest(q, products),
            online = o,
            canSearchOnline = OpenFoodFactsSource.searchQuery(q) != null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AddSearchState())

    /** Eingabe im Namensfeld; ein geänderter Begriff verwirft alte Online-Treffer. */
    fun setAddQuery(query: String) {
        if (query.trim() != addQuery.value.trim()) {
            onlineJob?.cancel()
            online.value = OnlineSearch.Idle
        }
        addQuery.value = query
    }

    fun searchOnline() {
        val query = addQuery.value
        if (OpenFoodFactsSource.searchQuery(query) == null) return
        onlineJob?.cancel()
        online.value = OnlineSearch.Loading
        onlineJob = viewModelScope.launch {
            online.value = when (val result = search.searchOnline(query)) {
                is SearchResult.Hits -> OnlineSearch.Done(result.products)
                is SearchResult.Failed -> OnlineSearch.Failed(result.kind)
            }
        }
    }

    fun resetAddSearch() {
        onlineJob?.cancel()
        online.value = OnlineSearch.Idle
        addQuery.value = ""
    }

    val state: StateFlow<ShoppingUiState> = combine(
        repository.observe(),
        departmentRepository.observeDepartments(),
        departmentRepository.observeAssignments(),
        settings.groupShoppingByDepartment,
        catalogRepository.observeUnits(),
    ) { list, stored, assignments, grouped, units ->
        val departments = stored.ifEmpty { DepartmentDefaults.entities() }
        ShoppingUiState(
            loading = false,
            list = list,
            grouped = grouped,
            departments = departments,
            units = units.associate { it.id to it.symbol },
            rows = if (grouped) ShoppingBoard.build(departments, assignments, inputs(list)) else emptyList(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShoppingUiState())

    private fun inputs(list: ShoppingList): List<BoardInput<ShoppingPayload>> =
        list.shortages.map { s ->
            BoardInput<ShoppingPayload>(
                key = "s-" + s.product.id,
                name = s.product.name,
                productId = s.product.id,
                category = s.product.category,
                manualGroup = s.product.ingredientGroup,
                payload = ShoppingPayload.Short(s),
            )
        } + list.items.map { e ->
            val payload = ShoppingPayload.Item(e)
            BoardInput<ShoppingPayload>(
                key = "i-" + e.item.id,
                name = e.item.name,
                productId = payload.productId,
                category = e.product?.category,
                manualGroup = e.product?.ingredientGroup,
                payload = payload,
            )
        }

    /** Neuer Artikel aus dem Hinzufügen-Dialog (Name passt zu Produkt → verknüpft; steht schon drauf → Menge erhöht). */
    fun add(name: String, quantity: Int = 1, packageSize: java.math.BigDecimal? = null, unitSymbol: String? = null) =
        viewModelScope.launch { repository.addText(name, quantity, packageSize, unitSymbol) }

    /** Gewählten Suchtreffer hinzufügen (OFF-Treffer werden samt Bild als Produkt angelegt). */
    fun addPicked(pick: AddPick, quantity: Int, packageSize: java.math.BigDecimal?, unitSymbol: String?) =
        viewModelScope.launch {
            when (pick) {
                is AddPick.Local -> repository.addProduct(pick.product, quantity, packageSize, unitSymbol)
                is AddPick.Remote -> {
                    val image = pick.product.imageUrl?.let { search.downloadImage(it) }
                    repository.addLookedUp(pick.product, image, quantity, packageSize, unitSymbol)
                }
            }
        }

    /** Name, Anzahl und Packungsgröße aus dem Bearbeiten-Dialog. */
    fun updateDetails(id: String, name: String, quantity: Int, packageSize: java.math.BigDecimal?, unitSymbol: String?) =
        viewModelScope.launch { repository.updateDetails(id, name, quantity, packageSize, unitSymbol) }

    fun setQuantity(id: String, quantity: Int) = viewModelScope.launch {
        if (quantity >= 1) repository.setQuantity(id, quantity)
    }

    /** Abhaken: Eintrag entfernen, mit Rückgängig. */
    fun check(id: String) = viewModelScope.launch {
        val removed = repository.remove(id) ?: return@launch
        messages.show(
            UiMessage(
                text = R.string.shopping_checked,
                args = listOf(removed.name),
                actionLabel = R.string.action_undo,
                onAction = { repository.restore(removed) },
            ),
        )
    }

    fun setGrouped(on: Boolean) = viewModelScope.launch { settings.setGroupShoppingByDepartment(on) }

    /** Artikel in eine Abteilung legen (Drag & Drop oder Auswahldialog); gilt künftig auch für diesen Artikel. */
    fun moveToDepartment(payload: ShoppingPayload, departmentId: String) = viewModelScope.launch {
        val key = DepartmentResolver.assignmentKey(payload.productId, payload.name) ?: return@launch
        departmentRepository.assign(key, departmentId)
        val name = state.value.departments.firstOrNull { it.id == departmentId }?.name
            ?: DepartmentDefaults.nameOf(departmentId).orEmpty()
        messages.show(UiMessage(R.string.shopping_department_assigned, listOf(payload.name, name)))
    }

    /** Reihenfolge der sichtbaren Abteilungen speichern (gilt im ganzen Haushalt); leere behalten ihren Platz. */
    fun saveDepartmentOrder(visibleOrder: List<String>) = viewModelScope.launch {
        val all = departmentRepository.getAll().map { it.id }
        if (all.isEmpty()) return@launch
        departmentRepository.saveOrder(ShoppingBoard.mergeOrder(all, visibleOrder))
    }
}
