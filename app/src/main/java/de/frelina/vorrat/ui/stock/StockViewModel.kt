package de.frelina.vorrat.ui.stock

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import de.frelina.vorrat.MainActivity
import de.frelina.vorrat.R
import de.frelina.vorrat.data.db.LocationEntity
import de.frelina.vorrat.data.db.StockItem
import de.frelina.vorrat.data.repo.CatalogRepository
import de.frelina.vorrat.data.repo.StockRepository
import de.frelina.vorrat.data.settings.SettingsRepository
import de.frelina.vorrat.domain.ExpiryLogic
import de.frelina.vorrat.domain.ExpiryStatus
import de.frelina.vorrat.domain.StockFilter
import de.frelina.vorrat.domain.StockQueries
import de.frelina.vorrat.domain.StockSort
import de.frelina.vorrat.ui.components.UiMessage
import de.frelina.vorrat.ui.components.UiMessages
import de.frelina.vorrat.ui.navigation.StockRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class StockUiState(
    val loading: Boolean = true,
    val items: List<StockItem> = emptyList(),
    val totalCount: Int = 0,
    val expiredCount: Int = 0,
    val soonCount: Int = 0,
    val sort: StockSort = StockSort.EXPIRY,
    val filter: StockFilter = StockFilter.ALL,
    val locationId: String? = null,
    val locations: List<LocationEntity> = emptyList(),
    val level: Int? = null,
    val shelf: Int? = null,
    /** Auswählbare Ebenen/Fächer (am gewählten Ort bzw. im ganzen Bestand). */
    val levelOptions: List<Int> = emptyList(),
    val shelfOptions: List<Int> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    val leadDays: Int = ExpiryLogic.DEFAULT_LEAD_DAYS,
)

private data class Controls(
    val sort: StockSort,
    val filter: StockFilter,
    val locationId: String?,
    val level: Int? = null,
    val shelf: Int? = null,
)

@HiltViewModel
class StockViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val stockRepository: StockRepository,
    catalogRepository: CatalogRepository,
    settingsRepository: SettingsRepository,
    private val messages: UiMessages,
) : ViewModel() {

    private val initialFilter = when (savedStateHandle.toRoute<StockRoute>().filter) {
        MainActivity.FILTER_SOON -> StockFilter.SOON
        else -> StockFilter.ALL
    }

    private val controls = MutableStateFlow(Controls(StockSort.EXPIRY, initialFilter, null))

    val state: StateFlow<StockUiState> = combine(
        stockRepository.observeItems(),
        catalogRepository.observeLocations(),
        settingsRepository.reminderSettings,
        controls,
    ) { items, locations, settings, c ->
        val today = LocalDate.now()
        val lead = settings.leadDays
        val statuses = items.map { ExpiryLogic.status(it.expiryDate, today, lead) }
        val location = locations.firstOrNull { it.id == c.locationId }
        val atLocation = if (location == null) items else items.filter { it.locationId == location.id }
        StockUiState(
            loading = false,
            items = StockQueries.sort(
                StockQueries.filter(items, c.filter, location?.id, today, lead, c.level, c.shelf),
                c.sort,
            ),
            totalCount = items.size,
            expiredCount = statuses.count { it == ExpiryStatus.EXPIRED },
            soonCount = statuses.count { it == ExpiryStatus.SOON },
            sort = c.sort,
            filter = c.filter,
            locationId = location?.id,
            locations = locations,
            level = c.level,
            shelf = c.shelf,
            levelOptions = StockQueries.slotOptions(atLocation.map { it.level }, location?.levels),
            shelfOptions = StockQueries.slotOptions(atLocation.map { it.shelf }, location?.shelves),
            today = today,
            leadDays = lead,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StockUiState())

    fun setSort(sort: StockSort) = controls.value.let { controls.value = it.copy(sort = sort) }
    fun setFilter(filter: StockFilter) = controls.value.let { controls.value = it.copy(filter = filter) }
    /** Ortswechsel setzt Ebene und Fach zurück (die Werte gehören zum jeweiligen Ort). */
    fun setLocation(id: String?) = controls.value.let {
        controls.value = if (it.locationId == id) it else it.copy(locationId = id, level = null, shelf = null)
    }
    fun setLevel(level: Int?) = controls.value.let { controls.value = it.copy(level = level) }
    fun setShelf(shelf: Int?) = controls.value.let { controls.value = it.copy(shelf = shelf) }

    /** Wischen nach links: Eintrag löschen, mit Rückgängig. */
    fun delete(entryId: String) = viewModelScope.launch {
        val removed = stockRepository.deleteEntry(entryId) ?: return@launch
        messages.show(
            UiMessage(
                text = R.string.entry_deleted,
                actionLabel = R.string.action_undo,
                onAction = { stockRepository.restoreEntry(removed) },
            ),
        )
    }

}
