package de.frelina.vorrat.ui.shopping

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
 * hinzugefügten Einträge. Karten im Stil der Bestandsliste mit Produktbild. Antippen bzw. nach rechts wischen
 * ("Gekauft") öffnet das Erfassen (ein Ort ist dabei nicht nötig), nach links wischen hakt ab. Gruppiert lassen
 * sich Einträge per langem Drücken in eine andere Abteilung ziehen und Abteilungen am Griff verschieben.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingScreen(
    /** Erfassen nach dem Kauf: Produkt-ID (bekanntes Produkt) bzw. Listeneintrag. */
    onBought: (productId: String?, shoppingItemId: String?) -> Unit,
    onOpenDepartments: () -> Unit,
    onOpenMealPlan: () -> Unit,
    viewModel: ShoppingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    var changeTarget by remember { mutableStateOf<Pair<ShoppingPayload, String>?>(null) }
    var editTarget by remember { mutableStateOf<ShoppingEntry?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.shopping_title)) },
                actions = {
                    ShoppingMenu(
                        grouped = state.grouped,
                        onToggleGrouped = { viewModel.setGrouped(!state.grouped) },
                        onOpenDepartments = onOpenDepartments,
                        onOpenMealPlan = onOpenMealPlan,
                    )
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (!state.loading && !state.list.isEmpty) ShoppingSummary(state.list)
            AddRow(suggestions = suggestions, onAdd = viewModel::add)
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
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.list.shortages.isNotEmpty()) {
                        item(key = "h-short") { SectionLabel(R.string.shopping_section_shortage) }
                        items(state.list.shortages, key = { "s-" + it.product.id }) { s ->
                            ShortageRow(
                                s,
                                units = state.units,
                                onBought = { onBought(s.product.id, null) },
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
                                onBought = { onBought(e.item.productId, e.item.id) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }

    editTarget?.let { entry ->
        ShoppingItemDialog(
            item = entry.item,
            unitSymbols = state.units.values.distinct().sorted(),
            onDismiss = { editTarget = null },
            onSave = { quantity, size, unit ->
                editTarget = null
                viewModel.updateDetails(entry.item.id, quantity, size, unit)
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
    onBought: (productId: String?, shoppingItemId: String?) -> Unit,
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
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
                            onBought = { onBought(payload.shortage.product.id, null) },
                            modifier = dragModifier,
                            onChangeDepartment = change,
                        )
                        is ShoppingPayload.Item -> ItemRow(
                            entry = payload.entry,
                            onCheck = { onCheck(payload.entry.item.id) },
                            onEdit = { onEdit(payload.entry) },
                            onBought = { onBought(payload.entry.item.productId, payload.entry.item.id) },
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

/** Freitext-Eingabe mit Vorschlägen aus den bekannten Produkten. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRow(suggestions: List<String>, onAdd: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val filtered = remember(text, suggestions) {
        val q = text.trim()
        if (q.length < 2) emptyList() else suggestions.filter { it.contains(q, ignoreCase = true) }.take(6)
    }
    fun submit(value: String = text) {
        if (value.isNotBlank()) onAdd(value)
        text = ""
        expanded = false
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded && filtered.isNotEmpty(),
            onExpandedChange = { expanded = it },
            modifier = Modifier.weight(1f),
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; expanded = true },
                placeholder = { Text(stringResource(R.string.shopping_add_hint)) },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = VorratTheme.colors.glass,
                    focusedContainerColor = VorratTheme.colors.glassStrong,
                    unfocusedBorderColor = VorratTheme.colors.glassBorder,
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
            )
            ExposedDropdownMenu(
                expanded = expanded && filtered.isNotEmpty(),
                onDismissRequest = { expanded = false },
            ) {
                filtered.forEach { s -> DropdownMenuItem(text = { Text(s) }, onClick = { submit(s) }) }
            }
        }
        FilledIconButton(
            onClick = { submit() },
            enabled = text.isNotBlank(),
            modifier = Modifier.padding(start = 8.dp).size(52.dp),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.shopping_add))
        }
    }
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
    onEdit: (() -> Unit)? = null,
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
                    if (onChangeDepartment != null || onEdit != null) ItemMenu(name, onChangeDepartment, onEdit)
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
private fun ItemMenu(name: String, onChangeDepartment: (() -> Unit)?, onEdit: (() -> Unit)?) {
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
            if (onEdit != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.shopping_edit_action)) },
                    onClick = { open = false; onEdit() },
                )
            }
            if (onChangeDepartment != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.shopping_change_department)) },
                    onClick = { open = false; onChangeDepartment() },
                )
            }
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
                entry.item.note?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
                if (entry.product == null) {
                    Text(stringResource(R.string.shopping_free_text), style = MaterialTheme.typography.bodySmall)
                }
            },
            onClick = onBought,
            onChangeDepartment = onChangeDepartment,
            onEdit = onEdit,
        ) {
            // Menge/Packung nur hier rechts; Antippen öffnet "Menge und Packung".
            val editLabel = stringResource(R.string.shopping_edit_action)
            AmountBadge(
                shoppingAmountText(entry.item),
                modifier = Modifier
                    .padding(end = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClickLabel = editLabel, role = Role.Button, onClick = onEdit),
            )
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
    val swipeState = rememberSwipeToDismissBoxState(positionalThreshold = { totalDistance -> totalDistance * 0.4f })
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
private fun shoppingAmountText(item: ShoppingItemEntity): String {
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

/** Anzahl, Packungsgröße und Einheit eines Listeneintrags pflegen (wie im Bestand-Formular). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShoppingItemDialog(
    item: ShoppingItemEntity,
    unitSymbols: List<String>,
    onDismiss: () -> Unit,
    onSave: (quantity: Int, packageSize: BigDecimal?, unit: String?) -> Unit,
) {
    var quantity by rememberSaveable { mutableIntStateOf(item.quantity) }
    var size by rememberSaveable { mutableStateOf(item.packageSize?.toPlainString()?.replace('.', ',').orEmpty()) }
    var unit by rememberSaveable { mutableStateOf(item.unitSymbol.orEmpty()) }
    var unitOpen by remember { mutableStateOf(false) }
    val sizeResult = Validation.optionalPositiveDecimal(size)
    val sizeError = (sizeResult as? Validated.Error)?.error
    val options = (unitSymbols + listOfNotNull(item.unitSymbol)).distinct()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.shopping_edit_title, item.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                            options.forEach { symbol ->
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
                    onSave(quantity, value, unit.ifEmpty { null })
                },
                enabled = sizeError == null,
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
