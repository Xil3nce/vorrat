package de.frelina.vorrat.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import de.frelina.vorrat.R
import de.frelina.vorrat.data.db.QuantityTypeEntity
import de.frelina.vorrat.data.db.UnitEntity
import de.frelina.vorrat.data.repo.CatalogRepository
import de.frelina.vorrat.data.repo.DeleteResult
import de.frelina.vorrat.data.repo.SaveResult
import de.frelina.vorrat.ui.navigation.CatalogRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Einheitliche Darstellung für Packungseinheiten und Menge-Arten. */
data class CatalogItem(val id: String, val symbol: String, val name: String)

data class CatalogInUse(val symbol: String, val usages: Int)

@HiltViewModel
class CatalogViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CatalogRepository,
) : ViewModel() {
    val isUnits = savedStateHandle.toRoute<CatalogRoute>().kind == CatalogRoute.UNITS

    private val source: Flow<List<CatalogItem>> = if (isUnits) {
        repository.observeUnits().map { list -> list.map { CatalogItem(it.id, it.symbol, it.name) } }
    } else {
        repository.observeQuantityTypes().map { list -> list.map { CatalogItem(it.id, it.symbol, it.name) } }
    }
    val items: StateFlow<List<CatalogItem>> = source.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _inUse = MutableStateFlow<CatalogInUse?>(null)
    val inUse: StateFlow<CatalogInUse?> = _inUse.asStateFlow()

    suspend fun save(id: String?, symbol: String, name: String): SaveResult =
        if (isUnits) repository.saveUnit(id, symbol, name) else repository.saveQuantityType(id, symbol, name)

    fun delete(item: CatalogItem) = viewModelScope.launch {
        val result = if (isUnits) {
            repository.deleteUnit(UnitEntity(item.id, item.symbol, item.name))
        } else {
            repository.deleteQuantityType(QuantityTypeEntity(item.id, item.symbol, item.name))
        }
        if (result is DeleteResult.InUse) _inUse.value = CatalogInUse(item.symbol, result.usages)
    }

    fun dismissInUse() {
        _inUse.value = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(onBack: () -> Unit, viewModel: CatalogViewModel = hiltViewModel()) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val inUse by viewModel.inUse.collectAsStateWithLifecycle()
    var editTarget by remember { mutableStateOf<CatalogItem?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<CatalogItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (viewModel.isUnits) R.string.catalog_units_title else R.string.catalog_types_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.catalog_add)) },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
            items(items, key = { it.id }) { item ->
                ListItem(
                    headlineContent = { Text(item.symbol, style = MaterialTheme.typography.titleMedium) },
                    supportingContent = { Text(item.name) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { editTarget = item }) {
                                Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.action_edit))
                            }
                            IconButton(onClick = { deleteTarget = item }) {
                                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_delete))
                            }
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }

    if (creating || editTarget != null) {
        CatalogEditDialog(
            initial = editTarget,
            onDismiss = { creating = false; editTarget = null },
            onSave = { symbol, name -> viewModel.save(editTarget?.id, symbol, name) },
        )
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.catalog_delete_title, target.symbol)) },
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
            text = {
                Text(
                    pluralStringResource(
                        if (viewModel.isUnits) R.plurals.catalog_unit_in_use else R.plurals.catalog_type_in_use,
                        info.usages,
                        info.usages,
                        info.symbol,
                    ),
                )
            },
            confirmButton = { TextButton(onClick = viewModel::dismissInUse) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}

@Composable
private fun CatalogEditDialog(
    initial: CatalogItem?,
    onDismiss: () -> Unit,
    onSave: suspend (String, String) -> SaveResult,
) {
    var symbol by remember { mutableStateOf(initial?.symbol.orEmpty()) }
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var error by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.catalog_add else R.string.catalog_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = symbol,
                    onValueChange = { symbol = it; error = null },
                    label = { Text(stringResource(R.string.catalog_symbol)) },
                    singleLine = true,
                    isError = error != null,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text(stringResource(R.string.catalog_name)) },
                    singleLine = true,
                    isError = error == R.string.error_required,
                )
                error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    when (onSave(symbol, name)) {
                        is SaveResult.Saved -> onDismiss()
                        SaveResult.Blank -> error = R.string.error_required
                        SaveResult.Duplicate -> error = R.string.error_duplicate_symbol
                    }
                }
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
