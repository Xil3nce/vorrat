package de.frelina.vorrat.ui.product

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material.icons.outlined.AddLocationAlt
import androidx.compose.material.icons.outlined.WrongLocation
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.frelina.vorrat.R
import de.frelina.vorrat.data.db.LocationEntity
import de.frelina.vorrat.data.db.QuantityTypeEntity
import de.frelina.vorrat.data.db.UnitEntity
import de.frelina.vorrat.data.off.LookupErrorKind
import de.frelina.vorrat.data.repo.SaveResult
import de.frelina.vorrat.ui.components.AutocompleteField
import de.frelina.vorrat.ui.components.DateField
import de.frelina.vorrat.ui.components.ErrorSupportingText
import de.frelina.vorrat.ui.components.NumberField
import de.frelina.vorrat.ui.components.ProductImage
import de.frelina.vorrat.ui.components.SelectField
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockFormScreen(
    onDone: () -> Unit,
    viewModel: StockFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val locations by viewModel.locations.collectAsStateWithLifecycle()
    val units by viewModel.units.collectAsStateWithLifecycle()
    val types by viewModel.quantityTypes.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val manualNames by viewModel.manualProductNames.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.saved.collect { onDone() } }

    val title = when (state.mode) {
        FormMode.NEW_PRODUCT -> when {
            state.frozenMode -> R.string.frozen_add_title
            viewModel.fromScan -> R.string.add_title
            else -> R.string.add_manual_title
        }
        FormMode.EXISTING_PRODUCT -> if (viewModel.duplicating) R.string.duplicate_entry_title else R.string.add_title
        FormMode.EDIT_ENTRY -> R.string.edit_entry_title
        FormMode.EDIT_PRODUCT -> R.string.edit_product_title
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(title)) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.add_loading_off))
                }
            }
            return@Scaffold
        }
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LookupHints(state)

            val editableProduct = state.mode == FormMode.NEW_PRODUCT || state.mode == FormMode.EDIT_PRODUCT
            if (editableProduct && state.frozenMode) {
                SectionTitle(R.string.section_product)
                AutocompleteField(
                    value = state.product.name,
                    onValueChange = { v -> viewModel.updateProduct { it.copy(name = v, nameError = null) } },
                    label = stringResource(R.string.frozen_name_label),
                    suggestions = manualNames,
                    supportingText = {
                        if (state.product.nameError != null) ErrorSupportingText(state.product.nameError!!)
                        else Text(stringResource(R.string.frozen_name_hint))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (editableProduct) {
                SectionTitle(R.string.section_product)
                ProductFormSection(
                    form = state.product,
                    units = units,
                    categories = categories,
                    imageError = state.imageError,
                    onChange = viewModel::updateProduct,
                    onImagePicked = viewModel::onImagePicked,
                    onRemoveImage = viewModel::removeImage,
                )
            } else {
                state.existingProduct?.let { p ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        ProductImage(p.imagePath, size = 56.dp)
                        Column {
                            Text(p.name, style = MaterialTheme.typography.titleMedium)
                            p.brand?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }

            if (state.mode != FormMode.EDIT_PRODUCT) {
                if (editableProduct) HorizontalDivider()
                SectionTitle(R.string.section_stock)
                EntryFormSection(
                    form = state.entry,
                    locations = locations,
                    types = types,
                    onChange = viewModel::updateEntry,
                    onCreateLocation = viewModel::createLocation,
                    onSetFrozen = viewModel::setFrozen,
                    nfcEntryId = viewModel.nfcEntryId,
                    onNfcWritten = viewModel::onNfcWritten,
                    onClearNfc = viewModel::clearNfcTag,
                )
            }

            Button(
                onClick = viewModel::save,
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text(stringResource(R.string.action_save)) }
        }
    }

    state.mergeOffer?.let { offer ->
        AlertDialog(
            onDismissRequest = viewModel::dismissMerge,
            title = { Text(stringResource(R.string.merge_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.merge_text,
                        offer.target.quantity,
                        offer.entry.quantity,
                        offer.target.quantity + offer.entry.quantity,
                    ),
                )
            },
            confirmButton = { TextButton(onClick = viewModel::confirmMerge) { Text(stringResource(R.string.merge_confirm)) } },
            dismissButton = { TextButton(onClick = viewModel::keepSeparate) { Text(stringResource(R.string.merge_keep_separate)) } },
        )
    }
}

@Composable
private fun SectionTitle(text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun LookupHints(state: StockFormState) {
    val messages = buildList {
        if (state.prefilledFromOff) add(R.string.add_prefilled_hint)
        if (state.packageUnparsed) add(R.string.add_package_unparsed)
        if (state.lookupNotFound) add(R.string.scan_off_not_found)
        when (state.lookupError) {
            LookupErrorKind.NO_NETWORK -> add(R.string.scan_error_network)
            LookupErrorKind.TIMEOUT -> add(R.string.scan_error_timeout)
            LookupErrorKind.RATE_LIMITED -> add(R.string.scan_error_rate_limited)
            LookupErrorKind.SERVER -> add(R.string.scan_error_server)
            null -> Unit
        }
    }
    if (messages.isEmpty()) return
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            messages.forEach { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
fun ProductFormSection(
    form: ProductForm,
    units: List<UnitEntity>,
    categories: List<String>,
    imageError: Boolean,
    onChange: ((ProductForm) -> ProductForm) -> Unit,
    onImagePicked: (Uri) -> Unit,
    onRemoveImage: () -> Unit,
) {
    val context = LocalContext.current
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri?.let(onImagePicked)
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(onImagePicked)
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ProductImage(form.imagePath, size = 96.dp)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = {
                val dir = File(context.cacheDir, "camera").apply { mkdirs() }
                val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                cameraUri = uri
                takePicture.launch(uri)
            }) {
                Icon(Icons.Outlined.PhotoCamera, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.image_take_photo))
            }
            OutlinedButton(onClick = {
                pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) {
                Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.image_pick_gallery))
            }
            if (form.imagePath != null) {
                TextButton(onClick = onRemoveImage) {
                    Icon(Icons.Outlined.Delete, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.image_remove))
                }
            }
        }
    }
    if (imageError) Text(stringResource(R.string.image_error), color = MaterialTheme.colorScheme.error)

    OutlinedTextField(
        value = form.name,
        onValueChange = { v -> onChange { it.copy(name = v, nameError = null) } },
        label = { Text(stringResource(R.string.field_name)) },
        singleLine = true,
        isError = form.nameError != null,
        supportingText = form.nameError?.let { { ErrorSupportingText(it) } },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = form.brand,
        onValueChange = { v -> onChange { it.copy(brand = v) } },
        label = { Text(stringResource(R.string.field_brand) + " (" + stringResource(R.string.optional) + ")") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    AutocompleteField(
        value = form.category,
        onValueChange = { v -> onChange { it.copy(category = v) } },
        label = stringResource(R.string.field_category) + " (" + stringResource(R.string.optional) + ")",
        suggestions = categories,
        supportingText = { Text(stringResource(R.string.field_category_help)) },
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField(
            value = form.packageSize,
            onValueChange = { v -> onChange { it.copy(packageSize = v, packageSizeError = null) } },
            label = stringResource(R.string.field_package_size),
            error = form.packageSizeError,
            decimal = true,
            modifier = Modifier.weight(1f),
        )
        val noneLabel = stringResource(R.string.field_unit_none)
        SelectField(
            label = stringResource(R.string.field_unit),
            options = listOf<UnitEntity?>(null) + units,
            selected = units.firstOrNull { it.id == form.unitId },
            optionLabel = { it?.symbol ?: noneLabel },
            onSelect = { u -> onChange { it.copy(unitId = u?.id) } },
            modifier = Modifier.weight(1f),
        )
    }
    OutlinedTextField(
        value = form.barcode,
        onValueChange = { v -> onChange { it.copy(barcode = v.filter(Char::isLetterOrDigit), barcodeDuplicate = false) } },
        label = { Text(stringResource(R.string.field_barcode) + " (" + stringResource(R.string.optional) + ")") },
        singleLine = true,
        isError = form.barcodeDuplicate,
        supportingText = if (form.barcodeDuplicate) {
            { Text(stringResource(R.string.error_duplicate_barcode), color = MaterialTheme.colorScheme.error) }
        } else {
            null
        },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
    NumberField(
        value = form.minStock,
        onValueChange = { v -> onChange { it.copy(minStock = v.filter(Char::isDigit).take(3), minStockError = null) } },
        label = stringResource(R.string.field_min_stock) + " (" + stringResource(R.string.optional) + ")",
        error = form.minStockError,
        modifier = Modifier.fillMaxWidth(),
    )
    if (form.minStockError == null) {
        Text(
            stringResource(R.string.field_min_stock_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    val detected = remember(form.name, form.category) {
        de.frelina.vorrat.domain.IngredientMatcher.match(form.name, form.category.ifBlank { null })
            ?.let(de.frelina.vorrat.domain.IngredientGroups::label)
    }
    de.frelina.vorrat.ui.components.IngredientGroupField(
        label = stringResource(R.string.field_ingredient_group),
        selected = form.ingredientGroup,
        nullOptionLabel = if (detected != null) {
            stringResource(R.string.ingredient_group_auto_detected, detected)
        } else {
            stringResource(R.string.ingredient_group_auto_none)
        },
        onSelect = { g -> onChange { it.copy(ingredientGroup = g) } },
        supportingText = { Text(stringResource(R.string.field_ingredient_group_help)) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun EntryFormSection(
    form: EntryForm,
    locations: List<LocationEntity>,
    types: List<QuantityTypeEntity>,
    onChange: ((EntryForm) -> EntryForm) -> Unit,
    onCreateLocation: suspend (String) -> SaveResult,
    onSetFrozen: (Boolean) -> Unit,
    nfcEntryId: String,
    onNfcWritten: (String) -> Unit,
    onClearNfc: () -> Unit,
) {
    var newLocationDialog by remember { mutableStateOf(false) }
    var nfcDialog by remember { mutableStateOf(false) }

    if (locations.isEmpty()) {
        Text(stringResource(R.string.add_no_locations), style = MaterialTheme.typography.bodyMedium)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        SelectField(
            label = stringResource(R.string.field_location),
            options = locations,
            selected = locations.firstOrNull { it.id == form.locationId },
            optionLabel = { it.name },
            onSelect = { loc -> onChange { it.copy(locationId = loc.id) } },
            emptyText = stringResource(R.string.location_none_later),
            supportingText = if (form.locationId == null) {
                { Text(stringResource(R.string.field_location_optional_hint)) }
            } else {
                null
            },
            modifier = Modifier.weight(1f),
            extraItem = { close ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.location_none_later)) },
                    leadingIcon = { Icon(Icons.Outlined.WrongLocation, contentDescription = null) },
                    onClick = { close(); onChange { it.copy(locationId = null, level = "", shelf = "") } },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.add_new_location)) },
                    leadingIcon = { Icon(Icons.Outlined.AddLocationAlt, contentDescription = null) },
                    onClick = { close(); newLocationDialog = true },
                )
            },
        )
        IconButton(onClick = { newLocationDialog = true }, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Outlined.AddLocationAlt, contentDescription = stringResource(R.string.add_new_location))
        }
    }
    val location = locations.firstOrNull { it.id == form.locationId }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SlotField(
            value = form.level,
            count = location?.levels,
            onValueChange = { v -> onChange { it.copy(level = v, levelError = null) } },
            label = stringResource(R.string.field_level) + " (" + stringResource(R.string.optional) + ")",
            error = form.levelError,
            modifier = Modifier.weight(1f),
        )
        SlotField(
            value = form.shelf,
            count = location?.shelves,
            onValueChange = { v -> onChange { it.copy(shelf = v, shelfError = null) } },
            label = stringResource(R.string.field_shelf) + " (" + stringResource(R.string.optional) + ")",
            error = form.shelfError,
            modifier = Modifier.weight(1f),
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Outlined.AcUnit, contentDescription = null)
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.frozen_switch), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.frozen_switch_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = form.frozen, onCheckedChange = onSetFrozen)
    }
    if (form.frozen) {
        DateField(
            value = form.frozenAt,
            onValueChange = { v -> onChange { it.copy(frozenAt = v, frozenAtError = null) } },
            label = stringResource(R.string.frozen_at_field),
            error = form.frozenAtError,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Nfc, contentDescription = null)
            Text(
                stringResource(if (form.nfcTagId != null) R.string.nfc_linked else R.string.nfc_not_linked),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            if (form.nfcTagId != null) {
                TextButton(onClick = onClearNfc) { Text(stringResource(R.string.nfc_unlink)) }
            }
            OutlinedButton(onClick = { nfcDialog = true }) {
                Text(stringResource(if (form.nfcTagId != null) R.string.nfc_rewrite else R.string.nfc_write))
            }
        }
    }
    if (nfcDialog) {
        de.frelina.vorrat.nfc.NfcDialog(
            mode = de.frelina.vorrat.nfc.NfcMode.Write(nfcEntryId),
            onDismiss = { nfcDialog = false },
            onWritten = { tagId -> nfcDialog = false; onNfcWritten(tagId) },
        )
    }
    DateField(
        value = form.expiry,
        onValueChange = { v -> onChange { it.copy(expiry = v, expiryError = null) } },
        label = stringResource(R.string.field_expiry),
        error = form.expiryError,
        modifier = Modifier.fillMaxWidth(),
        scanEnabled = true,
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalIconButton(
            onClick = {
                onChange {
                    val n = (it.quantity.toIntOrNull() ?: 1) - 1
                    it.copy(quantity = n.coerceAtLeast(1).toString(), quantityError = null)
                }
            },
            modifier = Modifier.size(48.dp),
        ) { Icon(Icons.Outlined.Remove, contentDescription = stringResource(R.string.action_decrease)) }
        NumberField(
            value = form.quantity,
            onValueChange = { v -> onChange { it.copy(quantity = v, quantityError = null) } },
            label = stringResource(R.string.field_quantity),
            error = form.quantityError,
            modifier = Modifier.weight(1f),
        )
        FilledTonalIconButton(
            onClick = {
                onChange {
                    val n = (it.quantity.toIntOrNull() ?: 0) + 1
                    it.copy(quantity = n.coerceAtLeast(1).toString(), quantityError = null)
                }
            },
            modifier = Modifier.size(48.dp),
        ) { Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.action_increase)) }
    }
    SelectField(
        label = stringResource(R.string.field_quantity_type),
        options = types,
        selected = types.firstOrNull { it.id == form.quantityTypeId },
        optionLabel = { "${it.symbol} – ${it.name}" },
        onSelect = { t -> onChange { it.copy(quantityTypeId = t.id, quantityTypeMissing = false) } },
        isError = form.quantityTypeMissing,
        supportingText = if (form.quantityTypeMissing) {
            { Text(stringResource(R.string.error_required), color = MaterialTheme.colorScheme.error) }
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = form.note,
        onValueChange = { v -> onChange { it.copy(note = v) } },
        label = { Text(stringResource(R.string.field_note) + " (" + stringResource(R.string.optional) + ")") },
        modifier = Modifier.fillMaxWidth(),
    )

    if (newLocationDialog) {
        NameDialog(
            title = stringResource(R.string.locations_new),
            label = stringResource(R.string.locations_name),
            initial = "",
            onDismiss = { newLocationDialog = false },
            onConfirm = onCreateLocation,
        )
    }
}

/**
 * Ebene bzw. Fach: Hat der Ort eine Anzahl festgelegt ([count]), Auswahlliste 1 … count (plus "keine Angabe"
 * und ggf. ein bisher gespeicherter Wert außerhalb des Bereichs); sonst freie Zahleneingabe.
 */
@Composable
private fun SlotField(
    value: String,
    count: Int?,
    onValueChange: (String) -> Unit,
    label: String,
    error: de.frelina.vorrat.domain.ValidationError?,
    modifier: Modifier = Modifier,
) {
    if (count == null || count <= 0) {
        NumberField(value = value, onValueChange = onValueChange, label = label, error = error, modifier = modifier)
        return
    }
    val current = value.trim().toIntOrNull()
    val options: List<Int?> = remember(count, current) {
        listOf<Int?>(null) + ((1..count).toSet() + listOfNotNull(current)).sorted()
    }
    val none = stringResource(R.string.field_slot_none)
    SelectField(
        label = label,
        options = options,
        selected = current,
        optionLabel = { it?.toString() ?: none },
        onSelect = { onValueChange(it?.toString().orEmpty()) },
        modifier = modifier,
    )
}

/**
 * Dialog zur Eingabe eines Namens; [onConfirm] liefert das Speicherergebnis,
 * damit Fehler (leer/doppelt) direkt im Dialog angezeigt werden.
 */
@Composable
fun NameDialog(
    title: String,
    label: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: suspend (String) -> SaveResult,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    var error by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; error = null },
                label = { Text(label) },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { e -> { Text(stringResource(e), color = MaterialTheme.colorScheme.error) } },
            )
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    when (onConfirm(text)) {
                        is SaveResult.Saved -> onDismiss()
                        SaveResult.Blank -> error = R.string.error_required
                        SaveResult.Duplicate -> error = R.string.error_duplicate_name
                    }
                }
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
