package de.frelina.vorrat.ui.stock

import de.frelina.vorrat.domain.StockQueries
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.frelina.vorrat.R
import de.frelina.vorrat.domain.StockFilter
import de.frelina.vorrat.domain.StockSort
import de.frelina.vorrat.ui.components.StockItemCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockScreen(
    onOpenEntry: (String) -> Unit,
    onAddManual: () -> Unit,
    onDuplicateEntry: (String) -> Unit,
    onAddFrozen: () -> Unit,
    onReadNfc: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenRecipes: () -> Unit,
    viewModel: StockViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var sortMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stock_title)) },
                actions = {
                    IconButton(onClick = onReadNfc) {
                        Icon(Icons.Outlined.Nfc, contentDescription = stringResource(R.string.nfc_read_title))
                    }
                    IconButton(onClick = onOpenRecipes) {
                        Icon(Icons.Outlined.Restaurant, contentDescription = stringResource(R.string.recipes_title))
                    }
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Outlined.History, contentDescription = stringResource(R.string.history_title))
                    }
                    Box {
                        IconButton(onClick = { sortMenu = true }) {
                            Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = stringResource(R.string.stock_sort))
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            SortItem(R.string.stock_sort_expiry, state.sort == StockSort.EXPIRY) {
                                viewModel.setSort(StockSort.EXPIRY); sortMenu = false
                            }
                            SortItem(R.string.stock_sort_location, state.sort == StockSort.LOCATION) {
                                viewModel.setSort(StockSort.LOCATION); sortMenu = false
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.material3.SmallFloatingActionButton(onClick = onAddFrozen) {
                    Icon(Icons.Outlined.AcUnit, contentDescription = stringResource(R.string.frozen_add))
                }
                ExtendedFloatingActionButton(
                    onClick = onAddManual,
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.stock_add_manual)) },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            FilterRow(state, viewModel)
            if (state.expiredCount > 0 || state.soonCount > 0) {
                ExpiryBanner(
                    expired = state.expiredCount,
                    soon = state.soonCount,
                    onShow = {
                        viewModel.setFilter(if (state.expiredCount > 0) StockFilter.EXPIRED else StockFilter.SOON)
                    },
                )
            }
            if (state.soonCount > 0) {
                RecipeBanner(state.soonCount, onShow = onOpenRecipes)
            }
            if (state.unassignedCount > 0 && state.locationId != StockQueries.NO_LOCATION) {
                UnassignedBanner(state.unassignedCount, onShow = { viewModel.setLocation(StockQueries.NO_LOCATION) })
            }
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.items.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(if (state.totalCount == 0) R.string.stock_empty else R.string.stock_empty_filtered),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.items, key = { it.entryId }) { item ->
                        SwipeableStockItem(
                            onDelete = { viewModel.delete(item.entryId) },
                            onDuplicate = { onDuplicateEntry(item.entryId) },
                            modifier = Modifier.animateItem(),
                        ) {
                            StockItemCard(item, state.today, state.leadDays, onClick = { onOpenEntry(item.entryId) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SortItem(label: Int, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        onClick = onClick,
        leadingIcon = { if (selected) Icon(Icons.Outlined.Check, contentDescription = null) },
    )
}

@Composable
private fun FilterRow(state: StockUiState, viewModel: StockViewModel) {
    var locationMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            StockFilter.ALL to R.string.stock_filter_all,
            StockFilter.SOON to R.string.stock_filter_soon,
            StockFilter.EXPIRED to R.string.stock_filter_expired,
            StockFilter.FROZEN to R.string.stock_filter_frozen,
        ).forEach { (filter, label) ->
            FilterChip(
                selected = state.filter == filter,
                onClick = { viewModel.setFilter(filter) },
                label = { Text(stringResource(label)) },
            )
        }
        Box {
            val selectedName = if (state.locationId == StockQueries.NO_LOCATION) {
                stringResource(R.string.location_none)
            } else {
                state.locations.firstOrNull { it.id == state.locationId }?.name
            }
            FilterChip(
                selected = selectedName != null,
                onClick = { locationMenu = true },
                label = { Text(selectedName ?: stringResource(R.string.stock_filter_location)) },
                leadingIcon = { Icon(Icons.Outlined.Place, contentDescription = null) },
            )
            DropdownMenu(expanded = locationMenu, onDismissRequest = { locationMenu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.stock_filter_all_locations)) },
                    onClick = { viewModel.setLocation(null); locationMenu = false },
                )
                if (state.unassignedCount > 0 || state.locationId == StockQueries.NO_LOCATION) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.stock_filter_no_location, state.unassignedCount)) },
                        onClick = { viewModel.setLocation(StockQueries.NO_LOCATION); locationMenu = false },
                    )
                }
                state.locations.forEach { loc ->
                    DropdownMenuItem(
                        text = { Text(loc.name) },
                        onClick = { viewModel.setLocation(loc.id); locationMenu = false },
                    )
                }
            }
        }
        if (state.levelOptions.isNotEmpty() || state.level != null) {
            SlotFilterChip(
                selected = state.level,
                options = state.levelOptions,
                chipLabel = R.string.stock_filter_level,
                allLabel = R.string.stock_filter_all_levels,
                valueLabel = R.string.level_label,
                onSelect = viewModel::setLevel,
            )
        }
        if (state.shelfOptions.isNotEmpty() || state.shelf != null) {
            SlotFilterChip(
                selected = state.shelf,
                options = state.shelfOptions,
                chipLabel = R.string.stock_filter_shelf,
                allLabel = R.string.stock_filter_all_shelves,
                valueLabel = R.string.shelf_label,
                onSelect = viewModel::setShelf,
            )
        }
    }
}

/** Filter-Chip mit Auswahlmenü für Ebene bzw. Fach. */
@Composable
private fun SlotFilterChip(
    selected: Int?,
    options: List<Int>,
    chipLabel: Int,
    allLabel: Int,
    valueLabel: Int,
    onSelect: (Int?) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected != null,
            onClick = { menu = true },
            label = { Text(if (selected != null) stringResource(valueLabel, selected) else stringResource(chipLabel)) },
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(allLabel)) },
                onClick = { onSelect(null); menu = false },
            )
            options.forEach { n ->
                DropdownMenuItem(
                    text = { Text(stringResource(valueLabel, n)) },
                    onClick = { onSelect(n); menu = false },
                )
            }
        }
    }
}

@Composable
private fun ExpiryBanner(expired: Int, soon: Int, onShow: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = if (expired > 0) {
        colors.errorContainer to colors.onErrorContainer
    } else {
        colors.tertiaryContainer to colors.onTertiaryContainer
    }
    Surface(
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.WarningAmber, contentDescription = null)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                if (expired > 0) Text(pluralStringResource(R.plurals.banner_expired, expired, expired))
                if (soon > 0) Text(pluralStringResource(R.plurals.banner_soon, soon, soon))
            }
            TextButton(onClick = onShow) { Text(stringResource(R.string.banner_show)) }
        }
    }
}

/**
 * Eintrag mit Wischgesten: nach links (Ende → Anfang) löschen, nach rechts (Anfang → Ende) duplizieren.
 * Duplizieren öffnet das vorbelegte Formular; die Kopie entsteht erst beim Speichern, der Eintrag springt zurück. Für Screenreader gibt es dieselben Aktionen als
 * Bedienungshilfen-Aktionen.
 */
@Composable
private fun SwipeableStockItem(
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // Erst ab 40 % der Breite auslösen (Standard sind 56 dp – zu empfindlich, kurze Wischer beim Scrollen würden löschen/duplizieren).
    val swipeState = rememberSwipeToDismissBoxState(positionalThreshold = { totalDistance -> totalDistance * 0.4f })
    val scope = rememberCoroutineScope()
    val deleteLabel = stringResource(R.string.stock_swipe_delete)
    val duplicateLabel = stringResource(R.string.stock_swipe_duplicate)
    SwipeToDismissBox(
        state = swipeState,
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(deleteLabel) { onDelete(); true },
                    CustomAccessibilityAction(duplicateLabel) { onDuplicate(); true },
                )
            },
        backgroundContent = { SwipeBackground(swipeState.dismissDirection, deleteLabel, duplicateLabel) },
        onDismiss = { direction ->
            when (direction) {
                SwipeToDismissBoxValue.EndToStart -> onDelete()
                SwipeToDismissBoxValue.StartToEnd -> {
                    onDuplicate()
                    scope.launch { swipeState.reset() }
                }
                SwipeToDismissBoxValue.Settled -> Unit
            }
        },
    ) { content() }
}

@Composable
private fun SwipeBackground(direction: SwipeToDismissBoxValue, deleteLabel: String, duplicateLabel: String) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (direction) {
        SwipeToDismissBoxValue.EndToStart -> colors.errorContainer to colors.onErrorContainer
        SwipeToDismissBoxValue.StartToEnd -> colors.secondaryContainer to colors.onSecondaryContainer
        SwipeToDismissBoxValue.Settled -> Color.Transparent to colors.onSurface
    }
    Row(
        Modifier
            .fillMaxSize()
            .background(container)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (direction == SwipeToDismissBoxValue.EndToStart) Arrangement.End else Arrangement.Start,
    ) {
        when (direction) {
            SwipeToDismissBoxValue.StartToEnd -> {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null, tint = content)
                Spacer(Modifier.width(12.dp))
                Text(duplicateLabel, color = content, style = MaterialTheme.typography.titleSmall)
            }
            SwipeToDismissBoxValue.EndToStart -> {
                Text(deleteLabel, color = content, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.width(12.dp))
                Icon(Icons.Outlined.Delete, contentDescription = null, tint = content)
            }
            SwipeToDismissBoxValue.Settled -> Unit
        }
    }
}

/** Hinweis auf Einträge ohne Ort (z. B. im Laden erfasst) mit Sprung zum Filter "Ohne Ort". */
@Composable
private fun UnassignedBanner(count: Int, onShow: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Place, contentDescription = null)
            Text(
                pluralStringResource(R.plurals.stock_unassigned_banner, count, count),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            )
            TextButton(onClick = onShow) { Text(stringResource(R.string.stock_unassigned_show)) }
        }
    }
}

/** Hinweis auf bald ablaufende Produkte mit Sprung zu den Rezeptvorschlägen. */
@Composable
private fun RecipeBanner(count: Int, onShow: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Restaurant, contentDescription = null)
            Text(
                pluralStringResource(R.plurals.recipes_banner, count, count),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            )
            TextButton(onClick = onShow) { Text(stringResource(R.string.recipes_banner_show)) }
        }
    }
}
