package de.frelina.vorrat.ui.locations

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import de.frelina.vorrat.ui.components.GlassCard
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
import androidx.compose.material.icons.outlined.TableRows
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import de.frelina.vorrat.data.repo.StockRepository
import de.frelina.vorrat.domain.StockQueries
import kotlinx.coroutines.flow.combine
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
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

/** Ort mit Anzahl Bestandseinträge; [location] null = Einträge ohne Ort. */
data class LocationStat(val location: LocationEntity?, val count: Int)

data class LocationsUiState(
    val loading: Boolean = true,
    val stats: List<LocationStat> = emptyList(),
    val total: Int = 0,
)

@HiltViewModel
class LocationsViewModel @Inject constructor(
    private val repository: CatalogRepository,
    stockRepository: StockRepository,
) : ViewModel() {
    val state: StateFlow<LocationsUiState> = combine(
        repository.observeLocations(),
        stockRepository.observeItems(),
    ) { locations, items ->
        val counts = items.groupingBy { it.locationId }.eachCount()
        val unassigned = counts[null] ?: 0
        LocationsUiState(
            loading = false,
            stats = locations.map { LocationStat(it, counts[it.id] ?: 0) } +
                listOfNotNull(LocationStat(null, unassigned).takeIf { unassigned > 0 }),
            total = items.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocationsUiState())

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

/**
 * Übersicht der Orte (Hauptbereich "Übersicht"; über die Einstellungen auch als Unterseite mit [onBack]):
 * Verteilung des Bestands, je Ort Anzahl Einträge und Ebenen/Fächer. Antippen öffnet den Bestand gefiltert
 * nach diesem Ort ([onOpenLocation]; [StockQueries.NO_LOCATION] für "Ohne Ort").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationsScreen(
    onBack: (() -> Unit)?,
    onOpenLocation: (String) -> Unit,
    viewModel: LocationsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val inUse by viewModel.inUse.collectAsStateWithLifecycle()
    var addDialog by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<LocationEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<LocationEntity?>(null) }
    val pastels = VorratTheme.colors.pastels
    val unassignedColor = VorratTheme.colors.textSecondary
    // Farbe je Ort nach Position in der Liste: benachbarte Orte bekommen nie dieselbe Farbe.
    val colorFor: (LocationStat) -> Color = { stat ->
        if (stat.location == null) unassignedColor else pastels[Math.floorMod(state.stats.indexOf(stat), pastels.size)]
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(if (onBack == null) R.string.overview_title else R.string.locations_title),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        GlassIconButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back), onBack, Modifier.padding(start = 8.dp))
                    }
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
        if (!state.loading && state.stats.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.locations_empty), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                Modifier.padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.total > 0) {
                    item(key = "distribution") { DistributionCard(state, colorFor) }
                }
                items(state.stats, key = { it.location?.id ?: StockQueries.NO_LOCATION }) { stat ->
                    LocationCard(
                        stat = stat,
                        color = colorFor(stat),
                        onClick = { onOpenLocation(stat.location?.id ?: StockQueries.NO_LOCATION) },
                        onEdit = stat.location?.let { loc -> { editTarget = loc } },
                    )
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
    editTarget?.let { target ->
        LocationDialog(
            title = stringResource(R.string.locations_edit),
            initial = target,
            onDismiss = { editTarget = null },
            onConfirm = { name, levels, shelves -> viewModel.update(target, name, levels, shelves) },
            onDelete = { editTarget = null; deleteTarget = target },
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

/** "Verteilung im Bestand": Gesamtzahl und ein Balken mit einem Abschnitt je Ort. */
@Composable
private fun DistributionCard(state: LocationsUiState, colorFor: (LocationStat) -> Color) {
    GlassCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.overview_distribution),
                    style = MaterialTheme.typography.bodyMedium,
                    color = VorratTheme.colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    pluralStringResource(R.plurals.overview_items, state.total, state.total),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            Row(
                Modifier.padding(top = 12.dp).fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                state.stats.filter { it.count > 0 }.forEach { stat ->
                    Box(Modifier.weight(stat.count.toFloat()).fillMaxHeight().background(colorFor(stat)))
                }
            }
        }
    }
}

@Composable
private fun LocationCard(stat: LocationStat, color: Color, onClick: () -> Unit, onEdit: (() -> Unit)?) {
    val loc = stat.location
    val name = loc?.name ?: stringResource(R.string.location_none)
    GlassCard(Modifier.fillMaxWidth(), onClick = onClick, shape = MaterialTheme.shapes.large) {
        Row(
            Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(50.dp).clip(RoundedCornerShape(16.dp)).background(color),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (loc == null) Icons.Outlined.Place else Icons.Outlined.TableRows,
                    contentDescription = null,
                    tint = VorratTheme.colors.onAccent,
                )
            }
            Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                    )
                    Text(
                        pluralStringResource(R.plurals.overview_items, stat.count, stat.count),
                        style = MaterialTheme.typography.bodyMedium,
                        color = VorratTheme.colors.textTertiary,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    slotChips(loc).forEach { SlotChip(it) }
                }
            }
            if (onEdit != null) {
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = stringResource(R.string.overview_edit, name),
                        modifier = Modifier.size(20.dp),
                    )
                }
            } else {
                Spacer(Modifier.width(48.dp))
            }
        }
    }
}

@Composable
private fun SlotChip(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = VorratTheme.colors.textTertiary,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(VorratTheme.colors.glassStrong)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Plaketten "3 Ebenen", "4 Fächer" bzw. "ohne Ebenen und Fächer"; "Ohne Ort" mit Hinweis. */
@Composable
private fun slotChips(loc: LocationEntity?): List<String> {
    if (loc == null) return listOf(stringResource(R.string.overview_unassigned_hint))
    val parts = listOfNotNull(
        loc.levels?.let { pluralStringResource(R.plurals.locations_levels_count, it, it) },
        loc.shelves?.let { pluralStringResource(R.plurals.locations_shelves_count, it, it) },
    )
    return parts.ifEmpty { listOf(stringResource(R.string.overview_no_slots)) }
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
    onDelete: (() -> Unit)? = null,
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
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text(
                            stringResource(R.string.locations_delete_action),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
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
