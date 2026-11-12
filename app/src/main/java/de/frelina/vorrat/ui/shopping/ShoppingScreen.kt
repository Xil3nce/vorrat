package de.frelina.vorrat.ui.shopping

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.input.KeyboardType
import de.frelina.vorrat.data.db.ShoppingItemEntity
import de.frelina.vorrat.data.repo.ShoppingList
import de.frelina.vorrat.data.sync.RemoteMapper
import de.frelina.vorrat.domain.ShoppingLogic
import de.frelina.vorrat.domain.ShoppingRecipes
import de.frelina.vorrat.domain.DepartmentResolver
import de.frelina.vorrat.data.repo.ShoppingItemUpdate
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.InputChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.rememberModalBottomSheetState
import de.frelina.vorrat.domain.Validated
import de.frelina.vorrat.domain.Validation
import de.frelina.vorrat.ui.components.AmountBadge
import de.frelina.vorrat.ui.components.SegmentRing
import de.frelina.vorrat.ui.components.glassBorder
import de.frelina.vorrat.ui.components.validationMessage
import de.frelina.vorrat.ui.theme.VorratTheme
import java.math.BigDecimal
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import de.frelina.vorrat.ui.components.GlassCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.frelina.vorrat.R
import coil3.compose.AsyncImage
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.layout.ContentScale
import de.frelina.vorrat.data.db.ProductEntity
import de.frelina.vorrat.data.off.LookedUpProduct
import de.frelina.vorrat.data.off.LookupErrorKind
import de.frelina.vorrat.data.db.DepartmentEntity
import de.frelina.vorrat.data.repo.ShoppingEntry
import de.frelina.vorrat.domain.BoardRow
import de.frelina.vorrat.domain.DepartmentDefaults
import de.frelina.vorrat.domain.Shortage
import de.frelina.vorrat.domain.ShoppingBoard
import de.frelina.vorrat.ui.components.ProductImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Einkaufsliste: oben Eingabe, darunter die Einträge – standardmäßig nach Abteilungen gruppiert (Reihenfolge wie
 * im Laden, im Haushalt gespeichert), sonst "Nachkaufen" (unter Mindestbestand, automatisch) und die manuell
 * hinzugefügten Einträge. Karten im Stil der Bestandsliste mit Produktbild. Antippen öffnet das Bearbeiten
 * (Bottom-Sheet mit allen Angaben des Eintrags), nach rechts wischen ("Gekauft") das Erfassen (ein Ort ist dabei nicht nötig), nach
 * links wischen hakt ab. Gruppiert lassen
 * sich Einträge per langem Drücken in eine andere Abteilung ziehen und Abteilungen am Griff verschieben.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingScreen(
    /** Erfassen nach dem Kauf: Produkt-ID (bekanntes Produkt) bzw. Listeneintrag. */
    onBought: (productId: String?, shoppingItemId: String?, name: String) -> Unit,
    onOpenDepartments: () -> Unit,
    onOpenMealPlan: () -> Unit,
    onOpenShopMode: () -> Unit,
    viewModel: ShoppingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var changeTarget by remember { mutableStateOf<Pair<ShoppingPayload, String>?>(null) }
    var editTarget by remember { mutableStateOf<ShoppingEntry?>(null) }
    var addDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.shopping_title)) },
                actions = {
                    if (!state.list.isEmpty) {
                        IconButton(onClick = onOpenShopMode) {
                            Icon(Icons.Outlined.Storefront, contentDescription = stringResource(R.string.shop_mode_start))
                        }
                    }
                    ShoppingMenu(
                        grouped = state.grouped,
                        onToggleGrouped = { viewModel.setGrouped(!state.grouped) },
                        onOpenDepartments = onOpenDepartments,
                        onOpenMealPlan = onOpenMealPlan,
                    )
                },
            )
        },
        floatingActionButton = {
            // Wie im Bestand: 8 dp nach unten versetzt (nur ~8 dp Abstand zur Navigationsleiste).
            SmallFloatingActionButton(onClick = { addDialog = true }, modifier = Modifier.offset(y = 8.dp)) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.shopping_add_hint))
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (!state.loading && !state.list.isEmpty) ShoppingSummary(state.list)
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.list.isEmpty -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.shopping_empty),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                state.grouped -> GroupedList(
                    state = state,
                    onBought = onBought,
                    onCheck = viewModel::check,
                    onEdit = { editTarget = it },
                    onMoveToDepartment = viewModel::moveToDepartment,
                    onSaveDepartmentOrder = viewModel::saveDepartmentOrder,
                    onChangeDepartment = { payload, current -> changeTarget = payload to current },
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = LIST_BOTTOM_PADDING),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.list.shortages.isNotEmpty()) {
                        item(key = "h-short") { SectionLabel(R.string.shopping_section_shortage) }
                        items(state.list.shortages, key = { "s-" + it.product.id }) { s ->
                            ShortageRow(
                                s,
                                units = state.units,
                                onBought = { onBought(s.product.id, null, s.product.name) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                    if (state.list.items.isNotEmpty()) {
                        item(key = "h-items") { SectionLabel(R.string.shopping_section_items) }
                        items(state.list.items, key = { "i-" + it.item.id }) { e ->
                            ItemRow(
                                entry = e,
                                onCheck = { viewModel.check(e.item.id) },
                                onEdit = { editTarget = e },
                                onBought = { onBought(e.item.productId, e.item.id, e.item.name) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }

    if (addDialog) {
        val addSearch by viewModel.addSearch.collectAsStateWithLifecycle()
        ShoppingAddDialog(
            unitSymbols = state.units.values.distinct().sorted(),
            search = addSearch,
            onQueryChange = viewModel::setAddQuery,
            onSearchOnline = viewModel::searchOnline,
            onDismiss = {
                addDialog = false
                viewModel.resetAddSearch()
            },
            onAdd = { name, quantity, size, unit, pick ->
                addDialog = false
                viewModel.resetAddSearch()
                if (pick != null) viewModel.addPicked(pick, quantity, size, unit)
                else viewModel.add(name, quantity, size, unit)
            },
        )
    }

    editTarget?.let { entry ->
        val addSearch by viewModel.addSearch.collectAsStateWithLifecycle()
        val departmentIds = state.departments.map { it.id }.toSet()
        ShoppingItemSheet(
            entry = entry,
            unitSymbols = state.units.values.distinct().sorted(),
            departments = state.departments,
            currentDepartmentId = DepartmentResolver.resolve(
                entry.item.productId ?: entry.product?.id,
                entry.item.name,
                entry.product?.category,
                entry.product?.ingredientGroup,
                state.assignments,
                departmentIds,
            ),
            search = addSearch,
            onQueryChange = viewModel::setAddQuery,
            onSearchOnline = viewModel::searchOnline,
            onResetSearch = viewModel::resetAddSearch,
            onDismiss = {
                editTarget = null
                viewModel.resetAddSearch()
            },
            onDelete = {
                editTarget = null
                viewModel.resetAddSearch()
                viewModel.delete(entry.item.id)
            },
            onSave = { update, pick, departmentId ->
                editTarget = null
                viewModel.resetAddSearch()
                viewModel.saveEntry(entry.item.id, update, pick, departmentId)
            },
        )
    }

    changeTarget?.let { (payload, current) ->
        ChangeDepartmentDialog(
            name = payload.name,
            departments = state.departments,
            currentId = current,
            onSelect = {
                changeTarget = null
                if (it != current) viewModel.moveToDepartment(payload, it)
            },
            onDismiss = { changeTarget = null },
        )
    }
}

@Composable
private fun ShoppingMenu(
    grouped: Boolean,
    onToggleGrouped: () -> Unit,
    onOpenDepartments: () -> Unit,
    onOpenMealPlan: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.shopping_menu_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.shopping_group_by_department)) },
                leadingIcon = {
                    if (grouped) Icon(Icons.Outlined.Check, contentDescription = null) else Spacer(Modifier.size(24.dp))
                },
                onClick = { open = false; onToggleGrouped() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.shopping_open_departments)) },
                onClick = { open = false; onOpenDepartments() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.shopping_open_mealplan)) },
                onClick = { open = false; onOpenMealPlan() },
            )
        }
    }
}

/** Auswahl der Abteilung (barrierearme Alternative zum Ziehen). */
@Composable
private fun ChangeDepartmentDialog(
    name: String,
    departments: List<DepartmentEntity>,
    currentId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.shopping_change_department_title, name)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                departments.forEach { d ->
                    Row(
                        Modifier.fillMaxWidth()
                            .selectable(selected = d.id == currentId, role = Role.RadioButton, onClick = { onSelect(d.id) })
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = d.id == currentId, onClick = null)
                        Text(d.name, modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * Gruppierte Liste als EINE flache LazyColumn (Überschrift, Einträge, Überschrift, …), damit Drag & Drop über
 * Abteilungsgrenzen funktioniert. Während des Ziehens arbeitet die Liste auf einer lokalen Kopie ([local]);
 * beim Loslassen wird die Zuordnung bzw. Reihenfolge gespeichert und die Kopie wieder durch den gespeicherten
 * Stand ersetzt.
 */
@Composable
private fun GroupedList(
    state: ShoppingUiState,
    onBought: (productId: String?, shoppingItemId: String?, name: String) -> Unit,
    onCheck: (String) -> Unit,
    onEdit: (ShoppingEntry) -> Unit,
    onMoveToDepartment: (ShoppingPayload, String) -> Unit,
    onSaveDepartmentOrder: (List<String>) -> Unit,
    onChangeDepartment: (ShoppingPayload, String) -> Unit,
) {
    val lazyListState = rememberLazyListState()
    var local by remember { mutableStateOf<List<BoardRow<ShoppingPayload>>?>(null) }
    var draggedKey by remember { mutableStateOf<String?>(null) }
    var settleTick by remember { mutableIntStateOf(0) }
    val rows = local ?: state.rows
    val rowsNow by rememberUpdatedState(rows)
    val upstreamNow by rememberUpdatedState(state.rows)
    val departmentsNow by rememberUpdatedState(state.departments)

    // Neuer gespeicherter Stand: lokale Kopie verwerfen (außer während des Ziehens).
    LaunchedEffect(state.rows) { if (draggedKey == null) local = null }
    // Sicherheitsnetz, falls das Speichern den Stand nicht ändert.
    LaunchedEffect(settleTick) {
        if (settleTick > 0) {
            delay(SETTLE_MS)
            if (draggedKey == null) local = null
        }
    }

    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val base = rowsNow
        val moved = if (base.getOrNull(from.index)?.isHeader == true) {
            ShoppingBoard.moveDepartment(base, from.index, to.index)
        } else {
            ShoppingBoard.moveEntry(base, from.index, to.index)
        }
        if (moved != null) local = moved
    }

    fun dragStarted(key: String) {
        draggedKey = key
        if (local == null) local = upstreamNow
    }

    fun dragStopped() {
        val key = draggedKey ?: return
        draggedKey = null
        val current = local ?: return
        val dragged = current.firstOrNull { it.key == key }
        var persisted = false
        val payload = dragged?.payload
        if (dragged != null && payload == null) {
            val order = ShoppingBoard.departmentOrder(current)
            if (order != ShoppingBoard.departmentOrder(upstreamNow)) {
                onSaveDepartmentOrder(order)
                persisted = true
            }
        } else if (dragged != null && payload != null) {
            val before = upstreamNow.firstOrNull { it.key == key }?.departmentId
            if (before != dragged.departmentId) {
                onMoveToDepartment(payload, dragged.departmentId)
                persisted = true
            }
        }
        if (persisted) settleTick++ else local = null
    }

    val counts = remember(rows) { departmentCounts(rows) }
    LazyColumn(
        state = lazyListState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = LIST_BOTTOM_PADDING),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(rows, key = { it.key }) { row ->
            ReorderableItem(reorderState, key = row.key) { isDragging ->
                val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "drag-elevation")
                val lifted = Modifier.shadow(elevation, MaterialTheme.shapes.medium)
                val payload = row.payload
                if (payload == null) {
                    val name = departmentsNow.firstOrNull { it.id == row.departmentId }?.name
                        ?: DepartmentDefaults.nameOf(row.departmentId).orEmpty()
                    DepartmentHeader(
                        name = name,
                        count = counts[row.departmentId] ?: 0,
                        modifier = lifted,
                        handleModifier = Modifier.draggableHandle(
                            onDragStarted = { dragStarted(row.key) },
                            onDragStopped = { dragStopped() },
                        ),
                    )
                } else {
                    val dragModifier = lifted.longPressDraggableHandle(
                        onDragStarted = { dragStarted(row.key) },
                        onDragStopped = { dragStopped() },
                    )
                    val change = { onChangeDepartment(payload, row.departmentId) }
                    when (payload) {
                        is ShoppingPayload.Short -> ShortageRow(
                            s = payload.shortage,
                            units = state.units,
                            onBought = { onBought(payload.shortage.product.id, null, payload.shortage.product.name) },
                            modifier = dragModifier,
                            onChangeDepartment = change,
                        )
                        is ShoppingPayload.Item -> ItemRow(
                            entry = payload.entry,
                            onCheck = { onCheck(payload.entry.item.id) },
                            onEdit = { onEdit(payload.entry) },
                            onBought = { onBought(payload.entry.item.productId, payload.entry.item.id, payload.entry.item.name) },
                            modifier = dragModifier,
                            onChangeDepartment = change,
                        )
                    }
                }
            }
        }
    }
}

private const val SETTLE_MS = 1_500L

/** Anzahl Einträge je Abteilung in der flachen Liste. */
private fun departmentCounts(rows: List<BoardRow<ShoppingPayload>>): Map<String, Int> {
    val result = LinkedHashMap<String, Int>()
    var current: String? = null
    rows.forEach { row ->
        if (row.isHeader) {
            current = row.departmentId
            result[row.departmentId] = 0
        } else {
            current?.let { result[it] = (result[it] ?: 0) + 1 }
        }
    }
    return result
}

/** Überschrift einer Abteilung mit Anzahl; am Griff lässt sie sich samt Inhalt verschieben. */
@Composable
private fun DepartmentHeader(name: String, count: Int, modifier: Modifier, handleModifier: Modifier) {
    val dragLabel = stringResource(R.string.shopping_department_drag, name)
    Row(modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.shopping_department_header, name, count),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        Icon(
            Icons.Outlined.DragHandle,
            contentDescription = dragLabel,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = handleModifier.size(48.dp).padding(12.dp),
        )
    }
}

@Composable
private fun SectionLabel(text: Int) {
    de.frelina.vorrat.ui.components.SectionLabel(stringResource(text), Modifier.padding(top = 8.dp, start = 4.dp))
}

/**
 * Karte im Stil der Bestandsliste: Bild links, Name/Marke/Detail in der Mitte, Menge rechts.
 * Mit [onChangeDepartment] erscheint ein Menü "Abteilung ändern …".
 */
@Composable
internal fun ShoppingCard(
    imagePath: String?,
    name: String,
    brand: String?,
    detail: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    onChangeDepartment: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    // Hervorgehoben (unter Mindestbestand): gelblicher Rand statt farbiger Fläche.
    val border = if (highlighted) BorderStroke(1.dp, VorratTheme.colors.warning.copy(alpha = 0.45f)) else glassBorder()
    GlassCard(onClick = onClick, modifier = modifier.fillMaxWidth(), border = border) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProductImage(imagePath, size = 48.dp, name = name)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (onChangeDepartment != null) ItemMenu(name, onChangeDepartment)
                }
                brand?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = VorratTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                CompositionLocalProvider(LocalContentColor provides VorratTheme.colors.textSecondary) { detail() }
            }
            trailing()
        }
    }
}

/** Kleines Menü am Eintrag: "Abteilung ändern …" (Alternative zum Ziehen). */
@Composable
private fun ItemMenu(name: String, onChangeDepartment: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Outlined.MoreVert,
                contentDescription = stringResource(R.string.shopping_item_options, name),
                modifier = Modifier.size(20.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.shopping_change_department)) },
                onClick = { open = false; onChangeDepartment() },
            )
        }
    }
}

@Composable
private fun ShortageRow(
    s: Shortage,
    units: Map<String, String>,
    onBought: () -> Unit,
    modifier: Modifier = Modifier,
    onChangeDepartment: (() -> Unit)? = null,
) {
    val changeLabel = stringResource(R.string.shopping_change_department)
    ShoppingCard(
        imagePath = s.product.imagePath,
        name = s.product.name,
        brand = s.product.brand,
        detail = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Outlined.TrendingDown, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text(
                    stringResource(R.string.shopping_shortage_detail, s.have, s.min, s.missing),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        onClick = onBought,
        modifier = modifier.semantics {
            if (onChangeDepartment != null) {
                customActions = listOf(CustomAccessibilityAction(changeLabel) { onChangeDepartment(); true })
            }
        },
        highlighted = true,
        onChangeDepartment = onChangeDepartment,
    ) {
        Column(horizontalAlignment = Alignment.End) {
            val pack = ShoppingLogic.packageLabel(s.product.packageSize, s.product.unitId?.let(units::get))
            AmountBadge(
                if (pack != null) stringResource(R.string.shopping_missing_package, s.missing, pack)
                else stringResource(R.string.shopping_missing, s.missing),
                color = VorratTheme.colors.warning,
            )
            IconButton(onClick = onBought) {
                Icon(Icons.Outlined.AddShoppingCart, contentDescription = stringResource(R.string.shopping_bought_to_stock))
            }
        }
    }
}

@Composable
private fun ItemRow(
    entry: ShoppingEntry,
    onCheck: () -> Unit,
    onEdit: () -> Unit,
    onBought: () -> Unit,
    modifier: Modifier = Modifier,
    onChangeDepartment: (() -> Unit)? = null,
) {
    SwipeableShoppingItem(
        onCheck = onCheck,
        onBought = onBought,
        onChangeDepartment = onChangeDepartment,
        modifier = modifier,
    ) {
        ShoppingCard(
            imagePath = entry.product?.imagePath,
            name = entry.item.name,
            brand = entry.product?.brand,
            detail = {
                // Packung · Rezepte in einer Zeile, darunter die Notiz.
                listOfNotNull(
                    ShoppingLogic.packageLabel(entry.item.packageSize, entry.item.unitSymbol),
                    ShoppingRecipes.label(entry.item.recipes),
                ).joinToString(" · ").takeIf { it.isNotEmpty() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
                entry.item.note?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
                if (entry.product == null) {
                    Text(stringResource(R.string.shopping_free_text), style = MaterialTheme.typography.bodySmall)
                }
            },
            onClick = onEdit,
            onChangeDepartment = onChangeDepartment,
        ) {
            // Menge/Packung nur hier rechts; Antippen der Karte öffnet das Bearbeiten.
            AmountBadge(shoppingAmountText(entry.item), modifier = Modifier.padding(end = 8.dp))
        }
    }
}

/**
 * Wischgesten wie in der Bestandsliste: nach links abhaken (mit Rückgängig), nach rechts "Gekauft"
 * (öffnet das Erfassen, die Karte springt zurück). Für Screenreader als Bedienungshilfen-Aktionen.
 */
@Composable
private fun SwipeableShoppingItem(
    onCheck: () -> Unit,
    onBought: () -> Unit,
    onChangeDepartment: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // Erst ab 40 % der Breite auslösen, damit kurze Wischer beim Scrollen nichts abhaken.
    val swipeState = de.frelina.vorrat.ui.components.rememberListSwipeState()
    val scope = rememberCoroutineScope()
    val checkLabel = stringResource(R.string.shopping_swipe_check)
    val boughtLabel = stringResource(R.string.shopping_bought)
    val changeLabel = stringResource(R.string.shopping_change_department)
    SwipeToDismissBox(
        state = swipeState,
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .semantics {
                customActions = listOfNotNull(
                    CustomAccessibilityAction(checkLabel) { onCheck(); true },
                    CustomAccessibilityAction(boughtLabel) { onBought(); true },
                    onChangeDepartment?.let { change -> CustomAccessibilityAction(changeLabel) { change(); true } },
                )
            },
        backgroundContent = { SwipeBackground(swipeState.dismissDirection, checkLabel, boughtLabel) },
        onDismiss = { direction ->
            when (direction) {
                SwipeToDismissBoxValue.EndToStart -> onCheck()
                SwipeToDismissBoxValue.StartToEnd -> {
                    onBought()
                    scope.launch { swipeState.reset() }
                }
                SwipeToDismissBoxValue.Settled -> Unit
            }
        },
    ) { content() }
}

@Composable
private fun SwipeBackground(direction: SwipeToDismissBoxValue, checkLabel: String, boughtLabel: String) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (direction) {
        SwipeToDismissBoxValue.EndToStart -> colors.primaryContainer to colors.onPrimaryContainer
        SwipeToDismissBoxValue.StartToEnd -> colors.secondaryContainer to colors.onSecondaryContainer
        SwipeToDismissBoxValue.Settled -> Color.Transparent to colors.onSurface
    }
    Row(
        Modifier.fillMaxSize().background(container).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (direction == SwipeToDismissBoxValue.EndToStart) Arrangement.End else Arrangement.Start,
    ) {
        when (direction) {
            SwipeToDismissBoxValue.StartToEnd -> {
                Icon(Icons.Outlined.AddShoppingCart, contentDescription = null, tint = content)
                Spacer(Modifier.width(12.dp))
                Text(boughtLabel, color = content, style = MaterialTheme.typography.titleSmall)
            }
            SwipeToDismissBoxValue.EndToStart -> {
                Text(checkLabel, color = content, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.width(12.dp))
                Icon(Icons.Outlined.Check, contentDescription = null, tint = content)
            }
            SwipeToDismissBoxValue.Settled -> Unit
        }
    }
}

/** Text der Plakette: "750 g", "2× 500 g", "3×" bzw. "1×". */
@Composable
internal fun shoppingAmountText(item: ShoppingItemEntity): String {
    val pack = ShoppingLogic.packageLabel(item.packageSize, item.unitSymbol)
    return when {
        pack == null -> stringResource(R.string.shopping_quantity, item.quantity)
        item.quantity > 1 -> stringResource(R.string.shopping_quantity_package, item.quantity, pack)
        else -> pack
    }
}

/** Übersicht oben: Ring mit Anteil "Nachkaufen" (gelb) und "Auf der Liste" (blau). */
@Composable
private fun ShoppingSummary(list: ShoppingList) {
    val total = list.count.coerceAtLeast(1)
    val colors = VorratTheme.colors
    GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), shape = MaterialTheme.shapes.large) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SegmentRing(
                segments = listOf(
                    list.shortages.size.toFloat() / total to colors.warning,
                    list.items.size.toFloat() / total to colors.info,
                ),
                size = 72.dp,
                stroke = 8.dp,
            ) {
                Text(list.count.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    pluralStringResource(R.plurals.shopping_summary_total, list.count, list.count),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                LegendDot(colors.warning, pluralStringResource(R.plurals.shopping_summary_short, list.shortages.size, list.shortages.size))
                LegendDot(colors.info, pluralStringResource(R.plurals.shopping_summary_items, list.items.size, list.items.size))
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = VorratTheme.colors.textSecondary,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** Unterkante der Listen: Platz, damit der Hinzufügen-Button den letzten Eintrag nicht verdeckt. */
private val LIST_BOTTOM_PADDING = 88.dp

/** Artikel hinzufügen: Name (Tastatur sofort offen), Anzahl, Packungsgröße und Einheit. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShoppingAddDialog(
    unitSymbols: List<String>,
    search: AddSearchState,
    onQueryChange: (String) -> Unit,
    onSearchOnline: () -> Unit,
    onDismiss: () -> Unit,
    onAdd: (name: String, quantity: Int, packageSize: BigDecimal?, unit: String?, pick: AddPick?) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var quantity by rememberSaveable { mutableIntStateOf(1) }
    var size by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf("") }
    var unitOpen by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf<AddPick?>(null) }
    val focus = remember { FocusRequester() }
    val sizeResult = Validation.optionalPositiveDecimal(size)
    val sizeError = (sizeResult as? Validated.Error)?.error
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.shopping_add_hint)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it.take(RemoteMapper.MAX_TEXT)
                        pick = null
                        onQueryChange(name)
                    },
                    label = { Text(stringResource(R.string.shopping_edit_name)) },
                    supportingText = { Text(stringResource(R.string.shopping_add_search_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { if (search.canSearchOnline) onSearchOnline() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                val chosen = pick
                if (chosen != null) {
                    PickedRow(chosen, onClear = { pick = null })
                } else {
                    AddSearchResults(
                        search = search,
                        onSearchOnline = onSearchOnline,
                        // Eigenes Produkt: Packungsgröße bleibt leer → beim Hinzufügen gilt die des Produkts.
                        onPickLocal = { p ->
                            pick = AddPick.Local(p)
                            name = p.name
                        },
                        // OFF-Treffer: Name und Packungsgröße vorbelegen (bleiben änderbar).
                        onPickRemote = { p ->
                            pick = AddPick.Remote(p)
                            name = p.name.orEmpty()
                            val packageSize = p.packageSize
                            if (packageSize != null && packageSize.signum() > 0) {
                                size = packageSize.stripTrailingZeros().toPlainString().replace('.', ',')
                                unit = p.unitSymbol.orEmpty()
                            }
                        },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.shopping_edit_quantity), modifier = Modifier.weight(1f))
                    IconButton(onClick = { quantity-- }, enabled = quantity > 1) {
                        Icon(Icons.Outlined.Remove, contentDescription = stringResource(R.string.action_decrease))
                    }
                    Text(
                        stringResource(R.string.shopping_quantity, quantity),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    IconButton(onClick = { quantity++ }, enabled = quantity < RemoteMapper.MAX_COUNT) {
                        Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.action_increase))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = size,
                        onValueChange = { size = it },
                        label = { Text(stringResource(R.string.shopping_edit_package)) },
                        isError = sizeError != null,
                        supportingText = sizeError?.let { e -> { Text(validationMessage(e)) } },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                        modifier = Modifier.weight(1f),
                    )
                    ExposedDropdownMenuBox(
                        expanded = unitOpen,
                        onExpandedChange = { unitOpen = it },
                        modifier = Modifier.weight(1f),
                    ) {
                        OutlinedTextField(
                            value = unit.ifEmpty { stringResource(R.string.shopping_edit_unit_none) },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.shopping_edit_unit)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitOpen) },
                            singleLine = true,
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        )
                        ExposedDropdownMenu(expanded = unitOpen, onDismissRequest = { unitOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.shopping_edit_unit_none)) },
                                onClick = { unit = ""; unitOpen = false },
                            )
                            (unitSymbols + listOfNotNull(unit.takeIf { it.isNotEmpty() })).distinct().forEach { symbol ->
                                DropdownMenuItem(text = { Text(symbol) }, onClick = { unit = symbol; unitOpen = false })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val value = if (sizeResult is Validated.Ok) sizeResult.value else null
                    onAdd(name, quantity, value, if (value != null) unit.ifEmpty { null } else null, pick)
                },
                enabled = sizeError == null && name.isNotBlank(),
            ) { Text(stringResource(R.string.shopping_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Vorschläge aus den eigenen Produkten und – auf Knopfdruck – Treffer von Open Food Facts. */
@Composable
private fun AddSearchResults(
    search: AddSearchState,
    onSearchOnline: () -> Unit,
    onPickLocal: (ProductEntity) -> Unit,
    onPickRemote: (LookedUpProduct) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (search.local.isNotEmpty()) {
            SearchSectionLabel(stringResource(R.string.shopping_add_own_products))
            search.local.forEach { p ->
                SuggestionRow(
                    title = p.name,
                    subtitle = suggestionSubtitle(p.brand, null, null),
                    image = { ProductImage(p.imagePath, 40.dp, name = p.name) },
                    onClick = { onPickLocal(p) },
                )
            }
        }
        when (val online = search.online) {
            OnlineSearch.Idle -> if (search.canSearchOnline) {
                TextButton(onClick = onSearchOnline) {
                    Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.shopping_add_search_off))
                }
            }
            OnlineSearch.Loading -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.shopping_add_search_loading), style = MaterialTheme.typography.bodyMedium)
            }
            is OnlineSearch.Done -> {
                SearchSectionLabel(stringResource(R.string.shopping_add_off_results))
                if (online.hits.isEmpty()) {
                    Text(
                        stringResource(R.string.shopping_add_off_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                online.hits.forEach { p ->
                    SuggestionRow(
                        title = p.name.orEmpty(),
                        subtitle = suggestionSubtitle(p.brand, p.packageSize, p.unitSymbol),
                        image = { RemoteImage(p.imageUrl, p.name) },
                        onClick = { onPickRemote(p) },
                    )
                }
            }
            is OnlineSearch.Failed -> Column {
                Text(
                    stringResource(searchErrorText(online.kind)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onSearchOnline) { Text(stringResource(R.string.shopping_add_search_retry)) }
            }
        }
    }
}

@Composable
private fun SearchSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun SuggestionRow(title: String, subtitle: String?, image: @Composable () -> Unit, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        image()
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Gewählter Treffer; über das X wird die Verknüpfung wieder aufgehoben (dann Freitext). */
@Composable
private fun PickedRow(pick: AddPick, onClear: () -> Unit) {
    val title: String
    val subtitle: String?
    when (pick) {
        is AddPick.Local -> {
            title = pick.product.name
            subtitle = pick.product.brand
        }
        is AddPick.Remote -> {
            title = pick.product.name.orEmpty()
            subtitle = listOfNotNull(pick.product.brand, stringResource(R.string.shopping_add_off_source))
                .joinToString(" · ")
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
    ) {
        when (pick) {
            is AddPick.Local -> ProductImage(pick.product.imagePath, 40.dp, name = pick.product.name)
            is AddPick.Remote -> RemoteImage(pick.product.imageUrl, pick.product.name)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.shopping_add_picked, title),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onClear) {
            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.shopping_add_unpick))
        }
    }
}

/** Kleines OFF-Vorschaubild (lokal gespeichert wird es erst beim Hinzufügen); ohne Bild das Kürzel. */
@Composable
private fun RemoteImage(url: String?, name: String?) {
    if (url == null) {
        ProductImage(null, 40.dp, name = name)
    } else {
        AsyncImage(
            model = url,
            contentDescription = stringResource(R.string.product_image),
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)),
        )
    }
}

private fun suggestionSubtitle(brand: String?, size: BigDecimal?, unit: String?): String? =
    listOfNotNull(brand?.takeIf { it.isNotBlank() }, ShoppingLogic.packageLabel(size, unit))
        .joinToString(" · ")
        .ifEmpty { null }

private fun searchErrorText(kind: LookupErrorKind): Int = when (kind) {
    LookupErrorKind.NO_NETWORK -> R.string.shopping_add_search_error_network
    LookupErrorKind.TIMEOUT -> R.string.shopping_add_search_error_timeout
    LookupErrorKind.RATE_LIMITED -> R.string.shopping_add_search_error_rate_limited
    LookupErrorKind.SERVER -> R.string.shopping_add_search_error_server
}

/**
 * Eintrag bearbeiten (Bottom-Sheet): Name, Anzahl, Packung, Abteilung, Rezepte, Notiz und Produkt-Verknüpfung eines
 * manuellen Listeneintrags. Gespeichert wird erst mit "Speichern"; das Papierkorb-Symbol löscht den Eintrag
 * (mit Rückgängig).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ShoppingItemSheet(
    entry: ShoppingEntry,
    unitSymbols: List<String>,
    departments: List<DepartmentEntity>,
    currentDepartmentId: String,
    search: AddSearchState,
    onQueryChange: (String) -> Unit,
    onSearchOnline: () -> Unit,
    onResetSearch: () -> Unit,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (update: ShoppingItemUpdate, pick: AddPick?, departmentId: String?) -> Unit,
) {
    val item = entry.item
    var name by rememberSaveable { mutableStateOf(item.name) }
    var quantity by rememberSaveable { mutableIntStateOf(item.quantity) }
    var size by rememberSaveable { mutableStateOf(item.packageSize?.toPlainString()?.replace('.', ',').orEmpty()) }
    var unit by rememberSaveable { mutableStateOf(item.unitSymbol.orEmpty()) }
    var department by rememberSaveable { mutableStateOf(currentDepartmentId) }
    var recipes by rememberSaveable { mutableStateOf(item.recipes.orEmpty()) }
    var note by rememberSaveable { mutableStateOf(item.note.orEmpty()) }
    var linkedId by rememberSaveable { mutableStateOf(item.productId) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var pick by remember { mutableStateOf<AddPick?>(null) }
    var unitOpen by remember { mutableStateOf(false) }
    var departmentOpen by remember { mutableStateOf(false) }
    val sizeResult = Validation.optionalPositiveDecimal(size)
    val sizeError = (sizeResult as? Validated.Error)?.error
    val unitOptions = (unitSymbols + listOfNotNull(item.unitSymbol)).distinct()
    val recipeTitles = ShoppingRecipes.split(recipes)
    val linked = pick != null || linkedId != null
    val departmentName = departments.firstOrNull { it.id == department }?.name
        ?: DepartmentDefaults.nameOf(department).orEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ProductImage(entry.product?.imagePath, 40.dp, name = name.ifBlank { item.name })
                Text(
                    stringResource(R.string.shopping_edit_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.shopping_edit_delete))
                }
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(RemoteMapper.MAX_TEXT) },
                label = { Text(stringResource(R.string.shopping_edit_name), maxLines = 1) },
                isError = name.isBlank(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.shopping_edit_quantity), modifier = Modifier.weight(1f))
                IconButton(onClick = { quantity-- }, enabled = quantity > 1) {
                    Icon(Icons.Outlined.Remove, contentDescription = stringResource(R.string.action_decrease))
                }
                Text(
                    stringResource(R.string.shopping_quantity, quantity),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(onClick = { quantity++ }, enabled = quantity < RemoteMapper.MAX_COUNT) {
                    Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.action_increase))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = size,
                    onValueChange = { size = it },
                    label = { Text(stringResource(R.string.shopping_edit_package), maxLines = 1) },
                    isError = sizeError != null,
                    supportingText = sizeError?.let { e -> { Text(validationMessage(e)) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1.1f),
                )
                ExposedDropdownMenuBox(
                    expanded = unitOpen,
                    onExpandedChange = { unitOpen = it },
                    modifier = Modifier.weight(1f),
                ) {
                    OutlinedTextField(
                        value = unit.ifEmpty { stringResource(R.string.shopping_edit_unit_none) },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.shopping_edit_unit), maxLines = 1) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitOpen) },
                        singleLine = true,
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(expanded = unitOpen, onDismissRequest = { unitOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.shopping_edit_unit_none)) },
                            onClick = { unit = ""; unitOpen = false },
                        )
                        unitOptions.forEach { symbol ->
                            DropdownMenuItem(text = { Text(symbol) }, onClick = { unit = symbol; unitOpen = false })
                        }
                    }
                }
            }

            de.frelina.vorrat.ui.components.SectionLabel(stringResource(R.string.shopping_edit_section_place), Modifier.padding(top = 4.dp))
            ExposedDropdownMenuBox(expanded = departmentOpen, onExpandedChange = { departmentOpen = it }) {
                OutlinedTextField(
                    value = departmentName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.shopping_edit_department), maxLines = 1) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = departmentOpen) },
                    singleLine = true,
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = departmentOpen, onDismissRequest = { departmentOpen = false }) {
                    departments.forEach { d ->
                        DropdownMenuItem(text = { Text(d.name) }, onClick = { department = d.id; departmentOpen = false })
                    }
                }
            }

            if (recipeTitles.isNotEmpty()) {
                de.frelina.vorrat.ui.components.SectionLabel(stringResource(R.string.shopping_edit_section_recipes), Modifier.padding(top = 4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    recipeTitles.forEach { title ->
                        val remove = stringResource(R.string.shopping_edit_recipe_remove, title)
                        InputChip(
                            selected = false,
                            onClick = { recipes = recipeTitles.filter { it != title }.joinToString("\n") },
                            label = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            trailingIcon = {
                                Icon(Icons.Outlined.Close, contentDescription = remove, modifier = Modifier.size(18.dp))
                            },
                        )
                    }
                }
            }

            de.frelina.vorrat.ui.components.SectionLabel(stringResource(R.string.shopping_edit_section_note), Modifier.padding(top = 4.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(RemoteMapper.MAX_PLAN_NOTE) },
                label = { Text(stringResource(R.string.shopping_edit_note), maxLines = 1) },
                placeholder = { Text(stringResource(R.string.shopping_edit_note_hint)) },
                minLines = 2,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            de.frelina.vorrat.ui.components.SectionLabel(stringResource(R.string.shopping_edit_section_product), Modifier.padding(top = 4.dp))
            if (linked) {
                val picked = pick
                val product = entry.product?.takeIf { it.id == linkedId }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (picked) {
                        is AddPick.Local -> ProductImage(picked.product.imagePath, 40.dp, name = picked.product.name)
                        is AddPick.Remote -> RemoteImage(picked.product.imageUrl, picked.product.name)
                        null -> ProductImage(product?.imagePath, 40.dp, name = product?.name ?: item.name)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            when (picked) {
                                is AddPick.Local -> picked.product.name
                                is AddPick.Remote -> picked.product.name.orEmpty()
                                null -> product?.name ?: item.name
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val brand = when (picked) {
                            is AddPick.Local -> picked.product.brand
                            is AddPick.Remote -> picked.product.brand
                            null -> product?.brand
                        }
                        if (!brand.isNullOrBlank()) {
                            Text(
                                brand,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    TextButton(onClick = { pick = null; linkedId = null }) {
                        Text(stringResource(R.string.shopping_edit_unlink))
                    }
                }
            } else if (searching) {
                var query by rememberSaveable { mutableStateOf(name) }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(RemoteMapper.MAX_TEXT); onQueryChange(query) },
                    label = { Text(stringResource(R.string.shopping_edit_link_search), maxLines = 1) },
                    supportingText = { Text(stringResource(R.string.shopping_add_search_hint)) },
                    trailingIcon = {
                        IconButton(onClick = { searching = false; onResetSearch() }) {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_close))
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { if (search.canSearchOnline) onSearchOnline() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                AddSearchResults(
                    search = search,
                    onSearchOnline = onSearchOnline,
                    onPickLocal = { p ->
                        pick = AddPick.Local(p)
                        linkedId = p.id
                        name = p.name
                        searching = false
                        onResetSearch()
                    },
                    // OFF-Treffer: Name und Packungsgröße vorbelegen (bleiben änderbar); angelegt wird das Produkt beim Speichern.
                    onPickRemote = { p ->
                        pick = AddPick.Remote(p)
                        linkedId = null
                        name = p.name.orEmpty().take(RemoteMapper.MAX_TEXT)
                        val packageSize = p.packageSize
                        if (packageSize != null && packageSize.signum() > 0) {
                            size = packageSize.stripTrailingZeros().toPlainString().replace('.', ',')
                            unit = p.unitSymbol.orEmpty()
                        }
                        searching = false
                        onResetSearch()
                    },
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.shopping_free_text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = { searching = true; onQueryChange(name) }) {
                        Text(stringResource(R.string.shopping_edit_link))
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Button(
                    onClick = {
                        val value = if (sizeResult is Validated.Ok) sizeResult.value else null
                        onSave(
                            ShoppingItemUpdate(
                                name = name,
                                quantity = quantity,
                                packageSize = value,
                                unitSymbol = unit.ifEmpty { null },
                                note = note,
                                recipes = recipeTitles,
                                productId = linkedId,
                            ),
                            pick,
                            department.takeIf { it != currentDepartmentId },
                        )
                    },
                    enabled = sizeError == null && name.isNotBlank(),
                ) { Text(stringResource(R.string.action_save)) }
            }
        }
    }
}
