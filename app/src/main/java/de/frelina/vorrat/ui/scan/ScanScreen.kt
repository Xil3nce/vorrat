package de.frelina.vorrat.ui.scan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import de.frelina.vorrat.domain.ExpiryLogic
import de.frelina.vorrat.domain.ExpiryStatus
import de.frelina.vorrat.domain.ShoppingLogic
import de.frelina.vorrat.ui.components.CountCircle
import de.frelina.vorrat.ui.components.GlassIconButton
import de.frelina.vorrat.ui.components.statusColor
import de.frelina.vorrat.ui.theme.VorratTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.RemoveCircleOutline
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
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ButtonDefaults
import de.frelina.vorrat.data.db.LocationEntity
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import de.frelina.vorrat.ui.components.GlassCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
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
    onOpenEntry: (String) -> Unit,
    onAddNewProduct: (barcode: String) -> Unit,
    onAddEntryForProduct: (productId: String) -> Unit,
    onCreateManual: () -> Unit,
    onReadNfc: () -> Unit,
    onOpenPurchase: () -> Unit,
    onOpenConsume: () -> Unit,
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val leadDays by viewModel.leadDays.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val units by viewModel.units.collectAsStateWithLifecycle()
    val purchaseCount by viewModel.purchaseCount.collectAsStateWithLifecycle()
    val consumeCount by viewModel.consumeCount.collectAsStateWithLifecycle()
    val locations by viewModel.locations.collectAsStateWithLifecycle()
    val purchaseLocationId by viewModel.purchaseLocationId.collectAsStateWithLifecycle()
    var consumeFor by remember { mutableStateOf<ScanOutcome?>(null) }

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

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // ---------------- Modus (Prüfen / + Bestand / Entnehmen)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            listOf(
                ScanMode.CHECK to R.string.scan_mode_check,
                ScanMode.PURCHASE to R.string.scan_mode_purchase,
                ScanMode.CONSUME to R.string.scan_mode_consume,
            ).forEachIndexed { index, (m, label) ->
                SegmentedButton(
                    selected = mode == m,
                    onClick = { viewModel.setMode(m) },
                    shape = SegmentedButtonDefaults.itemShape(index, 3),
                ) { Text(stringResource(label), maxLines = 1) }
            }
        }
        // ---------------- Kamera (fix oben; bei "+ Bestand" immer groß – Ergebnis gibt es dort nicht)
        val purchase = mode == ScanMode.PURCHASE
        val idle = state is ScanUiState.Idle
        val consume = mode == ScanMode.CONSUME
        val bigCamera = idle || purchase || (consume && state is ScanUiState.Loading)
        val expiryFor = (state as? ScanUiState.PurchaseExpiry)?.outcome
        Box(
            Modifier
                .fillMaxWidth()
                .then(if (bigCamera) Modifier.weight(1f) else Modifier.height(200.dp))
                .clip(RoundedCornerShape(28.dp))
                .background(Color.Black)
                .clipToBounds(), // PreviewView skaliert sein TextureView – ohne Clip ragt das Bild ins Ergebnis
        ) {
            if (hasPermission) {
                // Während des Ablaufdatum-Scans gehört die Kamera dem Dialog; danach wird sie neu gebunden.
                if (expiryFor == null) {
                    CameraPreview(
                        analyzing = idle,
                        torchOn = torch,
                        onBarcode = viewModel::onBarcode,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                ScanFrame(Modifier.fillMaxSize().padding(horizontal = 44.dp, vertical = 28.dp))
                GlassIconButton(
                    if (torch) Icons.Outlined.FlashlightOff else Icons.Outlined.FlashlightOn,
                    stringResource(if (torch) R.string.scan_torch_off else R.string.scan_torch_on),
                    onClick = { torch = !torch },
                    modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                )
                if ((purchase || consume) && state is ScanUiState.Loading) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                if (idle) {
                    Text(
                        stringResource(
                            when (mode) {
                                ScanMode.CONSUME -> R.string.scan_hint_consume
                                ScanMode.PURCHASE -> R.string.scan_hint_purchase
                                ScanMode.CHECK -> R.string.scan_hint
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = VorratTheme.colors.textTertiary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
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

        // ---------------- Ergebnis (füllt den Platz dazwischen)
        when (val s = state) {
            ScanUiState.Idle, is ScanUiState.PurchaseExpiry -> Unit
            is ScanUiState.Loading -> if (!purchase && !consume) GlassCard(Modifier.fillMaxWidth().weight(1f), shape = MaterialTheme.shapes.large) {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.scan_loading, s.barcode))
                }
            }
            is ScanUiState.Result -> ResultPanel(
                outcome = s.outcome,
                onShoppingList = s.onShoppingList,
                leadDays = leadDays,
                units = units,
                onOpenEntry = onOpenEntry,
                onRetry = viewModel::retry,
                onConsume = { consumeFor = s.outcome },
                onAddToShoppingList = viewModel::addToShoppingList,
                onAdd = {
                    val o = s.outcome
                    when {
                        o.exactMatches.isNotEmpty() -> existingDialogFor = o
                        o.localProduct != null -> onAddEntryForProduct(o.localProduct.id)
                        else -> onAddNewProduct(o.barcode)
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }

        // ---------------- Scan-Liste ("+ Bestand": mit Ort daneben, "Entnehmen": volle Breite)
        if (purchase) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onOpenPurchase,
                    enabled = purchaseCount > 0,
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    Icon(Icons.Outlined.ShoppingBag, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.scan_list_show, purchaseCount), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                LocationPicker(
                    locations = locations,
                    selectedId = purchaseLocationId,
                    onSelect = viewModel::setPurchaseLocation,
                    modifier = Modifier.weight(1f).height(52.dp),
                )
            }
        } else if (consume) {
            Button(
                onClick = onOpenConsume,
                enabled = consumeCount > 0,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Outlined.RemoveCircleOutline, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.scan_list_show, consumeCount))
            }
        }

        // ---------------- Modus-Kacheln (immer fix unten)
        Row(
            Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ModeTile(
                Icons.Outlined.QrCodeScanner,
                stringResource(if (idle) R.string.scan_tile_barcode else R.string.scan_again),
                active = idle,
                onClick = viewModel::rescan,
            )
            ModeTile(Icons.Outlined.Nfc, stringResource(R.string.scan_tile_nfc), active = false, onClick = onReadNfc)
            ModeTile(Icons.Outlined.Keyboard, stringResource(R.string.scan_tile_type), active = false, onClick = { manualDialog = true })
            ModeTile(Icons.Outlined.Add, stringResource(R.string.scan_tile_new), active = false, onClick = onCreateManual)
        }
    }

    (state as? ScanUiState.PurchaseExpiry)?.outcome?.let { outcome ->
        de.frelina.vorrat.ui.components.ExpiryScanDialog(
            title = stringResource(
                R.string.purchase_expiry_for,
                outcome.displayName ?: stringResource(R.string.scan_unknown_product, outcome.barcode),
            ),
            onDismiss = viewModel::cancelPurchase,
            onDate = { viewModel.addToPurchase(it) },
            onSkip = { viewModel.addToPurchase(null) },
        )
    }
    if (manualDialog) {
        ManualBarcodeDialog(
            onDismiss = { manualDialog = false },
            onConfirm = { manualDialog = false; viewModel.rescan(); viewModel.onBarcode(it) },
        )
    }
    consumeFor?.let { outcome ->
        if (outcome.exactMatches.isEmpty()) {
            consumeFor = null
        } else {
            ConsumeDialog(
                entries = outcome.exactMatches,
                leadDays = leadDays,
                onDismiss = { consumeFor = null },
                onConsume = { entryId, amount -> consumeFor = null; viewModel.consume(entryId, amount) },
            )
        }
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

/** Ort für neue Artikel im Modus "+ Bestand": Dropdown mit "Kein Ort" und allen Orten; gewählter Ort hervorgehoben. */
@Composable
private fun LocationPicker(
    locations: List<LocationEntity>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val selected = locations.firstOrNull { it.id == selectedId }
    val primary = MaterialTheme.colorScheme.primary
    Box(modifier) {
        OutlinedButton(
            onClick = { open = true },
            contentPadding = PaddingValues(start = 12.dp, end = 4.dp),
            colors = if (selected != null) {
                ButtonDefaults.outlinedButtonColors(containerColor = primary.copy(alpha = 0.16f), contentColor = primary)
            } else {
                ButtonDefaults.outlinedButtonColors(contentColor = VorratTheme.colors.textSecondary)
            },
            border = BorderStroke(1.dp, if (selected != null) primary else VorratTheme.colors.glassBorder),
            modifier = Modifier.fillMaxSize(),
        ) {
            Icon(Icons.Outlined.Place, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(6.dp))
            Text(
                selected?.name ?: stringResource(R.string.scan_location_none),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Outlined.ArrowDropDown, contentDescription = stringResource(R.string.scan_location_cd))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.scan_location_menu_none)) },
                trailingIcon = { if (selected == null) Icon(Icons.Outlined.Check, contentDescription = null) },
                onClick = { open = false; onSelect(null) },
            )
            if (locations.isNotEmpty()) HorizontalDivider()
            locations.forEach { location ->
                DropdownMenuItem(
                    text = { Text(location.name) },
                    trailingIcon = { if (location.id == selected?.id) Icon(Icons.Outlined.Check, contentDescription = null) },
                    onClick = { open = false; onSelect(location.id) },
                )
            }
        }
    }
}

@Composable
internal fun PermissionRequest(denied: Boolean, onRequest: () -> Unit, onOpenSettings: () -> Unit) {
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

/** Kachel der unteren Modusleiste. */
@Composable
private fun RowScope.ModeTile(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val colors = VorratTheme.colors
    Column(
        Modifier
            .weight(1f)
            .height(64.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (active) colors.accent.copy(alpha = 0.14f) else colors.glass)
            .border(1.dp, if (active) colors.accent.copy(alpha = 0.6f) else colors.glassBorder, RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (active) colors.accent else colors.textTertiary, modifier = Modifier.size(22.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) MaterialTheme.colorScheme.onSurface else colors.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp),
        )
    }
}

/** Rahmen-Ecken über dem Kamerabild. */
@Composable
internal fun ScanFrame(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(modifier) {
        val len = 38.dp.toPx()
        val w = 3.dp.toPx()
        val stroke = Stroke(w, cap = StrokeCap.Round)
        val r = size.width
        val b = size.height
        listOf(
            Offset(0f, len) to Offset(0f, 0f), Offset(0f, 0f) to Offset(len, 0f),
            Offset(r - len, 0f) to Offset(r, 0f), Offset(r, 0f) to Offset(r, len),
            Offset(0f, b - len) to Offset(0f, b), Offset(0f, b) to Offset(len, b),
            Offset(r - len, b) to Offset(r, b), Offset(r, b) to Offset(r, b - len),
        ).forEach { (from, to) -> drawLine(color, from, to, strokeWidth = stroke.width, cap = StrokeCap.Round) }
    }
}

@Composable
private fun ResultPanel(
    outcome: ScanOutcome,
    onShoppingList: Boolean,
    leadDays: Int,
    units: Map<String, String>,
    onOpenEntry: (String) -> Unit,
    onRetry: () -> Unit,
    onConsume: () -> Unit,
    onAddToShoppingList: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now()
    val colors = VorratTheme.colors
    GlassCard(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Kopf: gescanntes Produkt (fix)
            val name = outcome.displayName ?: stringResource(R.string.scan_unknown_product, outcome.barcode)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                ProductImage(outcome.localProduct?.imagePath ?: outcome.remote?.imagePath, size = 52.dp, name = name)
                Column(Modifier.weight(1f)) {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val pack = ShoppingLogic.packageLabel(
                        outcome.localProduct?.packageSize ?: outcome.remote?.packageSize,
                        outcome.localProduct?.unitId?.let(units::get) ?: outcome.remote?.unitSymbol,
                    )
                    val total = outcome.exactMatches.sumOf { it.quantity }
                    val places = outcome.exactMatches.size
                    val stock = when {
                        places == 0 -> null
                        places == 1 -> stringResource(R.string.scan_total_single, total)
                        else -> pluralStringResource(R.plurals.scan_total_places, places, total, places)
                    }
                    val brand = outcome.localProduct?.brand ?: outcome.remote?.brand
                    val sub = listOfNotNull(brand?.takeIf { it.isNotBlank() }, pack, stock).joinToString(" · ")
                    Text(
                        sub.ifEmpty { stringResource(R.string.detail_barcode, outcome.barcode) },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }

            // Mitte: nur dieser Teil scrollt (Orte, Alternativen, Hinweise)
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (outcome.localProduct == null && outcome.remote != null) {
                    Text(stringResource(R.string.scan_source_off), style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                }
                outcome.lookupError?.let { err ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(12.dp),
                    ) {
                        Text(
                            stringResource(
                                when (err) {
                                    LookupErrorKind.NO_NETWORK -> R.string.scan_error_network
                                    LookupErrorKind.TIMEOUT -> R.string.scan_error_timeout
                                    LookupErrorKind.RATE_LIMITED -> R.string.scan_error_rate_limited
                                    LookupErrorKind.SERVER -> R.string.scan_error_server
                                },
                            ),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        TextButton(onClick = onRetry) { Text(stringResource(R.string.scan_retry)) }
                    }
                }
                if (outcome.remoteNotFound) Text(stringResource(R.string.scan_off_not_found), color = colors.textSecondary)

                if (outcome.exactMatches.isNotEmpty()) {
                    outcome.exactMatches.forEach { item -> PlaceRow(item, today, leadDays, onClick = { onOpenEntry(item.entryId) }) }
                } else {
                    Text(
                        stringResource(R.string.scan_not_in_stock),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (outcome.alternatives.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            Icon(Icons.Outlined.SwapHoriz, contentDescription = null, tint = colors.warning)
                            Spacer(Modifier.size(8.dp))
                            Text(
                                stringResource(R.string.scan_alternatives_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.warning,
                            )
                        }
                        outcome.alternatives.forEach { item ->
                            PlaceRow(item, today, leadDays, onClick = { onOpenEntry(item.entryId) }, showName = true)
                        }
                    } else {
                        Text(stringResource(R.string.scan_nothing), color = colors.textSecondary)
                        when {
                            outcome.category == null && outcome.localProduct == null && outcome.lookupError != null ->
                                Text(stringResource(R.string.scan_offline_no_alternatives), style = MaterialTheme.typography.bodySmall)
                            outcome.category == null && (outcome.localProduct != null || outcome.remote != null) ->
                                Text(stringResource(R.string.scan_no_category), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // Aktionen fest am unteren Kartenrand: eine Zeile
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onAdd, modifier = Modifier.weight(1f).height(48.dp)) {
                    Text(
                        stringResource(
                            if (outcome.localProduct == null && outcome.remote == null) R.string.scan_create_manual
                            else R.string.scan_add_stock_short,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (onShoppingList) {
                    FilledTonalIconButton(onClick = {}, enabled = false, modifier = Modifier.size(48.dp)) {
                        Icon(
                            Icons.Outlined.ShoppingCart,
                            contentDescription = stringResource(R.string.shopping_on_list),
                            tint = colors.info,
                        )
                    }
                } else if (outcome.displayName != null) {
                    FilledTonalIconButton(onClick = onAddToShoppingList, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.AddShoppingCart, contentDescription = stringResource(R.string.shopping_add_product))
                    }
                }
                if (outcome.exactMatches.isNotEmpty()) {
                    FilledTonalIconButton(onClick = onConsume, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.RemoveCircleOutline, contentDescription = stringResource(R.string.consume_action))
                    }
                }
            }
        }
    }
}

/** Eine Zeile je Ort: Status-Punkt, Ort, Ablauf-Info, kleiner Kreis mit Anzahl am Ende. */
@Composable
private fun PlaceRow(item: StockItem, today: LocalDate, leadDays: Int, onClick: () -> Unit, showName: Boolean = false) {
    val status = ExpiryLogic.status(item.effectiveExpiry, today, leadDays)
    val color = statusColor(status)
    val colors = VorratTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.glass)
            .border(1.dp, colors.glassBorder.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Column(Modifier.weight(1f)) {
            Text(
                if (showName) item.productName else locationText(item),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (showName) locationText(item) + " · " + expiryText(item, today, leadDays) else expiryText(item, today, leadDays),
                style = MaterialTheme.typography.bodySmall,
                color = if (status == ExpiryStatus.EXPIRED || status == ExpiryStatus.SOON) color else colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        CountCircle(stringResource(R.string.shopping_quantity, item.quantity), size = 36.dp)
    }
}

@Composable
internal fun ManualBarcodeDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
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
    onIncrease: (entryId: String, amount: Int) -> Unit,
    onNewEntry: () -> Unit,
) {
    var selected by remember { mutableStateOf(entries.first().entryId) }
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
