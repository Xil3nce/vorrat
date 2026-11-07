package de.frelina.vorrat.ui.stock

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Place
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockScreen(
    onOpenEntry: (Long) -> Unit,
    onAddManual: () -> Unit,
    viewModel: StockViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var sortMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stock_title)) },
                actions = {
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
            ExtendedFloatingActionButton(
                onClick = onAddManual,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.stock_add_manual)) },
            )
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
                        StockItemCard(item, state.today, state.leadDays, onClick = { onOpenEntry(item.entryId) })
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
        ).forEach { (filter, label) ->
            FilterChip(
                selected = state.filter == filter,
                onClick = { viewModel.setFilter(filter) },
                label = { Text(stringResource(label)) },
            )
        }
        Box {
            val selectedName = state.locations.firstOrNull { it.id == state.locationId }?.name
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
                state.locations.forEach { loc ->
                    DropdownMenuItem(
                        text = { Text(loc.name) },
                        onClick = { viewModel.setLocation(loc.id); locationMenu = false },
                    )
                }
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
