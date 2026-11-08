package de.frelina.vorrat.ui.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import de.frelina.vorrat.R
import de.frelina.vorrat.data.db.StockItem
import de.frelina.vorrat.domain.ConsumeLogic
import de.frelina.vorrat.ui.components.expiryText
import de.frelina.vorrat.ui.components.locationText
import java.time.LocalDate

/**
 * Entnehmen: Eintrag wählen (frühestes Ablaufdatum vorausgewählt) und Anzahl per −/+ oder Schnellwahl festlegen.
 */
@Composable
fun ConsumeDialog(
    entries: List<StockItem>,
    leadDays: Int,
    onDismiss: () -> Unit,
    onConsume: (entryId: String, amount: Int) -> Unit,
) {
    val sorted = remember(entries) { ConsumeLogic.order(entries) }
    var selectedId by remember(sorted) { mutableStateOf(sorted.first().entryId) }
    val selected = sorted.firstOrNull { it.entryId == selectedId } ?: sorted.first()
    var amount by remember(selectedId) { mutableIntStateOf(1) }
    val max = selected.quantity
    val today = LocalDate.now()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.consume_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(selected.productName, style = MaterialTheme.typography.titleMedium)
                if (sorted.size > 1) {
                    Text(stringResource(R.string.consume_choose_entry), style = MaterialTheme.typography.bodySmall)
                    sorted.forEach { item ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(selected = item.entryId == selectedId, role = Role.RadioButton) {
                                    selectedId = item.entryId
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = item.entryId == selectedId, onClick = null)
                            Spacer(Modifier.size(8.dp))
                            Column {
                                Text(
                                    locationText(item) + " – " +
                                        stringResource(R.string.quantity_label, item.quantity, item.quantityTypeSymbol),
                                )
                                Text(expiryText(item, today, leadDays), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    HorizontalDivider()
                } else {
                    Text(
                        locationText(selected) + " – " +
                            stringResource(R.string.quantity_label, selected.quantity, selected.quantityTypeSymbol),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                // Anzahl: große −/+ Schaltflächen
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    FilledTonalIconButton(
                        onClick = { amount = (amount - 1).coerceAtLeast(1) },
                        enabled = amount > 1,
                        modifier = Modifier.size(56.dp),
                    ) { Icon(Icons.Outlined.Remove, contentDescription = stringResource(R.string.action_decrease)) }
                    Text(
                        stringResource(R.string.quantity_label, amount, selected.quantityTypeSymbol),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    FilledTonalIconButton(
                        onClick = { amount = (amount + 1).coerceAtMost(max) },
                        enabled = amount < max,
                        modifier = Modifier.size(56.dp),
                    ) { Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.action_increase)) }
                }
                // Schnellwahl
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ConsumeLogic.quickAmounts(max).forEach { n ->
                        FilterChip(
                            selected = amount == n,
                            onClick = { amount = n },
                            label = {
                                Text(if (n == max && max > 1) stringResource(R.string.consume_all, max) else n.toString())
                            },
                        )
                    }
                }
                if (amount == max) {
                    Text(
                        stringResource(R.string.consume_removes_entry),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConsume(selected.entryId, amount) }) { Text(stringResource(R.string.consume_action)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
