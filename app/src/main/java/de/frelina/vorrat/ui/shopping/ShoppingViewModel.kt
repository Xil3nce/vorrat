package de.frelina.vorrat.ui.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.frelina.vorrat.R
import de.frelina.vorrat.data.repo.ProductRepository
import de.frelina.vorrat.data.repo.ShoppingList
import de.frelina.vorrat.data.repo.ShoppingRepository
import de.frelina.vorrat.ui.components.UiMessage
import de.frelina.vorrat.ui.components.UiMessages
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShoppingUiState(val loading: Boolean = true, val list: ShoppingList = ShoppingList(emptyList(), emptyList()))

@HiltViewModel
class ShoppingViewModel @Inject constructor(
    private val repository: ShoppingRepository,
    productRepository: ProductRepository,
    private val messages: UiMessages,
) : ViewModel() {

    val state: StateFlow<ShoppingUiState> = repository.observe()
        .map { ShoppingUiState(loading = false, list = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShoppingUiState())

    /** Produktnamen als Vorschläge für die Eingabe. */
    val suggestions: StateFlow<List<String>> = productRepository.observeAll()
        .map { products -> products.map { it.name }.distinct() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
}
