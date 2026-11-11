package de.frelina.vorrat.ui.shopping

import de.frelina.vorrat.ui.theme.VorratTheme
import androidx.compose.ui.text.font.FontWeight
import de.frelina.vorrat.ui.components.GlassIconButton
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import de.frelina.vorrat.ui.components.GlassCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.frelina.vorrat.R
import de.frelina.vorrat.data.db.DepartmentEntity
import de.frelina.vorrat.data.repo.DepartmentNameResult
import de.frelina.vorrat.data.sync.RemoteMapper
import de.frelina.vorrat.domain.DepartmentDefaults
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Abteilungen der Einkaufsliste: Standardreihenfolge per Drag & Drop (gilt im ganzen Haushalt), umbenennen,
 * hinzufügen, löschen und auf den Standard zurücksetzen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DepartmentsScreen(
    onBack: () -> Unit,
    viewModel: DepartmentsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var addDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<DepartmentEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<DepartmentEntity?>(null) }
    var resetDialog by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    // Während des Ziehens arbeitet die Liste auf einer lokalen Kopie; der gespeicherte Stand ersetzt sie danach.
    var local by remember { mutableStateOf<List<DepartmentEntity>?>(null) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(state.departments) { if (!dragging) local = null }
    val shown = local ?: state.departments
    val shownNow by rememberUpdatedState(shown)

    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val moved = shownNow.toMutableList()
        if (from.index in moved.indices && to.index in moved.indices) {
            moved.add(to.index, moved.removeAt(from.index))
            local = moved
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.departments_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    GlassIconButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back), onBack, Modifier.padding(start = 8.dp))
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.shopping_menu_more))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.departments_reset)) },
                                onClick = { menuOpen = false; resetDialog = true },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { addDialog = true },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.departments_add)) },
            )
        },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(Modifier.padding(padding).fillMaxSize()) {
            Text(
                stringResource(R.string.departments_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = VorratTheme.colors.textSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (shown.isEmpty()) {
                Text(
                    stringResource(R.string.departments_loading),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp),
                )
            }
            LazyColumn(
                state = lazyListState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(shown, key = { it.id }) { department ->
                    ReorderableItem(reorderState, key = department.id) { isDragging ->
                        val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "drag-elevation")
                        val index = shown.indexOf(department)
                        val upLabel = stringResource(R.string.departments_move_up)
                        val downLabel = stringResource(R.string.departments_move_down)
                        val dragLabel = stringResource(R.string.departments_drag, department.name)
                        GlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(elevation, MaterialTheme.shapes.medium)
                                .semantics {
                                    customActions = listOfNotNull(
                                        if (index > 0) CustomAccessibilityAction(upLabel) { viewModel.move(department.id, -1); true } else null,
                                        if (index < shown.lastIndex) {
                                            CustomAccessibilityAction(downLabel) { viewModel.move(department.id, 1); true }
                                        } else {
                                            null
                                        },
                                    )
                                },
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Outlined.DragHandle,
                                    contentDescription = dragLabel,
                                    tint = VorratTheme.colors.textSecondary,
                                    modifier = Modifier
                                        .draggableHandle(
                                            onDragStarted = { dragging = true },
                                            onDragStopped = {
                                                dragging = false
                                                local?.let { viewModel.saveOrder(it.map(DepartmentEntity::id)) }
                                            },
                                        )
                                        .size(48.dp)
                                        .padding(12.dp),
                                )
                                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                                    Text(department.name, style = MaterialTheme.typography.titleMedium)
                                    if (department.id == DepartmentDefaults.OTHER) {
                                        Text(
                                            stringResource(R.string.departments_other_hint),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = VorratTheme.colors.textSecondary,
                                        )
                                    }
                                }
                                IconButton(onClick = { renameTarget = department }) {
                                    Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.departments_rename))
                                }
                                if (department.id != DepartmentDefaults.OTHER) {
                                    IconButton(onClick = { deleteTarget = department }) {
                                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_delete))
                                    }
                                } else {
                                    Box(Modifier.size(48.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (addDialog) {
        NameDialog(
            title = R.string.departments_add,
            initial = "",
            onDismiss = { addDialog = false },
            onConfirm = { name, onResult -> viewModel.add(name, onResult) },
            onDone = { addDialog = false },
        )
    }
    renameTarget?.let { target ->
        NameDialog(
            title = R.string.departments_rename,
            initial = target.name,
            onDismiss = { renameTarget = null },
            onConfirm = { name, onResult -> viewModel.rename(target.id, name, onResult) },
            onDone = { renameTarget = null },
        )
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.departments_delete_title)) },
            text = { Text(stringResource(R.string.departments_delete_text, target.name)) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(target.id); deleteTarget = null }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (resetDialog) {
        AlertDialog(
            onDismissRequest = { resetDialog = false },
            title = { Text(stringResource(R.string.departments_reset)) },
            text = { Text(stringResource(R.string.departments_reset_text)) },
            confirmButton = {
                TextButton(onClick = { viewModel.resetToDefaults(); resetDialog = false }) {
                    Text(stringResource(R.string.departments_reset))
                }
            },
            dismissButton = { TextButton(onClick = { resetDialog = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** Name eingeben (Hinzufügen/Umbenennen); Fehler (doppelt/ungültig) erscheinen im Feld. */
@Composable
private fun NameDialog(
    title: Int,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String, (DepartmentNameResult) -> Unit) -> Unit,
    onDone: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial) }
    var error by remember { mutableStateOf<DepartmentNameResult?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(RemoteMapper.MAX_DEPARTMENT_NAME); error = null },
                singleLine = true,
                label = { Text(stringResource(R.string.departments_name)) },
                isError = error != null,
                supportingText = {
                    when (error) {
                        DepartmentNameResult.DUPLICATE -> Text(stringResource(R.string.departments_name_exists))
                        DepartmentNameResult.INVALID -> Text(stringResource(R.string.departments_name_invalid))
                        else -> Unit
                    }
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onConfirm(name) { result -> if (result == DepartmentNameResult.OK) onDone() else error = result }
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
