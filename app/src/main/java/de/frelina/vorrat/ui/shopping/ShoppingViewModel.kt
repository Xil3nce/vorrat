package de.frelina.vorrat.ui.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.frelina.vorrat.R
import de.frelina.vorrat.data.db.DepartmentEntity
import de.frelina.vorrat.data.repo.DepartmentRepository
import de.frelina.vorrat.data.repo.ProductRepository
import de.frelina.vorrat.data.repo.ShoppingEntry
import de.frelina.vorrat.data.repo.ShoppingList
import de.frelina.vorrat.data.repo.ShoppingRepository
import de.frelina.vorrat.data.settings.SettingsRepository
import de.frelina.vorrat.domain.BoardInput
import de.frelina.vorrat.domain.BoardRow
import de.frelina.vorrat.domain.DepartmentDefaults
import de.frelina.vorrat.domain.DepartmentResolver
import de.frelina.vorrat.domain.Shortage
import de.frelina.vorrat.domain.ShoppingBoard
import de.frelina.vorrat.ui.components.UiMessage
import de.frelina.vorrat.ui.components.UiMessages
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
    /** Flache Liste aus Überschriften und Einträgen. */
    val rows: List<BoardRow<ShoppingPayload>> = emptyList(),
)

@HiltViewModel
class ShoppingViewModel @Inject constructor(
    private val repository: ShoppingRepository,
    productRepository: ProductRepository,
    private val departmentRepository: DepartmentRepository,
    private val settings: SettingsRepository,
    private val messages: UiMessages,
) : ViewModel() {

    val state: StateFlow<ShoppingUiState> = combine(
        repository.observe(),
        departmentRepository.observeDepartments(),
        departmentRepository.observeAssignments(),
        settings.groupShoppingByDepartment,
    ) { list, stored, assignments, grouped ->
        val departments = stored.ifEmpty { DepartmentDefaults.entities() }
        ShoppingUiState(
            loading = false,
            list = list,
            grouped = grouped,
            departments = departments,
            rows = if (grouped) ShoppingBoard.build(departments, assignments, inputs(list)) else emptyList(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShoppingUiState())

    /** Produktnamen als Vorschläge für die Eingabe. */
    val suggestions: StateFlow<List<String>> = productRepository.observeAll()
        .map { products -> products.map { it.name }.distinct() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    fun add(name: String) = viewModelScope.launch { repository.addText(name) }

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
