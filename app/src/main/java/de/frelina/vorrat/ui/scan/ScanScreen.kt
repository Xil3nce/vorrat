package de.frelina.vorrat.ui.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.frelina.vorrat.R
import de.frelina.vorrat.data.db.StockItem
import de.frelina.vorrat.data.off.LookupErrorKind
import de.frelina.vorrat.data.repo.ScanOutcome
import de.frelina.vorrat.domain.Validated
import de.frelina.vorrat.domain.Validation
import de.frelina.vorrat.ui.components.ProductImage
import de.frelina.vorrat.ui.components.StockItemCard
import de.frelina.vorrat.ui.components.expiryText
import de.frelina.vorrat.ui.components.locationText
import de.frelina.vorrat.ui.components.validationMessage
import java.time.LocalDate

@Composable
fun ScanScreen(
    onOpenEntry: (Long) -> Unit,
    onAddNewProduct: (barcode: String) -> Unit,
    onAddEntryForProduct: (productId: Long) -> Unit,
    onCreateManual: () -> Unit,
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val leadDays by viewModel.leadDays.collectAsStateWithLifecycle()

    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        permissionDenied = !granted
    }
    var torch by rememberSaveable { mutableStateOf(false) }
    var manualDialog by rememberSaveable { mutableStateOf(false) }
    var existingDialogFor by remember { mutableStateOf<ScanOutcome?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        viewModel.refresh()
    }

    Column(Modifier.fillMaxSize()) {
        // ---------------- Kamera
        Box(
            Modifier
                .fillMaxWidth()
                .weight(if (state is ScanUiState.Idle) 1f else 0.45f)
                .clipToBounds(), // PreviewView skaliert sein TextureView – ohne Clip ragt das Bild ins Ergebnis
        ) {
            if (hasPermission) {
                CameraPreview(
                    analyzing = state is ScanUiState.Idle,
                    torchOn = torch,
                    onBarcode = viewModel::onBarcode,
                    modifier = Modifier.fillMaxSize(),
                )
                IconButton(
                    onClick = { torch = !torch },
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(48.dp),
                ) {
                    Icon(
                        if (torch) Icons.Outlined.FlashlightOff else Icons.Outlined.FlashlightOn,
                        contentDescription = stringResource(if (torch) R.string.scan_torch_off else R.string.scan_torch_on),
                        tint = MaterialTheme.colorScheme.inverseOnSurface,
                    )
                }
                if (state is ScanUiState.Idle) {
                    Surface(
                        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.8f),
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    ) {
                        Text(stringResource(R.string.scan_hint), Modifier.padding(12.dp), textAlign = TextAlign.Center)
                    }
                }
            } else {
                PermissionRequest(
                    denied = permissionDenied,
                    onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    onOpenSettings = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                        )
                    },
                )
            }
        }

        // ---------------- Ergebnis
        when (val s = state) {
            ScanUiState.Idle -> Unit
            is ScanUiState.Loading -> Box(Modifier.fillMaxWidth().weight(0.55f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.scan_loading, s.barcode))
                }
            }
            is ScanUiState.Result -> ResultPanel(
                outcome = s.outcome,
                leadDays = leadDays,
                onOpenEntry = onOpenEntry,
                onRetry = viewModel::retry,
                onAdd = {
                    val o = s.outcome
                    when {
                        o.exactMatches.isNotEmpty() -> existingDialogFor = o
                        o.localProduct != null -> onAddEntryForProduct(o.localProduct.id)
                        else -> onAddNewProduct(o.barcode)
                    }
                },
                modifier = Modifier.weight(0.55f),
            )
        }

        // ---------------- Bedienleiste (unten, einhändig erreichbar)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = { manualDialog = true }, modifier = Modifier.height(56.dp)) {
                Icon(Icons.Outlined.Keyboard, contentDescription = stringResource(R.string.scan_enter_barcode))
            }
            OutlinedButton(onClick = onCreateManual, modifier = Modifier.height(56.dp)) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.stock_add_manual))
            }
            Button(
                onClick = viewModel::rescan,
                enabled = state !is ScanUiState.Idle,
                modifier = Modifier.weight(1f).height(56.dp),
            ) {
                Icon(Icons.Outlined.QrCodeScanner, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.scan_again), style = MaterialTheme.typography.titleMedium)
            }
        }
    }

    if (manualDialog) {
        ManualBarcodeDialog(
            onDismiss = { manualDialog = false },
            onConfirm = { manualDialog = false; viewModel.rescan(); viewModel.onBarcode(it) },
        )
    }
    existingDialogFor?.let { outcome ->
        ExistingEntryDialog(
            entries = outcome.exactMatches,
            leadDays = leadDays,
            onDismiss = { existingDialogFor = null },
            onIncrease = { entryId, amount -> existingDialogFor = null; viewModel.increase(entryId, amount) },
            onNewEntry = {
                existingDialogFor = null
                outcome.localProduct?.let { onAddEntryForProduct(it.id) }
            },
        )
    }
}

@Composable
private fun PermissionRequest(denied: Boolean, onRequest: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(48.dp))
        Text(stringResource(R.string.scan_permission_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.scan_permission_text), textAlign = TextAlign.Center)
        if (denied) {
            Text(stringResource(R.string.scan_permission_denied), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = onOpenSettings) { Text(stringResource(R.string.scan_open_settings)) }
        }
        Button(onClick = onRequest, modifier = Modifier.height(48.dp)) { Text(stringResource(R.string.scan_permission_grant)) }
    }
}

@Composable
private fun ResultPanel(
    outcome: ScanOutcome,
    leadDays: Int,
    onOpenEntry: (Long) -> Unit,
    onRetry: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now()
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Kopf: gescanntes Produkt
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ProductImage(outcome.localProduct?.imagePath ?: outcome.remote?.imagePath, size = 64.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    outcome.displayName ?: stringResource(R.string.scan_unknown_product, outcome.barcode),
                    style = MaterialTheme.typography.titleLarge,
                )
                (outcome.localProduct?.brand ?: outcome.remote?.brand)?.let { Text(it) }
                Text(stringResource(R.string.detail_barcode, outcome.barcode), style = MaterialTheme.typography.bodySmall)
                if (outcome.localProduct == null && outcome.remote != null) {
                    Text(stringResource(R.string.scan_source_off), style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // Fehler/Hinweise zur OFF-Abfrage
        outcome.lookupError?.let { err ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        stringResource(
                            when (err) {
                                LookupErrorKind.NO_NETWORK -> R.string.scan_error_network
                                LookupErrorKind.TIMEOUT -> R.string.scan_error_timeout
                                LookupErrorKind.RATE_LIMITED -> R.string.scan_error_rate_limited
                                LookupErrorKind.SERVER -> R.string.scan_error_server
                            },
                        ),
                    )
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.scan_retry)) }
                }
            }
        }
        if (outcome.remoteNotFound) Text(stringResource(R.string.scan_off_not_found))

        if (outcome.exactMatches.isNotEmpty()) {
            // Zustand 1: exakter Treffer
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(8.dp))
                Text(
                    stringResource(R.string.scan_in_stock),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            outcome.exactMatches.forEach { item ->
                StockItemCard(item, today, leadDays, onClick = { onOpenEntry(item.entryId) })
            }
        } else {
            Text(
                stringResource(R.string.scan_not_in_stock),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (outcome.alternatives.isNotEmpty()) {
                // Zustand 2: Alternativen – eigener, deutlich getrennter Abschnitt
                HorizontalDivider()
                OutlinedCard(
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.SwapHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(Modifier.size(8.dp))
                            Text(
                                stringResource(R.string.scan_alternatives_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                        outcome.alternatives.forEach { item ->
                            Text(
                                stringResource(R.string.scan_alternative_badge),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                            StockItemCard(item, today, leadDays, onClick = { onOpenEntry(item.entryId) })
                        }
                    }
                }
            } else {
                // Zustand 3: nichts vorrätig
                Text(stringResource(R.string.scan_nothing))
                when {
                    outcome.category == null && outcome.localProduct == null && outcome.lookupError != null ->
                        Text(stringResource(R.string.scan_offline_no_alternatives), style = MaterialTheme.typography.bodySmall)
                    outcome.category == null && (outcome.localProduct != null || outcome.remote != null) ->
                        Text(stringResource(R.string.scan_no_category), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        FilledTonalButton(onClick = onAdd, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(
                stringResource(
                    if (outcome.localProduct == null && outcome.remote == null) R.string.scan_create_manual else R.string.scan_add_to_stock,
                ),
            )
        }
    }
}

@Composable
private fun ManualBarcodeDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.scan_enter_barcode)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { v -> text = v.filter(Char::isDigit).take(14) },
                label = { Text(stringResource(R.string.scan_barcode_field)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.length >= 6) { Text(stringResource(R.string.scan_lookup)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Produkt ist schon im Bestand: Anzahl eines Eintrags erhöhen oder neuen Eintrag anlegen. */
@Composable
private fun ExistingEntryDialog(
    entries: List<StockItem>,
    leadDays: Int,
    onDismiss: () -> Unit,
    onIncrease: (entryId: Long, amount: Int) -> Unit,
    onNewEntry: () -> Unit,
) {
    var selected by remember { mutableLongStateOf(entries.first().entryId) }
    var amount by rememberSaveable { mutableStateOf("1") }
    val validated = Validation.quantity(amount)
    val today = LocalDate.now()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.scan_existing_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.scan_existing_text))
                entries.forEach { item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = selected == item.entryId, role = Role.RadioButton) { selected = item.entryId }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == item.entryId, onClick = null)
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
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text(stringResource(R.string.scan_existing_increase)) },
                    singleLine = true,
                    isError = validated is Validated.Error,
                    supportingText = (validated as? Validated.Error)?.let { e -> { Text(validationMessage(e.error)) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { (validated as? Validated.Ok)?.let { onIncrease(selected, it.value) } },
                enabled = validated is Validated.Ok,
            ) { Text(stringResource(R.string.scan_existing_increase_action)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                TextButton(onClick = onNewEntry) { Text(stringResource(R.string.scan_existing_new_entry)) }
            }
        },
    )
}
