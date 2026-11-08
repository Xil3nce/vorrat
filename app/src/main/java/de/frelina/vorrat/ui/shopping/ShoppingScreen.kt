package de.frelina.vorrat.ui.shopping

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.frelina.vorrat.R
import de.frelina.vorrat.data.repo.ShoppingEntry
import de.frelina.vorrat.domain.Shortage
import de.frelina.vorrat.ui.components.ProductImage

/**
 * Einkaufsliste: oben Eingabe, dann "Nachkaufen" (unter Mindestbestand, automatisch) und die manuell
 * hinzugefügten Einträge. "Gekauft" öffnet das Erfassen (ein Ort ist dabei nicht nötig).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingScreen(
    /** Erfassen nach dem Kauf: Produkt-ID (bekanntes Produkt) bzw. Listeneintrag. */
    onBought: (productId: String?, shoppingItemId: String?) -> Unit,
    viewModel: ShoppingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.shopping_title)) }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
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
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.list.shortages.isNotEmpty()) {
                        item(key = "h-short") { SectionLabel(R.string.shopping_section_shortage) }
                        items(state.list.shortages, key = { "s-" + it.product.id }) { s ->
                            ShortageRow(s, onBought = { onBought(s.product.id, null) }, modifier = Modifier.animateItem())
                        }
                    }
                    if (state.list.items.isNotEmpty()) {
                        item(key = "h-items") { SectionLabel(R.string.shopping_section_items) }
                        items(state.list.items, key = { "i-" + it.item.id }) { e ->
                            ItemRow(
                                entry = e,
                                onCheck = { viewModel.check(e.item.id) },
                                onQuantity = { viewModel.setQuantity(e.item.id, it) },
                                onBought = { onBought(e.item.productId, e.item.id) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
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
                label = { Text(stringResource(R.string.shopping_add_hint)) },
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
        IconButton(onClick = { submit() }, enabled = text.isNotBlank()) {
            Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.shopping_add))
        }
    }
}

@Composable
private fun ShortageRow(s: Shortage, onBought: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProductImage(s.product.imagePath, size = 48.dp)
            Column(Modifier.weight(1f)) {
                Text(s.product.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Outlined.TrendingDown, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text(
                        stringResource(R.string.shopping_shortage_detail, s.have, s.min, s.missing),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            FilledTonalButton(onClick = onBought) {
                Icon(Icons.Outlined.AddShoppingCart, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text(stringResource(R.string.shopping_bought))
            }
        }
    }
}

@Composable
private fun ItemRow(
    entry: ShoppingEntry,
    onCheck: () -> Unit,
    onQuantity: (Int) -> Unit,
    onBought: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = false, onCheckedChange = { onCheck() })
            if (entry.product != null) {
                ProductImage(entry.product.imagePath, size = 40.dp, modifier = Modifier.padding(end = 8.dp))
            }
            Text(
                entry.item.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onQuantity(entry.item.quantity - 1) }, enabled = entry.item.quantity > 1) {
                Icon(Icons.Outlined.Remove, contentDescription = stringResource(R.string.action_decrease))
            }
            Text(stringResource(R.string.shopping_quantity, entry.item.quantity), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { onQuantity(entry.item.quantity + 1) }) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.action_increase))
            }
            IconButton(onClick = onBought) {
                Icon(Icons.Outlined.AddShoppingCart, contentDescription = stringResource(R.string.shopping_bought_to_stock))
            }
        }
    }
}
