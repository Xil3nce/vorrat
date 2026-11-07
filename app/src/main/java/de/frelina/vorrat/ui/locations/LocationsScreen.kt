package de.frelina.vorrat.ui.locations

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Scaffold
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.frelina.vorrat.R
import de.frelina.vorrat.data.db.LocationEntity
import de.frelina.vorrat.data.repo.CatalogRepository
import de.frelina.vorrat.data.repo.DeleteResult
import de.frelina.vorrat.data.repo.SaveResult
import de.frelina.vorrat.ui.product.NameDialog
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

    suspend fun add(name: String): SaveResult = repository.addLocation(name)

    suspend fun rename(location: LocationEntity, name: String): SaveResult = repository.renameLocation(location, name)

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
fun LocationsScreen(viewModel: LocationsViewModel = hiltViewModel()) {
    val locations by viewModel.locations.collectAsStateWithLifecycle()
    val inUse by viewModel.inUse.collectAsStateWithLifecycle()
    var addDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<LocationEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<LocationEntity?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.locations_title)) }) },
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
            LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
                items(list.orEmpty(), key = { it.id }) { loc ->
                    ListItem(
                        headlineContent = { Text(loc.name) },
                        leadingContent = { Icon(Icons.Outlined.Place, contentDescription = null) },
                        trailingContent = {
                            androidx.compose.foundation.layout.Row {
                                IconButton(onClick = { renameTarget = loc }) {
                                    Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.action_rename))
                                }
                                IconButton(onClick = { deleteTarget = loc }) {
                                    Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_delete))
                                }
                            }
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (addDialog) {
        NameDialog(
            title = stringResource(R.string.locations_new),
            label = stringResource(R.string.locations_name),
            initial = "",
            onDismiss = { addDialog = false },
            onConfirm = viewModel::add,
        )
    }
    renameTarget?.let { target ->
        NameDialog(
            title = stringResource(R.string.locations_rename),
            label = stringResource(R.string.locations_name),
            initial = target.name,
            onDismiss = { renameTarget = null },
            onConfirm = { viewModel.rename(target, it) },
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
