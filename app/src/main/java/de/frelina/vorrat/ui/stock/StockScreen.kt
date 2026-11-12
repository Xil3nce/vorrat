package de.frelina.vorrat.ui.stock

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import de.frelina.vorrat.ui.components.GlassCard
import de.frelina.vorrat.ui.components.GlassIconButton
import de.frelina.vorrat.ui.components.SegmentRing
import de.frelina.vorrat.ui.theme.VorratTheme
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
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
    var dialOpen by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(state.query.isNotEmpty()) }
    val listState = rememberLazyListState()
    // Lupe: Liste nach oben scrollen und das Suchfeld fokussieren (auch wenn weit unten gescrollt war).
    var searchRequest by remember { mutableIntStateOf(0) }
    var focusPending by remember { mutableStateOf(false) }
    LaunchedEffect(searchRequest) { if (searchRequest > 0) listState.scrollToItem(0) }
    BackHandler(enabled = dialOpen) { dialOpen = false }
    BackHandler(enabled = searchOpen && !dialOpen) { searchOpen = false; viewModel.setQuery("") }

    Scaffold(
        topBar = {
            Box {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.stock_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                actions = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(end = 8.dp)) {
                    GlassIconButton(Icons.Outlined.Search, stringResource(R.string.stock_search), {
                        if (searchOpen && listState.firstVisibleItemIndex == 0) {
                            viewModel.setQuery("")
                            searchOpen = false
                        } else {
                            // Zu oder offen, aber weggescrollt: hochscrollen und fokussieren.
                            searchOpen = true
                            focusPending = true
                            searchRequest++
                        }
                    })
                    GlassIconButton(Icons.Outlined.Nfc, stringResource(R.string.nfc_read_title), onReadNfc)
                    GlassIconButton(Icons.Outlined.Restaurant, stringResource(R.string.recipes_title), onOpenRecipes)
                    GlassIconButton(Icons.Outlined.History, stringResource(R.string.history_title), onOpenHistory)
                    Box {
                        GlassIconButton(Icons.AutoMirrored.Outlined.Sort, stringResource(R.string.stock_sort), { sortMenu = true })
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            SortItem(R.string.stock_sort_expiry, state.sort == StockSort.EXPIRY) {
                                viewModel.setSort(StockSort.EXPIRY); sortMenu = false
                            }
                            SortItem(R.string.stock_sort_location, state.sort == StockSort.LOCATION) {
                                viewModel.setSort(StockSort.LOCATION); sortMenu = false
                            }
                        }
                    }
                    }
                },
            )
            // Offener Speed-Dial: Tippen auf die Kopfzeile klappt ihn nur zu.
            if (dialOpen) {
                Box(
                    Modifier
                        .matchParentSize()
                        .clickable(interactionSource = null, indication = null) { dialOpen = false },
                )
            }
            }
        },
        floatingActionButton = {
            // Speed-Dial: Optionen-Button, darüber klappen "Manuell anlegen" und "Einfrieren" auf.
            // 8 dp nach unten versetzt: nur ~8 dp Abstand zur Navigationsleiste (Scaffold lässt 16 dp).
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.offset(y = 8.dp),
            ) {
                AnimatedVisibility(
                    visible = dialOpen,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 },
                ) {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SmallFloatingActionButton(onClick = { dialOpen = false; onAddManual() }) {
                            Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.stock_add_manual))
                        }
                        SmallFloatingActionButton(onClick = { dialOpen = false; onAddFrozen() }) {
                            Icon(Icons.Outlined.AcUnit, contentDescription = stringResource(R.string.frozen_add))
                        }
                    }
                }
                SmallFloatingActionButton(onClick = { dialOpen = !dialOpen }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.stock_more_actions))
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
        when {
            state.loading -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 64.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (searchOpen) {
                    item(key = "search") {
                        SearchField(
                            state.query,
                            viewModel::setQuery,
                            focusPending = focusPending,
                            onFocused = { focusPending = false },
                            onClose = { searchOpen = false; viewModel.setQuery("") },
                        )
                    }
                }
                if (state.totalCount > 0 && state.query.isBlank()) {
                    item(key = "overview") {
                        OverviewCard(state, onFilter = viewModel::setFilter, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
                item(key = "filters") { FilterRow(state, viewModel) }
                if (state.soonCount > 0) {
                    item(key = "recipes") { RecipeBanner(state.soonCount, onShow = onOpenRecipes) }
                }
                if (state.unassignedCount > 0 && state.locationId != StockQueries.NO_LOCATION) {
                    item(key = "unassigned") {
                        UnassignedBanner(state.unassignedCount, onShow = { viewModel.setLocation(StockQueries.NO_LOCATION) })
                    }
                }
                if (state.items.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            when {
                                state.totalCount == 0 -> stringResource(R.string.stock_empty)
                                state.query.isNotBlank() -> stringResource(R.string.search_no_results, state.query.trim())
                                else -> stringResource(R.string.stock_empty_filtered)
                            },
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge,
                            color = VorratTheme.colors.textSecondary,
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                        )
                    }
                }
                items(state.items, key = { it.entryId }) { item ->
                    SwipeableStockItem(
                        onDelete = { viewModel.delete(item.entryId) },
                        onDuplicate = { onDuplicateEntry(item.entryId) },
                        modifier = Modifier.animateItem().padding(horizontal = 16.dp),
                    ) {
                        StockItemCard(item, state.today, state.leadDays, onClick = { onOpenEntry(item.entryId) })
                    }
                }
            }
        }
        if (dialOpen) {
            // Unsichtbarer Vollflächen-Layer: Tippen irgendwo sonst klappt das Menü zu.
            Box(
                Modifier.fillMaxSize().clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { dialOpen = false },
                ),
            )
        }
        }
    }
}

/** Suchfeld unter der Kopfzeile (Lupe); durchsucht den gefilterten Bestand. */
@Composable
private fun SearchField(
    query: String,
    onQuery: (String) -> Unit,
    focusPending: Boolean,
    onFocused: () -> Unit,
    onClose: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(focusPending) {
        if (focusPending) {
            focus.requestFocus()
            onFocused()
        }
    }
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        placeholder = { Text(stringResource(R.string.search_hint)) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Clear, contentDescription = stringResource(R.string.stock_search_close))
            }
        },
        shape = RoundedCornerShape(24.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = VorratTheme.colors.glass,
            focusedContainerColor = VorratTheme.colors.glassStrong,
            unfocusedBorderColor = VorratTheme.colors.glassBorder,
        ),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).focusRequester(focus),
    )
}

/**
 * Übersicht mit Ring: Anteile abgelaufen (rot), bald ab (gelb), gut haltbar (grün).
 * Antippen einer Zeile filtert die Liste.
 */
@Composable
private fun OverviewCard(state: StockUiState, onFilter: (StockFilter) -> Unit, modifier: Modifier = Modifier) {
    val colors = VorratTheme.colors
    val total = state.totalCount.coerceAtLeast(1)
    val ok = (state.totalCount - state.expiredCount - state.soonCount).coerceAtLeast(0)
    GlassCard(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Row(
            Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            SegmentRing(
                segments = listOf(
                    state.expiredCount.toFloat() / total to colors.danger,
                    state.soonCount.toFloat() / total to colors.warning,
                    ok.toFloat() / total to colors.accent,
                ),
                size = 104.dp,
                stroke = 11.dp,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.totalCount.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        pluralStringResource(R.plurals.stock_overview_entries, state.totalCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                OverviewLine(colors.accent, pluralStringResource(R.plurals.stock_overview_ok, ok, ok)) { onFilter(StockFilter.ALL) }
                OverviewLine(colors.warning, pluralStringResource(R.plurals.stock_overview_soon, state.soonCount, state.soonCount)) {
                    onFilter(StockFilter.SOON)
                }
                OverviewLine(
                    colors.danger,
                    pluralStringResource(R.plurals.stock_overview_expired, state.expiredCount, state.expiredCount),
                ) { onFilter(StockFilter.EXPIRED) }
            }
        }
    }
}

@Composable
private fun OverviewLine(color: Color, text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 10.dp))
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
        FilterChip(
            selected = state.filter == StockFilter.ALL,
            onClick = { viewModel.setFilter(StockFilter.ALL) },
            label = { Text(stringResource(R.string.stock_filter_all)) },
        )
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
        listOf(
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
    val swipeState = de.frelina.vorrat.ui.components.rememberListSwipeState()
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
