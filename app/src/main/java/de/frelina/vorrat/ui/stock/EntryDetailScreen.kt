package de.frelina.vorrat.ui.stock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.frelina.vorrat.R
import de.frelina.vorrat.domain.CategoryResolver
import de.frelina.vorrat.ui.components.ProductImage
import de.frelina.vorrat.ui.components.StockItemCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailScreen(
    onBack: () -> Unit,
    onEditEntry: (Long) -> Unit,
    onEditProduct: (Long) -> Unit,
    viewModel: EntryDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmLast by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.closed.collect { onBack() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        val item = state.item
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            item == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.detail_not_found))
            }
            else -> Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ProductImage(item.imagePath, size = 112.dp)
                    Column(Modifier.weight(1f)) {
                        Text(item.productName, style = MaterialTheme.typography.headlineSmall)
                        item.brand?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                        val product = state.product
                        product?.barcode?.let { Text(stringResource(R.string.detail_barcode, it), style = MaterialTheme.typography.bodySmall) }
                        CategoryResolver.displayName(product?.category)?.let {
                            Text(stringResource(R.string.detail_category, it), style = MaterialTheme.typography.bodySmall)
                        }
                        if (product?.packageSize != null) {
                            val unit = state.units.firstOrNull { it.id == product.unitId }?.symbol.orEmpty()
                            Text(
                                stringResource(R.string.detail_package, product.packageSize.toPlainString(), unit),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                StockItemCard(item, state.today, state.leadDays, onClick = null)
                item.note?.takeIf { it.isNotBlank() }?.let {
                    Text(stringResource(R.string.detail_note, it), style = MaterialTheme.typography.bodyMedium)
                }

                // Anzahl +/- (große Touch-Ziele)
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        FilledTonalIconButton(
                            onClick = { if (item.quantity <= 1) confirmLast = true else viewModel.decrease() },
                            modifier = Modifier.size(64.dp),
                        ) { Icon(Icons.Outlined.Remove, contentDescription = stringResource(R.string.action_decrease)) }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.detail_quantity), style = MaterialTheme.typography.labelLarge)
                            Text(
                                stringResource(R.string.quantity_label, item.quantity, item.quantityTypeSymbol),
                                style = MaterialTheme.typography.headlineMedium,
                            )
                        }
                        FilledTonalIconButton(onClick = viewModel::increase, modifier = Modifier.size(64.dp)) {
                            Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.action_increase))
                        }
                    }
                }

                OutlinedButton(onClick = { onEditEntry(item.entryId) }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Icon(Icons.Outlined.Edit, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.detail_edit_entry))
                }
                OutlinedButton(onClick = { onEditProduct(item.productId) }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Icon(Icons.Outlined.Edit, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.detail_edit_product))
                }
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Icon(Icons.Outlined.Delete, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.detail_delete_entry))
                }
            }
        }
    }

    if (confirmLast) {
        AlertDialog(
            onDismissRequest = { confirmLast = false },
            title = { Text(stringResource(R.string.detail_last_confirm_title)) },
            text = { Text(stringResource(R.string.detail_last_confirm_text)) },
            confirmButton = {
                TextButton(onClick = { confirmLast = false; viewModel.decrease() }) {
                    Text(stringResource(R.string.detail_remove))
                }
            },
            dismissButton = { TextButton(onClick = { confirmLast = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.detail_delete_confirm_title)) },
            text = { Text(stringResource(R.string.detail_delete_confirm_text)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete() }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
