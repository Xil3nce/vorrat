package de.frelina.vorrat.ui.locations

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import de.frelina.vorrat.ui.components.GlassCard
import de.frelina.vorrat.ui.components.pastelFor
import de.frelina.vorrat.ui.theme.VorratTheme
import androidx.compose.ui.text.font.FontWeight
import de.frelina.vorrat.ui.components.GlassIconButton
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.frelina.vorrat.R
import de.frelina.vorrat.data.db.LocationEntity
import de.frelina.vorrat.data.repo.CatalogRepository
import de.frelina.vorrat.data.repo.DeleteResult
import de.frelina.vorrat.data.repo.SaveResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InUseInfo(val name: String, val usages: Int)

@HiltViewModel
class LocationsViewModel @Inject constructor(
    private val repository: CatalogRepository,
) : ViewModel() {
    val locations: StateFlow<List<LocationEntity>?> =
        repository.observeLocations().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _inUse = MutableStateFlow<InUseInfo?>(null)
    val inUse: StateFlow<InUseInfo?> = _inUse.asStateFlow()

    suspend fun add(name: String, levels: Int?, shelves: Int?): SaveResult = repository.addLocation(name, levels, shelves)

    suspend fun update(location: LocationEntity, name: String, levels: Int?, shelves: Int?): SaveResult =
        repository.updateLocation(location, name, levels, shelves)

    fun delete(location: LocationEntity) = viewModelScope.launch {
        val result = repository.deleteLocation(location)
        if (result is DeleteResult.InUse) _inUse.value = InUseInfo(location.name, result.usages)
    }

    fun dismissInUse() {
        _inUse.value = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationsScreen(onBack: () -> Unit, viewModel: LocationsViewModel = hiltViewModel()) {
    val locations by viewModel.locations.collectAsStateWithLifecycle()
    val inUse by viewModel.inUse.collectAsStateWithLifecycle()
    var addDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<LocationEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<LocationEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.locations_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    GlassIconButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back), onBack, Modifier.padding(start = 8.dp))
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { addDialog = true },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.locations_add)) },
            )
        },
    ) { padding ->
        val list = locations
        if (list != null && list.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.locations_empty), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                Modifier.padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(list.orEmpty(), key = { it.id }) { loc ->
                    GlassCard(Modifier.fillMaxWidth()) {
                        ListItem(
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent,
                                supportingColor = VorratTheme.colors.textSecondary,
                            ),
                            headlineContent = { Text(loc.name, fontWeight = FontWeight.SemiBold) },
                            supportingContent = slotSummary(loc)?.let { summary -> { Text(summary) } },
                            leadingContent = {
                                Box(
                                    Modifier.size(44.dp).clip(RoundedCornerShape(16.dp)).background(pastelFor(loc.name)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Outlined.Place, contentDescription = null, tint = VorratTheme.colors.onAccent)
                                }
                            },
                            trailingContent = {
                                Row {
                                    IconButton(onClick = { renameTarget = loc }) {
                                        Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.action_edit))
                                    }
                                    IconButton(onClick = { deleteTarget = loc }) {
                                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_delete))
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (addDialog) {
        LocationDialog(
            title = stringResource(R.string.locations_new),
            initial = null,
            onDismiss = { addDialog = false },
            onConfirm = viewModel::add,
        )
    }
    renameTarget?.let { target ->
        LocationDialog(
            title = stringResource(R.string.locations_edit),
            initial = target,
            onDismiss = { renameTarget = null },
            onConfirm = { name, levels, shelves -> viewModel.update(target, name, levels, shelves) },
        )
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.locations_delete_title)) },
            text = { Text(stringResource(R.string.locations_delete_text, target.name)) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(target); deleteTarget = null }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    inUse?.let { info ->
        AlertDialog(
            onDismissRequest = viewModel::dismissInUse,
            title = { Text(stringResource(R.string.locations_delete_title)) },
            text = { Text(pluralStringResource(R.plurals.locations_in_use, info.usages, info.usages, info.name)) },
            confirmButton = { TextButton(onClick = viewModel::dismissInUse) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}

/** "3 Ebenen · 4 Fächer" (nur festgelegte Angaben); null, wenn nichts festgelegt ist. */
@Composable
private fun slotSummary(loc: LocationEntity): String? {
    val parts = listOfNotNull(
        loc.levels?.let { pluralStringResource(R.plurals.locations_levels_count, it, it) },
        loc.shelves?.let { pluralStringResource(R.plurals.locations_shelves_count, it, it) },
    )
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/** Leer = nicht festgelegt (null); sonst 1 … MAX_SLOTS, andernfalls ungültig (failure). */
private fun parseSlotCount(text: String): Result<Int?> {
    val t = text.trim()
    if (t.isEmpty()) return Result.success(null)
    val n = t.toIntOrNull()?.takeIf { it in 1..LocationEntity.MAX_SLOTS }
    return if (n != null) Result.success(n) else Result.failure(IllegalArgumentException(t))
}

/**
 * Ort anlegen/bearbeiten: Name sowie optional die Anzahl der Ebenen und Fächer.
 * [onConfirm] liefert das Speicherergebnis, damit Fehler (leer/doppelt) im Dialog erscheinen.
 */
@Composable
private fun LocationDialog(
    title: String,
    initial: LocationEntity?,
    onDismiss: () -> Unit,
    onConfirm: suspend (name: String, levels: Int?, shelves: Int?) -> SaveResult,
) {
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var levels by rememberSaveable { mutableStateOf(initial?.levels?.toString().orEmpty()) }
    var shelves by rememberSaveable { mutableStateOf(initial?.shelves?.toString().orEmpty()) }
    var nameError by remember { mutableStateOf<Int?>(null) }
    var levelsError by remember { mutableStateOf(false) }
    var shelvesError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val slotError = levelsError || shelvesError

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = null },
                    label = { Text(stringResource(R.string.locations_name)) },
                    singleLine = true,
                    isError = nameError != null,
                    supportingText = nameError?.let { e -> { Text(stringResource(e), color = MaterialTheme.colorScheme.error) } },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SlotCountField(
                        value = levels,
                        onValueChange = { levels = it; levelsError = false },
                        label = stringResource(R.string.locations_levels),
                        isError = levelsError,
                        modifier = Modifier.weight(1f),
                    )
                    SlotCountField(
                        value = shelves,
                        onValueChange = { shelves = it; shelvesError = false },
                        label = stringResource(R.string.locations_shelves),
                        isError = shelvesError,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    stringResource(if (slotError) R.string.locations_slots_error else R.string.locations_slots_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (slotError) MaterialTheme.colorScheme.error else VorratTheme.colors.textSecondary,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val l = parseSlotCount(levels)
                val sh = parseSlotCount(shelves)
                levelsError = l.isFailure
                shelvesError = sh.isFailure
                if (l.isSuccess && sh.isSuccess) {
                    scope.launch {
                        when (onConfirm(name, l.getOrNull(), sh.getOrNull())) {
                            is SaveResult.Saved -> onDismiss()
                            SaveResult.Blank -> nameError = R.string.error_required
                            SaveResult.Duplicate -> nameError = R.string.error_duplicate_name
                        }
                    }
                }
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun SlotCountField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
