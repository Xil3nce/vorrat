package de.frelina.vorrat.ui.stock

import androidx.compose.ui.text.font.FontWeight
import de.frelina.vorrat.ui.theme.VorratTheme
import de.frelina.vorrat.ui.components.GlassIconButton
import java.time.LocalDate
import de.frelina.vorrat.ui.components.NumberField
import de.frelina.vorrat.ui.components.DateField
import de.frelina.vorrat.domain.ValidationError
import de.frelina.vorrat.domain.Validation
import de.frelina.vorrat.domain.Validated
import de.frelina.vorrat.domain.OpenedLogic
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Nfc
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
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import de.frelina.vorrat.ui.components.GlassCard
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
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
import de.frelina.vorrat.domain.Nutrition
import de.frelina.vorrat.domain.NutritionParser
import de.frelina.vorrat.ui.components.ProductImage
import de.frelina.vorrat.ui.components.StockItemCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailScreen(
    onBack: () -> Unit,
    /** Anderen Eintrag anstelle dieses zeigen (nach dem Abspalten einer geöffneten Packung). */
    onReplace: (String) -> Unit,
    onEditEntry: (String) -> Unit,
    onEditProduct: (String) -> Unit,
    viewModel: EntryDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nutrition by viewModel.nutrition.collectAsStateWithLifecycle()
    var showNutrition by remember { mutableStateOf(false) }
    var confirmLast by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.closed.collect { onBack() } }
    LaunchedEffect(Unit) { viewModel.replaced.collect { onReplace(it) } }
    var openedDialog by remember { mutableStateOf(false) }
    var nfcDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.detail_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    GlassIconButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back), onBack, Modifier.padding(start = 8.dp))
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
                GlassCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ProductImage(item.imagePath, size = 112.dp, name = item.productName)
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                item.productName,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            if (nutrition != null) {
                                IconButton(onClick = { showNutrition = true }) {
                                    Icon(
                                        Icons.Outlined.Info,
                                        contentDescription = stringResource(R.string.nutrition_show),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                        item.brand?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = VorratTheme.colors.textTertiary) }
                        val product = state.product
                        product?.barcode?.let { Text(stringResource(R.string.detail_barcode, it), style = MaterialTheme.typography.bodySmall, color = VorratTheme.colors.textSecondary) }
                        CategoryResolver.displayName(product?.category)?.let {
                            Text(stringResource(R.string.detail_category, it), style = MaterialTheme.typography.bodySmall, color = VorratTheme.colors.textSecondary)
                        }
                        if (product?.packageSize != null) {
                            val unit = state.units.firstOrNull { it.id == product.unitId }?.symbol.orEmpty()
                            Text(
                                stringResource(R.string.detail_package, product.packageSize.toPlainString(), unit),
                                style = MaterialTheme.typography.bodySmall,
                                color = VorratTheme.colors.textSecondary,
                            )
                        }
                    }
                }
                }

                StockItemCard(item, state.today, state.leadDays, onClick = null)
                item.openedAt?.let { opened ->
                    Text(
                        stringResource(R.string.opened_since, Validation.formatDate(opened)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { openedDialog = true }, modifier = Modifier.weight(1f).height(48.dp)) {
                        Icon(Icons.Outlined.LockOpen, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(if (item.openedAt == null) R.string.opened_action else R.string.opened_edit))
                    }
                    OutlinedButton(onClick = viewModel::addToShoppingList, modifier = Modifier.weight(1f).height(48.dp)) {
                        Icon(Icons.Outlined.AddShoppingCart, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.shopping_add_short))
                    }
                }
                item.note?.takeIf { it.isNotBlank() }?.let {
                    Text(stringResource(R.string.detail_note, it), style = MaterialTheme.typography.bodyMedium)
                }
                GlassCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Outlined.Nfc, contentDescription = null, tint = VorratTheme.colors.textTertiary)
                    Text(
                        stringResource(if (item.nfcTagId != null) R.string.nfc_linked else R.string.nfc_not_linked),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (item.nfcTagId != null) {
                        androidx.compose.material3.TextButton(onClick = viewModel::clearNfcTag) {
                            Text(stringResource(R.string.nfc_unlink))
                        }
                    }
                    OutlinedButton(onClick = { nfcDialog = true }) {
                        Text(stringResource(if (item.nfcTagId != null) R.string.nfc_rewrite else R.string.nfc_write))
                    }
                }
                }
                if (nfcDialog) {
                    de.frelina.vorrat.nfc.NfcDialog(
                        mode = de.frelina.vorrat.nfc.NfcMode.Write(viewModel.nfcEntryId),
                        onDismiss = { nfcDialog = false },
                        onWritten = { tagId -> nfcDialog = false; viewModel.onNfcWritten(tagId) },
                    )
                }

                // Anzahl +/- (große Touch-Ziele)
                GlassCard(Modifier.fillMaxWidth()) {
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
                            Text(stringResource(R.string.detail_quantity), style = MaterialTheme.typography.labelLarge, color = VorratTheme.colors.textSecondary)
                            Text(
                                stringResource(R.string.quantity_label, item.quantity),
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

    val openedItem = state.item
    if (openedDialog && openedItem != null) {
        OpenedDialog(
            initialDate = openedItem.openedAt ?: LocalDate.now(),
            initialDays = openedItem.openedDays,
            alreadyOpened = openedItem.openedAt != null,
            expiry = openedItem.expiryDate,
            suggestion = viewModel::openedDaysSuggestion,
            onDismiss = { openedDialog = false },
            onConfirm = { date, days -> openedDialog = false; viewModel.markOpened(date, days) },
            onClear = { openedDialog = false; viewModel.clearOpened() },
        )
    }
    val shownNutrition = nutrition
    if (showNutrition && shownNutrition != null) {
        NutritionDialog(shownNutrition, onDismiss = { showNutrition = false })
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

/** Eine Zeile der Nährwerttabelle; [indented] für "davon …"-Werte. */
private data class NutritionRow(val label: String, val value: String?, val indented: Boolean = false)

/** Nährwerttabelle je 100 g/ml; fehlende Werte werden ausgelassen. */
@Composable
private fun NutritionDialog(nutrition: Nutrition, onDismiss: () -> Unit) {
    val g = stringResource(R.string.nutrition_unit_g)
    fun grams(value: Double?) = value?.let { NutritionParser.formatAmount(it) + " " + g }
    val energy = if (nutrition.energyKj != null && nutrition.energyKcal != null) {
        stringResource(
            R.string.nutrition_energy_value,
            NutritionParser.formatEnergy(nutrition.energyKj),
            NutritionParser.formatEnergy(nutrition.energyKcal),
        )
    } else {
        null
    }
    val rows = listOf(
        NutritionRow(stringResource(R.string.nutrition_energy), energy),
        NutritionRow(stringResource(R.string.nutrition_fat), grams(nutrition.fat)),
        NutritionRow(stringResource(R.string.nutrition_saturated_fat), grams(nutrition.saturatedFat), indented = true),
        NutritionRow(stringResource(R.string.nutrition_carbohydrates), grams(nutrition.carbohydrates)),
        NutritionRow(stringResource(R.string.nutrition_sugars), grams(nutrition.sugars), indented = true),
        NutritionRow(stringResource(R.string.nutrition_fiber), grams(nutrition.fiber)),
        NutritionRow(stringResource(R.string.nutrition_proteins), grams(nutrition.proteins)),
        NutritionRow(stringResource(R.string.nutrition_salt), grams(nutrition.salt)),
    ).filter { it.value != null }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Info, contentDescription = null) },
        title = { Text(stringResource(R.string.nutrition_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(if (nutrition.perMl) R.string.nutrition_per_100ml else R.string.nutrition_per_100g),
                    style = MaterialTheme.typography.labelLarge,
                )
                HorizontalDivider()
                rows.forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            row.label,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f).padding(start = if (row.indented) 16.dp else 0.dp),
                        )
                        Text(row.value.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                HorizontalDivider()
                Text(
                    stringResource(R.string.nutrition_source),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

/** "Geöffnet am": Datum (heute vorbelegt) und Haltbarkeit nach dem Öffnen in Tagen (mit Vorschlag). */
@Composable
private fun OpenedDialog(
    initialDate: LocalDate,
    initialDays: Int?,
    alreadyOpened: Boolean,
    expiry: LocalDate?,
    suggestion: suspend () -> Pair<Int?, Boolean>,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, Int?) -> Unit,
    onClear: () -> Unit,
) {
    var dateText by remember { mutableStateOf(Validation.formatDate(initialDate)) }
    var daysText by remember { mutableStateOf(initialDays?.toString().orEmpty()) }
    var fromOff by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (initialDays == null) {
            val (days, off) = suggestion()
            if (days != null && daysText.isEmpty()) {
                daysText = days.toString()
                fromOff = off
            }
        }
    }
    val date = (Validation.optionalDate(dateText) as? Validated.Ok)?.value
    val daysV = Validation.optionalWholeNumber(daysText)
    val days = (daysV as? Validated.Ok)?.value
    val useBy = OpenedLogic.useBy(date, days)
    val valid = date != null && daysV is Validated.Ok && (days == null || days <= OpenedLogic.MAX_DAYS)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.LockOpen, contentDescription = null) },
        title = { Text(stringResource(R.string.opened_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField(
                    value = dateText,
                    onValueChange = { dateText = it },
                    label = stringResource(R.string.opened_date),
                    error = if (dateText.isNotEmpty() && date == null) ValidationError.INVALID_DATE else null,
                    modifier = Modifier.fillMaxWidth(),
                )
                NumberField(
                    value = daysText,
                    onValueChange = { v -> daysText = v.filter(Char::isDigit).take(4); fromOff = false },
                    label = stringResource(R.string.opened_days) + " (" + stringResource(R.string.optional) + ")",
                    error = (daysV as? Validated.Error)?.error,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (fromOff) {
                    Text(stringResource(R.string.opened_days_from_off), style = MaterialTheme.typography.bodySmall)
                }
                if (useBy != null) {
                    val effective = OpenedLogic.effectiveExpiry(expiry, date, days)
                    Text(
                        stringResource(R.string.opened_use_by, Validation.formatDate(effective)),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                if (!alreadyOpened) {
                    Text(
                        stringResource(R.string.opened_split_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (date != null) onConfirm(date, days) }, enabled = valid) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Row {
                if (alreadyOpened) TextButton(onClick = onClear) { Text(stringResource(R.string.opened_clear)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}
